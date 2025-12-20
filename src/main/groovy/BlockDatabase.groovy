/**
 * SQLite database for storing and querying block coordinates from Minecraft worlds.
 *
 * Provides persistent storage for block locations discovered during world extraction,
 * enabling fast queries for specific block types without re-scanning the world.
 *
 * Features:
 * - Per-block-type limit to avoid storing millions of common blocks (stone, dirt, etc.)
 * - Always tracks total count even when limit is reached
 * - Indexes for fast queries by block type, dimension, and coordinates
 *
 * References:
 * - Groovy SQL: https://groovy-lang.org/databases.html
 * - SQLite: https://www.sqlite.org/docs.html
 */
import groovy.json.JsonBuilder
import groovy.sql.Sql
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class BlockDatabase {

    private static final Logger LOGGER = LoggerFactory.getLogger(BlockDatabase)

    private Sql sql
    private int blockLimit
    private Map<String, Integer> countCache = [:]  // In-memory count tracking for fast limit checks
    private String worldPath
    private String minecraftVersion

    /**
     * Create a new block database.
     *
     * @param dbFile The SQLite database file to create/open
     * @param blockLimit Maximum blocks per type to store (default: 5000)
     */
    BlockDatabase(File dbFile, int blockLimit = 5000) {
        this.blockLimit = blockLimit
        LOGGER.info("Opening block database: ${dbFile.absolutePath} (limit: ${blockLimit} per type)")

        // Ensure parent directory exists
        dbFile.parentFile?.mkdirs()

        // Connect to SQLite database
        this.sql = Sql.newInstance("jdbc:sqlite:${dbFile.absolutePath}", 'org.sqlite.JDBC')

        // Enable WAL mode for better concurrent read performance
        sql.execute('PRAGMA journal_mode=WAL')

        initSchema()
    }

    /**
     * Initialize database schema with tables and indexes.
     */
    private void initSchema() {
        // Main blocks table
        sql.execute('''
            CREATE TABLE IF NOT EXISTS blocks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                block_type TEXT NOT NULL,
                dimension TEXT NOT NULL,
                x INTEGER NOT NULL,
                y INTEGER NOT NULL,
                z INTEGER NOT NULL,
                properties TEXT,
                region_file TEXT,
                UNIQUE(block_type, dimension, x, y, z)
            )
        ''')

        // Block type summary table for fast counts and limit tracking
        sql.execute('''
            CREATE TABLE IF NOT EXISTS block_summary (
                block_type TEXT PRIMARY KEY,
                total_found INTEGER DEFAULT 0,
                indexed_count INTEGER DEFAULT 0,
                limit_reached INTEGER DEFAULT 0
            )
        ''')

        // Metadata table
        sql.execute('''
            CREATE TABLE IF NOT EXISTS metadata (
                key TEXT PRIMARY KEY,
                value TEXT
            )
        ''')

        // Create indexes for fast queries
        sql.execute('CREATE INDEX IF NOT EXISTS idx_block_type ON blocks(block_type)')
        sql.execute('CREATE INDEX IF NOT EXISTS idx_dimension ON blocks(dimension)')
        sql.execute('CREATE INDEX IF NOT EXISTS idx_coords ON blocks(x, y, z)')
        sql.execute('CREATE INDEX IF NOT EXISTS idx_block_dim ON blocks(block_type, dimension)')

        LOGGER.debug("Database schema initialized")
    }

    /**
     * Store metadata about the extraction.
     *
     * @param key Metadata key
     * @param value Metadata value
     */
    void setMetadata(String key, String value) {
        sql.execute('''
            INSERT INTO metadata (key, value) VALUES (?, ?)
            ON CONFLICT(key) DO UPDATE SET value = excluded.value
        ''', [key, value])
    }

    /**
     * Get metadata value.
     *
     * @param key Metadata key
     * @return Metadata value or null if not found
     */
    String getMetadata(String key) {
        def row = sql.firstRow('SELECT value FROM metadata WHERE key = ?', [key])
        return row?.value
    }

    /**
     * Set world path metadata.
     */
    void setWorldPath(String path) {
        this.worldPath = path
        setMetadata('world_path', path)
    }

    /**
     * Set extraction timestamp.
     */
    void setExtractionDate(String date) {
        setMetadata('extraction_date', date)
    }

    /**
     * Set Minecraft version.
     */
    void setMinecraftVersion(String version) {
        this.minecraftVersion = version
        setMetadata('minecraft_version', version)
    }

    /**
     * Set block limit in metadata.
     */
    void setBlockLimitMetadata() {
        setMetadata('block_limit', blockLimit.toString())
    }

    /**
     * Insert a block with limit enforcement.
     *
     * @param blockType Block type (e.g., "minecraft:nether_portal")
     * @param dimension Dimension name (overworld, nether, end)
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @param properties Optional block properties as a map
     * @param regionFile Optional source region file name
     * @return true if inserted, false if limit reached or duplicate
     */
    boolean insertBlock(String blockType, String dimension, int x, int y, int z,
                        Map<String, String> properties = null, String regionFile = null) {
        // Get current count from cache (or initialize if first time seeing this block type)
        int currentCount = countCache.getOrDefault(blockType, 0)

        // Always update total_found in summary table (even if we don't store the block)
        sql.execute('''
            INSERT INTO block_summary (block_type, total_found, indexed_count, limit_reached)
            VALUES (?, 1, 0, 0)
            ON CONFLICT(block_type) DO UPDATE SET total_found = total_found + 1
        ''', [blockType])

        // Check if limit reached
        if (blockLimit > 0 && currentCount >= blockLimit) {
            // Mark as limit reached if not already
            sql.execute('UPDATE block_summary SET limit_reached = 1 WHERE block_type = ?', [blockType])
            return false
        }

        // Convert properties to JSON if present
        String propsJson = properties ? new JsonBuilder(properties).toString() : null

        try {
            // Insert block (OR IGNORE handles duplicates from unique constraint)
            int inserted = sql.executeUpdate('''
                INSERT OR IGNORE INTO blocks (block_type, dimension, x, y, z, properties, region_file)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            ''', [blockType, dimension, x, y, z, propsJson, regionFile])

            if (inserted > 0) {
                // Update cache and indexed_count
                int newCount = currentCount + 1
                countCache[blockType] = newCount
                sql.execute('UPDATE block_summary SET indexed_count = indexed_count + 1 WHERE block_type = ?', [blockType])

                // Mark limit_reached when we hit exactly the limit
                if (blockLimit > 0 && newCount >= blockLimit) {
                    sql.execute('UPDATE block_summary SET limit_reached = 1 WHERE block_type = ?', [blockType])
                }
                return true
            }

            return false  // Duplicate (already exists)
        } catch (Exception e) {
            LOGGER.warn("Failed to insert block ${blockType} at (${x}, ${y}, ${z}): ${e.message}")
            return false
        }
    }

    /**
     * Query blocks by type.
     *
     * @param blockType Block type to query (e.g., "minecraft:nether_portal")
     * @param dimension Optional dimension filter
     * @return List of block records as maps
     */
    List<Map> queryByBlockType(String blockType, String dimension = null) {
        // Normalize block type
        String normalizedType = blockType.contains(':') ? blockType : "minecraft:${blockType}"

        String query = 'SELECT block_type, dimension, x, y, z, properties, region_file FROM blocks WHERE block_type = ?'
        List params = [normalizedType]

        if (dimension) {
            query += ' AND dimension = ?'
            params << dimension
        }

        query += ' ORDER BY dimension, x, y, z'

        return sql.rows(query, params)
    }

    /**
     * Query blocks near a coordinate.
     *
     * @param x Center X coordinate
     * @param y Center Y coordinate
     * @param z Center Z coordinate
     * @param radius Search radius
     * @param dimension Optional dimension filter
     * @return List of block records as maps
     */
    List<Map> queryNearCoordinates(int x, int y, int z, int radius = 50, String dimension = null) {
        String query = '''
            SELECT block_type, dimension, x, y, z, properties, region_file
            FROM blocks
            WHERE x BETWEEN ? AND ?
              AND y BETWEEN ? AND ?
              AND z BETWEEN ? AND ?
        '''
        List params = [x - radius, x + radius, y - radius, y + radius, z - radius, z + radius]

        if (dimension) {
            query += ' AND dimension = ?'
            params << dimension
        }

        query += ' ORDER BY block_type, x, y, z'

        return sql.rows(query, params)
    }

    /**
     * Get summary statistics for all block types.
     *
     * @return List of summary records ordered by total_found descending
     */
    List<Map> getSummary() {
        return sql.rows('SELECT block_type, total_found, indexed_count, limit_reached FROM block_summary ORDER BY total_found DESC')
    }

    /**
     * Get count for a specific block type.
     *
     * @param blockType Block type to count
     * @return Map with total_found and indexed_count, or null if not found
     */
    Map getBlockCount(String blockType) {
        String normalizedType = blockType.contains(':') ? blockType : "minecraft:${blockType}"
        def row = sql.firstRow('SELECT total_found, indexed_count, limit_reached FROM block_summary WHERE block_type = ?', [normalizedType])
        return row ? [total_found: row.total_found, indexed_count: row.indexed_count, limit_reached: row.limit_reached] : null
    }

    /**
     * Get total number of unique block types indexed.
     */
    int getBlockTypeCount() {
        def row = sql.firstRow('SELECT COUNT(DISTINCT block_type) as count FROM block_summary')
        return row?.count ?: 0
    }

    /**
     * Get total number of blocks indexed.
     */
    int getTotalBlocksIndexed() {
        def row = sql.firstRow('SELECT SUM(indexed_count) as total FROM block_summary')
        return row?.total ?: 0
    }

    /**
     * Begin a transaction for batch inserts.
     */
    void beginTransaction() {
        sql.execute('BEGIN TRANSACTION')
    }

    /**
     * Commit the current transaction.
     */
    void commitTransaction() {
        sql.execute('COMMIT')
    }

    /**
     * Rollback the current transaction.
     */
    void rollbackTransaction() {
        sql.execute('ROLLBACK')
    }

    /**
     * Close the database connection.
     */
    void close() {
        try {
            sql?.close()
            LOGGER.debug("Block database closed")
        } catch (Exception e) {
            LOGGER.warn("Error closing database: ${e.message}")
        }
    }

    /**
     * Query all blocks from the database in a deterministic order.
     *
     * @param dimension Optional dimension filter
     * @return List of all block records as maps
     */
    List<Map> queryAllBlocks(String dimension = null) {
        String query = 'SELECT block_type, dimension, x, y, z, properties, region_file FROM blocks'
        List params = []

        if (dimension) {
            query += ' WHERE dimension = ?'
            params << dimension
        }

        query += ' ORDER BY block_type, dimension, x, y, z'
        return sql.rows(query, params)
    }

    /**
     * Stream all blocks from the database, calling the provided callback for each row.
     * This avoids loading all blocks into memory at once.
     *
     * @param callback Closure receiving (blockType, dimension, x, y, z, properties, regionFile)
     * @param dimension Optional dimension filter
     */
    void streamBlocks(Closure callback, String dimension = null) {
        String query = 'SELECT block_type, dimension, x, y, z, properties, region_file FROM blocks'
        List params = []

        if (dimension) {
            query += ' WHERE dimension = ?'
            params << dimension
        }

        query += ' ORDER BY block_type, dimension, x, y, z'

        sql.eachRow(query, params) { row ->
            callback(row.block_type, row.dimension, row.x, row.y, row.z, row.properties, row.region_file)
        }
    }

    /**
     * Get total count of indexed blocks.
     */
    int getTotalBlockCount() {
        def row = sql.firstRow('SELECT COUNT(*) as count FROM blocks')
        return row?.count ?: 0
    }

    /**
     * Static factory method to open an existing database for querying.
     *
     * @param dbFile The SQLite database file
     * @return BlockDatabase instance, or null if file doesn't exist
     */
    static BlockDatabase openForQuery(File dbFile) {
        if (!dbFile.exists()) {
            return null
        }
        // Use a large limit (won't matter for queries)
        return new BlockDatabase(dbFile, Integer.MAX_VALUE)
    }
}

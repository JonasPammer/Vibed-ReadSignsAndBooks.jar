/**
 * SQLite database for storing and querying item metadata from Minecraft worlds.
 *
 * Provides persistent storage for item data discovered during world extraction,
 * enabling fast queries for specific item types, enchantments, custom names, etc.
 *
 * Features:
 * - Per-item-type limit to avoid storing millions of common items (cobblestone, dirt, etc.)
 * - Always tracks total count even when limit is reached
 * - Indexes for fast queries by item type, dimension, enchantments
 * - Supports both pre-1.20.5 and 1.20.5+ NBT formats
 *
 * References:
 * - Groovy SQL: https://groovy-lang.org/databases.html
 * - SQLite: https://www.sqlite.org/docs.html
 * - Minecraft Item Format: https://minecraft.wiki/w/Player.dat_format#Item_structure
 * - Minecraft Data Components (1.20.5+): https://minecraft.wiki/w/Data_component_format
 */
import groovy.json.JsonBuilder
import groovy.sql.Sql
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.SQLException

class ItemDatabase implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ItemDatabase)

    // Default items to skip (very common blocks that would flood the database)
    static final Set<String> DEFAULT_SKIP_ITEMS = [
        'minecraft:stone', 'minecraft:dirt', 'minecraft:cobblestone',
        'minecraft:grass_block', 'minecraft:air', 'minecraft:deepslate',
        'minecraft:netherrack', 'minecraft:end_stone', 'minecraft:sand',
        'minecraft:gravel', 'minecraft:diorite', 'minecraft:granite',
        'minecraft:andesite', 'minecraft:tuff', 'minecraft:calcite'
    ] as Set

    private final Sql sql
    private final int itemLimit
    private final Map<String, Integer> countCache = [:]  // In-memory count tracking for fast limit checks
    private String worldPath
    private String minecraftVersion

    /**
     * Create a new item database.
     *
     * @param dbFile The SQLite database file to create/open
     * @param itemLimit Maximum items per type to store (default: 1000)
     */
    ItemDatabase(File dbFile, int itemLimit = 1000) {
        this.itemLimit = itemLimit
        LOGGER.info("Opening item database: ${dbFile.absolutePath} (limit: ${itemLimit} per type)")

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
        // Main items table
        // Note: SQLite UNIQUE constraints treat NULLs as distinct, so we normalize player_uuid to '' (empty string)
        // and include it in the UNIQUE constraint to avoid collisions between different players.
        sql.execute('''
            CREATE TABLE IF NOT EXISTS items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                item_id TEXT NOT NULL,
                count INTEGER NOT NULL DEFAULT 1,
                dimension TEXT DEFAULT '',
                x INTEGER DEFAULT 0,
                y INTEGER DEFAULT 0,
                z INTEGER DEFAULT 0,
                container_type TEXT DEFAULT '',
                player_uuid TEXT DEFAULT '',
                slot INTEGER DEFAULT -1,
                custom_name TEXT,
                damage INTEGER,
                enchantments TEXT,
                stored_enchantments TEXT,
                lore TEXT,
                unbreakable INTEGER DEFAULT 0,
                custom_data TEXT,
                region_file TEXT,
                UNIQUE(item_id, dimension, x, y, z, slot, container_type, player_uuid)
            )
        ''')

        // Item type summary table for fast counts and limit tracking
        sql.execute('''
            CREATE TABLE IF NOT EXISTS item_summary (
                item_id TEXT PRIMARY KEY,
                total_count INTEGER DEFAULT 0,
                unique_locations INTEGER DEFAULT 0,
                with_enchantments INTEGER DEFAULT 0,
                with_custom_name INTEGER DEFAULT 0,
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
        sql.execute('CREATE INDEX IF NOT EXISTS idx_item_id ON items(item_id)')
        sql.execute('CREATE INDEX IF NOT EXISTS idx_item_dimension ON items(dimension)')
        sql.execute('CREATE INDEX IF NOT EXISTS idx_item_coords ON items(x, y, z)')
        sql.execute('CREATE INDEX IF NOT EXISTS idx_item_custom_name ON items(custom_name)')
        sql.execute('CREATE INDEX IF NOT EXISTS idx_item_enchantments ON items(enchantments)')
        sql.execute('CREATE INDEX IF NOT EXISTS idx_item_id_dim ON items(item_id, dimension)')

        // Uniqueness across item locations:
        // Include player_uuid (when present) to avoid collisions between different players whose items share the
        // same coordinates/slot (e.g. multiple playerdata files with identical Pos and Slot numbers).
        //
        // Use COALESCE so historical DBs (where player_uuid might be NULL) still behave consistently.
        sql.execute('''
            CREATE UNIQUE INDEX IF NOT EXISTS idx_item_unique_location
            ON items(item_id, dimension, x, y, z, slot, container_type, COALESCE(player_uuid, ''))
        ''')

        LOGGER.debug('Item database schema initialized')
    }

    /**
     * Store metadata about the extraction.
     */
    void setMetadata(String key, String value) {
        sql.execute('''
            INSERT INTO metadata (key, value) VALUES (?, ?)
            ON CONFLICT(key) DO UPDATE SET value = excluded.value
        ''', [key, value])
    }

    /**
     * Get metadata value.
     */
    String getMetadata(String key) {
        groovy.sql.GroovyRowResult row = sql.firstRow('SELECT value FROM metadata WHERE key = ?', [key])
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
     * Set item limit in metadata.
     */
    void setItemLimitMetadata() {
        setMetadata('item_limit', itemLimit.toString())
    }

    /**
     * Data class for item metadata extracted from NBT.
     */
    static class ItemMetadata {

        String itemId
        int count = 1
        String dimension
        int x, y, z
        String containerType
        String playerUuid
        Integer slot
        String customName
        Integer damage
        Map<String, Integer> enchantments = [:]
        Map<String, Integer> storedEnchantments = [:]  // For enchanted books
        List<String> lore = []
        boolean unbreakable = false
        String regionFile

        ItemMetadata(String itemId) {
            this.itemId = itemId
        }

        boolean hasEnchantments() {
            return !enchantments.isEmpty() || !storedEnchantments.isEmpty()
        }

        boolean hasCustomName() {
            return customName != null && !customName.trim().empty
        }

    }

    /**
     * Insert an item with limit enforcement.
     *
     * @param metadata ItemMetadata object containing all item data
     * @return true if inserted, false if limit reached or duplicate
     */
    boolean insertItem(ItemMetadata metadata) {
        String itemId = metadata.itemId

        // Get current count from cache
        int currentCount = countCache.getOrDefault(itemId, 0)

        // Normalize NULL values to defaults for unique constraint (SQLite treats NULLs as distinct).
        //
        // IMPORTANT: Do NOT use Groovy's Elvis operator (`?:`) for numeric fields like slot:
        // slot 0 is a valid value but is "falsey" in Groovy, which would incorrectly coerce it to -1.
        String dimension = metadata.dimension ?: ''
        String containerType = metadata.containerType ?: ''
        String playerUuid = metadata.playerUuid ?: ''
        Integer slot = (metadata.slot != null) ? metadata.slot : -1

        // Check if limit reached BEFORE updating summary
        if (itemLimit > 0 && currentCount >= itemLimit) {
            // Update summary with total_count only (item not stored)
            sql.execute('''
                INSERT INTO item_summary (item_id, total_count, unique_locations, with_enchantments, with_custom_name, limit_reached)
                VALUES (?, ?, 0, 0, 0, 1)
                ON CONFLICT(item_id) DO UPDATE SET
                    total_count = total_count + ?,
                    limit_reached = 1
            ''', [itemId, metadata.count, metadata.count])
            return false
        }

        // Convert collections to JSON
        String enchantmentsJson = metadata.enchantments.isEmpty() ? null : new JsonBuilder(metadata.enchantments).toString()
        String storedEnchJson = metadata.storedEnchantments.isEmpty() ? null : new JsonBuilder(metadata.storedEnchantments).toString()
        String loreJson = metadata.lore.isEmpty() ? null : new JsonBuilder(metadata.lore).toString()

        try {
            int inserted = sql.executeUpdate('''
                INSERT OR IGNORE INTO items (
                    item_id, count, dimension, x, y, z, container_type, player_uuid,
                    slot, custom_name, damage, enchantments, stored_enchantments,
                    lore, unbreakable, region_file
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ''', [
                itemId,
                metadata.count,
                dimension,
                metadata.x,
                metadata.y,
                metadata.z,
                containerType,
                playerUuid,
                slot,
                metadata.customName,
                metadata.damage,
                enchantmentsJson,
                storedEnchJson,
                loreJson,
                metadata.unbreakable ? 1 : 0,
                metadata.regionFile
            ])

            if (inserted > 0) {
                // Item was inserted - update summary with all fields
                countCache[itemId] = currentCount + 1
                sql.execute('''
                    INSERT INTO item_summary (item_id, total_count, unique_locations, with_enchantments, with_custom_name, limit_reached)
                    VALUES (?, ?, 1, ?, ?, 0)
                    ON CONFLICT(item_id) DO UPDATE SET
                        total_count = total_count + ?,
                        unique_locations = unique_locations + 1,
                        with_enchantments = with_enchantments + ?,
                        with_custom_name = with_custom_name + ?
                ''', [
                    itemId,
                    metadata.count,
                    metadata.hasEnchantments() ? 1 : 0,
                    metadata.hasCustomName() ? 1 : 0,
                    metadata.count,
                    metadata.hasEnchantments() ? 1 : 0,
                    metadata.hasCustomName() ? 1 : 0
                ])
                return true
            }

            // Duplicate item - update total_count only
            sql.execute('''
                INSERT INTO item_summary (item_id, total_count, unique_locations, with_enchantments, with_custom_name, limit_reached)
                VALUES (?, ?, 0, 0, 0, 0)
                ON CONFLICT(item_id) DO UPDATE SET
                    total_count = total_count + ?
            ''', [itemId, metadata.count, metadata.count])
            return false
        } catch (SQLException e) {
            LOGGER.warn("Failed to insert item ${itemId} at (${metadata.x}, ${metadata.y}, ${metadata.z}): ${e.message}")
            return false
        }
    }

    /**
     * Query items by type.
     *
     * @param itemId Item ID to query (e.g., "minecraft:diamond_sword")
     * @param dimension Optional dimension filter
     * @return List of item records as maps
     */
    List<Map> queryByItemType(String itemId, String dimension = null) {
        String normalizedId = itemId.contains(':') ? itemId : "minecraft:${itemId}"

        String query = '''
            SELECT item_id, count, dimension, x, y, z, container_type, player_uuid,
                   slot, custom_name, damage, enchantments, stored_enchantments,
                   lore, unbreakable, region_file
            FROM items WHERE item_id = ?
        '''
        List<Object> params = [normalizedId]

        if (dimension) {
            query += ' AND dimension = ?'
            params << dimension
        }

        query += ' ORDER BY dimension, x, y, z'

        return sql.rows(query, params) ?: []
    }

    /**
     * Query items with enchantments.
     *
     * @param enchantmentFilter Optional enchantment name to filter (e.g., "sharpness")
     * @param dimension Optional dimension filter
     * @return List of enchanted item records
     */
    List<Map> queryEnchantedItems(String enchantmentFilter = null, String dimension = null) {
        StringBuilder query = new StringBuilder('''
            SELECT item_id, count, dimension, x, y, z, container_type,
                   custom_name, damage, enchantments, stored_enchantments, region_file
            FROM items WHERE (enchantments IS NOT NULL OR stored_enchantments IS NOT NULL)
        ''')
        List<Object> params = []

        if (enchantmentFilter) {
            // Search in both enchantments columns for the filter text
            query.append(' AND (enchantments LIKE ? OR stored_enchantments LIKE ?)')
            params << "%${enchantmentFilter}%"
            params << "%${enchantmentFilter}%"
        }

        if (dimension) {
            query.append(' AND dimension = ?')
            params << dimension
        }

        query.append(' ORDER BY item_id, dimension, x, y, z')

        return sql.rows(query.toString(), params) ?: []
    }

    /**
     * Query items with custom names.
     *
     * @param nameFilter Optional partial name to filter
     * @param dimension Optional dimension filter
     * @return List of named item records
     */
    List<Map> queryNamedItems(String nameFilter = null, String dimension = null) {
        StringBuilder query = new StringBuilder('''
            SELECT item_id, count, dimension, x, y, z, container_type,
                   custom_name, enchantments, region_file
            FROM items WHERE custom_name IS NOT NULL
        ''')
        List<Object> params = []

        if (nameFilter) {
            query.append(' AND custom_name LIKE ?')
            params << "%${nameFilter}%"
        }

        if (dimension) {
            query.append(' AND dimension = ?')
            params << dimension
        }

        query.append(' ORDER BY custom_name, item_id, dimension')

        return sql.rows(query.toString(), params) ?: []
    }

    /**
     * Query items near a coordinate.
     */
    List<Map> queryNearCoordinates(int x, int y, int z, int radius = 50, String dimension = null) {
        StringBuilder query = new StringBuilder('''
            SELECT item_id, count, dimension, x, y, z, container_type, custom_name,
                   enchantments, stored_enchantments, region_file
            FROM items
            WHERE x BETWEEN ? AND ?
              AND y BETWEEN ? AND ?
              AND z BETWEEN ? AND ?
        ''')
        List<Object> params = [x - radius, x + radius, y - radius, y + radius, z - radius, z + radius]

        if (dimension) {
            query.append(' AND dimension = ?')
            params << dimension
        }

        query.append(' ORDER BY item_id, x, y, z')

        return sql.rows(query.toString(), params) ?: []
    }

    /**
     * Get summary statistics for all item types.
     *
     * @return List of summary records ordered by total_count descending
     */
    List<Map> getSummary() {
        return sql.rows('''
            SELECT item_id, total_count, unique_locations, with_enchantments,
                   with_custom_name, limit_reached
            FROM item_summary
            ORDER BY total_count DESC
        ''') ?: []
    }

    /**
     * Get count for a specific item type.
     */
    Map<String, Object> getItemCount(String itemId) {
        String normalizedId = itemId.contains(':') ? itemId : "minecraft:${itemId}"
        groovy.sql.GroovyRowResult row = sql.firstRow('''
            SELECT total_count, unique_locations, with_enchantments, with_custom_name, limit_reached
            FROM item_summary WHERE item_id = ?
        ''', [normalizedId])
        return row ? [
            total_count: row.total_count,
            unique_locations: row.unique_locations,
            with_enchantments: row.with_enchantments,
            with_custom_name: row.with_custom_name,
            limit_reached: row.limit_reached
        ] : [:]
    }

    /**
     * Get total number of unique item types indexed.
     */
    int getItemTypeCount() {
        groovy.sql.GroovyRowResult row = sql.firstRow('SELECT COUNT(DISTINCT item_id) as count FROM item_summary')
        return row?.count ?: 0
    }

    /**
     * Get total number of items indexed.
     */
    int getTotalItemsIndexed() {
        groovy.sql.GroovyRowResult row = sql.firstRow('SELECT SUM(unique_locations) as total FROM item_summary')
        return row?.total ?: 0
    }

    /**
     * Get total count of all items (including those not stored due to limit).
     */
    long getTotalItemCount() {
        groovy.sql.GroovyRowResult row = sql.firstRow('SELECT SUM(total_count) as total FROM item_summary')
        return row?.total ?: 0L
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
    @Override
    void close() {
        try {
            if (sql) {
                sql.close()
            }
            LOGGER.debug('Item database closed')
        } catch (SQLException e) {
            LOGGER.warn("Error closing database: ${e.message}")
        }
    }

    /**
     * Static factory method to open an existing database for querying.
     *
     * @param dbFile The SQLite database file
     * @return ItemDatabase instance, or null if file doesn't exist
     */
    static ItemDatabase openForQuery(File dbFile) {
        if (!dbFile.exists()) {
            return null
        }
        // Use a large limit (won't matter for queries)
        return new ItemDatabase(dbFile, Integer.MAX_VALUE)
    }

}

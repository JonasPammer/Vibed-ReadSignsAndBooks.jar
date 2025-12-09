# Block Index Database - Implementation Plan

**Date:** 2025-12-09
**Status:** Planning (for future implementation)
**Complexity:** Low-Medium (~150-200 lines new code)

---

## Problem Statement

After extracting data from a Minecraft world, users want to quickly search for coordinates of ANY block type without re-scanning the entire world (which can take 10+ minutes for large worlds).

**Use Cases:**
1. "Where are all the nether portals in my world?"
2. "Find all diamond ore blocks in the Nether"
3. "What's at coordinates X, Y, Z?"
4. "How many of each block type exist?"

---

## Recommended Solution: SQLite with Groovy SQL

After evaluating multiple approaches (H2, MapDB, JSON files, CSV + grep), **SQLite** is the winner because:

| Criteria | SQLite | H2 | JSON Files | CSV + grep |
|----------|--------|-----|------------|------------|
| KISS Factor | ✅ High | ✅ High | ✅ High | ✅ Very High |
| Query Speed | ✅ Fast (indexed) | ✅ Fast | ❌ O(n) | ❌ O(n) |
| Dependencies | 1 JAR (~10MB) | 1 JAR (~2.5MB) | None | None |
| Standard Tools | ✅ sqlite3 CLI | ❌ H2 Console | ✅ jq, cat | ✅ grep, awk |
| Groovy Support | ✅ Built-in SQL | ✅ Built-in SQL | Manual parse | Manual parse |
| Million+ rows | ✅ Handles well | ✅ Handles well | ❌ Memory issues | ❌ Very slow |

**Key Advantage:** Groovy has `groovy.sql.Sql` built into the language - no ORM, no boilerplate, just pure SQL.

---

## Technical Design

### New Dependency

```groovy
// build.gradle
dependencies {
    implementation 'org.xerial:sqlite-jdbc:3.45.1.0'
}
```

Single JAR, pure Java (includes native SQLite for all platforms), well-maintained.

### Database Schema

```sql
-- blocks.db

-- Main blocks table
CREATE TABLE IF NOT EXISTS blocks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    block_type TEXT NOT NULL,
    dimension TEXT NOT NULL,
    x INTEGER NOT NULL,
    y INTEGER NOT NULL,
    z INTEGER NOT NULL,
    properties TEXT,           -- JSON string: {"axis":"x"}
    region_file TEXT,
    UNIQUE(block_type, dimension, x, y, z)
);

-- Block type summary (for fast counts and limit tracking)
CREATE TABLE IF NOT EXISTS block_summary (
    block_type TEXT PRIMARY KEY,
    total_found INTEGER DEFAULT 0,   -- Total blocks found in world
    indexed_count INTEGER DEFAULT 0, -- Blocks actually stored
    limit_reached BOOLEAN DEFAULT 0  -- True if > N blocks skipped
);

-- Metadata
CREATE TABLE IF NOT EXISTS metadata (
    key TEXT PRIMARY KEY,
    value TEXT
);
-- Stores: world_path, extraction_date, minecraft_version, block_limit

-- Indexes for fast queries
CREATE INDEX IF NOT EXISTS idx_block_type ON blocks(block_type);
CREATE INDEX IF NOT EXISTS idx_dimension ON blocks(dimension);
CREATE INDEX IF NOT EXISTS idx_coords ON blocks(x, y, z);
CREATE INDEX IF NOT EXISTS idx_block_dim ON blocks(block_type, dimension);
```

### File Location

```
[output_folder]/
├── ReadBooks/
│   └── 2025-12-09/
│       ├── books.json
│       ├── signs.json
│       └── block_index.db     <-- NEW: SQLite database
```

---

## CLI Interface

### Building the Index

```bash
# Build block index during extraction
java -jar ReadSignsAndBooks.jar -w /path/to/world --build-block-index

# With custom limit (default: 5000)
java -jar ReadSignsAndBooks.jar -w /path/to/world --build-block-index --block-limit 10000

# Index specific blocks only (faster)
java -jar ReadSignsAndBooks.jar -w /path/to/world --build-block-index --blocks "nether_portal,diamond_ore,spawner"
```

### Querying the Index

```bash
# Query by block type (outputs CSV to stdout)
java -jar ReadSignsAndBooks.jar --query-blocks "nether_portal" -o ./output

# Query with dimension filter
java -jar ReadSignsAndBooks.jar --query-blocks "diamond_ore" --dimension nether -o ./output

# List all indexed block types with counts
java -jar ReadSignsAndBooks.jar --list-blocks -o ./output

# Count only (no coordinates)
java -jar ReadSignsAndBooks.jar --query-blocks "nether_portal" --count-only -o ./output

# Export query results to CSV file
java -jar ReadSignsAndBooks.jar --query-blocks "nether_portal" -o ./output --export-csv portals.csv
```

### Alternative: Direct sqlite3 Queries

Users can also query directly with the sqlite3 CLI:

```bash
# Open database
sqlite3 ./output/ReadBooks/2025-12-09/block_index.db

# Find all nether portals
SELECT x, y, z, dimension FROM blocks WHERE block_type = 'minecraft:nether_portal';

# Count by dimension
SELECT dimension, COUNT(*) FROM blocks WHERE block_type = 'minecraft:nether_portal' GROUP BY dimension;

# Find blocks near coordinates
SELECT * FROM blocks WHERE x BETWEEN 100 AND 200 AND z BETWEEN -50 AND 50;

# Block type summary
SELECT * FROM block_summary ORDER BY total_found DESC;
```

---

## Implementation Plan

### New File: `BlockDatabase.groovy` (~100-150 lines)

```groovy
import groovy.sql.Sql

class BlockDatabase {

    private Sql sql
    private int blockLimit
    private Map<String, Integer> countCache = [:]  // In-memory count tracking

    BlockDatabase(File dbFile, int blockLimit = 5000) {
        this.blockLimit = blockLimit
        this.sql = Sql.newInstance("jdbc:sqlite:${dbFile.absolutePath}")
        initSchema()
    }

    private void initSchema() {
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
        sql.execute('CREATE INDEX IF NOT EXISTS idx_block_type ON blocks(block_type)')
        sql.execute('CREATE INDEX IF NOT EXISTS idx_dimension ON blocks(dimension)')
        sql.execute('CREATE INDEX IF NOT EXISTS idx_coords ON blocks(x, y, z)')

        sql.execute('''
            CREATE TABLE IF NOT EXISTS block_summary (
                block_type TEXT PRIMARY KEY,
                total_found INTEGER DEFAULT 0,
                indexed_count INTEGER DEFAULT 0,
                limit_reached INTEGER DEFAULT 0
            )
        ''')
    }

    /**
     * Insert a block with limit enforcement
     * @return true if inserted, false if limit reached
     */
    boolean insertBlock(String blockType, String dimension, int x, int y, int z,
                        Map<String, String> properties = null, String regionFile = null) {
        // Check limit from cache
        int currentCount = countCache.getOrDefault(blockType, 0)

        // Update summary (always track total found)
        sql.execute('''
            INSERT INTO block_summary (block_type, total_found, indexed_count, limit_reached)
            VALUES (?, 1, 0, 0)
            ON CONFLICT(block_type) DO UPDATE SET total_found = total_found + 1
        ''', [blockType])

        if (currentCount >= blockLimit) {
            // Mark as limit reached
            sql.execute('UPDATE block_summary SET limit_reached = 1 WHERE block_type = ?', [blockType])
            return false
        }

        // Insert block
        String propsJson = properties ? new groovy.json.JsonBuilder(properties).toString() : null
        try {
            sql.execute('''
                INSERT OR IGNORE INTO blocks (block_type, dimension, x, y, z, properties, region_file)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            ''', [blockType, dimension, x, y, z, propsJson, regionFile])

            countCache[blockType] = currentCount + 1
            sql.execute('UPDATE block_summary SET indexed_count = indexed_count + 1 WHERE block_type = ?', [blockType])
            return true
        } catch (Exception e) {
            return false  // Duplicate or other error
        }
    }

    /**
     * Query blocks by type
     */
    List<Map> queryByBlockType(String blockType, String dimension = null) {
        String query = 'SELECT * FROM blocks WHERE block_type = ?'
        List params = [blockType]

        if (dimension) {
            query += ' AND dimension = ?'
            params << dimension
        }

        return sql.rows(query, params)
    }

    /**
     * Get summary statistics
     */
    List<Map> getSummary() {
        return sql.rows('SELECT * FROM block_summary ORDER BY total_found DESC')
    }

    void close() {
        sql?.close()
    }
}
```

### Integration with BlockSearcher.groovy

Add database writing during block iteration:

```groovy
// In BlockSearcher.searchBlocks()

static BlockDatabase database

static List<BlockLocation> searchBlocks(String worldPath, Set<String> targetBlocks,
                                         List<String> dimensions, File outputFolder = null,
                                         boolean buildIndex = false, int blockLimit = 5000) {

    if (buildIndex && outputFolder) {
        database = new BlockDatabase(new File(outputFolder, 'block_index.db'), blockLimit)
    }

    // ... existing iteration code ...

    // Inside the block processing loop:
    if (database) {
        database.insertBlock(
            blockLocation.blockType,
            blockLocation.dimension,
            blockLocation.x, blockLocation.y, blockLocation.z,
            blockLocation.properties,
            blockLocation.regionFile
        )
    }

    // At end:
    database?.close()
}
```

### New CLI Options in Main.groovy

```groovy
@Option(names = ['--build-block-index'], description = 'Build searchable block coordinate database')
static boolean buildBlockIndex = false

@Option(names = ['--block-limit'], description = 'Max blocks per type to index (default: 5000)')
static int blockLimit = 5000

@Option(names = ['--blocks'], description = 'Comma-separated block types to index (all if not specified)')
static String targetBlockTypes = null

@Option(names = ['--query-blocks'], description = 'Query block coordinates from index')
static String queryBlockType = null

@Option(names = ['--list-blocks'], description = 'List all indexed block types')
static boolean listIndexedBlocks = false
```

---

## The 5000 Limit Logic

**Why limit?**

Common blocks like `minecraft:stone`, `minecraft:dirt`, `minecraft:air` can have MILLIONS of instances. Storing all coordinates would:
1. Create massive databases (GB+)
2. Slow down queries
3. Provide little value (who needs coordinates of every stone block?)

**How it works:**

1. **During extraction:** Track count per block type in memory HashMap
2. **Check before insert:** If count >= limit, skip insert
3. **Always track totals:** The `block_summary` table shows actual world counts
4. **Configurable:** `--block-limit 10000` or `--block-limit 0` (unlimited)

**Example output:**

```
$ java -jar ReadSignsAndBooks.jar --list-blocks -o ./output

Block Index Summary:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Block Type                    Total Found    Indexed    Limited
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
minecraft:stone               12,847,293     5,000      ✓
minecraft:dirt                 3,421,567     5,000      ✓
minecraft:diamond_ore              1,247     1,247
minecraft:nether_portal              127       127
minecraft:spawner                     43        43
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## Alternative Approach: JSON Index Files (Simpler, No DB)

If SQLite feels like overkill, here's an even simpler approach:

### Structure

```
block_index/
├── minecraft_nether_portal.json
├── minecraft_diamond_ore.json
├── minecraft_spawner.json
└── _summary.json
```

### File Format

```json
// minecraft_nether_portal.json
{
  "block_type": "minecraft:nether_portal",
  "indexed_at": "2025-12-09T14:30:00Z",
  "total_found": 127,
  "limit_applied": false,
  "coordinates": [
    {"dim": "nether", "x": 15, "y": 64, "z": 102, "props": {"axis": "x"}},
    {"dim": "nether", "x": 16, "y": 64, "z": 102, "props": {"axis": "x"}}
  ]
}
```

### Query with jq

```bash
# Find all nether portals in the nether
cat block_index/minecraft_nether_portal.json | jq '.coordinates[] | select(.dim == "nether")'

# Count by dimension
cat block_index/minecraft_nether_portal.json | jq '.coordinates | group_by(.dim) | map({dim: .[0].dim, count: length})'
```

### Pros/Cons vs SQLite

| Aspect | JSON Files | SQLite |
|--------|------------|--------|
| Dependencies | None | sqlite-jdbc |
| Query speed (rare blocks) | Fast (small file) | Fast |
| Query speed (common blocks) | Slow (5000 entries) | Fast (indexed) |
| Cross-block queries | Hard (multiple files) | Easy (SQL JOIN) |
| Human readable | ✅ Yes | ❌ Binary |
| Standard tools | jq, grep | sqlite3 |

**Recommendation:** Use JSON for simple use cases, SQLite for power users.

---

## Implementation Phases

### Phase 1: SQLite Core (Recommended First)
- Add sqlite-jdbc dependency
- Create BlockDatabase.groovy
- Basic insert/query functionality
- CLI: `--build-block-index`, `--query-blocks`

### Phase 2: Integration
- Hook into BlockSearcher iteration
- Add block limit logic
- Add `--block-limit` flag

### Phase 3: Query Features
- `--list-blocks` summary
- `--dimension` filter
- `--export-csv` output
- `--count-only` mode

### Phase 4: GUI Integration (Optional)
- Add checkbox: "Build block index"
- Add query tab/dialog
- Block type autocomplete

---

## Estimated Effort

| Component | Lines of Code | Complexity |
|-----------|---------------|------------|
| BlockDatabase.groovy | ~100-150 | Low |
| Main.groovy CLI flags | ~30 | Low |
| BlockSearcher integration | ~20 | Low |
| Output formatting | ~50 | Low |
| **Total** | **~200-250** | **Low** |

**Time estimate:** 2-4 hours for core functionality

---

## References

- [SQLite JDBC Driver](https://github.com/xerial/sqlite-jdbc) - The driver we'll use
- [Groovy SQL Documentation](https://groovy-lang.org/databases.html) - Built-in SQL support
- [SQLite Documentation](https://www.sqlite.org/docs.html) - SQL syntax reference
- Existing code: `BlockSearcher.groovy`, `OutputWriters.groovy`

---

## Decision Summary

**Primary Recommendation:** SQLite with `groovy.sql.Sql`

**Rationale:**
1. ✅ KISS - Groovy SQL is built-in, minimal boilerplate
2. ✅ Fast - Indexed queries for millions of blocks
3. ✅ Standard - Can query with sqlite3 CLI too
4. ✅ Portable - Single .db file, easy to share
5. ✅ Proven - sqlite-jdbc is battle-tested
6. ✅ Flexible - Supports complex queries later

**Alternative:** JSON files per block type (simpler, no deps, but slower for large datasets)

# Minecraft World Save Structure - Complete Technical Reference

**Created:** 2025-11-18
**Sources:** Minecraft Wiki, wiki.vg, extensive research
**Coverage:** Java Edition world save format (1.13-1.21+)

## Overview

This document provides authoritative specifications for Minecraft world save file structure, covering region files, entity storage, chunk format, dimension organization, and player data. Essential for implementing world parsers and book extraction tools.

---

## World Directory Structure

### Root Level Files and Folders

```
world_save/
├── level.dat                  # Global world data and settings
├── level.dat_old              # Backup of previous level.dat
├── session.lock               # Server lock file (prevents corruption)
├── uid.dat                    # Unique world identifier
├── data/                      # World-specific data files
│   ├── raids.dat              # Raid state
│   ├── scoreboard.dat         # Scoreboard data
│   └── villages.dat           # Village information (legacy)
├── playerdata/                # Player inventory and stats
│   ├── <uuid>.dat             # Individual player data
│   └── <uuid>.dat_old         # Player data backup
├── stats/                     # Player statistics
│   └── <uuid>.json            # Statistics JSON
├── advancements/              # Player advancements
│   └── <uuid>.json            # Advancement JSON
├── region/                    # Overworld terrain chunks
│   └── r.x.z.mca              # Region files (32×32 chunks each)
├── entities/                  # Overworld entities
│   └── r.x.z.mca              # Entity region files
├── poi/                       # Points of Interest (villages, beds, etc.)
│   └── r.x.z.mca              # POI region files
├── DIM-1/                     # Nether dimension
│   ├── region/
│   ├── entities/
│   └── poi/
├── DIM1/                      # The End dimension
│   ├── region/
│   ├── entities/
│   └── poi/
└── datapacks/                 # Custom datapacks
    └── <datapack_name>/
```

### Dimension Naming Convention

**Historical Evolution:**

| Minecraft Version | Overworld | Nether | The End |
|-------------------|-----------|--------|---------|
| Pre-1.16 | `./` | `DIM-1/` | `DIM1/` |
| 1.16+ | `./` | `DIM-1/` | `DIM1/` |

**Custom Dimensions (1.16+):**
- Format: `dimensions/<namespace>/<path>/`
- Example: `dimensions/minecraft/custom_dimension/`

---

## Region File Format (.mca)

### File Naming Convention

**Format:** `r.{regionX}.{regionZ}.mca`

**Coordinate Calculation:**
```
regionX = floor(chunkX / 32)
regionZ = floor(chunkZ / 32)

Example:
Chunk (100, 50) → Region (3, 1) → File: r.3.1.mca
Chunk (-32, -64) → Region (-1, -2) → File: r.-1.-2.mca
```

### File Structure Overview

**Total Size:** Variable (minimum 8 KiB)

**Components:**
1. **Location Table:** Bytes 0x0000–0x0FFF (4,096 bytes)
2. **Timestamp Table:** Bytes 0x1000–0x1FFF (4,096 bytes)
3. **Chunk Data:** Bytes 0x2000+ (variable length)

### Location Table (4 KiB)

**Purpose:** Maps chunk coordinates to file offsets

**Entry Count:** 1,024 entries (32 × 32 chunks)

**Entry Structure (4 bytes each):**
```
Bytes 0-2: Offset (24-bit big-endian integer)
  - Value in 4 KiB sectors from file start
  - Multiply by 4096 to get byte offset
  - 0x000000 = chunk doesn't exist

Byte 3: Sector count (8-bit unsigned)
  - Length of chunk data in 4 KiB sectors
  - Rounded up to next sector boundary
  - Maximum: 255 sectors = ~1 MB
```

**Index Calculation:**
```
localX = chunkX & 31  // chunkX modulo 32
localZ = chunkZ & 31  // chunkZ modulo 32
index = localX + localZ * 32

byteOffset = index * 4
```

**Example:**
```
Chunk (35, 18) in region (1, 0):
  localX = 35 & 31 = 3
  localZ = 18 & 31 = 18
  index = 3 + 18 * 32 = 579
  byteOffset = 579 * 4 = 2316 (0x090C)

Location entry at offset 0x090C:
  [0x00, 0x00, 0x40, 0x05]
  Offset: 0x000040 * 4096 = 262,144 bytes
  Sectors: 5 (20,480 bytes allocated)
```

### Timestamp Table (4 KiB)

**Purpose:** Last modification time for each chunk

**Entry Count:** 1,024 timestamps

**Entry Structure (4 bytes each):**
- 32-bit big-endian signed integer
- Unix epoch seconds
- 0 = chunk never modified (or very old)

**Index Calculation:**
```
Same as location table:
  index = (chunkX & 31) + (chunkZ & 31) * 32
  byteOffset = 0x1000 + (index * 4)
```

### Chunk Data Format

**Chunk Header (5+ bytes):**
```
Bytes 0-3: Length (32-bit big-endian)
  - Exact byte count of compressed data + compression type byte
  - Does NOT include this 4-byte length field itself
  - Does NOT include padding bytes

Byte 4: Compression type
  - 1 = GZip (RFC1952) - rarely used
  - 2 = Zlib (RFC1950) - standard
  - 3 = Uncompressed - available since 1.15.1 pre1
  - 4 = LZ4 - available since 24w04a (server.properties option)
  - 127 = Custom external algorithm (since 24w05a)

Bytes 5+: Compressed NBT data
  - Contains chunk NBT structure
  - Padded to 4 KiB boundary (padding not counted in length)
```

**Padding Requirements:**
- Chunk data padded to next 4,096-byte boundary
- Padding bytes NOT included in length field
- Padding typically zeros, but not guaranteed

**Maximum Chunk Size:**
- Theoretical: 255 sectors × 4,096 bytes = 1,044,480 bytes
- Practical: Much smaller (typically 10-100 KiB compressed)

### Compression Comparison

| Type | ID | Format | Speed | Ratio | Usage |
|------|---|----|-------|-------|-------|
| GZip | 1 | RFC1952 | Slow | Good | Deprecated |
| Zlib | 2 | RFC1950 | Medium | Good | Standard (99%+ worlds) |
| Uncompressed | 3 | None | Fast | None | Debugging/testing |
| LZ4 | 4 | LZ4 frame | Very Fast | Fair | Optional (server) |

### Free Space Management

**Sector Allocation:**
- Region file divided into 4 KiB sectors
- Sector 0: Location table
- Sector 1: Timestamp table
- Sectors 2+: Chunk data (sparse allocation)

**Fragmentation:**
- Deleted/relocated chunks leave gaps
- No automatic defragmentation
- Third-party tools can optimize

**File Growth:**
- Appends new chunks to end of file
- Can exceed theoretical max (1,024 sectors) if fragmented
- Typical size: 100 KiB - 5 MB per region

---

## Chunk NBT Structure

### Root Structure (Pre-1.18)

```snbt
{
  DataVersion: 2860,  // Minecraft data version number
  Level: {
    // All chunk data nested here
  }
}
```

### Root Structure (1.18+)

```snbt
{
  DataVersion: 2860,
  // Chunk data at root level (Level wrapper removed)
  xPos: 10,
  zPos: 20,
  yPos: -4,  // NEW in 1.18: lowest section Y position
  Status: "full",
  sections: [...],
  block_entities: [...],  // Renamed from TileEntities
  // ... other fields
}
```

### Key Version Changes (1.18)

**Removed:**
- `Level` wrapper compound (data promoted to root)
- `Biomes` array (replaced with per-section biomes)

**Renamed:**
- `TileEntities` → `block_entities`
- `Entities` → `entities` (later moved to separate files)
- `Heightmaps` capitalization changes

**Added:**
- `yPos`: Lowest section Y coordinate
- `sections[].biomes`: Biome data per section
- Extended height support (-64 to 320)

### Sections Array

**Purpose:** Stores 16×16×16 sub-chunks of blocks

**Pre-1.18 Structure:**
```snbt
{
  sections: [
    {
      Y: 0b,  // Section Y coordinate (0-15 for old height)
      block_states: {
        palette: [...],  // Block ID palette
        data: [...]      // Packed block indices
      },
      BlockLight: [...],
      SkyLight: [...]
    }
  ]
}
```

**1.18+ Structure:**
```snbt
{
  sections: [
    {
      Y: -4b,  // Extended range: -4 to 19 (for -64 to 320 height)
      block_states: {
        palette: [...],
        data: [...]
      },
      biomes: {  // NEW: Per-section biomes
        palette: [...],
        data: [...]
      },
      BlockLight: [...],
      SkyLight: [...]
    }
  ]
}
```

**Empty Sections:**
- Missing sections = all air
- Not stored to save space
- Fully present in 1.18+ (even if air)

### Block Entities Array

**Pre-1.18 Field Name:** `TileEntities`
**1.18+ Field Name:** `block_entities`

**Structure:**
```snbt
{
  block_entities: [
    {
      id: "minecraft:chest",  // Block entity type
      x: 100,                 // Absolute world coordinates
      y: 64,
      z: 200,
      Items: [...]           // Container inventory
      // ... entity-specific fields
    }
  ]
}
```

**Common Block Entity Types:**
- Chests, barrels, shulker boxes
- Hoppers, dispensers, droppers
- Furnaces (all variants)
- Lecterns, brewing stands
- Chiseled bookshelves
- Signs (all types)

### Entities Array (Pre-1.17)

**Pre-1.17:** Entities stored in chunk NBT
**1.17+:** Entities moved to separate entity files

**Legacy Structure:**
```snbt
{
  Entities: [
    {
      id: "minecraft:item_frame",
      Pos: [100.5d, 64.0d, 200.5d],
      Item: {...}
      // ... entity-specific fields
    }
  ]
}
```

---

## Entity Files (1.17+)

### Directory Structure

```
world/
├── entities/          # Overworld entities
│   └── r.x.z.mca
├── DIM-1/entities/    # Nether entities
└── DIM1/entities/     # End entities
```

### Entity Region Format

**File Structure:** Identical to chunk region files
- Same header format (location + timestamp tables)
- Same compression schemes
- Same sector allocation

**Chunk → Entity Mapping:**
- One entity chunk per terrain chunk
- Same coordinate system
- Same r.x.z.mca naming

**Entity Chunk NBT:**
```snbt
{
  DataVersion: 2860,
  Position: [10, 20],  // Chunk coordinates (X, Z)
  Entities: [
    {
      id: "minecraft:chest_minecart",
      UUID: [...],
      Pos: [...]
      Items: [...]
      // ... entity data
    }
  ]
}
```

---

## Player Data Format

### File Location

**Singleplayer:** `level.dat` (inline player data)
**Multiplayer:** `playerdata/<UUID>.dat`

**UUID Format:** Hyphenated lowercase hex
- Example: `069a79f4-44e9-4726-a5be-fca90e38aaf5.dat`

### Player NBT Structure

```snbt
{
  DataVersion: 2860,

  // Inventory System
  Inventory: [
    {
      Slot: 0b,  // -106 to 103 (various slots)
      id: "minecraft:written_book",
      Count: 1b,
      tag: {...}
    }
  ],

  // Ender Chest (player-specific)
  EnderItems: [
    {
      Slot: 0b,  // 0-26 (27 slots)
      id: "minecraft:written_book",
      Count: 1b,
      tag: {...}
    }
  ],

  // Position and Dimension
  Pos: [100.5d, 64.0d, 200.5d],
  Dimension: "minecraft:overworld",  // or "minecraft:the_nether", "minecraft:the_end"

  // Player Stats
  Health: 20.0f,
  foodLevel: 20,
  XpLevel: 30,

  // Selected Item
  SelectedItemSlot: 0,  // 0-8 (hotbar)

  // Game Mode
  playerGameType: 0,  // 0=Survival, 1=Creative, 2=Adventure, 3=Spectator

  // Misc
  Score: 0,
  abilities: {...},
  recipeBook: {...}
}
```

### Inventory Slot Mapping

**Slot Numbers:**
```
-106: Off-hand slot
0-8: Hotbar (bottom row)
9-35: Main inventory (3 rows above hotbar)
100: Boots
101: Leggings
102: Chestplate
103: Helmet
```

**Crafting Slots (transient):**
```
-80 to -77: 2×2 crafting grid
-1: Crafting output
```

---

## Level.dat Format

### File Structure

**Compression:** GZIP compressed NBT
**Root Tag:** Unnamed compound

**Top-Level Structure:**
```snbt
{
  Data: {
    // All world data here
  }
}
```

### Data Compound Fields

**World Identity:**
```snbt
{
  LevelName: "My World",
  LastPlayed: 1234567890L,  // Unix timestamp (ms)
  version: {
    Id: 2860,               // Data version
    Name: "1.18.1",         // Version string
    Series: "main",
    Snapshot: 0b
  }
}
```

**World Settings:**
```snbt
{
  GameType: 0,              // Default game mode
  Difficulty: 2,            // 0=Peaceful, 1=Easy, 2=Normal, 3=Hard
  hardcore: 0b,
  allowCommands: 1b,

  // World Generation
  WorldGenSettings: {
    seed: -1234567890L,     // World seed
    generate_features: 1b,  // Structures
    bonus_chest: 0b,
    dimensions: {...}       // Per-dimension settings
  }
}
```

**Spawn Location:**
```snbt
{
  SpawnX: 0,
  SpawnY: 64,
  SpawnZ: 0,
  SpawnAngle: 0.0f
}
```

**Singleplayer Player Data:**
```snbt
{
  Player: {
    // Same structure as playerdata/<uuid>.dat
    Inventory: [...],
    EnderItems: [...],
    Pos: [...]
    // ... all player fields
  }
}
```

**World Rules:**
```snbt
{
  GameRules: {
    doMobSpawning: "true",
    keepInventory: "false",
    doDaylightCycle: "true",
    // ... 50+ game rules
  }
}
```

---

## POI (Points of Interest) Files

### Purpose

Stores village, bed, and job site locations for performance optimization.

### Directory Structure

```
world/
├── poi/          # Overworld POI
│   └── r.x.z.mca
├── DIM-1/poi/    # Nether POI
└── DIM1/poi/     # End POI (rarely used)
```

### POI Region Format

**Structure:** Same as chunk/entity regions (8 KiB header + data)

**POI Chunk NBT:**
```snbt
{
  DataVersion: 2860,
  Sections: {
    "0": {  // Section Y coordinate (string key)
      Records: [
        {
          pos: [100, 64, 200],  // Block position
          type: "minecraft:bed",
          free_tickets: 1,
          // ... type-specific data
        }
      ],
      Valid: 1b
    }
  }
}
```

**POI Types:**
- `minecraft:bed` - Player spawn points
- `minecraft:armorer` - Job site blocks
- `minecraft:meeting` - Village meeting points
- 30+ professions and special locations

---

## Data Folder Contents

### data/ Directory

```
data/
├── raids.dat              # Active raid state
├── scoreboard.dat         # Scoreboard objectives and teams
├── random_sequences.dat   # Random number generator state (1.19.3+)
├── villages.dat           # Legacy village data (removed 1.14)
├── idcounts.dat           # Map ID counter
├── map_<id>.dat           # Individual map data
└── command_storage_<namespace>.dat  # Command storage data
```

### Map Files

**File Naming:** `map_<id>.dat`
**ID Range:** 0 to 2,147,483,647

**Map NBT Structure:**
```snbt
{
  data: {
    scale: 0b,            // Zoom level (0-4)
    dimension: "minecraft:overworld",
    xCenter: 100,
    zCenter: 200,
    colors: [...]         // 128×128 color array
    banners: [...],       // Map markers
    frames: [...]         // Item frame locations
  }
}
```

---

## File Access Patterns for Book Extraction

### Required Files

**Minimum Set:**
1. `region/*.mca` - Block entities (chests, lecterns)
2. `entities/*.mca` - Entities (item frames, minecarts)
3. `playerdata/*.dat` - Player inventories
4. `level.dat` - Singleplayer player data

**Optional:**
- `DIM-1/region/*.mca`, `DIM-1/entities/*.mca` - Nether books
- `DIM1/region/*.mca`, `DIM1/entities/*.mca` - End books

### Extraction Order

**Recommended Sequence:**
1. Read `level.dat` for world version detection
2. Scan `playerdata/*.dat` files (parallel if multi-player)
3. Scan `region/*.mca` files (sequential or parallel)
4. Scan `entities/*.mca` files (sequential or parallel)
5. Repeat for dimensions if needed

### Progress Tracking

**File Counting:**
```java
int totalFiles = 0;
totalFiles += countFiles("playerdata/*.dat");
totalFiles += countFiles("region/*.mca");
totalFiles += countFiles("entities/*.mca");
// Repeat for dimensions
```

**Chunk-Level Progress:**
```java
// For each region file:
int chunksProcessed = 0;
for (int x = 0; x < 32; x++) {
    for (int z = 0; z < 32; z++) {
        if (regionFile.hasChunk(x, z)) {
            processChunk(regionFile.getChunk(x, z));
            chunksProcessed++;
            updateProgress(chunksProcessed, 1024);
        }
    }
}
```

---

## Version Detection

### DataVersion Mapping

**Critical Versions:**

| DataVersion | Minecraft Version | Key Changes |
|-------------|-------------------|-------------|
| 1519 | 1.13 | Flattening |
| 1976 | 1.14.4 | Village & Pillage |
| 2566 | 1.16.2 | Nether Update |
| 2724 | 1.17 | Caves & Cliffs Pt. 1 |
| 2860 | 1.18.1 | Caves & Cliffs Pt. 2 (world height) |
| 3105 | 1.19.3 | Chat signing |
| 3463 | 1.20 | Trails & Tales |
| 3700 | 1.20.5 | Data components |
| 3837 | 1.21 | Tricky Trials |

### Format Detection Code

```java
CompoundTag levelDat = NBTUtil.read(new File(worldDir, "level.dat"));
CompoundTag data = levelDat.getCompoundTag("Data");
int dataVersion = data.getInt("DataVersion");

if (dataVersion >= 3700) {
    // 1.20.5+ : Use data components
} else if (dataVersion >= 2860) {
    // 1.18+ : Use new chunk format
} else if (dataVersion >= 1519) {
    // 1.13+ : Use flattened IDs
} else {
    // Pre-1.13 : Unsupported
}
```

---

## Corruption and Error Handling

### Common Corruption Types

**Missing Chunks:**
- Location table entry = 0x00000000
- Skip gracefully, log warning

**Invalid Offsets:**
- Offset beyond file size
- Skip chunk, log error

**Decompression Failures:**
- Corrupted compressed data
- Try alternative compression schemes
- Skip on complete failure

**Invalid NBT:**
- Malformed NBT structure
- Catch exceptions, skip chunk
- Log detailed error for debugging

### Recovery Strategies

**Level.dat Corruption:**
```java
File levelDat = new File(worldDir, "level.dat");
File levelDatOld = new File(worldDir, "level.dat_old");

try {
    return NBTUtil.read(levelDat);
} catch (Exception e) {
    LOGGER.warn("level.dat corrupted, trying backup");
    return NBTUtil.read(levelDatOld);
}
```

**Chunk Corruption:**
```java
try {
    Chunk chunk = region.getChunk(x, z);
    processChunk(chunk);
} catch (Exception e) {
    LOGGER.error("Chunk ({}, {}) corrupted: {}", x, z, e.getMessage());
    corruptedChunks++;
    continue; // Skip to next chunk
}
```

**Entity File Corruption:**
```java
// Entity files less critical than terrain
try {
    processEntityFile(entityFile);
} catch (Exception e) {
    LOGGER.warn("Entity file {} corrupted, skipping", entityFile.getName());
    // Continue extraction (entities recoverable)
}
```

---

## Performance Optimization

### Memory Management

**Chunk Streaming:**
- Process one region file at a time
- Don't load all chunks into memory
- Clear processed chunks immediately

**NBT Caching:**
- Cache parsed level.dat (read once)
- Don't cache chunk NBT (too large)
- Cache region file headers only

### Parallel Processing

**Thread-Safe Regions:**
```java
// Region files independent, safe to parallelize
List<File> regionFiles = getRegionFiles(worldDir);
ExecutorService executor = Executors.newFixedThreadPool(4);

for (File regionFile : regionFiles) {
    executor.submit(() -> processRegion(regionFile));
}

executor.shutdown();
executor.awaitTermination(1, TimeUnit.HOURS);
```

**NOT Thread-Safe:**
- Writing to same output file
- Shared deduplication sets
- Progress counters

**Solution:**
```java
// Thread-local collections, merge after
ConcurrentHashMap<Integer, Book> books = new ConcurrentHashMap<>();

// OR: Synchronized access
synchronized(bookSet) {
    bookSet.add(book);
}
```

---

## Testing World Validation

### Minimal Valid World

**Required Files:**
```
world/
├── level.dat           # With Data.version
├── region/
│   └── r.0.0.mca       # At least one region
└── session.lock        # Can be empty
```

**Optional but Recommended:**
```
world/
├── level.dat_old       # For recovery testing
├── playerdata/
│   └── <uuid>.dat      # For inventory testing
└── entities/
    └── r.0.0.mca       # For entity testing
```

### Test World Contents

**Comprehensive Test Set:**
- Books in all container types (30+)
- Books in nested containers (3+ levels)
- Books in all dimensions
- Empty containers (test skipping)
- Corrupted chunks (error handling)
- Missing chunks (sparse regions)
- Old format (pre-1.18) compatibility
- New format (1.20.5+) components

---

## References

- Minecraft Wiki - Region File Format: https://minecraft.wiki/w/Region_file_format
- Minecraft Wiki - Chunk Format: https://minecraft.wiki/w/Chunk_format
- Minecraft Wiki - Entity Format: https://minecraft.wiki/w/Entity_format
- Minecraft Wiki - Level Format: https://minecraft.wiki/w/Java_Edition_level_format
- Minecraft Wiki - Player.dat Format: https://minecraft.wiki/w/Player.dat_format
- wiki.vg - Map Format: https://wiki.vg/Map_Format

**Document Version:** 1.0
**Last Updated:** 2025-11-18
**Minecraft Coverage:** Java Edition 1.13 through 1.21+

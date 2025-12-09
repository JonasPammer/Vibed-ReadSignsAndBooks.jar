# Minecraft Java Edition Chunk Format & Block Storage
## Deep Research Report (Versions 1.18-1.21+)

**Research Date:** 2025-12-07
**Versions Covered:** Minecraft Java Edition 1.18 through 1.21+
**Primary Source:** [minecraft.wiki](https://minecraft.wiki)

---

## Table of Contents

1. [Region File Format](#region-file-format)
2. [Chunk NBT Structure](#chunk-nbt-structure)
3. [Block Storage System](#block-storage-system)
4. [Coordinate Systems & Transformations](#coordinate-systems--transformations)
5. [Dimension Data](#dimension-data)
6. [Nether Portal Technical Details](#nether-portal-technical-details)
7. [Version-Specific Changes](#version-specific-changes)
8. [Code Implementation Guide](#code-implementation-guide)

---

## Region File Format

### File Structure

Region files (`.mca`) use a binary format with an 8 KiB header divided into two 4 KiB tables:

- **Bytes 0x0000-0x0FFF:** Chunk location table (1024 entries × 4 bytes)
- **Bytes 0x1000-0x1FFF:** Timestamp table (1024 entries × 4 bytes)
- **Bytes 0x2000+:** Chunk data and unused space

**Reference:** [Region File Format - Minecraft Wiki](https://minecraft.wiki/w/Region_file_format)

### Chunk Location Table

Each chunk location consists of 4 bytes:
- **Bytes 0-2:** Big-endian offset (in 4 KiB sectors from file start)
- **Byte 3:** Chunk length in 4 KiB sectors (rounded up)

**Formula for table index:**
```
index = (x & 31) + (z & 31) * 32
byte_offset = index * 4
```

### Chunk Data Format

Each chunk begins with:
1. **4 bytes:** Length (big-endian)
2. **1 byte:** Compression type
3. **N bytes:** Compressed NBT data

### Compression Methods

| ID | Method | Usage |
|----|--------|-------|
| 1 | GZip (RFC1952) | Unused |
| 2 | Zlib (RFC1950) | Official default |
| 3 | Uncompressed | Rare |
| 4 | LZ4 | Supported |
| 127 | Custom | Third-party servers |

---

## Chunk NBT Structure

### Root Level Tags (Post-1.18)

The 1.18 update **removed the `Level` container** and promoted all fields to root level:

```
ROOT (TAG_Compound)
├─ DataVersion [Int]: Format version identifier
├─ xPos [Int]: Chunk X position (in chunks, not blocks)
├─ zPos [Int]: Chunk Z position (in chunks, not blocks)
├─ yPos [Int]: Lowest section Y coordinate (e.g., -4 in 1.18+)
├─ Status [String]: Generation stage ("full", "features", etc.)
├─ LastUpdate [Long]: Last save tick
├─ InhabitedTime [Long]: Player presence ticks
├─ sections [NBT List]: Chunk sections (16×16×16 blocks each)
├─ block_entities [NBT List]: Block entity data (chests, signs, etc.)
├─ block_ticks [NBT List]: Scheduled block updates
├─ fluid_ticks [NBT List]: Scheduled fluid updates
└─ structures [NBT Compound]: Structure metadata
```

**Reference:** [Chunk Format - Minecraft Wiki](https://minecraft.wiki/w/Chunk_format)

### Pre-1.18 Structure (Legacy)

Prior to 1.18, chunk data was nested under a `Level` tag:

```
ROOT (TAG_Compound)
└─ Level (TAG_Compound)
   ├─ xPos, zPos [Int]
   ├─ Sections [NBT List]
   ├─ TileEntities [NBT List]  (now block_entities)
   └─ Entities [NBT List]
```

### Chunk Section Structure

Each section in the `sections` list contains:

```
Section (TAG_Compound)
├─ Y [Byte]: Section Y-coordinate
├─ block_states (TAG_Compound):
│  ├─ palette [NBT List]: Block state definitions
│  │  └─ Entry (TAG_Compound):
│  │     ├─ Name [String]: Block ID (e.g., "minecraft:nether_portal")
│  │     └─ Properties [TAG_Compound]: Block states (optional)
│  │        └─ axis [String]: "x" or "z" (for portals)
│  └─ data [Long Array]: Packed block indices (if palette > 1)
└─ biomes (TAG_Compound):
   ├─ palette [NBT List]: Biome definitions
   └─ data [Long Array]: Packed biome indices
```

**Key Points:**
- Sections span Y levels from `yPos` (typically -4) to `yPos + 23` (319 in Overworld)
- Each section stores **4,096 blocks** (16 × 16 × 16)
- If only one block state exists, `data` field is omitted

---

## Block Storage System

### Block Palette System

The palette stores unique block states for efficient storage:

```json
palette: [
  {
    Name: "minecraft:air"
  },
  {
    Name: "minecraft:obsidian"
  },
  {
    Name: "minecraft:nether_portal",
    Properties: {
      axis: "z"
    }
  }
]
```

### Block Data Array (Bit-Packed Long Array)

The `data` field stores indices into the palette using **bit-packing**:

**Bits per entry calculation:**
```
bits_per_entry = max(4, ceil(log2(palette_size)))
```

**Examples:**
- Palette size 1: No `data` array (all blocks same)
- Palette size 2-15: 4 bits per entry
- Palette size 16-31: 5 bits per entry
- Palette size 32-63: 6 bits per entry
- Palette size 256+: 8 bits per entry

**Reference:** [Java Edition Protocol/Chunk Format - Minecraft Wiki](https://minecraft.wiki/w/Java_Edition_protocol/Chunk_format)

### Bit-Packing Format (1.16+)

Entries are **tightly packed within longs** with **no cross-long spanning** (changed in 1.16):

```
Example: 6 bits per entry (palette size 47)
Each long stores 10 entries (10 × 6 = 60 bits, 4 bits padding)

Long value: 18300411153223745
Binary: 0000 000001 000001 000001 000010 000010 000010 000010 000001 000001 000001
        ↑    ↑                                                                  ↑
      Padding                                                            First entry (LSB)
```

**Key properties:**
- Entries stored **least significant bit first**
- No entry spans multiple longs (padding inserted)
- Minimum 4 bits per entry, even for palette size 2

### Block Index to Coordinates

Blocks within a section are ordered **YZX** (for compression):

```
Linear index = Y × 256 + Z × 16 + X
```

Where:
- **X, Y, Z:** Block coordinates within section (0-15)
- **Linear index:** Position in data array (0-4095)

**Reference:** [Chunk Format Discussion - Quarry Documentation](https://quarry.readthedocs.io/en/latest/data_types/chunks.html)

**Example:**
```
Block at (x=5, y=3, z=7) within section:
index = 3 × 256 + 7 × 16 + 5 = 768 + 112 + 5 = 885
```

---

## Coordinate Systems & Transformations

### Chunk Coordinates vs World Coordinates

**Chunk to World Coordinates:**
```
World X = xPos × 16  (northwest corner)
World Z = zPos × 16  (northwest corner)
```

**World to Chunk Coordinates:**
```
Chunk X = floor(World X / 16)
Chunk Z = floor(World Z / 16)
```

**Reference:** [Chunk Coordinate Calculator - Minecraft Wiki](https://minecraft.wiki/w/Calculators/Chunk_coordinates)

### Block Coordinates Within Chunk

To find block coordinates within a chunk:

```
Block X in chunk = World X mod 16
Block Z in chunk = World Z mod 16
```

**Example:**
```
World coordinates: (27, 64, -15)

Chunk coordinates:
  X = floor(27 / 16) = 1
  Z = floor(-15 / 16) = -1
  Chunk: (1, -1)

Block position within chunk:
  X = 27 mod 16 = 11
  Z = -15 mod 16 = 1
  Position: (11, 64, 1) within chunk
```

### Section Y-Level Calculation

Given a world Y coordinate:

```
Section Y = floor((World Y - world_min_y) / 16)
```

For 1.18+ Overworld:
- `world_min_y = -64`
- Y range: -64 to 319
- Section Y range: -4 to 19 (24 sections total)

**Block Y within section:**
```
Block Y in section = (World Y - world_min_y) mod 16
```

---

## Dimension Data

### Dimension Folder Locations

Each dimension stores region files in separate directories:

| Dimension | Folder Path | World Height |
|-----------|-------------|--------------|
| **Overworld** | `saves/[world]/region/` | -64 to 319 (384 blocks) |
| **Nether** | `saves/[world]/DIM-1/region/` | 0 to 255 (256 blocks) |
| **The End** | `saves/[world]/DIM1/region/` | 0 to 255 (256 blocks) |

**Mnemonic:**
- DIM**-1** = Nether (below Overworld, negative)
- DIM**1** = End (above Overworld, positive)

**Reference:** [Dimension Folders - PiglinHost KB](https://billing.piglinhost.com/index.php?rp=/knowledgebase/25/Where-is-the-Nether-and-End-folders-located-DIM1-vs-DIM-1.html)

### Region File Naming

Region files use the format:
```
r.X.Z.mca
```

Where X and Z are region coordinates (each region = 32 × 32 chunks).

**Example:**
- `r.0.0.mca` - Contains chunks (0,0) through (31,31)
- `r.-1.2.mca` - Contains chunks (-32,64) through (-1,95)

---

## Nether Portal Technical Details

### Block Identification

**Nether portals consist of TWO block types:**

1. **Obsidian blocks** (`minecraft:obsidian`)
   - Forms the rectangular frame
   - Standard block, no special properties
   - No NBT data

2. **Nether portal blocks** (`minecraft:nether_portal`)
   - The purple portal material inside frame
   - **Cannot be obtained as item** in Java Edition
   - **Not a block entity** (no NBT data beyond block state)

**Reference:** [Nether Portal Block - Minecraft Wiki](https://minecraft.wiki/w/Nether_Portal_(block))

### Block State Properties

**Java Edition:**
```json
{
  Name: "minecraft:nether_portal",
  Properties: {
    axis: "x"  // or "z"
  }
}
```

**Axis values:**
- `"x"` - Portal's long edge runs **east-west**
- `"z"` - Portal's long edge runs **north-south**

**Note:** Unlike most axis properties (which accept `x`, `y`, `z`), nether portals **only support `x` and `z`**.

**Reference:** [Block States - Minecraft Wiki](https://minecraft.wiki/w/Block_states)

### Portal Dimensions

- **Minimum:** 4×5 blocks (frame inclusive)
- **Maximum:** 23×23 blocks (frame inclusive)
- Frame provides "4 free/extra obsidian" (corners)

**Reference:** [Nether Portal - Minecraft Wiki](https://minecraft.wiki/w/Nether_portal)

### Detecting Portal Frames

**Challenge:** Obsidian blocks in a portal frame are **indistinguishable** from regular obsidian in NBT data.

**Detection strategies:**

1. **Find portal blocks** (`minecraft:nether_portal`)
2. **Search adjacent blocks** for obsidian within 23-block radius
3. **Validate frame structure** (rectangular, min 4×5, max 23×23)

**Pseudocode:**
```python
def find_portal_frames(chunk_data):
    portal_blocks = find_blocks_by_id(chunk_data, "minecraft:nether_portal")

    for portal in portal_blocks:
        # Scan adjacent blocks for obsidian
        frame_obsidian = []
        for offset in range(-23, 24):
            adjacent = get_block(portal.x + offset, portal.y, portal.z)
            if adjacent.id == "minecraft:obsidian":
                frame_obsidian.append(adjacent)

        # Validate rectangular structure
        if is_valid_portal_frame(frame_obsidian):
            yield PortalFrame(blocks=frame_obsidian, axis=portal.axis)
```

---

## Version-Specific Changes

### Minecraft 1.18 (Caves & Cliffs Part II)

**Major chunk format restructuring:**

**Removed `Level` container:**
```
Before:  ROOT → Level → xPos, zPos, Sections, TileEntities, Entities
After:   ROOT → xPos, zPos, sections, block_entities, entities
```

**Path changes:**
- `Level.Sections[].BlockStates` → `sections[].block_states.data`
- `Level.TileEntities` → `block_entities`
- `Level.Entities` → `entities`
- `Level.Biomes` → `sections[].biomes.palette` (per-section biomes)

**New fields:**
- `yPos` - Minimum section Y coordinate
- `below_zero_retrogen` - Negative Y compatibility
- `blending_data` - Chunk transition data

**World height expansion:**
- Overworld: -64 to 319 (384 blocks, was 256)
- Sections: 24 total (was 16)

**Reference:** [Java Edition 1.18 - Minecraft Wiki](https://minecraft.wiki/w/Java_Edition_1.18)

### Minecraft 1.20.2 (23w32a)

**Block entity changes:**
- Beacon: `Primary` → `primary_effect`, `Secondary` → `secondary_effect`
- Mob effects: Numeric IDs → String identifiers

### Minecraft 1.21 (24w21a)

**Entity attribute changes:**
- `Attributes` → `attributes`
- `Name` → `id`
- `Base` → `base`
- Modifier operations: Integers (0-2) → Strings (`"add_value"`, `"add_multiplied_base"`, `"add_multiplied_total"`)

### Minecraft 1.21.2 (24w39a)

**Container lock restructure:**
- `Lock` field now uses item predicate compound

### Minecraft 1.21.5 (25w07a)

**Field pruning:**
- `CustomName`, `LootTable`, `exit_portal`, `RecipesUsed`, `note_block_sound` no longer preserved when removed

**Reference:** [Chunk Format - Minecraft Wiki](https://minecraft.wiki/w/Chunk_format)

---

## Code Implementation Guide

### 1. Reading Region Files

```java
import net.querz.nbt.io.NBTUtil;
import net.querz.nbt.tag.CompoundTag;

public class RegionReader {
    public CompoundTag readChunk(File regionFile, int chunkX, int chunkZ) {
        // Calculate chunk position within region (0-31 for both X and Z)
        int localX = chunkX & 31;
        int localZ = chunkZ & 31;
        int index = localX + localZ * 32;

        try (RandomAccessFile raf = new RandomAccessFile(regionFile, "r")) {
            // Read location table entry
            raf.seek(index * 4);
            int offset = raf.read() << 16 | raf.read() << 8 | raf.read();
            int sectorCount = raf.read();

            if (offset == 0 && sectorCount == 0) {
                return null; // Chunk not generated
            }

            // Read chunk data
            raf.seek(offset * 4096);
            int length = raf.readInt();
            int compressionType = raf.read();

            byte[] data = new byte[length - 1];
            raf.readFully(data);

            // Decompress and parse NBT
            return NBTUtil.read(new ByteArrayInputStream(data), compressionType == 2);
        }
    }
}
```

### 2. Iterating Blocks in a Chunk

```java
public class BlockIterator {
    public void iterateBlocks(CompoundTag chunkData) {
        int chunkX = chunkData.getInt("xPos");
        int chunkZ = chunkData.getInt("zPos");
        ListTag<CompoundTag> sections = chunkData.getListTag("sections").asCompoundTagList();

        for (CompoundTag section : sections) {
            int sectionY = section.getByte("Y");

            CompoundTag blockStates = section.getCompoundTag("block_states");
            if (blockStates == null) continue;

            ListTag<?> palette = blockStates.getListTag("palette");
            LongArrayTag dataArray = blockStates.getLongArrayTag("data");

            // Calculate bits per entry
            int paletteSize = palette.size();
            if (paletteSize == 1) {
                // All blocks are the same
                CompoundTag block = (CompoundTag) palette.get(0);
                processSingleBlock(block, chunkX, sectionY, chunkZ);
                continue;
            }

            int bitsPerEntry = Math.max(4, (int) Math.ceil(Math.log(paletteSize) / Math.log(2)));
            long[] data = dataArray.getValue();

            // Iterate all 4096 blocks in section
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int index = y * 256 + z * 16 + x;
                        int paletteIndex = extractPaletteIndex(data, index, bitsPerEntry);

                        CompoundTag block = (CompoundTag) palette.get(paletteIndex);

                        int worldX = chunkX * 16 + x;
                        int worldY = sectionY * 16 + y;
                        int worldZ = chunkZ * 16 + z;

                        processBlock(block, worldX, worldY, worldZ);
                    }
                }
            }
        }
    }

    private int extractPaletteIndex(long[] data, int blockIndex, int bitsPerEntry) {
        int entriesPerLong = 64 / bitsPerEntry;
        int longIndex = blockIndex / entriesPerLong;
        int entryIndex = blockIndex % entriesPerLong;

        long value = data[longIndex];
        int shift = entryIndex * bitsPerEntry;
        long mask = (1L << bitsPerEntry) - 1;

        return (int) ((value >> shift) & mask);
    }

    private void processBlock(CompoundTag block, int x, int y, int z) {
        String blockId = block.getString("Name");
        CompoundTag properties = block.getCompoundTag("Properties");

        System.out.printf("Block at (%d, %d, %d): %s%n", x, y, z, blockId);

        if (properties != null) {
            System.out.println("  Properties: " + properties);
        }
    }
}
```

### 3. Finding Nether Portals

```java
public class PortalFinder {
    public List<Portal> findPortals(CompoundTag chunkData) {
        List<Portal> portals = new ArrayList<>();
        int chunkX = chunkData.getInt("xPos");
        int chunkZ = chunkData.getInt("zPos");
        ListTag<CompoundTag> sections = chunkData.getListTag("sections").asCompoundTagList();

        for (CompoundTag section : sections) {
            int sectionY = section.getByte("Y");
            CompoundTag blockStates = section.getCompoundTag("block_states");
            if (blockStates == null) continue;

            ListTag<?> palette = blockStates.getListTag("palette");
            LongArrayTag dataArray = blockStates.getLongArrayTag("data");

            // Check if palette contains nether_portal
            int portalPaletteIndex = -1;
            for (int i = 0; i < palette.size(); i++) {
                CompoundTag block = (CompoundTag) palette.get(i);
                if ("minecraft:nether_portal".equals(block.getString("Name"))) {
                    portalPaletteIndex = i;
                    break;
                }
            }

            if (portalPaletteIndex == -1) continue; // No portals in this section

            // Find all portal blocks
            int paletteSize = palette.size();
            int bitsPerEntry = Math.max(4, (int) Math.ceil(Math.log(paletteSize) / Math.log(2)));
            long[] data = dataArray != null ? dataArray.getValue() : new long[0];

            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int index = y * 256 + z * 16 + x;
                        int paletteIndex = extractPaletteIndex(data, index, bitsPerEntry);

                        if (paletteIndex == portalPaletteIndex) {
                            CompoundTag block = (CompoundTag) palette.get(paletteIndex);
                            CompoundTag properties = block.getCompoundTag("Properties");
                            String axis = properties != null ? properties.getString("axis") : "z";

                            int worldX = chunkX * 16 + x;
                            int worldY = sectionY * 16 + y;
                            int worldZ = chunkZ * 16 + z;

                            portals.add(new Portal(worldX, worldY, worldZ, axis));
                        }
                    }
                }
            }
        }

        return portals;
    }

    private int extractPaletteIndex(long[] data, int blockIndex, int bitsPerEntry) {
        if (data.length == 0) return 0; // Single-palette case

        int entriesPerLong = 64 / bitsPerEntry;
        int longIndex = blockIndex / entriesPerLong;
        int entryIndex = blockIndex % entriesPerLong;

        long value = data[longIndex];
        int shift = entryIndex * bitsPerEntry;
        long mask = (1L << bitsPerEntry) - 1;

        return (int) ((value >> shift) & mask);
    }

    static class Portal {
        int x, y, z;
        String axis;

        Portal(int x, int y, int z, String axis) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.axis = axis;
        }
    }
}
```

### 4. Processing All Dimensions

```java
public class WorldScanner {
    public void scanAllDimensions(File worldFolder) {
        // Overworld
        File overworldRegion = new File(worldFolder, "region");
        if (overworldRegion.exists()) {
            scanDimension(overworldRegion, "Overworld");
        }

        // Nether (DIM-1)
        File netherRegion = new File(worldFolder, "DIM-1/region");
        if (netherRegion.exists()) {
            scanDimension(netherRegion, "Nether");
        }

        // The End (DIM1)
        File endRegion = new File(worldFolder, "DIM1/region");
        if (endRegion.exists()) {
            scanDimension(endRegion, "The End");
        }
    }

    private void scanDimension(File regionFolder, String dimensionName) {
        System.out.println("Scanning dimension: " + dimensionName);

        for (File regionFile : regionFolder.listFiles((dir, name) -> name.endsWith(".mca"))) {
            String[] parts = regionFile.getName().replace(".mca", "").split("\\.");
            int regionX = Integer.parseInt(parts[1]);
            int regionZ = Integer.parseInt(parts[2]);

            System.out.printf("  Processing region r.%d.%d.mca%n", regionX, regionZ);

            // Each region contains 32×32 chunks
            for (int chunkX = 0; chunkX < 32; chunkX++) {
                for (int chunkZ = 0; chunkZ < 32; chunkZ++) {
                    int absChunkX = regionX * 32 + chunkX;
                    int absChunkZ = regionZ * 32 + chunkZ;

                    CompoundTag chunkData = readChunk(regionFile, absChunkX, absChunkZ);
                    if (chunkData != null) {
                        processChunk(chunkData);
                    }
                }
            }
        }
    }
}
```

---

## Summary & Key Takeaways

### Essential Knowledge

1. **Chunk format changed significantly in 1.18** - `Level` container removed, all fields promoted to root
2. **Block storage uses palette + bit-packed indices** - efficient compression for repeated blocks
3. **YZX ordering** - Blocks indexed as `Y × 256 + Z × 16 + X` within sections
4. **Coordinate transformations** - Chunk coordinates × 16 = World coordinates
5. **Dimension folders** - Overworld (`region/`), Nether (`DIM-1/region/`), End (`DIM1/region/`)
6. **Nether portals** - Two block types (obsidian frame + nether_portal blocks), axis property (`x` or `z`)
7. **No NBT for portal blocks** - Only block state (axis), no block entity data

### Common Pitfalls

- **Don't assume `Level` tag exists** - Only in pre-1.18 worlds
- **Handle single-palette sections** - `data` array may be absent
- **Respect bit-packing alignment** - Entries don't span longs (post-1.16)
- **Use floor division for negative coordinates** - `floor(x / 16)`, not `x / 16`
- **Check palette indices carefully** - Off-by-one errors common in extraction

### Performance Considerations

- **Cache palette lookups** - Palette size typically small (< 100 entries)
- **Use parallel processing** - Region files independent, can process concurrently
- **Skip air-only sections** - Check palette size before iterating
- **Batch NBT reads** - Minimize file I/O by reading multiple chunks per region file

---

## Sources & References

All information verified from official Minecraft Wiki and community resources:

### Primary Sources
- [Chunk Format - Minecraft Wiki](https://minecraft.wiki/w/Chunk_format)
- [Region File Format - Minecraft Wiki](https://minecraft.wiki/w/Region_file_format)
- [NBT Format - Minecraft Wiki](https://minecraft.wiki/w/NBT_format)
- [Block States - Minecraft Wiki](https://minecraft.wiki/w/Block_states)
- [Nether Portal Block - Minecraft Wiki](https://minecraft.wiki/w/Nether_Portal_(block))
- [Java Edition Protocol/Chunk Format - Minecraft Wiki](https://minecraft.wiki/w/Java_Edition_protocol/Chunk_format)

### Version Changes
- [Java Edition 1.18 - Minecraft Wiki](https://minecraft.wiki/w/Java_Edition_1.18)

### Community Resources
- [Chunk Coordinate Calculator - Minecraft Wiki](https://minecraft.wiki/w/Calculators/Chunk_coordinates)
- [Dimension Folders - PiglinHost KB](https://billing.piglinhost.com/index.php?rp=/knowledgebase/25/Where-is-the-Nether-and-End-folders-located-DIM1-vs-DIM-1.html)
- [Quarry Documentation - Block Storage](https://quarry.readthedocs.io/en/latest/data_types/chunks.html)

### Additional Resources
- [Nether Portal Item Details - MinecraftItemIDs](https://minecraftitemids.com/item/nether-portal)

**Research conducted:** 2025-12-07
**Researcher:** Claude Code with Deep Research Mode
**Last verified:** Minecraft versions 1.18 through 1.21.1

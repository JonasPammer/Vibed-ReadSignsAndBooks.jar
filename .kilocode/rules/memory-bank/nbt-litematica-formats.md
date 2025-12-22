# NBT Structure & Litematica Formats - Technical Reference

**Purpose**: Technical documentation for implementing NBT structure file and Litematica export features (GitHub Issue #18).

**Related Issue**: https://github.com/JonasPammer/Vibed-ReadSignsAndBooks.jar/issues/18

## Quick Reference

| Format | Extension | Library | Max Size | Use Case |
|--------|-----------|---------|----------|----------|
| Vanilla Structure | `.nbt` | Querz NBT 6.1 | 48x48x48 | Structure blocks, datapacks |
| Litematica | `.litematic` | Querz NBT 6.1 | Unlimited | Litematica mod users |
| Sponge Schematic | `.schem` | Querz NBT 6.1 | Unlimited | WorldEdit users |

## 1. Vanilla NBT Structure Format (.nbt)

### Official Documentation
- **Minecraft Wiki**: https://minecraft.wiki/w/Structure_file
- **NBT Format**: https://minecraft.wiki/w/NBT_format
- **Block Entity Format**: https://minecraft.wiki/w/Block_entity_format

### File Structure

```
CompoundTag (root) [GZip compressed]
├── DataVersion (Int)           [Minecraft version that saved it]
├── size (List of 3 Ints)       [Width, Height, Length]
├── palette (List of Compounds) [Block state definitions]
│   └── [compound]
│       ├── Name (String)       [Block ID, e.g. "minecraft:oak_sign"]
│       └── Properties (Compound) [Block state properties]
├── blocks (List of Compounds)  [Individual block entries]
│   └── [compound]
│       ├── pos (List of 3 Ints) [X, Y, Z relative coords]
│       ├── state (Int)          [Index into palette]
│       └── nbt (Compound)       [Block entity data - SIGNS HERE]
└── entities (List of Compounds) [Entity NBT data]
```

### Size Limitation
**CRITICAL**: Vanilla structure files are limited to **48x48x48 blocks** maximum.

### Block Entity Storage (Signs, Lecterns)

Signs are stored in the `blocks[].nbt` compound when present:

**Sign Block Entity (1.20+)**:
```
nbt (Compound)
├── id (String) "minecraft:sign"
├── front_text (Compound)
│   ├── messages (List of 4 Strings) [JSON text for each line]
│   ├── color (String) "white"|"orange"|etc.
│   └── has_glowing_text (Byte) 0|1
└── back_text (Compound) [Same structure as front_text]
```

**Lectern Block Entity (Books)**:
```
nbt (Compound)
├── id (String) "minecraft:lectern"
├── Book (Compound) [Item compound without slot tag]
│   ├── id (String) "minecraft:written_book"
│   ├── count (Byte) 1
│   └── components (Compound) [1.20.5+] or tag (Compound) [pre-1.20.5]
└── Page (Int) [Current page index, 0-indexed]
```

### Implementation Reference (SchemConvert)

**Repository**: https://github.com/PiTheGuy/SchemConvert
**Location**: `/tmp/SchemConvert` (cloned for analysis)

**Key Read Logic** (`NbtSchematicFormat.java`):
```java
// Read structure file
CompoundTag tag = NbtUtil.read(file);
ListTag sizeTag = tag.getList("size");
ListTag paletteTag = tag.getList("palette");
ListTag blocksTag = tag.getList("blocks");

// Process each block
for (Tag value : blocksTag) {
    CompoundTag entry = (CompoundTag) value;
    ListTag posTag = entry.getList("pos");
    int state = entry.getInt("state");

    // Block entities (signs, lecterns) stored in "nbt" compound
    if (entry.contains("nbt", Tag.TAG_COMPOUND)) {
        builder.addBlockEntity(x, y, z, entry.getCompound("nbt"));
    }
}
```

**Key Write Logic**:
```java
// Size limit check
if (size[0] > 48 || size[1] > 48 || size[2] > 48)
    throw new ConversionException("NBT format only supports up to 48x48x48");

// Build structure
CompoundTag tag = new CompoundTag();
tag.put("DataVersion", new IntTag(dataVersion));
tag.put("size", sizeTag);
tag.put("palette", paletteTag);
tag.put("blocks", blocksTag);
tag.put("entities", entitiesTag);

NbtUtil.write(tag, file);  // Automatically GZip compresses
```

---

## 2. Litematica Format (.litematic)

### Official Documentation
- **Litemapy Docs**: https://litemapy.readthedocs.io/en/latest/litematics.html
- **Litematica Mod**: https://github.com/maruohon/litematica
- **Sakura-Ryoko Fork (1.20.5+)**: https://github.com/sakura-ryoko/litematica

### File Structure

```
CompoundTag (root) [GZip compressed]
├── MinecraftDataVersion (Int)  [Minecraft version]
├── Version (Int)               [Litematica format version, currently 6]
├── Metadata (Compound)
│   ├── Name (String)
│   ├── Author (String)
│   ├── TimeCreated (Long)      [Unix timestamp ms]
│   ├── TimeModified (Long)
│   ├── RegionCount (Int)
│   ├── TotalVolume (Int)
│   └── EnclosingSize (Compound) {x, y, z}
└── Regions (Compound)          [Named regions]
    └── [RegionName] (Compound)
        ├── Position (Compound) {x, y, z}
        ├── Size (Compound) {x, y, z}
        ├── BlockStatePalette (List)    [Block state definitions]
        ├── BlockStates (LongArray)     [Packed block indices]
        ├── TileEntities (List)         [Block entities - SIGNS HERE]
        └── Entities (List)             [Entity data]
```

### Block State Packing

Litematica uses **bit-packed LongArray** for block storage:
- Bits per value = `max(2, ceil(log2(paletteSize)))`
- Blocks ordered: X → Z → Y (iterate X fastest, Y slowest)
- 1-indexed palette references (0 = air in some versions)

**Unpacking Algorithm** (from SchemConvert):
```java
private static class BlockStateContainer {
    public static BlockStateContainer fromLongArray(long[] longs, int[] size, int paletteSize) {
        int bitsPerValue = Math.max(2, Integer.SIZE - Integer.numberOfLeadingZeros(paletteSize));
        int totalValues = size[0] * size[1] * size[2];
        BitSet bits = BitSet.valueOf(longs);
        return new BlockStateContainer(bits, bitsPerValue, totalValues * bitsPerValue);
    }

    public int[] getBlockStates() {
        int totalValues = numBits / bitsPerValue;
        int[] blockStates = new int[totalValues];
        for (int i = 0; i < totalValues; i++) {
            int value = 0;
            for (int j = 0; j < bitsPerValue; j++)
                if (bits.get(i * bitsPerValue + j)) value |= 1 << j;
            blockStates[i] = value;
        }
        return blockStates;
    }
}
```

### TileEntities Storage (Signs, Lecterns)

**IMPORTANT**: In Litematica, tile entities store coordinates AT ROOT LEVEL:
```
TileEntities (List of Compounds)
└── [compound]
    ├── x (Int)                  [Coordinates at root!]
    ├── y (Int)
    ├── z (Int)
    ├── id (String) "minecraft:sign"
    ├── front_text (Compound)    [Same as vanilla structure]
    └── back_text (Compound)
```

**Read Logic** (SchemConvert):
```java
ListTag tileEntitiesTag = region.getList("TileEntities");
for (Tag value : tileEntitiesTag) {
    CompoundTag entityTag = (CompoundTag) value;
    // Coordinates are at root level, not in sub-compound
    int x = entityTag.getInt("x");
    int y = entityTag.getInt("y");
    int z = entityTag.getInt("z");
    builder.addBlockEntity(x, y, z, entityTag);
    // Remove coords after extraction (they're duplicated)
    entityTag.remove("x");
    entityTag.remove("y");
    entityTag.remove("z");
}
```

### Multi-Region Handling

Litematica supports multiple named regions in a single file. Each region has its own:
- Position offset
- Size (can be negative for mirrored regions)
- Block palette
- Tile entities

**Current SchemConvert Limitation**: Only single-region files supported.

---

## 3. Implementation Strategy for Issue #18

### Sign Export as NBT Structure

1. Calculate bounding box of all extracted signs
2. If > 48x48x48, split into multiple structure files OR use Litematica
3. Build palette with sign blocks (oak_sign, spruce_sign, etc.)
4. Create blocks list with sign positions and NBT data
5. Write as GZip-compressed NBT

**Groovy Pseudocode**:
```groovy
def exportSignsAsNbt(List<Map> signs, File outputFile) {
    def root = new CompoundTag()
    root.put("DataVersion", new IntTag(CURRENT_DATA_VERSION))

    // Calculate bounds and create palette
    def bounds = calculateBounds(signs)
    def palette = createSignPalette(signs)

    // Build blocks list
    def blocksTag = new ListTag(Tag.TAG_COMPOUND)
    signs.each { sign ->
        def entry = new CompoundTag()
        entry.put("pos", createPosTag(sign.x, sign.y, sign.z))
        entry.put("state", new IntTag(palette.indexOf(sign.blockType)))
        entry.put("nbt", createSignNbt(sign))
        blocksTag.add(entry)
    }

    root.put("blocks", blocksTag)
    // ... size, palette, entities

    NBTUtil.write(root, outputFile)  // Auto GZip
}
```

### Book Export as Command Block NBT

For books, create a structure with command blocks that give shulker boxes:

```groovy
def exportBooksAsCommandBlocks(List<Map> books, File outputFile) {
    def shulkerBoxes = groupBooksByAuthor(books)
    def commands = shulkerBoxes.collect { author, authorBooks ->
        generateShulkerGiveCommand(author, authorBooks)
    }

    // Create command block structure
    def blocks = commands.withIndex().collect { cmd, idx ->
        createCommandBlock(x: idx % 16, y: idx / 256, z: (idx / 16) % 16, command: cmd)
    }

    // Write as NBT structure
    writeNbtStructure(blocks, outputFile)
}
```

---

## 4. Library Usage (Querz NBT 6.1)

We already use Querz NBT 6.1 in this project. Key classes:

```groovy
import net.querz.nbt.io.NBTUtil
import net.querz.nbt.tag.*

// Read compressed NBT file
CompoundTag root = NBTUtil.read(file)

// Write compressed NBT file (auto GZip)
NBTUtil.write(root, file)

// Create tags
def intTag = new IntTag(42)
def stringTag = new StringTag("hello")
def listTag = new ListTag(IntTag.class)
def compoundTag = new CompoundTag()

// Nested access
def value = root.getCompoundTag("Regions")?.getListTag("TileEntities")
```

---

## 5. Reference Repositories

| Repository | Purpose | Language |
|------------|---------|----------|
| [SchemConvert](https://github.com/PiTheGuy/SchemConvert) | Format conversion reference | Java 21 |
| [Litematica](https://github.com/maruohon/litematica) | Original mod implementation | Java |
| [Litemapy](https://github.com/SmylerMC/litemapy) | Python library | Python |
| [Querz NBT](https://github.com/Querz/NBT) | NBT library (we use this) | Java |

**Local Clone**: `/tmp/SchemConvert` - Contains working implementations of:
- `NbtSchematicFormat.java` - Vanilla structure read/write
- `LitematicSchematicFormat.java` - Litematica read/write
- `Schematic.java` - Common data model with block entities

---

## 6. DataVersion Reference

The `DataVersion` field identifies the Minecraft version:

| DataVersion | Minecraft Version |
|-------------|-------------------|
| 3953 | 1.21.4 |
| 3837 | 1.21 |
| 3700 | 1.20.5 |
| 3578 | 1.20.4 |
| 3463 | 1.20 |
| 3337 | 1.19.4 |
| 2975 | 1.18.2 |

Use the current world's DataVersion when generating structure files for maximum compatibility.

---

## Last Updated
- **Date**: 2025-12-22
- **Issue**: #18 - NBT structure file export
- **SchemConvert Version**: Latest (cloned 2025-12-22)

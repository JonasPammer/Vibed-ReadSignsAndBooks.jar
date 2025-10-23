---
type: "always_apply"
---

# Minecraft File Formats (Java Edition Only)

## World Directory Structure
Reference: https://minecraft.wiki/w/Java_Edition_level_format

**Directories we process**:
- `playerdata/<uuid>.dat` - Player inventory/ender chest (via `NBTUtil.read()`)
- `region/r.x.z.mca` - Chunk data with block entities (via `MCAUtil.read()`)
- `entities/r.x.z.mca` - Entity data (1.17+, via `MCAUtil.read()`)
- `DIM-1/region/`, `DIM-1/entities/` - Nether
- `DIM1/region/`, `DIM1/entities/` - End
- `dimensions/<namespace>/<path>/region/`, `/entities/` - Custom dimensions

**Not supported**: Bedrock Edition (uses LevelDB, not Anvil .mca files), old MCRegion format (.mcr), pre-1.7.6 player data


## 1a. https://minecraft.wiki/w/Anvil_file_format

Anvil is a file storage format. It brings a list of changes and improvements over from the previous abandoned file format, Region.
The only changes from MCRegion to Anvil were in the Chunk format (documented in Link for curious); the Region file format is still used, but labeled as Anvil.
We must only support Anvil, no need to handle old Region File.

### 1b. https://minecraft.wiki/w/Region_file_format

**Region file format** stores 32×32 chunks (1024 chunks total, 512×512 blocks) in files named `r.x.z.mcr` (Beta 1.3 to 1.1) or `r.x.z.mca` (1.2.1+, Anvil format).
- **Handled by**: `net.querz.mca.MCAUtil.read(File, LoadFlags)` - reads entire region file

**File structure**: 8KiB header (4KiB chunk location table + 4KiB timestamp table) followed by chunk data.
- **Handled by**: `net.querz.mca.MCAFile` - represents the region file structure, use `mcaFile.getChunk(x, z)` to access chunks

**Chunk location entry**: 4 bytes = 3 bytes offset (in 4KiB sectors from file start) + 1 byte sector count.
- **Handled by**: `net.querz.mca.MCAFile` internal implementation

**Chunk data format**: 4-byte length + 1-byte compression type (1=GZip, 2=Zlib, 3=Uncompressed, 4=LZ4) + compressed NBT data.
- **Handled by**: `net.querz.mca.Chunk` - automatically decompresses chunk data, use `chunk.getHandle()` to get decompressed NBT `CompoundTag`

**Region coordinates**: `regionX = chunkX >> 5`, `regionZ = chunkZ >> 5` (arithmetic shift right by 5 bits).
- **Handled by**: Region files are named `r.x.z.mca` where x and z are region coordinates

**Header index for chunk [x,z]**: `(x & 31) + (z & 31) * 32`, multiply by 4 for byte offset.
- **Handled by**: `net.querz.mca.MCAFile.getChunk(x, z)` - handles index calculation internally, x and z are 0-31 (local chunk coordinates within region)

#### 1c. https://minecraft.wiki/w/Chunk_format

**Chunk dimensions**: 16×384×16 blocks (Overworld), 16×256×16 (Nether/End). Stored in NBT format in region files.
- **Handled by**: `net.querz.mca.Chunk` - represents a chunk, use `chunk.getHandle()` to get the root `CompoundTag`

**Chunk root structure** (21w43a/1.18+):
- `DataVersion` (int), `xPos` (int), `zPos` (int), `yPos` (int), `Status` (string), `LastUpdate` (long)
- **Handled by**: `CompoundTag chunkData = chunk.getHandle()` - access root tags directly
- **Our code**: `chunkData.getCompoundTag("Level")` checks for old format (pre-21w43a) which had "Level" wrapper tag

**Chunk sections** (`sections` list): 16×16×16 sub-chunks, each with Y position, block_states palette, biomes palette, lighting.
- **Handled by**: `net.querz.nbt.tag.ListTag<CompoundTag>` - access via `chunkRoot.getListTag("sections")`
- **Not used by our code**: We don't parse block states or biomes, only extract books/signs from block entities

**Block entities** (`block_entities` list, renamed from `TileEntities` in 21w43a):
- **Handled by**: `net.querz.nbt.tag.ListTag<CompoundTag>` - access via `chunkRoot.getListTag("block_entities")` or `getListTag("TileEntities")`
- **Our code**: `Main.readBooksAnvil()` and `Main.readSignsAnvil()` iterate through block entities to find chests, signs, etc.
  - Uses `getCompoundTagList(chunkRoot, "block_entities")` helper that falls back to "TileEntities" for old format
  - Checks `tileEntity.getString("id")` to identify block entity type (e.g., "minecraft:chest", "minecraft:sign")
  - Extracts items from containers via `getCompoundTagList(tileEntity, "Items")`

**Entities** (`entities` list, renamed from `Entities` in 21w43a, moved to separate files in 20w45a/1.17):
- **Handled by**: `net.querz.nbt.tag.ListTag<CompoundTag>` - access via `chunkRoot.getListTag("entities")` or `getListTag("Entities")`
- **Our code**: `Main.readBooksAnvil()` checks for entities in chunk data (for proto-chunks)
  - Uses `getCompoundTagList(chunkRoot, "entities")` helper that falls back to "Entities" for old format
  - `Main.readEntities()` reads separate entity region files from `entities/` folder (1.17+)
  - Uses `MCAUtil.read()` with same API as chunk regions, extracts items from minecarts, item frames, etc.

**Coordinate system**: X increases East, Y increases upward, Z increases South. Block positions ordered YZX.
- **Not used by our code**: We only extract metadata (books/signs), not block positions or terrain data

**Status field**: Indicates generation status (e.g., "minecraft:full" for complete, others for proto-chunks).
- **Not used by our code**: We process all chunks regardless of status

# 2. Bedrock Edition - NOT SUPPORTED

**Bedrock Edition uses LevelDB database (not Anvil .mca files).** Querz NBT library only supports Java Edition. Supporting Bedrock would require a LevelDB library and complete rewrite of world reading logic.



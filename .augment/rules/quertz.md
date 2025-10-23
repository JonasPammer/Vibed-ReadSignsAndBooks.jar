---
type: "always_apply"
---

# Querz NBT Library & Custom Format Handling

## Core Principle
**Querz NBT library (`com.github.Querz:NBT:6.1`) handles all low-level NBT/MCA I/O.** Our custom code (132 lines in Main.java) only adds:
1. Null-safe wrappers (returns empty objects instead of null)
2. Format detection for Minecraft version changes (1.20, 1.20.5, 21w43a)
3. Fallback logic for old/new format compatibility

## DO NOT
- Replace Querz with custom NBT parsing
- Add custom MCA file reading logic
- Duplicate Querz functionality

## Custom Helper Methods (Main.java lines 1538-1669)
- **Null-safe wrappers**: `hasKey()`, `getCompoundTag()`, `getCompoundTagList()`, `getListTag()`
- **List access**: `getDoubleAt()`, `getStringAt()`, `getCompoundAt()`
- **Type detection**: `isStringList()`, `isCompoundList()`

## Custom Format Handling Logic

### 1. Chunk Format Changes (21w43a/1.18)
**Location**: `readBooksAnvil()`, `readSignsAnvil()`, `readEntities()`

**Changes**:
- Removed "Level" wrapper tag
- Renamed "TileEntities" → "block_entities"
- Renamed "Entities" → "entities"

**Our code**:
```java
// Handle both old format (with "Level" tag, removed in 21w43a/1.18) and new format (without "Level" tag)
CompoundTag level = chunkData.getCompoundTag("Level");
CompoundTag chunkRoot = (level != null) ? level : chunkData;

// Get tile entities with fallback to old name
if (chunkRoot.containsKey("block_entities")) {
    tileEntities = getCompoundTagList(chunkRoot, "block_entities");
} else if (chunkRoot.containsKey("TileEntities")) {
    tileEntities = getCompoundTagList(chunkRoot, "TileEntities");
}
```

### 2. Item Format Changes (1.20.5)
**Location**: `parseItem()`, `readWrittenBook()`, `readWritableBook()`

**Changes**:
- Changed "tag" → "components"
- Changed "BlockEntityTag" → "minecraft:container"
- Changed book pages from string list to compound list with "raw"/"filtered" fields
- Changed author/title from string to text component (CompoundTag)

**Our code**:
```java
// Try new format first (1.20.5+ with components)
if (hasKey(item, "components")) {
    CompoundTag components = getCompoundTag(item, "components");
    // ... handle new format
} else if (hasKey(item, "tag")) {
    // Old format (pre-1.20.5)
    CompoundTag tag = getCompoundTag(item, "tag");
    // ... handle old format
}
```

### 3. Book Page Format Changes (1.20.5)
**Location**: `readWrittenBook()`, `readWritableBook()`

**Changes**:
- Pre-1.20.5: Pages are string list
- 1.20.5+: Pages are compound list with "raw"/"filtered" fields

**Our code**:
```java
// Check if pages are stored as strings (pre-1.20.5) or compounds (1.20.5+)
if (isStringList(pages)) {
    pageText = getStringAt(pages, pc);
} else if (isCompoundList(pages)) {
    CompoundTag pageCompound = getCompoundAt(pages, pc);
    if (hasKey(pageCompound, "raw")) {
        pageText = pageCompound.getString("raw");
    } else if (hasKey(pageCompound, "filtered")) {
        pageText = pageCompound.getString("filtered");
    }
}
```

### 4. Author/Title Format Changes (1.20.5)
**Location**: `readWrittenBook()`

**Changes**:
- Pre-1.20.5: Author/title are plain strings
- 1.20.5+: Author/title are text components (CompoundTag with "raw"/"text" fields)

**Our code**:
```java
Tag<?> authorTag = tag.get("author");
if (authorTag instanceof CompoundTag) {
    // 1.20.5+ format: text component
    CompoundTag authorComp = (CompoundTag) authorTag;
    if (authorComp.containsKey("raw")) {
        author = authorComp.getString("raw");
    } else if (authorComp.containsKey("text")) {
        author = authorComp.getString("text");
    }
} else if (authorTag instanceof StringTag) {
    // Pre-1.20.5 format: plain string
    author = tag.getString("author");
}
```

### 5. Sign Format Changes (1.20)
**Location**: `parseSign()`, `parseSignNew()`

**Changes**:
- Old format (changed in 1.20): "Text1", "Text2", "Text3", "Text4" fields
- New format (introduced in 1.20): "front_text"/"back_text" with "messages" array

**Our code**: Separate methods `parseSign()` (old) and `parseSignNew()` (new) with detection logic in `readSignsAnvil()`

## Summary for Future LLMs

**Key principle**: Querz NBT library handles low-level NBT/MCA file I/O perfectly. Our custom code adds:
1. **Null-safe wrappers** for easier chaining
2. **Format detection logic** to handle multiple Minecraft versions
3. **Fallback logic** to support both old and new formats

**Do NOT**:
- Replace Querz with custom NBT parsing
- Add custom MCA file reading logic
- Duplicate functionality that Querz already provides

**DO**:
- Use Querz for all NBT/MCA file I/O
- Add format-specific logic only when Minecraft changes data structures
- Keep helper methods minimal and focused on format variations

## Would Using the Fork Eliminate Our Custom Code? NO!

**What the fork DOES handle**:
- **Chunk format changes (21w43a/1.18)**: Uses `VersionAware<NbtPath>` system to map paths across versions
  - Example: `TILE_ENTITIES_PATH` maps `Level.TileEntities` (old) → `block_entities` (new)
  - Example: `ENTITIES_PATH` maps `Level.Entities` (old) → `entities` (new)
  - This is a sophisticated system that would eliminate ~20 lines of our fallback code
- **POI/ENTITIES MCA file support**: Separate classes for `McaPoiFile`, `McaEntitiesFile`
- **Streaming MCA I/O**: `McaFileChunkIterator`, `McaFileStreamingWriter`, `RandomAccessMcaFile`
- **Region file relocation**: `RegionFileRelocator` with full chunk coordinate updating
- **Block/biome palette manipulation**: `PalettizedCuboid`, `LongArrayTagPackedIntegers`
- **DataVersion tracking**: Comprehensive enum from 1.9.0 to 1.21.3+ with snapshot support

**What the fork does NOT handle** (verified by searching the codebase):
- **Item format changes (1.20.5)**: No code for "tag" → "components" conversion
- **Book format changes (1.20.5)**: No code for string pages → compound pages with "raw"/"filtered"
- **Author/title format changes (1.20.5)**: No code for string → text component (CompoundTag)
- **Container format changes (1.20.5)**: No code for "BlockEntityTag" → "minecraft:container"
- **Sign format changes (1.20)**: No code for "Text1-4" → "front_text"/"back_text"
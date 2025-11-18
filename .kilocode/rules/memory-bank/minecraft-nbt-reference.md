# Minecraft NBT Format - Complete Technical Reference

**Created:** 2025-11-18
**Sources:** Minecraft Wiki (minecraft.wiki), community documentation
**Versions Covered:** All Java Edition versions, focus on 1.13-1.21+

## Overview

Named Binary Tag (NBT) is the tree data structure format used by Minecraft for save files, world data, item metadata, and entity storage. This document provides authoritative technical specifications for parsing and generating NBT data.

---

## NBT Tag Types - Complete Specification

### Tag Type IDs and Binary Structure

| Type ID | Hex | Tag Name | Java Type | Payload Size | Description |
|---------|-----|----------|-----------|--------------|-------------|
| 0 | 0x00 | TAG_End | - | 0 bytes | Marks end of compound tags |
| 1 | 0x01 | TAG_Byte | byte | 1 byte | Signed 8-bit integer |
| 2 | 0x02 | TAG_Short | short | 2 bytes | Signed 16-bit integer, big-endian |
| 3 | 0x03 | TAG_Int | int | 4 bytes | Signed 32-bit integer, big-endian |
| 4 | 0x04 | TAG_Long | long | 8 bytes | Signed 64-bit integer, big-endian |
| 5 | 0x05 | TAG_Float | float | 4 bytes | IEEE 754-2008 binary32 |
| 6 | 0x06 | TAG_Double | double | 8 bytes | IEEE 754-2008 binary64 |
| 7 | 0x07 | TAG_Byte_Array | byte[] | 4-byte size + data | Variable-length byte sequence |
| 8 | 0x08 | TAG_String | String | 2-byte size + UTF-8 | Modified UTF-8 encoding |
| 9 | 0x09 | TAG_List | List<Tag> | 1-byte type + 4-byte size + elements | Homogeneous typed list |
| 10 | 0x0A | TAG_Compound | Map<String, Tag> | Named tags + TAG_End | Key-value map structure |
| 11 | 0x0B | TAG_Int_Array | int[] | 4-byte size + data | Variable-length int sequence |
| 12 | 0x0C | TAG_Long_Array | long[] | 4-byte size + data | Variable-length long sequence |

### Binary Format Details

**Standard Tag Structure (all except TAG_End):**
```
[1 byte] Type ID
[2 bytes] Name length (unsigned big-endian)
[N bytes] Name (UTF-8 string)
[Variable] Payload (type-specific)
```

**TAG_End:**
- Single byte: `0x00`
- No name or payload
- Terminates TAG_Compound lists

**TAG_String Encoding:**
- 2-byte unsigned big-endian length (max 65,535 bytes)
- Modified UTF-8 data (not null-terminated)
- Empty strings: length `0x0000`, no data bytes

**TAG_List Structure:**
```
[1 byte] Element type ID (0-12)
[4 bytes] Element count (signed big-endian)
[Variable] Elements (payloads only, no names/IDs)
```

**TAG_Compound Structure:**
```
[Repeated] Named child tags
[1 byte] 0x00 (TAG_End delimiter)
```

---

## SNBT (String Named Binary Tag) Format

### Purpose
Human-readable representation of NBT data used in Minecraft commands.

### Syntax Rules

**Primitives:**
- Byte: `123b` (suffix `b`)
- Short: `123s` (suffix `s`)
- Int: `123` (no suffix)
- Long: `123l` or `123L` (suffix `l` or `L`)
- Float: `1.23f` or `1.23F` (suffix `f` or `F`)
- Double: `1.23d` or `1.23` (suffix `d` optional)
- String: `"text"` or `'text'` (quoted) or `unquoted` (alphanumeric only)

**Arrays:**
- Byte Array: `[B;1b,2b,3b]`
- Int Array: `[I;1,2,3]`
- Long Array: `[L;1L,2L,3L]`

**Collections:**
- List: `[element1, element2, element3]`
- Compound: `{key1: value1, key2: value2}`

**Example:**
```snbt
{
  title: "My Book",
  author: "Player123",
  generation: 0,
  pages: ['{"text":"Page 1"}', '{"text":"Page 2"}']
}
```

---

## Technical Constraints and Limits

### Structural Limits

**Maximum Nesting Depth:** 512 levels
- Applies to TAG_Compound and TAG_List nesting
- Exceeding throws `MaxDepthReachedException` in Querz library
- Prevents infinite recursion and stack overflow

**Maximum List/Array Elements:** 2,147,483,639 (2³¹ - 9)
- Stored as signed 32-bit integer
- Practical limit due to memory constraints much lower

**String Length:** 65,535 bytes maximum
- Stored as unsigned 16-bit length prefix
- UTF-8 encoding (multi-byte characters count as multiple bytes)

### Endianness

**Java Edition:** Big-endian (network byte order)
- All multi-byte integers stored most-significant byte first
- Example: `0x12345678` → bytes `[0x12, 0x34, 0x56, 0x78]`

**Bedrock Edition:** Little-endian (not covered in this reference)

### Compression

**World Save Files:** GZIP compressed
- Level.dat, playerdata/*.dat files use GZIP
- Region files (*.mca) use per-chunk Zlib compression

**Network Protocol:** Varies by packet type

---

## CompoundTag Detailed Specification

### Purpose
Key-value map structure supporting arbitrary nested data.

### Characteristics
- **Uniqueness:** No two tags may share the same name within a compound
- **Ordering:** Implementation-dependent (not guaranteed)
- **Nesting:** Can contain other compounds, lists, primitives
- **Termination:** Always ends with TAG_End (0x00) byte

### Common Operations

**Reading Values (Querz Library):**
```java
CompoundTag tag = ...;
String author = tag.getString("author");     // Returns "" if missing
int generation = tag.getInt("generation");   // Returns 0 if missing
CompoundTag nested = tag.getCompoundTag("nested"); // Returns empty if missing
```

**Writing Values:**
```java
CompoundTag tag = new CompoundTag();
tag.putString("title", "My Book");
tag.putInt("generation", 1);
tag.put("nested", new CompoundTag());
```

**Null Safety:**
- Querz library returns default values (empty string, 0, empty collections) for missing keys
- `containsKey(String)` method checks existence before retrieval
- Direct `get(String)` returns `null` if key doesn't exist

---

## ListTag Detailed Specification

### Purpose
Ordered homogeneous collection of tag payloads.

### Characteristics
- **Typed:** All elements must be same tag type
- **Indexed:** Zero-based integer indexing
- **Size:** Stored as 4-byte signed integer prefix
- **No Names:** Elements stored as payloads only (no individual names)

### Element Type Enforcement
```java
// Valid: all elements are same type
ListTag<StringTag> pages = new ListTag<>(StringTag.class);
pages.add(new StringTag("Page 1"));
pages.addString("Page 2"); // Convenience method

// Invalid: mixing types throws exception
ListTag<StringTag> mixed = new ListTag<>(StringTag.class);
mixed.add(new IntTag(5)); // Runtime error
```

### Empty List Handling
- Empty lists have element type TAG_End (ID 0)
- First insertion sets the element type
- Cannot change type after first element added

---

## Version-Specific NBT Changes

### Minecraft 1.13 (18w19a)
- Introduced flattening: block/item IDs changed from numeric to namespaced strings
- NBT structure remained compatible

### Minecraft 1.14 (18w43a)
- Book page/title limits increased
- NBT structure unchanged

### Minecraft 1.17.1 (Pre-release 1)
- Book validation limits tightened for multiplayer
- NBT structure unchanged

### Minecraft 1.18 (21w43a)
- Removed "Level" wrapper tag from chunk data
- Renamed "TileEntities" → "block_entities"
- Renamed "Entities" → "entities"
- NBT format itself unchanged

### Minecraft 1.20
- Sign format changed: "Text1-4" → "front_text"/"back_text" compounds
- NBT structure extended, not replaced

### Minecraft 1.20.5 (24w09a) - MAJOR BREAKING CHANGE
- Introduced **Data Components** system
- Item NBT largely replaced by typed components
- Legacy NBT still used for world/entity data
- See separate section below

---

## Data Components System (1.20.5+)

### Architectural Change

**Before 1.20.5:**
```
Item {
  id: "minecraft:written_book"
  Count: 1b
  tag: {
    pages: [...]
    title: "..."
    author: "..."
    generation: 0
  }
}
```

**After 1.20.5:**
```
Item {
  id: "minecraft:written_book"
  count: 1
  components: {
    minecraft:written_book_content: {
      pages: [...]
      title: {raw: "..."}
      author: "..."
      generation: 0
    }
  }
}
```

### Key Differences

**Field Name Changes:**
- `Count` (byte) → `count` (int)
- `tag` (compound) → `components` (compound)

**Component Namespacing:**
- All components use resource location format: `minecraft:component_name`
- Custom components: `modid:component_name`

**Type Safety:**
- Components have structured schemas
- Invalid data rejected at parse time
- Default values defined per item type

### Backward Compatibility
- World files auto-migrate on load
- External tools must handle both formats
- No automatic downgrade path exists

---

## Querz NBT Library - API Reference

### Installation

**Gradle:**
```gradle
repositories {
    maven { url 'https://jitpack.io/' }
}
dependencies {
    implementation 'com.github.Querz:NBT:6.1'
}
```

**Maven:**
```xml
<repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
</repository>
<dependency>
    <groupId>com.github.Querz</groupId>
    <artifactId>NBT</artifactId>
    <version>6.1</version>
</dependency>
```

### Core API Methods

**NBTUtil (File I/O):**
```java
// Read compressed NBT from file
NamedTag namedTag = NBTUtil.read(file);
CompoundTag root = (CompoundTag) namedTag.tag;

// Write compressed NBT to file
NBTUtil.write(tag, filename);
```

**SNBTUtil (SNBT Conversion):**
```java
// NBT to SNBT string
String snbt = SNBTUtil.toSNBT(tag);

// SNBT string to NBT (parse)
Tag<?> tag = SNBTUtil.fromSNBT(snbtString);
```

**MCAUtil (Region Files):**
```java
// Read Minecraft region file
MCAFile region = MCAUtil.read(file);

// Access chunks
Chunk chunk = region.getChunk(x, z); // x, z in 0-31 range
CompoundTag chunkData = chunk.handle;
```

### Null Safety Pattern

**Recommended Safe Access:**
```java
// Pattern 1: Use safe getters with defaults
String author = tag.getString("author"); // "" if missing
int gen = tag.getInt("generation");      // 0 if missing

// Pattern 2: Check existence first
if (tag.containsKey("generation")) {
    int gen = tag.getInt("generation");
}

// Pattern 3: Handle null explicitly
Tag<?> rawTag = tag.get("generation");
if (rawTag instanceof IntTag) {
    int gen = ((IntTag) rawTag).asInt();
}
```

**Avoid:**
```java
// Dangerous: NullPointerException if key missing
int gen = tag.get("generation").asInt(); // CRASH if null
```

---

## Common Parsing Patterns

### Multi-Format Fallback (1.20.5 Compatibility)

```java
// Example: Extract book generation from item NBT
int generation = 0; // Default: Original

// Try 1.20.5+ format first
if (item.containsKey("components")) {
    CompoundTag components = item.getCompoundTag("components");
    if (components.containsKey("minecraft:written_book_content")) {
        CompoundTag bookContent = components.getCompoundTag("minecraft:written_book_content");
        generation = bookContent.getInt("generation");
    }
}
// Fallback to pre-1.20.5 format
else if (item.containsKey("tag")) {
    CompoundTag tag = item.getCompoundTag("tag");
    generation = tag.getByte("generation"); // Querz auto-converts byte to int
}
```

### List Iteration

```java
ListTag<?> pages = tag.getListTag("pages");
for (int i = 0; i < pages.size(); i++) {
    Tag<?> element = pages.get(i);

    if (element instanceof StringTag) {
        String pageText = ((StringTag) element).getValue();
    } else if (element instanceof CompoundTag) {
        CompoundTag pageComp = (CompoundTag) element;
        String rawText = pageComp.getString("raw");
    }
}
```

### Nested Compound Access

```java
// Safe nested access
CompoundTag level = chunkData.getCompoundTag("Level");
CompoundTag blockEntities = level.containsKey("block_entities")
    ? level.getCompoundTag("block_entities")
    : level.getCompoundTag("TileEntities"); // Fallback for old format
```

---

## Performance Considerations

### Memory Usage
- Each tag object has Java object overhead (~24-40 bytes)
- Large compound trees can consume significant memory
- Streaming parsers not available in Querz library

### Parsing Speed
- GZIP decompression is CPU-intensive
- Large NBT structures (>1MB) may take 10-100ms to parse
- Caching parsed structures recommended

### Best Practices
1. **Close resources:** Use try-with-resources for file handles
2. **Limit depth:** Validate against malicious deeply-nested structures
3. **Reuse objects:** Don't parse same file multiple times
4. **Stream where possible:** Process chunk-by-chunk instead of full world

---

## Debugging NBT Data

### Tools

**NBTExplorer:** GUI tool for viewing/editing NBT files
- Windows/Mac/Linux compatible
- Visual tree navigation
- Hex editor for binary inspection

**Command-Line Tools:**
```bash
# Decompress and view with Querz library
java -jar NBTDump.jar level.dat

# Online SNBT validators
# https://minecraft.tools/en/nbt.php
```

### Common Issues

**Issue:** "Tag name mismatch"
- **Cause:** Incorrect capitalization (NBT is case-sensitive)
- **Fix:** Use exact field names from documentation

**Issue:** ClassCastException on tag retrieval
- **Cause:** Assuming wrong tag type
- **Fix:** Check type before casting or use instanceof

**Issue:** Empty strings/zeros for existing fields
- **Cause:** Using wrong getter for tag type
- **Fix:** TAG_Byte needs `getByte()`, not `getInt()`

---

## References

- Minecraft Wiki NBT Format: https://minecraft.wiki/w/NBT_format
- Querz NBT Library: https://github.com/Querz/NBT
- wiki.vg NBT Protocol: https://wiki.vg/NBT
- SNBT Specification: Part of Minecraft command syntax docs

**Last Updated:** 2025-11-18
**Minecraft Version Coverage:** 1.13 - 1.21+
**Querz NBT Version:** 6.1

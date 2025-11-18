# Minecraft Written Book NBT Formats - Complete Reference

**Created:** 2025-11-18
**Sources:** Minecraft Wiki, community documentation, testing
**Coverage:** Java Edition 1.13 through 1.21+

## Overview

This document provides authoritative specifications for Minecraft written book NBT data structures across all major versions. Written books store player-created text content and have undergone significant format changes.

---

## Book Item Types

### minecraft:writable_book (Book and Quill)
- **State:** Unsigned, editable
- **NBT Component (1.20.5+):** `minecraft:writable_book_content`
- **Contains:** Pages only (no title/author)
- **Page Format:** Plain text strings

### minecraft:written_book (Signed Book)
- **State:** Signed, immutable
- **NBT Component (1.20.5+):** `minecraft:written_book_content`
- **Contains:** Pages, title, author, generation, resolved flag
- **Page Format:** JSON text components

---

## Pre-1.20.5 Format (Legacy NBT Tags)

### Item Structure
```
{
  id: "minecraft:written_book",
  Count: 1b,
  tag: {
    // Book data here
  }
}
```

### Written Book Tag Fields

**Required Fields:**

**title** [String]
- Maximum length: Varies by version (see version history below)
- Display name for the book
- Does not unlock locked containers
- Lower priority than custom display names

**author** [String]
- Player name or arbitrary text
- Automatically set to player name when signing
- Can be modified via commands

**pages** [NBT List of Strings]
- Each element is a serialized JSON text component
- List can be empty (0 pages)
- Each page is one string element

**Optional Fields:**

**generation** [Int]
- Values: 0=Original, 1=Copy of Original, 2=Copy of Copy, 3=Tattered
- **Default if missing:** 0 (Original)
- **Type:** Integer (not Byte, despite being 0-3 range)
- Controls copyability: values > 1 cannot be copied

**resolved** [Byte]
- Values: 0=false, 1=true
- **Default if missing:** 0 (false)
- Set to 1 when book opened for first time
- Triggers resolution of dynamic text components (selectors, scores, NBT)

**Realm-Specific Fields (multiplayer only):**

**filtered_title** [String]
- Alternative title for profanity-filtered clients
- Only present on Realms servers

**filtered_pages** [NBT Compound]
- Page number → filtered page text mapping
- Format: `{0: "filtered page 0", 1: "filtered page 1"}`

### Writable Book Tag Fields

**pages** [NBT List of Strings]
- Plain text strings (not JSON)
- Each element is one page
- No title or author until signed

### Example NBT (Pre-1.20.5)

**Written Book:**
```snbt
{
  id: "minecraft:written_book",
  Count: 1b,
  tag: {
    title: "My Journey",
    author: "Steve",
    generation: 0,
    resolved: 0b,
    pages: [
      '{"text":"Chapter 1","bold":true}',
      '{"text":"Once upon a time...","color":"dark_blue"}'
    ]
  }
}
```

**Writable Book:**
```snbt
{
  id: "minecraft:writable_book",
  Count: 1b,
  tag: {
    pages: [
      "This is plain text",
      "Another plain page"
    ]
  }
}
```

---

## 1.20.5+ Format (Data Components)

### Item Structure Change

**Major Breaking Change (Snapshot 24w09a):**
- Replaced `tag` compound with `components` compound
- Changed `Count` (byte) to `count` (int)
- Namespaced all component keys

### Item Structure
```
{
  id: "minecraft:written_book",
  count: 1,
  components: {
    minecraft:written_book_content: {
      // Book data here
    }
  }
}
```

### Written Book Content Component

**Component Name:** `minecraft:written_book_content`

**Structure:**
```snbt
{
  title: {
    raw: "My Book",          // Required, max 32 characters
    filtered: "*** ****"     // Optional, profanity-filtered alternative
  },
  author: "PlayerName",      // Required, plain string
  generation: 0,             // Optional, defaults to 0
  resolved: false,           // Optional, defaults to false
  pages: [                   // Required, list of page objects
    {
      raw: '{"text":"Page 1"}',      // Required, JSON text component as string
      filtered: '{"text":"***"}'     // Optional, filtered alternative
    },
    {
      raw: '{"text":"Page 2"}'
    }
  ]
}
```

**Field Specifications:**

**title** [Compound with raw/filtered]
- `raw`: Main title (max 32 characters in 1.20.5+)
- `filtered`: Optional alternative for profanity filter
- If exceeds limit, entire book data erased

**author** [String]
- Plain text author name
- NOT a compound (unlike title)

**pages** [List of Compounds]
- Each page is a compound with `raw` and optional `filtered`
- `raw` field contains JSON text component as serialized string
- Max page content: 32,767 characters serialized (1.20.5+)

**generation** [Int]
- Same values as pre-1.20.5: 0-3
- Defaults to 0 if omitted

**resolved** [Boolean]
- Changed from byte to boolean
- Defaults to false if omitted

### Writable Book Content Component

**Component Name:** `minecraft:writable_book_content`

**Structure:**
```snbt
{
  pages: [
    {
      raw: "Plain text page 1",
      filtered: "***"         // Optional
    },
    {
      raw: "Plain text page 2"
    }
  ]
}
```

- No title or author fields
- Pages contain plain text in `raw` field (not JSON)
- Same filtered system as written books

### Example NBT (1.20.5+)

**Written Book:**
```snbt
{
  id: "minecraft:written_book",
  count: 1,
  components: {
    minecraft:written_book_content: {
      title: {raw: "Epic Tale"},
      author: "Notch",
      generation: 1,
      resolved: true,
      pages: [
        {raw: '{"text":"The Beginning","bold":true,"color":"gold"}'},
        {raw: '{"text":"A hero emerged..."}'}
      ]
    }
  }
}
```

**Writable Book:**
```snbt
{
  id: "minecraft:writable_book",
  count: 1,
  components: {
    minecraft:writable_book_content: {
      pages: [
        {raw: "My notes"},
        {raw: "More thoughts"}
      ]
    }
  }
}
```

---

## Version-Specific Limits History

### Before 1.13 (legacy)
- **Pages:** 50 maximum
- **Characters per page:** 256 maximum
- **Title:** 16 characters maximum
- **Packet size:** 32,767 bytes compressed

### 1.13 - 1.14 (18w19a through 18w43a)
- **Pages:** 50 maximum
- **Characters per page:** 256 server-side, GUI dynamic limit
- **Title:** 16 characters (server enforced)
- **Packet size:** 32,767 bytes compressed

### 1.14 (18w43a+)
- **Pages:** 100 maximum (increased from 50)
- **Characters per page:** 1,023 GUI limit (increased from dynamic)
- **Title:** 65,535 characters (NbtString limit)
- **Packet size:** 2,097,152 bytes raw (multiplayer), unlimited (singleplayer)

### 1.17.1 (Pre-release 1)
- **Pages:** 100 maximum (unchanged)
- **Characters per page:** 8,192 maximum (multiplayer), unlimited (singleplayer)
- **Title:** 128 characters (multiplayer), unlimited (singleplayer)
- **Packet size:** 8,388,608 bytes raw, 2,097,152 bytes compressed (multiplayer)

### 1.20 - 1.20.4
- No format changes from 1.17.1
- Limits remained stable

### 1.20.5+ (24w09a+)
- **Pages:** 100 maximum (unchanged)
- **Characters per page:** 1,023 GUI limit, 32,767 serialized limit
- **Title:** 32 characters maximum (MAJOR REDUCTION)
- **Packet size:** Books exceeding limits have data erased
- **Breaking change:** Title/page exceeding limits → entire book wiped

---

## Generation Field - Complete Specification

### Purpose
Tracks how many times a book has been copied, controlling copyability.

### Values and Meanings

| Value | Label | Description | Can Copy? |
|-------|-------|-------------|-----------|
| 0 | Original | First signed book from book & quill | ✅ Yes |
| 1 | Copy of Original | Copied from an original (generation 0) | ✅ Yes |
| 2 | Copy of Copy | Copied from a copy (generation 1) | ❌ No |
| 3 | Tattered | Unused in normal gameplay | ❌ No |

### Data Type
- **Pre-1.20.5:** Integer (TAG_Int, 4 bytes)
- **1.20.5+:** Integer (component type)
- **NOT Byte:** Despite 0-3 range, stored as full integer

### Default Behavior
- **If field missing:** Assumed to be 0 (Original)
- **Minecraft behavior:** Books without generation tag are originals
- **Parsing logic:** Must default to 0 for missing field

### Copy Mechanics

**Crafting Table Recipe:**
```
[Book & Quill] + [Written Book (gen 0)] = [Written Book (gen 1)] × up to 8
[Book & Quill] + [Written Book (gen 1)] = [Written Book (gen 2)] × up to 8
[Book & Quill] + [Written Book (gen 2)] = Recipe disabled
```

**Generation Calculation:**
```
new_generation = original_generation + 1
if (original_generation > 1) {
    // Cannot copy
}
```

### Tattered Books
- **Availability:** Commands only (`/give`, NBT editors)
- **Behavior:** Identical to Copy of Copy (generation 2)
- **Purpose:** Map makers can create uncopyable books
- **Rarity:** Not obtainable in survival

### Usage in Commands

**Pre-1.20.5:**
```
/give @p written_book{title:"Test",author:"Me",generation:2,pages:['{"text":"Hi"}']}
```

**1.20.5+:**
```
/give @p written_book[written_book_content={title:{raw:"Test"},author:"Me",generation:2,pages:[{raw:'{"text":"Hi"}'}]}]
```

---

## Resolved Field - Complete Specification

### Purpose
Controls when dynamic JSON text components are resolved to static text.

### What Gets Resolved?

**Dynamic Components:**
- **Selector:** `{"selector":"@p"}` → Player name
- **Score:** `{"score":{"name":"Player","objective":"kills"}}` → Numeric value
- **NBT:** `{"nbt":"Items[0].Count","entity":"@s"}` → NBT data

**Resolution Process:**
1. Parse JSON component
2. Query world data (scores, entities, NBT)
3. Convert to static text component
4. Replace original dynamic component

**Important:** Resolution fixes value permanently (not dynamic updates).

### Behavior by Version

**Pre-1.20.5:**
- Type: Byte (0 or 1)
- Default: 0 (false)
- Set to 1 when book first opened by player

**1.20.5+:**
- Type: Boolean
- Default: false
- Set to true when book first opened

### When Resolution Happens
- Written books: On first open
- Signs: On placement
- Text displays: On creation
- Commands (/tellraw, /title): Immediately
- Boss bars: On creation

### Limitations
- Cannot resolve in all contexts (e.g., stored in chest unopened)
- Reader-specific data (like @s selector) only works for single reader
- Once resolved, cannot be made dynamic again

---

## Page Format - JSON Text Components

### Overview
Book pages use Minecraft's JSON text component format for rich formatting.

### Basic Structure

**Simple Text:**
```json
{"text": "Hello World"}
```

**Formatted Text:**
```json
{
  "text": "Important",
  "color": "red",
  "bold": true,
  "underlined": true
}
```

**Multi-Part (Extra):**
```json
{
  "text": "Hello ",
  "extra": [
    {"text": "World", "color": "blue"},
    {"text": "!", "bold": true}
  ]
}
```

### Formatting Options

**Color (Named):**
- black, dark_blue, dark_green, dark_aqua, dark_red, dark_purple
- gold, gray, dark_gray, blue, green, aqua, red, light_purple
- yellow, white

**Color (Hex, 1.16+):**
```json
{"text": "Custom", "color": "#FF5733"}
```

**Style Flags:**
- `bold`: true/false
- `italic`: true/false
- `underlined`: true/false
- `strikethrough`: true/false
- `obfuscated`: true/false (random character animation)

### Special Content Types

**Translatable Text:**
```json
{
  "translate": "item.minecraft.diamond_sword",
  "with": ["Excalibur"]
}
```

**Scoreboard Values:**
```json
{
  "score": {
    "name": "PlayerName",
    "objective": "deaths"
  }
}
```

**Entity Selectors:**
```json
{"selector": "@p"}
```

**Keybinds:**
```json
{"keybind": "key.jump"}
```

**NBT Data:**
```json
{
  "nbt": "SelectedItem.tag.display.Name",
  "entity": "@s"
}
```

### Line Breaks
Use `\n` within text field:
```json
{"text": "Line 1\nLine 2\nLine 3"}
```

### Legacy Formatting Codes (§)

**§ Code System (pre-JSON):**
- §0-9, §a-f: Colors
- §k: Obfuscated
- §l: Bold
- §m: Strikethrough
- §n: Underline
- §o: Italic
- §r: Reset

**Usage in JSON:**
```json
{"text": "§lBold §rNormal"}
```

Modern books should use JSON properties instead of § codes.

---

## Parsing Strategies

### Multi-Version Compatibility

**Recommended Fallback Pattern:**
```java
// Try 1.20.5+ format first
if (item.containsKey("components")) {
    CompoundTag components = item.getCompoundTag("components");
    if (components.containsKey("minecraft:written_book_content")) {
        // Parse 1.20.5+ format
        return parse1205Format(components);
    }
}

// Fallback to pre-1.20.5 format
if (item.containsKey("tag")) {
    CompoundTag tag = item.getCompoundTag("tag");
    return parseLegacyFormat(tag);
}

// No book data found
return null;
```

### Field Extraction

**Safe Title Extraction (handles both formats):**
```java
String title = "";

// 1.20.5+: title is compound with raw field
Tag<?> titleTag = bookData.get("title");
if (titleTag instanceof CompoundTag) {
    CompoundTag titleComp = (CompoundTag) titleTag;
    title = titleComp.getString("raw");
}
// Pre-1.20.5: title is plain string
else if (titleTag instanceof StringTag) {
    title = ((StringTag) titleTag).getValue();
}
```

**Safe Page Extraction:**
```java
ListTag<?> pages = bookData.getListTag("pages");
List<String> pageTexts = new ArrayList<>();

for (int i = 0; i < pages.size(); i++) {
    Tag<?> pageTag = pages.get(i);

    // 1.20.5+: pages are compounds with raw field
    if (pageTag instanceof CompoundTag) {
        CompoundTag pageComp = (CompoundTag) pageTag;
        pageTexts.add(pageComp.getString("raw"));
    }
    // Pre-1.20.5: pages are strings
    else if (pageTag instanceof StringTag) {
        pageTexts.add(((StringTag) pageTag).getValue());
    }
}
```

### Generation Extraction

**Correct Multi-Format Approach:**
```java
int generation = 0; // Default: Original

// 1.20.5+ format
if (item.containsKey("components")) {
    CompoundTag components = item.getCompoundTag("components");
    CompoundTag bookContent = components.getCompoundTag("minecraft:written_book_content");
    generation = bookContent.getInt("generation"); // Returns 0 if missing
}
// Pre-1.20.5 format
else if (item.containsKey("tag")) {
    CompoundTag tag = item.getCompoundTag("tag");
    generation = tag.getInt("generation"); // Returns 0 if missing
}

// Validate range
if (generation < 0 || generation > 3) {
    generation = 0; // Invalid data, treat as original
}
```

---

## Command Syntax Evolution

### Pre-1.20.5 Command Format

**1.13 - 1.14:**
```
/give @p written_book{title:"My Book",author:"Steve",pages:['{"text":"Page 1"}','{"text":"Page 2"}']}
```

**Notes:**
- Uses curly braces `{}` for NBT compound
- Pages use single quotes `'...'` for string literals
- JSON inside pages uses double quotes

### 1.20.5+ Command Format

**Version 1.20.5:**
```
/give @p written_book[minecraft:written_book_content={title:{raw:"My Book"},author:"Steve",pages:[{raw:'{"text":"Page 1"}'}]}]
```

**Version 1.21+ (namespace optional):**
```
/give @p written_book[written_book_content={title:{raw:"My Book"},author:"Steve",pages:[{raw:'{"text":"Page 1"}'}]}]
```

**Notes:**
- Uses square brackets `[]` for components
- Component names are namespaced
- Title is compound with `raw` field
- Pages are compounds with `raw` field
- JSON in raw field uses single quotes for outer string, double for inner

### Version-Specific Escaping

**1.13-1.14 (Double Escaping):**
```
title:"Test\\"Quote"    # For literal: Test"Quote
title:"Line1\\\\nLine2" # For literal: Line1\nLine2 (newline)
```

**1.20.5+ (Single Escaping):**
```
title:{raw:"Test\"Quote"}   # For literal: Test"Quote
title:{raw:"Line1\nLine2"}  # For literal newline
```

---

## Migration Guide (1.20.4 → 1.20.5)

### Automatic Migration
Minecraft automatically migrates old books when loading worlds. However, external tools must handle both formats.

### Manual Migration Steps

**Step 1: Detect Format**
```java
boolean is1205Plus = item.containsKey("components");
```

**Step 2: Extract Data**
```java
if (is1205Plus) {
    // Extract from components.minecraft:written_book_content
} else {
    // Extract from tag
}
```

**Step 3: Convert if Needed**
```java
// Convert pre-1.20.5 → 1.20.5+
CompoundTag components = new CompoundTag();
CompoundTag bookContent = new CompoundTag();

// Title: string → compound
CompoundTag titleComp = new CompoundTag();
titleComp.putString("raw", legacyTag.getString("title"));
bookContent.put("title", titleComp);

// Author: unchanged (still string)
bookContent.putString("author", legacyTag.getString("author"));

// Generation: unchanged (still int)
bookContent.putInt("generation", legacyTag.getInt("generation"));

// Pages: list of strings → list of compounds
ListTag<CompoundTag> newPages = new ListTag<>(CompoundTag.class);
ListTag<?> oldPages = legacyTag.getListTag("pages");
for (int i = 0; i < oldPages.size(); i++) {
    CompoundTag pageComp = new CompoundTag();
    pageComp.putString("raw", getStringAt(oldPages, i));
    newPages.add(pageComp);
}
bookContent.put("pages", newPages);

components.put("minecraft:written_book_content", bookContent);
```

---

## Common Parsing Pitfalls

### Pitfall 1: Assuming Generation is Byte
**Wrong:**
```java
byte gen = tag.getByte("generation"); // Loses data if stored as Int
```

**Correct:**
```java
int gen = tag.getInt("generation"); // Works for both Byte and Int
```

### Pitfall 2: Not Defaulting Missing Generation
**Wrong:**
```java
int gen = tag.getInt("generation"); // Returns 0, but why?
```

**Correct:**
```java
int gen = tag.containsKey("generation")
    ? tag.getInt("generation")
    : 0; // Explicitly document default
```

### Pitfall 3: Ignoring Format Version
**Wrong:**
```java
String title = tag.getString("title"); // Fails on 1.20.5+
```

**Correct:**
```java
Tag<?> titleTag = tag.get("title");
String title = titleTag instanceof CompoundTag
    ? ((CompoundTag) titleTag).getString("raw")
    : tag.getString("title");
```

### Pitfall 4: Assuming Pages are Always Strings
**Wrong:**
```java
for (Tag<?> page : pages) {
    String text = ((StringTag) page).getValue(); // Crashes on 1.20.5+
}
```

**Correct:**
```java
for (Tag<?> page : pages) {
    String text = page instanceof CompoundTag
        ? ((CompoundTag) page).getString("raw")
        : ((StringTag) page).getValue();
}
```

---

## Testing Data Sets

### Minimal Valid Book (Pre-1.20.5)
```snbt
{
  id: "minecraft:written_book",
  Count: 1b,
  tag: {
    title: "Test",
    author: "Tester",
    pages: ['{"text":"Hi"}']
  }
}
```

### Minimal Valid Book (1.20.5+)
```snbt
{
  id: "minecraft:written_book",
  count: 1,
  components: {
    minecraft:written_book_content: {
      title: {raw: "Test"},
      author: "Tester",
      pages: [{raw: '{"text":"Hi"}'}]
    }
  }
}
```

### Maximal Book (all fields)
```snbt
{
  id: "minecraft:written_book",
  count: 1,
  components: {
    minecraft:written_book_content: {
      title: {
        raw: "Complete Book",
        filtered: "******* ****"
      },
      author: "Author123",
      generation: 2,
      resolved: true,
      pages: [
        {
          raw: '{"text":"Page with all features","bold":true,"color":"gold"}',
          filtered: '{"text":"**** **** *** ********"}'
        },
        {
          raw: '{"text":"Dynamic: ","extra":[{"selector":"@p"}]}'
        }
      ]
    }
  }
}
```

---

## References

- Minecraft Wiki - Written Book: https://minecraft.wiki/w/Written_Book
- Minecraft Wiki - NBT Format: https://minecraft.wiki/w/NBT_format
- Minecraft Wiki - Item Format (1.20.5): https://minecraft.wiki/w/Item_format/1.20.5
- Minecraft Wiki - Item Format (Legacy): https://minecraft.wiki/w/Item_format/Written_Books
- Minecraft Wiki - Text Components: https://minecraft.wiki/w/Text_component_format
- Minecraft Wiki - Data Components: https://minecraft.wiki/w/Data_component_format

**Document Version:** 1.0
**Last Updated:** 2025-11-18
**Minecraft Coverage:** Java Edition 1.13 through 1.21+

# Custom Name NBT Format Specification

**Document Created:** 2025-11-18
**Purpose:** Complete reference for detecting custom names on items and entities in Minecraft NBT data
**Scope:** Minecraft Java Edition 1.18 through 1.21+
**Sources:** Official Minecraft Wiki (minecraft.wiki), Querz NBT Library Documentation

---

## Executive Summary

Custom names in Minecraft are stored differently for **entities** vs. **items**, and item format changed significantly in **version 1.20.5+**. This document provides comprehensive detection logic for all supported versions.

### Quick Reference Table

| Type | Minecraft Version | NBT Path | Data Type | Notes |
|------|------------------|----------|-----------|-------|
| **Entity** | All versions | `CustomName` | String/Text Component | Root level tag |
| **Item** | 1.13 - 1.20.4 | `tag.display.Name` | String/Text Component | Nested in tag compound |
| **Item** | 1.20.5+ | `components.minecraft:custom_name` | String/Text Component | Component system |
| **Shulker Item** | 1.13 - 1.20.4 | `tag.BlockEntityTag.CustomName` | String | For shulker box item itself |
| **Shulker Item** | 1.20.5+ | `components.minecraft:container.CustomName` | String | New component path |

---

## Entity Custom Names

### NBT Structure

**Tag Name:** `CustomName` (case-sensitive)
**Location:** Direct child of entity compound tag
**Data Type:** StringTag containing JSON text component
**Optional:** Yes - tag may not exist if entity has no custom name

### Format Specification

**Basic format (1.14+):**
```json
{
  "CustomName": "\"Bob\""
}
```

**JSON text component format:**
```json
{
  "CustomName": "{\"text\":\"Custom Name\",\"color\":\"blue\"}"
}
```

### Companion Tag: CustomNameVisible

**Tag Name:** `CustomNameVisible`
**Data Type:** ByteTag (0 or 1, equivalent to boolean)
**Purpose:** Controls whether name displays always (1) or only when hovering (0)
**Default Behavior:** If CustomNameVisible doesn't exist but CustomName does, name shows only on hover

### Detection Logic

```java
// Pseudocode for entity custom name detection
if (entityCompound.containsKey("CustomName")) {
    String customName = entityCompound.getString("CustomName");
    if (customName != null && !customName.isEmpty()) {
        // Entity has custom name
        boolean alwaysVisible = entityCompound.getByte("CustomNameVisible") == 1;
        // Process custom name...
    }
}
```

### Version Compatibility

- **Minecraft 1.13 and earlier:** Used simpler string format
- **Minecraft 1.14+:** Requires escaped quotes: `"\"Name\""`
- **Minecraft 1.18+:** No format changes (stable)
- **Minecraft 1.20+:** No format changes (stable)
- **Minecraft 1.21+:** No format changes (stable)

### Applicable Entity Types

Custom names work on **ALL** entity types, including:
- Mobs (zombie, creeper, villager, etc.)
- Animals (cow, pig, sheep, etc.)
- Minecarts (including chest minecarts, hopper minecarts)
- Item frames
- Armor stands
- End crystals
- Any other entity type

---

## Item Custom Names (Pre-1.20.5)

### NBT Structure (Minecraft 1.13 - 1.20.4)

**Tag Path:** `tag.display.Name`
**Location:** Nested inside item's `tag` compound, within `display` compound
**Data Type:** StringTag containing JSON text component
**Optional:** Yes - entire `tag` or `display` compound may not exist

### Format Specification

**Full item structure (1.13-1.20.1):**
```json
{
  "id": "minecraft:stick",
  "Count": 1,
  "tag": {
    "display": {
      "Name": "{\"text\":\"Magic Wand\"}"
    }
  }
}
```

**Simplified format (1.20.2-1.20.4):**
```json
{
  "id": "minecraft:diamond_sword",
  "Count": 1,
  "tag": {
    "display": {
      "Name": "\"Epic Sword\""
    }
  }
}
```

**Note:** In 1.20.2-1.20.4, Minecraft automatically converts `{"text":"..."}` to plain `"..."` format if no formatting (color, italic, bold) is applied.

### Additional Display Tags

The `display` compound can contain other formatting tags:
- `Lore` - Array of strings for item description lines
- `color` - For leather armor coloring

### Detection Logic (Pre-1.20.5)

```java
// Pseudocode for item custom name detection (1.13-1.20.4)
CompoundTag itemTag = item.getCompoundTag("tag");
if (itemTag != null) {
    CompoundTag displayTag = itemTag.getCompoundTag("display");
    if (displayTag != null && displayTag.containsKey("Name")) {
        String customName = displayTag.getString("Name");
        if (customName != null && !customName.isEmpty()) {
            // Item has custom name
            // Process custom name...
        }
    }
}
```

---

## Item Custom Names (1.20.5+)

### NBT Structure (Minecraft 1.20.5 and later)

**Major Change:** Minecraft 1.20.5 introduced the **component system**, replacing the old `tag` structure.

**Tag Path:** `components.minecraft:custom_name`
**Location:** Direct child of item's `components` compound
**Data Type:** StringTag or CompoundTag (full text component)
**Optional:** Yes - `components` compound may not exist or may lack custom_name

### Format Specification

**Full item structure (1.20.5+):**
```json
{
  "id": "minecraft:stick",
  "count": 1,
  "components": {
    "minecraft:custom_name": "\"Awesome Stick\""
  }
}
```

**With formatting (1.20.5+):**
```json
{
  "id": "minecraft:stick",
  "count": 1,
  "components": {
    "minecraft:custom_name": {
      "text": "Magic Wand",
      "color": "light_purple",
      "italic": false
    }
  }
}
```

**Give command example:**
```
/give @s stick[custom_name={text:"Magic Wand",color:"light_purple",italic:false}]
```

### Key Differences from Pre-1.20.5

| Aspect | Pre-1.20.5 | 1.20.5+ |
|--------|------------|---------|
| Container | `tag` compound | `components` compound |
| Path | `tag.display.Name` | `components.minecraft:custom_name` |
| Count field | `Count` (byte) | `count` (integer) |
| Namespace | Implicit | Explicit `minecraft:` prefix |

### Detection Logic (1.20.5+)

```java
// Pseudocode for item custom name detection (1.20.5+)
CompoundTag componentsTag = item.getCompoundTag("components");
if (componentsTag != null && componentsTag.containsKey("minecraft:custom_name")) {
    Tag<?> customNameTag = componentsTag.get("minecraft:custom_name");

    // Handle both string and compound formats
    String customName = null;
    if (customNameTag instanceof StringTag) {
        customName = ((StringTag) customNameTag).getValue();
    } else if (customNameTag instanceof CompoundTag) {
        // Extract text from JSON component
        customName = ((CompoundTag) customNameTag).getString("text");
    }

    if (customName != null && !customName.isEmpty()) {
        // Item has custom name
        // Process custom name...
    }
}
```

### Version Detection Strategy

Since the tool supports multiple Minecraft versions (1.18, 1.20, 1.20.5+), use **dual-path checking**:

```java
// Pseudocode for multi-version item custom name detection
String customName = null;

// Try new format first (1.20.5+)
CompoundTag componentsTag = item.getCompoundTag("components");
if (componentsTag != null && componentsTag.containsKey("minecraft:custom_name")) {
    customName = extractCustomNameFromComponents(componentsTag);
}

// Fallback to old format (1.13-1.20.4)
if (customName == null) {
    CompoundTag tagCompound = item.getCompoundTag("tag");
    if (tagCompound != null) {
        CompoundTag displayTag = tagCompound.getCompoundTag("display");
        if (displayTag != null) {
            customName = displayTag.getString("Name");
        }
    }
}

if (customName != null && !customName.isEmpty()) {
    // Process custom name...
}
```

---

## Special Cases: Container Items with Custom Names

### Shulker Boxes

Shulker boxes are items that contain other items. They can have:
1. **Their own custom name** (the shulker box item itself)
2. **Custom names on items inside** (nested item names)

**Pre-1.20.5 Structure:**
```json
{
  "id": "minecraft:shulker_box",
  "Count": 1,
  "tag": {
    "display": {
      "Name": "\"My Storage\""  // Shulker's custom name
    },
    "BlockEntityTag": {
      "Items": [
        {
          "Slot": 0,
          "id": "minecraft:diamond",
          "Count": 1,
          "tag": {
            "display": {
              "Name": "\"Special Diamond\""  // Item inside has custom name
            }
          }
        }
      ]
    }
  }
}
```

**1.20.5+ Structure:**
```json
{
  "id": "minecraft:shulker_box",
  "count": 1,
  "components": {
    "minecraft:custom_name": "\"My Storage\"",  // Shulker's name
    "minecraft:container": [
      {
        "slot": 0,
        "item": {
          "id": "minecraft:diamond",
          "count": 1,
          "components": {
            "minecraft:custom_name": "\"Special Diamond\""  // Nested item name
          }
        }
      }
    ]
  }
}
```

### Bundles

Bundles work similarly to shulker boxes with nested items.

**Pre-1.20.5:** Items stored in `tag.Items` array
**1.20.5+:** Items stored in `components.minecraft:bundle_contents` array

### Detection Strategy for Nested Items

The existing `processContainer()` method in Main.groovy already handles recursive container traversal. Custom name detection should work at each level:
1. Check container item's own custom name
2. Recursively process items inside container
3. Check each nested item's custom name

---

## JSON Text Component Parsing

### Format Overview

Custom names are stored as **JSON text components**, which can range from simple strings to complex formatted objects.

### Simple Format

```json
"\"Plain Text\""
```
This is just a string with escaped quotes.

### Full JSON Format

```json
"{\"text\":\"Colored Name\",\"color\":\"red\",\"bold\":true}"
```

### Text Component Properties

| Property | Type | Description | Example |
|----------|------|-------------|---------|
| `text` | string | Display text | `"Hello"` |
| `color` | string | Color name or hex | `"red"`, `"#FF0000"` |
| `bold` | boolean | Bold formatting | `true`, `false` |
| `italic` | boolean | Italic formatting | `true`, `false` |
| `underlined` | boolean | Underline | `true`, `false` |
| `strikethrough` | boolean | Strikethrough | `true`, `false` |
| `obfuscated` | boolean | Random chars | `true`, `false` |

### Extraction Strategy

For this tool's purposes, we want to extract the **raw NBT string value** as-is, preserving the JSON format. This allows:
1. Seeing exactly how the name is stored
2. Preserving formatting information
3. Potential future parsing if needed

**Recommended approach:** Store the raw string value from NBT without JSON parsing.

---

## Querz NBT Library Safe Access Patterns

### CompoundTag Methods Reference

Based on Querz NBT 6.1 documentation:

**Null-safe getter methods:**
- `getCompoundTag(String key)` - Returns null if key doesn't exist
- `getString(String key)` - Returns `StringTag.ZERO_VALUE` (empty string) if absent
- `getByte(String key)` - Returns `ByteTag.ZERO_VALUE` (0) if absent
- `containsKey(String key)` - Boolean check for key existence

### Recommended Access Pattern

```java
// Pattern 1: containsKey check (explicit)
if (compound.containsKey("CustomName")) {
    String name = compound.getString("CustomName");
    if (!name.isEmpty()) {
        // Process name
    }
}

// Pattern 2: Null-safe nested access
CompoundTag tag = item.getCompoundTag("tag");
if (tag != null) {
    CompoundTag display = tag.getCompoundTag("display");
    if (display != null) {
        String name = display.getString("Name");
        if (!name.isEmpty()) {
            // Process name
        }
    }
}

// Pattern 3: Combined approach (most robust)
if (compound.containsKey("tag")) {
    CompoundTag tag = compound.getCompoundTag("tag");
    if (tag != null && tag.containsKey("display")) {
        CompoundTag display = tag.getCompoundTag("display");
        if (display != null && display.containsKey("Name")) {
            String name = display.getString("Name");
            // Process name (already checked existence)
        }
    }
}
```

---

## Implementation Checklist

When implementing custom name extraction in ReadSignsAndBooks.jar:

- [ ] Detect entity custom names via `CustomName` tag
- [ ] Detect item custom names via `tag.display.Name` (pre-1.20.5)
- [ ] Detect item custom names via `components.minecraft:custom_name` (1.20.5+)
- [ ] Handle both string and compound formats for custom names
- [ ] Extract custom names from nested containers (shulkers, bundles)
- [ ] Store raw NBT string value without JSON parsing
- [ ] Deduplicate custom names using content-based hashing
- [ ] Track location information (entity type, item type, coordinates)
- [ ] Support all existing output formats (Stendhal, CSV, text)
- [ ] Add new output file specifically for custom names
- [ ] Test with real-world Minecraft data across versions 1.18, 1.20, 1.21

---

## Edge Cases & Considerations

### Empty Custom Names

Some NBT data may have `CustomName` tag with empty string value. **Filter these out** - they're not truly custom named.

### Default Names

Do not confuse default entity/item names with custom names:
- Default: Stored in game code, not in NBT
- Custom: Explicitly stored in NBT data

**Detection rule:** If `CustomName` tag exists and is non-empty, it's a custom name, regardless of content.

### Formatting Codes vs. Plain Text

Pre-1.13 used **legacy formatting codes** (§ character). Modern versions use JSON text components. Since we support 1.18+, we only encounter JSON format.

### Anvil Renamed Items

Items renamed in an anvil get `tag.display.Name` tag. This is the exact use case we want to detect.

### Book Titles vs. Custom Names

Books have both:
- **Title:** `tag.title` or `components.minecraft:written_book_content.title`
- **Custom Name:** `tag.display.Name` or `components.minecraft:custom_name`

Most books only have title, not custom name. If a book item is renamed in an anvil, it gets a custom name separate from its title.

---

## Testing Recommendations

### Test Data Requirements

Create test world with:
1. Entity with custom name (renamed zombie, villager, etc.)
2. Item with custom name pre-1.20.5 format (anvil-renamed stick in 1.20.4 world)
3. Item with custom name 1.20.5+ format (anvil-renamed item in 1.21 world)
4. Shulker box with custom name containing items with custom names
5. Bundle with custom-named items inside
6. Book with both title and custom name (anvil-renamed book)
7. Entities without custom names (verify no false positives)
8. Items without custom names (verify no false positives)

### Validation Criteria

- All custom names extracted
- No false positives (default names excluded)
- Correct version detection and fallback
- Nested containers processed recursively
- Location tracking accurate
- Output formats valid

---

## References

**Official Minecraft Wiki:**
- Entity format: https://minecraft.wiki/w/Entity_format
- Data component format: https://minecraft.wiki/w/Data_component_format
- Tutorial on NBT tags: https://minecraft.wiki/w/Tutorial:Command_NBT_tags
- Player.dat format: https://minecraft.wiki/w/Player.dat_format
- Block entity format: https://minecraft.wiki/w/Block_entity_format

**Querz NBT Library:**
- GitHub Repository: https://github.com/Querz/NBT
- CompoundTag source: https://github.com/Querz/NBT/blob/master/src/main/java/net/querz/nbt/tag/CompoundTag.java

**Community Resources:**
- digminecraft.com NBT tag examples
- Minecraft Commands wiki

**Document Version:** 1.0
**Last Updated:** 2025-11-18
**Maintained by:** ReadSignsAndBooks.jar project

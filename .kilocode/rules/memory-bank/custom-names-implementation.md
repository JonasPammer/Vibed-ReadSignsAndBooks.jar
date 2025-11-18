# Custom Names Feature Implementation

**Created:** 2025-11-18
**Issue:** https://github.com/JonasPammer/Vibed-ReadSignsAndBooks.jar/issues/11
**Feature:** Extract custom names from items and entities in Minecraft worlds

---

## Feature Overview

This feature extends ReadSignsAndBooks.jar to extract custom names from items and entities while processing Minecraft world files. Since the tool already loops through all world data to find books and signs, it now also identifies items and entities that have been given custom names (via anvil renaming for items, or name tags for entities).

## Implementation Summary

### New CLI Option

```bash
--extract-custom-names    Extract custom names from items and entities (default: false)
```

**Usage:**
```bash
java -jar ReadSignsAndBooks.jar /path/to/world --extract-custom-names
```

### GUI Integration

Added new checkbox in GUI:
- **Label:** "Extract custom names from items and entities"
- **Location:** Below "Remove Minecraft formatting codes" option
- **Default:** Unchecked (feature opt-in)

## Architecture Changes

### Static Fields Added (Main.groovy)

```groovy
static Set<String> customNameHashes = [] as Set  // Deduplication
static List<Map<String, Object>> customNameData = []  // Extraction data
static boolean extractCustomNames = false  // CLI flag
static CheckBox extractCustomNamesCheckBox  // GUI field (in GUI.groovy)
```

### Helper Methods Added

#### `extractCustomNameFromItem(CompoundTag item)`

Extracts custom name from an item NBT compound with multi-version support:

**Minecraft 1.20.5+ format:**
```
components.minecraft:custom_name
```

**Pre-1.20.5 format:**
```
tag.display.Name
```

**Return value:** Custom name string, or null if none exists

**Handles:**
- Both string and compound (JSON) formats
- Empty/whitespace-only names (returns null)
- Missing NBT paths (safe null checks)

#### `extractCustomNameFromEntity(CompoundTag entity)`

Extracts custom name from an entity NBT compound:

**Format (all versions):**
```
CustomName
```

**Return value:** Custom name string, or null if none exists

**Notes:**
- Entity custom names are at root level (not nested)
- Same across all Minecraft versions

#### `recordCustomName(...)`

Records a custom name with deduplication:

**Parameters:**
- `customName` - Raw NBT string
- `itemOrEntityId` - Minecraft ID (e.g., "minecraft:diamond_sword")
- `type` - "item" or "entity"
- `location` - Description of where found
- `x, y, z` - Coordinates (optional, defaults to 0)

**Deduplication strategy:**
- Hash: `"${customName}|${type}|${itemOrEntityId}".hashCode()`
- Combines name + type + ID for uniqueness
- Prevents duplicate entries for identical custom names

#### `writeCustomNamesOutput()`

Generates three output files:

1. **all_custom_names.csv** - Spreadsheet format
2. **all_custom_names.txt** - Human-readable report
3. **all_custom_names.json** - Stendhal JSON format

**Only runs if:** `extractCustomNames == true && customNameData.isEmpty() == false`

#### `escapeJson(String text)`

Escapes strings for JSON output:
- Backslashes: `\\`
- Quotes: `\"`
- Newlines: `\n`
- Returns: `\r`
- Tabs: `\t`

## Integration Points

### 1. parseItem() Method

**Location:** Main.groovy:1399

**Change:** Added custom name extraction at the beginning of method

```groovy
public static void parseItem(CompoundTag item, String bookInfo) {
    String itemId = item.getString('id')

    // Extract custom name if --extract-custom-names flag is set
    if (extractCustomNames) {
        String customName = extractCustomNameFromItem(item)
        if (customName) {
            recordCustomName(customName, itemId, 'item', bookInfo, 0, 0, 0)
        }
    }

    // ... existing book processing code
}
```

**Impact:**
- Runs on every item processed
- Minimal performance impact (only when flag enabled)
- Coordinates not available in parseItem context (uses 0, 0, 0)

### 2. readEntities() Method

**Location:** Main.groovy:1312-1326

**Change:** Added entity custom name extraction after position parsing

```groovy
entities.each { CompoundTag entity ->
    String entityId = entity.getString('id')
    ListTag<?> entityPos = getListTag(entity, 'Pos')
    int xPos = entityPos.size() >= 3 ? getDoubleAt(entityPos, 0) as int : 0
    int yPos = entityPos.size() >= 3 ? getDoubleAt(entityPos, 1) as int : 0
    int zPos = entityPos.size() >= 3 ? getDoubleAt(entityPos, 2) as int : 0

    // Extract custom name from entity if --extract-custom-names flag is set
    if (extractCustomNames) {
        String customName = extractCustomNameFromEntity(entity)
        if (customName) {
            String location = "Chunk [${x}, ${z}] Entity ${entityId} at (${xPos} ${yPos} ${zPos}) ${file.name}"
            recordCustomName(customName, entityId, 'entity', location, xPos, yPos, zPos)
        }
    }

    // ... existing entity processing code
}
```

**Impact:**
- Runs on every entity processed
- Includes accurate coordinates from entity position data

### 3. runExtraction() Method

**Location:** Main.groovy:228-230 (reset), 315-317 (output)

**Changes:**

**Reset section:**
```groovy
[bookHashes, signHashes, customNameHashes, ...].each { collection -> collection.clear() }
```

**Output section:**
```groovy
writeBooksCSV()
writeSignsCSV()
writeCustomNamesOutput()  // NEW
```

### 4. GUI.groovy

**Changes:**

**Field declaration (line 30):**
```groovy
static CheckBox extractCustomNamesCheckBox
```

**UI creation (lines 98-102):**
```groovy
def customNamesBox = new HBox(10)
customNamesBox.alignment = Pos.CENTER_LEFT
extractCustomNamesCheckBox = new CheckBox('Extract custom names from items and entities')
extractCustomNamesCheckBox.selected = false
customNamesBox.children.addAll(new Label('').with { it.minWidth = 120; it }, extractCustomNamesCheckBox)
```

**Layout (line 140):**
```groovy
formattingBox,
customNamesBox,  // NEW
new Separator(),
```

**CLI argument passing (lines 302-304):**
```groovy
if (extractCustomNamesCheckBox.selected) {
    args += ['--extract-custom-names']
}
```

## Output Formats

### CSV Format

**File:** `all_custom_names.csv`

**Header:**
```
Type,ItemOrEntityID,CustomName,X,Y,Z,Location
```

**Example rows:**
```csv
item,minecraft:diamond_sword,"\"Epic Blade\"",0,0,0,Inventory of player abc123.dat
entity,minecraft:zombie,"\"Bob\"",123,64,-456,Chunk [4, -14] Entity minecraft:zombie at (123 64 -456) r.4.-15.mca
```

### Text Format

**File:** `all_custom_names.txt`

**Structure:**
```
================================================================================
CUSTOM NAMES EXTRACTION REPORT
================================================================================

Total unique custom names found: 42

--------------------------------------------------------------------------------
ITEMS WITH CUSTOM NAMES (38)
--------------------------------------------------------------------------------

#1
  Name: "Magic Wand"
  Item ID: minecraft:stick
  Location: (0, 0, 0)
  Found in: Inventory of player abc123.dat

#2
  Name: {"text":"Epic Sword","color":"red"}
  Item ID: minecraft:diamond_sword
  Location: (0, 0, 0)
  Found in: Chunk [5, 8] In minecraft:chest at (82 65 134) r.0.0.mca

...

--------------------------------------------------------------------------------
ENTITYS WITH CUSTOM NAMES (4)
--------------------------------------------------------------------------------

#1
  Name: "Pet Zombie"
  Entity ID: minecraft:zombie
  Location: (123, 64, -456)
  Found in: Chunk [4, -14] Entity minecraft:zombie at (123 64 -456) r.4.-15.mca

...

================================================================================
END OF REPORT
================================================================================
```

### JSON Format

**File:** `all_custom_names.json`

**Structure:**
```json
[
  {
    "type": "item",
    "itemOrEntityId": "minecraft:diamond_sword",
    "customName": "\"Epic Blade\"",
    "x": 0,
    "y": 0,
    "z": 0,
    "location": "Inventory of player abc123.dat"
  },
  {
    "type": "entity",
    "itemOrEntityId": "minecraft:zombie",
    "customName": "\"Bob\"",
    "x": 123,
    "y": 64,
    "z": -456,
    "location": "Chunk [4, -14] Entity minecraft:zombie at (123 64 -456) r.4.-15.mca"
  }
]
```

## Version Compatibility

### Item Custom Names

| Minecraft Version | NBT Path | Notes |
|------------------|----------|-------|
| 1.13 - 1.20.1 | `tag.display.Name` | JSON text format: `'{"text":"Name"}'` |
| 1.20.2 - 1.20.4 | `tag.display.Name` | Plain text if no formatting: `'"Name"'` |
| 1.20.5+ | `components.minecraft:custom_name` | Component system |

**Implementation uses fallback strategy:**
1. Try new format first (`components.minecraft:custom_name`)
2. If null, try old format (`tag.display.Name`)
3. Handle both string and compound (JSON) formats

### Entity Custom Names

| Minecraft Version | NBT Path | Notes |
|------------------|----------|-------|
| All versions | `CustomName` | Root-level tag, consistent across versions |

**Format:** Always JSON text component string

## Data Flow Diagram

```
parseItem(item, bookInfo)
  └─> extractCustomNameFromItem(item)
       └─> recordCustomName(name, id, 'item', location, 0, 0, 0)
            └─> customNameData.add(...)
                 └─> customNameHashes.add(hash)

readEntities()
  └─> for each entity in entities
       └─> extractCustomNameFromEntity(entity)
            └─> recordCustomName(name, id, 'entity', location, x, y, z)
                 └─> customNameData.add(...)
                      └─> customNameHashes.add(hash)

runExtraction()
  └─> writeCustomNamesOutput()
       ├─> all_custom_names.csv
       ├─> all_custom_names.txt
       └─> all_custom_names.json
```

## Performance Considerations

### Minimal Overhead

**When feature disabled (`--extract-custom-names` not set):**
- Single if-check per item/entity: `if (extractCustomNames) { ... }`
- No performance impact (guard clause returns immediately)

**When feature enabled:**
- Additional NBT path checks per item/entity
- Hash calculation for deduplication
- Memory usage: O(n) where n = unique custom names

### Memory Usage

**Typical world:**
- ~100-1000 custom names expected
- ~1KB per entry (with location strings)
- Total: ~100KB - 1MB additional memory

**Large server world:**
- ~10,000 custom names possible
- Total: ~10MB additional memory
- Still well within -Xmx10G allocation

### Deduplication Efficiency

**Hash-based deduplication:**
- Time complexity: O(1) for add check (HashSet)
- Space complexity: O(n) unique custom names
- No duplicate processing or output

## Edge Cases Handled

### Empty Custom Names

**Check:** `if (customName == null || customName.trim().isEmpty())`

**Action:** Return null (not recorded)

**Rationale:** Empty tags exist but aren't truly custom named

### Missing NBT Paths

**Safe access pattern:**
```groovy
if (hasKey(item, 'components')) {
    CompoundTag components = getCompoundTag(item, 'components')
    if (hasKey(components, 'minecraft:custom_name')) {
        // Extract
    }
}
```

**Benefit:** No NullPointerException on corrupt/incomplete data

### Nested Container Items

**Example:** Shulker box with custom name containing items with custom names

**Behavior:**
1. Extract shulker box's custom name (if any)
2. Recursively process items inside
3. Extract each item's custom name (if any)

**Implementation:** Works automatically via recursive `parseItem()` calls

### JSON Text Components

**Storage:** Raw NBT string preserved as-is

**Example:** `{"text":"Name","color":"red","bold":true}`

**Rationale:**
- Preserves all formatting information
- Allows future JSON parsing if needed
- Shows exactly how stored in NBT

## Testing Recommendations

### Test Cases

1. **Item with custom name (pre-1.20.5):**
   - Anvil-rename a stick in 1.20.4 world
   - Run extraction with `--extract-custom-names`
   - Verify appears in all three output files

2. **Item with custom name (1.20.5+):**
   - Anvil-rename a sword in 1.21 world
   - Verify new component format detected

3. **Entity with custom name:**
   - Name a zombie with name tag
   - Verify entity name extracted with coordinates

4. **Nested containers:**
   - Shulker box (custom named) with custom-named items inside
   - Verify all names extracted

5. **Empty names:**
   - Item with empty `CustomName` tag
   - Verify not included in output

6. **No custom names:**
   - Run on vanilla world with no custom names
   - Verify empty output (no files created)

7. **GUI integration:**
   - Check/uncheck custom names checkbox
   - Verify flag passed to CLI correctly

## Logging

**Debug logs:**
```groovy
LOGGER.debug("Recorded custom name: '${customName}' on ${type} ${itemOrEntityId} at (${x}, ${y}, ${z})")
LOGGER.debug("Skipping duplicate custom name: ${customName} on ${itemOrEntityId}")
LOGGER.debug('Custom name extraction not enabled or no custom names found')
```

**Info logs:**
```groovy
LOGGER.info("Writing custom names output files with ${customNameData.size()} unique custom names...")
LOGGER.info("Custom names CSV written to: ${csvFile.absolutePath}")
LOGGER.info("Custom names text file written to: ${textFile.absolutePath}")
LOGGER.info("Custom names JSON file written to: ${jsonFile.absolutePath}")
LOGGER.info("Custom names output complete: ${customNameData.size()} entries written to 3 files")
```

## Future Enhancements

### Potential Improvements

1. **Coordinate tracking for items:**
   - Currently items use (0, 0, 0) as parseItem doesn't have coordinates
   - Could pass coordinates through bookInfo parsing

2. **CustomNameVisible tracking:**
   - Entities have `CustomNameVisible` byte tag
   - Could record whether name always visible or only on hover

3. **Color/formatting extraction:**
   - Parse JSON text components
   - Extract color, bold, italic, etc. as separate columns

4. **Statistics:**
   - Most common custom names
   - Distribution by item/entity type
   - Custom names by player (if owner tracked)

5. **Filter options:**
   - `--custom-names-items-only` - Extract only items
   - `--custom-names-entities-only` - Extract only entities
   - `--custom-names-filter="pattern"` - Regex filter

## Code Locations

| Component | File | Line Range | Description |
|-----------|------|------------|-------------|
| Static fields | Main.groovy | 51, 63, 91 | Data structures and flag |
| Helper methods | Main.groovy | 2436-2562 | Extract/record/escape methods |
| parseItem integration | Main.groovy | 1402-1408 | Item extraction call |
| readEntities integration | Main.groovy | 1319-1326 | Entity extraction call |
| Output method | Main.groovy | 421-519 | File writing logic |
| Reset integration | Main.groovy | 230 | Clear collections |
| Output call | Main.groovy | 317 | Write output files |
| GUI checkbox | GUI.groovy | 30, 98-102 | UI component |
| GUI layout | GUI.groovy | 140 | Layout addition |
| GUI args | GUI.groovy | 302-304 | CLI argument passing |

## Dependencies

**No new dependencies required.**

Uses existing:
- Querz NBT 6.1 - NBT parsing
- Groovy 4.0.24 - Language features
- SLF4J/Logback - Logging

## Backward Compatibility

**Fully backward compatible:**
- Feature opt-in (disabled by default)
- No changes to existing extraction behavior
- No changes to book/sign output formats
- Existing CLI/GUI usage unaffected

## Documentation Updates Needed

1. **README.md:**
   - Add `--extract-custom-names` to CLI options section
   - Add custom names output files to "Output Files" section
   - Add example usage

2. **Memory bank files:**
   - This file (custom-names-implementation.md)
   - Reference from architecture.md
   - Reference from product.md

3. **Issue #11:**
   - Mark as resolved
   - Reference implementation docs
   - Provide example output

---

**Implementation Status:** Complete
**Code Review:** Pending
**Testing:** Pending
**Documentation:** In Progress

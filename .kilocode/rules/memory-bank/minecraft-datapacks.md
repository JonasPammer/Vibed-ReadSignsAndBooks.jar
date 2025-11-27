# Minecraft Datapacks - Technical Reference

**CRITICAL KNOWLEDGE**: This file contains essential Minecraft datapack specifications verified from minecraft.wiki. All information is authoritative and must be followed exactly to ensure datapack compatibility.

## Pack Format Version Table (Data Packs)

**Source**: https://minecraft.wiki/w/Pack_format (verified 2025-11-18)

| pack_format | Minecraft Versions | Directory Structure | Notes |
|-------------|-------------------|---------------------|-------|
| 4 | 1.13 – 1.14.4 | `functions/` (plural) | Initial datapack system |
| 5 | 1.15 – 1.16.1 | `functions/` (plural) | Added predicates |
| 6 | 1.16.2 – 1.16.5 | `functions/` (plural) | |
| 7 | 1.17 – 1.17.1 | `functions/` (plural) | |
| 8 | 1.18 – 1.18.1 | `functions/` (plural) | |
| 9 | 1.18.2 | `functions/` (plural) | |
| 10 | 1.19 – 1.19.3 | `functions/` (plural) | |
| 12 | 1.19.4 | `functions/` (plural) | |
| 15 | 1.20 – 1.20.1 | `functions/` (plural) | |
| 18 | 1.20.2 | `functions/` (plural) | |
| 26 | 1.20.3 – 1.20.4 | `functions/` (plural) | |
| 41 | 1.20.5 – 1.20.6 | `functions/` (plural) | Item components introduced |
| 42 | 24w18a (snapshot) | `functions/` (plural) | Data-driven enchantments |
| 43 | 24w19a–24w19b | `functions/` (plural) | Tag folder renames |
| 44 | 24w20a (snapshot) | `functions/` (plural) | Enchantment bounds |
| 45 | 24w21a–24w21b | `functions/` (plural) | **CRITICAL: See Directory Rename** |
| 46 | 1.21-pre1 | `function/` (singular) | **Directory changed** |
| 47 | 1.21-pre2 | `function/` (singular) | Placement modifiers |
| 48 | 1.21-pre3 to 1.21.1 | `function/` (singular) | **Current stable** |
| 49 | 24w33a (1.21.2 dev) | `function/` (singular) | Attribute ID changes |
| 50+ | Future versions | `function/` (singular) | Future developments |

**CRITICAL WARNING**: pack_format numbers are NOT the same as resource pack formats. Data packs and resource packs have separate numbering systems since snapshot 20w45a (1.17 development).

## The Great Directory Rename (Snapshot 24w21a)

**Date**: May 2024 (Minecraft Java Edition 24w21a)
**Source**: https://minecraft.wiki/w/Java_Edition_24w21a

### What Changed

In snapshot 24w21a, Mojang renamed ALL datapack directories from plural to singular to match registry names:

| Old Name (Pre-1.21) | New Name (1.21+) |
|---------------------|------------------|
| `functions/` | `function/` |
| `loot_tables/` | `loot_table/` |
| `advancements/` | `advancement/` |
| `recipes/` | `recipe/` |
| `structures/` | `structure/` |
| `predicates/` | `predicate/` |
| `item_modifiers/` | `item_modifier/` |
| `tags/functions/` | `tags/function/` |

### Impact on This Project

**readbooks_datapack_1_13**: Uses `data/readbooks/functions/` (plural)
**readbooks_datapack_1_14**: Uses `data/readbooks/functions/` (plural)
**readbooks_datapack_1_20_5**: Uses `data/readbooks/functions/` (plural)
**readbooks_datapack_1_21**: Uses `data/readbooks/function/` (SINGULAR)

### Implementation

```groovy
// CRITICAL: Version-specific directory naming
String functionDirName = (version == '1_21') ? 'function' : 'functions'
File functionFolder = new File(namespaceFolder, functionDirName)
```

## Datapack Directory Structure

### Complete Structure (Pre-1.21)

```
datapack_root/
├── pack.mcmeta (REQUIRED)
├── pack.png (optional, 128x128 icon)
└── data/
    └── <namespace>/
        ├── functions/              ← PLURAL
        │   ├── load.mcfunction     (runs on /reload)
        │   ├── tick.mcfunction     (runs every tick)
        │   └── custom.mcfunction
        ├── loot_tables/            ← PLURAL
        ├── recipes/                ← PLURAL
        ├── advancements/           ← PLURAL
        ├── structures/             ← PLURAL
        ├── predicates/             ← PLURAL
        ├── item_modifiers/         ← PLURAL
        └── tags/
            ├── functions/          ← PLURAL
            ├── blocks/
            └── items/
```

### Complete Structure (1.21+)

```
datapack_root/
├── pack.mcmeta (REQUIRED)
├── pack.png (optional, 128x128 icon)
└── data/
    └── <namespace>/
        ├── function/               ← SINGULAR
        │   ├── load.mcfunction     (runs on /reload)
        │   ├── tick.mcfunction     (runs every tick)
        │   └── custom.mcfunction
        ├── loot_table/             ← SINGULAR
        ├── recipe/                 ← SINGULAR
        ├── advancement/            ← SINGULAR
        ├── structure/              ← SINGULAR
        ├── predicate/              ← SINGULAR
        ├── item_modifier/          ← SINGULAR
        └── tags/
            ├── function/           ← SINGULAR
            ├── block/
            └── item/
```

## pack.mcmeta Format

### Required Fields

```json
{
  "pack": {
    "pack_format": 48,
    "description": "ReadSignsAndBooks extracted content for Minecraft 1.21+"
  }
}
```

### Modern Format (1.21.9+)

Since snapshot 25w31a, pack format uses minor versions:

```json
{
  "pack": {
    "min_format": [88, 0],
    "max_format": [88, 0],
    "description": "Description text"
  }
}
```

**Note**: Our datapacks use the stable single-number format for backward compatibility.

## Namespace Rules

**Source**: https://minecraft.wiki/w/Data_pack, https://mc-datapacks.github.io/en/conventions/namespace.html

### Valid Characters

Namespaces MUST contain ONLY:
- Lowercase letters (a-z)
- Numbers (0-9)
- Underscores (_)
- Hyphens (-)
- Periods (.)

### Invalid Characters

**FORBIDDEN** (will cause datapack to fail):
- Uppercase letters (A-Z)
- Spaces
- Special characters (except _ - .)
- Unicode characters

### Naming Convention

**Preferred**: `lower_snake_case`
**Examples**:
- ✅ `readbooks` (our namespace)
- ✅ `my_datapack`
- ✅ `custom-pack`
- ✅ `pack.name`
- ❌ `MyDatapack` (uppercase)
- ❌ `my datapack` (space)
- ❌ `my@pack` (special char)

### Namespace Collision

Use unique namespaces to avoid conflicts:
- `readbooks:books` (our function)
- `otherpack:books` (different pack)

Only use other namespaces when deliberately overriding content.

## Function Files (.mcfunction)

### File Format

- **Extension**: `.mcfunction`
- **Location**: `data/<namespace>/function/<path>.mcfunction` (1.21+)
- **Location**: `data/<namespace>/functions/<path>.mcfunction` (pre-1.21)
- **Format**: One command per line
- **No slash**: Commands don't start with `/`
- **Comments**: Start with `#`

### Character Limits

**Per-line limit**: NO HARD LIMIT
- Function files can have arbitrarily long lines
- Command blocks have a 32,500 character limit
- Functions DO NOT have this limit

**Execution limits**:
- **Java Edition**: `maxCommandChainLength` = 65,536 commands (default)
- **Bedrock Edition**: 10,000 commands maximum

### Example

```mcfunction
# Load function - runs on /reload
give @p written_book{title:"Example",author:"System",pages:['{"text":"Hello"}']}
give @p written_book{title:"Long command with over 32500 characters is fine in mcfunction files..."}
```

## Load Order and Priority

**Source**: https://minecraft.wiki/w/Data_pack

### Default Load Order

1. Vanilla data (minecraft namespace)
2. Data packs in alphabetical order
3. Later packs override earlier packs

### File Override Behavior

- **Regular files**: Last loaded wins (complete override)
- **Tag files**: Merge UNLESS `"replace": true` is set

### Managing Priority

```
/datapack list
/datapack enable "file/datapack_name"
/datapack disable "file/datapack_name"
/datapack enable "file/datapack_name" first
/datapack enable "file/datapack_name" last
/datapack enable "file/datapack_name" before "other_pack"
/datapack enable "file/datapack_name" after "other_pack"
```

## Common Pitfalls

### 1. Wrong Directory Name (CRITICAL)

**Symptom**: "Unknown function" errors in Minecraft 1.13-1.20.6
**Cause**: Using `function/` (singular) instead of `functions/` (plural)
**Fix**: Use version-specific directory naming

### 2. Wrong pack_format

**Symptom**: "Made for a different version" warning
**Cause**: pack_format doesn't match Minecraft version
**Fix**: Use correct pack_format from table above

### 3. Invalid Namespace Characters

**Symptom**: Datapack not loaded, unhelpful error
**Cause**: Uppercase letters or invalid characters in namespace
**Fix**: Use only lowercase, numbers, _, -, .

### 4. Missing pack.mcmeta

**Symptom**: Datapack not recognized
**Cause**: pack.mcmeta file missing or misnamed
**Fix**: Ensure file is exactly `pack.mcmeta` (not `.txt`)

### 5. JSON Syntax Errors

**Symptom**: Datapack fails to load
**Cause**: Missing braces, commas, quotes in pack.mcmeta
**Fix**: Validate JSON syntax carefully

### 6. File Extension Issues

**Symptom**: Files not recognized on Windows
**Cause**: Hidden file extensions (e.g., `pack.mcmeta.txt`)
**Fix**: Enable "File name extensions" in Windows Explorer

## Written Book Command Formats

### 1.13 Format (NBT with JSON pages)

```
give @p written_book{title:"Title",author:"Author",pages:['{"text":"Page 1"}']}
```

### 1.14 Format (Different JSON wrapping)

```
give @p written_book{title:"Title",author:"Author",pages:['["Page 1"]']}
```

### 1.20.5+ Format (Component-based)

```
give @p written_book[minecraft:written_book_content={title:"Title",author:"Author",pages:["Page 1"]}]
```

### 1.21+ Format (No minecraft: prefix)

```
give @p written_book[written_book_content={title:"Title",author:"Author",pages:["Page 1"]}]
```

**Note**: The `minecraft:` prefix is optional in 1.20.5+ but our code includes it for 1.20.5 to be explicit.

## Testing Datapacks

### In-Game Testing

1. **Copy to world**:
   ```bash
   cp -r datapack_folder ~/.minecraft/saves/WorldName/datapacks/
   ```

2. **Reload in-game**:
   ```
   /reload
   ```

3. **List datapacks**:
   ```
   /datapack list
   ```

4. **Run function**:
   ```
   /function namespace:function_name
   ```

### Debug Commands

```
/datapack list available  (shows all packs in datapacks folder)
/datapack list enabled    (shows currently loaded packs)
/datapack enable "file/pack_name"
```

## Version Migration Guide

### Updating from Pre-1.21 to 1.21+

1. **Rename directories**:
   - `functions/` → `function/`
   - `loot_tables/` → `loot_table/`
   - `advancements/` → `advancement/`
   - etc.

2. **Update pack_format**:
   - Change from 41 (or earlier) to 48

3. **Update tags**:
   - `tags/functions/` → `tags/function/`

4. **Test thoroughly**:
   - Load in Minecraft 1.21
   - Run `/reload`
   - Execute all functions

## References

All information verified from official sources:

- **Pack Format Table**: https://minecraft.wiki/w/Pack_format
- **Data Pack Structure**: https://minecraft.wiki/w/Data_pack
- **24w21a Changelog**: https://minecraft.wiki/w/Java_Edition_24w21a
- **Function Files**: https://minecraft.wiki/w/Function_(Java_Edition)
- **Tutorial**: https://minecraft.wiki/w/Tutorial:Creating_a_data_pack
- **Namespace Conventions**: https://mc-datapacks.github.io/en/conventions/namespace.html

**Last Verified**: 2025-11-18
**Minecraft Version**: 1.13 through 1.21.1
**pack_format Range**: 4 through 48

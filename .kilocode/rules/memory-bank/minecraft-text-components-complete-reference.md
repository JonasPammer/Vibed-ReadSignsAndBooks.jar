# Minecraft Text Components - Complete Reference

**Last Updated:** 2025-11-18
**Purpose:** Comprehensive reference for JSON text component format
**Applies to:** Minecraft Java Edition, used in books, signs, commands, chat, titles

---

## Table of Contents

1. [Overview](#overview)
2. [Basic Structure](#basic-structure)
3. [Content Types](#content-types)
4. [Styling Properties](#styling-properties)
5. [Color Options](#color-options)
6. [Formatting Codes](#formatting-codes)
7. [Interactive Events](#interactive-events)
8. [Nesting and Inheritance](#nesting-and-inheritance)
9. [Usage Contexts](#usage-contexts)
10. [Escape Sequences](#escape-sequences)
11. [Version Differences](#version-differences)

---

## Overview

**Definition:** Text components are JSON-based structures used by Minecraft to display rich-formatted text with colors, styling, and interactivity.

**Format:** Prior to 1.21.5 - JSON; 1.21.5+ - SNBT

**Used In:**
- Written books (pages)
- Signs (text lines)
- /tellraw command
- /title command
- Custom item names
- Scoreboards
- Chat messages (limited)

---

## Basic Structure

### Complete Template

```json
{
    "text": "",
    "translate": "",
    "with": [],
    "score": {"name": "", "objective": "", "value": ""},
    "selector": "",
    "keybind": "",
    "nbt": "",
    "storage": "",
    "block": "",
    "entity": "",
    "interpret": false,

    "color": "",
    "font": "",
    "bold": false,
    "italic": false,
    "underlined": false,
    "strikethrough": false,
    "obfuscated": false,

    "insertion": "",
    "clickEvent": {"action": "", "value": ""},
    "hoverEvent": {"action": "", "value": ""},

    "extra": []
}
```

### Minimal Example

```json
{"text": "Hello World"}
```

### Array Shorthand

```json
["Parent Text", "Child 1", "Child 2"]
```

First element = parent, remaining = children (appended to parent)

---

## Content Types

**Note:** Only ONE content type per component. Priority order if multiple specified:
1. `text`
2. `translate`
3. `score`
4. `selector`
5. `keybind`
6. `nbt`

### 1. Plain Text

**Field:** `"text"`
**Type:** String
**Description:** Literal text to display

**Example:**
```json
{"text": "Hello World"}
```

**With Escaping:**
```json
{"text": "Line 1\nLine 2"}
{"text": "He said \"hi\""}
```

### 2. Translatable Text

**Field:** `"translate"`
**Type:** String (translation key)
**Description:** References language file entries for multi-language support

**Format:**
```json
{
    "translate": "translation.key",
    "with": ["arg1", "arg2"]
}
```

**Example:**
```json
{
    "translate": "item.minecraft.diamond_sword",
    "with": []
}
```

**Placeholder Syntax:** Translation strings use `%s` for single, `%1$s`, `%2$s` for multiple

**Translation Example:**
```
# In language file
death.attack.player=%1$s was slain by %2$s

# In component
{"translate": "death.attack.player", "with": ["Steve", "Zombie"]}
# Displays: "Steve was slain by Zombie"
```

### 3. Scoreboard Value

**Field:** `"score"`
**Type:** Object
**Description:** Displays a player's score from an objective

**Format:**
```json
{
    "score": {
        "name": "player_name_or_selector",
        "objective": "objective_name",
        "value": "fallback_value"
    }
}
```

**Example:**
```json
{
    "score": {
        "name": "@p",
        "objective": "deaths"
    }
}
```

**Behavior:** Displays nothing if score holder or objective doesn't exist

### 4. Entity Selector

**Field:** `"selector"`
**Type:** String (target selector)
**Description:** Displays names of entities matching selector

**Format:**
```json
{"selector": "@e[type=creeper,limit=3]"}
```

**Display Pattern:**
- 1 entity: `EntityName`
- Multiple: `Name1, Name2, Name3` (gray commas)

**Example:**
```json
{"selector": "@a[team=red]"}
```

### 5. Keybind

**Field:** `"keybind"`
**Type:** String (keybind ID)
**Description:** Shows the key bound to an action (adapts to player's settings)

**Format:**
```json
{"keybind": "key.inventory"}
```

**Common Keybinds:**
- `key.forward`
- `key.back`
- `key.left`
- `key.right`
- `key.jump`
- `key.sneak`
- `key.sprint`
- `key.inventory`
- `key.drop`
- `key.attack`
- `key.use`

**Example:**
```json
{
    "text": "Press ",
    "extra": [
        {"keybind": "key.jump"},
        {"text": " to jump"}
    ]
}
```

### 6. NBT Data

**Fields:** `"nbt"`, `"block"`, `"entity"`, `"storage"`
**Type:** String (NBT path)
**Description:** Extracts and displays NBT data

**Block NBT:**
```json
{
    "nbt": "Items[0].tag.display.Name",
    "block": "~ ~ ~"
}
```

**Entity NBT:**
```json
{
    "nbt": "CustomName",
    "entity": "@e[type=cow,limit=1]"
}
```

**Storage NBT:**
```json
{
    "nbt": "player.name",
    "storage": "namespace:storage_id"
}
```

**Interpret Flag:**
```json
{
    "nbt": "sign_text",
    "block": "~ ~ ~",
    "interpret": true
}
```

If `interpret: true`, NBT value is parsed as JSON text component

---

## Styling Properties

### Bold

**Field:** `"bold"`
**Type:** Boolean
**Default:** `false`

**Example:**
```json
{"text": "Bold Text", "bold": true}
```

### Italic

**Field:** `"italic"`
**Type:** Boolean
**Default:** `false` (true for some contexts like lore)

**Example:**
```json
{"text": "Italic Text", "italic": true}
```

**Special:** Item lore defaults to italic; use `"italic": false` to disable

### Underlined

**Field:** `"underlined"`
**Type:** Boolean
**Default:** `false`

**Example:**
```json
{"text": "Underlined", "underlined": true}
```

### Strikethrough

**Field:** `"strikethrough"`
**Type:** Boolean
**Default:** `false`

**Example:**
```json
{"text": "Strikethrough", "strikethrough": true}
```

### Obfuscated

**Field:** `"obfuscated"`
**Type:** Boolean
**Default:** `false`

**Example:**
```json
{"text": "Secret", "obfuscated": true}
```

**Effect:** Characters cycle randomly (like magic table text)

### Font

**Field:** `"font"`
**Type:** String (resource location)
**Default:** `"minecraft:default"`

**Example:**
```json
{"text": "Alternate", "font": "minecraft:alt"}
```

**Built-in Fonts:**
- `minecraft:default` - Standard font
- `minecraft:alt` - Alternative font (enchanting table)
- `minecraft:uniform` - Uniform-width font
- Custom fonts via resource packs

---

## Color Options

### Named Colors

**Field:** `"color"`
**Type:** String
**Values:** 16 named colors + `"reset"`

| Color Name | Hex | Description |
|------------|-----|-------------|
| `black` | `#000000` | Black |
| `dark_blue` | `#0000AA` | Dark Blue |
| `dark_green` | `#00AA00` | Dark Green |
| `dark_aqua` | `#00AAAA` | Dark Aqua |
| `dark_red` | `#AA0000` | Dark Red |
| `dark_purple` | `#AA00AA` | Dark Purple |
| `gold` | `#FFAA00` | Gold |
| `gray` | `#AAAAAA` | Gray |
| `dark_gray` | `#555555` | Dark Gray |
| `blue` | `#5555FF` | Blue |
| `green` | `#55FF55` | Green |
| `aqua` | `#55FFFF` | Aqua |
| `red` | `#FF5555` | Red |
| `light_purple` | `#FF55FF` | Light Purple |
| `yellow` | `#FFFF55` | Yellow |
| `white` | `#FFFFFF` | White |
| `reset` | - | Reset to default |

**Example:**
```json
{"text": "Red Text", "color": "red"}
```

### Hex Colors

**Format:** `#RRGGBB`

**Example:**
```json
{"text": "Custom Color", "color": "#FF8800"}
```

**Note:** Hex colors supported in 1.16+

---

## Formatting Codes

### Section Sign (§)

**Legacy system** using the section sign character (Unicode U+00A7)

**Format:** `§` + code character

### Color Codes

| Code | Color |
|------|-------|
| `§0` | Black |
| `§1` | Dark Blue |
| `§2` | Dark Green |
| `§3` | Dark Aqua |
| `§4` | Dark Red |
| `§5` | Dark Purple |
| `§6` | Gold |
| `§7` | Gray |
| `§8` | Dark Gray |
| `§9` | Blue |
| `§a` | Green |
| `§b` | Aqua |
| `§c` | Red |
| `§d` | Light Purple |
| `§e` | Yellow |
| `§f` | White |

### Format Codes

| Code | Effect |
|------|--------|
| `§k` | Obfuscated |
| `§l` | Bold |
| `§m` | Strikethrough |
| `§n` | Underline |
| `§o` | Italic |
| `§r` | Reset |

### Usage in JSON

**Not recommended** in modern JSON components, but can appear in legacy text:

```json
{"text": "§cRed §lBold"}
```

**Better approach:**
```json
{
    "text": "Red ",
    "color": "red",
    "extra": [
        {"text": "Bold", "bold": true}
    ]
}
```

### Unicode Escaping

In contexts where § cannot be typed:

**Unicode:** `\u00A7` or `\u00a7`

**Example:**
```json
{"text": "\u00A7cRed Text"}
```

**MOTD (server.properties):**
```
motd=\u00A7aGreen Server Name
```

---

## Interactive Events

### Click Events

**Field:** `"clickEvent"`
**Type:** Object

**Format:**
```json
{
    "clickEvent": {
        "action": "action_type",
        "value": "action_value"
    }
}
```

#### Action Types

##### open_url

**Description:** Opens URL in browser
**Allowed:** `http://` and `https://` only
**Security:** Minecraft shows confirmation dialog

**Example:**
```json
{
    "text": "Click here",
    "clickEvent": {
        "action": "open_url",
        "value": "https://minecraft.net"
    }
}
```

##### run_command

**Description:** Executes command as player
**Limit:** 256 characters for players, unlimited for command blocks
**Auto-prepends:** `/` if not present

**Example:**
```json
{
    "text": "[Teleport Home]",
    "clickEvent": {
        "action": "run_command",
        "value": "/tp @s 0 64 0"
    }
}
```

##### suggest_command

**Description:** Fills chat box with text (doesn't execute)

**Example:**
```json
{
    "text": "Click to fill",
    "clickEvent": {
        "action": "suggest_command",
        "value": "/give @s diamond"
    }
}
```

##### change_page

**Description:** Changes page in written books
**Context:** Written books only

**Example:**
```json
{
    "text": "Next Page",
    "clickEvent": {
        "action": "change_page",
        "value": "2"
    }
}
```

##### copy_to_clipboard

**Description:** Copies text to clipboard
**Version:** 1.15+

**Example:**
```json
{
    "text": "Copy this",
    "clickEvent": {
        "action": "copy_to_clipboard",
        "value": "Copied text here"
    }
}
```

### Hover Events

**Field:** `"hoverEvent"`
**Type:** Object

**Format:**
```json
{
    "hoverEvent": {
        "action": "action_type",
        "value": "action_value"
    }
}
```

#### Action Types

##### show_text

**Description:** Shows tooltip with text component

**Example:**
```json
{
    "text": "Hover me",
    "hoverEvent": {
        "action": "show_text",
        "value": "This is a tooltip"
    }
}
```

**Complex Tooltip:**
```json
{
    "text": "Hover me",
    "hoverEvent": {
        "action": "show_text",
        "value": {
            "text": "Colored tooltip",
            "color": "gold",
            "bold": true
        }
    }
}
```

##### show_item

**Description:** Shows item tooltip

**Format (1.20.5+):**
```json
{
    "hoverEvent": {
        "action": "show_item",
        "contents": {
            "id": "minecraft:diamond_sword",
            "count": 1,
            "components": {}
        }
    }
}
```

**Format (Pre-1.20.5):**
```json
{
    "hoverEvent": {
        "action": "show_item",
        "value": "{id:\"minecraft:diamond_sword\",Count:1b,tag:{}}"
    }
}
```

##### show_entity

**Description:** Shows entity tooltip (name, type, UUID)

**Format:**
```json
{
    "hoverEvent": {
        "action": "show_entity",
        "value": {
            "name": "Steve",
            "type": "minecraft:player",
            "id": "UUID-here"
        }
    }
}
```

---

## Nesting and Inheritance

### Using "extra" Array

**Concept:** Child components inherit parent styling

**Example:**
```json
{
    "text": "Parent ",
    "color": "red",
    "bold": true,
    "extra": [
        {"text": "Child1 "},
        {"text": "Child2", "color": "blue"},
        {"text": "Child3"}
    ]
}
```

**Result:**
- "Parent " = red, bold
- "Child1 " = red, bold (inherited)
- "Child2" = blue, bold (override color, keep bold)
- "Child3" = red, bold (inherited)

### Inheritance Rules

1. **Children inherit parent styling** unless overridden
2. **Siblings don't affect each other**
3. **Grandchildren inherit from both** parent and grandparent
4. **Color resets all formatting** before it (legacy behavior with §)

### Array Syntax

**Shorthand:**
```json
["Parent", "Child1", "Child2"]
```

**Equivalent to:**
```json
{
    "text": "Parent",
    "extra": ["Child1", "Child2"]
}
```

### Deep Nesting

```json
{
    "text": "Level 1",
    "extra": [
        {
            "text": "Level 2",
            "extra": [
                {
                    "text": "Level 3",
                    "extra": [...]
                }
            ]
        }
    ]
}
```

**Note:** NBT has 512-level depth limit

---

## Usage Contexts

### Written Books

**Location:** `pages` array in written_book NBT (pre-1.20.5) or `written_book_content` component (1.20.5+)

**Format:** Each page is a JSON string

**Limits:**
- Max 100 pages (Java Edition)
- Max 1,023 characters per page
- 14 lines per page
- ~121 pixels width per line

**Example:**
```json
{
    "pages": [
        "{\"text\":\"Page 1\",\"color\":\"red\"}",
        "[\"Page 2\",{\"text\":\" continued\",\"italic\":true}]"
    ]
}
```

**Available Features:**
- Full styling (color, bold, italic, etc.)
- Click events (except `open_file`)
- Hover events: `show_text`, `show_item`
- **NOT available:** `insertion`, `suggest_command`

### Signs

**Location:** `Text1`, `Text2`, `Text3`, `Text4` (pre-1.20) or `front_text.messages` / `back_text.messages` (1.20+)

**Format:** JSON string

**Limits:**
- 4 lines per sign face
- Character limit based on pixel width
- ~90 pixels per line

**Example (1.20+):**
```json
{
    "front_text": {
        "messages": [
            "{\"text\":\"Line 1\",\"color\":\"blue\"}",
            "\"\"",
            "\"\"",
            "{\"text\":\"Line 4\",\"bold\":true}"
        ]
    }
}
```

**Available Features:**
- Colors and basic styling
- Click events: `run_command`
- **NOT available:** `insertion`, `open_url`, `suggest_command`, hover events

### /tellraw

**Full support** for all text component features except `change_page`

**Example:**
```
/tellraw @a {"text":"Hello","color":"green","clickEvent":{"action":"run_command","value":"/say Hi"}}
```

### /title

**Styling only** - no click events, no hover events

**Example:**
```
/title @a title {"text":"Victory!","color":"gold","bold":true}
/title @a subtitle {"text":"You win","color":"yellow"}
```

### Custom Names

**Item names:** `custom_name` component (1.20.5+) or `display.Name` NBT (pre-1.20.5)

**Example:**
```json
{
    "custom_name": {
        "text": "Legendary Sword",
        "color": "gold",
        "italic": false
    }
}
```

**Note:** Custom names default to italic; use `"italic": false` to disable

---

## Escape Sequences

### JSON Escape Sequences

When text components are embedded in commands or NBT:

| Sequence | Meaning |
|----------|---------|
| `\"` | Double quote |
| `\\` | Backslash |
| `\n` | Newline |
| `\r` | Carriage return |
| `\t` | Tab |
| `\b` | Backspace |
| `\f` | Form feed |
| `\uXXXX` | Unicode character |

### In Command Context

**Level 1 - Simple JSON:**
```json
{"text":"He said \"hi\""}
```

**Level 2 - JSON in NBT:**
```
/give @p written_book{pages:["{\"text\":\"He said \\\"hi\\\"\"}"]}
```

**Level 3 - NBT in Command Block:**
```
/setblock ~ ~ ~ command_block{Command:"/give @p written_book{pages:[\"{\\\"text\\\":\\\"He said \\\\\\\"hi\\\\\\\"\\\"}\"]}"
```

### Backslash Doubling Formula

**Formula:** `(2 × current_backslashes) + 1`

- Level 1: `\"`
- Level 2: `\\\"`
- Level 3: `\\\\\\\"`
- Level 4: `\\\\\\\\\\\\\\\"`

### Single Quote Trick (1.14+)

**Use single quotes** for outer string to avoid escaping double quotes:

```
/give @p written_book{pages:['{"text":"No escaping needed!"}']}
```

**Inside single quotes:**
- Double quotes: `"` (no escape needed)
- Single quotes: `\'` (escape required)
- Backslashes: `\\` (still need escaping)

---

## Version Differences

### Pre-1.13 (Before Flattening)

- Lenient JSON parsing allowed (unquoted keys)
- Numeric item IDs
- Less strict validation

### 1.13 (The Flattening)

- **Strict JSON required** for Name field
- Removed numeric IDs
- NBT tag structure changes

### 1.14

- **Single quote trick** for page escaping
- Title limit: 65,535 characters
- Page limit: 1,023 characters
- Max pages: 100

### 1.16

- **Hex colors** added (`#RRGGBB`)

### 1.20

- Sign **front_text/back_text** structure
- 4-element messages array required

### 1.20.5

- **Data components** replace NBT
- `written_book_content` component
- `custom_name`, `item_name` distinction

### 1.21.5

- Text components switch from **JSON to SNBT** format
- Backward compatible with JSON

---

## Practical Examples

### Example 1: Clickable Menu

```json
{
    "text": "Main Menu\n",
    "color": "gold",
    "bold": true,
    "extra": [
        {
            "text": "[Shop] ",
            "color": "green",
            "clickEvent": {
                "action": "run_command",
                "value": "/tp @s -100 64 200"
            }
        },
        {
            "text": "[Arena] ",
            "color": "red",
            "clickEvent": {
                "action": "run_command",
                "value": "/tp @s 500 70 -300"
            }
        },
        {
            "text": "[Home]",
            "color": "blue",
            "clickEvent": {
                "action": "run_command",
                "value": "/spawn"
            }
        }
    ]
}
```

### Example 2: Item Tooltip

```json
{
    "text": "Diamond Sword",
    "color": "aqua",
    "hoverEvent": {
        "action": "show_item",
        "contents": {
            "id": "minecraft:diamond_sword",
            "count": 1
        }
    }
}
```

### Example 3: Multi-Page Book

```json
{
    "pages": [
        "{\"text\":\"Chapter 1\",\"color\":\"dark_purple\",\"bold\":true}",
        "[\"Once upon a time...\",{\"text\":\"\\n\\nThe End\",\"color\":\"gray\"}]",
        "{\"text\":\"Next Chapter\",\"color\":\"blue\",\"underlined\":true,\"clickEvent\":{\"action\":\"change_page\",\"value\":\"4\"}}"
    ]
}
```

### Example 4: Scoreboard Display

```json
{
    "text": "Your Deaths: ",
    "color": "red",
    "extra": [
        {
            "score": {
                "name": "@p",
                "objective": "deaths"
            },
            "color": "yellow"
        }
    ]
}
```

### Example 5: Keybind Tutorial

```json
{
    "text": "Press ",
    "extra": [
        {"keybind": "key.forward", "color": "green"},
        {"text": " to move forward, and "},
        {"keybind": "key.jump", "color": "yellow"},
        {"text": " to jump!"}
    ]
}
```

---

## Reference Links

### Official Documentation
- Minecraft Wiki - Text Component Format: https://minecraft.wiki/w/Text_component_format
- Minecraft Wiki - Formatting Codes: https://minecraft.wiki/w/Formatting_codes

### Community Resources
- 1.12 JSON Text Component Guide: https://www.minecraftforum.net/forums/minecraft-java-edition/redstone-discussion-and/351959-1-12-json-text-component-for-tellraw-title-books
- JSON Text Generator: https://minecraft.tools/en/json_text.php
- Raw JSON Text Generator: https://www.gamergeeks.net/apps/minecraft/raw-json-text-format-generator

### Technical Guides
- Skylinerw's Text Component Guide: https://github.com/skylinerw/guides/blob/master/java/text%20component.md

---

**End of Reference Document**

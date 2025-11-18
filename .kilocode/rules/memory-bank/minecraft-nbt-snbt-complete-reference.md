# Minecraft NBT and SNBT - Complete Reference

**Last Updated:** 2025-11-18
**Purpose:** Comprehensive reference for NBT (Named Binary Tag) and SNBT (Stringified NBT) formats
**Applies to:** Minecraft Java Edition, all versions

---

## Table of Contents

1. [NBT Format Overview](#nbt-format-overview)
2. [SNBT (Stringified NBT)](#snbt-stringified-nbt)
3. [Data Types](#data-types)
4. [Escape Sequences](#escape-sequences)
5. [String Quoting Rules](#string-quoting-rules)
6. [Compound Tags](#compound-tags)
7. [List Tags](#list-tags)
8. [Binary Format](#binary-format)
9. [Parsing Libraries](#parsing-libraries)
10. [Best Practices](#best-practices)

---

## NBT Format Overview

**Definition:** Named Binary Tag (NBT) is a tree data structure used by Minecraft in many save files to store arbitrary data.

**Usage:**
- World save files (.dat, .mca)
- Player data
- Item data
- Block entity data
- Structure files

**File Storage:** NBT files are typically GZip-compressed

---

## SNBT (Stringified NBT)

### What is SNBT?

SNBT is a text representation of NBT data used in Minecraft commands. It allows inline specification of complex data structures using JSON-like syntax.

### Format Example

```
{blah:5b,foo:"bär",list:[test,text]}
```

### Key Characteristics

1. **Human-readable** - Can be typed directly in commands
2. **Type-specific** - Requires type suffixes for numeric values
3. **Extends JSON** - Similar to JSON but with stronger type enforcement
4. **Used in commands** - Primary format for /give, /setblock, /data, etc.

---

## Data Types

### Numeric Types

| Type | ID | Suffix | Range | Example |
|------|-----|---------|-------|---------|
| Byte | 1 | `b` or `B` | -128 to 127 | `5b`, `-3B` |
| Short | 2 | `s` or `S` | -32,768 to 32,767 | `17s`, `-1024S` |
| Int | 3 | `i` or none | -2,147,483,648 to 2,147,483,647 | `123`, `456i` |
| Long | 4 | `l` or `L` | ±9,223,372,036,854,775,807 | `43046721L` |
| Float | 5 | `f` or `F` | 32-bit IEEE 754 | `3.141f`, `0.0F` |
| Double | 6 | `d` or none | 64-bit IEEE 754 | `18932.214`, `10.2d` |

**Note:** Int and Double don't require suffixes but can include them for clarity.

### Numeric Format Features

**Multiple Representations:**
- Hexadecimal: `0xABCD`
- Binary: `0b1010`
- Scientific notation: `1.2e3`
- Underscores for readability: `1_000_000`

**Signedness:** Can use `u`/`s` prefixes before type suffixes for explicit signed/unsigned representation.

### String Type

**String (ID 8):** UTF-8 encoded text

**Quoting Rules:**
- Can be unquoted if contains only: `a-z`, `A-Z`, `0-9`, `_`, `-`, `.`, `+`
- Must be quoted if contains spaces, special characters, or quotes
- Use single or double quotes

**Examples:**
```
unquoted_string
"quoted string with spaces"
'string with "double quotes"'
```

### Collection Types

#### List Tag (ID 9)

**Format:** `[value1, value2, value3]`

**Characteristics:**
- Homogeneous (all elements same type)
- No type prefix in SNBT
- Type determined by first element

**Example:**
```snbt
[test, text, "more text"]
["string1", "string2"]
[1b, 2b, 3b]
```

#### Compound Tag (ID 10)

**Format:** `{key1: value1, key2: value2}`

**Characteristics:**
- Heterogeneous (mixed value types)
- Named key-value pairs
- Keys are strings (quoted or unquoted)

**Example:**
```snbt
{
  name: "Diamond Sword",
  damage: 100,
  enchanted: true,
  lore: ["First line", "Second line"]
}
```

#### Array Types

**Byte Array (ID 7):**
```snbt
[B; 1b, 2b, 3b, -4b]
```

**Int Array (ID 11):**
```snbt
[I; 100, 200, 300]
```

**Long Array (ID 12):**
```snbt
[L; 1000L, 2000L, 3000L]
```

**Key:** Type specifier (`B;`, `I;`, `L;`) comes FIRST, then semicolon, then values.

### Boolean Type

**Note:** SNBT doesn't have native boolean type.

**Convention:** Use byte values
- `true` → `1b`
- `false` → `0b`

**Example:**
```snbt
{enabled: 1b, disabled: 0b}
```

### End Tag (ID 0)

**Purpose:** Marks the end of a Compound Tag in binary format
**SNBT:** Not represented (implicit from `}` closing brace)

---

## Escape Sequences

### Supported Sequences

Only apply when strings are enclosed in quotes. Unquoted strings treat escape sequences literally.

| Sequence | Meaning | Example |
|----------|---------|---------|
| `\"` | Double quote | `"He said \"hi\""` |
| `\'` | Single quote | `'It\'s here'` |
| `\\` | Backslash | `"path\\to\\file"` |
| `\n` | Newline | `"line1\nline2"` |
| `\r` | Carriage return | `"text\rmore"` |
| `\t` | Tab | `"col1\tcol2"` |
| `\b` | Backspace | `"text\bmore"` |
| `\f` | Form feed | `"page1\fpage2"` |
| `\s` | Space | `"word\sword"` |
| `\xhh` | Hex byte | `"\x41"` = 'A' |
| `\uhhhh` | Unicode 16-bit | `"\u00A7"` = '§' |
| `\Uhhhhhhhh` | Unicode 32-bit | `"\U0001F600"` = '😀' |
| `\N{name}` | Unicode name | `"\N{SNOWMAN}"` = '☃' |

### Important Notes

1. **Quoted strings only:** Escape sequences work ONLY in quoted strings
2. **Unquoted behavior:** `test\nvalue` (unquoted) = literal "test\nvalue"
3. **Quoted behavior:** `"test\nvalue"` = "test" + newline + "value"

---

## String Quoting Rules

### Automatic Quote Selection

**Rule:** SNBT automatically chooses quote type based on content.

**Algorithm:**
1. If string has no quotes → use double quotes `"`
2. If string has `"` but no `'` → use single quotes `'`
3. If string has `'` but no `"` → use double quotes `"`
4. If string has both → use opposite of first occurrence

**Examples:**
```snbt
plain_text          → "plain_text"
text with "quotes"  → 'text with "quotes"'
text with 'apos'    → "text with 'apos'"
"mixed' quotes"     → '"mixed\' quotes"' (first is ", so use ')
```

### Manual Quoting

You can explicitly choose quote type and escape internal quotes:

```snbt
"He said \"hello\""           # Double quotes with escaping
'He said "hello"'             # Single quotes, no escaping needed
"It's a nice day"             # Double quotes, apostrophe OK
'It\'s a nice day'            # Single quotes with escaping
```

### Unquoted Strings

**Valid characters:** `a-z`, `A-Z`, `0-9`, `_`, `-`, `.`, `+`

**Examples:**
```snbt
myKey: myValue          # Valid
my-key: test_value-123  # Valid
2fast2furious: 1        # Valid (starts with number is OK)
"has spaces": value     # Key requires quotes
key: "has spaces"       # Value requires quotes
```

---

## Compound Tags

### Syntax

```snbt
{
  key1: value1,
  key2: value2,
  "key with spaces": value3
}
```

### Key Requirements

- Must be unique within compound
- Can be quoted or unquoted (follows string rules)
- Case-sensitive: `name` ≠ `Name`

### Nested Compounds

```snbt
{
  outer: {
    inner: {
      deepest: "value"
    }
  }
}
```

### Empty Compound

```snbt
{}
```

---

## List Tags

### Homogeneous Requirement

**All elements must be the same type:**

```snbt
[1, 2, 3]              # Valid - all ints
["a", "b", "c"]        # Valid - all strings
[1b, 2b, 3b]           # Valid - all bytes
[1, "mixed"]           # INVALID - mixed types
```

### Type Determination

List type is determined by the **first element**:

```snbt
[test, more, text]     # String list (first element is unquoted string)
[1, 2, 3]              # Int list
[1.0, 2.0, 3.0]        # Double list
```

### Nested Lists

```snbt
[
  [1, 2, 3],
  [4, 5, 6],
  [7, 8, 9]
]
```

All inner lists must also be homogeneous and the same type.

### Empty List

```snbt
[]
```

**Note:** Empty list has no type until an element is added.

---

## Binary Format

### File Structure

1. **Type ID** (1 byte) - Tag type identifier
2. **Name Length** (2 bytes, big-endian) - Length of tag name
3. **Tag Name** (UTF-8 string) - The name of the tag
4. **Payload** (variable) - Type-specific data

### Tag Type IDs

| ID | Type | Payload |
|----|------|---------|
| 0 | TAG_End | None |
| 1 | TAG_Byte | 1 signed byte |
| 2 | TAG_Short | 2 bytes, big-endian signed |
| 3 | TAG_Int | 4 bytes, big-endian signed |
| 4 | TAG_Long | 8 bytes, big-endian signed |
| 5 | TAG_Float | 4 bytes, big-endian IEEE 754 |
| 6 | TAG_Double | 8 bytes, big-endian IEEE 754 |
| 7 | TAG_Byte_Array | Length (int) + bytes |
| 8 | TAG_String | Length (short) + UTF-8 bytes |
| 9 | TAG_List | Type ID + length (int) + elements |
| 10 | TAG_Compound | Tags until TAG_End |
| 11 | TAG_Int_Array | Length (int) + ints |
| 12 | TAG_Long_Array | Length (int) + longs |

### Endianness

**All multi-byte values are BIG-ENDIAN (network byte order)**

### Root Tag

NBT files start with a root tag, typically TAG_Compound or TAG_List.

### Compression

**.dat files:** GZip compressed
**.mca files:** Each chunk compressed with GZip or Zlib

---

## Parsing Libraries

### Querz NBT (Java)

**Repository:** https://github.com/Querz/NBT
**Version:** 6.1+
**Language:** Java

**Features:**
- Full NBT read/write support
- SNBT conversion: `SNBTUtil.toSNBT(tag)`
- SNBT parsing: `SNBTUtil.fromSNBT(string)`
- MCA file support
- Block state API
- Maximum depth protection (512 levels)

**Example:**
```java
CompoundTag c = new CompoundTag();
c.putByte("health", (byte) 20);
c.putString("name", "Player");
ListTag<StringTag> inventory = new ListTag<>(StringTag.class);
inventory.addString("diamond_sword");
inventory.addString("iron_pickaxe");
c.put("inventory", inventory);

String snbt = SNBTUtil.toSNBT(c);
// Output: {health:20b,name:"Player",inventory:[diamond_sword,iron_pickaxe]}
```

**Depth Protection:**
```java
// Throws MaxDepthReachedException if nesting > 512
CompoundTag deep = SNBTUtil.fromSNBT(nestedString);
```

### ArcadiusMC NBT (Java)

**Repository:** https://github.com/ArcadiusMC/NBT
**Language:** Java

**Features:**
- SNBT parsing: `Snbt.parse(input)`
- Modern Java API
- BinaryTag objects

### quartz_nbt (Rust)

**Documentation:** https://docs.rs/quartz_nbt/latest/quartz_nbt/snbt/
**Language:** Rust

**Features:**
- SNBT parser for Rust
- Type-safe tag handling
- `parse()` and `parse_and_size()` functions

---

## Best Practices

### 1. Always Validate Input

```java
// BAD - assumes tag exists
String name = compound.getString("name");

// GOOD - check first
String name = compound.containsKey("name")
    ? compound.getString("name")
    : "Unknown";
```

### 2. Use Type-Safe Accessors

```java
// Querz NBT library
ListTag<StringTag> pages = (ListTag<StringTag>) compound.getListTag("pages");
if (pages != null) {
    for (StringTag page : pages) {
        String text = page.getValue();
        // Process page
    }
}
```

### 3. Handle Nested Paths Safely

```java
// BAD - can throw NPE
String text = compound.getCompound("book")
    .getCompound("pages")
    .getString("text");

// GOOD - validate at each level
if (compound.containsKey("book")) {
    CompoundTag book = compound.getCompoundTag("book");
    if (book != null && book.containsKey("pages")) {
        CompoundTag pages = book.getCompoundTag("pages");
        if (pages != null) {
            String text = pages.getString("text");
            // Process text
        }
    }
}
```

### 4. Respect Maximum Depth

**Limit:** NBT structures are limited to 512 levels of nesting.

**Why:** Prevents denial-of-service attacks and circular references.

**Detection:** Querz library throws `MaxDepthReachedException` when exceeded.

### 5. Use Appropriate Data Types

```snbt
# GOOD - correct types
{enabled: 1b, count: 10, ratio: 0.5f}

# BAD - wrong types (still valid but wasteful)
{enabled: 1, count: 10L, ratio: 0.5d}
```

### 6. Escape Properly for Context

**For SNBT in commands:**
```java
String title = "He said \"hello\"";
// In SNBT: {title:"He said \"hello\""}  # \" for quote escaping
```

**For SNBT in single-quoted context:**
```java
// In command: pages:['{"text":"He said \"hello\""}']
// Inside single quotes, double quotes don't need escaping
```

### 7. Use Compound Tags for Structure

```snbt
# GOOD - organized
{
  player: {
    name: "Steve",
    health: 20,
    inventory: [...]
  }
}

# BAD - flat (hard to maintain)
{
  playerName: "Steve",
  playerHealth: 20,
  playerInventory: [...]
}
```

### 8. Leverage SNBT for Testing

```java
// Quick test data creation
CompoundTag test = SNBTUtil.fromSNBT(
    "{name:\"TestItem\",damage:10,enchanted:1b}"
);
```

---

## Common Pitfalls

### 1. Forgetting Type Suffixes

```snbt
{count: 5}      # This is an INT (5)
{count: 5b}     # This is a BYTE (5b)
```

For small numbers, forgetting `b` means you're using 4 bytes instead of 1.

### 2. Mixed List Types

```snbt
[1, 2, 3]       # Valid - all ints
[1b, 2b, 3b]    # Valid - all bytes
[1, 2b, 3]      # INVALID - mixed int and byte
```

### 3. Quote Escaping in Nested Structures

```snbt
# Level 0 (command)
give @p written_book{pages:['...']}

# Level 1 (inside single quotes)
'{"text":"..."}'     # Double quotes OK, no escaping

# Level 2 (nested JSON in JSON)
'{"text":"He said \"hi\""}' # Must escape inner quotes
```

### 4. Array vs List Confusion

```snbt
[B; 1b, 2b, 3b]    # Byte ARRAY - type prefix with semicolon
[1b, 2b, 3b]       # Byte LIST - no type prefix
```

Arrays and Lists are different types with different serialization.

### 5. Incorrect Empty Structures

```snbt
{key: }            # INVALID - missing value
{key: ""}          # Valid - empty string
{key: []}          # Valid - empty list
{key: {}}          # Valid - empty compound
```

---

## Version-Specific Notes

### Java Edition 1.21.5+

**New Feature:** SNBT supports freeform numeric literals and operations

**Boolean/UUID Functions:**
- `bool()` - Convert values to boolean
- `uuid()` - Generate/convert UUIDs

### Pre-1.13 (Before Flattening)

**Numeric IDs:** Blocks and items used numeric IDs alongside text IDs
**Post-1.13:** Numeric IDs removed entirely

### 1.14 Snapshot 18w43a

**Written Books:**
- Title limit raised to 65,535 characters
- Page limit: 1,023 characters per page
- Max pages: 100
- Packet size limit: 2,097,152 bytes

---

## Reference Links

### Official Documentation
- Minecraft Wiki - NBT Format: https://minecraft.wiki/w/NBT_format
- Minecraft Wiki - SNBT: Embedded in NBT format page

### Technical Specifications
- wiki.vg NBT Protocol: https://wiki.vg/NBT
- Kaitai Struct NBT Parser: https://formats.kaitai.io/minecraft_nbt/

### Libraries
- Querz/NBT (Java): https://github.com/Querz/NBT
- ArcadiusMC/NBT (Java): https://github.com/ArcadiusMC/NBT
- quartz_nbt (Rust): https://docs.rs/quartz_nbt/

### Tools
- SNBT Editor: https://gorymoon.github.io/snbt-editor/
- NBTExplorer (Windows): External NBT editor

---

## Glossary

**NBT:** Named Binary Tag - Binary data structure format
**SNBT:** Stringified NBT - Text representation for commands
**Compound Tag:** Key-value map structure
**List Tag:** Ordered collection of same-typed elements
**Array Tag:** Fixed-type array (Byte, Int, or Long)
**Type Suffix:** Letter indicating numeric type (b, s, i, l, f, d)
**Escape Sequence:** Special character combination (\n, \t, etc.)
**Payload:** The actual data stored in a tag
**Root Tag:** Top-level tag in NBT structure
**Big-Endian:** Most significant byte first (network byte order)

---

**End of Reference Document**

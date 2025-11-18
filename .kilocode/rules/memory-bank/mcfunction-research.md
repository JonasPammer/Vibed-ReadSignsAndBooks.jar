# MCFunction Command Generation Research

**Research Date:** 2025-11-18
**Purpose:** Fix mcfunction file generation issues (GitHub Issue #6)
**Versions Covered:** Minecraft 1.13, 1.14, 1.20.5, 1.21

## Problem Statement

The current implementation generates mcfunction files with invalid command syntax that fails to load in Minecraft:

1. **Serialization Error:** "Input is not a list of 4 elements" when processing 'back_text' field (signs)
2. **Parse Error:** "Invalid string contents at position X" when processing written book commands
3. **Escaping Issues:** Complex nested JSON structures with quotes aren't properly escaped

## Command Format Specifications

### Version 1.13 (The Flattening)

**Format:** NBT Tag format with curly braces `{}`

**Syntax:**
```
give @p written_book{title:"Title",author:"Author",pages:['{"text":"page1"}','{"text":"page2"}']}
```

**Key Rules:**
- Uses NBT tag syntax with `{key:value}` structure
- Pages array uses single quotes `'` to avoid escaping internal double quotes
- Page content must be valid JSON text components: `{"text":"content"}`
- Inside single quotes, only backslashes need escaping: `\` → `\\`
- Double quotes inside single-quoted strings: `"` → `"` (no escaping needed)

**Escaping Pattern:**
- Backslash: `\` → `\\`
- Newline in JSON: `\n` → `\\n` (double-escaped because it's inside JSON inside SNBT)

### Version 1.14

**Format:** NBT Tag format with JSON array wrapping

**Syntax:**
```
give @p written_book{title:"Title",author:"Author",pages:['["page1"]','["page2"]']}
```

**Key Rules:**
- Similar to 1.13 but pages wrapped in JSON array syntax `["..."]`
- Uses single quotes for the outer string to avoid escaping
- Page content can be:
  - Plain string wrapped in array: `["plain text"]`
  - JSON object wrapped in array: `[{"text":"formatted"}]`
  - Already-array JSON: `[...]` (use as-is)

**Escaping Pattern:**
- Same as 1.13: backslashes only `\` → `\\`
- Newlines: `\n` → `\\n`

### Version 1.20.5+ (Data Components)

**Format:** Data component syntax with square brackets `[]`

**Syntax:**
```
give @p written_book[minecraft:written_book_content={title:"Title",author:"Author",pages:["page1","page2"]}]
```

**Key Rules:**
- Uses data component syntax: `item[component={...}]`
- Component namespace: `minecraft:written_book_content`
- Pages are double-quoted strings in an array
- Pages can be:
  - Simple strings: `"plain text"`
  - JSON text components as strings: `"{\"text\":\"content\"}"`
- All quotes must be escaped: `"` → `\"`
- Backslashes must be escaped: `\` → `\\`

**Escaping Pattern:**
- Backslash first: `\` → `\\`
- Then quotes: `"` → `\"`
- Newlines: `\n` → `\n` (single-escaped, not double)
- Order matters: escape backslashes before quotes

### Version 1.21

**Format:** Data component syntax without namespace prefix

**Syntax:**
```
give @p written_book[written_book_content={title:"Title",author:"Author",pages:["page1","page2"]}]
```

**Key Rules:**
- Same as 1.20.5 but **no** `minecraft:` namespace prefix
- Component: `written_book_content` (not `minecraft:written_book_content`)
- All other rules identical to 1.20.5

**Escaping Pattern:**
- Identical to 1.20.5

## SNBT (Stringified NBT) Escaping Rules

### Fundamental Rules (All Versions)

1. **Quote Selection:**
   - If string contains `"`, use single quotes: `'content with "quotes"'`
   - If string contains `'`, use double quotes: `"content with 'apostrophe'"`
   - If string contains both, use one and escape the other

2. **Escape Sequences:**
   - `\"` - Double quote
   - `\'` - Single quote
   - `\\` - Backslash
   - `\n` - Newline
   - `\r` - Carriage return
   - `\t` - Tab
   - `\b` - Backspace
   - `\f` - Form feed
   - `\uXXXX` - Unicode character

3. **Nesting Levels:**
   - Each nesting level requires doubling backslashes
   - Formula: `backslashes = (2 ^ depth) - 1`
   - Level 0 (no nesting): `\n` (1 backslash)
   - Level 1 (inside one string): `\\n` (2 backslashes)
   - Level 2 (inside two strings): `\\\\n` (4 backslashes)

### Current Implementation Errors

**Problem 1: Inconsistent Escaping**

Current code (versions 1.13/1.14):
```groovy
escaped = jsonComponent.replace('\\', '\\\\')
```

This escapes backslashes but doesn't handle existing escaped sequences in the raw JSON.

**Problem 2: Double-Escaping Title/Author**

The `escapeForMinecraftCommand()` function is called on title/author, then used in double-quoted context:
```groovy
String escapedTitle = escapeForMinecraftCommand(title ?: 'Untitled', version)
// Used as: title:"${escapedTitle}"
```

But `escapeForMinecraftCommand()` for 1.13/1.14 does:
```groovy
escaped = escaped.replace('\\', '\\\\\\\\')  // 4 backslashes!
```

This is incorrect for the direct double-quoted usage.

**Problem 3: Raw JSON Not Handled Properly**

When `rawText` from NBT is already JSON (e.g., `{"text":"hello"}`), it contains:
- Quotes that need escaping for SNBT
- Possible escape sequences like `\n` that need handling
- Nested formatting codes

Current code wraps this in another JSON object:
```groovy
String jsonComponent = rawText.startsWith('{') ? rawText : "{\"text\":\"${rawText}\"}"
```

If `rawText` is `{"text":"line1\nline2"}`, this uses it as-is, but then only escapes backslashes:
```groovy
String escaped = jsonComponent.replace('\\', '\\\\')
```

This doesn't properly escape the quotes in the JSON structure.

## Correct Escaping Strategy

### For Versions 1.13 and 1.14 (Single-Quoted Strings)

**Pages:**
```groovy
// 1. Extract raw text from NBT (may already be JSON)
String rawText = getStringAt(pages, i)

// 2. Ensure it's valid JSON text component
String jsonText
if (rawText.startsWith('{') || rawText.startsWith('[')) {
    jsonText = rawText  // Already JSON
} else {
    // Plain text: wrap in JSON and escape internal quotes/backslashes
    String escapedContent = rawText
        .replace('\\', '\\\\')      // Escape backslashes
        .replace('"', '\\"')        // Escape quotes (for JSON)
        .replace('\n', '\\n')       // Escape newlines (for JSON)
    jsonText = "{\"text\":\"${escapedContent}\"}"
}

// 3. For single-quoted SNBT string, only escape backslashes and single quotes
String snbtEscaped = jsonText
    .replace('\\', '\\\\')          // Backslash → double backslash
    .replace("'", "\\'")            // Single quote → escaped (if needed)

// 4. Wrap in single quotes
String pageStr = "'${snbtEscaped}'"
```

**Title/Author:**
```groovy
// For double-quoted SNBT strings (title:"..." author:"...")
String titleEscaped = title
    .replace('\\', '\\\\')          // Backslash → double backslash
    .replace('"', '\\"')            // Quote → escaped quote
    .replace('\n', '\\n')           // Newline → escaped newline
```

### For Versions 1.20.5 and 1.21 (Double-Quoted Strings)

**Pages:**
```groovy
// 1. Extract raw text (may be JSON)
String rawText = getStringAt(pages, i)

// 2. Ensure valid JSON text component
String jsonText
if (rawText.startsWith('{') || rawText.startsWith('[')) {
    jsonText = rawText
} else {
    String escapedContent = rawText
        .replace('\\', '\\\\')
        .replace('"', '\\"')
        .replace('\n', '\\n')
    jsonText = "{\"text\":\"${escapedContent}\"}"
}

// 3. For double-quoted SNBT, escape backslashes and quotes
String snbtEscaped = jsonText
    .replace('\\', '\\\\')          // Backslash → double backslash
    .replace('"', '\\"')            // Quote → escaped quote

// 4. Wrap in double quotes
String pageStr = "\"${snbtEscaped}\""
```

**Title/Author:**
```groovy
// Same as pages - double-quoted SNBT
String titleEscaped = title
    .replace('\\', '\\\\')
    .replace('"', '\\"')
    .replace('\n', '\\n')
```

## Special Cases

### Case 1: Plain Text Pages

Input: `"Hello World"`
- 1.13: `'{"text":"Hello World"}'`
- 1.14: `'["Hello World"]'`
- 1.20.5+: `"{\"text\":\"Hello World\"}"`
- 1.21: `"{\"text\":\"Hello World\"}"`

### Case 2: Text with Quotes

Input: `He said "hi"`
- Raw JSON: `{"text":"He said \"hi\""}`
- 1.13 single-quoted: `'{"text":"He said \\"hi\\""}'`
  - Wait, inside single quotes we don't escape double quotes!
  - Correction: `'{"text":"He said \"hi\""}'`
- 1.20.5+ double-quoted: `"{\"text\":\"He said \\\"hi\\\"\"}"`

### Case 3: Text with Newlines

Input: `Line 1\nLine 2`
- Raw JSON: `{"text":"Line 1\\nLine 2"}` (JSON escapes \n as \\n)
- 1.13 single-quoted: `'{"text":"Line 1\\nLine 2"}'`
- 1.20.5+ double-quoted: `"{\"text\":\"Line 1\\nLine 2\"}"`

### Case 4: Formatting Codes

Input: `§cRed Text` (section sign color code)
- Convert to JSON: `{"text":"Red Text","color":"red"}`
- Then apply standard escaping rules

### Case 5: Already-JSON Pages (from 1.20.5+ NBT)

Input from NBT: `{"text":"Hello","color":"red","bold":true}`
- This is already valid JSON
- For 1.13: `'{"text":"Hello","color":"red","bold":true}'`
- For 1.20.5+: `"{\"text\":\"Hello\",\"color\":\"red\",\"bold\":true}"`

## Implementation Fix Strategy

1. **Simplify escapeForMinecraftCommand():**
   - Remove version-specific 4x backslash escaping
   - Make it a simple utility for direct string escaping

2. **Create separate page escaping logic:**
   - Handle JSON vs plain text detection
   - Apply correct escaping per version
   - Consider existing escape sequences in input

3. **Fix the nesting problem:**
   - Understand the actual nesting depth
   - Pages in books are: Command → SNBT → String → JSON → String
   - This is 2 levels of string nesting for JSON content

4. **Test with real Minecraft:**
   - Generate sample commands
   - Load in actual Minecraft 1.21
   - Verify books display correctly

## Reference Materials

### Official Sources
- Minecraft Wiki - NBT Format: https://minecraft.wiki/w/NBT_format
- Minecraft Wiki - Written Book: https://minecraft.wiki/w/Written_Book
- Minecraft Wiki - Data Component Format: https://minecraft.wiki/w/Data_component_format
- Minecraft Wiki - Text Component Format: https://minecraft.wiki/w/Text_component_format

### Community Resources
- Gaming StackExchange: Multiple Q&A about book command escaping
- Minecraft Forum: 1.12 JSON Text Component guide
- Gamer Geeks: Written Book command generator (handles escaping automatically)

### Known Issues
- MC-103171: Selectors in written books require operator status
- Version 1.21.5 changes: New component format causing compatibility issues

## Next Steps

1. Update `escapeForMinecraftCommand()` to be simpler and version-agnostic
2. Rewrite `generateBookCommand()` with proper escaping logic per version
3. Add comprehensive unit tests for edge cases
4. Test generated commands in actual Minecraft
5. Document the final escaping patterns in code comments

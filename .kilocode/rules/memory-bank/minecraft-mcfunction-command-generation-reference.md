# Minecraft MCFunction Command Generation - Complete Reference

**Last Updated:** 2025-11-18
**Purpose:** Comprehensive guide for generating valid Minecraft commands in .mcfunction files
**Focus:** Written books and signs across all Minecraft versions
**Applies to:** Minecraft Java Edition 1.13 - 1.21+

---

## Table of Contents

1. [MCFunction File Format](#mcfunction-file-format)
2. [Written Book Commands](#written-book-commands)
3. [Sign Commands](#sign-commands)
4. [Escaping Rules by Version](#escaping-rules-by-version)
5. [Common Pitfalls](#common-pitfalls)
6. [Testing and Validation](#testing-and-validation)
7. [Version Migration Guide](#version-migration-guide)

---

## MCFunction File Format

### File Structure

**Extension:** `.mcfunction`
**Location:** `data/<namespace>/function/`
**Format:** Plain text, UTF-8 encoding

### Syntax Rules

#### Commands

```mcfunction
# One command per line, no leading slash
give @p diamond 1
setblock ~ ~ ~ stone
```

**Key Points:**
- NO `/` prefix (unlike manual commands)
- One command per line
- Case-sensitive
- Max 32,500 characters per command

#### Comments

```mcfunction
# This is a comment
give @p diamond  # This is NOT a comment (part of command)
```

**Rules:**
- Line must START with `#` to be a comment
- `#` anywhere else is part of the command
- No inline comments

#### Line Continuation

```mcfunction
give @p written_book[written_book_content={title:"Long Title",\
author:"Author",pages:["Page 1"]}]
```

**Rules:**
- Backslash `\` as LAST non-whitespace character
- Leading/trailing whitespace of next line stripped
- Allows breaking long commands

### Execution Model

**Execution Order (per tick):**
1. Functions from advancements
2. Functions from enchantments
3. `#minecraft:tick` functions (top to bottom)
4. Scheduled functions (FIFO)
5. Manual function calls

**Context:**
- Inherits execution context (entity, position, dimension)
- `/execute` changes apply ONLY to that command
- Changes don't carry to next line

**Limits:**
- Max 65,536 commands per function (gamerule `maxCommandChainLength`)
- Commands beyond limit are silently ignored

---

## Written Book Commands

### Version Overview

| Version | Format | Namespace | Page Quotes |
|---------|--------|-----------|-------------|
| 1.13-1.14 | NBT Tags `{}` | N/A | Single `'...'` |
| 1.20-1.20.4 | NBT Tags `{}` | N/A | Single `'...'` |
| 1.20.5+ | Data Components `[]` | `minecraft:` | Double `"..."` |
| 1.21+ | Data Components `[]` | None | Double `"..."` |

---

### Version 1.13

**Command Format:**
```
give @p written_book{title:"Title",author:"Author",pages:['{"text":"page1"}','{"text":"page2"}']}
```

**Structure:**
- `written_book{...}` - NBT tag syntax
- `title:"..."` - Double-quoted string
- `author:"..."` - Double-quoted string
- `pages:[...]` - List of single-quoted JSON strings

**Page Format:**
```
'{"text":"content"}'
```

**Escaping Rules:**

1. **Title/Author (double-quoted context):**
   - Escape backslashes: `\` → `\\`
   - Escape double quotes: `"` → `\"`
   - Escape newlines: (newline) → `\\n`

2. **Pages (single-quoted context):**
   - Build JSON text component first
   - Escape backslashes IN THE JSON: `\` → `\\`
   - NO need to escape double quotes (inside single quotes)
   - Wrap in single quotes: `'...'`

**Example with Special Characters:**
```
give @p written_book{title:"He said \"hi\"",author:"Unknown",pages:['{"text":"Line 1\\nLine 2"}']}
```

**Explanation:**
- Title: `He said \"hi\"` - Double quotes escaped
- Page: `'{"text":"Line 1\\nLine 2"}'` - Backslash doubled for JSON newline, wrapped in single quotes

**Plain Text to Command:**

```groovy
// Input
String plainText = "Hello\nWorld"
String title = "My Book"

// Step 1: Escape for JSON
String jsonEscaped = plainText
    .replace('\\', '\\\\')    // \ → \\
    .replace('"', '\\"')      // " → \"
    .replace('\n', '\\n')     // newline → \n

String jsonComponent = "{\"text\":\"${jsonEscaped}\"}"
// Result: {"text":"Hello\\nWorld"}

// Step 2: Escape for SNBT (single-quoted)
String snbtEscaped = jsonComponent.replace('\\', '\\\\')
// Result: {"text":"Hello\\\\nWorld"}

// Step 3: Wrap in single quotes
String page = "'${snbtEscaped}'"
// Result: '{"text":"Hello\\\\nWorld"}'

// Step 4: Build command
String titleEscaped = title.replace('\\', '\\\\').replace('"', '\\"')
String command = "give @p written_book{title:\"${titleEscaped}\",author:\"Unknown\",pages:[${page}]}"
```

**Already-JSON Input:**

```groovy
// Input: already JSON from NBT
String rawText = "{\"text\":\"Hello\",\"color\":\"red\"}"

// Step 1: Use as-is (already valid JSON)
String jsonComponent = rawText

// Step 2: Escape backslashes for SNBT
String snbtEscaped = jsonComponent.replace('\\', '\\\\')
// Result: {\"text\":\"Hello\",\"color\":\"red\"}
// Becomes: {\\"text\\":\\"Hello\\",\\"color\\":\\"red\\"}

// Step 3: Wrap in single quotes
String page = "'${snbtEscaped}'"
```

---

### Version 1.14

**Command Format:**
```
give @p written_book{title:"Title",author:"Author",pages:['["page1"]','["page2"]']}
```

**Structure:**
- Same as 1.13 but pages wrapped in JSON array `[...]`

**Page Format:**
```
'["content"]'              # Plain text in array
'[{"text":"content"}]'     # JSON component in array
```

**Escaping Rules:**
Same as 1.13, but with array wrapper

**Example:**
```
give @p written_book{title:"Book",author:"Me",pages:['["Line 1"]','[{"text":"Colored","color":"red"}]']}
```

**Plain Text to Command:**

```groovy
// Input
String plainText = "Hello World"

// Step 1: Escape for JSON
String jsonEscaped = plainText
    .replace('\\', '\\\\')
    .replace('"', '\\"')
    .replace('\n', '\\n')

// Step 2: Wrap in JSON array
String jsonArray = "[\"${jsonEscaped}\"]"
// Result: ["Hello World"]

// Step 3: Escape backslashes for SNBT
String snbtEscaped = jsonArray.replace('\\', '\\\\')

// Step 4: Wrap in single quotes
String page = "'${snbtEscaped}'"
// Result: '["Hello World"]'
```

**Already-JSON Input:**

```groovy
// Input: JSON object from NBT
String rawText = "{\"text\":\"Hello\",\"color\":\"red\"}"

// If starts with '{', wrap in array
String jsonArray = "[${rawText}]"
// Result: [{"text":"Hello","color":"red"}]

// If starts with '[', use as-is
String jsonArray = rawText  // Already an array

// Escape backslashes
String snbtEscaped = jsonArray.replace('\\', '\\\\')

// Wrap in single quotes
String page = "'${snbtEscaped}'"
```

---

### Version 1.20.5

**Command Format:**
```
give @p written_book[minecraft:written_book_content={title:"Title",author:"Author",pages:["page1","page2"]}]
```

**Structure:**
- `written_book[...]` - Data component syntax (square brackets!)
- `minecraft:written_book_content={...}` - Namespaced component
- `pages:[...]` - Array of double-quoted strings

**Page Format:**
```
"content"                    # Plain string (Minecraft wraps in JSON)
"{\"text\":\"content\"}"     # Explicit JSON component
```

**Escaping Rules:**

1. **Title/Author (double-quoted context):**
   - Same as 1.13

2. **Pages (double-quoted context):**
   - Build JSON text component
   - Escape backslashes FIRST: `\` → `\\`
   - Then escape double quotes: `"` → `\"`
   - Wrap in double quotes: `"..."`

**Example:**
```
give @p written_book[minecraft:written_book_content={title:"Book",author:"Me",pages:["{\"text\":\"Hello\\nWorld\"}"]}]
```

**Plain Text to Command:**

```groovy
// Input
String plainText = "Hello\nWorld"

// Step 1: Escape for JSON
String jsonEscaped = plainText
    .replace('\\', '\\\\')
    .replace('"', '\\"')
    .replace('\n', '\\n')

String jsonComponent = "{\"text\":\"${jsonEscaped}\"}"
// Result: {"text":"Hello\\nWorld"}

// Step 2: Escape for SNBT (double-quoted)
// ORDER MATTERS: backslashes FIRST, then quotes
String snbtEscaped = jsonComponent
    .replace('\\', '\\\\')    // First: \ → \\
    .replace('"', '\\"')      // Then: " → \"

// Result: {\"text\":\"Hello\\nWorld\"}

// Step 3: Wrap in double quotes
String page = "\"${snbtEscaped}\""
// Result: "{\"text\":\"Hello\\nWorld\"}"

// Step 4: Build command
String command = "give @p written_book[minecraft:written_book_content={title:\"${titleEscaped}\",author:\"${authorEscaped}\",pages:[${page}]}]"
```

**Critical:** Escape backslashes BEFORE quotes!

**Wrong:**
```groovy
// WRONG ORDER - escapes quotes first
String wrong = jsonComponent
    .replace('"', '\\"')      // Creates \"
    .replace('\\', '\\\\')    // Escapes backslash, creating \\\"
// Result: \\\"text\\\":\\\"Hello... (WRONG!)
```

**Correct:**
```groovy
// CORRECT ORDER - backslashes first
String correct = jsonComponent
    .replace('\\', '\\\\')    // First: \ → \\
    .replace('"', '\\"')      // Then: " → \"
// Result: {\"text\":\"Hello... (CORRECT!)
```

---

### Version 1.21

**Command Format:**
```
give @p written_book[written_book_content={title:"Title",author:"Author",pages:["page1","page2"]}]
```

**Structure:**
- Same as 1.20.5 but WITHOUT `minecraft:` namespace prefix
- `written_book_content={...}` (no prefix)

**Escaping Rules:**
Identical to 1.20.5

**Example:**
```
give @p written_book[written_book_content={title:"My Book",author:"Me",pages:["{\"text\":\"Page 1\"}"]}]
```

**Implementation:**
Same code as 1.20.5, just remove namespace prefix from output

---

## Sign Commands

### Version Overview

| Version | Format | Text Fields | Messages Array |
|---------|--------|-------------|----------------|
| 1.13-1.14 | `Text1-4` | 4 individual | N/A |
| 1.20 | `front_text` | 4-element array | **Required** |
| 1.20.5+ | `front_text` | 4-element array | **Required** |
| 1.21 | `front_text` | 4-element array | **Required** |

---

### Version 1.13-1.14

**Command Format:**
```
setblock ~ ~ ~ oak_sign{Text1:'[\"\":{\"text\":\"line1\"}]',Text2:'[\"\":{\"text\":\"line2\"}]',Text3:'[\"\":{\"text\":\"line3\"}]',Text4:'[\"\":{\"text\":\"line4\"}]',GlowingText:0} replace
```

**Structure:**
- Individual `Text1`, `Text2`, `Text3`, `Text4` fields
- Each field is single-quoted JSON
- Format: `'[\"\":{\"text\":\"...\"}]'`

**Escaping:**
Same as 1.13 books (single-quoted context)

---

### Version 1.20

**Command Format:**
```
setblock ~ ~ ~ oak_sign[rotation=0,waterlogged=false]{front_text:{messages:['msg1','msg2','msg3','msg4'],has_glowing_text:0},back_text:{messages:['','','',''],has_glowing_text:0},is_waxed:0} replace
```

**CRITICAL REQUIREMENT:**
Both `front_text.messages` and `back_text.messages` MUST have **exactly 4 elements**

**Error if not 4 elements:**
```
[Server thread/ERROR]: Input is not a list of 4 elements
```

**Structure:**
- `front_text:{messages:[...]}` - 4 messages for front
- `back_text:{messages:[...]}` - 4 messages for back (can be empty)
- Each message is single-quoted JSON

**Example:**
```
setblock ~0 ~ ~0 oak_sign[rotation=0,waterlogged=false]{front_text:{messages:['[\"\":{\"text\":\"Line 1\"}]','[\"\":{\"text\":\"Line 2\"}]','[\"\":{\"text\":\"\"}]','[\"\":{\"text\":\"\"}]'],has_glowing_text:0},back_text:{messages:['[\"\":{\"text\":\"\"}]','[\"\":{\"text\":\"\"}]','[\"\":{\"text\":\"\"}]','[\"\":{\"text\":\"\"}]'],has_glowing_text:0},is_waxed:0} replace
```

**Empty Line:**
```
'[\"\":{\"text\":\"\"}]'
```

**Groovy Generation:**

```groovy
// Generate front_text (4 lines from input)
String frontMessages = (0..3).collect { int i ->
    String line = i < lines.size() ? lines[i] : ''
    String escaped = escapeForSnbt(line)
    "'[\\\"\\\":{\\\"text\\\":\\\"${escaped}\\\"}]'"
}.join(',')

// Generate back_text (4 empty messages)
String backMessages = (0..3).collect {
    "'[\\\"\\\":{\\\"text\\\":\\\"\\\"}]'"
}.join(',')

String command = "setblock ~${x} ~ ~${z} oak_sign[rotation=0,waterlogged=false]{front_text:{messages:[${frontMessages}],has_glowing_text:0},back_text:{messages:[${backMessages}],has_glowing_text:0},is_waxed:0} replace"
```

---

### Version 1.20.5+

**Command Format:**
```
setblock ~ ~ ~ oak_sign[rotation=0,waterlogged=false]{front_text:{messages:['{"text":"line1"}','{"text":"line2"}','{"text":""}','{"text":""}'],has_glowing_text:0},back_text:{messages:['{"text":""}','{"text":""}','{"text":""}','{"text":""}'],has_glowing_text:0},is_waxed:0} replace
```

**Structure:**
- Simpler JSON format: `'{"text":"..."}'`
- Still requires 4 elements in each array

**Example:**
```
setblock ~5 ~ ~10 oak_sign[rotation=0,waterlogged=false]{front_text:{messages:['{"text":"Shop"}','{"text":""}','{"text":""}','{"text":""}'],has_glowing_text:0},back_text:{messages:['{"text":""}','{"text":""}','{"text":""}','{"text":""}'],has_glowing_text:0},is_waxed:0} replace
```

**Groovy Generation:**

```groovy
// Generate front_text
String frontMessages = (0..3).collect { int i ->
    String line = i < lines.size() ? lines[i] : ''
    String escaped = escapeForSnbt(line)
    '\'{"text":"' + escaped + '"}\''
}.join(',')

// Generate back_text (4 empty messages)
String backMessages = (0..3).collect {
    '\'{"text":""}\''
}.join(',')

String command = "setblock ~${x} ~ ~${z} oak_sign[rotation=0,waterlogged=false]{front_text:{messages:[${frontMessages}],has_glowing_text:0},back_text:{messages:[${backMessages}],has_glowing_text:0},is_waxed:0} replace"
```

---

### Version 1.21

**Format:** Same as 1.20.5

**Implementation:** Reuse 1.20.5 code

---

## Escaping Rules by Version

### Summary Table

| Version | Book Pages | Sign Messages | Title/Author |
|---------|-----------|---------------|--------------|
| 1.13 | Single-quote, escape `\\` | Single-quote, escape `\\` | Double-quote, escape `\\` and `\"` |
| 1.14 | Single-quote, escape `\\` | Single-quote, escape `\\` | Double-quote, escape `\\` and `\"` |
| 1.20 | Single-quote, escape `\\` | Single-quote, escape `\\` | Double-quote, escape `\\` and `\"` |
| 1.20.5 | Double-quote, escape `\\` then `\"` | Single-quote, escape `\\` | Double-quote, escape `\\` and `\"` |
| 1.21 | Double-quote, escape `\\` then `\"` | Single-quote, escape `\\` | Double-quote, escape `\\` and `\"` |

### Universal Escaping Utility

```groovy
/**
 * Escape text for SNBT double-quoted strings
 * Handles: backslashes, double quotes, newlines, tabs, carriage returns
 */
static String escapeForSnbt(String text) {
    if (!text) return ''

    return text
        .replace('\\', '\\\\')      // Backslash → double backslash
        .replace('"', '\\"')        // Quote → escaped quote
        .replace('\n', '\\n')       // Newline → escaped newline
        .replace('\r', '\\r')       // CR → escaped CR
        .replace('\t', '\\t')       // Tab → escaped tab
}
```

**Use for:**
- Book titles and authors (all versions)
- Sign text lines (all versions)
- Any double-quoted SNBT context

**Do NOT use directly for:**
- Book pages in single-quoted context (1.13-1.20)
  - Only escape backslashes, not quotes
- JSON text component content
  - Escape first for JSON, then for SNBT

### Escaping Workflow

#### Plain Text → Book Command (1.13-1.14)

```
Plain Text: Hello "World"
     ↓ Escape for JSON
JSON: {"text":"Hello \"World\""}
     ↓ Escape backslashes only (single-quote context)
SNBT: {"text":"Hello \\"World\\"}
     ↓ Wrap in single quotes
Page: '{"text":"Hello \\"World\\"}'
     ↓ Use in command
Command: give @p written_book{pages:['{"text":"Hello \\"World\\"}']...}
```

#### Plain Text → Book Command (1.20.5+)

```
Plain Text: Hello "World"
     ↓ Escape for JSON
JSON: {"text":"Hello \"World\""}
     ↓ Escape backslashes THEN quotes (double-quote context)
SNBT: {\"text\":\"Hello \\\"World\\\"\"}
     ↓ Wrap in double quotes
Page: "{\"text\":\"Hello \\\"World\\\"\"}"
     ↓ Use in command
Command: give @p written_book[...pages:["{\"text\":\"Hello \\\"World\\\"\"}"]...]
```

#### Already-JSON → Book Command (1.13-1.14)

```
JSON Input: {"text":"Hello","color":"red"}
     ↓ Escape backslashes only
SNBT: {\"text\":\"Hello\",\"color\":\"red\"}
      Becomes: {\\"text\\":\\"Hello\\",\\"color\\":\\"red\\"}
     ↓ Wrap in single quotes
Page: '{\"text\":\"Hello\",\"color\":\"red\"}'
     ↓ Use in command
Command: give @p written_book{pages:['{\\"text\\":\\"Hello\\",\\"color\\":\\"red\\"}']...}
```

---

## Common Pitfalls

### 1. Wrong Escaping Order

**WRONG:**
```groovy
String escaped = text
    .replace('"', '\\"')      // Quotes first
    .replace('\\', '\\\\')    // Backslashes second - ESCAPES THE ESCAPES!
```

**Result:** `\"` becomes `\\\"` (wrong!)

**CORRECT:**
```groovy
String escaped = text
    .replace('\\', '\\\\')    // Backslashes first
    .replace('"', '\\"')      // Quotes second
```

**Result:** `\"` stays `\"` (correct!)

### 2. Missing Sign Messages Elements

**WRONG:**
```
back_text:{messages:[]}  # Empty array
```

**Error:** "Input is not a list of 4 elements"

**CORRECT:**
```
back_text:{messages:['{"text":""}','{"text":""}','{"text":""}','{"text":""}']}
```

### 3. Using Double Quotes in Single-Quote Context

**WRONG (wasteful escaping):**
```groovy
// In single-quote context
String page = '\\"text\\"'  # Don't need to escape quotes!
```

**CORRECT:**
```groovy
String page = '"text"'  # Quotes are fine in single-quote context
```

### 4. Forgetting JSON Layer

**WRONG:**
```
give @p written_book{pages:['Hello World']}  # Not valid JSON
```

**CORRECT:**
```
give @p written_book{pages:['{"text":"Hello World"}']}  # Valid JSON
```

### 5. Not Handling Already-JSON Input

**WRONG:**
```groovy
String rawText = "{\"text\":\"Hello\"}"
String wrapped = "{\"text\":\"${rawText}\"}"  # Double-wrapped!
// Result: {"text":"{\"text\":\"Hello\"}"}  # WRONG!
```

**CORRECT:**
```groovy
String rawText = "{\"text\":\"Hello\"}"
if (rawText.startsWith('{') || rawText.startsWith('[')) {
    // Already JSON, use as-is
    jsonComponent = rawText
} else {
    // Plain text, wrap it
    jsonComponent = "{\"text\":\"${rawText}\"}"
}
```

### 6. Namespace Confusion

**1.20.5:**
```
written_book[minecraft:written_book_content={...}]  # Has namespace
```

**1.21:**
```
written_book[written_book_content={...}]  # No namespace
```

**Common error:** Using 1.20.5 command in 1.21 - command fails silently

---

## Testing and Validation

### Manual Testing in Minecraft

1. **Create test world**
2. **Place command block:**
   ```
   /setblock ~ ~ ~ command_block
   ```
3. **Copy generated command into block**
4. **Activate block:**
   - If success: Item/block appears
   - If failure: Error in log

### Common Error Messages

| Error | Cause | Fix |
|-------|-------|-----|
| "Invalid string contents at position X" | Quote escaping wrong | Fix escaping order |
| "Input is not a list of 4 elements" | Sign messages count ≠ 4 | Always use 4 elements |
| "Expected ':' at position X" | Missing colon in NBT | Check NBT syntax |
| "Unknown or invalid command" | Wrong version format | Use correct version syntax |

### Validation Tools

**Online Generators:**
- Gamer Geeks Written Book: https://www.gamergeeks.net/apps/minecraft/give-command-generator/written-books
- JSON Text Generator: https://minecraft.tools/en/json_text.php

**Strategy:**
1. Generate command with tool
2. Compare with your output
3. Identify differences
4. Fix escaping/format

### Automated Testing

```groovy
@Test
void "test book command generation for 1.21"() {
    String title = "Test Book"
    String author = "Tester"
    String page = "Hello\nWorld"

    String command = generateBookCommand_1_21(title, author, [page])

    // Test structure
    assert command.startsWith("give @p written_book[written_book_content=")
    assert command.contains("title:")
    assert command.contains("author:")
    assert command.contains("pages:[")

    // Test escaping
    assert command.contains("Hello\\nWorld")  // Newline escaped

    // Test JSON validity (extract and parse)
    String pageContent = extractPageContent(command)
    def json = new JsonSlurper().parseText(pageContent)
    assert json.text == "Hello\nWorld"  // After parsing, newline restored
}
```

---

## Version Migration Guide

### From 1.13 to 1.14

**Changes:** Pages now wrapped in JSON array `[...]`

**Migration:**
```groovy
// 1.13
'{"text":"content"}'

// 1.14
'["content"]'  // OR
'[{"text":"content"}]'
```

### From 1.14 to 1.20.5

**Changes:**
1. NBT `{}` → Data Components `[]`
2. Add namespace: `minecraft:written_book_content`
3. Single quotes `'...'` → Double quotes `"..."`
4. Update escaping: also escape double quotes

**Migration:**
```groovy
// 1.14
give @p written_book{title:"T",author:"A",pages:['["p1"]']}

// 1.20.5
give @p written_book[minecraft:written_book_content={title:"T",author:"A",pages:["{\"text\":\"p1\"}"]}]
```

### From 1.20.5 to 1.21

**Changes:** Remove `minecraft:` namespace prefix

**Migration:**
```groovy
// 1.20.5
written_book[minecraft:written_book_content={...}]

// 1.21
written_book[written_book_content={...}]
```

**Code change:**
```groovy
// Version-specific prefix
String namespace = version == '1_20_5' ? 'minecraft:' : ''
String command = "give @p written_book[${namespace}written_book_content={...}]"
```

---

## Reference Implementation

### Complete Book Command Generator

```groovy
static String generateBookCommand(String title, String author, List<String> pages, String version) {
    String escapedTitle = escapeForSnbt(title ?: 'Untitled')
    String escapedAuthor = escapeForSnbt(author ?: 'Unknown')

    String pagesStr

    switch (version) {
        case '1_13':
            pagesStr = pages.collect { page ->
                String jsonComponent = buildJsonComponent(page)
                String snbtEscaped = jsonComponent.replace('\\', '\\\\')
                "'${snbtEscaped}'"
            }.join(',')
            return "give @p written_book{title:\"${escapedTitle}\",author:\"${escapedAuthor}\",pages:[${pagesStr}]}"

        case '1_14':
            pagesStr = pages.collect { page ->
                String jsonArray = buildJsonArray(page)
                String snbtEscaped = jsonArray.replace('\\', '\\\\')
                "'${snbtEscaped}'"
            }.join(',')
            return "give @p written_book{title:\"${escapedTitle}\",author:\"${escapedAuthor}\",pages:[${pagesStr}]}"

        case '1_20_5':
            pagesStr = pages.collect { page ->
                String jsonComponent = buildJsonComponent(page)
                String snbtEscaped = jsonComponent
                    .replace('\\', '\\\\')
                    .replace('"', '\\"')
                "\"${snbtEscaped}\""
            }.join(',')
            return "give @p written_book[minecraft:written_book_content={title:\"${escapedTitle}\",author:\"${escapedAuthor}\",pages:[${pagesStr}]}]"

        case '1_21':
            pagesStr = pages.collect { page ->
                String jsonComponent = buildJsonComponent(page)
                String snbtEscaped = jsonComponent
                    .replace('\\', '\\\\')
                    .replace('"', '\\"')
                "\"${snbtEscaped}\""
            }.join(',')
            return "give @p written_book[written_book_content={title:\"${escapedTitle}\",author:\"${escapedAuthor}\",pages:[${pagesStr}]}]"
    }
}

static String buildJsonComponent(String text) {
    if (text.startsWith('{') || text.startsWith('[')) {
        return text  // Already JSON
    }
    String escaped = text
        .replace('\\', '\\\\')
        .replace('"', '\\"')
        .replace('\n', '\\n')
    return "{\"text\":\"${escaped}\"}"
}

static String buildJsonArray(String text) {
    if (text.startsWith('[')) return text
    if (text.startsWith('{')) return "[${text}]"
    String escaped = text
        .replace('\\', '\\\\')
        .replace('"', '\\"')
        .replace('\n', '\\n')
    return "[\"${escaped}\"]"
}

static String escapeForSnbt(String text) {
    if (!text) return ''
    return text
        .replace('\\', '\\\\')
        .replace('"', '\\"')
        .replace('\n', '\\n')
        .replace('\r', '\\r')
        .replace('\t', '\\t')
}
```

---

## Reference Links

### Official Documentation
- Minecraft Wiki - Function: https://minecraft.wiki/w/Function_(Java_Edition)
- Minecraft Wiki - Commands: https://minecraft.wiki/w/Commands

### Community Resources
- MCFunction Syntax for Notepad++: https://www.minecraftforum.net/forums/minecraft-java-edition/redstone-discussion-and/commands-command-blocks-and/2860372-mcfunction-syntax-definition-for-notepad
- Written Book Generator: https://www.gamergeeks.net/apps/minecraft/give-command-generator/written-books

### Technical References
- Quote Escaping Guide: https://gaming.stackexchange.com/questions/384883/how-do-i-use-quotes-in-an-extremely-nested-command
- Text Component Guide: https://www.minecraftforum.net/forums/minecraft-java-edition/redstone-discussion-and/351959-1-12-json-text-component-for-tellraw-title-books

---

**End of Reference Document**

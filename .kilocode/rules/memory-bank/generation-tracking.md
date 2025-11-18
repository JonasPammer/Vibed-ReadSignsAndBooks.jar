# Book Generation Tracking - Technical Specification

## Feature Overview
Track the "generation" or copy tier of Minecraft written books to distinguish between originals, copies, copies of copies, and tattered books. This metadata is extracted from book NBT data and included in output formats (CSV, mcfunction, Stendhal).

## Minecraft Book Generation System

### Generation Values
Minecraft tracks book copying with an integer field called `generation`:

- **0 = Original** - The first book created by signing a book & quill
- **1 = Copy of Original** - Created by copying an original book
- **2 = Copy of Copy** - Created by copying a "Copy of Original" book
- **3 = Tattered** - Maximum copy tier (unused in normal gameplay, functions like tier 2)

### Copy Behavior
- **Books with generation > 1 cannot be copied** (including tattered books)
- **Only originals (0) and copies (1) can be copied**
- **Missing generation field = assumed to be original (0)**

### Human-Readable Labels
For user-facing output, generation values are mapped to human-readable labels:

- **0 → "Original"**
- **1 → "Copy of Original"**
- **2 → "Copy of Copy"**
- **3 → "Tattered"**

## NBT Format Specifications

### Pre-1.20.5 Format (Legacy NBT Tags)

**Location:** `item.tag.generation`

**Data Type:** Byte or Integer (NBT implementation varies)

**Access Pattern:**
```groovy
CompoundTag tag = item.getCompoundTag('tag')
int generation = tag.getByte('generation')  // Returns 0 if missing
// OR
int generation = tag.getInt('generation')   // Returns 0 if missing
```

**Example NBT Structure:**
```json
{
  "id": "minecraft:written_book",
  "Count": 1,
  "tag": {
    "title": "My Book",
    "author": "Player",
    "pages": [...],
    "generation": 1  // Byte value: 0, 1, 2, or 3
  }
}
```

### 1.20.5+ Format (Data Components)

**Location:** `item.components.minecraft:written_book_content.generation`

**Data Type:** Integer

**Access Pattern:**
```groovy
CompoundTag components = item.getCompoundTag('components')
CompoundTag bookContent = components.getCompoundTag('minecraft:written_book_content')
int generation = bookContent.getInt('generation')  // Returns 0 if missing
```

**Example NBT Structure:**
```json
{
  "id": "minecraft:written_book",
  "count": 1,
  "components": {
    "minecraft:written_book_content": {
      "title": {"raw": "My Book"},
      "author": "Player",
      "pages": [...],
      "generation": 1  // Integer value: 0, 1, 2, or 3
    }
  }
}
```

### Version Detection Strategy

**Multi-Format Support:**
The code must handle both formats since world files can contain books from different Minecraft versions:

1. **Check for 1.20.5+ format first** (components path)
2. **Fall back to pre-1.20.5 format** (tag path)
3. **Default to 0 (Original)** if neither exists

**Implementation Pattern:**
```groovy
int generation = 0  // Default: Original

// Try 1.20.5+ format first
if (hasKey(item, 'components')) {
    CompoundTag components = getCompoundTag(item, 'components')
    if (hasKey(components, 'minecraft:written_book_content')) {
        CompoundTag bookContent = getCompoundTag(components, 'minecraft:written_book_content')
        generation = bookContent.getInt('generation')
    }
}
// Fall back to pre-1.20.5 format
else if (hasKey(item, 'tag')) {
    CompoundTag tag = getCompoundTag(item, 'tag')
    generation = tag.getByte('generation')  // Querz returns 0 if missing
}

// Map to human-readable label
String generationLabel = getGenerationLabel(generation)
```

## Implementation Changes

### 1. Book Metadata Tracking

**New Static Field:**
Add generation tracking to book metadata structures.

**Modified Data Structures:**
```groovy
// Add to bookMetadataList entries
bookMetadataList.add([
    title: title,
    author: author,
    pageCount: pages.size(),
    foundWhere: foundWhere,
    coordinates: coords,
    generation: generation,              // NEW: integer value (0-3)
    generationLabel: generationLabel    // NEW: human-readable label
])

// Add to bookCsvData entries
bookCsvData.add([
    x: x,
    y: y,
    z: z,
    foundWhere: foundWhere,
    bookname: title,
    author: author,
    pageCount: pages.size(),
    pages: concatenatedPages,
    generation: generation,              // NEW
    generationLabel: generationLabel    // NEW
])

// Add to booksByAuthor entries (for shulker generation)
booksByAuthor[author].add([
    title: title,
    author: author,
    pages: pages,
    generation: generation              // NEW
])
```

### 2. Stendhal Output Format

**Modified File Format:**
Add generation metadata to `.stendhal` book files.

**New Format:**
```yaml
title: My Book
author: Player
generation: 1
generation_label: Copy of Original
pages:
#- Page 1 text here
#- Page 2 text here
```

**Implementation:**
```groovy
bookFile.withWriter('UTF-8') { BufferedWriter writer ->
    writer.writeLine("title: ${title ?: 'Untitled'}")
    writer.writeLine("author: ${author ?: ''}")
    writer.writeLine("generation: ${generation}")                    // NEW
    writer.writeLine("generation_label: ${generationLabel}")         // NEW
    writer.writeLine('pages:')
    // ... rest of pages
}
```

### 3. CSV Output Format

**New Columns:**
Add two new columns to `books.csv`:

- **generation** (integer 0-3)
- **generation_label** (string: "Original", "Copy of Original", etc.)

**Column Order:**
```
X,Y,Z,Found Where,Book Title,Author,Page Count,Generation,Generation Label,Content Preview
```

**Implementation:**
```groovy
static void writeBooksCSV() {
    File csvFile = new File(baseDirectory, "${outputFolder}${File.separator}books.csv")
    csvFile.withWriter('UTF-8') { BufferedWriter writer ->
        // Header
        writer.writeLine('X,Y,Z,Found Where,Book Title,Author,Page Count,Generation,Generation Label,Content Preview')

        // Data rows
        bookCsvData.each { Map<String, Object> book ->
            String contentPreview = (book.pages as String)?.take(100)?.replace('\n', ' ')?.replace('"', '""')
            writer.writeLine("${book.x},${book.y},${book.z},\"${book.foundWhere}\",\"${book.bookname}\",\"${book.author}\",${book.pageCount},${book.generation},\"${book.generationLabel}\",\"${contentPreview}\"")
        }
    }
}
```

### 4. Minecraft Command Generation

**NBT Injection:**
Add `generation` field to all generated `/give` commands to preserve copy tier when recreating books.

#### 1.13 Format
```
give @p written_book{title:"...",author:"...",generation:1,pages:[...]}
```

#### 1.14 Format
```
give @p written_book{title:"...",author:"...",generation:1,pages:[...]}
```

#### 1.20.5 Format
```
give @p written_book[minecraft:written_book_content={title:"...",author:"...",generation:1,pages:[...]}]
```

#### 1.21 Format
```
give @p written_book[written_book_content={title:"...",author:"...",generation:1,pages:[...]}]
```

**Implementation:**
```groovy
static String generateBookCommand(String title, String author, ListTag<?> pages, int generation, String version) {
    // ... existing code for title, author, pages ...

    switch (version) {
        case '1_13':
            return "give @p written_book{title:\"${escapedTitle}\",author:\"${escapedAuthor}\",generation:${generation},pages:[${pagesStr}]}"

        case '1_14':
            return "give @p written_book{title:\"${escapedTitle}\",author:\"${escapedAuthor}\",generation:${generation},pages:[${pagesStr}]}"

        case '1_20_5':
            return "give @p written_book[minecraft:written_book_content={title:\"${escapedTitle}\",author:\"${escapedAuthor}\",generation:${generation},pages:[${pagesStr}]}]"

        case '1_21':
            return "give @p written_book[written_book_content={title:\"${escapedTitle}\",author:\"${escapedAuthor}\",generation:${generation},pages:[${pagesStr}]}]"
    }
}
```

**Shulker Box Commands:**
Similarly update `generateBookNBT()` and `generateBookComponents()` to include generation.

### 5. Duplicates Folder Logic

**Current Behavior:**
- First discovered book (by content hash) → saved to `books/`
- Subsequent identical books → saved to `books/.duplicates/`

**New Requirement:**
Ensure originals (generation = 0) are **never** placed in `.duplicates/` folder.

**Solution:**
Post-processing step after extraction completes to swap any originals found in duplicates folder with non-originals.

**Implementation Strategy:**

```groovy
static void ensureOriginalsNotInDuplicates() {
    LOGGER.info("Checking for originals in .duplicates folder...")

    File duplicatesDir = new File(baseDirectory, duplicatesFolder)
    if (!duplicatesDir.exists()) {
        LOGGER.debug("No .duplicates folder exists - skipping check")
        return
    }

    // Track books by content hash
    Map<Integer, List<Map>> booksByHash = [:]

    // Scan all .stendhal files in both folders
    [booksFolder, duplicatesFolder].each { String folder ->
        File dir = new File(baseDirectory, folder)
        dir.listFiles()?.findAll { it.name.endsWith('.stendhal') }?.each { File bookFile ->
            // Parse .stendhal file to extract generation
            Map bookData = parseStendhalFile(bookFile)
            int contentHash = bookData.contentHash

            if (!booksByHash.containsKey(contentHash)) {
                booksByHash[contentHash] = []
            }

            booksByHash[contentHash].add([
                file: bookFile,
                generation: bookData.generation,
                isInDuplicates: folder == duplicatesFolder
            ])
        }
    }

    // For each content hash, ensure original (if exists) is in books/ folder
    int swapsPerformed = 0
    booksByHash.each { int hash, List<Map> copies ->
        // Find original (generation = 0)
        Map original = copies.find { it.generation == 0 }
        if (!original) {
            return  // No original exists for this content
        }

        // If original is in .duplicates/, swap with a non-original from books/
        if (original.isInDuplicates) {
            Map nonOriginal = copies.find { it.generation != 0 && !it.isInDuplicates }
            if (nonOriginal) {
                // Swap files
                File tempFile = File.createTempFile('swap', '.stendhal')
                original.file.renameTo(tempFile)
                nonOriginal.file.renameTo(original.file)
                tempFile.renameTo(nonOriginal.file)

                swapsPerformed++
                LOGGER.debug("Swapped original \"${original.file.name}\" from .duplicates to books/")
            } else {
                // All copies are originals or only original exists - move to books/
                File newLocation = new File(baseDirectory, "${booksFolder}${File.separator}${original.file.name}")
                original.file.renameTo(newLocation)
                swapsPerformed++
                LOGGER.debug("Moved original \"${original.file.name}\" from .duplicates to books/")
            }
        }
    }

    if (swapsPerformed > 0) {
        LOGGER.info("✓ Ensured ${swapsPerformed} original(s) are in books/ folder (not .duplicates/)")
    } else {
        LOGGER.debug("No originals found in .duplicates/ - folder structure is correct")
    }
}
```

**Call Location:**
Add to `runExtraction()` after all books are written:

```groovy
static void runExtraction() {
    // ... existing extraction code ...

    combinedBooksWriter?.close()
    mcfunctionWriters.values().each { it?.close() }
    signsMcfunctionWriters.values().each { it?.close() }

    // NEW: Ensure originals aren't in duplicates folder
    ensureOriginalsNotInDuplicates()

    // ... rest of code (CSV, summary, etc.) ...
}
```

## Helper Methods

### Generation Label Mapping

```groovy
/**
 * Convert generation integer to human-readable label
 * @param generation Integer 0-3 representing copy tier
 * @return Human-readable label string
 */
static String getGenerationLabel(int generation) {
    switch (generation) {
        case 0: return 'Original'
        case 1: return 'Copy of Original'
        case 2: return 'Copy of Copy'
        case 3: return 'Tattered'
        default:
            LOGGER.warn("Unknown generation value: ${generation}, defaulting to 'Original'")
            return 'Original'
    }
}
```

### Stendhal File Parsing (for post-processing)

```groovy
/**
 * Parse a .stendhal file to extract generation metadata
 * @param bookFile File object pointing to .stendhal file
 * @return Map with generation, contentHash, etc.
 */
static Map parseStendhalFile(File bookFile) {
    int generation = 0
    List<String> pages = []

    bookFile.eachLine('UTF-8') { String line ->
        if (line.startsWith('generation:')) {
            generation = line.split(':')[1].trim() as int
        } else if (line.startsWith('#-')) {
            pages.add(line.substring(2).trim())
        }
    }

    // Compute content hash (same algorithm as bookHashes)
    int contentHash = pages.hashCode()

    return [
        generation: generation,
        contentHash: contentHash
    ]
}
```

## Testing Considerations

### Integration Test Updates

**Test World Requirements:**
- Include books with different generation values (0, 1, 2, 3)
- Include duplicate books with different generations
- Include original books in various containers

**Test Assertions:**
```groovy
def "should extract generation metadata correctly"() {
    when: 'extraction runs'
    Main.runExtraction()

    then: 'CSV includes generation columns'
    Path csvFile = outputDir.resolve('books.csv')
    List<String> csvLines = Files.readAllLines(csvFile)
    assert csvLines[0].contains('Generation')
    assert csvLines[0].contains('Generation Label')

    and: 'generation values are present in data rows'
    assert csvLines.any { it.contains(',0,Original,') }
    assert csvLines.any { it.contains(',1,Copy of Original,') }
}

def "should place originals in books/ folder, not .duplicates/"() {
    when: 'extraction runs'
    Main.runExtraction()

    then: 'all originals are in books/ folder'
    Path booksDir = outputDir.resolve('books')
    Path duplicatesDir = booksDir.resolve('.duplicates')

    // Parse all .stendhal files in books/
    List<Integer> booksGenerations = Files.list(booksDir)
        .filter { it.toString().endsWith('.stendhal') }
        .map { parseStendhalFile(it.toFile()) }
        .map { it.generation as int }
        .toList()

    // Parse all .stendhal files in .duplicates/
    List<Integer> duplicatesGenerations = Files.list(duplicatesDir)
        .filter { it.toString().endsWith('.stendhal') }
        .map { parseStendhalFile(it.toFile()) }
        .map { it.generation as int }
        .toList()

    // Ensure no originals (0) in duplicates folder
    assert !duplicatesGenerations.contains(0)
}

def "should include generation in mcfunction commands"() {
    when: 'extraction runs'
    Main.runExtraction()

    then: 'mcfunction files include generation NBT'
    Path mcfunction113 = outputDir.resolve('all_books-1_13.mcfunction')
    String content = Files.readString(mcfunction113)

    // Should contain generation field in NBT
    assert content.contains('generation:')
}
```

## Documentation Updates

### README Updates

Add new section describing generation tracking:

```markdown
### Book Generation Tracking

ReadSignsAndBooks now tracks the **generation** (copy tier) of written books:

- **Original (0)**: First signed book
- **Copy of Original (1)**: Copied from an original
- **Copy of Copy (2)**: Copied from a copy
- **Tattered (3)**: Maximum copy tier (rare)

This information is included in:
- **CSV exports** (new `generation` and `generation_label` columns)
- **Stendhal files** (new `generation:` and `generation_label:` fields)
- **Minecraft commands** (generation NBT preserved in `/give` commands)

**Smart Deduplication:**
Original books (generation = 0) are never placed in the `.duplicates/` folder, ensuring the canonical version is always in the main `books/` directory.
```

### Memory Bank Updates

Update:
- `architecture.md` - Add generation tracking to data processing pipeline
- `product.md` - Add generation tracking to features list
- `tech.md` - Document new CSV columns and Stendhal format changes

## Performance Impact

**Expected Impact:**
- **Minimal** - Only adds 2 integer field reads per book
- **No additional file I/O** - Generation extracted during existing NBT parsing
- **Post-processing overhead** - `ensureOriginalsNotInDuplicates()` runs once at end, O(n) where n = number of books

**Memory Impact:**
- **Negligible** - Two additional fields per book in metadata structures

## Backwards Compatibility

**File Format Changes:**
- **Stendhal files** - New optional fields (parsers should ignore unknown fields)
- **CSV format** - New columns at end (Excel/tools ignore extra columns)
- **Minecraft commands** - Generation NBT is valid in all Minecraft versions

**No Breaking Changes:**
All changes are additive and maintain backwards compatibility with existing tooling.

# Integration Test Specifications

**Created:** 2025-11-18
**Purpose:** Comprehensive test specifications for ReadSignsAndBooks.jar generation tracking and duplicates folder logic
**Framework:** Spock 2.3-groovy-4.0
**Status:** Implementation pending (specifications complete)

---

## Table of Contents

1. [Test Suite Overview](#test-suite-overview)
2. [Generation Extraction Tests](#generation-extraction-tests)
3. [Generation Label Mapping Tests](#generation-label-mapping-tests)
4. [Stendhal File Parsing Tests](#stendhal-file-parsing-tests)
5. [Duplicates Folder Logic Tests](#duplicates-folder-logic-tests)
6. [Output Format Tests](#output-format-tests)
7. [Mcfunction Command Tests](#mcfunction-command-tests)
8. [Multi-Version Compatibility Tests](#multi-version-compatibility-tests)
9. [Edge Cases and Error Handling Tests](#edge-cases-and-error-handling-tests)
10. [Test Data Requirements](#test-data-requirements)

---

## Test Suite Overview

### Goals

**Primary Objectives:**
1. Verify generation extraction from NBT data (both pre-1.20.5 and 1.20.5+ formats)
2. Validate generation label mapping (0-3 → human-readable strings)
3. Test parseStendhalFile() helper method accuracy
4. Ensure ensureOriginalsNotInDuplicates() correctly repositions original books
5. Confirm all output formats include generation metadata
6. Test multi-version compatibility

### Test Organization

```
src/test/groovy/
├── GenerationTrackingSpec.groovy           (Unit tests)
├── StendhalParsingSpec.groovy              (Unit tests)
├── DuplicatesFolderLogicSpec.groovy        (Integration tests)
├── MultiVersionGenerationSpec.groovy       (Integration tests)
└── ReadBooksIntegrationSpec.groovy         (Existing integration tests - extend)
```

---

## Generation Extraction Tests

### Unit Test: GenerationTrackingSpec.groovy

**Purpose:** Test extractBookGeneration() method with various NBT structures

#### Test 1: Extract generation from pre-1.20.5 format

```groovy
@Unroll
def "extract generation #generation from pre-1.20.5 NBT format"() {
    given: "a book in pre-1.20.5 format with generation #generation"
    CompoundTag item = new CompoundTag()
    item.putString('id', 'minecraft:written_book')

    CompoundTag tag = new CompoundTag()
    tag.putByte('generation', (byte) generation)
    item.put('tag', tag)

    when: "extracting generation"
    int extracted = Main.extractBookGeneration(item)

    then: "extracted generation matches"
    extracted == generation

    where:
    generation << [0, 1, 2, 3]
}
```

#### Test 2: Extract generation from 1.20.5+ format

```groovy
@Unroll
def "extract generation #generation from 1.20.5+ NBT format"() {
    given: "a book in 1.20.5+ format with generation #generation"
    CompoundTag item = new CompoundTag()
    item.putString('id', 'minecraft:written_book')

    CompoundTag components = new CompoundTag()
    CompoundTag bookContent = new CompoundTag()
    bookContent.putInt('generation', generation)
    components.put('minecraft:written_book_content', bookContent)
    item.put('components', components)

    when: "extracting generation"
    int extracted = Main.extractBookGeneration(item)

    then: "extracted generation matches"
    extracted == generation

    where:
    generation << [0, 1, 2, 3]
}
```

#### Test 3: Missing generation defaults to 0

```groovy
def "missing generation field defaults to 0 (Original)"() {
    given: "a book without generation field"
    CompoundTag item = new CompoundTag()
    item.putString('id', 'minecraft:written_book')

    CompoundTag tag = new CompoundTag()
    tag.putString('title', 'Test Book')
    // No generation field
    item.put('tag', tag)

    when: "extracting generation"
    int extracted = Main.extractBookGeneration(item)

    then: "defaults to 0"
    extracted == 0
}
```

#### Test 4: Invalid generation values

```groovy
@Unroll
def "invalid generation #invalidGen is corrected to 0"() {
    given: "a book with invalid generation value"
    CompoundTag item = new CompoundTag()
    item.putString('id', 'minecraft:written_book')

    CompoundTag tag = new CompoundTag()
    tag.putByte('generation', (byte) invalidGen)
    item.put('tag', tag)

    when: "extracting generation"
    int extracted = Main.extractBookGeneration(item)

    then: "corrected to 0"
    extracted == 0

    and: "warning is logged"
    // Verify LOGGER.warn() called (mock logger or capture logs)

    where:
    invalidGen << [-1, 4, 5, 127, -128]
}
```

---

## Generation Label Mapping Tests

### Unit Test: GenerationLabelSpec.groovy

**Purpose:** Test getGenerationLabel() method

#### Test 1: Valid generation labels

```groovy
@Unroll
def "generation #generation maps to label '#expectedLabel'"() {
    expect:
    Main.getGenerationLabel(generation) == expectedLabel

    where:
    generation | expectedLabel
    0          | 'Original'
    1          | 'Copy of Original'
    2          | 'Copy of Copy'
    3          | 'Tattered'
}
```

#### Test 2: Invalid generation warning

```groovy
@Unroll
def "invalid generation #invalidGen returns 'Original' with warning"() {
    when:
    def label = Main.getGenerationLabel(invalidGen)

    then:
    label == 'Original'
    // Verify warning logged

    where:
    invalidGen << [-1, 4, 5, 99, -50]
}
```

---

## Stendhal File Parsing Tests

### Unit Test: StendhalParsingSpec.groovy

**Purpose:** Test parseStendhalFile() method

#### Test 1: Parse valid Stendhal file

```groovy
def "parse valid Stendhal file with all fields"() {
    given: "a Stendhal file with generation and pages"
    File tempFile = File.createTempFile('test', '.stendhal')
    tempFile.deleteOnExit()

    tempFile.text = """title: Test Book
author: Test Author
generation: 1
generation_label: Copy of Original
pages:
#- Page 1 content
#- Page 2 content
#- Page 3 content
"""

    when: "parsing the file"
    Map<String, Object> result = Main.parseStendhalFile(tempFile)

    then: "all fields extracted correctly"
    result.title == 'Test Book'
    result.generation == 1
    result.pages.size() == 3
    result.pages[0] == 'Page 1 content'
    result.pages[1] == 'Page 2 content'
    result.pages[2] == 'Page 3 content'
    result.contentHash == result.pages.hashCode()
}
```

#### Test 2: Parse file missing generation

```groovy
def "parse Stendhal file without generation defaults to 0"() {
    given: "a Stendhal file without generation field"
    File tempFile = File.createTempFile('test', '.stendhal')
    tempFile.deleteOnExit()

    tempFile.text = """title: Test Book
pages:
#- Page 1
"""

    when: "parsing the file"
    Map<String, Object> result = Main.parseStendhalFile(tempFile)

    then: "generation defaults to 0"
    result.generation == 0
}
```

#### Test 3: Parse file with malformed generation

```groovy
def "parse Stendhal file with non-numeric generation"() {
    given: "a Stendhal file with invalid generation"
    File tempFile = File.createTempFile('test', '.stendhal')
    tempFile.deleteOnExit()

    tempFile.text = """title: Test Book
generation: invalid
pages:
#- Page 1
"""

    when: "parsing the file"
    Map<String, Object> result = Main.parseStendhalFile(tempFile)

    then: "generation defaults to 0 and warning logged"
    result.generation == 0
    // Verify warning logged
}
```

#### Test 4: Content hash consistency

```groovy
def "content hash is consistent for identical pages"() {
    given: "two files with identical pages"
    File file1 = File.createTempFile('test1', '.stendhal')
    File file2 = File.createTempFile('test2', '.stendhal')
    [file1, file2].each { it.deleteOnExit() }

    String content = """title: Book
pages:
#- Page 1
#- Page 2
"""
    file1.text = content
    file2.text = content

    when: "parsing both files"
    def result1 = Main.parseStendhalFile(file1)
    def result2 = Main.parseStendhalFile(file2)

    then: "content hashes match"
    result1.contentHash == result2.contentHash
}
```

---

## Duplicates Folder Logic Tests

### Integration Test: DuplicatesFolderLogicSpec.groovy

**Purpose:** Test ensureOriginalsNotInDuplicates() post-processing

#### Test 1: Original in duplicates gets swapped with copy in books

```groovy
def "original book in .duplicates swaps with copy in books folder"() {
    given: "setup test folders"
    File tempDir = Files.createTempDirectory('test').toFile()
    tempDir.deleteOnExit()

    File booksDir = new File(tempDir, 'books')
    File duplicatesDir = new File(booksDir, '.duplicates')
    [booksDir, duplicatesDir].each { it.mkdirs() }

    and: "create copy (gen=1) in books/, original (gen=0) in duplicates/"
    File copyInBooks = new File(booksDir, 'test_copy.stendhal')
    File originalInDuplicates = new File(duplicatesDir, 'test_original.stendhal')

    copyInBooks.text = """title: Test Book
generation: 1
generation_label: Copy of Original
pages:
#- Page 1
#- Page 2
"""

    originalInDuplicates.text = """title: Test Book
generation: 0
generation_label: Original
pages:
#- Page 1
#- Page 2
"""

    and: "set Main static fields"
    Main.baseDirectory = tempDir.absolutePath
    Main.booksFolder = 'books'
    Main.duplicatesFolder = 'books/.duplicates'
    Main.outputFolder = '.'

    when: "running post-processing"
    Main.ensureOriginalsNotInDuplicates()

    then: "original is now in books/, copy is in duplicates/"
    def booksFiles = booksDir.listFiles().findAll { it.isFile() }
    def duplicatesFiles = duplicatesDir.listFiles().findAll { it.isFile() }

    booksFiles.size() == 1
    duplicatesFiles.size() == 1

    def booksGeneration = Main.parseStendhalFile(booksFiles[0]).generation
    def duplicatesGeneration = Main.parseStendhalFile(duplicatesFiles[0]).generation

    booksGeneration == 0  // Original in books/
    duplicatesGeneration == 1  // Copy in duplicates/
}
```

#### Test 2: Original in duplicates with no books to swap gets moved

```groovy
def "original in .duplicates with no other books gets moved to books"() {
    given: "setup test folders"
    File tempDir = Files.createTempDirectory('test').toFile()
    tempDir.deleteOnExit()

    File booksDir = new File(tempDir, 'books')
    File duplicatesDir = new File(booksDir, '.duplicates')
    [booksDir, duplicatesDir].each { it.mkdirs() }

    and: "create only original in duplicates/"
    File originalInDuplicates = new File(duplicatesDir, 'test.stendhal')
    originalInDuplicates.text = """title: Test Book
generation: 0
pages:
#- Page 1
"""

    and: "set Main static fields"
    Main.baseDirectory = tempDir.absolutePath
    Main.booksFolder = 'books'
    Main.duplicatesFolder = 'books/.duplicates'
    Main.outputFolder = '.'

    when: "running post-processing"
    Main.ensureOriginalsNotInDuplicates()

    then: "original moved to books/, duplicates empty"
    booksDir.listFiles().findAll { it.isFile() }.size() == 1
    duplicatesDir.listFiles().findAll { it.isFile() }.size() == 0
}
```

#### Test 3: No originals in duplicates - no changes

```groovy
def "no originals in .duplicates causes no file movements"() {
    given: "setup test folders with only copies in duplicates"
    File tempDir = Files.createTempDirectory('test').toFile()
    tempDir.deleteOnExit()

    File booksDir = new File(tempDir, 'books')
    File duplicatesDir = new File(booksDir, '.duplicates')
    [booksDir, duplicatesDir].each { it.mkdirs() }

    and: "create original in books/, copy in duplicates/"
    File originalInBooks = new File(booksDir, 'original.stendhal')
    File copyInDuplicates = new File(duplicatesDir, 'copy.stendhal')

    originalInBooks.text = """title: Book
generation: 0
pages:
#- Page
"""

    copyInDuplicates.text = """title: Book
generation: 1
pages:
#- Page
"""

    and: "set Main static fields"
    Main.baseDirectory = tempDir.absolutePath
    Main.booksFolder = 'books'
    Main.duplicatesFolder = 'books/.duplicates'
    Main.outputFolder = '.'

    when: "running post-processing"
    Main.ensureOriginalsNotInDuplicates()

    then: "no changes - files remain in place"
    def booksGeneration = Main.parseStendhalFile(originalInBooks).generation
    def duplicatesGeneration = Main.parseStendhalFile(copyInDuplicates).generation

    booksGeneration == 0
    duplicatesGeneration == 1
}
```

#### Test 4: Multiple books with same content hash

```groovy
def "multiple books with same content handled correctly"() {
    given: "multiple books with identical content but different generations"
    File tempDir = Files.createTempDirectory('test').toFile()
    tempDir.deleteOnExit()

    File booksDir = new File(tempDir, 'books')
    File duplicatesDir = new File(booksDir, '.duplicates')
    [booksDir, duplicatesDir].each { it.mkdirs() }

    and: "create: copy1 in books/, original in duplicates/, copy2 in duplicates/"
    File copy1InBooks = new File(booksDir, 'copy1.stendhal')
    File originalInDuplicates = new File(duplicatesDir, 'original.stendhal')
    File copy2InDuplicates = new File(duplicatesDir, 'copy2.stendhal')

    String content = """pages:
#- Page 1
"""

    copy1InBooks.text = "title: Book\ngeneration: 1\n" + content
    originalInDuplicates.text = "title: Book\ngeneration: 0\n" + content
    copy2InDuplicates.text = "title: Book\ngeneration: 2\n" + content

    and: "set Main static fields"
    Main.baseDirectory = tempDir.absolutePath
    Main.booksFolder = 'books'
    Main.duplicatesFolder = 'books/.duplicates'
    Main.outputFolder = '.'

    when: "running post-processing"
    Main.ensureOriginalsNotInDuplicates()

    then: "original in books/, copies in duplicates/"
    def booksFiles = booksDir.listFiles().findAll { it.isFile() }
    def duplicatesFiles = duplicatesDir.listFiles().findAll { it.isFile() }

    booksFiles.size() == 1
    duplicatesFiles.size() == 2

    Main.parseStendhalFile(booksFiles[0]).generation == 0
}
```

---

## Output Format Tests

### Integration Test: OutputFormatsSpec.groovy

**Purpose:** Verify generation is included in all output formats

#### Test 1: Stendhal files include generation

```groovy
def "Stendhal files include generation and generation_label fields"() {
    when: "book with generation 1 is written"
    // Create book, call Main.readWrittenBook()

    then: "Stendhal file contains generation fields"
    File stendhalFile = ... // locate written file
    def lines = stendhalFile.readLines()

    lines.any { it.startsWith('generation: 1') }
    lines.any { it.startsWith('generation_label: Copy of Original') }
}
```

#### Test 2: CSV includes generation columns

```groovy
def "CSV export includes Generation and GenerationLabel columns"() {
    when: "extraction runs and CSV is written"
    Main.writeBooksCSV()

    then: "CSV header includes generation columns"
    File csvFile = new File(Main.baseDirectory, "${Main.outputFolder}/all_books.csv")
    def header = csvFile.readLines()[0]

    header.contains('Generation')
    header.contains('GenerationLabel')
}
```

#### Test 3: Mcfunction commands include generation NBT

```groovy
@Unroll
def "mcfunction #version file includes generation NBT"() {
    when: "book command is generated"
    String command = Main.generateBookCommand(
        "Test Book",
        "Author",
        createTestPages(),
        1,  // generation
        version
    )

    then: "command includes generation field"
    command.contains('generation')
    command.contains(':1') || command.contains('=1')  // NBT syntax varies

    where:
    version << ['1_13', '1_14', '1_20_5', '1_21']
}
```

---

## Mcfunction Command Tests

### Unit Test: McfunctionGenerationSpec.groovy

**Purpose:** Test generation NBT in mcfunction commands

#### Test 1: Generation in 1.13 format

```groovy
def "1.13 mcfunction includes generation in NBT tag"() {
    when:
    String command = Main.generateBookCommand(
        "Book",
        "Author",
        createPages(["Page 1"]),
        2,  // generation
        '1_13'
    )

    then:
    command.startsWith('give @p written_book{')
    command.contains('generation:2')
}
```

#### Test 2: Generation in 1.20.5+ format

```groovy
def "1.20.5 mcfunction includes generation in components"() {
    when:
    String command = Main.generateBookCommand(
        "Book",
        "Author",
        createPages(["Page 1"]),
        1,
        '1_20_5'
    )

    then:
    command.contains('[minecraft:written_book_content={')
    command.contains('generation:1')
}
```

---

## Multi-Version Compatibility Tests

### Integration Test: MultiVersionGenerationSpec.groovy

**Purpose:** Test generation extraction across Minecraft versions

#### Test 1: Extract from multiple version formats

```groovy
@Unroll
def "extract generation from #format format book"() {
    given: "a book in #format format with generation 1"
    CompoundTag book = createBookInFormat(format, 1)

    when: "extracting generation"
    int generation = Main.extractBookGeneration(book)

    then: "generation is correctly extracted"
    generation == 1

    where:
    format << ['pre-1.20.5', '1.20.5+']
}

static CompoundTag createBookInFormat(String format, int generation) {
    CompoundTag item = new CompoundTag()
    item.putString('id', 'minecraft:written_book')

    if (format == 'pre-1.20.5') {
        CompoundTag tag = new CompoundTag()
        tag.putByte('generation', (byte) generation)
        item.put('tag', tag)
    } else {
        CompoundTag components = new CompoundTag()
        CompoundTag bookContent = new CompoundTag()
        bookContent.putInt('generation', generation)
        components.put('minecraft:written_book_content', bookContent)
        item.put('components', components)
    }

    return item
}
```

---

## Edge Cases and Error Handling Tests

### Unit Test: GenerationEdgeCasesSpec.groovy

**Purpose:** Test edge cases and error conditions

#### Test 1: Null CompoundTag

```groovy
def "extractBookGeneration handles null CompoundTag gracefully"() {
    when:
    int generation = Main.extractBookGeneration(null)

    then:
    generation == 0  // Default
    // Or throws NullPointerException - define expected behavior
}
```

#### Test 2: Empty CompoundTag

```groovy
def "extractBookGeneration handles empty CompoundTag"() {
    given:
    CompoundTag empty = new CompoundTag()

    when:
    int generation = Main.extractBookGeneration(empty)

    then:
    generation == 0
}
```

#### Test 3: Corrupted Stendhal file

```groovy
def "parseStendhalFile handles corrupted file gracefully"() {
    given: "a corrupted Stendhal file"
    File corrupted = File.createTempFile('corrupted', '.stendhal')
    corrupted.deleteOnExit()
    corrupted.bytes = [0xFF, 0xFE, 0xFD] as byte[]  // Invalid UTF-8

    when:
    Map<String, Object> result = Main.parseStendhalFile(corrupted)

    then:
    notThrown(Exception)  // Should handle gracefully
    // Or define specific exception handling
}
```

---

## Test Data Requirements

### Minimal Test World

**Required Structure:**
```
test-world/
├── level.dat
├── region/
│   └── r.0.0.mca  (with books of different generations)
├── entities/
│   └── r.0.0.mca
└── playerdata/
    └── <uuid>.dat
```

**Test Books to Include:**

| Location | Title | Generation | Purpose |
|----------|-------|------------|---------|
| Chest at 100,64,200 | Original Book | 0 | Test original extraction |
| Chest at 101,64,200 | Copy Book | 1 | Test copy extraction |
| Barrel at 102,64,200 | Copy of Copy | 2 | Test tier 2 extraction |
| Shulker at 103,64,200 | Tattered Book | 3 | Test tattered extraction |
| Chest at 100,65,200 | Duplicate Original | 0 | Test duplicates with original first |
| Chest at 101,65,200 | Duplicate Copy | 1 | Test duplicates with copy first |

### Test Data Generation Script

```groovy
// Utility to generate test worlds
static void generateTestWorld(File worldDir) {
    worldDir.mkdirs()

    // Create level.dat
    def level = new CompoundTag()
    def data = new CompoundTag()
    data.putInt('DataVersion', 3465)  // 1.20.5
    data.putInt('version', 19133)
    level.put('Data', data)
    NBTUtil.write(new NamedTag('', level), new File(worldDir, 'level.dat'))

    // Create region with test books
    // ... implementation
}
```

---

## Summary

### Total Test Coverage

**Unit Tests (Estimated):**
- Generation extraction: 8 tests
- Generation label mapping: 3 tests
- Stendhal parsing: 6 tests
- Edge cases: 5 tests

**Integration Tests (Estimated):**
- Duplicates folder logic: 5 tests
- Output formats: 4 tests
- Mcfunction commands: 6 tests
- Multi-version compatibility: 3 tests

**Total: ~40 test cases**

### Implementation Priority

1. **High Priority:**
   - Generation extraction tests
   - Duplicates folder swap logic tests
   - Output format validation

2. **Medium Priority:**
   - Stendhal parsing tests
   - Mcfunction command tests

3. **Low Priority:**
   - Edge case tests (implement after core functionality verified)

### Test Execution Time Estimates

- Unit tests: < 1 second total
- Integration tests: 5-10 seconds total
- Full test suite: < 15 seconds

---

## References

- **Testing Strategies:** `.kilocode/rules/memory-bank/testing-strategies.md`
- **Generation Tracking Spec:** `.kilocode/rules/memory-bank/generation-tracking.md`
- **Spock Documentation:** https://spockframework.org/
- **Existing Tests:** `src/test/groovy/ReadBooksIntegrationSpec.groovy`

**Last Updated:** 2025-11-18
**Status:** Specifications complete, implementation pending
**Framework:** Spock 2.3-groovy-4.0 + JUnit 5 Platform

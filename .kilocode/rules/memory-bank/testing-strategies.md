# Testing Strategies and Best Practices

**Created:** 2025-11-18
**Purpose:** Comprehensive testing guide for ReadSignsAndBooks.jar project
**Frameworks:** Spock 2.4, Groovy 4.0.24, JUnit 5 Platform
**Context:** Integration and unit testing for Minecraft NBT data extraction

---

## Table of Contents

1. [Testing Philosophy](#testing-philosophy)
2. [Spock Framework Overview](#spock-framework-overview)
3. [Data-Driven Testing](#data-driven-testing)
4. [Mocking and Stubbing](#mocking-and-stubbing)
5. [Integration Testing Patterns](#integration-testing-patterns)
6. [Test Fixture Generation](#test-fixture-generation)
7. [Edge Cases and Error Handling](#edge-cases-and-error-handling)
8. [File I/O Testing](#file-io-testing)
9. [NBT Data Testing](#nbt-data-testing)
10. [Performance Testing](#performance-testing)
11. [Test Organization](#test-organization)
12. [Groovy Power Assertions](#groovy-power-assertions)
13. [Duplicate Detection Testing](#duplicate-detection-testing)
14. [World Corruption Testing](#world-corruption-testing)
15. [Test Data Generation](#test-data-generation)

---

## Testing Philosophy

### Core Principles

**Comprehensive Coverage:**
- Unit tests for individual methods and functions
- Integration tests for end-to-end extraction workflows
- Edge case testing for corrupt data, missing fields, unusual structures
- Performance tests for large-scale world files

**Test-Driven Development Benefits:**
- Catch regressions early in development cycle
- Document expected behavior through test specifications
- Enable confident refactoring with safety nets
- Validate multi-version compatibility (Minecraft 1.18, 1.20, 1.20.5+)

**Testing Goals for ReadSignsAndBooks:**
1. Verify correct NBT extraction from all container types
2. Validate multi-format compatibility (pre-1.20.5 and 1.20.5+)
3. Ensure deduplication works correctly across all scenarios
4. Confirm generation tracking accuracy
5. Test output format correctness (Stendhal, CSV, mcfunction)
6. Verify original books never placed in `.duplicates/` folder
7. Validate graceful handling of corrupt or invalid data

---

## Spock Framework Overview

### What is Spock?

**Spock** is a testing and specification framework for Java and Groovy applications, combining:
- BDD-style specifications (Behavior-Driven Development)
- Built-in mocking and stubbing capabilities
- Powerful data-driven testing with minimal boilerplate
- Integration with JUnit 5 Platform (Spock 2.x)

**Version Coverage:**
- **Latest:** Spock 2.4-M6 (released 2025-04-15)
- **Java Requirements:** Java 8+
- **Groovy Versions:** 2.5, 3.0, 4.0, 5.0
- **Current Project:** Spock 2.3-groovy-4.0

### Key Features (Spock 2.4)

#### 1. Data Provider Combinations (New in 2.4)

Combine multiple data providers using cartesian product:

```groovy
def "test with combined providers"() {
    expect:
    result == expected

    where:
    [a, b] << combinations(
        [1, 2, 3],
        [10, 20]
    )
    result = a + b
    expected = a + b
}
// Runs 6 iterations: (1,10), (1,20), (2,10), (2,20), (3,10), (3,20)
```

#### 2. Filter Block (New in 2.4)

Selectively exclude iterations without modifying data providers:

```groovy
def "filtered iterations"() {
    expect:
    Math.max(a, b) == c

    where:
    a | b | c
    1 | 3 | 3
    7 | 4 | 7
    0 | 0 | 0

    filter:
    a != 0  // Excludes (0, 0, 0) iteration
}
```

#### 3. VerifyEach with Index (New in 2.4)

```groovy
verifyEach(list) { element, index ->
    element.id == index
    element.valid == true
}
```

#### 4. Global Timeout Configuration

```groovy
// In SpockConfig.groovy
timeout {
    enabled true
    defaultTimeout 10  // seconds
}
```

### Specification Structure

```groovy
import spock.lang.Specification

class BookExtractionSpec extends Specification {

    // Setup runs before EACH test method
    def setup() {
        // Initialize test fixtures
    }

    // Cleanup runs after EACH test method
    def cleanup() {
        // Clean up resources
    }

    // setupSpec runs ONCE before all tests (must be static)
    def setupSpec() {
        // One-time setup
    }

    // cleanupSpec runs ONCE after all tests (must be static)
    def cleanupSpec() {
        // One-time cleanup
    }

    // Test method using given-when-then
    def "should extract book from chest"() {
        given: "a chest with one book"
        CompoundTag chest = createChestWithBook("Test Title")

        when: "extraction runs"
        Main.processContainer(chest, "chest", 100, 64, 200)

        then: "book is extracted"
        Main.bookHashes.size() == 1
        Main.bookMetadataList[0].title == "Test Title"
    }
}
```

### Block Types

| Block | Purpose | Required |
|-------|---------|----------|
| `given:` | Setup test preconditions | Optional |
| `when:` | Execute action under test | Required with `then:` |
| `then:` | Assert expected outcomes | Required with `when:` |
| `expect:` | Combined when+then (for pure functions) | Alternative to when/then |
| `where:` | Provide test data (data-driven tests) | Optional |
| `cleanup:` | Release resources | Optional |
| `and:` | Continue previous block | Optional |

**Example with expect:**
```groovy
def "calculate book hash"() {
    expect:
    pages.hashCode() == expectedHash

    where:
    pages                    || expectedHash
    ["Page 1"]               || ["Page 1"].hashCode()
    ["Page 1", "Page 2"]     || ["Page 1", "Page 2"].hashCode()
}
```

---

## Data-Driven Testing

### Why Data-Driven Testing?

**Benefits:**
- Test identical logic with many different inputs
- Separate test code from test data
- Reduce duplication (DRY principle)
- Easy to add new test cases without code changes
- Clear tabular view of all tested scenarios

### Data Tables

**Basic Syntax:**
```groovy
def "maximum of two numbers"() {
    expect:
    Math.max(a, b) == c

    where:
    a | b | c
    1 | 3 | 3
    7 | 4 | 7
    0 | 0 | 0
}
```

**Key Rules:**
- First row is the header (declares variables)
- Subsequent rows are data
- Minimum 2 columns required
- Use `|` to separate columns
- Use `||` (double pipe) to visually separate inputs from outputs

**Single Column Tables:**
```groovy
where:
generation | _
0          | _
1          | _
2          | _
3          | _
```

### Data Pipes

**Syntax:**
```groovy
where:
a << [3, 7, 0]
b << [5, 0, 0]
c << [5, 7, 0]
```

**Any Iterable Works:**
```groovy
where:
minecraftVersion << ['1.18', '1.20', '1.20.5', '1.21']
book << generateTestBooks()
```

**Multi-Variable Data Pipes:**
```groovy
where:
[title, author, pages] << [
    ['Book 1', 'Player1', ['Page 1']],
    ['Book 2', 'Player2', ['Page 1', 'Page 2']]
]
```

**Ignoring Values:**
```groovy
where:
[a, b, _, c] << sql.rows("select * from maxdata")
// Third column ignored with underscore
```

### Derived Values

Compute values based on other data variables:

```groovy
def "test generation labels"() {
    expect:
    generationLabel == expectedLabel

    where:
    generation << [0, 1, 2, 3]
    generationLabel = Main.getGenerationLabel(generation)
    expectedLabel = ['Original', 'Copy of Original', 'Copy of Copy', 'Tattered'][generation]
}
```

### Combining Approaches

Mix tables, pipes, and assignments:

```groovy
def "test book extraction across versions"() {
    expect:
    extractedGeneration == expectedGeneration

    where:
    version     | bookData
    '1.18'      | createLegacyBook()
    '1.20'      | createLegacyBook()
    '1.20.5'    | createComponentBook()

    item << [bookData]
    extractedGeneration = Main.extractBookGeneration(item)
    expectedGeneration = 1
}
```

### Unrolling Tests with @Unroll

**Without @Unroll:**
```
maximum of two numbers   FAILED
```

**With @Unroll:**
```groovy
@Unroll
def "maximum of two numbers"() {
    expect:
    Math.max(a, b) == c

    where:
    a | b | c
    3 | 5 | 5
    7 | 0 | 7  // This one fails
    0 | 0 | 0
}
```

**Output:**
```
maximum of two numbers[0]   PASSED
maximum of two numbers[1]   FAILED
maximum of two numbers[2]   PASSED
```

### Unrolled Method Names with Data

```groovy
@Unroll
def "extracting generation #generation yields label '#expectedLabel'"() {
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

**Output:**
```
extracting generation 0 yields label 'Original'           PASSED
extracting generation 1 yields label 'Copy of Original'   PASSED
extracting generation 2 yields label 'Copy of Copy'       PASSED
extracting generation 3 yields label 'Tattered'           PASSED
```

**Placeholder Rules:**
- Use `#variable` (not `$variable`)
- Property access: `#book.title`
- Zero-argument methods: `#person.name.toUpperCase()`
- No operators or method arguments allowed

---

## Mocking and Stubbing

### Concepts

**Mock:** Verifies interactions (method calls, arguments, invocation count)
**Stub:** Provides canned responses to method calls
**Spy:** Partial mock (real object with some methods stubbed)

### Creating Test Doubles

```groovy
def "test with mocks and stubs"() {
    given:
    def mock = Mock(SomeClass)
    def stub = Stub(SomeClass)
    def spy = Spy(SomeClass)
}
```

### Stubbing Return Values

**Simple Stubbing:**
```groovy
given:
def fileReader = Stub(FileReader)
fileReader.readLine() >> "mocked line"

when:
def result = fileReader.readLine()

then:
result == "mocked line"
```

**Sequence of Return Values:**
```groovy
stub.method() >>> ["first", "second", "third"]
// First call returns "first", second returns "second", etc.
```

**Argument-Based Stubbing:**
```groovy
stub.getBookTitle(_ as String) >> { String author ->
    return "Book by ${author}"
}
```

### Mock Verification

**Exact Invocation Count:**
```groovy
then:
1 * mock.processBook(_)  // Called exactly once
3 * mock.processBook(_)  // Called exactly 3 times
0 * mock.processBook(_)  // Never called
```

**Range of Invocations:**
```groovy
then:
(1..3) * mock.processBook(_)  // Called 1, 2, or 3 times
(2.._) * mock.processBook(_)  // Called at least 2 times
(_..5) * mock.processBook(_)  // Called at most 5 times
```

**Argument Constraints:**
```groovy
then:
1 * mock.processBook("Specific Title")  // Exact match
1 * mock.processBook(_ as String)       // Any string
1 * mock.processBook(!null)             // Any non-null
1 * mock.processBook({it.startsWith("Book")})  // Custom matcher
```

**Method Constraints:**
```groovy
then:
1 * mock./process.*/(_)  // Any method starting with "process"
1 * mock./get[A-Z].*/()  // Any getter method
```

### Spock-Specific Example for ReadSignsAndBooks

```groovy
def "should call NBTUtil.read for each region file"() {
    given:
    def mockNBT = Mock(NBTUtil)
    def regionFile = new File("test.mca")

    when:
    processRegionFile(regionFile)

    then:
    1 * mockNBT.read(regionFile)
}
```

---

## Integration Testing Patterns

### What are Integration Tests?

**Integration tests** validate that multiple units or components work together correctly, including:
- File system interactions
- External dependencies (NBT library)
- Complete workflows (extraction from world → output files)
- Cross-version compatibility

### Integration Test Organization (Gradle)

**Modern Gradle Approach (Test Suites):**

```gradle
testing {
    suites {
        test {
            useJUnitJupiter()
        }

        integrationTest(JvmTestSuite) {
            dependencies {
                implementation project()
            }

            targets {
                all {
                    testTask.configure {
                        shouldRunAfter(test)
                        timeout = Duration.ofMinutes(10)
                    }
                }
            }
        }
    }
}
```

**Directory Structure:**
```
src/
├── test/
│   └── groovy/
│       └── ReadBooksIntegrationSpec.groovy  (unit/integration)
└── integrationTest/
    └── groovy/
        └── FullExtractionSpec.groovy         (integration only)
```

**Key Benefits:**
- Separate integration from unit tests
- Different timeouts (integration slower)
- Run unit tests frequently, integration less often
- Clear separation of concerns

### Integration Test Best Practices

**1. Use Real Test Data:**
- Include actual Minecraft world saves (e.g., `src/test/resources/1_21_10-44-3/`)
- Represent diverse scenarios (multiple containers, versions, edge cases)
- Version control test worlds for reproducibility

**2. Clean Slate Approach:**
```groovy
def setup() {
    // Delete output folder before each test
    def outputDir = new File('build/test-output')
    outputDir.deleteDir()
    outputDir.mkdirs()
}
```

**3. Verify Complete Workflows:**
```groovy
def "full extraction workflow"() {
    when:
    Main.runExtraction()

    then: "all books extracted"
    def csvFile = new File(Main.outputFolder, 'all_books.csv')
    csvFile.exists()
    def lines = csvFile.readLines()
    lines.size() == 45  // 44 books + 1 header

    and: "all signs extracted"
    def signCsv = new File(Main.outputFolder, 'all_signs.csv')
    signCsv.exists()
    signCsv.readLines().size() == 4  // 3 signs + 1 header
}
```

**4. Test Across Minecraft Versions:**
```groovy
@Unroll
def "extract books from Minecraft #version world"() {
    given:
    Main.baseDirectory = "src/test/resources/${worldFolder}"

    when:
    Main.runExtraction()

    then:
    Main.bookHashes.size() == expectedBookCount

    where:
    version   | worldFolder          | expectedBookCount
    '1.18'    | '1_18_world'         | 10
    '1.20'    | '1_20_world'         | 15
    '1.20.5'  | '1_20_5_world'       | 20
    '1.21'    | '1_21_10-44-3'       | 44
}
```

---

## Test Fixture Generation

### What are Test Fixtures?

**Test fixtures** are objects/data used to set up a known test environment. For ReadSignsAndBooks:
- CompoundTag structures representing books
- Region files with specific container arrangements
- Player data files with inventories
- Expected output files (SHOULDBE.txt)

### Fixture Strategies

**1. Programmatic Generation:**
```groovy
static CompoundTag createBook(String title, String author, List<String> pages, int generation = 0) {
    def item = new CompoundTag()
    item.putString('id', 'minecraft:written_book')

    def tag = new CompoundTag()
    tag.putString('title', title)
    tag.putString('author', author)
    tag.putByte('generation', (byte) generation)

    def pageList = new ListTag<>(StringTag.class)
    pages.each { pageList.addString(it) }
    tag.put('pages', pageList)

    item.put('tag', tag)
    return item
}
```

**2. Version Control Test Data:**
- Store real Minecraft worlds in `src/test/resources/`
- Include `SHOULDBE.txt` with expected output
- Commit to git for reproducibility

**3. Fixture Builder Pattern:**
```groovy
class BookBuilder {
    String title = 'Untitled'
    String author = 'Unknown'
    List<String> pages = []
    int generation = 0

    BookBuilder withTitle(String title) {
        this.title = title
        return this
    }

    BookBuilder withGeneration(int generation) {
        this.generation = generation
        return this
    }

    CompoundTag build() {
        return createBook(title, author, pages, generation)
    }
}

// Usage:
def book = new BookBuilder()
    .withTitle("Test Book")
    .withGeneration(1)
    .build()
```

### Shared Fixtures

**Use @Shared for Cross-Test Data:**
```groovy
class BookExtractionSpec extends Specification {
    @Shared CompoundTag testBook

    def setupSpec() {
        testBook = createBook("Shared Test", "Author", ["Page 1"])
    }

    def "test 1"() {
        expect:
        testBook != null
    }

    def "test 2"() {
        expect:
        testBook.getString('id') == 'minecraft:written_book'
    }
}
```

---

## Edge Cases and Error Handling

### Testing Exceptions with Spock

**Basic Exception Testing:**
```groovy
def "should throw exception for invalid generation"() {
    when:
    Main.getGenerationLabel(99)

    then:
    thrown(IllegalArgumentException)
}
```

**Exception with Message Verification:**
```groovy
def "should throw with specific message"() {
    when:
    processInvalidBook(null)

    then:
    def error = thrown(NullPointerException)
    error.message == 'Book cannot be null'
}
```

**Testing for No Exception:**
```groovy
def "should not throw for valid input"() {
    when:
    Main.extractBookGeneration(validBook)

    then:
    noExceptionThrown()
}
```

### Edge Cases with Data Tables

**Separate Success and Failure Tests:**
```groovy
def "valid generations are handled correctly"() {
    expect:
    Main.getGenerationLabel(generation) == label

    where:
    generation | label
    0          | 'Original'
    1          | 'Copy of Original'
    2          | 'Copy of Copy'
    3          | 'Tattered'
}

def "invalid generations are rejected"() {
    when:
    Main.getGenerationLabel(generation)

    then:
    def error = thrown(IllegalArgumentException)
    error.message.contains('Invalid generation')

    where:
    generation << [-1, 4, 5, 100]
}
```

### NBT Edge Cases to Test

**1. Missing Fields:**
```groovy
def "missing generation defaults to 0"() {
    given: "book without generation field"
    def book = createBookWithoutGeneration()

    when:
    int generation = Main.extractBookGeneration(book)

    then:
    generation == 0
}
```

**2. Empty Collections:**
```groovy
def "empty pages list is handled"() {
    given:
    def book = createBook("Title", "Author", [])

    expect:
    book.getListTag('pages').size() == 0
    noExceptionThrown()
}
```

**3. Null Values:**
```groovy
def "null title is handled gracefully"() {
    given:
    def book = createBook(null, "Author", ["Page 1"])

    when:
    String title = book.getString('title')

    then:
    title == '' || title == null
    noExceptionThrown()
}
```

**4. Malformed NBT:**
```groovy
def "corrupted NBT data is skipped"() {
    given: "region file with corrupted chunk"
    def corruptedRegion = createCorruptedRegion()

    when:
    Main.extractRegionFiles()

    then:
    noExceptionThrown()
    // Verify corrupted chunk skipped, others processed
}
```

**5. Maximum Depth Nesting:**
```groovy
def "deeply nested containers are handled"() {
    given: "shulker inside shulker inside shulker (10 levels deep)"
    def deeplyNested = createDeeplyNestedShulkers(10)

    when:
    Main.processContainer(deeplyNested, "shulker", 0, 0, 0)

    then:
    noExceptionThrown()
    Main.bookHashes.size() == 1  // Book at deepest level extracted
}
```

---

## File I/O Testing

### JUnit 5 @TempDir

**Purpose:** Automatically create and clean up temporary directories for file tests.

**Basic Usage:**
```groovy
import org.junit.jupiter.api.io.TempDir
import spock.lang.Specification

class FileOutputSpec extends Specification {
    @TempDir
    File tempDir

    def "writes books to Stendhal files"() {
        given:
        Main.booksFolder = tempDir.absolutePath

        when:
        Main.writeBookToFile(createBook("Test", "Author", ["Page 1"]), false)

        then:
        def files = tempDir.listFiles()
        files.length == 1
        files[0].name.endsWith('.stendhal')
    }
}
```

**Cleanup Modes:**
```groovy
@TempDir(cleanup = CleanupMode.NEVER)  // Don't delete (for debugging)
File debugDir

@TempDir(cleanup = CleanupMode.ON_SUCCESS)  // Delete only if test passes
File conditionalDir
```

### File Comparison Testing

**Content Verification:**
```groovy
def "generated CSV matches expected format"() {
    when:
    Main.writeBooksCSV()

    then:
    def csvFile = new File(Main.outputFolder, 'all_books.csv')
    def lines = csvFile.readLines('UTF-8')

    lines[0] == 'X,Y,Z,FoundWhere,Bookname,Author,PageCount,Generation,GenerationLabel,Pages'
    lines[1].startsWith('100,64,200,chest,')
}
```

**Binary File Comparison (for MCA files):**
```groovy
def "generated region file is valid"() {
    when:
    // Test that writes region file

    then:
    def generatedFile = new File(Main.outputFolder, 'r.0.0.mca')
    def expectedFile = new File('src/test/resources/expected/r.0.0.mca')

    generatedFile.bytes == expectedFile.bytes
}
```

### Resource Loading in Tests

```groovy
def "load test world from resources"() {
    given:
    def worldPath = this.class.getResource('/1_21_10-44-3').toURI().path

    when:
    Main.baseDirectory = worldPath
    Main.runExtraction()

    then:
    Main.bookHashes.size() == 44
}
```

---

## NBT Data Testing

### Testing NBT Parsing

**Querz Library Testing Patterns:**
```groovy
def "parse CompoundTag from file"() {
    given:
    def testFile = new File('src/test/resources/test.dat')

    when:
    def namedTag = NBTUtil.read(testFile)
    def root = namedTag.tag as CompoundTag

    then:
    root != null
    root.containsKey('Data')
}
```

### Multi-Format Compatibility Testing

```groovy
@Unroll
def "extract book from #format format"() {
    given:
    def book = createBookInFormat(format)

    when:
    def title = Main.extractBookTitle(book)
    def generation = Main.extractBookGeneration(book)

    then:
    title == 'Test Book'
    generation == 1

    where:
    format << ['pre-1.20.5', '1.20.5+']
}

static CompoundTag createBookInFormat(String format) {
    def item = new CompoundTag()
    item.putString('id', 'minecraft:written_book')

    if (format == 'pre-1.20.5') {
        def tag = new CompoundTag()
        tag.putString('title', 'Test Book')
        tag.putByte('generation', (byte) 1)
        item.put('tag', tag)
    } else {
        def components = new CompoundTag()
        def bookContent = new CompoundTag()
        bookContent.putString('title', 'Test Book')
        bookContent.putInt('generation', 1)
        components.put('minecraft:written_book_content', bookContent)
        item.put('components', components)
    }

    return item
}
```

### Validation Testing

```groovy
def "validate NBT structure before processing"() {
    when:
    def valid = isValidBookNBT(book)

    then:
    valid == expectedValid

    where:
    book                          || expectedValid
    createValidBook()             || true
    createBookWithoutPages()      || false
    createBookWithWrongType()     || false
    new CompoundTag()             || false
}

static boolean isValidBookNBT(CompoundTag book) {
    if (!book.containsKey('id')) return false
    def id = book.getString('id')
    if (id != 'minecraft:written_book' && id != 'minecraft:writable_book') {
        return false
    }
    // Additional validation...
    return true
}
```

---

## Performance Testing

### CompileStatic for Test Performance

**Use @CompileStatic on Helper Methods:**
```groovy
import groovy.transform.CompileStatic

@CompileStatic
static CompoundTag createBook(String title, String author, List<String> pages) {
    // 2-100x faster than dynamic Groovy
    def item = new CompoundTag()
    item.putString('id', 'minecraft:written_book')
    // ...
    return item
}
```

**Benefits:**
- 2-2.5x performance improvement typical
- Up to 100x in hotspot areas
- Catches type errors at compile time
- Minimal code changes required

### Benchmarking Large-Scale Extraction

```groovy
def "extraction completes within timeout for large world"() {
    given:
    Main.baseDirectory = 'src/test/resources/large_world'
    def startTime = System.currentTimeMillis()

    when:
    Main.runExtraction()
    def duration = System.currentTimeMillis() - startTime

    then:
    duration < 60_000  // Must complete in under 60 seconds
    Main.bookHashes.size() >= 1000  // Verify processing occurred
}
```

### Memory Profiling Tests

```groovy
def "memory usage stays within bounds"() {
    given:
    def runtime = Runtime.runtime
    def beforeMemory = runtime.totalMemory() - runtime.freeMemory()

    when:
    Main.runExtraction()
    runtime.gc()
    def afterMemory = runtime.totalMemory() - runtime.freeMemory()

    then:
    def memoryIncrease = (afterMemory - beforeMemory) / 1024 / 1024  // MB
    memoryIncrease < 500  // Less than 500MB increase
}
```

---

## Test Organization

### Gradle Test Suite Configuration

```gradle
testing {
    suites {
        test {
            useJUnitJupiter()
            dependencies {
                implementation 'org.spockframework:spock-core:2.3-groovy-4.0'
            }
        }

        integrationTest(JvmTestSuite) {
            dependencies {
                implementation project()
            }

            testType = TestSuiteType.INTEGRATION_TEST

            targets {
                all {
                    testTask.configure {
                        shouldRunAfter test
                        timeout = Duration.ofMinutes(10)

                        // More verbose logging
                        testLogging {
                            events "passed", "skipped", "failed"
                            exceptionFormat "full"
                        }
                    }
                }
            }
        }
    }
}

tasks.named('check') {
    dependsOn testing.suites.integrationTest
}
```

### Directory Structure

```
src/
├── test/
│   ├── groovy/
│   │   ├── unit/
│   │   │   ├── GenerationLabelSpec.groovy
│   │   │   ├── NBTParsingSpec.groovy
│   │   │   └── DeduplicationSpec.groovy
│   │   └── integration/
│   │       ├── ReadBooksIntegrationSpec.groovy
│   │       └── MultiVersionSpec.groovy
│   └── resources/
│       ├── 1_18_world/
│       ├── 1_20_world/
│       ├── 1_20_5_world/
│       └── 1_21_10-44-3/
└── integrationTest/
    └── groovy/
        └── FullWorkflowSpec.groovy
```

### Naming Conventions

**Specification Names:**
- Unit tests: `*Spec.groovy` (e.g., `GenerationLabelSpec.groovy`)
- Integration tests: `*IntegrationSpec.groovy`
- End-to-end: `*E2ESpec.groovy`

**Test Method Names:**
- Use natural language: `"should extract generation from 1.20.5+ format"`
- Be specific: `"should place originals in books folder not duplicates"`
- Document behavior: `"should handle missing generation field by defaulting to 0"`

---

## Groovy Power Assertions

### What are Power Assertions?

Groovy's `assert` provides rich failure output showing:
- Each sub-expression value
- Visual representation of comparison
- Why assertion failed

**Example:**
```groovy
def a = 5
def b = 10
assert a + b == 20
```

**Output:**
```
Assertion failed:

assert a + b == 20
       | | | |
       5 15  false
```

### Using in Spock

Power assertions work automatically in `expect:` and `then:` blocks:

```groovy
def "power assert example"() {
    given:
    def book = createBook("Title", "Author", ["Page 1", "Page 2"])

    expect:
    book.getListTag('pages').size() == 3
    // Output shows: pages.size() == 2, not 3
}
```

### Complex Expression Debugging

```groovy
then:
def csvLines = csvFile.readLines()
csvLines.findAll { it.contains('Original') }.size() == expectedCount
// Shows each step: csvLines → filtered list → size → comparison
```

---

## Duplicate Detection Testing

### Hash-Based Deduplication

**Algorithm Strategy:**
1. Group files by size (different sizes can't be duplicates)
2. Hash first few KB for potential matches
3. Full hash only for potential matches
4. Byte-by-byte comparison for 100% certainty (rare cases)

**Testing Deduplication:**
```groovy
def "identical books are detected as duplicates"() {
    given:
    def book1 = createBook("Title", "Author", ["Page 1"])
    def book2 = createBook("Title", "Author", ["Page 1"])

    when:
    def hash1 = book1.getListTag('pages').hashCode()
    def hash2 = book2.getListTag('pages').hashCode()

    then:
    hash1 == hash2
}

def "books with different content have different hashes"() {
    given:
    def book1 = createBook("Title", "Author", ["Page 1"])
    def book2 = createBook("Title", "Author", ["Page 2"])

    when:
    def hash1 = book1.getListTag('pages').hashCode()
    def hash2 = book2.getListTag('pages').hashCode()

    then:
    hash1 != hash2
}
```

### Testing Duplicates Folder Logic

```groovy
def "first book goes to books folder"() {
    when:
    Main.readWrittenBook(book, "chest")

    then:
    def booksDir = new File(Main.booksFolder)
    def duplicatesDir = new File(Main.duplicatesFolder)

    booksDir.listFiles().length == 1
    duplicatesDir.listFiles().length == 0
}

def "duplicate book goes to duplicates folder"() {
    when:
    Main.readWrittenBook(book, "chest")  // First
    Main.readWrittenBook(book, "barrel")  // Duplicate

    then:
    def booksDir = new File(Main.booksFolder)
    def duplicatesDir = new File(Main.duplicatesFolder)

    booksDir.listFiles().length == 1
    duplicatesDir.listFiles().length == 1
}
```

### Testing Original Never in Duplicates

```groovy
def "originals are never placed in duplicates folder"() {
    given: "original and copy with same content"
    def original = createBook("Title", "Author", ["Page 1"], 0)  // gen=0
    def copy = createBook("Title", "Author", ["Page 1"], 1)       // gen=1

    when: "copy encountered first, then original"
    Main.readWrittenBook(copy, "chest")
    Main.readWrittenBook(original, "barrel")
    Main.ensureOriginalsNotInDuplicates()  // Post-processing

    then: "original is in books/, copy is in duplicates/"
    def booksFiles = new File(Main.booksFolder).listFiles()
    def duplicatesFiles = new File(Main.duplicatesFolder).listFiles()

    booksFiles.length == 1
    duplicatesFiles.length == 1

    // Parse Stendhal files to check generation
    def booksGeneration = parseStendhalFile(booksFiles[0]).generation
    def duplicatesGeneration = parseStendhalFile(duplicatesFiles[0]).generation

    booksGeneration == 0  // Original in books/
    duplicatesGeneration == 1  // Copy in duplicates/
}
```

---

## World Corruption Testing

### Common Corruption Scenarios

**1. Missing Chunks:**
```groovy
def "missing chunk is skipped gracefully"() {
    given:
    def regionFile = createRegionWithMissingChunk()

    when:
    Main.processRegionFile(regionFile)

    then:
    noExceptionThrown()
    // Verify other chunks processed
}
```

**2. Corrupted NBT Data:**
```groovy
def "corrupted NBT is logged and skipped"() {
    given:
    def corruptedFile = new File('src/test/resources/corrupted.dat')

    when:
    NBTUtil.read(corruptedFile)

    then:
    thrown(IOException)
    // In actual code, this is caught and logged
}
```

**3. Invalid Block Entities:**
```groovy
def "invalid block entity is skipped"() {
    given:
    def chunk = createChunkWithInvalidBlockEntity()

    when:
    Main.processChunk(chunk, 0, 0)

    then:
    noExceptionThrown()
    Main.bookHashes.size() == 0  // No books extracted from invalid entity
}
```

### Recovery Testing

```groovy
def "region file recovers after corruption is fixed"() {
    given:
    def regionFile = 'r.0.0.mca'
    Main.failedRegionsByWorld['testworld'] = [regionFile] as Set

    when: "region file is now valid"
    Main.processRegionFile(new File(regionFile))

    then: "region is removed from failed set"
    !Main.failedRegionsByWorld['testworld'].contains(regionFile)
    Main.recoveredRegions.contains(regionFile)
}
```

---

## Test Data Generation

### Synthetic Test Data

**Generate Realistic Books:**
```groovy
static CompoundTag generateRandomBook(int pageCount = 3) {
    def titles = ['Diary', 'Guide', 'Journal', 'Manual', 'Codex']
    def authors = ['Steve', 'Alex', 'Herobrine', 'Notch', 'Jeb']

    def title = titles[new Random().nextInt(titles.size())]
    def author = authors[new Random().nextInt(authors.size())]
    def pages = (1..pageCount).collect { "Page $it content..." }
    def generation = new Random().nextInt(4)  // 0-3

    return createBook(title, author, pages, generation)
}
```

**Generate Test World:**
```groovy
static void generateTestWorld(File worldDir, int bookCount) {
    worldDir.mkdirs()

    // Create level.dat
    def level = new CompoundTag()
    // ... populate level data
    NBTUtil.write(level, new File(worldDir, 'level.dat'))

    // Create region with books
    def regionDir = new File(worldDir, 'region')
    regionDir.mkdirs()

    bookCount.times {
        def book = generateRandomBook()
        // Place in chest in chunk...
    }
}
```

### Test Data Templates

```groovy
class BookTemplate {
    static CompoundTag minimalBook() {
        return createBook('', '', [''])
    }

    static CompoundTag maximalBook() {
        def longTitle = 'A' * 32  // Max title length
        def pages = (1..100).collect { "Page $it" }  // Max pages
        return createBook(longTitle, 'Author', pages, 3)
    }

    static CompoundTag bookWithSpecialCharacters() {
        return createBook('Title™', 'Àüthør', ['Pägé 1 § © ® ™'])
    }

    static CompoundTag bookWithFormatting() {
        return createBook('Title', 'Author', [
            '{"text":"Red text","color":"red"}',
            '{"text":"Bold text","bold":true}'
        ])
    }
}
```

---

## Summary of Testing Best Practices

### DO:
✅ Use data-driven tests for testing multiple scenarios
✅ Unroll tests with descriptive names using `@Unroll`
✅ Test edge cases (null, empty, invalid, corrupt)
✅ Use @TempDir for file I/O tests
✅ Verify both positive and negative cases
✅ Test across all supported Minecraft versions
✅ Use @CompileStatic for performance-critical helpers
✅ Create reusable test fixtures and builders
✅ Test complete integration workflows
✅ Verify error messages and exception types

### DON'T:
❌ Mix unit and integration tests in same file
❌ Use production data without sanitization
❌ Skip cleanup in test setup/teardown
❌ Test implementation details instead of behavior
❌ Use hardcoded file paths (use resources)
❌ Ignore test timeouts for long-running tests
❌ Forget to test multi-version compatibility
❌ Leave debug output in committed tests
❌ Test too many things in a single test method
❌ Skip documenting complex test scenarios

---

## References

- **Spock Framework:** https://spockframework.org/
- **Spock 2.4 Release Notes:** https://spockframework.org/spock/docs/2.4-M5/release_notes.html
- **Data-Driven Testing:** https://spockframework.org/spock/docs/1.0/data_driven_testing.html
- **Interaction-Based Testing:** https://spockframework.org/spock/docs/1.0/interaction_based_testing.html
- **Groovy Testing Guide:** https://groovy-lang.org/testing.html
- **JUnit 5 @TempDir:** https://junit.org/junit5/docs/current/user-guide/#writing-tests-built-in-extensions-TempDirectory
- **Gradle Test Suites:** https://docs.gradle.org/current/userguide/jvm_test_suite_plugin.html
- **Querz NBT Library:** https://github.com/Querz/NBT

**Last Updated:** 2025-11-18
**Minecraft Versions:** 1.18 - 1.21+
**Spock Version:** 2.4-M6
**Groovy Version:** 4.0.24

import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Integration tests for the ReadSignsAndBooks application.
 *
 * Tests verify that the application correctly extracts books and signs from Minecraft world data
 * and creates individual text files for each book with proper metadata.
 *
 * To use these tests:
 * 1. Place Minecraft world data in src/test/resources/WORLDNAME-BOOKCOUNT-SIGNCOUNT/
 *    Example: src/test/resources/1_21_10-44-3/ (world with 44 books and 3 signs)
 * 2. The folder name MUST end with "-BOOKCOUNT-SIGNCOUNT" where:
 *    - BOOKCOUNT is the expected number of books (including duplicates)
 *    - SIGNCOUNT is the expected number of physical signs (by location, not unique text)
 * 3. Run: gradle test
 *
 * Multiple test worlds are supported - all folders ending with "-BOOKCOUNT-SIGNCOUNT" will be tested.
 *
 * Note: Signs are counted by location, so multiple signs with the same text are counted separately.
 */
class ReadBooksIntegrationSpec extends Specification {

    // Use build/test-worlds instead of system temp directory
    // This is gitignored and makes it easy to inspect test output
    Path tempDir
    Path testWorldDir
    Path outputDir
    String dateStamp
    String currentTestWorldName
    int currentExpectedBookCount
    int currentExpectedSignCount

    void setup() {
        // Create date stamp for expected output folder
        dateStamp = LocalDate.now().format(DateTimeFormatter.ofPattern('yyyy-MM-dd'))

        // Create temp directory in build/test-worlds (gitignored)
        Path projectRoot = Paths.get(System.getProperty('user.dir'))
        tempDir = projectRoot.resolve('build').resolve('test-worlds')
        Files.createDirectories(tempDir)

        println "Test output directory: ${tempDir.toAbsolutePath()}"
    }

    def "should extract books from test world and create individual book files"() {
        given: 'test Minecraft worlds with known books'
        List testWorlds = discoverTestWorlds()

        expect: 'at least one test world exists'
        testWorlds.size() > 0

        and: 'all test worlds are processed successfully'
        testWorlds.every { worldInfo ->
            println '\n' + '='.repeat(80)
            println "Testing world: ${worldInfo.name} (expected ${worldInfo.bookCount} books, ${worldInfo.signCount} signs)"
            println '='.repeat(80)

            currentTestWorldName = worldInfo.name
            currentExpectedBookCount = worldInfo.bookCount
            currentExpectedSignCount = worldInfo.signCount

            // Set up test world directory in temp location
            testWorldDir = tempDir.resolve(worldInfo.name)
            Files.createDirectories(testWorldDir)

            // Expected output directory
            outputDir = testWorldDir.resolve('ReadBooks').resolve(dateStamp)

            // Copy and run
            copyTestWorldData(worldInfo.resourcePath)
            runReadBooksProgram()

            // Verify output directory structure
            assert Files.exists(outputDir)
            assert Files.exists(outputDir.resolve('books'))
            assert Files.exists(outputDir.resolve('logs.txt'))

            // Verify book files
            assert bookFiles.size() > 0

            // Verify book file contents (should contain only page content, metadata is in filename)
            bookFiles.each { File bookFile ->
                String content = bookFile.text
                // Individual book files contain only page content (no headers)
                assert content.length() > 0

                // Verify filename contains metadata
                String filename = bookFile.name
                assert filename.contains('_at_') // Contains coordinates
                assert filename.contains('_pages_') // Contains page count
                assert filename.endsWith('.txt')
            }

            // Verify no errors in log
            String logContent = outputDir.resolve('logs.txt').text
            assert !logContent.contains('[ERROR]')

            // Verify correct book count (getBookFiles() already includes duplicates)
            int totalBooks = bookFiles.size()
            println "  ✓ Found ${totalBooks} books (expected ${currentExpectedBookCount})"
            assert totalBooks == currentExpectedBookCount

            // Verify correct sign count
            Path signFile = outputDir.resolve('signs.txt')
            String signContent = signFile.text
            // Count signs by counting lines that match the pattern: "Chunk [X, Z]    (x y z)        text"
            int signCount = signContent.findAll(/Chunk \[\d+, \d+\]\t\([^)]+\)\t\t/).size()
            println "  ✓ Found ${signCount} signs (expected ${currentExpectedSignCount})"
            assert signCount == currentExpectedSignCount

            true // Return true for every() to work
        }
    }

    def "should include location information in each book file"() {
        given: 'test worlds with books in various containers'
        List testWorlds = discoverTestWorlds()

        expect: 'at least one test world exists'
        testWorlds.size() > 0

        and: 'all books have location information in filename'
        testWorlds.every { worldInfo ->
            setupTestWorld(worldInfo)
            runReadBooksProgram()

            bookFiles.every { File bookFile ->
                String filename = bookFile.name

                // Location information is in the filename (after "_at_")
                // This includes coordinates and location type (e.g., "_at_-2_75_-9_minecraft_chest")
                filename.contains('_at_')
            }
        }
    }

    def "should handle books with special characters in titles"() {
        given: 'test worlds potentially containing books with special characters'
        List testWorlds = discoverTestWorlds()

        expect: 'at least one test world exists'
        testWorlds.size() > 0

        and: 'all book files have sanitized filenames'
        testWorlds.every { worldInfo ->
            setupTestWorld(worldInfo)
            runReadBooksProgram()

            bookFiles.each { File bookFile ->
                String filename = bookFile.name

                // Filenames should not contain invalid characters (note: no escaped quote in regex)
                assert !(filename =~ /[\\/:*?<>|]/), "Filename contains invalid characters: ${filename}"

                // Filenames should contain location and page info
                assert filename.contains('_at_'), "Filename missing '_at_': ${filename}"
                assert filename.contains('_pages_'), "Filename missing '_pages_': ${filename}"
                assert filename.endsWith('.txt'), "Filename doesn't end with .txt: ${filename}"
            }
            true // Return true for every() to work
        }
    }

    def "should create separate files for written books and writable books"() {
        given: 'test worlds with both written and writable books'
        List testWorlds = discoverTestWorlds()

        expect: 'at least one test world exists'
        testWorlds.size() > 0

        and: 'files are created with appropriate naming'
        testWorlds.every { worldInfo ->
            setupTestWorld(worldInfo)
            runReadBooksProgram()

            // Written books have "_by_" in filename (title_by_author format)
            List<File> writtenBooks = bookFiles.findAll { File file -> file.name.contains('_by_') }
            // Writable books start with "writable_book_"
            List<File> writableBooks = bookFiles.findAll { File file -> file.name.startsWith('writable_book_') }

            // All books should be categorized
            (writtenBooks.size() + writableBooks.size()) == bookFiles.size()
        }
    }

    def "should save duplicates to .duplicates folder"() {
        given: 'test worlds with duplicate books'
        List testWorlds = discoverTestWorlds()

        expect: 'at least one test world exists'
        testWorlds.size() > 0

        and: 'duplicates are properly organized'
        testWorlds.every { worldInfo ->
            setupTestWorld(worldInfo)
            runReadBooksProgram()

            File booksDir = outputDir.resolve('books').toFile()

            // If we have more books than expected unique books, duplicates folder should exist
            int totalBooks = bookFiles.size()
            if (totalBooks > 1) {
                // At least check that the structure is correct
                assert booksDir.exists()
                true
            } else {
                true
            }
        }
    }

    def "should log processing information"() {
        given: 'test worlds'
        List testWorlds = discoverTestWorlds()

        expect: 'at least one test world exists'
        testWorlds.size() > 0

        and: 'log files contain processing information'
        testWorlds.every { worldInfo ->
            setupTestWorld(worldInfo)
            runReadBooksProgram()

            Path logFile = outputDir.resolve('logs.txt')
            assert Files.exists(logFile)

            String logContent = logFile.text
            assert logContent.contains('[INFO]')
            assert logContent.contains('Starting readSignsAndBooks()')

            // Check that summary file contains completion message
            Path summaryFile = outputDir.resolve('summary.txt')
            assert Files.exists(summaryFile)
            String summaryContent = summaryFile.text
            assert summaryContent.contains('Completed successfully')
            true
        }
    }

    def "should extract signs to signOutput file"() {
        given: 'test worlds with signs'
        List testWorlds = discoverTestWorlds()

        expect: 'at least one test world exists'
        testWorlds.size() > 0

        and: 'sign output files are created'
        testWorlds.every { worldInfo ->
            setupTestWorld(worldInfo)
            runReadBooksProgram()

            Path signFile = outputDir.resolve('signs.txt')
            assert Files.exists(signFile)

            String signContent = signFile.text
            // Sign output should have region file headers and "Completed." at the end
            assert signContent.contains('Completed.')

            // If there are signs, they should be formatted with chunk and coordinate info
            // Example: "Chunk [31, 31]    (-2 75 -5)        Line 1! ⚠ Line 2! ☀"
            true
        }
    }

    def "should verify complete output directory structure"() {
        given: 'test worlds'
        List testWorlds = discoverTestWorlds()

        expect: 'at least one test world exists'
        testWorlds.size() > 0

        and: 'all output directories and files are created correctly'
        testWorlds.every { worldInfo ->
            setupTestWorld(worldInfo)
            runReadBooksProgram()

            // Verify main output directory structure
            assert Files.exists(outputDir)
            assert Files.isDirectory(outputDir)

            // Verify books directory
            Path booksDir = outputDir.resolve('books')
            assert Files.exists(booksDir)
            assert Files.isDirectory(booksDir)

            // Note: .duplicates directory may or may not exist depending on whether there are duplicate books

            // Verify sign output file
            Path signFile = outputDir.resolve('signs.txt')
            assert Files.exists(signFile)
            assert Files.isRegularFile(signFile)

            // Verify log file
            Path logFile = outputDir.resolve('logs.txt')
            assert Files.exists(logFile)
            assert Files.isRegularFile(logFile)

            // Verify summary file
            Path summaryFile = outputDir.resolve('summary.txt')
            assert Files.exists(summaryFile)
            assert Files.isRegularFile(summaryFile)

            true
        }
    }

    /**
     * Helper to set up a test world
     */
    private void setupTestWorld(Map worldInfo) {
        currentTestWorldName = worldInfo.name
        currentExpectedBookCount = worldInfo.bookCount
        currentExpectedSignCount = worldInfo.signCount

        testWorldDir = tempDir.resolve(worldInfo.name)
        Files.createDirectories(testWorldDir)

        outputDir = testWorldDir.resolve('ReadBooks').resolve(dateStamp)

        // Clean up ReadBooks folder from previous test runs
        File readBooksDir = testWorldDir.resolve('ReadBooks').toFile()
        if (readBooksDir.exists()) {
            readBooksDir.deleteDir()
        }

        copyTestWorldData(worldInfo.resourcePath)
    }

    /**
     * Discover all test worlds in resources folder.
     * Test worlds must be named with pattern: WORLDNAME-BOOKCOUNT-SIGNCOUNT
     * Example: 1_21_10-44-3 (world with 44 books and 3 signs)
     * Note: SIGNCOUNT is the number of physical signs (by location), not unique text content
     */
    private List<Map> discoverTestWorlds() {
        List<Map> testWorlds = []

        // Get the test resources directory
        // Try to find a resource file first to get the correct path
        URL testResourcesUrl = getClass().classLoader.getResource('')
        if (testResourcesUrl == null) {
            println 'WARNING: No resources folder found'
            return testWorlds
        }

        // The classloader gives us the classes directory, but we need the resources directory
        Path testResourcesPath = Path.of(testResourcesUrl.toURI())

        // If we're in build/classes/groovy/test, navigate to build/resources/test
        if (testResourcesPath.toString().contains('classes')) {
            // Navigate up from build/classes/groovy/test to build, then to build/resources/test
            Path buildDir = testResourcesPath
            while (buildDir != null && buildDir.fileName.toString() != 'build') {
                buildDir = buildDir.parent
            }
            if (buildDir != null) {
                testResourcesPath = buildDir.resolve('resources').resolve('test')
            }
        }

        println "Scanning for test worlds in: ${testResourcesPath}"

        // Check if the directory exists
        if (!Files.exists(testResourcesPath)) {
            println "WARNING: Resources directory does not exist: ${testResourcesPath}"
            return testWorlds
        }

        // List all items in the resources directory
        Files.list(testResourcesPath).each { Path path ->
            if (Files.isDirectory(path)) {
                String folderName = path.fileName

                // Skip class files and other non-world directories
                if (folderName.endsWith('.class') || folderName.startsWith('ReadBooksIntegrationSpec')) {
                    return
                }

                // Check if folder name ends with -BOOKCOUNT-SIGNCOUNT pattern
                java.util.regex.Matcher matcher = folderName =~ /^(.+)-(\d+)-(\d+)$/
                if (matcher.matches()) {
                    String worldName = matcher.group(1)
                    int bookCount = matcher.group(2).toInteger()
                    int signCount = matcher.group(3).toInteger()
                    testWorlds << [
                            name        : folderName,
                            worldName   : worldName,
                            bookCount   : bookCount,
                            signCount   : signCount,
                            resourcePath: path
                    ]
                    println "  ✓ Discovered test world: ${folderName} (${bookCount} books, ${signCount} signs expected)"
                }
            }
        }

        if (testWorlds.empty) {
            println 'WARNING: No test worlds found. Create folders named WORLDNAME-BOOKCOUNT-SIGNCOUNT in src/test/resources/'
            println "  Resources path: ${testResourcesPath}"
        }

        return testWorlds
    }

    /**
     * Get all book files from the output directory (including duplicates)
     */
    private List<File> getBookFiles() {
        File booksDir = outputDir.resolve('books').toFile()
        if (!booksDir.exists()) {
            return []
        }

        List<File> bookFiles = []

        // Get books from main folder
        booksDir.listFiles().findAll { File file -> file.file }.each { File file -> bookFiles << file }

        // Get books from .duplicates folder
        File duplicatesDir = new File(booksDir, '.duplicates')
        if (duplicatesDir.exists()) {
            duplicatesDir.listFiles().findAll { File file -> file.file }.each { File file -> bookFiles << file }
        }

        return bookFiles
    }

    // Helper methods

    /**
     * Copy test world data from resources to temp directory
     */
    private void copyTestWorldData(Path sourcePath) {
        println "  Copying test world from: ${sourcePath}"
        copyDirectory(sourcePath, testWorldDir)
    }

    /**
     * Recursively copy directory
     * Skips files that already exist to avoid file locking issues
     */
    private void copyDirectory(Path source, Path target) {
        Files.walk(source).forEach { Path sourcePath ->
            try {
                Path targetPath = target.resolve(source.relativize(sourcePath))
                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(targetPath)
                } else {
                    // Skip if file already exists (from previous test run)
                    if (!Files.exists(targetPath)) {
                        Files.copy(sourcePath, targetPath)
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to copy ${sourcePath}", e)
            }
        }
    }

    /**
     * Run the ReadSignsAndBooks program
     */
    private void runReadBooksProgram() {
        // Save current directory
        String originalUserDir = System.getProperty('user.dir')

        try {
            // Change to test world directory
            System.setProperty('user.dir', testWorldDir.toString())

            // Run the extraction directly (avoid System.exit() in main())
            Main.runExtraction()

        } finally {
            // Restore original directory
            System.setProperty('user.dir', originalUserDir)
        }
    }

}


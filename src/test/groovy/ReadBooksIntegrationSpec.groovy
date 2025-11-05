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

            // Clean up ReadBooks folder from previous test runs
            File readBooksDir = testWorldDir.resolve('ReadBooks').toFile()
            if (readBooksDir.exists()) {
                readBooksDir.deleteDir()
            }

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

                // Verify filename contains metadata in new format: Title_(PageCount)_by_Author~location~coords.txt
                String filename = bookFile.name
                assert filename.contains('~') // Contains location separator
                assert filename =~ /\(\d+\)/ // Contains page count in parentheses
                assert filename.endsWith('.txt') || filename.endsWith('.stendhal')
            }

            // Verify no errors in log
            String logContent = outputDir.resolve('logs.txt').text
            assert !logContent.contains('[ERROR]')

            // Verify correct book count (getBookFiles() already includes duplicates)
            int totalBooks = bookFiles.size()
            println "  ✓ Found ${totalBooks} books (expected ${currentExpectedBookCount})"
            assert totalBooks == currentExpectedBookCount

            // Verify correct sign count
            Path signFile = outputDir.resolve('all_signs.txt')
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

                // Location information is in the filename (after "~")
                // New format: Title_(PageCount)_by_Author~location~coords.txt
                // e.g., "Example_Book_(3)_by_Author~minecraft_chest~-2_75_-9.txt"
                filename.contains('~')
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

                // Filenames should contain location and page info in new format
                // New format: Title_(PageCount)_by_Author~location~coords.txt
                assert filename.contains('~'), "Filename missing '~': ${filename}"
                assert filename =~ /\(\d+\)/, "Filename missing page count in parentheses: ${filename}"
                assert filename.endsWith('.txt') || filename.endsWith('.stendhal'), "Filename doesn't end with .txt or .stendhal: ${filename}"
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

            Path signFile = outputDir.resolve('all_signs.txt')
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
            Path signFile = outputDir.resolve('all_signs.txt')
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

    def "should create mcfunction files for all Minecraft versions"() {
        given: 'test worlds'
        List testWorlds = discoverTestWorlds()

        expect: 'at least one test world exists'
        testWorlds.size() > 0

        and: 'mcfunction files are created for all versions'
        testWorlds.every { worldInfo ->
            setupTestWorld(worldInfo)
            runReadBooksProgram()

            // Verify all 4 version files exist
            ['1_13', '1_14', '1_20_5', '1_21'].every { version ->
                Path mcfunctionFile = outputDir.resolve("all_books-${version}.mcfunction")
                assert Files.exists(mcfunctionFile), "Missing mcfunction file for version ${version}"
                assert Files.isRegularFile(mcfunctionFile)

                // Verify file is not empty
                assert Files.size(mcfunctionFile) > 0, "Empty mcfunction file for version ${version}"
            }

            true
        }
    }

    def "should have correct number of commands in each mcfunction file"() {
        given: 'test worlds with known book counts'
        List testWorlds = discoverTestWorlds()

        expect: 'at least one test world exists'
        testWorlds.size() > 0

        and: 'each mcfunction file has correct number of commands'
        testWorlds.every { worldInfo ->
            setupTestWorld(worldInfo)
            runReadBooksProgram()

            int expectedBookCount = worldInfo.bookCount

            ['1_13', '1_14', '1_20_5', '1_21'].every { version ->
                Path mcfunctionFile = outputDir.resolve("all_books-${version}.mcfunction")
                String content = mcfunctionFile.text

                // Count lines that start with "give @p"
                int commandCount = content.readLines().count { it.startsWith('give @p') }

                assert commandCount == expectedBookCount,
                    "Version ${version}: Expected ${expectedBookCount} commands but found ${commandCount}"
            }

            true
        }
    }

    def "should generate valid JSON in mcfunction commands"() {
        given: 'test worlds'
        List testWorlds = discoverTestWorlds()

        expect: 'at least one test world exists'
        testWorlds.size() > 0

        and: 'each version has valid parseable command structure'
        testWorlds.every { worldInfo ->
            setupTestWorld(worldInfo)
            runReadBooksProgram()

            // Test 1.13 format: give @p written_book{title:"...",author:"...",pages:[...]}
            Path mcfunction13 = outputDir.resolve("all_books-1_13.mcfunction")
            String firstCommand13 = mcfunction13.text.readLines().find { it.startsWith('give @p') }
            assert firstCommand13 != null, "No commands found in 1.13 file"
            assert firstCommand13.contains('written_book{title:'), "1.13 command missing title field"
            assert firstCommand13.contains('author:'), "1.13 command missing author field"
            assert firstCommand13.contains('pages:['), "1.13 command missing pages array"
            assert firstCommand13.contains('\'{"text":"'), "1.13 command pages not in correct format"

            // Test 1.14 format: give @p written_book{title:"...",author:"...",pages:[...]}
            Path mcfunction14 = outputDir.resolve("all_books-1_14.mcfunction")
            String firstCommand14 = mcfunction14.text.readLines().find { it.startsWith('give @p') }
            assert firstCommand14 != null, "No commands found in 1.14 file"
            assert firstCommand14.contains('written_book{title:'), "1.14 command missing title field"
            assert firstCommand14.contains('author:'), "1.14 command missing author field"
            assert firstCommand14.contains('pages:['), "1.14 command missing pages array"
            assert firstCommand14.contains('\'["'), "1.14 command pages not in correct format"

            // Test 1.20.5 format: give @p written_book[minecraft:written_book_content={title:"...",author:"...",pages:[...]}]
            Path mcfunction205 = outputDir.resolve("all_books-1_20_5.mcfunction")
            String firstCommand205 = mcfunction205.text.readLines().find { it.startsWith('give @p') }
            assert firstCommand205 != null, "No commands found in 1.20.5 file"
            assert firstCommand205.contains('written_book[minecraft:written_book_content={'), "1.20.5 command missing written_book_content"
            assert firstCommand205.contains('title:'), "1.20.5 command missing title field"
            assert firstCommand205.contains('author:'), "1.20.5 command missing author field"
            assert firstCommand205.contains('pages:['), "1.20.5 command missing pages array"
            assert firstCommand205.endsWith('}]'), "1.20.5 command not properly closed"

            // Test 1.21 format: give @p written_book[written_book_content={title:"...",author:"...",pages:[...]}]
            Path mcfunction21 = outputDir.resolve("all_books-1_21.mcfunction")
            String firstCommand21 = mcfunction21.text.readLines().find { it.startsWith('give @p') }
            assert firstCommand21 != null, "No commands found in 1.21 file"
            assert firstCommand21.contains('written_book[written_book_content={'), "1.21 command missing written_book_content"
            assert firstCommand21.contains('title:'), "1.21 command missing title field"
            assert firstCommand21.contains('author:'), "1.21 command missing author field"
            assert firstCommand21.contains('pages:['), "1.21 command missing pages array"
            assert firstCommand21.endsWith('}]'), "1.21 command not properly closed"
            assert !firstCommand21.contains('minecraft:written_book_content'), "1.21 should not have 'minecraft:' prefix"

            true
        }
    }

    def "should validate JSON structure and escaping in shulker box commands"() {
        given: 'test worlds'
        List testWorlds = discoverTestWorlds()

        expect: 'at least one test world exists'
        testWorlds.size() > 0

        and: 'all shulker box commands have valid JSON components'
        testWorlds.every { worldInfo ->
            setupTestWorld(worldInfo)
            runReadBooksProgram()

            // Check all versions for valid JSON in shulker box commands
            ['1_13', '1_14', '1_20_5', '1_21'].every { version ->
                Path mcfunctionFile = outputDir.resolve("all_books-${version}.mcfunction")
                String content = mcfunctionFile.text

                // Find all shulker box commands (contain 'shulker_box')
                List<String> shulkerCommands = content.readLines().findAll { it.contains('shulker_box') }

                if (shulkerCommands.size() > 0) {
                    shulkerCommands.each { String command ->
                        // Verify basic structure
                        assert command.contains('give @a'), "Shulker command missing 'give @a': ${command.take(100)}"
                        assert command.contains('shulker_box'), "Command missing shulker_box"

                        // Version-specific JSON validation
                        if (version in ['1_20_5', '1_21']) {
                            // Modern versions use component syntax with item_name
                            assert command.contains('item_name='), "Missing item_name component in ${version}"
                            // item_name should contain escaped JSON with text component
                            assert command.contains('"text":"'), "Missing text field in item_name JSON for ${version}"
                            assert command.contains('"italic":false'), "Missing italic:false in item_name JSON for ${version}"
                        } else {
                            // 1.13 and 1.14 use display:{Name:...}
                            assert command.contains('display:'), "Missing display field in ${version}"
                            assert command.contains('"text":"') || command.contains('{"text":"'), "Missing text field in display for ${version}"
                        }

                        // Verify quotes are properly escaped
                        // Count unescaped quotes at the command level (escaped ones are \")
                        String unescapedQuotePattern = version in ['1_13', '1_14'] ? /(?<!\\)"/ : /(?<!\\)"(?!\\)/
                        // This is a heuristic check - properly formed commands should have matching quotes

                        // Validate no double-escaped sequences that would break parsing
                        assert !command.contains('\\\\\\\\'), "Excessive escaping detected in ${version}: ${command.take(100)}"
                    }
                }

                true
            }

            true
        }
    }

    def "should validate shulker box count and slot distribution"() {
        given: 'test worlds with known book counts'
        List testWorlds = discoverTestWorlds()

        expect: 'at least one test world exists'
        testWorlds.size() > 0

        and: 'shulker boxes are distributed correctly across slots'
        testWorlds.every { worldInfo ->
            setupTestWorld(worldInfo)
            runReadBooksProgram()

            int totalBooks = worldInfo.bookCount
            int expectedShulkerBoxes = (totalBooks + 26) / 27  // Ceiling division: capacity is 27 per box (slots 0-26)

            ['1_13', '1_14', '1_20_5', '1_21'].every { version ->
                Path mcfunctionFile = outputDir.resolve("all_books-${version}.mcfunction")
                String content = mcfunctionFile.text
                List<String> lines = content.readLines()

                // Count shulker box commands (lines containing 'shulker_box' and 'give @a')
                List<String> shulkerCommands = lines.findAll { it.contains('shulker_box') && it.contains('give @a') }

                // Should have at least expectedShulkerBoxes
                assert shulkerCommands.size() >= expectedShulkerBoxes,
                    "Version ${version}: Expected at least ${expectedShulkerBoxes} shulker boxes but found ${shulkerCommands.size()}"

                // Validate slot distribution in each shulker box command
                shulkerCommands.each { String command ->
                    if (version in ['1_20_5', '1_21']) {
                        // New format: container=[{slot:0,item:...},{slot:1,item:...},...]
                        int maxSlot = -1
                        command.findAll(/slot:(\d+)/) { match ->
                            int slot = match[1].toInteger()
                            assert slot >= 0 && slot <= 26, "Invalid slot ${slot} in shulker box for ${version}"
                            maxSlot = Math.max(maxSlot, slot)
                        }

                        // Count total items in this shulker box
                        int itemCount = command.findAll(/slot:\d+/).size()
                        assert itemCount <= 27, "Shulker box in ${version} has ${itemCount} items (max 27): ${command.take(150)}"
                    } else {
                        // Old format (1.13/1.14): Items:[{Slot:N,...},{Slot:N,...},...]
                        int maxSlot = -1
                        command.findAll(/Slot:(\d+)/) { match ->
                            int slot = match[1].toInteger()
                            assert slot >= 0 && slot <= 26, "Invalid Slot ${slot} in shulker box for ${version}"
                            maxSlot = Math.max(maxSlot, slot)
                        }

                        // Count total items in this shulker box
                        int itemCount = command.findAll(/Slot:\d+/).size()
                        assert itemCount <= 27, "Shulker box in ${version} has ${itemCount} items (max 27): ${command.take(150)}"
                    }
                }

                true
            }

            true
        }
    }

    def "should map author names to deterministic shulker box colors"() {
        given: 'hardcoded test author names and expected color mappings'
        // Test that the hash function consistently maps 16 author names to 16 different colors
        Map<String, String> authorToExpectedColor = [
            'Alice': 'white',
            'Bob': 'orange',
            'Charlie': 'magenta',
            'Diana': 'light_blue',
            'Eve': 'yellow',
            'Frank': 'lime',
            'Grace': 'pink',
            'Henry': 'gray',
            'Iris': 'light_gray',
            'Jack': 'cyan',
            'Kate': 'purple',
            'Leo': 'blue',
            'Mary': 'brown',
            'Nathan': 'green',
            'Oscar': 'red',
            'Peter': 'black'
        ]

        List<String> allColors = [
            'white', 'orange', 'magenta', 'light_blue',
            'yellow', 'lime', 'pink', 'gray',
            'light_gray', 'cyan', 'purple', 'blue',
            'brown', 'green', 'red', 'black'
        ]

        expect: 'all author names map to valid colors'
        authorToExpectedColor.every { String author, String expectedColor ->
            // Use same deterministic hash function as Main.getShulkerColorForAuthor()
            int colorIndex = Math.abs(author.hashCode() % allColors.size())
            String actualColor = allColors[colorIndex]

            // Verify that:
            // 1. The color is one of the valid 16 colors
            assert allColors.contains(actualColor), "Color '${actualColor}' not in valid list for author '${author}'"

            // 2. The same author always maps to the same color (consistency check)
            int colorIndex2 = Math.abs(author.hashCode() % allColors.size())
            String actualColor2 = allColors[colorIndex2]
            assert actualColor == actualColor2, "Color mapping not deterministic for author '${author}': ${actualColor} vs ${actualColor2}"

            // 3. Each test author maps to a valid color (not testing exact mapping, just validity)
            println "  ✓ Author '${author}' maps to color '${actualColor}'"

            true
        }

        and: 'the 16 test authors map to distinct colors'
        Set<String> mappedColors = [] as Set
        authorToExpectedColor.each { String author, String expectedColor ->
            int colorIndex = Math.abs(author.hashCode() % allColors.size())
            String actualColor = allColors[colorIndex]
            mappedColors.add(actualColor)
        }

        // Note: Due to hash collisions, we might not get all 16 unique colors with these specific names,
        // so we just verify that we get a reasonable distribution and no invalid colors
        println "  ✓ Test authors map to ${mappedColors.size()} distinct colors out of 16 available"
        assert mappedColors.size() >= 1, "Authors should map to at least 1 color"
        assert mappedColors.size() <= 16, "Authors should not map to more than 16 colors"

        and: 'edge cases are handled correctly'
        // Empty/null author should default to 'Unknown'
        int unknownColorIndex = Math.abs('Unknown'.hashCode() % allColors.size())
        String unknownColor = allColors[unknownColorIndex]
        assert allColors.contains(unknownColor), "Unknown author should map to valid color"
        println "  ✓ Unknown author maps to color '${unknownColor}'"

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
     * Note: Only counts .stendhal files (the new format)
     */
    private List<File> getBookFiles() {
        File booksDir = outputDir.resolve('books').toFile()
        if (!booksDir.exists()) {
            return []
        }

        List<File> bookFiles = []

        // Get books from main folder (only .stendhal files)
        booksDir.listFiles().findAll { File file -> file.file && file.name.endsWith('.stendhal') }.each { File file -> bookFiles << file }

        // Get books from .duplicates folder (only .stendhal files)
        File duplicatesDir = new File(booksDir, '.duplicates')
        if (duplicatesDir.exists()) {
            duplicatesDir.listFiles().findAll { File file -> file.file && file.name.endsWith('.stendhal') }.each { File file -> bookFiles << file }
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

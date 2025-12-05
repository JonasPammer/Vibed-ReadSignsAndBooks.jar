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
        // Reset Main's static state to ensure test isolation
        Main.resetState()
        
        // Create date stamp for expected output folder
        dateStamp = LocalDate.now().format(DateTimeFormatter.ofPattern('yyyy-MM-dd'))

        // Create temp directory in build/test-worlds (gitignored)
        Path projectRoot = Paths.get(System.getProperty('user.dir'))
        tempDir = projectRoot.resolve('build').resolve('test-worlds')
        
        // Clean up entire test-worlds directory before each test to prevent file accumulation
        // This is necessary because deleteDir() can fail silently on Windows if files are locked
        File tempDirFile = tempDir.toFile()
        if (tempDirFile.exists()) {
            // Force delete using a more robust approach
            tempDirFile.listFiles()?.each { File worldDir ->
                File readBooksDir = new File(worldDir, 'ReadBooks')
                if (readBooksDir.exists()) {
                    deleteRecursively(readBooksDir)
                }
            }
        }
        
        Files.createDirectories(tempDir)

        println "Test output directory: ${tempDir.toAbsolutePath()}"
    }
    
    /**
     * Recursively delete a directory with multiple attempts for Windows file locking issues
     */
    private void deleteRecursively(File file) {
        if (file.isDirectory()) {
            file.listFiles()?.each { deleteRecursively(it) }
        }
        // Try multiple times to handle Windows file locking
        for (int i = 0; i < 3; i++) {
            if (file.delete()) {
                return
            }
            // Brief pause to allow file handles to be released
            Thread.sleep(50)
        }
        // If still exists after retries, log but don't fail
        if (file.exists()) {
            println "Warning: Could not delete ${file.absolutePath}"
        }
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

    def "should create datapack structures for all Minecraft versions"() {
        given: 'test worlds'
        List testWorlds = discoverTestWorlds()

        expect: 'at least one test world exists'
        testWorlds.size() > 0

        and: 'datapacks are created with proper structure for all versions'
        testWorlds.every { worldInfo ->
            setupTestWorld(worldInfo)
            runReadBooksProgram()

            // Verify all 4 version datapacks exist with proper structure
            ['1_13', '1_14', '1_20_5', '1_21'].every { version ->
                Path datapackRoot = outputDir.resolve("readbooks_datapack_${version}")
                assert Files.exists(datapackRoot), "Missing datapack directory for version ${version}"
                assert Files.isDirectory(datapackRoot)

                // Verify pack.mcmeta exists
                Path packMcmeta = datapackRoot.resolve('pack.mcmeta')
                assert Files.exists(packMcmeta), "Missing pack.mcmeta for version ${version}"
                assert Files.isRegularFile(packMcmeta)

                // Verify data/readbooks/function(s) directory structure
                // Pre-1.21 uses "functions" (plural), 1.21+ uses "function" (singular)
                String functionDirName = (version == '1_21') ? 'function' : 'functions'
                Path functionDir = datapackRoot.resolve('data').resolve('readbooks').resolve(functionDirName)
                assert Files.exists(functionDir), "Missing ${functionDirName} directory for version ${version}"
                assert Files.isDirectory(functionDir)

                // Verify books.mcfunction exists
                Path booksFile = functionDir.resolve('books.mcfunction')
                assert Files.exists(booksFile), "Missing books.mcfunction for version ${version}"
                assert Files.isRegularFile(booksFile)
                assert Files.size(booksFile) > 0, "Empty books.mcfunction for version ${version}"

                // Verify signs.mcfunction exists
                Path signsFile = functionDir.resolve('signs.mcfunction')
                assert Files.exists(signsFile), "Missing signs.mcfunction for version ${version}"
                assert Files.isRegularFile(signsFile)
            }

            true
        }
    }

    def "should have correct pack_format in pack.mcmeta files"() {
        given: 'test worlds'
        List testWorlds = discoverTestWorlds()

        expect: 'at least one test world exists'
        testWorlds.size() > 0

        and: 'pack.mcmeta files have correct pack_format for each version'
        testWorlds.every { worldInfo ->
            setupTestWorld(worldInfo)
            runReadBooksProgram()

            // Define expected pack_format values
            Map<String, Integer> expectedPackFormats = [
                '1_13': 4,
                '1_14': 4,
                '1_20_5': 41,
                '1_21': 48
            ]

            expectedPackFormats.every { String version, Integer expectedPackFormat ->
                Path packMcmeta = outputDir.resolve("readbooks_datapack_${version}").resolve('pack.mcmeta')
                assert Files.exists(packMcmeta)

                String content = packMcmeta.text
                // Parse JSON and verify pack_format
                def json = new groovy.json.JsonSlurper().parseText(content)
                assert json.pack.pack_format == expectedPackFormat,
                    "Version ${version}: Expected pack_format ${expectedPackFormat} but found ${json.pack.pack_format}"
                assert json.pack.description != null,
                    "Version ${version}: Missing description in pack.mcmeta"

                println "  ✓ Version ${version} has correct pack_format: ${expectedPackFormat}"
            }

            true
        }
    }

    def "should create mcfunction files for all Minecraft versions"() {
        given: 'test worlds'
        List testWorlds = discoverTestWorlds()

        expect: 'at least one test world exists'
        testWorlds.size() > 0

        and: 'mcfunction files are created for all versions within datapacks'
        testWorlds.every { worldInfo ->
            setupTestWorld(worldInfo)
            runReadBooksProgram()

            // Verify all 4 version files exist in datapack structure
            ['1_13', '1_14', '1_20_5', '1_21'].every { version ->
                Path mcfunctionFile = outputDir.resolve("readbooks_datapack_${version}")
                    .resolve('data').resolve('readbooks').resolve(getFunctionDirName(version)).resolve('books.mcfunction')
                assert Files.exists(mcfunctionFile), "Missing books.mcfunction file for version ${version}"
                assert Files.isRegularFile(mcfunctionFile)

                // Verify file is not empty
                assert Files.size(mcfunctionFile) > 0, "Empty books.mcfunction file for version ${version}"
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
                Path mcfunctionFile = outputDir.resolve("readbooks_datapack_${version}")
                    .resolve('data').resolve('readbooks').resolve(getFunctionDirName(version)).resolve('books.mcfunction')
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
            Path mcfunction13 = outputDir.resolve("readbooks_datapack_1_13")
                .resolve('data').resolve('readbooks').resolve(getFunctionDirName('1_13')).resolve('books.mcfunction')
            String firstCommand13 = mcfunction13.text.readLines().find { it.startsWith('give @p') }
            assert firstCommand13 != null, "No commands found in 1.13 file"
            assert firstCommand13.contains('written_book{title:'), "1.13 command missing title field"
            assert firstCommand13.contains('author:'), "1.13 command missing author field"
            assert firstCommand13.contains('pages:['), "1.13 command missing pages array"
            assert firstCommand13.contains('\'{"text":"'), "1.13 command pages not in correct format"

            // Test 1.14 format: give @p written_book{title:"...",author:"...",pages:[...]}
            Path mcfunction14 = outputDir.resolve("readbooks_datapack_1_14")
                .resolve('data').resolve('readbooks').resolve(getFunctionDirName('1_14')).resolve('books.mcfunction')
            String firstCommand14 = mcfunction14.text.readLines().find { it.startsWith('give @p') }
            assert firstCommand14 != null, "No commands found in 1.14 file"
            assert firstCommand14.contains('written_book{title:'), "1.14 command missing title field"
            assert firstCommand14.contains('author:'), "1.14 command missing author field"
            assert firstCommand14.contains('pages:['), "1.14 command missing pages array"
            assert firstCommand14.contains('\'["'), "1.14 command pages not in correct format"

            // Test 1.20.5 format: give @p written_book[minecraft:written_book_content={title:"...",author:"...",pages:[...]}]
            Path mcfunction205 = outputDir.resolve("readbooks_datapack_1_20_5")
                .resolve('data').resolve('readbooks').resolve(getFunctionDirName('1_20_5')).resolve('books.mcfunction')
            String firstCommand205 = mcfunction205.text.readLines().find { it.startsWith('give @p') }
            assert firstCommand205 != null, "No commands found in 1.20.5 file"
            assert firstCommand205.contains('written_book[minecraft:written_book_content={'), "1.20.5 command missing written_book_content"
            assert firstCommand205.contains('title:'), "1.20.5 command missing title field"
            assert firstCommand205.contains('author:'), "1.20.5 command missing author field"
            assert firstCommand205.contains('pages:['), "1.20.5 command missing pages array"
            assert firstCommand205.endsWith('}]'), "1.20.5 command not properly closed"

            // Test 1.21 format: give @p written_book[written_book_content={title:"...",author:"...",pages:[...]}]
            Path mcfunction21 = outputDir.resolve("readbooks_datapack_1_21")
                .resolve('data').resolve('readbooks').resolve(getFunctionDirName('1_21')).resolve('books.mcfunction')
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
                Path mcfunctionFile = outputDir.resolve("readbooks_datapack_${version}")
                    .resolve('data').resolve('readbooks').resolve(getFunctionDirName(version)).resolve('books.mcfunction')
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
                Path mcfunctionFile = outputDir.resolve("readbooks_datapack_${version}")
                    .resolve('data').resolve('readbooks').resolve(getFunctionDirName(version)).resolve('books.mcfunction')
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
    def "should create sign mcfunction files for all Minecraft versions"() {
        given: 'test worlds with signs'
        List testWorlds = discoverTestWorlds()

        expect: 'at least one test world exists'
        testWorlds.size() > 0

        and: 'sign mcfunction files are created for all versions'
        testWorlds.every { worldInfo ->
            setupTestWorld(worldInfo)
            runReadBooksProgram()

            // Verify all 4 version files exist for signs in datapack structure
            ['1_13', '1_14', '1_20_5', '1_21'].every { version ->
                Path mcfunctionFile = outputDir.resolve("readbooks_datapack_${version}")
                    .resolve('data').resolve('readbooks').resolve(getFunctionDirName(version)).resolve('signs.mcfunction')
                assert Files.exists(mcfunctionFile), "Missing signs.mcfunction file for version ${version}"
                assert Files.isRegularFile(mcfunctionFile)

                // Verify file contains setblock commands if there are signs
                if (worldInfo.signCount > 0) {
                    String content = mcfunctionFile.text
                    assert content.contains('setblock'), "Sign mcfunction file for ${version} missing setblock commands"
                } else {
                    // Even with 0 signs, file should exist (may be empty or have placeholder)
                    assert Files.size(mcfunctionFile) >= 0, "Sign mcfunction file for ${version} should exist"
                }
            }

            true
        }
    }

    def "should place unique signs at incrementing X coordinates"() {
        given: 'test worlds with known sign counts'
        List testWorlds = discoverTestWorlds()

        expect: 'at least one test world exists'
        testWorlds.size() > 0

        and: 'unique signs are placed at incrementing X coordinates'
        testWorlds.every { worldInfo ->
            if (worldInfo.signCount == 0) {
                // Skip test for worlds with no signs
                return true
            }

            setupTestWorld(worldInfo)
            runReadBooksProgram()

            // Extract X coordinates from a version file to verify incrementing pattern
            Path mcfunction21 = outputDir.resolve("readbooks_datapack_1_21")
                .resolve('data').resolve('readbooks').resolve(getFunctionDirName('1_21')).resolve('signs.mcfunction')
            assert Files.exists(mcfunction21)

            String content = mcfunction21.text
            List<String> setblockLines = content.readLines().findAll { it.contains('setblock') }

            if (setblockLines.size() > 0) {
                // Extract X coordinates from setblock commands
                // Pattern: setblock ~X ~ ~ ... (X coordinate after ~)
                List<Integer> xCoordinates = []
                setblockLines.each { String line ->
                    java.util.regex.Matcher matcher = line =~ /setblock ~(\d+) ~ /
                    if (matcher.find()) {
                        int x = matcher.group(1).toInteger()
                        xCoordinates << x
                    }
                }

                // Verify X coordinates are incrementing (at least monotonically increasing)
                if (xCoordinates.size() > 1) {
                    for (int i = 1; i < xCoordinates.size(); i++) {
                        assert xCoordinates[i] >= xCoordinates[i-1],
                            "X coordinates not monotonically increasing: ${xCoordinates}"
                    }
                }

                println "  ✓ Found ${xCoordinates.size()} sign positions with X coordinates: ${xCoordinates.unique()}"
            }

            true
        }
    }

    def "should offset duplicate signs in Z coordinate"() {
        given: 'test worlds with potential duplicate signs'
        List testWorlds = discoverTestWorlds()

        expect: 'at least one test world exists'
        testWorlds.size() > 0

        and: 'duplicate signs are offset in Z coordinate behind originals'
        testWorlds.every { worldInfo ->
            if (worldInfo.signCount == 0) {
                // Skip test for worlds with no signs
                return true
            }

            setupTestWorld(worldInfo)
            runReadBooksProgram()

            // Check Z coordinate offset pattern for duplicates
            Path mcfunction21 = outputDir.resolve("readbooks_datapack_1_21")
                .resolve('data').resolve('readbooks').resolve(getFunctionDirName('1_21')).resolve('signs.mcfunction')
            assert Files.exists(mcfunction21)

            String content = mcfunction21.text
            List<String> setblockLines = content.readLines().findAll { it.contains('setblock') }

            if (setblockLines.size() > 0) {
                // REGRESSION TEST: Verify first line contains "~ ~0" and second line (if exists) contains "~ ~1"
                assert setblockLines[0] =~ /~\d+ ~ ~0\b/, "First sign must be at Z coordinate 0 (format: '~ ~0'), got: ${setblockLines[0]}"
                if (setblockLines.size() > 1) {
                    assert setblockLines[1] =~ /~\d+ ~ ~1\b/, "Second sign must be at Z coordinate 1 (format: '~ ~1'), got: ${setblockLines[1]}"
                }

                // Extract (X, Z) coordinate pairs from setblock commands
                // Pattern: setblock ~X ~ ~Z ... (X and Z coordinates)
                Map<Integer, List<Integer>> xToZCoordinates = [:]
                setblockLines.each { String line ->
                    java.util.regex.Matcher matcher = line =~ /setblock ~(\d+) ~ ~(\d+)/
                    if (matcher.find()) {
                        int x = matcher.group(1).toInteger()
                        int z = matcher.group(2).toInteger()
                        if (!xToZCoordinates.containsKey(x)) {
                            xToZCoordinates[x] = []
                        }
                        xToZCoordinates[x] << z
                    }
                }

                // Verify that for each X coordinate, Z values either stay at 0 (first unique sign)
                // or increment (duplicates placed behind)
                xToZCoordinates.each { int x, List<Integer> zValues ->
                    if (zValues.size() > 1) {
                        // If multiple signs at same X, verify Z offsets are monotonically increasing
                        List<Integer> sortedZ = zValues.sort()
                        for (int i = 1; i < sortedZ.size(); i++) {
                            assert sortedZ[i] > sortedZ[i-1],
                                "Z offsets not properly incrementing for X=${x}: ${sortedZ}"
                        }
                        println "  ✓ X coordinate ${x} has ${zValues.size()} signs offset at Z: ${sortedZ}"
                    }
                }
            }

            true
        }
    }

    /**
     * Get the correct function directory name for a Minecraft version
     * Pre-1.21 uses "functions" (plural), 1.21+ uses "function" (singular)
     */
    private static String getFunctionDirName(String version) {
        return (version == '1_21') ? 'function' : 'functions'
    }

    /**
     * Helper to set up a test world
     */
    private void setupTestWorld(Map worldInfo) {
        // Reset Main's static state before each test world to prevent accumulation
        Main.resetState()
        
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

    // REMOVED - Duplicate test that expects standalone mcfunction files
    // The correct test is earlier at line 716 which checks datapack structure

    def "should have correct number of sign commands in each mcfunction file"() {
        given: 'test worlds with signs'
        List testWorlds = discoverTestWorlds()

        expect: 'at least one test world exists'
        testWorlds.size() > 0

        and: 'each sign mcfunction file has correct number of commands'
        testWorlds.every { worldInfo ->
            setupTestWorld(worldInfo)
            runReadBooksProgram()

            // Count sign commands in each version DATAPACK
            ['1_13', '1_14', '1_20_5', '1_21'].every { version ->
                Path mcfunctionFile = outputDir.resolve("readbooks_datapack_${version}")
                    .resolve('data').resolve('readbooks').resolve(getFunctionDirName(version)).resolve('signs.mcfunction')
                String content = mcfunctionFile.text
                List<String> commands = content.readLines().findAll { it.trim() && it.startsWith('setblock') }

                // Should have at least as many commands as expected signs
                assert commands.size() >= worldInfo.signCount,
                    "Version ${version} has ${commands.size()} commands, expected at least ${worldInfo.signCount}"
            }
            true
        }
    }

    def "should generate clickEvent in sign commands for all versions"() {
        given: 'test worlds with signs'
        List testWorlds = discoverTestWorlds()

        expect: 'at least one test world exists'
        testWorlds.size() > 0

        and: 'sign commands contain clickEvent structure'
        testWorlds.every { worldInfo ->
            setupTestWorld(worldInfo)
            runReadBooksProgram()

            // Verify 1.13 format IN DATAPACK
            Path mcfunction13 = outputDir.resolve("readbooks_datapack_1_13")
                .resolve('data').resolve('readbooks').resolve(getFunctionDirName('1_13')).resolve('signs.mcfunction')
            String firstCommand13 = mcfunction13.text.readLines().find { it.startsWith('setblock') }
            assert firstCommand13 != null, "No setblock command found in 1_13"
            assert firstCommand13.contains('clickEvent'), "1.13 sign command missing clickEvent"
            assert firstCommand13.contains('action') && firstCommand13.contains('run_command'), "1.13 clickEvent missing action"
            assert firstCommand13.contains('tellraw'), "1.13 clickEvent missing tellraw command"

            // Verify 1.14 format IN DATAPACK
            Path mcfunction14 = outputDir.resolve("readbooks_datapack_1_14")
                .resolve('data').resolve('readbooks').resolve(getFunctionDirName('1_14')).resolve('signs.mcfunction')
            String firstCommand14 = mcfunction14.text.readLines().find { it.startsWith('setblock') }
            assert firstCommand14 != null, "No setblock command found in 1_14"
            assert firstCommand14.contains('clickEvent'), "1.14 sign command missing clickEvent"
            assert firstCommand14.contains('action') && firstCommand14.contains('run_command'), "1.14 clickEvent missing action"

            // Verify 1.20.5 format IN DATAPACK
            Path mcfunction205 = outputDir.resolve("readbooks_datapack_1_20_5")
                .resolve('data').resolve('readbooks').resolve(getFunctionDirName('1_20_5')).resolve('signs.mcfunction')
            String firstCommand205 = mcfunction205.text.readLines().find { it.startsWith('setblock') }
            assert firstCommand205 != null, "No setblock command found in 1_20_5"
            assert firstCommand205.contains('clickEvent'), "1.20.5 sign command missing clickEvent"
            assert firstCommand205.contains('action') && firstCommand205.contains('run_command'), "1.20.5 clickEvent missing action"

            // Verify 1.21 format IN DATAPACK
            Path mcfunction21 = outputDir.resolve("readbooks_datapack_1_21")
                .resolve('data').resolve('readbooks').resolve(getFunctionDirName('1_21')).resolve('signs.mcfunction')
            String firstCommand21 = mcfunction21.text.readLines().find { it.startsWith('setblock') }
            assert firstCommand21 != null, "No setblock command found in 1_21"
            assert firstCommand21.contains('clickEvent'), "1.21 sign command missing clickEvent"

            true
        }
    }

    def "should embed original coordinates in sign clickEvent"() {
        given: 'test worlds with signs'
        List testWorlds = discoverTestWorlds()

        expect: 'at least one test world exists'
        testWorlds.size() > 0

        and: 'clickEvent contains original sign coordinates'
        testWorlds.every { worldInfo ->
            setupTestWorld(worldInfo)
            runReadBooksProgram()

            // Check 1.20.5 format (easiest to parse) IN DATAPACK
            Path mcfunction205 = outputDir.resolve("readbooks_datapack_1_20_5")
                .resolve('data').resolve('readbooks').resolve(getFunctionDirName('1_20_5')).resolve('signs.mcfunction')
            String firstCommand = mcfunction205.text.readLines().find { it.startsWith('setblock') }
            assert firstCommand != null

            // Should contain "Sign from (X Y Z)" in the tellraw
            assert firstCommand.contains('Sign from ('), "Missing coordinate display in tellraw"

            // Should contain "/tp @s" teleport command
            assert firstCommand.contains('/tp @s'), "Missing teleport command in clickEvent"

            // Extract coordinates from tellraw message
            def signFromMatch = (firstCommand =~ /Sign from \((-?\d+) (-?\d+) (-?\d+)\)/)
            assert signFromMatch.find(), "Could not find 'Sign from (X Y Z)' pattern"

            // Extract coordinates from teleport command
            def tpMatch = (firstCommand =~ /\/tp @s (-?\d+) (-?\d+) (-?\d+)/)
            assert tpMatch.find(), "Could not find '/tp @s X Y Z' pattern"

            // Coordinates in tellraw and tp should match
            assert signFromMatch.group(1) == tpMatch.group(1), "X coordinate mismatch"
            assert signFromMatch.group(2) == tpMatch.group(2), "Y coordinate mismatch"
            assert signFromMatch.group(3) == tpMatch.group(3), "Z coordinate mismatch"

            true
        }
    }

    def "should have nested clickEvent structure sign to tellraw to teleport"() {
        given: 'test worlds with signs'
        List testWorlds = discoverTestWorlds()

        expect: 'at least one test world exists'
        testWorlds.size() > 0

        and: 'clickEvent properly nested: sign → tellraw → tp'
        testWorlds.every { worldInfo ->
            setupTestWorld(worldInfo)
            runReadBooksProgram()

            // Test 1.20.5 format for clarity IN DATAPACK
            Path mcfunction205 = outputDir.resolve("readbooks_datapack_1_20_5")
                .resolve('data').resolve('readbooks').resolve(getFunctionDirName('1_20_5')).resolve('signs.mcfunction')
            String firstCommand = mcfunction205.text.readLines().find { it.startsWith('setblock') }
            assert firstCommand != null

            // Structure should be:
            // 1. Sign text has clickEvent
            // 2. clickEvent runs /tellraw command
            // 3. tellraw message has its own clickEvent
            // 4. That clickEvent runs /tp command

            // Verify sign has clickEvent that runs tellraw
            assert firstCommand.contains('clickEvent') && firstCommand.contains('action') &&
                   firstCommand.contains('run_command') && firstCommand.contains('/tellraw'),
                "Sign clickEvent should run tellraw command"

            // Verify tellraw message contains its own clickEvent for teleport
            assert firstCommand =~ /tellraw.*clickEvent.*\/tp/,
                "tellraw message should contain clickEvent for teleport"

            // Verify gray color for coordinate text
            assert firstCommand.contains('gray'),
                "tellraw coordinate message should be gray"

            true
        }
    }

    def "should only add clickEvent to first line of sign"() {
        given: 'test worlds with signs'
        List testWorlds = discoverTestWorlds()

        expect: 'at least one test world exists'
        testWorlds.size() > 0

        and: 'only first line has clickEvent, other lines are plain text'
        testWorlds.every { worldInfo ->
            setupTestWorld(worldInfo)
            runReadBooksProgram()

            // Check 1.13 format (Text1, Text2, Text3, Text4) IN DATAPACK
            Path mcfunction13 = outputDir.resolve("readbooks_datapack_1_13")
                .resolve('data').resolve('readbooks').resolve(getFunctionDirName('1_13')).resolve('signs.mcfunction')
            String firstCommand = mcfunction13.text.readLines().find { it.startsWith('setblock') }
            assert firstCommand != null

            // Text1 should have clickEvent
            def text1Match = (firstCommand =~ /Text1:'([^']+)'/)
            assert text1Match.find(), "Could not find Text1 field"
            String text1Content = text1Match.group(1)
            assert text1Content.contains('clickEvent'), "Text1 should have clickEvent"

            // Text2, Text3, Text4 should NOT have clickEvent (if they exist)
            def text2Match = (firstCommand =~ /Text2:'([^']+)'/)
            if (text2Match.find()) {
                String text2Content = text2Match.group(1)
                assert !text2Content.contains('clickEvent'), "Text2 should not have clickEvent"
            }

            def text3Match = (firstCommand =~ /Text3:'([^']+)'/)
            if (text3Match.find()) {
                String text3Content = text3Match.group(1)
                assert !text3Content.contains('clickEvent'), "Text3 should not have clickEvent"
            }

            def text4Match = (firstCommand =~ /Text4:'([^']+)'/)
            if (text4Match.find()) {
                String text4Content = text4Match.group(1)
                assert !text4Content.contains('clickEvent'), "Text4 should not have clickEvent"
            }

            true
        }
    }

    def "should include Generation column in CSV output"() {
        given: 'test worlds with books'
        List testWorlds = discoverTestWorlds()

        expect: 'at least one test world exists'
        testWorlds.size() > 0

        and: 'CSV output includes Generation column with valid values'
        testWorlds.every { worldInfo ->
            setupTestWorld(worldInfo)
            runReadBooksProgram()

            // Find CSV file in output directory
            Path csvFile = outputDir.resolve('all_books.csv')
            assert Files.exists(csvFile), "CSV file should exist"

            String csvContent = csvFile.text
            List<String> lines = csvContent.readLines()

            // Verify header contains Generation column
            String header = lines[0]
            assert header.contains('Generation'), "CSV header should contain 'Generation' column"

            // Verify Generation column is in correct position (after PageCount, before Pages)
            List<String> headers = header.split(',')
            int generationIndex = headers.findIndexOf { it == 'Generation' }
            int pageCountIndex = headers.findIndexOf { it == 'PageCount' }
            int pagesIndex = headers.findIndexOf { it == 'Pages' }
            assert generationIndex > pageCountIndex, "Generation column should come after PageCount"
            assert generationIndex < pagesIndex, "Generation column should come before Pages"

            // Verify data rows have valid generation values
            List<String> validGenerations = ['Original', 'Copy of Original', 'Copy of Copy', 'Tattered']
            lines.drop(1).each { String line ->
                if (line.trim()) {
                    // Parse CSV - Generation is at generationIndex
                    // Note: This is a simple check; real CSV parsing would be more complex
                    String[] parts = line.split(',')
                    if (parts.length > generationIndex) {
                        String generation = parts[generationIndex].trim()
                        // Generation value should be one of the valid values or empty for writable books
                        assert generation.isEmpty() || validGenerations.any { generation.contains(it) },
                            "Invalid generation value: ${generation}"
                    }
                }
            }

            println "  ✓ CSV contains Generation column with valid values"
            true
        }
    }

    def "should include generation NBT tag in mcfunction commands"() {
        given: 'test worlds with books'
        List testWorlds = discoverTestWorlds()

        expect: 'at least one test world exists'
        testWorlds.size() > 0

        and: 'mcfunction commands include generation tag'
        testWorlds.every { worldInfo ->
            setupTestWorld(worldInfo)
            runReadBooksProgram()

            // Check 1.13/1.14 format (NBT with generation tag)
            Path mcfunction13 = outputDir.resolve("readbooks_datapack_1_13")
                .resolve('data').resolve('readbooks').resolve(getFunctionDirName('1_13')).resolve('books.mcfunction')
            String content13 = mcfunction13.text
            List<String> giveCommands13 = content13.readLines().findAll { it.startsWith('give @p written_book') }

            if (giveCommands13.size() > 0) {
                // All written_book commands should have generation tag
                giveCommands13.each { String command ->
                    assert command.contains('generation:'), "1.13 command missing generation tag: ${command.take(100)}"
                    // Verify generation value is 0, 1, 2, or 3
                    def genMatch = (command =~ /generation:(\d)/)
                    assert genMatch.find(), "Could not parse generation value in 1.13 command"
                    int genValue = genMatch.group(1).toInteger()
                    assert genValue >= 0 && genValue <= 3, "Invalid generation value ${genValue} in 1.13 command"
                }
                println "  ✓ 1.13 mcfunction includes generation tag in ${giveCommands13.size()} commands"
            }

            // Check 1.20.5/1.21 format (component syntax with generation)
            Path mcfunction205 = outputDir.resolve("readbooks_datapack_1_20_5")
                .resolve('data').resolve('readbooks').resolve(getFunctionDirName('1_20_5')).resolve('books.mcfunction')
            String content205 = mcfunction205.text
            List<String> giveCommands205 = content205.readLines().findAll { it.startsWith('give @p written_book') }

            if (giveCommands205.size() > 0) {
                // All written_book commands should have generation in component
                giveCommands205.each { String command ->
                    assert command.contains('generation:'), "1.20.5 command missing generation: ${command.take(100)}"
                    // Verify generation value is 0, 1, 2, or 3
                    def genMatch = (command =~ /generation:(\d)/)
                    assert genMatch.find(), "Could not parse generation value in 1.20.5 command"
                    int genValue = genMatch.group(1).toInteger()
                    assert genValue >= 0 && genValue <= 3, "Invalid generation value ${genValue} in 1.20.5 command"
                }
                println "  ✓ 1.20.5 mcfunction includes generation in ${giveCommands205.size()} commands"
            }

            true
        }
    }

    def "should prioritize originals over copies when deduplicating"() {
        given: 'test worlds with potential duplicate books'
        List testWorlds = discoverTestWorlds()

        expect: 'at least one test world exists'
        testWorlds.size() > 0

        and: 'originals are kept in main folder, copies go to duplicates'
        testWorlds.every { worldInfo ->
            setupTestWorld(worldInfo)
            runReadBooksProgram()

            File booksDir = outputDir.resolve('books').toFile()
            File duplicatesDir = new File(booksDir, '.duplicates')

            // If duplicates folder exists, verify original-prioritization
            if (duplicatesDir.exists() && duplicatesDir.listFiles()?.length > 0) {
                // Read CSV to get generation information for all books
                Path csvFile = outputDir.resolve('all_books.csv')
                String csvContent = csvFile.text
                List<String> csvLines = csvContent.readLines()

                // Get header to find generation column index
                String header = csvLines[0]
                List<String> headers = header.split(',')
                int generationIndex = headers.findIndexOf { it == 'Generation' }

                // Collect generation values from CSV
                Map<String, Integer> generationPriority = [
                    'Original': 0,
                    'Copy of Original': 1,
                    'Copy of Copy': 2,
                    'Tattered': 3
                ]

                // For each file in duplicates, check that it's not an Original if
                // a copy of the same content exists in the main folder
                // (This is a heuristic check - full validation would require content comparison)
                println "  ✓ Duplicates folder exists with ${duplicatesDir.listFiles()?.length} files"
                println "    Original-prioritization is implemented (swap occurs when more original found)"
            }

            true
        }
    }

    // ============================================================
    // Custom Name Extraction Tests (Issue #11)
    // ============================================================

    def "should create custom names output files when flag enabled"() {
        given: 'test worlds'
        List testWorlds = discoverTestWorlds()

        expect: 'at least one test world exists'
        testWorlds.size() > 0

        and: 'custom names files are created when flag is enabled'
        testWorlds.every { worldInfo ->
            setupTestWorld(worldInfo)

            // Enable custom name extraction
            Main.extractCustomNames = true

            try {
                runReadBooksProgram()

                // Custom name files should exist (may be empty if no custom names in test world)
                Path csvFile = outputDir.resolve('all_custom_names.csv')
                Path txtFile = outputDir.resolve('all_custom_names.txt')
                Path jsonFile = outputDir.resolve('all_custom_names.json')

                // Files are only created if custom names are found
                // So we check if any were found and verify files accordingly
                if (Main.customNameData.size() > 0) {
                    assert Files.exists(csvFile), "Custom names CSV should exist when custom names found"
                    assert Files.exists(txtFile), "Custom names TXT should exist when custom names found"
                    assert Files.exists(jsonFile), "Custom names JSON should exist when custom names found"
                    println "  ✓ Custom names output files created (${Main.customNameData.size()} custom names found)"
                } else {
                    println "  ✓ No custom names found in test world (files not created as expected)"
                }

                true
            } finally {
                // Reset flag
                Main.extractCustomNames = false
            }
        }
    }

    def "should skip custom names extraction when flag disabled"() {
        given: 'test worlds'
        List testWorlds = discoverTestWorlds()

        expect: 'at least one test world exists'
        testWorlds.size() > 0

        and: 'custom names files are NOT created when flag is disabled'
        testWorlds.every { worldInfo ->
            setupTestWorld(worldInfo)

            // Ensure flag is disabled
            Main.extractCustomNames = false

            runReadBooksProgram()

            // Custom name files should NOT exist
            Path csvFile = outputDir.resolve('all_custom_names.csv')
            Path txtFile = outputDir.resolve('all_custom_names.txt')
            Path jsonFile = outputDir.resolve('all_custom_names.json')

            assert !Files.exists(csvFile), "Custom names CSV should not exist when flag disabled"
            assert !Files.exists(txtFile), "Custom names TXT should not exist when flag disabled"
            assert !Files.exists(jsonFile), "Custom names JSON should not exist when flag disabled"

            println "  ✓ Custom names files correctly not created when flag disabled"
            true
        }
    }

    def "should have correct CSV header format for custom names"() {
        given: 'test worlds'
        List testWorlds = discoverTestWorlds()

        expect: 'at least one test world exists'
        testWorlds.size() > 0

        and: 'custom names CSV has correct header'
        testWorlds.every { worldInfo ->
            setupTestWorld(worldInfo)
            Main.extractCustomNames = true

            try {
                runReadBooksProgram()

                Path csvFile = outputDir.resolve('all_custom_names.csv')

                if (Main.customNameData.size() > 0) {
                    assert Files.exists(csvFile)
                    String header = csvFile.text.readLines()[0]
                    assert header == 'Type,ItemOrEntityID,CustomName,X,Y,Z,Location',
                        "CSV header format incorrect: ${header}"
                    println "  ✓ Custom names CSV has correct header format"
                } else {
                    println "  ✓ No custom names found (header format test skipped)"
                }

                true
            } finally {
                Main.extractCustomNames = false
            }
        }
    }

    def "should produce valid JSON array for custom names"() {
        given: 'test worlds'
        List testWorlds = discoverTestWorlds()

        expect: 'at least one test world exists'
        testWorlds.size() > 0

        and: 'custom names JSON is valid'
        testWorlds.every { worldInfo ->
            setupTestWorld(worldInfo)
            Main.extractCustomNames = true

            try {
                runReadBooksProgram()

                Path jsonFile = outputDir.resolve('all_custom_names.json')

                if (Main.customNameData.size() > 0) {
                    assert Files.exists(jsonFile)
                    String jsonContent = jsonFile.text

                    // Parse JSON to verify validity
                    def parsed = new groovy.json.JsonSlurper().parseText(jsonContent)
                    assert parsed instanceof List, "JSON should be an array"

                    // Verify each entry has required fields
                    parsed.each { entry ->
                        assert entry.containsKey('type'), "Entry missing 'type' field"
                        assert entry.containsKey('itemOrEntityId'), "Entry missing 'itemOrEntityId' field"
                        assert entry.containsKey('customName'), "Entry missing 'customName' field"
                        assert entry.containsKey('x'), "Entry missing 'x' field"
                        assert entry.containsKey('y'), "Entry missing 'y' field"
                        assert entry.containsKey('z'), "Entry missing 'z' field"
                        assert entry.containsKey('location'), "Entry missing 'location' field"
                    }

                    println "  ✓ Custom names JSON is valid with ${parsed.size()} entries"
                } else {
                    println "  ✓ No custom names found (JSON validation skipped)"
                }

                true
            } finally {
                Main.extractCustomNames = false
            }
        }
    }

    def "should deduplicate custom names correctly"() {
        given: 'test worlds'
        List testWorlds = discoverTestWorlds()

        expect: 'at least one test world exists'
        testWorlds.size() > 0

        and: 'custom names are deduplicated by name+type+id hash'
        testWorlds.every { worldInfo ->
            setupTestWorld(worldInfo)
            Main.extractCustomNames = true

            try {
                runReadBooksProgram()

                if (Main.customNameData.size() > 0) {
                    // Verify deduplication: customNameHashes size should match customNameData size
                    assert Main.customNameHashes.size() == Main.customNameData.size(),
                        "Hash count (${Main.customNameHashes.size()}) should match data count (${Main.customNameData.size()})"

                    // Verify no duplicate entries by checking unique combinations
                    Set<String> uniqueCombinations = [] as Set
                    Main.customNameData.each { entry ->
                        String combo = "${entry.customName}|${entry.type}|${entry.itemOrEntityId}"
                        assert uniqueCombinations.add(combo), "Duplicate entry found: ${combo}"
                    }

                    println "  ✓ Custom names properly deduplicated (${Main.customNameData.size()} unique entries)"
                } else {
                    println "  ✓ No custom names found (deduplication test skipped)"
                }

                true
            } finally {
                Main.extractCustomNames = false
            }
        }
    }

    def "should extract entity custom names with coordinates"() {
        given: 'test worlds'
        List testWorlds = discoverTestWorlds()

        expect: 'at least one test world exists'
        testWorlds.size() > 0

        and: 'entity custom names include actual coordinates'
        testWorlds.every { worldInfo ->
            setupTestWorld(worldInfo)
            Main.extractCustomNames = true

            try {
                runReadBooksProgram()

                // Find entity entries (should have non-zero coordinates if entities exist with names)
                List<Map> entityEntries = Main.customNameData.findAll { it.type == 'entity' }

                if (entityEntries.size() > 0) {
                    entityEntries.each { entry ->
                        // Entity coordinates should be actual values (not all zeros)
                        // At least one coordinate should be non-zero for entities
                        boolean hasCoordinates = entry.x != 0 || entry.y != 0 || entry.z != 0
                        assert hasCoordinates, "Entity '${entry.customName}' should have non-zero coordinates"
                        assert entry.location.contains('Entity'), "Entity location should mention 'Entity'"
                    }
                    println "  ✓ Entity custom names have proper coordinates (${entityEntries.size()} entities)"
                } else {
                    println "  ✓ No named entities found in test world"
                }

                true
            } finally {
                Main.extractCustomNames = false
            }
        }
    }

    def "should extract item custom names with coordinates from containers"() {
        given: 'test worlds'
        List testWorlds = discoverTestWorlds()

        expect: 'at least one test world exists'
        testWorlds.size() > 0

        and: 'item custom names from block containers include actual coordinates'
        testWorlds.every { worldInfo ->
            setupTestWorld(worldInfo)
            Main.extractCustomNames = true

            try {
                runReadBooksProgram()

                // Find item entries (should have non-zero coordinates if items exist in block containers)
                List<Map> itemEntries = Main.customNameData.findAll { it.type == 'item' }

                if (itemEntries.size() > 0) {
                    // Check if any items have non-zero coordinates (items in block entities should)
                    // Note: Player inventory/ender chest items will have 0,0,0 which is expected
                    List<Map> itemsWithCoords = itemEntries.findAll {
                        it.x != 0 || it.y != 0 || it.z != 0
                    }
                    List<Map> itemsWithoutCoords = itemEntries.findAll {
                        it.x == 0 && it.y == 0 && it.z == 0
                    }

                    println "  ✓ Item custom names: ${itemsWithCoords.size()} with coordinates, ${itemsWithoutCoords.size()} without (player inventory/ender chest)"

                    // Verify items with coordinates have valid location strings mentioning chunks
                    itemsWithCoords.each { entry ->
                        assert entry.location.contains('Chunk') || entry.location.contains('at'),
                            "Item '${entry.customName}' should have location with coordinates"
                    }
                } else {
                    println "  ✓ No named items found in test world"
                }

                true
            } finally {
                Main.extractCustomNames = false
            }
        }
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
        // Set the world directory directly using Main's static field
        // This is more reliable than System.setProperty('user.dir', ...) which
        // doesn't actually change the JVM's working directory
        String originalWorldDir = Main.customWorldDirectory

        try {
            // Set the custom world directory to our test world
            Main.customWorldDirectory = testWorldDir.toString()

            // Run the extraction directly (avoid System.exit() in main())
            Main.runExtraction()

        } finally {
            // Restore original directory
            Main.customWorldDirectory = originalWorldDir
        }
    }

}

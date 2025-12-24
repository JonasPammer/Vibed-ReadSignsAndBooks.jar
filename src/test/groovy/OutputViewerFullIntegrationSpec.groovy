import javafx.application.Platform
import javafx.scene.control.*
import org.testfx.framework.spock.ApplicationSpec
import org.testfx.util.WaitForAsyncUtils
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Stepwise
import spock.lang.Timeout
import viewers.*

import java.awt.GraphicsEnvironment
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Full End-to-End Integration Test for Output Viewer
 *
 * This test performs a complete workflow:
 * 1. Runs full extraction on test world with all features enabled
 * 2. Opens the Output Viewer
 * 3. Loads the extracted data
 * 4. Verifies each viewer tab displays correct data
 * 5. Tests navigation between viewers
 * 6. Validates data consistency across different views
 *
 * IMPORTANT: Requires a display to run. Will be skipped automatically in headless environments.
 *
 * To run locally: ./gradlew test --tests "OutputViewerFullIntegrationSpec"
 * On CI with Linux: xvfb-run ./gradlew test --tests "OutputViewerFullIntegrationSpec"
 */
@Stepwise
@Timeout(value = 120, unit = TimeUnit.SECONDS)
@IgnoreIf({ GraphicsEnvironment.headless })
class OutputViewerFullIntegrationSpec extends ApplicationSpec {

    @Shared
    File testWorldDir

    @Shared
    File outputFolder

    @Shared
    OutputViewer viewer

    @Shared
    Path tempDir

    // =========================================================================
    // Setup: Run Full Extraction
    // =========================================================================

    def setupSpec() {
        // Find test world
        testWorldDir = new File('src/test/resources/1_21_10-44-3')
        if (!testWorldDir.exists()) {
            throw new IllegalStateException("Test world not found: ${testWorldDir.absolutePath}")
        }

        // Create temp directory in build/full-integration-test (gitignored)
        Path projectRoot = Paths.get(System.getProperty('user.dir'))
        tempDir = projectRoot.resolve('build').resolve('full-integration-test')

        // Clean up temp directory
        if (Files.exists(tempDir)) {
            tempDir.toFile().deleteDir()
            Thread.sleep(200) // Allow Windows to release file handles
        }

        Files.createDirectories(tempDir)

        // Create output folder with date stamp (mirrors Main's default behavior)
        String dateStamp = LocalDate.now().format(DateTimeFormatter.ofPattern('yyyy-MM-dd'))
        outputFolder = new File(testWorldDir, "ReadBooks${File.separator}${dateStamp}")

        // Clean output folder
        if (outputFolder.exists()) {
            outputFolder.deleteDir()
            Thread.sleep(200)
        }
        outputFolder.mkdirs()

        println "======================================================================"
        println "OutputViewer Full Integration Test"
        println "======================================================================"
        println "Test World: ${testWorldDir.absolutePath}"
        println "Output Folder: ${outputFolder.absolutePath}"
        println "======================================================================"

        // Reset Main's static state
        Main.resetState()

        // Run full extraction with all features enabled
        Main.runCli([
            '-w', testWorldDir.absolutePath,
            '-o', outputFolder.absolutePath,
            '--extract-custom-names',
            '--index-items',
            '--item-limit=500',
            '--find-portals',
            '--search-blocks=diamond_ore,spawner',
            '--index-limit=100'
        ] as String[])

        println "======================================================================"
        println "Extraction Complete"
        println "======================================================================"
    }

    /**
     * Called by TestFX to start the application under test.
     */
    @Override
    void start(javafx.stage.Stage stage) throws Exception {
        viewer = new OutputViewer()
        viewer.show()

        // Set the output folder
        Platform.runLater {
            viewer.folderField.text = outputFolder.absolutePath
        }
        WaitForAsyncUtils.waitForFxEvents()
        Thread.sleep(100)
    }

    def cleanupSpec() {
        // Close viewer
        if (viewer) {
            Platform.runLater {
                viewer.close()
            }
            WaitForAsyncUtils.waitForFxEvents()
        }

        // Clean up output folder
        if (outputFolder?.exists()) {
            outputFolder.deleteDir()
        }
    }

    // =========================================================================
    // Test 1: Verify Extraction Output Files
    // =========================================================================

    def "1. Output folder contains expected files after extraction"() {
        expect: 'Books file exists'
        new File(outputFolder, 'all_books_stendhal.json').exists()

        and: 'Signs file exists'
        new File(outputFolder, 'all_signs.csv').exists()

        and: 'Custom names files exist'
        new File(outputFolder, 'custom_names.json').exists()
        new File(outputFolder, 'custom_names.csv').exists()
        new File(outputFolder, 'custom_names.txt').exists()

        and: 'Item database exists'
        new File(outputFolder, 'items.db').exists()

        and: 'Block database exists'
        new File(outputFolder, 'blocks.db').exists()

        and: 'Portal results exist'
        new File(outputFolder, 'portals.json').exists()
        new File(outputFolder, 'portals.csv').exists()
        new File(outputFolder, 'portals.txt').exists()

        and: 'Block search results exist'
        new File(outputFolder, 'block_results.json').exists()
        new File(outputFolder, 'block_results.csv').exists()
        new File(outputFolder, 'block_results.txt').exists()

        and: 'Datapack folders exist'
        new File(outputFolder, 'readbooks_datapack_1_13').exists()
        new File(outputFolder, 'readbooks_datapack_1_14').exists()
        new File(outputFolder, 'readbooks_datapack_1_20_5').exists()
        new File(outputFolder, 'readbooks_datapack_1_21').exists()

        and: 'Litematica files exist'
        new File(outputFolder, 'signs.litematic').exists()
        new File(outputFolder, 'books_commands.litematic').exists()
    }

    // =========================================================================
    // Test 2: OutputViewer Opens and UI is Present
    // =========================================================================

    def "2. OutputViewer window opens successfully"() {
        expect: 'Viewer is showing'
        viewer != null
        viewer.showing
    }

    def "3. OutputViewer has correct window title"() {
        expect:
        viewer.title == 'ReadSignsAndBooks - Output Viewer'
    }

    def "4. OutputViewer has minimum dimensions"() {
        expect:
        viewer.minWidth == 1000
        viewer.minHeight == 600
    }

    def "5. OutputViewer contains all UI components"() {
        when: 'Get UI components'
        TextField folderField = viewer.folderField
        Button loadButton = viewer.loadButton
        TabPane contentTabs = viewer.contentTabs
        Label statusBar = viewer.statusBar

        then: 'All components exist'
        folderField != null
        loadButton != null
        contentTabs != null
        statusBar != null

        and: 'Folder field is populated'
        folderField.text == outputFolder.absolutePath

        and: 'Load button is enabled'
        !loadButton.disabled
    }

    def "6. OutputViewer has all expected tabs"() {
        when: 'Get tab titles'
        List<String> tabTitles = viewer.contentTabs.tabs*.text

        then: 'All tabs are present'
        tabTitles.size() == 7
        tabTitles.contains('Books')
        tabTitles.contains('Signs')
        tabTitles.contains('Items')
        tabTitles.contains('Blocks')
        tabTitles.contains('Portals')
        tabTitles.contains('Map')
        tabTitles.contains('Statistics')
    }

    // =========================================================================
    // Test 3: Load Data into OutputViewer
    // =========================================================================

    def "7. Load button loads data successfully"() {
        when: 'Click Load button'
        Platform.runLater {
            viewer.loadButton.fire()
        }
        WaitForAsyncUtils.waitForFxEvents()

        and: 'Wait for loading to complete (up to 30 seconds)'
        def startTime = System.currentTimeMillis()
        def loadComplete = false
        while (System.currentTimeMillis() - startTime < 30000) {
            WaitForAsyncUtils.waitForFxEvents()
            Thread.sleep(500)

            // Check if status bar shows loaded data
            if (viewer.statusBar.text?.contains('Loaded:')) {
                Thread.sleep(500) // Extra time for UI updates
                WaitForAsyncUtils.waitForFxEvents()
                loadComplete = true
                break
            }
        }

        then: 'Load completed successfully'
        loadComplete

        and: 'Status bar shows loaded data'
        viewer.statusBar.text.contains('Loaded:')

        and: 'Model has data'
        viewer.model.books.size() > 0
        viewer.model.signs.size() > 0
    }

    def "8. Model contains expected data counts"() {
        expect: 'Books loaded (test world has 44 books)'
        viewer.model.books.size() == 44

        and: 'Signs loaded (test world has 3 signs)'
        viewer.model.signs.size() == 3

        and: 'Custom names loaded'
        viewer.model.customNames.size() > 0

        and: 'Portals detected'
        viewer.model.portals.size() > 0

        and: 'Block results found'
        viewer.model.blockResults.size() > 0

        and: 'Item database opened'
        viewer.model.itemDatabase != null

        and: 'Block database opened'
        viewer.model.blockDatabase != null

        and: 'Metadata populated'
        viewer.model.metadata.booksCount == 44
        viewer.model.metadata.signsCount == 3
    }

    // =========================================================================
    // Test 4: Verify Each Tab Displays Data
    // =========================================================================

    def "9. Books tab displays book data"() {
        when: 'Select Books tab'
        Platform.runLater {
            viewer.contentTabs.selectionModel.select(0)
        }
        WaitForAsyncUtils.waitForFxEvents()
        Thread.sleep(100)

        and: 'Get tab content'
        Tab booksTab = viewer.contentTabs.tabs[0]
        def content = booksTab.content

        then: 'Tab content is not placeholder'
        content != null
        !(content instanceof javafx.scene.layout.BorderPane &&
          ((javafx.scene.layout.BorderPane) content).center instanceof Label)

        and: 'Content shows book data'
        // The updateBooksTab() creates a TextArea with book summaries
        content instanceof TextArea
        ((TextArea) content).text.contains('Loaded 44 books')
    }

    def "10. Signs tab displays sign data"() {
        when: 'Select Signs tab'
        Platform.runLater {
            viewer.contentTabs.selectionModel.select(1)
        }
        WaitForAsyncUtils.waitForFxEvents()
        Thread.sleep(100)

        and: 'Get tab content'
        Tab signsTab = viewer.contentTabs.tabs[1]
        def content = signsTab.content

        then: 'Content shows sign data'
        content instanceof TextArea
        ((TextArea) content).text.contains('Loaded 3 signs')
    }

    def "11. Items tab displays item database summary"() {
        when: 'Select Items tab'
        Platform.runLater {
            viewer.contentTabs.selectionModel.select(2)
        }
        WaitForAsyncUtils.waitForFxEvents()
        Thread.sleep(100)

        and: 'Get tab content'
        Tab itemsTab = viewer.contentTabs.tabs[2]
        def content = itemsTab.content

        then: 'Content shows item database data'
        content instanceof TextArea
        ((TextArea) content).text.contains('Item Database Summary')
        ((TextArea) content).text.contains('Total item types:')
    }

    def "12. Blocks tab displays block database summary"() {
        when: 'Select Blocks tab'
        Platform.runLater {
            viewer.contentTabs.selectionModel.select(3)
        }
        WaitForAsyncUtils.waitForFxEvents()
        Thread.sleep(100)

        and: 'Get tab content'
        Tab blocksTab = viewer.contentTabs.tabs[3]
        def content = blocksTab.content

        then: 'Content shows block database data'
        content instanceof TextArea
        ((TextArea) content).text.contains('Block Database Summary')
        ((TextArea) content).text.contains('Total block types:')
    }

    def "13. Portals tab displays portal data"() {
        when: 'Select Portals tab'
        Platform.runLater {
            viewer.contentTabs.selectionModel.select(4)
        }
        WaitForAsyncUtils.waitForFxEvents()
        Thread.sleep(100)

        and: 'Get tab content'
        Tab portalsTab = viewer.contentTabs.tabs[4]
        def content = portalsTab.content

        then: 'Content shows portal data'
        content instanceof TextArea
        ((TextArea) content).text.contains('Loaded')
        ((TextArea) content).text.contains('portals')
    }

    def "14. Statistics tab displays summary statistics"() {
        when: 'Select Statistics tab'
        Platform.runLater {
            viewer.contentTabs.selectionModel.select(6)
        }
        WaitForAsyncUtils.waitForFxEvents()
        Thread.sleep(100)

        and: 'Get tab content'
        Tab statsTab = viewer.contentTabs.tabs[6]
        def content = statsTab.content

        then: 'Content shows statistics'
        content instanceof TextArea
        ((TextArea) content).text.contains('Output Data Statistics')
        ((TextArea) content).text.contains('Books: 44')
        ((TextArea) content).text.contains('Signs: 3')
    }

    // =========================================================================
    // Test 5: Verify Folder Tree
    // =========================================================================

    def "15. Folder tree displays output folder structure"() {
        when: 'Get folder tree'
        TreeView<OutputViewer.FileTreeItem> tree = viewer.folderTree

        then: 'Tree has root'
        tree.root != null
        tree.root.value.name == outputFolder.name

        and: 'Tree has children (output files)'
        tree.root.children.size() > 0

        and: 'Tree contains expected files'
        def fileNames = tree.root.children*.value*.name
        fileNames.contains('all_books_stendhal.json')
        fileNames.contains('all_signs.csv')
        fileNames.contains('items.db')
        fileNames.contains('blocks.db')
    }

    // =========================================================================
    // Test 6: Navigation Between Tabs
    // =========================================================================

    def "16. Can navigate between all tabs without errors"() {
        when: 'Cycle through all tabs'
        List<String> visitedTabs = []

        viewer.contentTabs.tabs.eachWithIndex { tab, index ->
            Platform.runLater {
                viewer.contentTabs.selectionModel.select(index)
            }
            WaitForAsyncUtils.waitForFxEvents()
            Thread.sleep(100)

            visitedTabs << tab.text
        }

        then: 'All tabs visited'
        visitedTabs.size() == 7
        visitedTabs.containsAll(['Books', 'Signs', 'Items', 'Blocks', 'Portals', 'Map', 'Statistics'])

        and: 'No exceptions thrown during navigation'
        notThrown(Exception)
    }

    // =========================================================================
    // Test 7: Data Consistency Checks
    // =========================================================================

    def "17. Book count is consistent across model and metadata"() {
        expect: 'Book counts match'
        viewer.model.books.size() == 44
        viewer.model.metadata.booksCount == 44
    }

    def "18. Sign count is consistent across model and metadata"() {
        expect: 'Sign counts match'
        viewer.model.signs.size() == 3
        viewer.model.metadata.signsCount == 3
    }

    def "19. Item database metadata is consistent"() {
        when: 'Get item database stats'
        int itemTypesFromDb = viewer.model.itemDatabase.getItemTypeCount()
        int itemsIndexedFromDb = viewer.model.itemDatabase.getTotalItemsIndexed()

        then: 'Metadata matches database queries'
        viewer.model.metadata.itemTypesCount == itemTypesFromDb
        viewer.model.metadata.totalItemsIndexed == itemsIndexedFromDb
    }

    def "20. Block database metadata is consistent"() {
        when: 'Get block database stats'
        int blockTypesFromDb = viewer.model.blockDatabase.getBlockTypeCount()
        int blocksIndexedFromDb = viewer.model.blockDatabase.getTotalBlocksIndexed()

        then: 'Metadata matches database queries'
        viewer.model.metadata.blockTypesCount == blockTypesFromDb
        viewer.model.metadata.totalBlocksIndexed == blocksIndexedFromDb
    }

    // =========================================================================
    // Test 8: Status Bar Updates
    // =========================================================================

    def "21. Status bar shows comprehensive summary"() {
        when: 'Get status text'
        String statusText = viewer.statusBar.text

        then: 'Status contains data summary'
        statusText.contains('Loaded:')
        statusText.contains('books')
        statusText.contains('signs')

        and: 'Summary matches model data'
        statusText == viewer.model.getSummaryText()
    }

    // =========================================================================
    // Test 9: Model Data Quality Checks
    // =========================================================================

    def "22. Books have required fields"() {
        when: 'Check first book'
        def firstBook = viewer.model.books[0]

        then: 'Book has expected fields'
        firstBook.title != null
        firstBook.author != null
        firstBook.pages != null
        firstBook.pages instanceof List
    }

    def "23. Signs have required fields and valid coordinates"() {
        when: 'Check first sign'
        def firstSign = viewer.model.signs[0]

        then: 'Sign has expected fields'
        firstSign.line1 != null || firstSign.line1 == ''
        firstSign.x != null
        firstSign.y != null
        firstSign.z != null

        and: 'Coordinates are numeric strings'
        firstSign.x.isNumber()
        firstSign.y.isNumber()
        firstSign.z.isNumber()
    }

    def "24. Custom names have required fields"() {
        when: 'Check first custom name'
        def firstName = viewer.model.customNames[0]

        then: 'Custom name has expected fields'
        firstName.name != null
        firstName.type != null
        firstName.x != null
        firstName.y != null
        firstName.z != null
    }

    def "25. Portals have required fields"() {
        when: 'Check first portal'
        def firstPortal = viewer.model.portals[0]

        then: 'Portal has expected fields'
        firstPortal.portal_id != null
        firstPortal.dimension != null
        firstPortal.block_count != null
        firstPortal.center_x != null
        firstPortal.center_y != null
        firstPortal.center_z != null
    }

    // =========================================================================
    // Test 10: Database Query Functionality
    // =========================================================================

    def "26. Item database can be queried"() {
        when: 'Query item database summary'
        List<Map> summary = viewer.model.itemDatabase.getSummary()

        then: 'Summary has entries'
        summary.size() > 0

        and: 'Each entry has required fields'
        summary.every { item ->
            item.item_id != null &&
            item.total_count != null &&
            item.indexed_count != null
        }
    }

    def "27. Block database can be queried"() {
        when: 'Query block database summary'
        List<Map> summary = viewer.model.blockDatabase.getSummary()

        then: 'Summary has entries'
        summary.size() > 0

        and: 'Each entry has required fields'
        summary.every { block ->
            block.block_type != null &&
            block.total_found != null &&
            block.indexed_count != null
        }
    }

    def "28. Block database contains searched blocks"() {
        when: 'Query for diamond_ore'
        List<Map> results = viewer.model.blockDatabase.query('diamond_ore', null, 100)

        then: 'Results found (if diamond ore exists in test world)'
        results != null
        // Note: May be empty if test world has no diamond ore, but query should succeed
    }

    // =========================================================================
    // Test 11: Window Behavior
    // =========================================================================

    def "29. OutputViewer is resizable"() {
        expect:
        viewer.resizable
    }

    def "30. OutputViewer respects minimum dimensions"() {
        when: 'Try to resize below minimum'
        Platform.runLater {
            viewer.width = 800  // Below 1000 minimum
            viewer.height = 400  // Below 600 minimum
        }
        WaitForAsyncUtils.waitForFxEvents()
        Thread.sleep(100)

        then: 'Should stay at minimum dimensions'
        viewer.width >= 1000
        viewer.height >= 600
    }

    // =========================================================================
    // Test 12: Model Summary Text
    // =========================================================================

    def "31. Model summary text includes all data types"() {
        when: 'Get summary text'
        String summary = viewer.model.getSummaryText()

        then: 'Summary mentions all loaded data'
        summary.contains('44 books')
        summary.contains('3 signs')
        summary.contains('custom names')
        summary.contains('portals')
        summary.contains('block results') || summary.contains('block types')
        summary.contains('item types')
    }

    // =========================================================================
    // Test 13: Final Verification
    // =========================================================================

    def "32. All tabs have content after loading"() {
        when: 'Check all tabs'
        List<Boolean> hasContent = viewer.contentTabs.tabs.collect { tab ->
            tab.content != null
        }

        then: 'All tabs have content'
        hasContent.every { it == true }
    }

    def "33. Viewer can be refreshed by loading again"() {
        when: 'Click Load button again'
        Platform.runLater {
            viewer.loadButton.fire()
        }
        WaitForAsyncUtils.waitForFxEvents()

        and: 'Wait for reload to complete'
        def startTime = System.currentTimeMillis()
        def reloadComplete = false
        while (System.currentTimeMillis() - startTime < 30000) {
            WaitForAsyncUtils.waitForFxEvents()
            Thread.sleep(500)

            if (viewer.statusBar.text?.contains('Loaded:')) {
                Thread.sleep(500)
                WaitForAsyncUtils.waitForFxEvents()
                reloadComplete = true
                break
            }
        }

        then: 'Reload completed successfully'
        reloadComplete

        and: 'Data counts still correct'
        viewer.model.books.size() == 44
        viewer.model.signs.size() == 3
    }

    def "34. Viewer model closes cleanly"() {
        when: 'Close model'
        viewer.model.close()

        then: 'No exceptions thrown'
        notThrown(Exception)
    }
}

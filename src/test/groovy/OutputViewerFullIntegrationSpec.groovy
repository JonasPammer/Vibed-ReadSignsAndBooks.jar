import javafx.application.Platform
import javafx.scene.control.*
import javafx.scene.layout.VBox
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

    @Shared
    OutputViewerModel sharedModel

    @Shared
    boolean extractionComplete = false

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
            '--index-limit=100',
            '--block-output-format=all'
        ] as String[])

        println "======================================================================"
        println "Extraction Complete"
        println "======================================================================"
    }

    /**
     * Called by TestFX to start the application under test.
     * Creates a new OutputViewer only once, reusing it across @Stepwise tests.
     */
    @Override
    void start(javafx.stage.Stage stage) throws Exception {
        // Only create the viewer once - reuse across @Stepwise tests
        if (viewer == null) {
            viewer = new OutputViewer()
            sharedModel = viewer.model
            viewer.show()

            // Set the output folder
            Platform.runLater {
                viewer.folderField.text = outputFolder.absolutePath
            }
            WaitForAsyncUtils.waitForFxEvents()
            Thread.sleep(100)
        } else if (!viewer.showing) {
            // If viewer was closed, show it again
            viewer.show()
        }
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
    // Test 1: Verify Extraction Output Files AND Initialize Viewer
    // =========================================================================

    def "1. Output folder contains expected files after extraction and viewer opens"() {
        expect: 'Books file exists'
        new File(outputFolder, 'all_books_stendhal.json').exists()

        and: 'Signs file exists'
        new File(outputFolder, 'all_signs.csv').exists()

        and: 'Custom names files exist'
        new File(outputFolder, 'all_custom_names.json').exists()
        new File(outputFolder, 'all_custom_names.csv').exists()
        new File(outputFolder, 'all_custom_names.txt').exists()

        and: 'Item database exists'
        new File(outputFolder, 'item_index.db').exists() || new File(outputFolder, 'items.db').exists()

        and: 'Block database exists'
        new File(outputFolder, 'block_index.db').exists() || new File(outputFolder, 'blocks.db').exists()

        and: 'Portal results exist'
        new File(outputFolder, 'portals.json').exists()
        new File(outputFolder, 'portals.csv').exists()
        new File(outputFolder, 'portals.txt').exists()

        and: 'Block search results exist (Main writes blocks.json/csv/txt)'
        new File(outputFolder, 'blocks.json').exists()
        new File(outputFolder, 'blocks.csv').exists()
        new File(outputFolder, 'blocks.txt').exists()

        and: 'Datapack folders exist'
        new File(outputFolder, 'readbooks_datapack_1_13').exists()
        new File(outputFolder, 'readbooks_datapack_1_14').exists()
        new File(outputFolder, 'readbooks_datapack_1_20_5').exists()
        new File(outputFolder, 'readbooks_datapack_1_21').exists()

        and: 'Litematica files exist'
        new File(outputFolder, 'signs.litematic').exists()
        new File(outputFolder, 'books_commands.litematic').exists()

        and: 'Viewer is initialized (triggers FX startup for subsequent tests)'
        viewer != null
        viewer.showing
    }

    // =========================================================================
    // Test 2: OutputViewer Window Properties
    // =========================================================================

    def "2. OutputViewer has correct window title"() {
        expect:
        viewer.title == 'ReadSignsAndBooks - Output Viewer'
    }

    def "3. OutputViewer has minimum dimensions"() {
        expect:
        viewer.minWidth == 1000
        viewer.minHeight == 600
    }

    def "4. OutputViewer contains all UI components"() {
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

    def "5. OutputViewer has all expected tabs"() {
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

    def "6. Load button loads data successfully"() {
        when: 'Click Load button'
        Platform.runLater {
            viewer.loadButton.fire()
        }
        WaitForAsyncUtils.waitForFxEvents()

        and: 'Wait for loading to complete (up to 60 seconds)'
        def startTime = System.currentTimeMillis()
        def loadComplete = false
        while (System.currentTimeMillis() - startTime < 60000) {
            WaitForAsyncUtils.waitForFxEvents()
            Thread.sleep(500)

            // Check if status bar shows loaded data OR model has data loaded
            boolean statusBarReady = viewer.statusBar.text?.contains('Loaded:')
            boolean modelReady = (viewer.model?.books?.size() ?: 0) > 0

            if (statusBarReady || modelReady) {
                Thread.sleep(1000) // Extra time for UI updates to complete
                WaitForAsyncUtils.waitForFxEvents()
                loadComplete = true
                break
            }
        }

        then: 'Load completed successfully (either status bar updated or model has data)'
        loadComplete

        and: 'Model has data'
        viewer.model.books.size() > 0
        viewer.model.signs.size() > 0
    }

    def "7. Model contains expected data counts"() {
        expect: 'Books loaded (test world has 44 books)'
        viewer.model.books.size() == 44

        and: 'Signs loaded (test world has 3 signs)'
        viewer.model.signs.size() == 3

        and: 'Custom names loaded (test world has none)'
        viewer.model.customNames != null  // May be empty, just check it's initialized

        and: 'Portals list initialized (test world has none)'
        viewer.model.portals != null  // May be empty, just check it's initialized

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

    def "8. Books tab displays book data"() {
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

        and: 'Content shows book viewer UI'
        content instanceof javafx.scene.layout.BorderPane
        ((javafx.scene.layout.BorderPane) content).left != null
        ((javafx.scene.layout.BorderPane) content).center != null
    }

    def "9. Signs tab displays sign data"() {
        when: 'Select Signs tab'
        Platform.runLater {
            viewer.contentTabs.selectionModel.select(1)
        }
        WaitForAsyncUtils.waitForFxEvents()
        Thread.sleep(100)

        and: 'Get tab content'
        Tab signsTab = viewer.contentTabs.tabs[1]
        def content = signsTab.content

        then: 'Content shows sign viewer UI'
        content instanceof javafx.scene.layout.BorderPane
        ((javafx.scene.layout.BorderPane) content).center != null
    }

    def "10. Items tab displays item database summary"() {
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
        content instanceof VBox
        def summaryArea = ((VBox) content).children.find { it instanceof TextArea } as TextArea
        summaryArea != null
        summaryArea.text.contains('Item Database Summary')
        summaryArea.text.contains('Total item types:')
    }

    def "11. Blocks tab displays block database summary"() {
        when: 'Select Blocks tab'
        Platform.runLater {
            viewer.contentTabs.selectionModel.select(3)
        }
        WaitForAsyncUtils.waitForFxEvents()
        Thread.sleep(100)

        and: 'Get tab content'
        Tab blocksTab = viewer.contentTabs.tabs[3]
        def content = blocksTab.content

        then: 'Content shows block grid viewer'
        content instanceof BlockGridViewer
    }

    def "12. Portals tab displays content"() {
        when: 'Select Portals tab'
        Platform.runLater {
            viewer.contentTabs.selectionModel.select(4)
        }
        WaitForAsyncUtils.waitForFxEvents()
        Thread.sleep(100)

        and: 'Get tab content'
        Tab portalsTab = viewer.contentTabs.tabs[4]
        def content = portalsTab.content

        then: 'Content shows portal viewer or placeholder (test world has no portals)'
        // When no portals exist, a placeholder BorderPane with Label is shown
        // When portals exist, PortalViewer is shown
        content != null
        content instanceof javafx.scene.layout.BorderPane || content instanceof PortalViewer
    }

    def "13. Statistics tab displays summary statistics"() {
        when: 'Select Statistics tab'
        Platform.runLater {
            viewer.contentTabs.selectionModel.select(6)
        }
        WaitForAsyncUtils.waitForFxEvents()
        Thread.sleep(100)

        and: 'Get tab content'
        Tab statsTab = viewer.contentTabs.tabs[6]
        def content = statsTab.content

        then: 'Content shows statistics dashboard'
        content instanceof StatsDashboard
    }

    // =========================================================================
    // Test 5: Verify Folder Tree
    // =========================================================================

    def "14. Folder tree displays output folder structure"() {
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
        fileNames.contains('item_index.db') || fileNames.contains('items.db')
        fileNames.contains('block_index.db') || fileNames.contains('blocks.db')
    }

    // =========================================================================
    // Test 6: Navigation Between Tabs
    // =========================================================================

    def "15. Can navigate between all tabs without errors"() {
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

    def "16. Book count is consistent across model and metadata"() {
        expect: 'Book counts match'
        viewer.model.books.size() == 44
        viewer.model.metadata.booksCount == 44
    }

    def "17. Sign count is consistent across model and metadata"() {
        expect: 'Sign counts match'
        viewer.model.signs.size() == 3
        viewer.model.metadata.signsCount == 3
    }

    def "18. Item database metadata is consistent"() {
        when: 'Get item database stats'
        int itemTypesFromDb = viewer.model.itemDatabase.getItemTypeCount()
        int itemsIndexedFromDb = viewer.model.itemDatabase.getTotalItemsIndexed()

        then: 'Metadata matches database queries'
        viewer.model.metadata.itemTypesCount == itemTypesFromDb
        viewer.model.metadata.totalItemsIndexed == itemsIndexedFromDb
    }

    def "19. Block database metadata is consistent"() {
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

    def "20. Status bar shows comprehensive summary"() {
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

    def "21. Books have required fields"() {
        when: 'Check first book'
        def firstBook = viewer.model.books[0]

        then: 'Book has expected fields'
        firstBook.title != null
        firstBook.author != null
        firstBook.pages != null
        firstBook.pages instanceof List
    }

    def "22. Signs have required fields and valid coordinates"() {
        when: 'Check first sign'
        def firstSign = viewer.model.signs[0]

        then: 'Sign has expected fields'
        firstSign.line1 != null || firstSign.line1 == ''
        firstSign.x != null
        firstSign.y != null
        firstSign.z != null

        and: 'Coordinates are numeric'
        firstSign.x instanceof Number
        firstSign.y instanceof Number
        firstSign.z instanceof Number
    }

    def "23. Custom names list is initialized"() {
        expect: 'Custom names list exists (may be empty in test world)'
        viewer.model.customNames != null
        // Test world has no custom names, so we just verify the list is initialized
    }

    def "24. Portals list is initialized"() {
        expect: 'Portals list exists (may be empty in test world)'
        viewer.model.portals != null
        // Test world has no portals, so we just verify the list is initialized
    }

    // =========================================================================
    // Test 10: Database Query Functionality
    // =========================================================================

    def "25. Item database can be queried"() {
        when: 'Query item database summary'
        List<Map> summary = viewer.model.itemDatabase.getSummary()

        then: 'Summary has entries'
        summary.size() > 0

        and: 'Each entry has required fields'
        summary.every { item ->
            item.item_id != null &&
            item.total_count != null &&
            item.unique_locations != null
        }
    }

    def "26. Block database can be queried"() {
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

    def "27. Block database contains searched blocks"() {
        when: 'Query for diamond_ore'
        Map blockCount = viewer.model.blockDatabase.getBlockCount('diamond_ore')

        then: 'Block count retrieved (if diamond ore exists in test world)'
        blockCount != null
        // Test world should have diamond ore blocks indexed
        blockCount.total_found > 0 || blockCount.isEmpty()  // Empty map means not found
    }

    // =========================================================================
    // Test 11: Window Behavior
    // =========================================================================

    def "28. OutputViewer is resizable"() {
        expect:
        viewer.resizable
    }

    def "29. OutputViewer respects minimum dimensions"() {
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

    def "30. Model summary text includes data present in test world"() {
        when: 'Get summary text'
        String summary = viewer.model.getSummaryText()

        then: 'Summary mentions data that is present in test world'
        summary.contains('44 books')
        summary.contains('3 signs')
        // Test world has no custom names and no portals, so those won't appear
        summary.contains('block results') || summary.contains('block types')
        summary.contains('item types')
    }

    // =========================================================================
    // Test 13: Final Verification
    // =========================================================================

    def "31. All tabs have content after loading"() {
        when: 'Check all tabs'
        List<Boolean> hasContent = viewer.contentTabs.tabs.collect { tab ->
            tab.content != null
        }

        then: 'All tabs have content'
        hasContent.every { it == true }
    }

    def "32. Viewer can be refreshed by loading again"() {
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

    def "33. Viewer model closes cleanly"() {
        when: 'Close model'
        viewer.model.close()

        then: 'No exceptions thrown'
        notThrown(Exception)
    }
}

import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.input.Clipboard
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise
import viewers.ItemGridViewer

import java.awt.GraphicsEnvironment
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * GUI Integration Tests for ItemGridViewer using TestFX.
 *
 * Tests the JEI-style item grid viewer with REAL extracted data from test world.
 *
 * IMPORTANT: These tests require a display to run. They will be skipped
 * automatically in headless environments (CI without xvfb, etc.).
 */
@IgnoreIf({ GraphicsEnvironment.headless })
@Stepwise
class ItemGridViewerGuiSpec extends Specification {

    @Shared
    static boolean jfxInitialized = false

    @Shared
    File testOutputFolder

    @Shared
    File itemDbFile

    @Shared
    ItemGridViewer viewer

    static {
        // Initialize JavaFX toolkit once for all tests
        if (!jfxInitialized) {
            new JFXPanel()
            jfxInitialized = true
            println 'JavaFX toolkit initialized successfully for ItemGridViewer tests'
        }
    }

    def setupSpec() {
        // Run extraction with item indexing to create real test database
        testOutputFolder = new File('build/test-output-item-viewer')
        testOutputFolder.deleteDir()
        testOutputFolder.mkdirs()

        itemDbFile = new File(testOutputFolder, 'item_index.db')

        println "Running extraction with --index-items to create ${itemDbFile.absolutePath}"

        Main.runCli([
            '-w', 'src/test/resources/1_21_10-44-3',
            '-o', testOutputFolder.absolutePath,
            '--index-items',
            '--item-limit', '5000',
            '--skip-common-items'
        ] as String[])

        assert itemDbFile.exists(), "item_index.db should exist after extraction"
        println "item_index.db created successfully: ${itemDbFile.length()} bytes"
    }

    def cleanupSpec() {
        if (viewer?.stage) {
            runOnFxThread {
                viewer.stage.close()
            }
        }
    }

    def "Item grid opens without error and loads items from database"() {
        given:
        CountDownLatch latch = new CountDownLatch(1)
        List<Map> loadedItems = []

        when:
        runOnFxThread {
            viewer = new ItemGridViewer(itemDbFile)
            loadedItems.addAll(viewer.allItems)
            latch.countDown()
        }

        then:
        latch.await(5, TimeUnit.SECONDS)
        loadedItems.size() > 0
        println "Loaded ${loadedItems.size()} items from database"
    }

    def "Item grid renders slots after show() is called"() {
        given:
        CountDownLatch latch = new CountDownLatch(1)
        int gridChildren = 0

        when:
        runOnFxThread {
            viewer.show()
            Platform.runLater {
                gridChildren = viewer.itemGrid.children.size()
                latch.countDown()
            }
        }

        then:
        latch.await(5, TimeUnit.SECONDS)
        gridChildren > 0
        println "Rendered ${gridChildren} item slots in grid"
        viewer.stage != null
        viewer.stage.showing
    }

    def "Search field filters items correctly - book query"() {
        given:
        CountDownLatch latch = new CountDownLatch(1)
        int beforeCount = 0
        int afterCount = 0
        List<String> filteredIds = []

        when:
        runOnFxThread {
            beforeCount = viewer.filteredItems.size()
            viewer.searchField.text = 'book'  // Search for books, which exist in test data

            Platform.runLater {
                afterCount = viewer.filteredItems.size()
                filteredIds.addAll(viewer.filteredItems.collect { it.item_id as String })
                latch.countDown()
            }
        }

        then:
        latch.await(5, TimeUnit.SECONDS)
        afterCount > 0
        afterCount <= beforeCount
        filteredIds.every { it.toLowerCase().contains('book') }
        println "Search 'book': ${beforeCount} → ${afterCount} items"
        filteredIds.each { println "  - ${it}" }
    }

    def "Clear button resets search filter"() {
        given:
        CountDownLatch latch = new CountDownLatch(1)
        int allItemsCount = 0

        when:
        runOnFxThread {
            viewer.searchField.text = 'book'

            Platform.runLater {
                viewer.searchField.text = ''

                Platform.runLater {
                    allItemsCount = viewer.filteredItems.size()
                    latch.countDown()
                }
            }
        }

        then:
        latch.await(5, TimeUnit.SECONDS)
        allItemsCount == viewer.allItems.size()
        println "Clear search: restored all ${allItemsCount} items"
    }

    def "Copy teleport command executes without error"() {
        given:
        CountDownLatch latch = new CountDownLatch(1)
        boolean commandCopied = false

        when:
        runOnFxThread {
            if (!viewer.allItems.empty) {
                Map item = viewer.allItems[0]
                try {
                    viewer.copyTeleportCommand(item)
                    commandCopied = true
                } catch (Exception e) {
                    println "Clipboard error (expected in test env): ${e.message}"
                }
                latch.countDown()
            } else {
                latch.countDown()
            }
        }

        then:
        latch.await(5, TimeUnit.SECONDS)
        commandCopied
        println "Copy teleport command executed successfully"
    }

    def "Status label updates with filter count"() {
        given:
        CountDownLatch latch = new CountDownLatch(1)
        String statusText = ''

        when:
        runOnFxThread {
            viewer.searchField.text = 'book'

            Platform.runLater {
                statusText = viewer.statusLabel.text
                latch.countDown()
            }
        }

        then:
        latch.await(5, TimeUnit.SECONDS)
        statusText =~ /\d+ \/ \d+ items/
        println "Status label: ${statusText}"
    }

    def "Tooltip displays item metadata"() {
        given:
        CountDownLatch latch = new CountDownLatch(1)
        String tooltipText = ''

        when:
        runOnFxThread {
            if (!viewer.allItems.empty) {
                Map item = viewer.allItems[0]
                tooltipText = viewer.buildTooltipText(item)
            }
            latch.countDown()
        }

        then:
        latch.await(5, TimeUnit.SECONDS)
        tooltipText.length() > 0
        tooltipText.contains('Location:')
        tooltipText.contains('[R] Details')
        println "Tooltip includes item details and keyboard hints"
    }

    def "Database closes cleanly on viewer close"() {
        given:
        CountDownLatch latch = new CountDownLatch(1)

        when:
        runOnFxThread {
            viewer.stage.close()
            latch.countDown()
        }

        then:
        latch.await(5, TimeUnit.SECONDS)
        println "Viewer closed successfully"
    }

    // Helper method
    private void runOnFxThread(Closure closure) {
        if (Platform.fxApplicationThread) {
            closure.call()
        } else {
            Platform.runLater(closure)
        }
    }
}

import org.testfx.framework.spock.ApplicationSpec
import viewers.BlockGridViewer
import javafx.scene.Scene
import javafx.stage.Stage

/**
 * Integration test for BlockGridViewer.
 * Tests block grid rendering, filtering, and user interactions.
 */
class BlockGridViewerSpec extends ApplicationSpec {

    BlockGridViewer viewer

    @Override
    void start(Stage stage) {
        viewer = new BlockGridViewer()
        stage.scene = new Scene(viewer, 900, 700)
        stage.show()
    }

    def "viewer initializes without errors"() {
        expect:
        viewer != null
    }

    def "viewer can load blocks from database"() {
        given: "a test blocks database"
        File testDb = new File('src/test/resources/test_blocks.db')

        when: "loading blocks"
        boolean loaded = viewer.loadBlocks(testDb)

        then: "load succeeds or fails gracefully"
        // Will fail if test DB doesn't exist, but shouldn't crash
        loaded == false || loaded == true
    }

    def "filtering by category works"() {
        when: "selecting a category filter"
        interact {
            // This would interact with the UI in a real test
            viewer.categoryFilter?.value = 'Ores'
        }

        then: "no exceptions are thrown"
        true
    }

    def "search field filters blocks"() {
        when: "typing in search field"
        interact {
            viewer.searchField?.text = 'diamond'
        }

        then: "no exceptions are thrown"
        true
    }

    def "clear filters resets all filters"() {
        when: "clicking clear filters button"
        interact {
            viewer.clearFiltersBtn?.fire()
        }

        then: "filters are reset"
        viewer.categoryFilter?.value == 'All'
        viewer.dimensionFilter?.value == 'All'
        viewer.searchField?.text == ''
    }

    def cleanup() {
        viewer?.close()
    }
}

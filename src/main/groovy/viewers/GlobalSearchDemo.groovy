package viewers

import javafx.application.Application
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.Alert
import javafx.scene.layout.BorderPane
import javafx.scene.layout.VBox
import javafx.stage.Stage
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Standalone demo application for GlobalSearch component.
 *
 * Usage:
 *   gradlew run -PmainClass=viewers.GlobalSearchDemo
 *
 * This demo creates a mock data model and demonstrates all GlobalSearch features:
 * - Searching across all data types
 * - Relevance ranking
 * - Grouped results
 * - Keyboard shortcuts (Ctrl+F)
 * - Result navigation callbacks
 */
class GlobalSearchDemo extends Application {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalSearchDemo)

    private GlobalSearch globalSearch
    private OutputViewerModel model

    @Override
    void start(Stage primaryStage) {
        primaryStage.title = 'Global Search Demo'

        // Create mock data model
        model = createMockModel()

        // Create global search component
        globalSearch = new GlobalSearch(model, { result ->
            handleResultSelection(result)
        })

        // Layout
        BorderPane root = new BorderPane()
        root.padding = new Insets(20)

        VBox container = new VBox(10)
        container.children.add(globalSearch)

        root.center = container

        Scene scene = new Scene(root, 600, 700)
        primaryStage.scene = scene
        primaryStage.show()

        // Focus search field on startup
        globalSearch.focusSearch()

        // Show welcome message
        showInfo('Welcome to Global Search Demo',
                "Try searching for:\n" +
                "- 'diamond' (finds books, signs, items)\n" +
                "- 'steve' (finds books by author)\n" +
                "- 'excalibur' (finds custom named item)\n" +
                "- 'nether' (finds portals, signs)\n" +
                "- 'spawn' (finds signs)\n\n" +
                "Keyboard shortcuts:\n" +
                "- Ctrl+F: Focus search\n" +
                "- Arrow keys: Navigate results\n" +
                "- Enter: Select result\n" +
                "- Esc: Clear search")
    }

    /**
     * Handle when user selects a search result.
     */
    private void handleResultSelection(GlobalSearch.SearchResult result) {
        String message = buildResultMessage(result)
        showInfo("Selected: ${result.displayText}", message)
        LOGGER.info("Selected result: ${result.type} - ${result.displayText}")
    }

    /**
     * Build detailed message for selected result.
     */
    private String buildResultMessage(GlobalSearch.SearchResult result) {
        StringBuilder sb = new StringBuilder()

        sb.append("Type: ${result.type}\n")
        sb.append("Score: ${result.score}\n\n")

        switch (result.type) {
            case 'book':
                Map book = result.data as Map
                sb.append("Title: ${book.title}\n")
                sb.append("Author: ${book.author}\n")
                sb.append("Pages: ${book.pages?.size()}\n")
                sb.append("Location: ${book.location}\n")
                break

            case 'sign':
                Map sign = result.data as Map
                sb.append("Lines:\n")
                ['line1', 'line2', 'line3', 'line4'].each { line ->
                    if (sign[line]) {
                        sb.append("  ${sign[line]}\n")
                    }
                }
                sb.append("Location: (${sign.x}, ${sign.y}, ${sign.z})\n")
                sb.append("Dimension: ${sign.dimension}\n")
                break

            case 'custom_name':
                Map item = result.data as Map
                sb.append("Name: ${item.name}\n")
                sb.append("Type: ${item.type}\n")
                sb.append("Item ID: ${item.item_id}\n")
                sb.append("Location: (${item.x}, ${item.y}, ${item.z})\n")
                break

            case 'portal':
                Map portal = result.data as Map
                sb.append("Portal ID: ${portal.portal_id}\n")
                sb.append("Dimension: ${portal.dimension}\n")
                sb.append("Blocks: ${portal.block_count}\n")
                sb.append("Center: (${portal.center_x}, ${portal.center_y}, ${portal.center_z})\n")
                break

            case 'item':
                Map item = result.data as Map
                sb.append("Item ID: ${item.item_id}\n")
                sb.append("Count: ${item.count}\n")
                if (item.custom_name) {
                    sb.append("Custom Name: ${item.custom_name}\n")
                }
                sb.append("Location: (${item.x}, ${item.y}, ${item.z})\n")
                break
        }

        return sb.toString()
    }

    /**
     * Create a mock data model with sample data for testing.
     */
    private OutputViewerModel createMockModel() {
        OutputViewerModel model = new OutputViewerModel()

        // Books
        model.books = [
            [
                title: 'Diamond Mining Guide',
                author: 'Steve',
                pages: [
                    'Welcome to diamond mining!',
                    'Dig down to Y=-59 for best results',
                    'Always bring iron pickaxe or better',
                    'Watch out for lava!'
                ],
                location: 'chest at (100, 64, 200)'
            ],
            [
                title: 'My Diary - Day 12',
                author: 'Alex',
                pages: [
                    'Day 12: I found diamonds today!',
                    'Built a diamond pickaxe',
                    'Started mining obsidian for nether portal'
                ],
                location: 'player inventory'
            ],
            [
                title: 'Redstone Tutorial',
                author: 'Steve',
                pages: [
                    'Basic redstone circuits',
                    'How to make a piston door',
                    'Advanced contraptions'
                ],
                location: 'lectern at (50, 70, 150)'
            ],
            [
                title: 'Brewing Guide',
                author: 'Wizard',
                pages: [
                    'Potion recipes',
                    'Nether wart farming',
                    'Advanced brewing stand usage'
                ],
                location: 'brewing stand at (75, 65, 125)'
            ],
            [
                title: 'Spawn Rules',
                author: 'Admin',
                pages: [
                    'Server spawn rules',
                    'No griefing!',
                    'Respect other players'
                ],
                location: 'spawn chest at (0, 64, 0)'
            ]
        ]

        // Signs
        model.signs = [
            [
                line1: 'Spawn Point',
                line2: 'This way →',
                line3: 'Welcome!',
                line4: '',
                x: 0,
                y: 64,
                z: 0,
                dimension: 'overworld'
            ],
            [
                line1: 'Diamond Mine',
                line2: 'Level -59',
                line3: 'Danger!',
                line4: 'Bring torches',
                x: 100,
                y: -59,
                z: 200,
                dimension: 'overworld'
            ],
            [
                line1: 'Nether Portal',
                line2: 'To overworld',
                line3: '',
                line4: '',
                x: 12,
                y: 70,
                z: 34,
                dimension: 'nether'
            ],
            [
                line1: 'Storage Room',
                line2: '← This way',
                line3: 'Organized!',
                line4: '',
                x: 50,
                y: 64,
                z: 100,
                dimension: 'overworld'
            ],
            [
                line1: 'Villager',
                line2: 'Trading Hall',
                line3: 'Good prices!',
                line4: '',
                x: 25,
                y: 64,
                z: 75,
                dimension: 'overworld'
            ]
        ]

        // Custom Names
        model.customNames = [
            [
                name: 'Excalibur',
                type: 'ITEM',
                item_id: 'minecraft:diamond_sword',
                x: 50,
                y: 64,
                z: 100,
                dimension: 'overworld'
            ],
            [
                name: 'Steve\'s House',
                type: 'ENTITY',
                item_id: 'minecraft:armor_stand',
                x: 0,
                y: 65,
                z: 0,
                dimension: 'overworld'
            ],
            [
                name: 'Mega Pickaxe',
                type: 'ITEM',
                item_id: 'minecraft:netherite_pickaxe',
                x: 100,
                y: -59,
                z: 200,
                dimension: 'overworld'
            ],
            [
                name: 'Alex\'s Pet Horse',
                type: 'ENTITY',
                item_id: 'minecraft:horse',
                x: 150,
                y: 64,
                z: 250,
                dimension: 'overworld'
            ]
        ]

        // Portals
        model.portals = [
            [
                portal_id: 1,
                dimension: 'overworld',
                block_count: 10,
                center_x: 100,
                center_y: 64,
                center_z: 200,
                min_x: 99,
                max_x: 101,
                min_y: 63,
                max_y: 67,
                min_z: 199,
                max_z: 201
            ],
            [
                portal_id: 2,
                dimension: 'nether',
                block_count: 10,
                center_x: 12,
                center_y: 70,
                center_z: 25,
                min_x: 11,
                max_x: 13,
                min_y: 69,
                max_y: 73,
                min_z: 24,
                max_z: 26
            ],
            [
                portal_id: 3,
                dimension: 'overworld',
                block_count: 12,
                center_x: 500,
                center_y: 65,
                center_z: 600,
                min_x: 499,
                max_x: 501,
                min_y: 64,
                max_y: 68,
                min_z: 599,
                max_z: 601
            ]
        ]

        // Item database is null (not implemented in demo)
        model.itemDatabase = null
        model.blockDatabase = null

        LOGGER.info("Created mock model: ${model.books.size()} books, ${model.signs.size()} signs, " +
                "${model.customNames.size()} custom names, ${model.portals.size()} portals")

        return model
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION)
        alert.title = title
        alert.headerText = null
        alert.contentText = message
        alert.showAndWait()
    }

    static void main(String[] args) {
        launch(GlobalSearchDemo, args)
    }
}

package viewers

import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.control.Alert
import javafx.scene.control.ButtonType
import javafx.stage.FileChooser
import javafx.stage.Stage

/**
 * Standalone application to test the CustomNamesViewer.
 *
 * Usage: java -cp ReadSignsAndBooks.jar viewers.CustomNamesViewerApp
 */
class CustomNamesViewerApp extends Application {

    @Override
    void start(Stage primaryStage) {
        primaryStage.title = 'Custom Names Viewer'

        // Create viewer
        CustomNamesViewer viewer = new CustomNamesViewer()

        // Create scene
        Scene scene = new Scene(viewer, 1200, 700)
        primaryStage.scene = scene

        // Apply theme (basic styling)
        applyBasicStyling(scene)

        // Show file chooser on startup
        primaryStage.onShown = {
            promptForDataFile(viewer)
        }

        primaryStage.show()
    }

    private void promptForDataFile(CustomNamesViewer viewer) {
        FileChooser fileChooser = new FileChooser()
        fileChooser.title = 'Select Custom Names Data File'
        fileChooser.extensionFilters.addAll(
            new FileChooser.ExtensionFilter('JSON Files', '*.json'),
            new FileChooser.ExtensionFilter('CSV Files', '*.csv'),
            new FileChooser.ExtensionFilter('All Files', '*.*')
        )

        File selectedFile = fileChooser.showOpenDialog(viewer.scene?.window)

        if (selectedFile) {
            if (selectedFile.name.endsWith('.json')) {
                viewer.loadData(selectedFile)
            } else if (selectedFile.name.endsWith('.csv')) {
                viewer.loadFromCsv(selectedFile)
            } else {
                // Try JSON first
                try {
                    viewer.loadData(selectedFile)
                } catch (Exception e) {
                    Alert alert = new Alert(Alert.AlertType.ERROR,
                        "Failed to load data: ${e.message}",
                        ButtonType.OK)
                    alert.showAndWait()
                }
            }
        } else {
            // Load sample data for testing
            loadSampleData(viewer)
        }
    }

    private void loadSampleData(CustomNamesViewer viewer) {
        // Create sample data for demonstration
        List<Map> sampleData = [
            [
                type: 'item',
                itemOrEntityId: 'minecraft:diamond_sword',
                customName: '§cExcalibur',
                x: 100,
                y: 64,
                z: 200,
                location: 'Chest in Stronghold'
            ],
            [
                type: 'item',
                itemOrEntityId: 'minecraft:diamond_pickaxe',
                customName: '§9Miner\'s Best Friend',
                x: -45,
                y: 12,
                z: 350,
                location: 'Nether fortress chest'
            ],
            [
                type: 'item',
                itemOrEntityId: 'minecraft:bow',
                customName: '§aHunter\'s Pride',
                x: 500,
                y: 70,
                z: -300,
                location: 'Player inventory (Steve)'
            ],
            [
                type: 'entity',
                itemOrEntityId: 'minecraft:armor_stand',
                customName: 'Shop Display',
                x: 0,
                y: 64,
                z: 0,
                location: 'Spawn area'
            ],
            [
                type: 'item',
                itemOrEntityId: 'minecraft:netherite_helmet',
                customName: '§5Crown of the Nether',
                x: 1000,
                y: 32,
                z: 1000,
                location: 'Ender chest'
            ],
            [
                type: 'item',
                itemOrEntityId: 'minecraft:diamond_sword',
                customName: '§cExcalibur',
                x: 150,
                y: 64,
                z: 250,
                location: 'Chest in Village'
            ],
            [
                type: 'entity',
                itemOrEntityId: 'minecraft:horse',
                customName: 'Lightning',
                x: 300,
                y: 65,
                z: 400,
                location: 'Stable near spawn'
            ],
            [
                type: 'item',
                itemOrEntityId: 'minecraft:elytra',
                customName: '§bWings of Freedom',
                x: 2000,
                y: 120,
                z: 2000,
                location: 'End city loot chest'
            ],
            [
                type: 'item',
                itemOrEntityId: 'minecraft:trident',
                customName: '§3Poseidon\'s Fury',
                x: -200,
                y: 40,
                z: -500,
                location: 'Ocean monument treasure'
            ],
            [
                type: 'item',
                itemOrEntityId: 'minecraft:golden_apple',
                customName: 'Golden Delicious',
                x: 50,
                y: 64,
                z: 50,
                location: 'Shulker box in base'
            ]
        ]

        viewer.loadFromList(sampleData)
    }

    private void applyBasicStyling(Scene scene) {
        // Basic styling - matches the main GUI theme preference
        String css = '''
            .root {
                -fx-font-family: "Segoe UI", "Helvetica", "Arial", sans-serif;
                -fx-font-size: 12px;
            }

            .table-view {
                -fx-background-color: -fx-control-inner-background;
            }

            .table-row-cell:selected {
                -fx-background-color: -fx-selection-bar;
                -fx-text-fill: -fx-selection-bar-text;
            }

            .text-field {
                -fx-prompt-text-fill: derive(-fx-control-inner-background, -30%);
            }
        '''

        scene.root.style = css
    }

    static void main(String[] args) {
        launch(args)
    }
}

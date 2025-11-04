import javafx.application.Application
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.stage.DirectoryChooser
import javafx.stage.Stage
import java.awt.Desktop

/**
 * Modern GUI for ReadSignsAndBooks using pure JavaFX
 * Minimal code with Groovy's concise syntax
 */
class GUI extends Application {

    static TextField worldPathField
    static TextField outputPathField
    static CheckBox removeFormattingCheckBox
    static TextArea logArea
    static Label statusLabel
    static File worldDir
    static File outputFolder
    static File actualOutputFolder

    void start(Stage stage) {
        stage.title = 'ReadSignsAndBooks Extractor'

        // Set up GUI log handler
        GuiLogAppender.setLogHandler { message ->
            logArea.appendText(message)
        }

        // Clean up on close
        stage.onCloseRequest = {
            GuiLogAppender.clearLogHandler()
        }

        // Create menu bar
        def menuBar = createMenuBar()

        // Main content layout
        def contentRoot = new VBox(15)
        contentRoot.padding = new Insets(20)

        // Title
        def title = new Label('Minecraft Book & Sign Extractor')
        title.style = '-fx-font-size: 20px; -fx-font-weight: bold;'

        // World directory selection
        def worldBox = new HBox(10)
        worldBox.alignment = Pos.CENTER_LEFT
        worldPathField = new TextField()
        worldPathField.promptText = 'Select Minecraft world folder...'
        worldPathField.editable = false
        HBox.setHgrow(worldPathField, Priority.ALWAYS)  // Make it grow horizontally
        def worldBtn = new Button('Browse...')
        worldBtn.onAction = { selectWorldDirectory(stage) }
        worldBox.children.addAll(new Label('World Directory:').with { it.minWidth = 120; it }, worldPathField, worldBtn)

        // Output folder selection (optional)
        def outputBox = new HBox(10)
        outputBox.alignment = Pos.CENTER_LEFT
        outputPathField = new TextField()
        outputPathField.editable = false
        HBox.setHgrow(outputPathField, Priority.ALWAYS)  // Make it grow horizontally
        updateOutputFolderPrompt()  // Set initial prompt text
        def outputBtn = new Button('Browse...')
        outputBtn.onAction = { selectOutputFolder(stage) }
        outputBox.children.addAll(new Label('Output Folder:').with { it.minWidth = 120; it }, outputPathField, outputBtn)

        // Remove formatting checkbox
        def formattingBox = new HBox(10)
        formattingBox.alignment = Pos.CENTER_LEFT
        removeFormattingCheckBox = new CheckBox('Remove Minecraft formatting codes (§ codes)')
        removeFormattingCheckBox.selected = false
        formattingBox.children.addAll(new Label('Options:').with { it.minWidth = 120; it }, removeFormattingCheckBox)

        // Action buttons (left-aligned)
        def btnBox = new HBox(15)
        btnBox.alignment = Pos.CENTER_LEFT
        def extractBtn = new Button('Extract')
        extractBtn.minWidth = 100
        extractBtn.style = '-fx-font-size: 14px; -fx-background-color: #4CAF50; -fx-text-fill: white;'
        extractBtn.onAction = { runExtraction() }
        def openFolderBtn = new Button('Open Output Folder')
        openFolderBtn.minWidth = 130
        openFolderBtn.onAction = { openOutputFolder() }
        def clearBtn = new Button('Clear Log')
        clearBtn.minWidth = 100
        clearBtn.onAction = { logArea.text = '' }
        def exitBtn = new Button('Exit')
        exitBtn.minWidth = 100
        exitBtn.onAction = { Platform.exit() }
        btnBox.children.addAll(extractBtn, openFolderBtn, clearBtn, exitBtn)

        // Status label
        statusLabel = new Label('Ready')
        statusLabel.style = '-fx-font-style: italic;'

        // Log area (grows with window)
        logArea = new TextArea()
        logArea.editable = false
        logArea.wrapText = true
        logArea.style = '-fx-font-family: "Courier New"; -fx-font-size: 11px;'
        VBox.setVgrow(logArea, Priority.ALWAYS)  // Make it grow vertically

        // Assemble content layout
        contentRoot.children.addAll(
            title,
            new Separator(),
            worldBox,
            outputBox,
            formattingBox,
            new Separator(),
            btnBox,
            new Separator(),
            statusLabel,
            new Label('Extraction Log:').with { it.style = '-fx-font-weight: bold;'; it },
            logArea
        )

        // Main layout with menu bar
        def root = new BorderPane()
        root.top = menuBar
        root.center = contentRoot

        stage.scene = new Scene(root, 720, 550)
        stage.minWidth = 700
        stage.minHeight = 500
        stage.show()
    }

    MenuBar createMenuBar() {
        def menuBar = new MenuBar()

        // Help menu
        def helpMenu = new Menu('Help')

        def githubItem = new MenuItem('View on GitHub')
        githubItem.onAction = {
            try {
                def desktop = Desktop.desktop
                if (desktop && desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(new URI('https://github.com/Vibed/ReadSignsAndBooks.jar'))
                } else {
                    showAlert('GitHub', 'Project URL:\nhttps://github.com/Vibed/ReadSignsAndBooks.jar', Alert.AlertType.INFORMATION)
                }
            } catch (Exception e) {
                showAlert('Error', "Could not open browser: ${e.message}", Alert.AlertType.ERROR)
            }
        }

        def aboutItem = new MenuItem('About')
        aboutItem.onAction = {
            showAlert('About',
                'ReadSignsAndBooks v1.0.0\n\n' +
                'Attribution:\n' +
                'This project would not exist without the code shared in 2020\n' +
                'by Matt (/u/worldseed) in the r/MinecraftDataMining Discord server,\n' +
                'and it would be more than one file if not for the Querz NBT Library.\n\n' +
                'All changes are just vibe coded with help of Claude 4.5.',
                Alert.AlertType.INFORMATION)
        }

        def separator = new SeparatorMenuItem()

        helpMenu.items.addAll(githubItem, separator, aboutItem)
        menuBar.menus.add(helpMenu)

        return menuBar
    }

    void selectWorldDirectory(Stage stage) {
        def chooser = new DirectoryChooser(title: 'Select Minecraft World Directory')
        worldDir = chooser.showDialog(stage)
        if (worldDir) {
            worldPathField.text = worldDir.absolutePath
            updateOutputFolderPrompt()
        }
    }

    void selectOutputFolder(Stage stage) {
        def chooser = new DirectoryChooser(title: 'Select Output Folder')
        outputFolder = chooser.showDialog(stage)
        if (outputFolder) {
            outputPathField.text = outputFolder.absolutePath
            updateOutputFolderPrompt()
        }
    }

    void updateOutputFolderPrompt() {
        def dateStamp = new java.text.SimpleDateFormat('yyyy-MM-dd').format(new Date())
        def defaultPath

        if (worldDir) {
            defaultPath = new File(worldDir, "ReadBooks${File.separator}${dateStamp}").absolutePath
            actualOutputFolder = new File(worldDir, "ReadBooks${File.separator}${dateStamp}")
        } else {
            defaultPath = new File(System.getProperty('user.dir'), "ReadBooks${File.separator}${dateStamp}").absolutePath
            actualOutputFolder = new File(System.getProperty('user.dir'), "ReadBooks${File.separator}${dateStamp}")
        }

        // Update output folder field prompt
        if (outputFolder) {
            actualOutputFolder = outputFolder
        } else {
            outputPathField.promptText = "Optional: Custom output folder (default: ${defaultPath})"
        }
    }

    void openOutputFolder() {
        if (!actualOutputFolder || !actualOutputFolder.exists()) {
            showAlert('Error', 'Output folder does not exist yet. Run extraction first.', Alert.AlertType.WARNING)
            return
        }

        try {
            def desktop = Desktop.desktop
            if (desktop && desktop.isSupported(Desktop.Action.OPEN)) {
                desktop.open(actualOutputFolder)
            } else {
                showAlert('Error', 'Cannot open folder: Desktop operations not supported', Alert.AlertType.ERROR)
            }
        } catch (Exception e) {
            showAlert('Error', "Could not open folder: ${e.message}", Alert.AlertType.ERROR)
        }
    }

    void runExtraction() {
        statusLabel.text = 'Extracting...'

        // Run extraction in background thread
        Thread.start {
            try {
                // Prepare arguments - let CLI handle defaults if not set
                def args = []
                if (worldDir) {
                    args += ['-w', worldDir.absolutePath]
                }
                if (outputFolder) {
                    args += ['-o', outputFolder.absolutePath]
                }
                if (removeFormattingCheckBox.selected) {
                    args += ['--remove-formatting']
                }

                // Call Main CLI directly (avoid double launch)
                // Logging will automatically appear in GUI via GuiLogAppender
                Main.runCli(args as String[])

                Platform.runLater {
                    statusLabel.text = "Complete! ${Main.bookHashes.size()} books, ${Main.signHashes.size()} signs"
                    showAlert('Success', "Extraction complete!\n\nBooks: ${Main.bookHashes.size()}\nSigns: ${Main.signHashes.size()}", Alert.AlertType.INFORMATION)
                }

            } catch (Exception e) {
                Platform.runLater {
                    statusLabel.text = 'Error occurred'
                    showAlert('Error', "Extraction failed:\n${e.message}", Alert.AlertType.ERROR)
                }
            }
        }
    }

    static void showAlert(String title, String message, Alert.AlertType type) {
        Platform.runLater {
            def alert = new Alert(type)
            alert.title = title
            alert.headerText = null
            alert.contentText = message
            alert.showAndWait()
        }
    }

    static void main(String[] args) {
        launch(GUI, args)
    }
}

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
import javafx.animation.Timeline
import javafx.animation.KeyFrame
import javafx.util.Duration
import javafx.scene.input.TransferMode
import javafx.scene.input.DragEvent
import javafx.scene.input.Dragboard
import com.jthemedetecor.OsThemeDetector
import atlantafx.base.theme.PrimerLight
import atlantafx.base.theme.PrimerDark

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
    static Button extractBtn
    static Timeline elapsedTimeTimer
    static long extractionStartTime

    void start(Stage stage) {
        stage.title = 'ReadSignsAndBooks Extractor'

        // Apply theme before creating UI (AtlantaFX)
        applyTheme()

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
        worldPathField.promptText = 'Select Minecraft world folder or drag & drop here...'
        worldPathField.editable = false
        HBox.setHgrow(worldPathField, Priority.ALWAYS)  // Make it grow horizontally
        setupDragAndDrop(worldPathField, true)  // Enable drag-and-drop for world folder
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
        setupDragAndDrop(outputPathField, false)  // Enable drag-and-drop for output folder
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
        extractBtn = new Button('Extract')
        extractBtn.minWidth = 150
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
                'All changes are just vibe coded with help of Claude 4.5.\n\n' +
                '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n' +
                'DISCLAIMER:\n' +
                'This software is NOT affiliated with, endorsed by, or associated with\n' +
                'Mojang Studios, Microsoft Corporation, or any of their subsidiaries.\n\n' +
                'This program is provided "AS IS" without warranty of any kind.\n' +
                'Although the program aims to only write to its destination/output folder, ' +
                'neither the program nor the author is responsible for any corruption of world files,\n' +
                'data loss, or any other damages that may occur from using this software.\n\n' +
                'Use at your own risk. Always backup your worlds before processing.',
                Alert.AlertType.INFORMATION)
        }

        def separator = new SeparatorMenuItem()

        def licensesItem = new MenuItem('Third-Party Licenses')
        licensesItem.onAction = {
            showLicensesDialog()
        }

        helpMenu.items.addAll(githubItem, separator, aboutItem, licensesItem)
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
        // Disable extract button and start timer
        extractBtn.disable = true
        extractionStartTime = System.currentTimeMillis()
        statusLabel.text = 'Extracting...'

        // Start elapsed time timer (updates button text every second)
        elapsedTimeTimer = new Timeline(new KeyFrame(Duration.seconds(1), { event ->
            def elapsed = (System.currentTimeMillis() - extractionStartTime) / 1000
            def minutes = (elapsed / 60) as int
            def seconds = (elapsed % 60) as int
            Platform.runLater {
                extractBtn.text = String.format('Extracting... %02d:%02d', minutes, seconds)
            }
        }))
        elapsedTimeTimer.cycleCount = Timeline.INDEFINITE
        elapsedTimeTimer.play()

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
                    // Stop timer and re-enable button
                    elapsedTimeTimer?.stop()
                    extractBtn.disable = false
                    extractBtn.text = 'Extract'

                    def totalElapsed = (System.currentTimeMillis() - extractionStartTime) / 1000
                    def minutes = (totalElapsed / 60) as int
                    def seconds = (totalElapsed % 60) as int
                    statusLabel.text = String.format("Complete! %d books, %d signs (took %02d:%02d)",
                        Main.bookHashes.size(), Main.signHashes.size(), minutes, seconds)
                    showAlert('Success', "Extraction complete!\n\nBooks: ${Main.bookHashes.size()}\nSigns: ${Main.signHashes.size()}\nTime: ${minutes}m ${seconds}s", Alert.AlertType.INFORMATION)
                }

            } catch (Exception e) {
                Platform.runLater {
                    // Stop timer and re-enable button
                    elapsedTimeTimer?.stop()
                    extractBtn.disable = false
                    extractBtn.text = 'Extract'

                    statusLabel.text = 'Error occurred'
                    showAlert('Error', "Extraction failed:\n${e.message}", Alert.AlertType.ERROR)
                }
            }
        }
    }

    void setupDragAndDrop(TextField textField, boolean isWorldFolder) {
        // Handle drag over event - show that drop is accepted
        textField.onDragOver = { DragEvent event ->
            if (event.dragboard.hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY)
            }
            event.consume()
        }

        // Visual feedback when drag enters
        textField.onDragEntered = { DragEvent event ->
            if (event.dragboard.hasFiles()) {
                textField.style = '-fx-border-color: #4CAF50; -fx-border-width: 2px;'  // Green border highlight
            }
            event.consume()
        }

        // Remove visual feedback when drag exits
        textField.onDragExited = { DragEvent event ->
            textField.style = ''  // Reset to default
            event.consume()
        }

        // Handle the actual drop
        textField.onDragDropped = { DragEvent event ->
            Dragboard db = event.dragboard
            boolean success = false

            if (db.hasFiles()) {
                def files = db.files
                if (files && files.size() > 0) {
                    def droppedFile = files[0]  // Take first file/folder

                    if (droppedFile.isDirectory()) {
                        if (isWorldFolder) {
                            worldDir = droppedFile
                            worldPathField.text = droppedFile.absolutePath
                            updateOutputFolderPrompt()
                        } else {
                            outputFolder = droppedFile
                            outputPathField.text = droppedFile.absolutePath
                            updateOutputFolderPrompt()
                        }
                        success = true
                    } else {
                        // User dropped a file instead of a folder
                        Platform.runLater {
                            showAlert('Invalid Selection',
                                'Please drop a folder, not a file.',
                                Alert.AlertType.WARNING)
                        }
                    }
                }
            }

            event.dropCompleted = success
            textField.style = ''  // Reset styling
            event.consume()
        }
    }

    void applyTheme() {
        // Use jSystemThemeDetector to detect system theme
        try {
            def detector = OsThemeDetector.detector
            def isDark = detector.isDark()

            // Apply AtlantaFX theme based on system preference
            if (isDark) {
                Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet())
            } else {
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet())
            }
        } catch (Exception e) {
            // Default to light theme if detection fails
            Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet())
        }
    }

    void showLicensesDialog() {
        // Create a new stage for the licenses dialog
        def dialog = new Stage()
        dialog.title = 'Third-Party Licenses'
        dialog.initOwner(statusLabel.scene.window)

        // Create TextArea to display license content
        def licenseText = new TextArea()
        licenseText.editable = false
        licenseText.wrapText = true
        licenseText.style = '-fx-font-family: "Courier New"; -fx-font-size: 11px; -fx-font-style: italic;'

        // Try to load the license file from resources
        try {
            def licenseStream = getClass().getResourceAsStream('/licenses/THIRD-PARTY-LICENSES.txt')
            if (licenseStream) {
                licenseText.text = licenseStream.text
            } else {
                licenseText.text = 'License information not available.\n\n' +
                    'The license report should be generated during the build process.\n' +
                    'Please rebuild the project with: ./gradlew clean build\n\n' +
                    'Main dependencies:\n' +
                    '- Apache Groovy (Apache License 2.0)\n' +
                    '- Querz NBT Library (MIT License)\n' +
                    '- Picocli (Apache License 2.0)\n' +
                    '- JavaFX (GPL v2 + Classpath Exception)\n' +
                    '- AtlantaFX (MIT License)\n' +
                    '- Apache Commons (Apache License 2.0)\n' +
                    '- SLF4J & Logback (MIT & EPL/LGPL)\n' +
                    '- Other dependencies as listed in build.gradle'
            }
        } catch (Exception e) {
            licenseText.text = "Error loading license information: ${e.message}\n\n" +
                'Please check that the project was built correctly.'
        }

        // Create close button
        def closeBtn = new Button('Close')
        closeBtn.onAction = { dialog.close() }
        closeBtn.minWidth = 100

        // Layout
        def btnBox = new HBox(closeBtn)
        btnBox.alignment = Pos.CENTER
        btnBox.padding = new Insets(10)

        def root = new BorderPane()
        root.center = licenseText
        root.bottom = btnBox
        BorderPane.setMargin(licenseText, new Insets(10))

        dialog.scene = new Scene(root, 800, 600)
        dialog.show()
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

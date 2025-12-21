import atlantafx.base.theme.PrimerDark
import atlantafx.base.theme.PrimerLight
import com.jthemedetecor.OsThemeDetector
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.application.Application
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Alert
import javafx.scene.control.Alert.AlertType
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.control.Menu
import javafx.scene.control.MenuBar
import javafx.scene.control.MenuItem
import javafx.scene.control.Separator
import javafx.scene.control.SeparatorMenuItem
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.input.Dragboard
import javafx.scene.input.DragEvent
import javafx.scene.input.TransferMode
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.DirectoryChooser
import javafx.stage.Stage
import javafx.util.Duration
import java.awt.Desktop
import java.text.SimpleDateFormat

/**
 * Modern GUI for ReadSignsAndBooks using pure JavaFX
 * Minimal code with Groovy's concise syntax
 */
class GUI extends Application {

    TextField worldPathField
    TextField outputPathField
    CheckBox removeFormattingCheckBox
    CheckBox extractCustomNamesCheckBox
    CheckBox findPortalsCheckBox
    CheckBox overworldCheckBox
    CheckBox netherCheckBox
    CheckBox endCheckBox
    TextArea logArea
    Label statusLabel
    File worldDir
    File outputFolder
    File actualOutputFolder
    Button extractBtn
    Timeline elapsedTimeTimer
    long extractionStartTime

    void start(Stage stage) {
        stage.title = 'ReadSignsAndBooks Extractor'

        // Apply theme before creating UI (AtlantaFX)
        applyTheme()

        // Set application icon (using 512px for best quality - JavaFX will scale as needed)
        try {
            InputStream iconStream = getClass().getResourceAsStream('/icons/icon-512.png')
            if (iconStream) {
                stage.icons.add(new javafx.scene.image.Image(iconStream))
            }
        } catch (IOException e) {
            // Icon loading is optional, silently ignore if not found - trace level for debugging only
            org.slf4j.LoggerFactory.getLogger(GUI).trace('Icon not found, using default', e)
        }

        // Set up GUI log handler with rolling buffer to prevent TextArea freeze
        // (GitHub issue #12: TextArea.appendText() has O(n) performance, causing UI freeze with large logs)
        final int maxLogChars = 80000  // ~80KB of log text (prevents exponential slowdown)

        GuiLogAppender.logHandler = { String message ->
            int currentLength = logArea.text.length()
            int newLength = currentLength + message.length()

            if (newLength > maxLogChars) {
                // Remove oldest ~20% of text to make room (avoids frequent trimming)
                int trimAmount = (int)(maxLogChars * 0.2) + message.length()
                logArea.deleteText(0, Math.min(trimAmount, currentLength))
            }
            logArea.appendText(message)
        }

        // Clean up on close
        stage.onCloseRequest = { event ->
            GuiLogAppender.clearLogHandler()
        }

        // Create menu bar
        MenuBar menuBar = setupMenuBar()

        // Main content layout
        VBox contentRoot = new VBox(15)
        contentRoot.padding = new Insets(20)

        // Title
        Label title = new Label('Minecraft Book & Sign Extractor')
        title.style = '-fx-font-size: 20px; -fx-font-weight: bold;'

        // World directory selection
        HBox worldBox = new HBox(10)
        worldBox.alignment = Pos.CENTER_LEFT
        worldPathField = new TextField()
        worldPathField.promptText = 'Select Minecraft world folder or drag & drop here...'
        worldPathField.editable = false
        HBox.setHgrow(worldPathField, Priority.ALWAYS)  // Make it grow horizontally
        setupDragAndDrop(worldPathField, true)  // Enable drag-and-drop for world folder
        Button worldBtn = new Button('Browse...')
        worldBtn.onAction = { event -> selectWorldDirectory(stage) }
        worldBox.children.addAll(new Label('World Directory:').with { Label label -> label.minWidth = 120; label }, worldPathField, worldBtn)

        // Output folder selection (optional)
        HBox outputBox = new HBox(10)
        outputBox.alignment = Pos.CENTER_LEFT
        outputPathField = new TextField()
        outputPathField.editable = false
        HBox.setHgrow(outputPathField, Priority.ALWAYS)  // Make it grow horizontally
        updateOutputFolderPrompt()  // Set initial prompt text
        setupDragAndDrop(outputPathField, false)  // Enable drag-and-drop for output folder
        Button outputBtn = new Button('Browse...')
        outputBtn.onAction = { event -> selectOutputFolder(stage) }
        outputBox.children.addAll(new Label('Output Folder:').with { Label label -> label.minWidth = 120; label }, outputPathField, outputBtn)

        // Remove formatting checkbox
        HBox formattingBox = new HBox(10)
        formattingBox.alignment = Pos.CENTER_LEFT
        removeFormattingCheckBox = new CheckBox('Remove Minecraft formatting codes (§ codes)')
        removeFormattingCheckBox.selected = false
        formattingBox.children.addAll(new Label('Options:').with { Label label -> label.minWidth = 120; label }, removeFormattingCheckBox)

        // Extract custom names checkbox
        HBox customNamesBox = new HBox(10)
        customNamesBox.alignment = Pos.CENTER_LEFT
        extractCustomNamesCheckBox = new CheckBox('Extract custom names from items and entities')
        extractCustomNamesCheckBox.selected = false
        customNamesBox.children.addAll(new Label('').with { Label label -> label.minWidth = 120; label }, extractCustomNamesCheckBox)

        // Block Search section with visual grouping
        VBox blockSearchSection = new VBox(8)
        blockSearchSection.style = '-fx-padding: 10; -fx-background-color: #f5f5f5; -fx-background-radius: 5;'

        Label blockSearchHeader = new Label('Block Search Options')
        blockSearchHeader.style = '-fx-font-weight: bold; -fx-font-size: 12px;'

        // Find portals checkbox
        HBox findPortalsBox = new HBox(10)
        findPortalsBox.alignment = Pos.CENTER_LEFT
        findPortalsCheckBox = new CheckBox('Find nether portals (with intelligent clustering)')
        findPortalsCheckBox.selected = false
        findPortalsBox.children.addAll(findPortalsCheckBox)

        // Dimension selection checkboxes
        HBox dimensionsBox = new HBox(10)
        dimensionsBox.alignment = Pos.CENTER_LEFT
        overworldCheckBox = new CheckBox('Overworld')
        overworldCheckBox.selected = true
        netherCheckBox = new CheckBox('Nether')
        netherCheckBox.selected = true
        endCheckBox = new CheckBox('The End')
        endCheckBox.selected = true
        Label dimensionsLabel = new Label('Search dimensions:')
        dimensionsBox.children.addAll(dimensionsLabel, overworldCheckBox, netherCheckBox, endCheckBox)

        blockSearchSection.children.addAll(blockSearchHeader, findPortalsBox, dimensionsBox)

        // Action buttons (left-aligned)
        HBox btnBox = new HBox(15)
        btnBox.alignment = Pos.CENTER_LEFT
        extractBtn = new Button('Extract')
        extractBtn.minWidth = 150
        extractBtn.style = '-fx-font-size: 14px; -fx-background-color: #4CAF50; -fx-text-fill: white;'
        extractBtn.onAction = { event -> runExtraction() }
        Button openFolderBtn = new Button('Open Output Folder')
        openFolderBtn.minWidth = 130
        openFolderBtn.onAction = { event -> openOutputFolder() }
        Button clearBtn = new Button('Clear Log')
        clearBtn.minWidth = 100
        clearBtn.onAction = { event -> logArea.text = '' }
        Button exitBtn = new Button('Exit')
        exitBtn.minWidth = 100
        exitBtn.onAction = { event -> Platform.exit() }
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
            customNamesBox,
            blockSearchSection,
            new Separator(),
            btnBox,
            new Separator(),
            statusLabel,
            new Label('Extraction Log:').with { Label label -> label.style = '-fx-font-weight: bold;'; label },
            logArea
        )

        // Main layout with menu bar
        BorderPane root = new BorderPane()
        root.top = menuBar
        root.center = contentRoot

        stage.scene = new Scene(root, 720, 550)
        stage.minWidth = 700
        stage.minHeight = 500
        stage.show()

        // Parse command-line arguments using Picocli (reuses Main's @Option definitions)
        parseGuiArguments()

        // Handle auto-start if --start flag was provided
        if (Main.autoStart) {
            handleAutoStart()
        }
    }

    /**
     * Parse command-line arguments using Picocli into Main's static fields.
     * This reuses Main's @Option definitions to avoid duplication.
     */
    void parseGuiArguments() {
        // Skip when not launched via Application.launch() (e.g., TestFX tests)
        Application.Parameters params = parameters
        if (params == null) {
            return
        }

        String[] args = params.raw as String[]

        // Use Picocli to parse args into Main's static fields
        // parseArgs doesn't execute run(), just populates the @Option fields
        new picocli.CommandLine(new Main()).parseArgs(args)

        // Apply parsed values to GUI controls
        if (Main.customWorldDirectory) {
            File dir = new File(Main.customWorldDirectory)
            if (dir.exists() && dir.directory) {
                worldDir = dir
                worldPathField.text = dir.absolutePath
                updateOutputFolderPrompt()
                logArea.appendText("World directory: ${Main.customWorldDirectory}\n")
            } else {
                logArea.appendText("WARNING: World directory not found: ${Main.customWorldDirectory}\n")
            }
        }

        if (Main.customOutputDirectory) {
            outputFolder = new File(Main.customOutputDirectory)
            outputPathField.text = Main.customOutputDirectory
            updateOutputFolderPrompt()
            logArea.appendText("Output directory: ${Main.customOutputDirectory}\n")
        }

        if (Main.removeFormatting) {
            removeFormattingCheckBox.selected = true
        }

        if (Main.extractCustomNames) {
            extractCustomNamesCheckBox.selected = true
        }

        if (Main.findPortals) {
            findPortalsCheckBox.selected = true
        }

        // Handle dimension flags
        if (Main.searchDimensions) {
            overworldCheckBox.selected = Main.searchDimensions.contains('overworld')
            netherCheckBox.selected = Main.searchDimensions.contains('nether')
            endCheckBox.selected = Main.searchDimensions.contains('end')
        }
    }

    /**
     * Handle auto-start countdown.
     * Shows 3-second countdown in status label, then begins extraction.
     */
    void handleAutoStart() {
        logArea.appendText('Auto-start enabled. Beginning extraction in 3 seconds...\n')

        // Countdown timeline: 3... 2... 1... Extract!
        int[] countdown = [3]  // Use array to allow modification in closure

        Timeline countdownTimer = new Timeline(new KeyFrame(Duration.seconds(1), { event ->
            countdown[0]--
            if (countdown[0] > 0) {
                statusLabel.text = "Auto-starting in ${countdown[0]}..."
            } else {
                statusLabel.text = 'Starting extraction...'
            }
        }))
        countdownTimer.cycleCount = 3

        countdownTimer.onFinished = {
            runExtraction()
        }

        statusLabel.text = 'Auto-starting in 3...'
        countdownTimer.play()
    }

    MenuBar setupMenuBar() {
        MenuBar menuBar = new MenuBar()

        // Help menu
        Menu helpMenu = new Menu('Help')

        MenuItem githubItem = new MenuItem('View on GitHub')
        githubItem.onAction = { event ->
            try {
                Desktop desktop = Desktop.desktop
                if (desktop && desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(new URI('https://github.com/Vibed/ReadSignsAndBooks.jar'))
                } else {
                    showAlert('GitHub', 'Project URL:\nhttps://github.com/Vibed/ReadSignsAndBooks.jar', Alert.AlertType.INFORMATION)
                }
            } catch (IOException | URISyntaxException e) {
                // Catch browser/URI exceptions during GitHub URL opening
                showAlert('Error', "Could not open browser: ${e.message}", Alert.AlertType.ERROR)
            }
        }

        MenuItem aboutItem = new MenuItem('About')
        aboutItem.onAction = { event ->
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

        SeparatorMenuItem separator = new SeparatorMenuItem()

        MenuItem licensesItem = new MenuItem('Third-Party Licenses')
        licensesItem.onAction = { event ->
            showLicensesDialog()
        }

        helpMenu.items.addAll(githubItem, separator, aboutItem, licensesItem)
        menuBar.menus.add(helpMenu)

        return menuBar
    }

    void selectWorldDirectory(Stage stage) {
        DirectoryChooser chooser = new DirectoryChooser(title: 'Select Minecraft World Directory')
        worldDir = chooser.showDialog(stage)
        if (worldDir) {
            worldPathField.text = worldDir.absolutePath
            updateOutputFolderPrompt()
        }
    }

    void selectOutputFolder(Stage stage) {
        DirectoryChooser chooser = new DirectoryChooser(title: 'Select Output Folder')
        outputFolder = chooser.showDialog(stage)
        if (outputFolder) {
            outputPathField.text = outputFolder.absolutePath
            updateOutputFolderPrompt()
        }
    }

    void updateOutputFolderPrompt() {
        String dateStamp = new SimpleDateFormat('yyyy-MM-dd', Locale.US).format(new Date())
        String defaultPath

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
            Desktop desktop = Desktop.desktop
            if (desktop && desktop.isSupported(Desktop.Action.OPEN)) {
                desktop.open(actualOutputFolder)
            } else {
                showAlert('Error', 'Cannot open folder: Desktop operations not supported', Alert.AlertType.ERROR)
            }
        } catch (IOException e) {
            showAlert('Error', "Could not open folder: ${e.message}", Alert.AlertType.ERROR)
        }
    }

    void runExtraction() {
        // Disable extract button and start timer
        extractBtn.disable = true
        extractionStartTime = System.currentTimeMillis()
        statusLabel.text = 'Extracting...'

        // Start elapsed time timer (updates button text every second)
        // Note: Timeline KeyFrame handlers already run on the JavaFX Application Thread,
        // so no Platform.runLater is needed (see GitHub issue #12)
        elapsedTimeTimer = new Timeline(new KeyFrame(Duration.seconds(1), { event ->
            long elapsed = (System.currentTimeMillis() - extractionStartTime) / 1000
            int minutes = (elapsed / 60) as int
            int seconds = (elapsed % 60) as int
            extractBtn.text = String.format('Extracting... %02d:%02d', minutes, seconds)
        }))
        elapsedTimeTimer.cycleCount = Timeline.INDEFINITE
        elapsedTimeTimer.play()

        // Run extraction in background thread
        Thread.start {
            try {
                // Prepare arguments - let CLI handle defaults if not set
                List<String> args = []
                if (worldDir) {
                    args += ['-w', worldDir.absolutePath]
                }
                if (outputFolder) {
                    args += ['-o', outputFolder.absolutePath]
                }
                if (removeFormattingCheckBox.selected) {
                    args += ['--remove-formatting']
                }
                if (extractCustomNamesCheckBox.selected) {
                    args += ['--extract-custom-names']
                }
                if (findPortalsCheckBox.selected) {
                    args += ['--find-portals']
                }

                // Build dimensions list from checkboxes
                List<String> selectedDimensions = []
                if (overworldCheckBox.selected) {
                    selectedDimensions += 'overworld'
                }
                if (netherCheckBox.selected) {
                    selectedDimensions += 'nether'
                }
                if (endCheckBox.selected) {
                    selectedDimensions += 'end'
                }
                if (selectedDimensions) {
                    args += ['--search-dimensions', selectedDimensions.join(',')]
                }

                // Call Main CLI directly (avoid double launch)
                // Logging will automatically appear in GUI via GuiLogAppender
                Main.runCli(args as String[])

                Platform.runLater {
                    // Stop timer and re-enable button
                    elapsedTimeTimer?.stop()
                    extractBtn.disable = false
                    extractBtn.text = 'Extract'

                    long totalElapsed = (System.currentTimeMillis() - extractionStartTime) / 1000
                    int minutes = (totalElapsed / 60) as int
                    int seconds = (totalElapsed % 60) as int

                    // Build summary message
                    int portalCount = Main.portalResults?.size() ?: 0
                    String portalStr = portalCount > 0 ? ", ${portalCount} portals" : ''
                    statusLabel.text = String.format('Complete! %d books, %d signs%s (took %02d:%02d)',
                        Main.bookHashes.size(), Main.signHashes.size(), portalStr, minutes, seconds)

                    String alertMsg = "Extraction complete!\n\nBooks: ${Main.bookHashes.size()}\nSigns: ${Main.signHashes.size()}"
                    if (portalCount > 0) {
                        alertMsg += "\nPortals: ${portalCount}"
                    }
                    alertMsg += "\nTime: ${minutes}m ${seconds}s"
                    showAlert('Success', alertMsg, Alert.AlertType.INFORMATION)
                }

            } catch (IOException | IllegalArgumentException | IllegalStateException e) {
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
                List<File> files = db.files
                if (files && !files.empty) {
                    File droppedFile = files[0]  // Take first file/folder

                    if (droppedFile.directory) {
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
            OsThemeDetector detector = OsThemeDetector.detector
            boolean isDark = detector.dark

            // Apply AtlantaFX theme based on system preference
            if (isDark) {
                Application.userAgentStylesheet = new PrimerDark().userAgentStylesheet
            } else {
                Application.userAgentStylesheet = new PrimerLight().userAgentStylesheet
            }
        } catch (UnsupportedOperationException | SecurityException e) {
            // Default to light theme if detection fails - exception may occur if theme detection unavailable
            Application.userAgentStylesheet = new PrimerLight().userAgentStylesheet
        }
    }

    void showLicensesDialog() {
        // Create a new stage for the licenses dialog
        Stage dialog = new Stage()
        dialog.title = 'Third-Party Licenses'
        dialog.initOwner(statusLabel.scene.window)

        // Create TextArea to display license content
        TextArea licenseText = new TextArea()
        licenseText.editable = false
        licenseText.wrapText = true
        licenseText.style = '-fx-font-family: "Courier New"; -fx-font-size: 11px; -fx-font-style: italic;'

        // Try to load the license file from resources
        try {
            InputStream licenseStream = getClass().getResourceAsStream('/licenses/THIRD-PARTY-LICENSES.txt')
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
        } catch (IOException e) {
            licenseText.text = "Error loading license information: ${e.message}\n\n" +
                'Please check that the project was built correctly.'
        }

        // Create close button
        Button closeBtn = new Button('Close')
        closeBtn.onAction = { dialog.close() }
        closeBtn.minWidth = 100

        // Layout
        HBox btnBox = new HBox(closeBtn)
        btnBox.alignment = Pos.CENTER
        btnBox.padding = new Insets(10)

        BorderPane root = new BorderPane()
        root.center = licenseText
        root.bottom = btnBox
        BorderPane.setMargin(licenseText, new Insets(10))

        dialog.scene = new Scene(root, 800, 600)
        dialog.show()
    }

    static void showAlert(String title, String message, Alert.AlertType type) {
        Platform.runLater {
            Alert alert = new Alert(type)
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

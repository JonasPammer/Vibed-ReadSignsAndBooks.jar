package viewers

import atlantafx.base.theme.PrimerDark
import atlantafx.base.theme.PrimerLight
import com.jthemedetecor.OsThemeDetector
import javafx.application.Application
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import javafx.scene.text.Font
import javafx.scene.text.Text
import javafx.scene.text.TextFlow
import javafx.stage.FileChooser
import javafx.stage.Stage
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.awt.Desktop

/**
 * Minecraft-styled sign viewer with wood textures and interactive features.
 *
 * Displays signs in a card grid layout with:
 * - Visual sign blocks with authentic wood type colors
 * - 4 lines of text (max 15 chars per line)
 * - Wood type badges (12 wood types)
 * - Coordinate labels
 * - Click to expand with full details
 * - Search/filter by text, dimension, coordinates
 * - Sort by content, location, dimension
 */
class SignViewer extends Application {

    private static final Logger LOGGER = LoggerFactory.getLogger(SignViewer)

    // Wood type colors (Minecraft-accurate)
    private static final Map<String, String> WOOD_COLORS = [
        oak: '#BA8755',
        spruce: '#5A3D2D',
        birch: '#D6CB8E',
        jungle: '#AB7743',
        acacia: '#9F5429',
        dark_oak: '#3E2912',
        mangrove: '#6B3028',
        cherry: '#E4A2A6',
        crimson: '#582A32',
        warped: '#1A7475',
        bamboo: '#D4CE67',
        pale_oak: '#E8E0D0'
    ]

    // Dimension colors
    private static final Map<String, Color> DIMENSION_COLORS = [
        overworld: Color.web('#4CAF50'),
        nether: Color.web('#D32F2F'),
        end: Color.web('#9C27B0'),
        unknown: Color.web('#757575')
    ]

    // Sign data structure: [line1, line2, line3, line4, x, y, z, dimension, blockType]
    private List<Map<String, Object>> allSigns = []
    private List<Map<String, Object>> filteredSigns = []

    // UI components
    private TextField searchField
    private ComboBox<String> dimensionFilter
    private ComboBox<String> sortComboBox
    private Spinner<Integer> minXSpinner
    private Spinner<Integer> maxXSpinner
    private Spinner<Integer> minYSpinner
    private Spinner<Integer> maxYSpinner
    private Spinner<Integer> minZSpinner
    private Spinner<Integer> maxZSpinner
    private FlowPane signGridPane
    private Label statusLabel

    // Root pane for embedding or standalone usage
    private BorderPane rootPane

    @Override
    void start(Stage primaryStage) {
        primaryStage.title = 'Minecraft Sign Viewer'
        applyTheme()

        // Build core UI and add menu bar for standalone mode
        BorderPane root = initializeUI()
        root.top = createMenuBar(primaryStage)

        Scene scene = new Scene(root, 1200, 800)
        primaryStage.scene = scene
        primaryStage.show()

        // Load sample data or from CLI args
        loadSignsFromArgs()
    }

    /**
     * Build the SignViewer UI and return the root pane.
     * This is used by both the standalone Application and embedded in other windows (e.g., OutputViewer).
     */
    BorderPane initializeUI() {
        if (rootPane) {
            return rootPane
        }

        rootPane = new BorderPane()

        // Center: Sign grid
        VBox centerContent = new VBox(10)
        centerContent.padding = new Insets(15)

        // Filter controls
        VBox filterControls = createFilterControls()
        centerContent.children.add(filterControls)

        // Sign grid (scrollable)
        ScrollPane scrollPane = new ScrollPane()
        scrollPane.fitToWidth = true
        scrollPane.style = '-fx-background-color: derive(-fx-base, -5%);'

        signGridPane = new FlowPane()
        signGridPane.hgap = 15
        signGridPane.vgap = 15
        signGridPane.padding = new Insets(15)
        signGridPane.style = '-fx-background-color: derive(-fx-base, -5%);'

        // Initial placeholder content
        Label emptyLabel = new Label('No signs loaded')
        emptyLabel.style = '-fx-font-size: 14px; -fx-text-fill: gray; -fx-font-style: italic;'
        signGridPane.children.add(emptyLabel)

        scrollPane.content = signGridPane
        VBox.setVgrow(scrollPane, Priority.ALWAYS)
        centerContent.children.add(scrollPane)

        rootPane.center = centerContent

        // Bottom: Status bar
        statusLabel = new Label('No signs loaded')
        statusLabel.padding = new Insets(5, 10, 5, 10)
        statusLabel.style = '-fx-font-size: 11px; -fx-text-fill: gray;'
        rootPane.bottom = statusLabel

        return rootPane
    }

    /**
     * Set the current sign dataset (used for embedding in OutputViewer).
     */
    void setSigns(List<Map<String, Object>> signs) {
        allSigns = (signs ?: []) as List<Map<String, Object>>
        filteredSigns = new ArrayList<>(allSigns)

        if (statusLabel) {
            statusLabel.text = "Loaded ${allSigns.size()} signs"
        }

        // If UI is initialized, render immediately.
        if (signGridPane) {
            applyFilters()
        }
    }

    /**
     * Best-effort navigation/highlight hook (used by GlobalSearch integration).
     */
    void highlightSign(Map<String, Object> sign) {
        if (!sign) return

        // Ensure UI exists
        initializeUI()

        // Try to make the sign visible by narrowing the search to its first line (fast + simple).
        if (searchField) {
            searchField.text = (sign.line1 ?: sign.signText ?: '').toString()
        }

        // Open detail dialog for immediate context
        try {
            Platform.runLater { showSignDetail(sign) }
        } catch (Exception ignored) {
            // ignore if called outside FX thread
            try {
                showSignDetail(sign)
            } catch (Exception ignored2) {
                // no-op
            }
        }
    }

    /**
     * Create menu bar with File and Help menus.
     */
    private MenuBar createMenuBar(Stage stage) {
        MenuBar menuBar = new MenuBar()

        // File menu
        Menu fileMenu = new Menu('File')
        MenuItem loadCsvItem = new MenuItem('Load Signs CSV...')
        loadCsvItem.onAction = { loadSignsFromCsv(stage) }
        MenuItem exitItem = new MenuItem('Exit')
        exitItem.onAction = { Platform.exit() }
        fileMenu.items.addAll(loadCsvItem, new SeparatorMenuItem(), exitItem)

        // Help menu
        Menu helpMenu = new Menu('Help')
        MenuItem aboutItem = new MenuItem('About')
        aboutItem.onAction = { showAboutDialog(stage) }
        helpMenu.items.add(aboutItem)

        menuBar.menus.addAll(fileMenu, helpMenu)
        return menuBar
    }

    /**
     * Create filter controls panel.
     */
    private VBox createFilterControls() {
        VBox filterBox = new VBox(10)
        filterBox.style = '-fx-padding: 10; -fx-background-color: derive(-fx-base, 5%); -fx-background-radius: 5; -fx-border-color: derive(-fx-base, -10%); -fx-border-radius: 5;'

        Label filterLabel = new Label('Filters & Search')
        filterLabel.style = '-fx-font-weight: bold; -fx-font-size: 13px;'

        // Search row
        HBox searchRow = new HBox(10)
        searchRow.alignment = Pos.CENTER_LEFT
        Label searchLabel = new Label('Search Text:')
        searchLabel.minWidth = 100
        searchField = new TextField()
        searchField.promptText = 'Search sign text...'
        HBox.setHgrow(searchField, Priority.ALWAYS)
        searchField.textProperty().addListener { obs, oldVal, newVal -> applyFilters() }
        searchRow.children.addAll(searchLabel, searchField)

        // Dimension and Sort row
        HBox dimensionSortRow = new HBox(10)
        dimensionSortRow.alignment = Pos.CENTER_LEFT
        Label dimensionLabel = new Label('Dimension:')
        dimensionLabel.minWidth = 100
        dimensionFilter = new ComboBox<>()
        dimensionFilter.items.addAll('All', 'Overworld', 'Nether', 'The End')
        dimensionFilter.value = 'All'
        dimensionFilter.valueProperty().addListener { obs, oldVal, newVal -> applyFilters() }

        Label sortLabel = new Label('Sort By:')
        sortLabel.padding = new Insets(0, 0, 0, 20)
        sortComboBox = new ComboBox<>()
        sortComboBox.items.addAll('By Content (A-Z)', 'By Location (X)', 'By Dimension', 'By Y Level')
        sortComboBox.value = 'By Content (A-Z)'
        sortComboBox.valueProperty().addListener { obs, oldVal, newVal -> applyFilters() }

        dimensionSortRow.children.addAll(dimensionLabel, dimensionFilter, sortLabel, sortComboBox)

        // Coordinate range filters
        HBox coordRow = new HBox(10)
        coordRow.alignment = Pos.CENTER_LEFT
        Label coordLabel = new Label('Coordinates:')
        coordLabel.minWidth = 100

        minXSpinner = createCoordSpinner(-30000000, 30000000, -10000)
        maxXSpinner = createCoordSpinner(-30000000, 30000000, 10000)
        minYSpinner = createCoordSpinner(-64, 320, -64)
        maxYSpinner = createCoordSpinner(-64, 320, 320)
        minZSpinner = createCoordSpinner(-30000000, 30000000, -10000)
        maxZSpinner = createCoordSpinner(-30000000, 30000000, 10000)

        Button resetCoordsBtn = new Button('Reset Range')
        resetCoordsBtn.onAction = {
            resetCoordinateFilters()
            applyFilters()
        }

        coordRow.children.addAll(
            coordLabel,
            new Label('X:'), minXSpinner, new Label('to'), maxXSpinner,
            new Label('Y:'), minYSpinner, new Label('to'), maxYSpinner,
            new Label('Z:'), minZSpinner, new Label('to'), maxZSpinner,
            resetCoordsBtn
        )

        filterBox.children.addAll(filterLabel, searchRow, dimensionSortRow, coordRow)
        return filterBox
    }

    /**
     * Create coordinate spinner with editable field.
     */
    private Spinner<Integer> createCoordSpinner(int min, int max, int initial) {
        Spinner<Integer> spinner = new Spinner<>()
        spinner.valueFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, initial, 100)
        spinner.editable = true
        spinner.prefWidth = 100
        spinner.valueProperty().addListener { obs, oldVal, newVal -> applyFilters() }
        return spinner
    }

    /**
     * Reset coordinate filters to default wide range.
     */
    private void resetCoordinateFilters() {
        minXSpinner.valueFactory.value = -10000
        maxXSpinner.valueFactory.value = 10000
        minYSpinner.valueFactory.value = -64
        maxYSpinner.valueFactory.value = 320
        minZSpinner.valueFactory.value = -10000
        maxZSpinner.valueFactory.value = 10000
    }

    /**
     * Load signs from command-line arguments or default location.
     */
    private void loadSignsFromArgs() {
        List<String> args = parameters.raw
        if (args && args.size() > 0) {
            File csvFile = new File(args[0])
            if (csvFile.exists()) {
                loadSignsFromFile(csvFile)
                return
            }
        }

        // Try default location: output folder
        File defaultCsv = new File(System.getProperty('user.dir'), 'ReadBooks/all_signs.csv')
        if (defaultCsv.exists()) {
            loadSignsFromFile(defaultCsv)
        } else {
            // Load sample data for demo
            loadSampleData()
        }
    }

    /**
     * Load signs from CSV file via file chooser.
     */
    private void loadSignsFromCsv(Stage stage) {
        FileChooser chooser = new FileChooser()
        chooser.title = 'Load Signs CSV'
        chooser.extensionFilters.add(new FileChooser.ExtensionFilter('CSV Files', '*.csv'))
        chooser.initialDirectory = new File(System.getProperty('user.dir'))

        File file = chooser.showOpenDialog(stage)
        if (file) {
            loadSignsFromFile(file)
        }
    }

    /**
     * Load signs from CSV file.
     * CSV format: X,Y,Z,FoundWhere,SignText,Line1,Line2,Line3,Line4
     */
    private void loadSignsFromFile(File file) {
        LOGGER.info("Loading signs from: ${file.absolutePath}")
        allSigns.clear()

        try {
            file.withReader('UTF-8') { reader ->
                String header = reader.readLine() // Skip header
                String line
                while ((line = reader.readLine()) != null) {
                    List<String> parts = parseCsvLine(line)
                    if (parts.size() >= 8) {
                        int x = parts[0] as int
                        int y = parts[1] as int
                        int z = parts[2] as int
                        String foundWhere = parts[3]
                        String line1 = parts.size() > 5 ? parts[5] : ''
                        String line2 = parts.size() > 6 ? parts[6] : ''
                        String line3 = parts.size() > 7 ? parts[7] : ''
                        String line4 = parts.size() > 8 ? parts[8] : ''

                        // Extract dimension and block type
                        String dimension = extractDimension(foundWhere)
                        String blockType = extractWoodType(foundWhere)

                        allSigns.add([
                            line1: line1,
                            line2: line2,
                            line3: line3,
                            line4: line4,
                            x: x,
                            y: y,
                            z: z,
                            dimension: dimension,
                            blockType: blockType
                        ])
                    }
                }
            }

            LOGGER.info("Loaded ${allSigns.size()} signs")
            statusLabel.text = "Loaded ${allSigns.size()} signs from ${file.name}"
            applyFilters()

        } catch (Exception e) {
            LOGGER.error("Failed to load signs", e)
            showAlert('Error', "Failed to load signs:\n${e.message}", Alert.AlertType.ERROR)
        }
    }

    /**
     * Parse CSV line handling quoted fields.
     */
    private List<String> parseCsvLine(String line) {
        List<String> fields = []
        StringBuilder current = new StringBuilder()
        boolean inQuotes = false

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i)
            if (c == '"') {
                inQuotes = !inQuotes
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString())
                current.setLength(0)
            } else {
                current.append(c)
            }
        }
        fields.add(current.toString())
        return fields
    }

    /**
     * Extract dimension from foundWhere field.
     */
    private String extractDimension(String foundWhere) {
        if (foundWhere.contains('nether') || foundWhere.contains('DIM-1')) {
            return 'nether'
        } else if (foundWhere.contains('end') || foundWhere.contains('DIM1')) {
            return 'end'
        } else {
            return 'overworld'
        }
    }

    /**
     * Extract wood type from block ID (e.g., "minecraft:oak_sign" -> "oak").
     */
    private String extractWoodType(String blockId) {
        if (!blockId || !blockId.contains('sign')) {
            return 'oak'
        }

        String lower = blockId.toLowerCase()
        for (String wood : WOOD_COLORS.keySet()) {
            if (lower.contains(wood)) {
                return wood
            }
        }
        return 'oak'
    }

    /**
     * Load sample data for demonstration.
     */
    private void loadSampleData() {
        LOGGER.info('Loading sample data')
        allSigns = [
            [line1: 'Welcome to', line2: 'Minecraft', line3: 'Server', line4: '→ Spawn', x: 100, y: 64, z: 200, dimension: 'overworld', blockType: 'oak'],
            [line1: 'Nether Hub', line2: '', line3: 'Portal Room', line4: '', x: 50, y: 100, z: 50, dimension: 'nether', blockType: 'crimson'],
            [line1: 'End City', line2: 'Ahead', line3: '', line4: 'Careful!', x: 1000, y: 70, z: 1000, dimension: 'end', blockType: 'spruce'],
            [line1: 'Shop', line2: '', line3: 'Buy/Sell', line4: '', x: 150, y: 65, z: 300, dimension: 'overworld', blockType: 'birch'],
            [line1: 'Farm Area', line2: '', line3: 'Keep gate', line4: 'closed', x: 200, y: 63, z: 400, dimension: 'overworld', blockType: 'jungle']
        ]
        statusLabel.text = "Showing ${allSigns.size()} sample signs (load CSV to view real data)"
        applyFilters()
    }

    /**
     * Apply filters and update sign display.
     */
    private void applyFilters() {
        String searchText = searchField?.text?.toLowerCase() ?: ''
        String dimension = dimensionFilter?.value ?: 'All'
        int minX = minXSpinner?.value ?: -10000
        int maxX = maxXSpinner?.value ?: 10000
        int minY = minYSpinner?.value ?: -64
        int maxY = maxYSpinner?.value ?: 320
        int minZ = minZSpinner?.value ?: -10000
        int maxZ = maxZSpinner?.value ?: 10000

        filteredSigns = allSigns.findAll { sign ->
            // Text search
            boolean matchesSearch = searchText.isEmpty() ||
                sign.line1?.toLowerCase()?.contains(searchText) ||
                sign.line2?.toLowerCase()?.contains(searchText) ||
                sign.line3?.toLowerCase()?.contains(searchText) ||
                sign.line4?.toLowerCase()?.contains(searchText)

            // Dimension filter
            boolean matchesDimension = dimension == 'All' ||
                (dimension == 'Overworld' && sign.dimension == 'overworld') ||
                (dimension == 'Nether' && sign.dimension == 'nether') ||
                (dimension == 'The End' && sign.dimension == 'end')

            // Coordinate range
            boolean matchesCoords = sign.x >= minX && sign.x <= maxX &&
                                   sign.y >= minY && sign.y <= maxY &&
                                   sign.z >= minZ && sign.z <= maxZ

            return matchesSearch && matchesDimension && matchesCoords
        }

        // Apply sorting
        String sortBy = sortComboBox?.value ?: 'By Content (A-Z)'
        switch (sortBy) {
            case 'By Content (A-Z)':
                filteredSigns.sort { a, b ->
                    (a.line1 ?: '').compareTo(b.line1 ?: '')
                }
                break
            case 'By Location (X)':
                filteredSigns.sort { a, b -> a.x <=> b.x }
                break
            case 'By Dimension':
                filteredSigns.sort { a, b ->
                    (a.dimension ?: '').compareTo(b.dimension ?: '')
                }
                break
            case 'By Y Level':
                filteredSigns.sort { a, b -> b.y <=> a.y } // Descending
                break
        }

        updateSignGrid()
    }

    /**
     * Update sign grid with filtered and sorted signs.
     */
    private void updateSignGrid() {
        signGridPane.children.clear()

        if (filteredSigns.isEmpty()) {
            Label emptyLabel = new Label('No signs match the current filters')
            emptyLabel.style = '-fx-font-size: 14px; -fx-text-fill: gray;'
            signGridPane.children.add(emptyLabel)
            statusLabel.text = 'No signs match filters (total: ' + allSigns.size() + ')'
            return
        }

        filteredSigns.each { sign ->
            VBox signCard = createSignCard(sign)
            signGridPane.children.add(signCard)
        }

        statusLabel.text = "Showing ${filteredSigns.size()} of ${allSigns.size()} signs"
    }

    /**
     * Create a sign card with wood texture background and text.
     */
    private VBox createSignCard(Map<String, Object> sign) {
        VBox card = new VBox(5)
        card.padding = new Insets(10)
        card.style = '-fx-background-color: derive(-fx-base, 10%); -fx-background-radius: 5; -fx-border-color: derive(-fx-base, -20%); -fx-border-radius: 5; -fx-border-width: 1;'
        card.prefWidth = 220
        card.cursor = javafx.scene.Cursor.HAND

        // Wood type badge
        String woodType = sign.blockType ?: 'oak'
        String woodColor = WOOD_COLORS[woodType] ?: WOOD_COLORS['oak']

        HBox badgeRow = new HBox(5)
        badgeRow.alignment = Pos.CENTER_LEFT

        Label badge = new Label(woodType.replace('_', ' ').capitalize())
        badge.style = "-fx-background-color: ${woodColor}; -fx-text-fill: white; -fx-padding: 2 8 2 8; -fx-background-radius: 3; -fx-font-size: 10px; -fx-font-weight: bold;"

        // Dimension badge
        String dimension = sign.dimension ?: 'unknown'
        Color dimColor = DIMENSION_COLORS[dimension] ?: DIMENSION_COLORS['unknown']
        Label dimBadge = new Label(dimension.capitalize())
        dimBadge.style = "-fx-background-color: ${toHex(dimColor)}; -fx-text-fill: white; -fx-padding: 2 6 2 6; -fx-background-radius: 3; -fx-font-size: 9px;"

        badgeRow.children.addAll(badge, dimBadge)
        card.children.add(badgeRow)

        // Sign visual block
        StackPane signBlock = new StackPane()
        signBlock.prefHeight = 120

        // Background rectangle with wood color
        Rectangle background = new Rectangle(200, 100)
        background.fill = Color.web(woodColor)
        background.arcWidth = 5
        background.arcHeight = 5

        // Border for depth
        Rectangle border = new Rectangle(200, 100)
        border.fill = Color.TRANSPARENT
        border.stroke = Color.web(woodColor).darker()
        border.strokeWidth = 2
        border.arcWidth = 5
        border.arcHeight = 5

        // Text lines
        VBox textBox = new VBox(2)
        textBox.alignment = Pos.CENTER
        textBox.padding = new Insets(15)

        String line1 = truncateSignLine(sign.line1 ?: '')
        String line2 = truncateSignLine(sign.line2 ?: '')
        String line3 = truncateSignLine(sign.line3 ?: '')
        String line4 = truncateSignLine(sign.line4 ?: '')

        Label text1 = createSignLineLabel(line1)
        Label text2 = createSignLineLabel(line2)
        Label text3 = createSignLineLabel(line3)
        Label text4 = createSignLineLabel(line4)

        textBox.children.addAll(text1, text2, text3, text4)
        signBlock.children.addAll(background, border, textBox)
        card.children.add(signBlock)

        // Coordinates label
        Label coordsLabel = new Label("(${sign.x}, ${sign.y}, ${sign.z})")
        coordsLabel.style = '-fx-font-size: 10px; -fx-text-fill: gray; -fx-font-family: monospace;'
        coordsLabel.alignment = Pos.CENTER
        card.children.add(coordsLabel)

        // Click to expand
        card.onMouseClicked = { event -> showSignDetail(sign) }

        return card
    }

    /**
     * Truncate sign line to 15 characters max (Minecraft limit).
     */
    private String truncateSignLine(String line) {
        if (!line) return ''
        return line.length() > 15 ? line.substring(0, 15) : line
    }

    /**
     * Create label for sign text line.
     */
    private Label createSignLineLabel(String text) {
        Label label = new Label(text ?: '')
        label.style = '-fx-font-family: monospace; -fx-font-size: 12px; -fx-text-fill: black;'
        label.prefWidth = 180
        label.alignment = Pos.CENTER
        return label
    }

    /**
     * Convert Color to hex string.
     */
    private String toHex(Color color) {
        return String.format('#%02X%02X%02X',
            (int)(color.red * 255),
            (int)(color.green * 255),
            (int)(color.blue * 255))
    }

    /**
     * Show detailed sign view in a dialog.
     */
    private void showSignDetail(Map<String, Object> sign) {
        Stage dialog = new Stage()
        dialog.title = 'Sign Details'

        VBox content = new VBox(15)
        content.padding = new Insets(20)

        // Sign text (larger)
        VBox signTextBox = new VBox(5)
        signTextBox.style = '-fx-background-color: derive(-fx-base, 10%); -fx-padding: 15; -fx-background-radius: 5;'

        Label titleLabel = new Label('Sign Text:')
        titleLabel.style = '-fx-font-weight: bold; -fx-font-size: 14px;'

        TextFlow textFlow = new TextFlow()
        String line1 = sign.line1 ?: ''
        String line2 = sign.line2 ?: ''
        String line3 = sign.line3 ?: ''
        String line4 = sign.line4 ?: ''

        Text text1 = new Text(line1 + '\n')
        Text text2 = new Text(line2 + '\n')
        Text text3 = new Text(line3 + '\n')
        Text text4 = new Text(line4)
        text1.style = '-fx-font-family: monospace; -fx-font-size: 16px;'
        text2.style = '-fx-font-family: monospace; -fx-font-size: 16px;'
        text3.style = '-fx-font-family: monospace; -fx-font-size: 16px;'
        text4.style = '-fx-font-family: monospace; -fx-font-size: 16px;'

        textFlow.children.addAll(text1, text2, text3, text4)
        signTextBox.children.addAll(titleLabel, textFlow)
        content.children.add(signTextBox)

        // Metadata
        GridPane metadataGrid = new GridPane()
        metadataGrid.hgap = 10
        metadataGrid.vgap = 5

        int row = 0
        addMetadataRow(metadataGrid, row++, 'Coordinates:', "${sign.x}, ${sign.y}, ${sign.z}")
        addMetadataRow(metadataGrid, row++, 'Dimension:', (sign.dimension ?: 'unknown').capitalize())
        addMetadataRow(metadataGrid, row++, 'Block Type:', (sign.blockType ?: 'oak').replace('_', ' ').capitalize() + ' Sign')

        content.children.add(metadataGrid)

        // Action buttons
        HBox buttonBox = new HBox(10)
        buttonBox.alignment = Pos.CENTER

        Button copyTpBtn = new Button('Copy TP Command')
        copyTpBtn.onAction = {
            String tpCmd = "/tp @s ${sign.x} ${sign.y} ${sign.z}"
            ClipboardContent clipContent = new ClipboardContent()
            clipContent.putString(tpCmd)
            Clipboard.systemClipboard.setContent(clipContent)
            showAlert('Copied', "Teleport command copied:\n${tpCmd}", Alert.AlertType.INFORMATION)
        }

        Button closeBtn = new Button('Close')
        closeBtn.onAction = { dialog.close() }

        buttonBox.children.addAll(copyTpBtn, closeBtn)
        content.children.add(buttonBox)

        Scene scene = new Scene(content, 450, 350)
        dialog.scene = scene
        dialog.show()
    }

    /**
     * Add metadata row to grid.
     */
    private void addMetadataRow(GridPane grid, int row, String label, String value) {
        Label labelNode = new Label(label)
        labelNode.style = '-fx-font-weight: bold;'
        Label valueNode = new Label(value)
        valueNode.style = '-fx-font-family: monospace;'
        grid.add(labelNode, 0, row)
        grid.add(valueNode, 1, row)
    }

    /**
     * Show about dialog.
     */
    private void showAboutDialog(Stage owner) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION)
        alert.initOwner(owner)
        alert.title = 'About Sign Viewer'
        alert.headerText = 'Minecraft Sign Viewer'
        alert.contentText = '''
Part of ReadSignsAndBooks.jar
Minecraft World Data Extraction Tool

Features:
• View signs in Minecraft-styled cards
• Filter by text, dimension, coordinates
• Sort by various criteria
• Copy teleport commands
• 12 wood type colors

Load signs from all_signs.csv or all_signs_raw.csv
'''
        alert.showAndWait()
    }

    /**
     * Show alert dialog.
     */
    private void showAlert(String title, String message, Alert.AlertType type) {
        Platform.runLater {
            Alert alert = new Alert(type)
            alert.title = title
            alert.headerText = null
            alert.contentText = message
            alert.showAndWait()
        }
    }

    /**
     * Apply theme based on system preference.
     */
    private void applyTheme() {
        try {
            OsThemeDetector detector = OsThemeDetector.detector
            boolean isDark = detector.dark
            if (isDark) {
                Application.userAgentStylesheet = new PrimerDark().userAgentStylesheet
            } else {
                Application.userAgentStylesheet = new PrimerLight().userAgentStylesheet
            }
        } catch (Exception e) {
            Application.userAgentStylesheet = new PrimerLight().userAgentStylesheet
        }
    }

    static void main(String[] args) {
        launch(SignViewer, args)
    }
}

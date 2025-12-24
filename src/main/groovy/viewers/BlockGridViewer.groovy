package viewers

import javafx.application.Platform
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.collections.transformation.FilteredList
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.TextField
import javafx.scene.control.Tooltip
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.FlowPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Interactive grid viewer for Minecraft blocks from BlockDatabase.
 *
 * Features:
 * - Category tabs: Ores, Storage, Redstone, Natural, Manufactured, All
 * - Block slot rendering with isometric view
 * - Search/filter by block type, dimension, coordinate range
 * - Hover tooltip with block details
 * - Copy teleport command on click
 * - "Show all of type" filtering
 *
 * Block rendering: Simplified isometric view showing top face rotated 45°.
 */
class BlockGridViewer extends BorderPane {

    private static final Logger LOGGER = LoggerFactory.getLogger(BlockGridViewer)

    // Data source
    private BlockDatabase blockDatabase
    private ObservableList<BlockEntry> allBlocks = FXCollections.observableArrayList()
    private FilteredList<BlockEntry> filteredBlocks

    // UI components
    private FlowPane blockGrid
    private TextField searchField
    private ComboBox<String> categoryFilter
    private ComboBox<String> dimensionFilter
    private Label statusLabel
    private Button clearFiltersBtn

    // Current filter state
    private String currentCategory = 'All'
    private String currentDimension = 'All'
    private String currentSearchText = ''

    // Block categories
    private static final Map<String, List<String>> BLOCK_CATEGORIES = [
        'Ores': ['_ore', 'ancient_debris', 'raw_', 'quartz'],
        'Storage': ['chest', 'barrel', 'shulker_box', 'hopper', 'dropper', 'dispenser', 'furnace', 'smoker', 'blast_furnace'],
        'Redstone': ['redstone_', 'observer', 'piston', 'comparator', 'repeater', 'lever', 'button', 'pressure_plate'],
        'Natural': ['grass_block', 'dirt', 'stone', 'sand', 'gravel', 'leaves', 'log', 'water', 'lava', 'ice', 'snow'],
        'Manufactured': ['crafting_table', 'anvil', 'enchanting_table', 'brewing_stand', 'beacon', 'conduit', 'lectern'],
        'Portal': ['nether_portal', 'end_portal', 'end_gateway'],
        'Rare': ['spawner', 'dragon_egg', 'beacon', 'conduit', 'sculk_shrieker', 'sculk_catalyst']
    ]

    // Block color mapping for rendering (simplified)
    private static final Map<String, Color> BLOCK_COLORS = [
        'diamond_ore': Color.rgb(93, 219, 213),
        'emerald_ore': Color.rgb(80, 218, 130),
        'gold_ore': Color.rgb(252, 238, 75),
        'iron_ore': Color.rgb(217, 194, 180),
        'coal_ore': Color.rgb(52, 52, 56),
        'redstone_ore': Color.rgb(171, 28, 28),
        'lapis_ore': Color.rgb(31, 87, 166),
        'ancient_debris': Color.rgb(101, 67, 57),
        'nether_portal': Color.rgb(140, 36, 209),
        'end_portal': Color.rgb(13, 26, 33),
        'chest': Color.rgb(160, 110, 70),
        'spawner': Color.rgb(25, 25, 35),
        'stone': Color.rgb(125, 125, 125),
        'default': Color.rgb(100, 100, 100)
    ]

    /**
     * Create block grid viewer.
     */
    BlockGridViewer() {
        buildUI()
    }

    /**
     * Load blocks from BlockDatabase.
     *
     * @param dbFile The blocks.db SQLite database file
     * @return True if loaded successfully
     */
    boolean loadBlocks(File dbFile) {
        if (!dbFile || !dbFile.exists()) {
            LOGGER.error("Block database file not found: ${dbFile?.absolutePath}")
            updateStatus("Block database not found", true)
            return false
        }

        try {
            // Open database
            blockDatabase = BlockDatabase.openForQuery(dbFile)
            if (!blockDatabase) {
                LOGGER.error("Failed to open block database")
                updateStatus("Failed to open database", true)
                return false
            }

            // Load all blocks
            List<Map> blockRows = blockDatabase.queryAllBlocks()
            LOGGER.info("Loading ${blockRows.size()} blocks from database")

            allBlocks.clear()
            blockRows.each { row ->
                BlockEntry entry = new BlockEntry(
                    blockType: row.block_type,
                    dimension: row.dimension,
                    x: row.x as int,
                    y: row.y as int,
                    z: row.z as int,
                    properties: row.properties,
                    regionFile: row.region_file
                )
                allBlocks.add(entry)
            }

            // Initialize filtered list
            filteredBlocks = new FilteredList<>(allBlocks, { true })

            // Rebuild grid
            rebuildGrid()

            updateStatus("Loaded ${allBlocks.size()} blocks", false)
            LOGGER.info("Block grid viewer loaded successfully")
            return true

        } catch (Exception e) {
            LOGGER.error("Failed to load blocks: ${e.message}", e)
            updateStatus("Error loading blocks: ${e.message}", true)
            return false
        }
    }

    /**
     * Build the UI layout.
     */
    private void buildUI() {
        padding = new Insets(15)

        // Top: Filter controls
        VBox topSection = new VBox(10)
        topSection.padding = new Insets(0, 0, 15, 0)

        // Title
        Label title = new Label('Block Grid Viewer')
        title.font = Font.font('System', FontWeight.BOLD, 18)

        // Filter row 1: Search + Category + Dimension
        HBox filterRow1 = new HBox(10)
        filterRow1.alignment = Pos.CENTER_LEFT

        // Search field
        searchField = new TextField()
        searchField.promptText = 'Search block type (e.g., diamond_ore, spawner)...'
        HBox.setHgrow(searchField, Priority.ALWAYS)
        searchField.textProperty().addListener { obs, oldVal, newVal ->
            currentSearchText = newVal?.toLowerCase() ?: ''
            applyFilters()
        }

        // Category filter
        Label categoryLabel = new Label('Category:')
        categoryFilter = new ComboBox<>()
        categoryFilter.items.addAll('All', *BLOCK_CATEGORIES.keySet().sort())
        categoryFilter.value = 'All'
        categoryFilter.onAction = { event ->
            currentCategory = categoryFilter.value
            applyFilters()
        }

        // Dimension filter
        Label dimensionLabel = new Label('Dimension:')
        dimensionFilter = new ComboBox<>()
        dimensionFilter.items.addAll('All', 'overworld', 'nether', 'end')
        dimensionFilter.value = 'All'
        dimensionFilter.onAction = { event ->
            currentDimension = dimensionFilter.value
            applyFilters()
        }

        // Clear filters button
        clearFiltersBtn = new Button('Clear Filters')
        clearFiltersBtn.onAction = { event -> clearFilters() }

        filterRow1.children.addAll(
            searchField,
            categoryLabel, categoryFilter,
            dimensionLabel, dimensionFilter,
            clearFiltersBtn
        )

        // Status label
        statusLabel = new Label('No blocks loaded')
        statusLabel.style = '-fx-font-style: italic; -fx-text-fill: gray;'

        topSection.children.addAll(title, filterRow1, statusLabel)

        // Center: Block grid (scrollable)
        ScrollPane scrollPane = new ScrollPane()
        scrollPane.fitToWidth = true
        scrollPane.style = '-fx-background-color: derive(-fx-base, -5%);'

        blockGrid = new FlowPane(10, 10)
        blockGrid.padding = new Insets(10)
        blockGrid.prefWrapLength = 800  // Wrap after ~800px width

        scrollPane.content = blockGrid

        // Layout
        top = topSection
        center = scrollPane

        LOGGER.debug('BlockGridViewer UI built')
    }

    /**
     * Apply current filters to the block list.
     */
    private void applyFilters() {
        if (!filteredBlocks) {
            return
        }

        filteredBlocks.predicate = { BlockEntry entry ->
            // Category filter
            if (currentCategory != 'All') {
                List<String> categoryPatterns = BLOCK_CATEGORIES[currentCategory]
                boolean matchesCategory = categoryPatterns?.any { pattern ->
                    entry.blockType.contains(pattern)
                }
                if (!matchesCategory) {
                    return false
                }
            }

            // Dimension filter
            if (currentDimension != 'All' && entry.dimension != currentDimension) {
                return false
            }

            // Search text filter
            if (currentSearchText && !entry.blockType.toLowerCase().contains(currentSearchText)) {
                return false
            }

            return true
        }

        rebuildGrid()
        updateStatus("Showing ${filteredBlocks.size()} blocks", false)
    }

    /**
     * Clear all filters.
     */
    private void clearFilters() {
        searchField.text = ''
        categoryFilter.value = 'All'
        dimensionFilter.value = 'All'
        currentCategory = 'All'
        currentDimension = 'All'
        currentSearchText = ''
        applyFilters()
    }

    /**
     * Rebuild the block grid from filtered blocks.
     */
    private void rebuildGrid() {
        blockGrid.children.clear()

        if (!filteredBlocks || filteredBlocks.empty) {
            Label noDataLabel = new Label('No blocks match the current filters')
            noDataLabel.style = '-fx-font-size: 14px; -fx-text-fill: gray;'
            blockGrid.children.add(noDataLabel)
            return
        }

        // Create block slots (limit to 500 for performance)
        int displayLimit = 500
        int displayCount = Math.min(filteredBlocks.size(), displayLimit)

        for (int i = 0; i < displayCount; i++) {
            BlockEntry entry = filteredBlocks[i]
            blockGrid.children.add(createBlockSlot(entry))
        }

        if (filteredBlocks.size() > displayLimit) {
            Label limitLabel = new Label("Showing ${displayLimit} of ${filteredBlocks.size()} blocks (limit for performance)")
            limitLabel.style = '-fx-font-size: 12px; -fx-text-fill: orange; -fx-padding: 10;'
            blockGrid.children.add(0, limitLabel)
        }
    }

    /**
     * Create a block slot UI component.
     */
    private VBox createBlockSlot(BlockEntry entry) {
        VBox slot = new VBox(5)
        slot.alignment = Pos.CENTER
        slot.padding = new Insets(8)
        slot.prefWidth = 120
        slot.prefHeight = 130
        slot.style = '''
            -fx-background-color: derive(-fx-base, 8%);
            -fx-background-radius: 8;
            -fx-border-color: derive(-fx-base, -10%);
            -fx-border-width: 1;
            -fx-border-radius: 8;
            -fx-cursor: hand;
        '''

        // Block visualization (isometric view)
        Canvas canvas = createBlockCanvas(entry)

        // Block ID label (remove minecraft: prefix)
        String blockId = entry.blockType.replaceAll('minecraft:', '')
        Label idLabel = new Label(blockId)
        idLabel.style = '-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: -fx-text-base-color;'
        idLabel.wrapText = true
        idLabel.alignment = Pos.CENTER
        idLabel.maxWidth = 110

        // Coordinate badge
        Label coordLabel = new Label("${entry.x}, ${entry.y}, ${entry.z}")
        coordLabel.style = '''
            -fx-font-size: 9px;
            -fx-text-fill: derive(-fx-text-base-color, 30%);
            -fx-background-color: derive(-fx-base, -5%);
            -fx-padding: 2 6 2 6;
            -fx-background-radius: 10;
        '''

        // Dimension indicator (color-coded)
        Label dimLabel = new Label(entry.dimension.substring(0, 1).toUpperCase())
        dimLabel.style = """
            -fx-font-size: 8px;
            -fx-font-weight: bold;
            -fx-text-fill: white;
            -fx-background-color: ${getDimensionColor(entry.dimension)};
            -fx-padding: 2 4 2 4;
            -fx-background-radius: 3;
        """

        HBox badgeRow = new HBox(5)
        badgeRow.alignment = Pos.CENTER
        badgeRow.children.addAll(coordLabel, dimLabel)

        slot.children.addAll(canvas, idLabel, badgeRow)

        // Hover effects
        slot.onMouseEntered = { event ->
            slot.style = '''
                -fx-background-color: derive(-fx-accent, 70%);
                -fx-background-radius: 8;
                -fx-border-color: -fx-accent;
                -fx-border-width: 2;
                -fx-border-radius: 8;
                -fx-cursor: hand;
            '''
        }

        slot.onMouseExited = { event ->
            slot.style = '''
                -fx-background-color: derive(-fx-base, 8%);
                -fx-background-radius: 8;
                -fx-border-color: derive(-fx-base, -10%);
                -fx-border-width: 1;
                -fx-border-radius: 8;
                -fx-cursor: hand;
            '''
        }

        // Click handler: Copy teleport command
        slot.onMouseClicked = { event ->
            copyTeleportCommand(entry)
        }

        // Tooltip with detailed info
        Tooltip tooltip = createBlockTooltip(entry)
        Tooltip.install(slot, tooltip)

        return slot
    }

    /**
     * Create isometric block visualization using Canvas.
     */
    private Canvas createBlockCanvas(BlockEntry entry) {
        Canvas canvas = new Canvas(60, 40)
        GraphicsContext gc = canvas.graphicsContext2D

        // Get block color
        Color blockColor = getBlockColor(entry.blockType)

        // Draw isometric cube (simplified: top face only, rotated 45°)
        // Top face coordinates (diamond shape)
        double centerX = 30
        double centerY = 20
        double size = 18

        // Diamond shape points (isometric top face)
        double[] xPoints = [
            centerX,          // Top
            centerX + size,   // Right
            centerX,          // Bottom
            centerX - size    // Left
        ] as double[]
        double[] yPoints = [
            centerY - size/2, // Top
            centerY,          // Right
            centerY + size/2, // Bottom
            centerY           // Left
        ] as double[]

        // Fill top face
        gc.fill = blockColor
        gc.fillPolygon(xPoints, yPoints, 4)

        // Outline for definition
        gc.stroke = blockColor.darker()
        gc.lineWidth = 1.5
        gc.strokePolygon(xPoints, yPoints, 4)

        // Add highlight for 3D effect (lighter left edge)
        gc.stroke = blockColor.brighter().brighter()
        gc.lineWidth = 1
        gc.strokeLine(xPoints[0], yPoints[0], xPoints[3], yPoints[3])  // Top-left edge

        // Add shadow for 3D effect (darker right edge)
        gc.stroke = blockColor.darker().darker()
        gc.strokeLine(xPoints[1], yPoints[1], xPoints[2], yPoints[2])  // Right-bottom edge

        return canvas
    }

    /**
     * Get color for block type.
     */
    private Color getBlockColor(String blockType) {
        String normalized = blockType.replaceAll('minecraft:', '').toLowerCase()

        // Check exact matches first
        Color exactMatch = BLOCK_COLORS.find { key, value ->
            normalized.contains(key)
        }?.value

        if (exactMatch) {
            return exactMatch
        }

        // Fallback: Categorize by type
        if (normalized.contains('ore')) {
            return Color.rgb(150, 150, 150)  // Generic ore gray
        }
        if (normalized.contains('portal')) {
            return Color.rgb(140, 36, 209)  // Purple
        }
        if (normalized.contains('chest') || normalized.contains('barrel')) {
            return Color.rgb(160, 110, 70)  // Wood brown
        }
        if (normalized.contains('redstone')) {
            return Color.rgb(171, 28, 28)  // Redstone red
        }

        return BLOCK_COLORS['default']
    }

    /**
     * Get dimension badge color.
     */
    private String getDimensionColor(String dimension) {
        switch (dimension) {
            case 'overworld':
                return '#4CAF50'  // Green
            case 'nether':
                return '#F44336'  // Red
            case 'end':
                return '#9C27B0'  // Purple
            default:
                return '#757575'  // Gray
        }
    }

    /**
     * Create detailed tooltip for block.
     */
    private Tooltip createBlockTooltip(BlockEntry entry) {
        StringBuilder sb = new StringBuilder()

        // Block type
        sb.append("Block: ${entry.blockType}\n")

        // Coordinates
        sb.append("Location: (${entry.x}, ${entry.y}, ${entry.z})\n")

        // Dimension
        sb.append("Dimension: ${entry.dimension}\n")

        // Block state properties (if present)
        if (entry.properties) {
            try {
                def props = new groovy.json.JsonSlurper().parseText(entry.properties)
                if (props instanceof Map && !props.isEmpty()) {
                    sb.append("\nProperties:\n")
                    props.each { key, value ->
                        sb.append("  ${key}: ${value}\n")
                    }
                }
            } catch (Exception e) {
                // Ignore JSON parse errors
            }
        }

        // Region file (if present)
        if (entry.regionFile) {
            sb.append("\nRegion: ${entry.regionFile}\n")
        }

        // Action hint
        sb.append("\n[Click] Copy /tp command")

        Tooltip tooltip = new Tooltip(sb.toString())
        tooltip.style = '-fx-font-size: 11px; -fx-font-family: "Courier New";'
        tooltip.showDelay = javafx.util.Duration.millis(300)

        return tooltip
    }

    /**
     * Copy teleport command to clipboard.
     */
    private void copyTeleportCommand(BlockEntry entry) {
        String command = "/tp @s ${entry.x} ${entry.y} ${entry.z}"

        ClipboardContent content = new ClipboardContent()
        content.putString(command)
        Clipboard.systemClipboard.setContent(content)

        // Flash status message
        updateStatus("Copied: ${command}", false)

        // Reset status after 2 seconds
        Platform.runLater({
            Thread.sleep(2000)
            updateStatus("Showing ${filteredBlocks?.size() ?: 0} blocks", false)
        } as Runnable)

        LOGGER.debug("Copied teleport command: ${command}")
    }

    /**
     * Filter to specific block type (e.g., from "Show all of type" button).
     */
    void filterByBlockType(String blockType) {
        searchField.text = blockType.replaceAll('minecraft:', '')
        categoryFilter.value = 'All'
        dimensionFilter.value = 'All'
        currentSearchText = searchField.text.toLowerCase()
        currentCategory = 'All'
        currentDimension = 'All'
        applyFilters()

        LOGGER.debug("Filtered to block type: ${blockType}")
    }

    /**
     * Update status label.
     */
    private void updateStatus(String message, boolean isError) {
        Platform.runLater {
            statusLabel.text = message
            statusLabel.style = isError ?
                '-fx-font-style: italic; -fx-text-fill: red;' :
                '-fx-font-style: italic; -fx-text-fill: gray;'
        }
    }

    /**
     * Close database when done.
     */
    void close() {
        blockDatabase?.close()
    }

    /**
     * Block entry data class.
     */
    static class BlockEntry {
        String blockType
        String dimension
        int x
        int y
        int z
        String properties
        String regionFile

        // Observable properties for TableView (if needed later)
        SimpleStringProperty blockTypeProperty() {
            new SimpleStringProperty(blockType)
        }

        SimpleStringProperty dimensionProperty() {
            new SimpleStringProperty(dimension)
        }

        SimpleStringProperty coordinatesProperty() {
            new SimpleStringProperty("${x}, ${y}, ${z}")
        }
    }

}

package viewers

import groovy.json.JsonSlurper
import javafx.application.Platform
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.collections.transformation.FilteredList
import javafx.collections.transformation.SortedList
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.control.cell.PropertyValueFactory
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.input.KeyCode
import javafx.scene.layout.*
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import javafx.scene.text.Text
import javafx.scene.text.TextFlow
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Custom Names Viewer - Interactive table view for custom-named items and entities.
 *
 * Features:
 * - Table view with filtering and sorting
 * - Group-by mode to collapse duplicate names
 * - Multi-dimensional filtering (text, type, dimension, category)
 * - Context menu for copying and navigation
 * - Detail panel showing full item information
 */
class CustomNamesViewer extends BorderPane {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomNamesViewer)

    // UI Components
    TableView<CustomNameEntry> table
    TextField searchField
    ComboBox<String> typeFilter
    ComboBox<String> dimensionFilter
    ComboBox<String> categoryFilter
    CheckBox groupByNameCheckBox
    VBox detailPanel
    Label statusLabel
    TextFlow detailTextFlow

    // Data
    ObservableList<CustomNameEntry> allEntries = FXCollections.observableArrayList()
    FilteredList<CustomNameEntry> filteredEntries
    SortedList<CustomNameEntry> sortedEntries

    // Item category mappings
    private static final Map<String, String> ITEM_CATEGORIES = [
        // Tools
        'minecraft:diamond_pickaxe': 'Tools',
        'minecraft:diamond_axe': 'Tools',
        'minecraft:diamond_shovel': 'Tools',
        'minecraft:diamond_hoe': 'Tools',
        'minecraft:iron_pickaxe': 'Tools',
        'minecraft:iron_axe': 'Tools',
        'minecraft:iron_shovel': 'Tools',
        'minecraft:iron_hoe': 'Tools',
        'minecraft:golden_pickaxe': 'Tools',
        'minecraft:golden_axe': 'Tools',
        'minecraft:golden_shovel': 'Tools',
        'minecraft:golden_hoe': 'Tools',
        'minecraft:netherite_pickaxe': 'Tools',
        'minecraft:netherite_axe': 'Tools',
        'minecraft:netherite_shovel': 'Tools',
        'minecraft:netherite_hoe': 'Tools',
        'minecraft:stone_pickaxe': 'Tools',
        'minecraft:stone_axe': 'Tools',
        'minecraft:stone_shovel': 'Tools',
        'minecraft:stone_hoe': 'Tools',
        'minecraft:wooden_pickaxe': 'Tools',
        'minecraft:wooden_axe': 'Tools',
        'minecraft:wooden_shovel': 'Tools',
        'minecraft:wooden_hoe': 'Tools',
        'minecraft:fishing_rod': 'Tools',
        'minecraft:shears': 'Tools',
        'minecraft:flint_and_steel': 'Tools',

        // Weapons
        'minecraft:diamond_sword': 'Weapons',
        'minecraft:iron_sword': 'Weapons',
        'minecraft:golden_sword': 'Weapons',
        'minecraft:netherite_sword': 'Weapons',
        'minecraft:stone_sword': 'Weapons',
        'minecraft:wooden_sword': 'Weapons',
        'minecraft:bow': 'Weapons',
        'minecraft:crossbow': 'Weapons',
        'minecraft:trident': 'Weapons',

        // Armor
        'minecraft:diamond_helmet': 'Armor',
        'minecraft:diamond_chestplate': 'Armor',
        'minecraft:diamond_leggings': 'Armor',
        'minecraft:diamond_boots': 'Armor',
        'minecraft:iron_helmet': 'Armor',
        'minecraft:iron_chestplate': 'Armor',
        'minecraft:iron_leggings': 'Armor',
        'minecraft:iron_boots': 'Armor',
        'minecraft:golden_helmet': 'Armor',
        'minecraft:golden_chestplate': 'Armor',
        'minecraft:golden_leggings': 'Armor',
        'minecraft:golden_boots': 'Armor',
        'minecraft:netherite_helmet': 'Armor',
        'minecraft:netherite_chestplate': 'Armor',
        'minecraft:netherite_leggings': 'Armor',
        'minecraft:netherite_boots': 'Armor',
        'minecraft:chainmail_helmet': 'Armor',
        'minecraft:chainmail_chestplate': 'Armor',
        'minecraft:chainmail_leggings': 'Armor',
        'minecraft:chainmail_boots': 'Armor',
        'minecraft:leather_helmet': 'Armor',
        'minecraft:leather_chestplate': 'Armor',
        'minecraft:leather_leggings': 'Armor',
        'minecraft:leather_boots': 'Armor',
        'minecraft:turtle_helmet': 'Armor',
        'minecraft:elytra': 'Armor',
        'minecraft:shield': 'Armor',

        // Entities
        'minecraft:armor_stand': 'Entities',
        'minecraft:item_frame': 'Entities',
        'minecraft:glow_item_frame': 'Entities',
        'minecraft:painting': 'Entities',
        'minecraft:minecart': 'Entities',
        'minecraft:chest_minecart': 'Entities',
        'minecraft:furnace_minecart': 'Entities',
        'minecraft:hopper_minecart': 'Entities',
        'minecraft:tnt_minecart': 'Entities',
        'minecraft:command_block_minecart': 'Entities',
        'minecraft:boat': 'Entities',
        'minecraft:horse': 'Entities',
        'minecraft:donkey': 'Entities',
        'minecraft:mule': 'Entities',
        'minecraft:llama': 'Entities',
        'minecraft:villager': 'Entities',
    ]

    CustomNamesViewer() {
        buildUI()
        setupEventHandlers()
    }

    private void buildUI() {
        // Top toolbar
        HBox toolbar = createToolbar()
        setTop(toolbar)

        // Center: Split view (table + detail panel)
        SplitPane splitPane = new SplitPane()
        splitPane.orientation = Orientation.HORIZONTAL

        // Left: Table
        table = createTable()
        splitPane.items.add(table)

        // Right: Detail panel
        detailPanel = createDetailPanel()
        splitPane.items.add(detailPanel)

        splitPane.setDividerPositions(0.6)
        SplitPane.setResizableWithParent(detailPanel, false)

        setCenter(splitPane)

        // Bottom: Status bar
        statusLabel = new Label('No data loaded')
        statusLabel.padding = new Insets(5, 10, 5, 10)
        statusLabel.style = '-fx-font-size: 11px; -fx-text-fill: gray;'
        setBottom(statusLabel)
    }

    private HBox createToolbar() {
        HBox toolbar = new HBox(10)
        toolbar.padding = new Insets(10)
        toolbar.alignment = Pos.CENTER_LEFT

        // Search field
        searchField = new TextField()
        searchField.promptText = 'Search custom names...'
        searchField.prefWidth = 250
        HBox.setHgrow(searchField, Priority.ALWAYS)

        // Type filter
        Label typeLabel = new Label('Type:')
        typeFilter = new ComboBox<>()
        typeFilter.items.addAll('All', 'Items', 'Entities')
        typeFilter.value = 'All'
        typeFilter.prefWidth = 120

        // Dimension filter
        Label dimensionLabel = new Label('Dimension:')
        dimensionFilter = new ComboBox<>()
        dimensionFilter.items.addAll('All', 'overworld', 'nether', 'end')
        dimensionFilter.value = 'All'
        dimensionFilter.prefWidth = 120

        // Category filter
        Label categoryLabel = new Label('Category:')
        categoryFilter = new ComboBox<>()
        categoryFilter.items.addAll('All', 'Tools', 'Weapons', 'Armor', 'Entities', 'Other')
        categoryFilter.value = 'All'
        categoryFilter.prefWidth = 120

        // Group by checkbox
        groupByNameCheckBox = new CheckBox('Group by name')
        groupByNameCheckBox.selected = false
        groupByNameCheckBox.tooltip = new Tooltip('Collapse duplicate names and show count')

        // Clear filters button
        Button clearBtn = new Button('Clear Filters')
        clearBtn.onAction = { clearFilters() }

        toolbar.children.addAll(
            searchField,
            new Separator(Orientation.VERTICAL),
            typeLabel, typeFilter,
            dimensionLabel, dimensionFilter,
            categoryLabel, categoryFilter,
            new Separator(Orientation.VERTICAL),
            groupByNameCheckBox,
            clearBtn
        )

        return toolbar
    }

    private TableView<CustomNameEntry> createTable() {
        TableView<CustomNameEntry> tbl = new TableView<>()

        // Icon column (future enhancement - placeholder for now)
        TableColumn<CustomNameEntry, String> iconCol = new TableColumn<>('')
        iconCol.prefWidth = 40
        iconCol.cellValueFactory = { new SimpleStringProperty('') }
        iconCol.sortable = false

        // Display Name column
        TableColumn<CustomNameEntry, String> nameCol = new TableColumn<>('Custom Name')
        nameCol.prefWidth = 250
        nameCol.cellValueFactory = new PropertyValueFactory<>('displayName')
        nameCol.cellFactory = { column ->
            new TableCell<CustomNameEntry, String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty)
                    if (empty || item == null) {
                        text = null
                        graphic = null
                        style = ''
                    } else {
                        text = item
                        // Apply Minecraft formatting codes styling
                        if (item.contains('§')) {
                            style = '-fx-font-weight: bold;'
                        }
                    }
                }
            }
        }

        // Type column
        TableColumn<CustomNameEntry, String> typeCol = new TableColumn<>('Type')
        typeCol.prefWidth = 200
        typeCol.cellValueFactory = new PropertyValueFactory<>('itemOrEntityId')

        // Coordinates column
        TableColumn<CustomNameEntry, String> coordsCol = new TableColumn<>('Coordinates')
        coordsCol.prefWidth = 150
        coordsCol.cellValueFactory = { entry ->
            new SimpleStringProperty("${entry.value.x}, ${entry.value.y}, ${entry.value.z}")
        }

        // Dimension column
        TableColumn<CustomNameEntry, String> dimCol = new TableColumn<>('Dimension')
        dimCol.prefWidth = 100
        dimCol.cellValueFactory = new PropertyValueFactory<>('dimension')

        // Container/Location column
        TableColumn<CustomNameEntry, String> locationCol = new TableColumn<>('Container')
        locationCol.prefWidth = 200
        locationCol.cellValueFactory = new PropertyValueFactory<>('location')
        locationCol.cellFactory = { column ->
            new TableCell<CustomNameEntry, String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty)
                    if (empty || item == null) {
                        text = null
                        tooltip = null
                    } else {
                        // Truncate long location strings
                        if (item.length() > 30) {
                            text = item.substring(0, 27) + '...'
                            tooltip = new Tooltip(item)
                        } else {
                            text = item
                            tooltip = null
                        }
                    }
                }
            }
        }

        // Count column (only visible in group-by mode)
        TableColumn<CustomNameEntry, Integer> countCol = new TableColumn<>('Count')
        countCol.prefWidth = 80
        countCol.cellValueFactory = new PropertyValueFactory<>('count')
        countCol.visible = false

        tbl.columns.addAll(iconCol, nameCol, typeCol, coordsCol, dimCol, locationCol, countCol)

        // Setup filtered/sorted data
        filteredEntries = new FilteredList<>(allEntries, p -> true)
        sortedEntries = new SortedList<>(filteredEntries)
        sortedEntries.comparatorProperty().bind(tbl.comparatorProperty())
        tbl.items = sortedEntries

        // Enable multi-selection
        tbl.selectionModel.selectionMode = SelectionMode.MULTIPLE

        // Setup context menu
        tbl.contextMenu = createContextMenu()

        // Setup row double-click to show detail
        tbl.onMouseClicked = { event ->
            if (event.clickCount == 2 && tbl.selectionModel.selectedItem) {
                showDetail(tbl.selectionModel.selectedItem)
            }
        }

        return tbl
    }

    private VBox createDetailPanel() {
        VBox panel = new VBox(10)
        panel.padding = new Insets(10)
        panel.prefWidth = 400

        Label titleLabel = new Label('Details')
        titleLabel.font = Font.font('System', FontWeight.BOLD, 14)

        detailTextFlow = new TextFlow()
        detailTextFlow.style = '-fx-background-color: -fx-control-inner-background; -fx-padding: 10;'

        ScrollPane scrollPane = new ScrollPane(detailTextFlow)
        scrollPane.fitToWidth = true
        VBox.setVgrow(scrollPane, Priority.ALWAYS)

        panel.children.addAll(titleLabel, scrollPane)
        return panel
    }

    private ContextMenu createContextMenu() {
        ContextMenu menu = new ContextMenu()

        MenuItem copyNameItem = new MenuItem('Copy Name')
        copyNameItem.onAction = { copyToClipboard('name') }

        MenuItem copyTpItem = new MenuItem('Copy TP Command')
        copyTpItem.onAction = { copyToClipboard('tp') }

        MenuItem showAllSameNameItem = new MenuItem('Show All with Same Name')
        showAllSameNameItem.onAction = { showAllWithSameName() }

        menu.items.addAll(copyNameItem, copyTpItem, new SeparatorMenuItem(), showAllSameNameItem)
        return menu
    }

    private void setupEventHandlers() {
        // Search field
        searchField.textProperty().addListener { obs, oldVal, newVal -> applyFilters() }

        // Filters
        typeFilter.valueProperty().addListener { obs, oldVal, newVal -> applyFilters() }
        dimensionFilter.valueProperty().addListener { obs, oldVal, newVal -> applyFilters() }
        categoryFilter.valueProperty().addListener { obs, oldVal, newVal -> applyFilters() }

        // Group by checkbox
        groupByNameCheckBox.selectedProperty().addListener { obs, oldVal, newVal ->
            toggleGroupByMode(newVal)
        }

        // Selection change listener
        table.selectionModel.selectedItemProperty().addListener { obs, oldVal, newVal ->
            if (newVal != null) {
                showDetail(newVal)
            }
        }

        // Keyboard shortcuts
        table.onKeyPressed = { event ->
            if (event.code == KeyCode.C && event.controlDown) {
                copyToClipboard('name')
            } else if (event.code == KeyCode.DELETE) {
                // Future: Remove from view
            }
        }
    }

    /**
     * Load custom names from JSON file.
     */
    void loadData(File dataFile) {
        if (!dataFile || !dataFile.exists()) {
            LOGGER.error("Data file not found: ${dataFile?.absolutePath}")
            statusLabel.text = 'Error: Data file not found'
            return
        }

        try {
            LOGGER.info("Loading custom names from: ${dataFile.absolutePath}")
            JsonSlurper slurper = new JsonSlurper()
            def data = slurper.parse(dataFile)

            if (!(data instanceof List)) {
                LOGGER.error("Invalid data format: expected List, got ${data.getClass()}")
                statusLabel.text = 'Error: Invalid data format'
                return
            }

            loadFromList(data as List<Map>)
        } catch (Exception e) {
            LOGGER.error("Failed to load custom names: ${e.message}", e)
            statusLabel.text = "Error: ${e.message}"
        }
    }

    /**
     * Load custom names from CSV file.
     */
    void loadFromCsv(File csvFile) {
        if (!csvFile || !csvFile.exists()) {
            LOGGER.error("CSV file not found: ${csvFile?.absolutePath}")
            statusLabel.text = 'Error: CSV file not found'
            return
        }

        try {
            List<String> lines = csvFile.readLines()
            if (lines.size() < 2) {
                statusLabel.text = 'Error: Empty CSV file'
                return
            }

            // Parse header
            List<String> headers = parseCsvLine(lines[0])

            // Parse data
            List<Map> dataList = []
            for (int i = 1; i < lines.size(); i++) {
                List<String> values = parseCsvLine(lines[i])
                if (values.size() == headers.size()) {
                    Map row = [:]
                    headers.eachWithIndex { header, idx ->
                        row[header] = values[idx]
                    }
                    dataList << row
                }
            }

            loadFromList(dataList)
        } catch (Exception e) {
            LOGGER.error("Failed to load CSV: ${e.message}", e)
            statusLabel.text = "Error: ${e.message}"
        }
    }

    private void loadFromList(List<Map> dataList) {
        Platform.runLater {
            allEntries.clear()

            dataList.each { Map item ->
                CustomNameEntry entry = new CustomNameEntry(
                    type: item.type as String,
                    itemOrEntityId: item.itemOrEntityId as String,
                    displayName: item.customName as String,
                    x: parseCoordinate(item.x),
                    y: parseCoordinate(item.y),
                    z: parseCoordinate(item.z),
                    location: item.location as String,
                    dimension: extractDimension(item.location as String)
                )
                allEntries.add(entry)
            }

            LOGGER.info("Loaded ${allEntries.size()} custom name entries")
            updateStatusLabel()
        }
    }

    private int parseCoordinate(Object value) {
        if (value instanceof Integer) return value
        if (value instanceof String) {
            try {
                return Integer.parseInt(value)
            } catch (NumberFormatException e) {
                return 0
            }
        }
        return 0
    }

    private String extractDimension(String location) {
        if (!location) return 'unknown'

        String lower = location.toLowerCase()
        if (lower.contains('nether') || lower.contains('dim-1')) return 'nether'
        if (lower.contains('end') || lower.contains('dim1')) return 'end'
        return 'overworld'
    }

    private List<String> parseCsvLine(String line) {
        List<String> fields = []
        StringBuilder currentField = new StringBuilder()
        boolean inQuotes = false

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i)

            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    currentField.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                fields << currentField.toString()
                currentField.setLength(0)
            } else {
                currentField.append(c)
            }
        }

        fields << currentField.toString()
        return fields
    }

    private void applyFilters() {
        filteredEntries.predicate = { CustomNameEntry entry ->
            // Search filter
            String searchText = searchField.text?.toLowerCase()
            if (searchText && !entry.displayName.toLowerCase().contains(searchText) &&
                !entry.itemOrEntityId.toLowerCase().contains(searchText)) {
                return false
            }

            // Type filter
            String typeVal = typeFilter.value
            if (typeVal && typeVal != 'All') {
                if (typeVal == 'Items' && entry.type != 'item') return false
                if (typeVal == 'Entities' && entry.type != 'entity') return false
            }

            // Dimension filter
            String dimVal = dimensionFilter.value
            if (dimVal && dimVal != 'All' && entry.dimension != dimVal) {
                return false
            }

            // Category filter
            String catVal = categoryFilter.value
            if (catVal && catVal != 'All') {
                String itemCategory = getItemCategory(entry.itemOrEntityId)
                if (itemCategory != catVal) return false
            }

            return true
        }

        updateStatusLabel()
    }

    private void toggleGroupByMode(boolean groupBy) {
        if (groupBy) {
            // Group entries by display name
            Map<String, List<CustomNameEntry>> grouped = allEntries.groupBy { it.displayName }

            ObservableList<CustomNameEntry> groupedList = FXCollections.observableArrayList()
            grouped.each { name, entries ->
                if (entries.size() == 1) {
                    groupedList.add(entries[0])
                } else {
                    // Create a grouped entry
                    CustomNameEntry groupedEntry = entries[0].clone()
                    groupedEntry.count = entries.size()
                    groupedEntry.location = "${entries.size()} locations"
                    groupedList.add(groupedEntry)
                }
            }

            filteredEntries = new FilteredList<>(groupedList, p -> true)
            sortedEntries = new SortedList<>(filteredEntries)
            sortedEntries.comparatorProperty().bind(table.comparatorProperty())
            table.items = sortedEntries

            // Show count column
            table.columns.find { it.text == 'Count' }.visible = true
        } else {
            // Restore original list
            filteredEntries = new FilteredList<>(allEntries, p -> true)
            sortedEntries = new SortedList<>(filteredEntries)
            sortedEntries.comparatorProperty().bind(table.comparatorProperty())
            table.items = sortedEntries

            // Hide count column
            table.columns.find { it.text == 'Count' }.visible = false
        }

        applyFilters()
    }

    private void showDetail(CustomNameEntry entry) {
        if (!entry) return

        detailTextFlow.children.clear()

        // Custom name with formatting
        Text nameHeader = new Text('Custom Name:\n')
        nameHeader.font = Font.font('System', FontWeight.BOLD, 13)

        Text nameText = new Text("${entry.displayName}\n\n")
        nameText.font = Font.font('System', 16)
        nameText.style = '-fx-fill: -fx-accent;'

        // Item/Entity details
        Text typeHeader = new Text('Type:\n')
        typeHeader.font = Font.font('System', FontWeight.BOLD, 13)

        Text typeText = new Text("${entry.type == 'item' ? 'Item' : 'Entity'}: ${entry.itemOrEntityId}\n")
        Text categoryText = new Text("Category: ${getItemCategory(entry.itemOrEntityId)}\n\n")

        // Location details
        Text locationHeader = new Text('Location:\n')
        locationHeader.font = Font.font('System', FontWeight.BOLD, 13)

        Text coordsText = new Text("Coordinates: (${entry.x}, ${entry.y}, ${entry.z})\n")
        Text dimText = new Text("Dimension: ${entry.dimension}\n")
        Text containerText = new Text("Container: ${entry.location}\n\n")

        // TP command
        Text tpHeader = new Text('Teleport Command:\n')
        tpHeader.font = Font.font('System', FontWeight.BOLD, 13)

        Text tpText = new Text("/tp @s ${entry.x} ${entry.y} ${entry.z}\n")
        tpText.style = '-fx-font-family: monospace; -fx-fill: green;'

        detailTextFlow.children.addAll(
            nameHeader, nameText,
            typeHeader, typeText, categoryText,
            locationHeader, coordsText, dimText, containerText,
            tpHeader, tpText
        )

        // If in group mode and entry has count > 1, show all locations
        if (groupByNameCheckBox.selected && entry.count > 1) {
            List<CustomNameEntry> allWithSameName = allEntries.findAll { it.displayName == entry.displayName }

            Text locationsHeader = new Text("\nAll Locations (${allWithSameName.size()}):\n")
            locationsHeader.font = Font.font('System', FontWeight.BOLD, 13)
            detailTextFlow.children.add(locationsHeader)

            allWithSameName.eachWithIndex { loc, idx ->
                Text locText = new Text("${idx + 1}. (${loc.x}, ${loc.y}, ${loc.z}) - ${loc.dimension} - ${loc.location}\n")
                detailTextFlow.children.add(locText)
            }
        }
    }

    private void copyToClipboard(String type) {
        CustomNameEntry selected = table.selectionModel.selectedItem
        if (!selected) return

        ClipboardContent content = new ClipboardContent()

        switch (type) {
            case 'name':
                content.putString(selected.displayName)
                LOGGER.debug("Copied name to clipboard: ${selected.displayName}")
                break
            case 'tp':
                String tpCommand = "/tp @s ${selected.x} ${selected.y} ${selected.z}"
                content.putString(tpCommand)
                LOGGER.debug("Copied TP command to clipboard: ${tpCommand}")
                break
        }

        Clipboard.systemClipboard.setContent(content)
    }

    private void showAllWithSameName() {
        CustomNameEntry selected = table.selectionModel.selectedItem
        if (!selected) return

        // Set search field to the selected name
        searchField.text = selected.displayName

        // Clear other filters
        typeFilter.value = 'All'
        dimensionFilter.value = 'All'
        categoryFilter.value = 'All'

        // Disable group by
        groupByNameCheckBox.selected = false
    }

    private void clearFilters() {
        searchField.text = ''
        typeFilter.value = 'All'
        dimensionFilter.value = 'All'
        categoryFilter.value = 'All'
        groupByNameCheckBox.selected = false
    }

    private String getItemCategory(String itemOrEntityId) {
        return ITEM_CATEGORIES.getOrDefault(itemOrEntityId, 'Other')
    }

    private void updateStatusLabel() {
        int total = allEntries.size()
        int filtered = filteredEntries.size()

        if (total == filtered) {
            statusLabel.text = "Showing ${total} custom name(s)"
        } else {
            statusLabel.text = "Showing ${filtered} of ${total} custom name(s)"
        }
    }

    /**
     * Data model for a single custom name entry.
     */
    static class CustomNameEntry implements Cloneable {
        String type  // 'item' or 'entity'
        String itemOrEntityId  // e.g., 'minecraft:diamond_sword'
        String displayName  // The custom name
        int x, y, z  // Coordinates
        String location  // Container or location description
        String dimension  // 'overworld', 'nether', 'end'
        int count = 1  // For grouped entries

        @Override
        CustomNameEntry clone() {
            return new CustomNameEntry(
                type: this.type,
                itemOrEntityId: this.itemOrEntityId,
                displayName: this.displayName,
                x: this.x,
                y: this.y,
                z: this.z,
                location: this.location,
                dimension: this.dimension,
                count: this.count
            )
        }
    }
}

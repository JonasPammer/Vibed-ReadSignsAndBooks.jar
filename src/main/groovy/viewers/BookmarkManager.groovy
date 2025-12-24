package viewers

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import javafx.application.Platform
import javafx.beans.property.SimpleStringProperty
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.function.Consumer

/**
 * Bookmark Manager for saving and managing coordinate bookmarks across all viewers.
 *
 * Features:
 * - Save/load bookmarks to persistent JSON file
 * - Quick-add from any viewer (right-click context menu)
 * - Categorization by dimension (overworld, nether, end)
 * - Icon support for different bookmark types
 * - Notes and metadata tracking
 * - Jump-to-location integration with Map Viewer
 * - Import from extraction results (books, signs, items, portals)
 *
 * Storage Format:
 * {
 *   "bookmarks": [
 *     {
 *       "name": "Spawn Portal",
 *       "x": 100, "y": 64, "z": -200,
 *       "dimension": "overworld",
 *       "icon": "🟣",
 *       "notes": "Main hub portal",
 *       "createdAt": 1234567890000,
 *       "source": "portal",
 *       "color": "#FF00FF"
 *     }
 *   ]
 * }
 */
class BookmarkManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(BookmarkManager)
    static final String BOOKMARKS_FILE = "bookmarks.json"

    List<Bookmark> bookmarks = []
    File storageFile
    List<Runnable> changeListeners = []

    /**
     * Bookmark data class
     */
    static class Bookmark {
        String name
        int x, y, z
        String dimension      // overworld, nether, end
        String icon          // emoji or icon name
        String notes
        long createdAt
        String source        // Where it was bookmarked from: book, sign, item, portal, block, manual
        String color         // Hex color for marker (e.g., "#FF00FF")
        String category      // Optional category/tag

        // Computed property for display
        String getCoordinates() {
            return "($x, $y, $z)"
        }

        String getFormattedDate() {
            def instant = Instant.ofEpochMilli(createdAt)
            def formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .withZone(ZoneId.systemDefault())
            return formatter.format(instant)
        }

        String getDimensionIcon() {
            switch (dimension) {
                case 'overworld': return '🌍'
                case 'nether': return '🔥'
                case 'end': return '⭐'
                default: return '📍'
            }
        }

        @Override
        String toString() {
            return "$icon $name ${getCoordinates()} ${getDimensionIcon()}"
        }

        Map<String, Object> toMap() {
            return [
                    name: name,
                    x: x, y: y, z: z,
                    dimension: dimension,
                    icon: icon,
                    notes: notes ?: '',
                    createdAt: createdAt,
                    source: source ?: 'manual',
                    color: color ?: getDefaultColor(),
                    category: category ?: ''
            ]
        }

        static Bookmark fromMap(Map<String, Object> map) {
            return new Bookmark(
                    name: map.name as String,
                    x: map.x as int,
                    y: map.y as int,
                    z: map.z as int,
                    dimension: map.dimension as String,
                    icon: map.icon as String,
                    notes: map.notes as String ?: '',
                    createdAt: map.createdAt as long,
                    source: map.source as String ?: 'manual',
                    color: map.color as String,
                    category: map.category as String ?: ''
            )
        }

        private String getDefaultColor() {
            switch (source) {
                case 'book': return '#8B4513'   // Brown
                case 'sign': return '#D2691E'   // Chocolate
                case 'portal': return '#9400D3' // Dark Violet
                case 'item': return '#FFD700'   // Gold
                case 'block': return '#808080'  // Gray
                default: return '#FF0000'       // Red
            }
        }
    }

    /**
     * Load bookmarks from JSON file in output folder
     */
    void loadBookmarks(File outputFolder) {
        storageFile = new File(outputFolder, BOOKMARKS_FILE)
        bookmarks.clear()

        if (storageFile.exists()) {
            try {
                def json = new JsonSlurper().parse(storageFile) as Map
                def bookmarkList = json.bookmarks as List

                bookmarkList.each { Map bookmarkData ->
                    try {
                        bookmarks << Bookmark.fromMap(bookmarkData)
                    } catch (Exception e) {
                        LOGGER.warn("Failed to parse bookmark: ${bookmarkData}", e)
                    }
                }

                LOGGER.info("Loaded ${bookmarks.size()} bookmarks from ${storageFile.name}")
            } catch (Exception e) {
                LOGGER.error("Failed to load bookmarks from ${storageFile}", e)
            }
        } else {
            LOGGER.info("No bookmarks file found at ${storageFile}, starting fresh")
        }

        notifyListeners()
    }

    /**
     * Save bookmarks to JSON file
     */
    void saveBookmarks() {
        if (!storageFile) {
            LOGGER.warn("Cannot save bookmarks: storage file not initialized")
            return
        }

        try {
            def data = [
                    bookmarks: bookmarks.collect { it.toMap() }
            ]

            storageFile.parentFile?.mkdirs()
            storageFile.text = JsonOutput.prettyPrint(JsonOutput.toJson(data))

            LOGGER.info("Saved ${bookmarks.size()} bookmarks to ${storageFile.name}")
        } catch (Exception e) {
            LOGGER.error("Failed to save bookmarks to ${storageFile}", e)
        }
    }

    /**
     * Add a new bookmark
     */
    void addBookmark(String name, int x, int y, int z, String dimension, String source = 'manual',
                     String icon = null, String notes = '', String category = '') {
        def bookmark = new Bookmark(
                name: name,
                x: x, y: y, z: z,
                dimension: dimension,
                icon: icon ?: getDefaultIcon(source),
                notes: notes,
                createdAt: System.currentTimeMillis(),
                source: source,
                category: category
        )

        bookmarks << bookmark
        saveBookmarks()
        notifyListeners()

        LOGGER.info("Added bookmark: ${bookmark.name} at (${x}, ${y}, ${z}) in ${dimension}")
    }

    /**
     * Add bookmark object directly
     */
    void addBookmark(Bookmark bookmark) {
        if (!bookmark.icon) {
            bookmark.icon = getDefaultIcon(bookmark.source)
        }
        if (bookmark.createdAt == 0) {
            bookmark.createdAt = System.currentTimeMillis()
        }

        bookmarks << bookmark
        saveBookmarks()
        notifyListeners()

        LOGGER.info("Added bookmark: ${bookmark.name}")
    }

    /**
     * Remove a bookmark
     */
    void removeBookmark(Bookmark bookmark) {
        if (bookmarks.remove(bookmark)) {
            saveBookmarks()
            notifyListeners()
            LOGGER.info("Removed bookmark: ${bookmark.name}")
        }
    }

    /**
     * Update an existing bookmark
     */
    void updateBookmark(Bookmark bookmark, String newName, String newNotes, String newCategory, String newIcon) {
        bookmark.name = newName
        bookmark.notes = newNotes
        bookmark.category = newCategory
        bookmark.icon = newIcon

        saveBookmarks()
        notifyListeners()

        LOGGER.info("Updated bookmark: ${bookmark.name}")
    }

    /**
     * Get bookmarks filtered by dimension
     */
    List<Bookmark> getBookmarksByDimension(String dimension) {
        return bookmarks.findAll { it.dimension == dimension }
    }

    /**
     * Get bookmarks filtered by source type
     */
    List<Bookmark> getBookmarksBySource(String source) {
        return bookmarks.findAll { it.source == source }
    }

    /**
     * Get bookmarks filtered by category
     */
    List<Bookmark> getBookmarksByCategory(String category) {
        return bookmarks.findAll { it.category == category }
    }

    /**
     * Search bookmarks by name or notes
     */
    List<Bookmark> searchBookmarks(String query) {
        def lowerQuery = query.toLowerCase()
        return bookmarks.findAll {
            it.name.toLowerCase().contains(lowerQuery) ||
                    it.notes.toLowerCase().contains(lowerQuery)
        }
    }

    /**
     * Get all unique categories
     */
    Set<String> getCategories() {
        return bookmarks.collect { it.category }.findAll { it } as Set
    }

    /**
     * Get all unique dimensions
     */
    Set<String> getDimensions() {
        return bookmarks.collect { it.dimension }.findAll { it } as Set
    }

    /**
     * Import bookmarks from extraction results
     */
    void importFromSigns(List<Map> signs, String dimension = 'overworld') {
        signs.each { sign ->
            def name = sign.lines ? sign.lines[0] : "Sign"
            addBookmark(
                    name.take(50),  // Limit name length
                    sign.x as int,
                    sign.y as int,
                    sign.z as int,
                    dimension,
                    'sign',
                    '🪧',
                    sign.lines.join('\n'),
                    'sign'
            )
        }
        LOGGER.info("Imported ${signs.size()} sign bookmarks")
    }

    void importFromPortals(List<Map> portals, String dimension = 'overworld') {
        portals.each { portal ->
            def name = "Portal at ${portal.centerX},${portal.centerY},${portal.centerZ}"
            addBookmark(
                    name,
                    portal.centerX as int,
                    portal.centerY as int,
                    portal.centerZ as int,
                    dimension,
                    'portal',
                    '🟣',
                    "Blocks: ${portal.blocks?.size() ?: 0}",
                    'portal'
            )
        }
        LOGGER.info("Imported ${portals.size()} portal bookmarks")
    }

    void importFromItems(List<Map> items, String dimension = 'overworld') {
        items.each { item ->
            def name = item.name ?: item.type
            addBookmark(
                    name.take(50),
                    item.x as int,
                    item.y as int,
                    item.z as int,
                    dimension,
                    'item',
                    '⭐',
                    "Type: ${item.type}",
                    'item'
            )
        }
        LOGGER.info("Imported ${items.size()} item bookmarks")
    }

    /**
     * Register change listener
     */
    void addChangeListener(Runnable listener) {
        changeListeners << listener
    }

    private void notifyListeners() {
        changeListeners.each { it.run() }
    }

    /**
     * Get default icon for source type
     */
    private static String getDefaultIcon(String source) {
        switch (source) {
            case 'book': return '📚'
            case 'sign': return '🪧'
            case 'portal': return '🟣'
            case 'item': return '⭐'
            case 'block': return '🧱'
            case 'player': return '👤'
            case 'structure': return '🏰'
            default: return '📍'
        }
    }
}

/**
 * Bookmark Panel UI Component
 */
class BookmarkPanel extends VBox {

    private static final Logger LOGGER = LoggerFactory.getLogger(BookmarkPanel)

    BookmarkManager manager
    TableView<BookmarkManager.Bookmark> bookmarkTable
    TextField searchField
    ComboBox<String> dimensionFilter
    ComboBox<String> categoryFilter
    Consumer<BookmarkManager.Bookmark> onBookmarkSelected
    Consumer<BookmarkManager.Bookmark> onBookmarkJump

    BookmarkPanel(BookmarkManager manager) {
        this.manager = manager
        setupUI()
        manager.addChangeListener { -> refresh() }
    }

    private void setupUI() {
        padding = new Insets(10)
        spacing = 10

        // Header
        def titleLabel = new Label("📌 Bookmarks")
        titleLabel.font = Font.font('System', FontWeight.BOLD, 16)

        // Toolbar
        HBox toolbar = createToolbar()

        // Filters
        HBox filters = createFilters()

        // Table
        bookmarkTable = createTable()

        // Action buttons
        HBox actions = createActionButtons()

        children.addAll(titleLabel, toolbar, filters, bookmarkTable, actions)
        VBox.setVgrow(bookmarkTable, Priority.ALWAYS)
    }

    private HBox createToolbar() {
        def toolbar = new HBox(10)
        toolbar.alignment = Pos.CENTER_LEFT

        // Search field
        searchField = new TextField()
        searchField.promptText = "🔍 Search bookmarks..."
        searchField.prefWidth = 200
        searchField.textProperty().addListener { obs, old, newVal ->
            applyFilters()
        }

        // Add bookmark button
        def addBtn = new Button("➕ Add")
        addBtn.onAction = { handleAddBookmark() }

        // Import button
        def importBtn = new Button("📥 Import")
        def importMenu = new ContextMenu()
        importMenu.items.addAll(
                new MenuItem("Import from Signs").tap { it.onAction = { importFromSigns() } },
                new MenuItem("Import from Portals").tap { it.onAction = { importFromPortals() } },
                new MenuItem("Import from Items").tap { it.onAction = { importFromItems() } }
        )
        importBtn.onAction = { importMenu.show(importBtn, javafx.geometry.Side.BOTTOM, 0, 0) }

        // Clear all button
        def clearBtn = new Button("🗑️ Clear All")
        clearBtn.onAction = { clearAllBookmarks() }

        toolbar.children.addAll(searchField, addBtn, importBtn, clearBtn)
        HBox.setHgrow(searchField, Priority.ALWAYS)

        return toolbar
    }

    private HBox createFilters() {
        def filters = new HBox(10)
        filters.alignment = Pos.CENTER_LEFT

        // Dimension filter
        def dimLabel = new Label("Dimension:")
        dimensionFilter = new ComboBox<>()
        dimensionFilter.items.addAll('All', 'overworld', 'nether', 'end')
        dimensionFilter.value = 'All'
        dimensionFilter.onAction = { applyFilters() }

        // Category filter
        def catLabel = new Label("Category:")
        categoryFilter = new ComboBox<>()
        categoryFilter.items.add('All')
        categoryFilter.value = 'All'
        categoryFilter.onAction = { applyFilters() }

        filters.children.addAll(dimLabel, dimensionFilter, catLabel, categoryFilter)

        return filters
    }

    private TableView<BookmarkManager.Bookmark> createTable() {
        def table = new TableView<BookmarkManager.Bookmark>()

        // Icon column
        def iconCol = new TableColumn<BookmarkManager.Bookmark, String>('')
        iconCol.prefWidth = 40
        iconCol.setCellValueFactory { cellData ->
            new SimpleStringProperty(cellData.value.icon)
        }

        // Name column
        def nameCol = new TableColumn<BookmarkManager.Bookmark, String>('Name')
        nameCol.prefWidth = 150
        nameCol.setCellValueFactory { cellData ->
            new SimpleStringProperty(cellData.value.name)
        }

        // Coordinates column
        def coordsCol = new TableColumn<BookmarkManager.Bookmark, String>('Coordinates')
        coordsCol.prefWidth = 120
        coordsCol.setCellValueFactory { cellData ->
            new SimpleStringProperty(cellData.value.coordinates)
        }

        // Dimension column
        def dimCol = new TableColumn<BookmarkManager.Bookmark, String>('Dim')
        dimCol.prefWidth = 60
        dimCol.setCellValueFactory { cellData ->
            new SimpleStringProperty(cellData.value.dimensionIcon)
        }

        // Date column
        def dateCol = new TableColumn<BookmarkManager.Bookmark, String>('Created')
        dateCol.prefWidth = 120
        dateCol.setCellValueFactory { cellData ->
            new SimpleStringProperty(cellData.value.formattedDate)
        }

        table.columns.addAll(iconCol, nameCol, coordsCol, dimCol, dateCol)

        // Double-click to jump
        table.onMouseClicked = { event ->
            if (event.clickCount == 2 && table.selectionModel.selectedItem) {
                jumpToBookmark(table.selectionModel.selectedItem)
            }
        }

        // Right-click context menu
        def contextMenu = new ContextMenu()
        def jumpItem = new MenuItem("🎯 Jump to Location")
        jumpItem.onAction = {
            if (table.selectionModel.selectedItem) {
                jumpToBookmark(table.selectionModel.selectedItem)
            }
        }

        def editItem = new MenuItem("✏️ Edit")
        editItem.onAction = {
            if (table.selectionModel.selectedItem) {
                handleEditBookmark(table.selectionModel.selectedItem)
            }
        }

        def deleteItem = new MenuItem("🗑️ Delete")
        deleteItem.onAction = {
            if (table.selectionModel.selectedItem) {
                deleteBookmark(table.selectionModel.selectedItem)
            }
        }

        contextMenu.items.addAll(jumpItem, editItem, new SeparatorMenuItem(), deleteItem)
        table.contextMenu = contextMenu

        return table
    }

    private HBox createActionButtons() {
        def actions = new HBox(10)
        actions.alignment = Pos.CENTER_RIGHT

        def refreshBtn = new Button("🔄 Refresh")
        refreshBtn.onAction = { refresh() }

        def exportBtn = new Button("💾 Export")
        exportBtn.onAction = { exportBookmarks() }

        actions.children.addAll(refreshBtn, exportBtn)

        return actions
    }

    void refresh() {
        Platform.runLater {
            // Update category filter
            def categories = ['All'] + manager.categories.sort()
            categoryFilter.items.setAll(categories)

            // Apply current filters
            applyFilters()
        }
    }

    private void applyFilters() {
        def filtered = manager.bookmarks

        // Search filter
        def query = searchField.text?.trim()
        if (query) {
            filtered = manager.searchBookmarks(query)
        }

        // Dimension filter
        def dim = dimensionFilter.value
        if (dim && dim != 'All') {
            filtered = filtered.findAll { it.dimension == dim }
        }

        // Category filter
        def cat = categoryFilter.value
        if (cat && cat != 'All') {
            filtered = filtered.findAll { it.category == cat }
        }

        bookmarkTable.items.setAll(filtered)
    }

    private void handleAddBookmark() {
        def result = BookmarkPanel.showAddBookmarkDialog(0, 64, 0, 'overworld')
        if (result) {
            manager.addBookmark(result)
        }
    }

    private void handleEditBookmark(BookmarkManager.Bookmark bookmark) {
        def result = BookmarkPanel.showEditBookmarkDialog(bookmark)
        if (result) {
            manager.updateBookmark(
                    bookmark,
                    result.name,
                    result.notes,
                    result.category,
                    result.icon
            )
            refresh()
        }
    }

    private void deleteBookmark(BookmarkManager.Bookmark bookmark) {
        def alert = new Alert(Alert.AlertType.CONFIRMATION)
        alert.title = "Delete Bookmark"
        alert.headerText = "Delete bookmark '${bookmark.name}'?"
        alert.contentText = "This action cannot be undone."

        if (alert.showAndWait().get() == ButtonType.OK) {
            manager.removeBookmark(bookmark)
        }
    }

    private void clearAllBookmarks() {
        def alert = new Alert(Alert.AlertType.CONFIRMATION)
        alert.title = "Clear All Bookmarks"
        alert.headerText = "Delete all ${manager.bookmarks.size()} bookmarks?"
        alert.contentText = "This action cannot be undone."

        if (alert.showAndWait().get() == ButtonType.OK) {
            manager.bookmarks.clear()
            manager.saveBookmarks()
            refresh()
        }
    }

    private void jumpToBookmark(BookmarkManager.Bookmark bookmark) {
        if (onBookmarkJump) {
            onBookmarkJump.accept(bookmark)
        } else if (onBookmarkSelected) {
            onBookmarkSelected.accept(bookmark)
        }
        LOGGER.info("Jump to bookmark: ${bookmark.name} at ${bookmark.coordinates}")
    }

    private void importFromSigns() {
        // TODO: Integrate with extraction results
        showAlert("Import Signs", "This feature requires integration with sign extraction results.")
    }

    private void importFromPortals() {
        // TODO: Integrate with extraction results
        showAlert("Import Portals", "This feature requires integration with portal detection results.")
    }

    private void importFromItems() {
        // TODO: Integrate with extraction results
        showAlert("Import Items", "This feature requires integration with item extraction results.")
    }

    private void exportBookmarks() {
        // TODO: Export to CSV/JSON
        showAlert("Export Bookmarks", "Bookmarks exported to: ${manager.storageFile}")
    }

    private void showAlert(String title, String message) {
        def alert = new Alert(Alert.AlertType.INFORMATION)
        alert.title = title
        alert.headerText = null
        alert.contentText = message
        alert.showAndWait()
    }

    /**
     * Show dialog to add a new bookmark
     */
    static BookmarkManager.Bookmark showAddBookmarkDialog(int x, int y, int z, String dimension) {
        Dialog<BookmarkManager.Bookmark> dialog = new Dialog<>()
        dialog.title = "Add Bookmark"
        dialog.headerText = "Create a new coordinate bookmark"

        // Icon
        def iconLabel = new Label("Icon:")
        def iconField = new TextField("📍")
        iconField.prefWidth = 60

        // Name
        def nameLabel = new Label("Name:")
        def nameField = new TextField()
        nameField.promptText = "Bookmark name"

        // Coordinates
        def coordsLabel = new Label("Coordinates:")
        def xField = new TextField(x.toString())
        xField.prefWidth = 80
        def yField = new TextField(y.toString())
        yField.prefWidth = 80
        def zField = new TextField(z.toString())
        zField.prefWidth = 80

        def coordsBox = new HBox(5, new Label("X:"), xField, new Label("Y:"), yField, new Label("Z:"), zField)

        // Dimension
        def dimLabel = new Label("Dimension:")
        def dimChoice = new ComboBox<String>()
        dimChoice.items.addAll('overworld', 'nether', 'end')
        dimChoice.value = dimension

        // Category
        def catLabel = new Label("Category:")
        def catField = new TextField()
        catField.promptText = "Optional category/tag"

        // Notes
        def notesLabel = new Label("Notes:")
        def notesField = new TextArea()
        notesField.promptText = "Optional notes"
        notesField.prefRowCount = 3

        // Layout
        def grid = new GridPane()
        grid.hgap = 10
        grid.vgap = 10
        grid.padding = new Insets(20)

        grid.add(iconLabel, 0, 0)
        grid.add(iconField, 1, 0)
        grid.add(nameLabel, 0, 1)
        grid.add(nameField, 1, 1)
        grid.add(coordsLabel, 0, 2)
        grid.add(coordsBox, 1, 2)
        grid.add(dimLabel, 0, 3)
        grid.add(dimChoice, 1, 3)
        grid.add(catLabel, 0, 4)
        grid.add(catField, 1, 4)
        grid.add(notesLabel, 0, 5)
        grid.add(notesField, 1, 5)

        dialog.dialogPane.content = grid
        dialog.dialogPane.buttonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)

        // Enable OK only if name is provided
        def okButton = dialog.dialogPane.lookupButton(ButtonType.OK)
        okButton.disableProperty().bind(nameField.textProperty().isEmpty())

        dialog.resultConverter = { button ->
            if (button == ButtonType.OK) {
                try {
                    return new BookmarkManager.Bookmark(
                            name: nameField.text,
                            x: xField.text.toInteger(),
                            y: yField.text.toInteger(),
                            z: zField.text.toInteger(),
                            dimension: dimChoice.value,
                            icon: iconField.text ?: '📍',
                            notes: notesField.text,
                            category: catField.text,
                            createdAt: System.currentTimeMillis(),
                            source: 'manual'
                    )
                } catch (NumberFormatException e) {
                    showErrorAlert("Invalid coordinates. Please enter valid numbers.")
                    return null
                }
            }
            return null
        }

        return dialog.showAndWait().orElse(null)
    }

    /**
     * Show dialog to edit an existing bookmark
     */
    static BookmarkManager.Bookmark showEditBookmarkDialog(BookmarkManager.Bookmark bookmark) {
        Dialog<BookmarkManager.Bookmark> dialog = new Dialog<>()
        dialog.title = "Edit Bookmark"
        dialog.headerText = "Edit bookmark details"

        // Icon
        def iconLabel = new Label("Icon:")
        def iconField = new TextField(bookmark.icon)
        iconField.prefWidth = 60

        // Name
        def nameLabel = new Label("Name:")
        def nameField = new TextField(bookmark.name)

        // Category
        def catLabel = new Label("Category:")
        def catField = new TextField(bookmark.category ?: '')

        // Notes
        def notesLabel = new Label("Notes:")
        def notesField = new TextArea(bookmark.notes)
        notesField.prefRowCount = 5

        // Coordinates (read-only)
        def coordsLabel = new Label("Coordinates:")
        def coordsInfo = new Label("${bookmark.coordinates} (${bookmark.dimension})")

        // Layout
        def grid = new GridPane()
        grid.hgap = 10
        grid.vgap = 10
        grid.padding = new Insets(20)

        grid.add(iconLabel, 0, 0)
        grid.add(iconField, 1, 0)
        grid.add(nameLabel, 0, 1)
        grid.add(nameField, 1, 1)
        grid.add(catLabel, 0, 2)
        grid.add(catField, 1, 2)
        grid.add(coordsLabel, 0, 3)
        grid.add(coordsInfo, 1, 3)
        grid.add(notesLabel, 0, 4)
        grid.add(notesField, 1, 4)

        dialog.dialogPane.content = grid
        dialog.dialogPane.buttonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)

        dialog.resultConverter = { button ->
            if (button == ButtonType.OK) {
                // Return modified bookmark (we'll update the original in the caller)
                return new BookmarkManager.Bookmark(
                        name: nameField.text,
                        icon: iconField.text,
                        category: catField.text,
                        notes: notesField.text,
                        x: bookmark.x, y: bookmark.y, z: bookmark.z,
                        dimension: bookmark.dimension,
                        createdAt: bookmark.createdAt,
                        source: bookmark.source
                )
            }
            return null
        }

        return dialog.showAndWait().orElse(null)
    }

    private static void showErrorAlert(String message) {
        def alert = new Alert(Alert.AlertType.ERROR)
        alert.title = "Error"
        alert.headerText = null
        alert.contentText = message
        alert.showAndWait()
    }
}

import atlantafx.base.theme.PrimerDark
import atlantafx.base.theme.PrimerLight
import com.jthemedetecor.OsThemeDetector
import javafx.application.Application
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.stage.DirectoryChooser
import javafx.stage.Stage
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import viewers.BlockGridViewer
import viewers.BookViewer
import viewers.PortalViewer
import viewers.StatsDashboard
import viewers.ThemeManager

/**
 * Output Viewer - Browse and analyze extracted Minecraft data.
 *
 * Features:
 * - Tabbed interface for Books, Signs, Items, Blocks, Portals, Map, Statistics
 * - Sidebar showing output folder structure
 * - Status bar with data summary
 * - Themed with AtlantaFX (PrimerDark/PrimerLight)
 */
class OutputViewer extends Stage {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutputViewer)

    OutputViewerModel model
    TabPane contentTabs
    TreeView<FileTreeItem> folderTree
    Label statusBar
    Button loadButton
    TextField folderField

    /**
     * Create the Output Viewer window.
     */
    OutputViewer() {
        this.title = 'ReadSignsAndBooks - Output Viewer'
        this.model = new OutputViewerModel()

        // Initialize ThemeManager
        ThemeManager.initialize()

        // Set window icon
        try {
            InputStream iconStream = getClass().getResourceAsStream('/icons/icon-512.png')
            if (iconStream) {
                icons.add(new javafx.scene.image.Image(iconStream))
            }
        } catch (Exception e) {
            LOGGER.trace('Icon not found, using default', e)
        }

        // Build UI
        BorderPane root = new BorderPane()

        // Top: Toolbar with folder selection
        root.top = createToolbar()

        // Center: Split pane with sidebar and tabs
        SplitPane splitPane = new SplitPane()
        splitPane.dividerPositions = [0.25] as double[]

        // Left: Folder tree
        folderTree = createFolderTree()
        VBox leftPane = new VBox(new Label('Output Files:').with { it.style = '-fx-font-weight: bold;'; it }, folderTree)
        leftPane.padding = new Insets(10)
        VBox.setVgrow(folderTree, Priority.ALWAYS)

        // Right: Content tabs
        contentTabs = createContentTabs()

        splitPane.items.addAll(leftPane, contentTabs)
        root.center = splitPane

        // Bottom: Status bar
        statusBar = new Label('Ready - No data loaded')
        statusBar.style = '-fx-padding: 5; -fx-background-color: derive(-fx-base, -5%);'
        statusBar.maxWidth = Double.MAX_VALUE
        root.bottom = statusBar

        // Set up scene and register with ThemeManager
        Scene scene = new Scene(root, 1200, 800)
        this.scene = scene
        this.minWidth = 1000
        this.minHeight = 600

        // Register scene with ThemeManager for theme updates
        ThemeManager.registerScene(scene)

        // Cleanup on close
        onCloseRequest = { event ->
            ThemeManager.unregisterScene(scene)
            model?.close()
        }
    }

    /**
     * Create the toolbar with folder selection.
     */
    private HBox createToolbar() {
        HBox toolbar = new HBox(10)
        toolbar.padding = new Insets(10)
        toolbar.alignment = Pos.CENTER_LEFT
        toolbar.style = '-fx-background-color: derive(-fx-base, 5%);'

        Label label = new Label('Output Folder:')
        label.minWidth = 100

        folderField = new TextField()
        folderField.promptText = 'Select an output folder to browse...'
        folderField.editable = false
        HBox.setHgrow(folderField, Priority.ALWAYS)

        Button browseButton = new Button('Browse...')
        browseButton.onAction = { event -> selectOutputFolder() }

        loadButton = new Button('Load')
        loadButton.style = '-fx-font-weight: bold; -fx-background-color: #4CAF50; -fx-text-fill: white;'
        loadButton.disable = true
        loadButton.onAction = { event -> loadFolder() }

        Button refreshButton = new Button('Refresh')
        refreshButton.onAction = { event -> loadFolder() }
        refreshButton.disable = true

        // Theme toggle button
        Button themeToggleButton = new Button()
        updateThemeButtonText(themeToggleButton)
        themeToggleButton.tooltip = new Tooltip('Toggle dark/light theme')
        themeToggleButton.onAction = { event ->
            ThemeManager.toggleTheme()
            updateThemeButtonText(themeToggleButton)
        }

        // Enable/disable buttons based on folder selection
        folderField.textProperty().addListener { obs, oldVal, newVal ->
            boolean hasFolder = newVal && !newVal.trim().isEmpty()
            loadButton.disable = !hasFolder
            refreshButton.disable = !hasFolder
        }

        toolbar.children.addAll(label, folderField, browseButton, loadButton, refreshButton, new Separator(), themeToggleButton)
        return toolbar
    }

    /**
     * Create the folder tree view.
     */
    private TreeView<FileTreeItem> createFolderTree() {
        TreeItem<FileTreeItem> root = new TreeItem<>(new FileTreeItem('(No folder loaded)', null))
        root.expanded = true

        TreeView<FileTreeItem> tree = new TreeView<>(root)
        tree.showRoot = true
        VBox.setVgrow(tree, Priority.ALWAYS)

        return tree
    }

    /**
     * Create the main content tabs.
     */
    private TabPane createContentTabs() {
        TabPane tabs = new TabPane()
        tabs.tabClosingPolicy = TabPane.TabClosingPolicy.UNAVAILABLE

        // Create tabs - some start as placeholders and get populated on data load
        tabs.tabs.addAll(
            createPlaceholderTab('Books', 'Load an output folder to view books...'),
            createPlaceholderTab('Signs', 'Load an output folder to view signs...'),
            createPlaceholderTab('Items', 'Load an output folder to view item database...'),
            createPlaceholderTab('Blocks', 'Load an output folder to view block database...'),
            createPlaceholderTab('Portals', 'Load an output folder to view portals...'),
            createPlaceholderTab('Map', '🗺️ Interactive map view - Coming Soon!\n\nThis feature will display extracted items and blocks on a Minecraft world map.'),
            createPlaceholderTab('Statistics', 'Load an output folder to view statistics dashboard...')
        )

        return tabs
    }

    /**
     * Create a placeholder tab with a label.
     */
    private Tab createPlaceholderTab(String title, String message) {
        Tab tab = new Tab(title)
        Label placeholder = new Label(message)
        placeholder.style = '-fx-text-fill: gray; -fx-font-style: italic;'
        BorderPane content = new BorderPane(placeholder)
        BorderPane.setAlignment(placeholder, Pos.CENTER)
        tab.content = content
        return tab
    }

    /**
     * Open folder selection dialog.
     */
    private void selectOutputFolder() {
        DirectoryChooser chooser = new DirectoryChooser(title: 'Select Output Folder')

        // Set initial directory from field if valid
        if (folderField.text) {
            File current = new File(folderField.text)
            if (current.exists() && current.isDirectory()) {
                chooser.initialDirectory = current
            }
        }

        File selected = chooser.showDialog(this)
        if (selected) {
            folderField.text = selected.absolutePath
        }
    }

    /**
     * Load data from the selected folder.
     */
    void loadFolder() {
        String path = folderField.text?.trim()
        if (!path) {
            showError('No folder selected', 'Please select an output folder to load.')
            return
        }

        File folder = new File(path)
        if (!folder.exists() || !folder.isDirectory()) {
            showError('Invalid folder', "Folder does not exist: ${path}")
            return
        }

        // Disable UI during load
        loadButton.disable = true
        statusBar.text = 'Loading...'

        // Load in background thread
        Thread.start {
            try {
                boolean success = model.loadFromFolder(folder)

                javafx.application.Platform.runLater {
                    if (success) {
                        updateUI()
                        statusBar.text = model.getSummaryText()
                        showInfo('Load Complete', "Successfully loaded data from:\n${folder.absolutePath}")
                    } else {
                        statusBar.text = 'Failed to load data'
                        showError('Load Failed', 'Could not load data from the selected folder.')
                    }
                    loadButton.disable = false
                }
            } catch (Exception e) {
                LOGGER.error("Failed to load folder: ${e.message}", e)
                javafx.application.Platform.runLater {
                    statusBar.text = 'Error occurred'
                    showError('Error', "Failed to load data:\n${e.message}")
                    loadButton.disable = false
                }
            }
        }
    }

    /**
     * Update UI after loading data.
     */
    private void updateUI() {
        // Update folder tree
        updateFolderTree()

        // Update tabs (will be implemented by sub-viewers)
        updateBooksTab()
        updateSignsTab()
        updateItemsTab()
        updateBlocksTab()
        updatePortalsTab()
        updateStatisticsTab()
    }

    /**
     * Update the folder tree view.
     */
    private void updateFolderTree() {
        if (!model.outputFolder) {
            return
        }

        TreeItem<FileTreeItem> root = new TreeItem<>(new FileTreeItem(model.outputFolder.name, model.outputFolder))
        root.expanded = true

        // Add files from the output folder
        model.outputFolder.listFiles()?.sort { it.name }?.each { File file ->
            TreeItem<FileTreeItem> item = new TreeItem<>(new FileTreeItem(file.name, file))
            root.children.add(item)

            // If it's a directory, add its children
            if (file.isDirectory()) {
                file.listFiles()?.sort { it.name }?.each { File subFile ->
                    item.children.add(new TreeItem<>(new FileTreeItem(subFile.name, subFile)))
                }
            }
        }

        folderTree.root = root
    }

    /**
     * Update the Books tab with embedded BookViewer.
     */
    private void updateBooksTab() {
        Tab booksTab = contentTabs.tabs[0]

        if (model.books.isEmpty()) {
            Label placeholder = new Label('No books found in output folder')
            placeholder.style = '-fx-text-fill: gray; -fx-font-style: italic;'
            booksTab.content = new BorderPane(placeholder)
            return
        }

        try {
            // Create BookViewer and initialize UI
            BookViewer bookViewer = new BookViewer()
            BorderPane bookViewerUI = bookViewer.initializeUI()

            // Load books from model (convert to format BookViewer expects)
            bookViewer.allBooks = model.books
            bookViewer.filterBooks()

            booksTab.content = bookViewerUI
            LOGGER.info("BookViewer integrated with ${model.books.size()} books")
        } catch (Exception e) {
            LOGGER.error("Failed to create BookViewer: ${e.message}", e)
            // Fallback to simple text display
            TextArea textArea = new TextArea()
            textArea.editable = false
            textArea.wrapText = true
            textArea.text = "Loaded ${model.books.size()} books\n\n" +
                model.books.take(10).collect { book ->
                    "Title: ${book.title ?: 'Untitled'}\n" +
                    "Author: ${book.author ?: 'Unknown'}\n" +
                    "Pages: ${book.pages?.size() ?: 0}\n"
                }.join('\n---\n')
            booksTab.content = textArea
        }
    }

    /**
     * Update the Signs tab.
     */
    private void updateSignsTab() {
        if (model.signs.isEmpty()) {
            return
        }

        Tab signsTab = contentTabs.tabs[1]
        TextArea textArea = new TextArea()
        textArea.editable = false
        textArea.wrapText = true
        textArea.text = "Loaded ${model.signs.size()} signs\n\n" +
            model.signs.take(10).collect { sign ->
                "Lines: ${sign.line1 ?: ''} | ${sign.line2 ?: ''} | ${sign.line3 ?: ''} | ${sign.line4 ?: ''}\n" +
                "Location: (${sign.x}, ${sign.y}, ${sign.z})\n" +
                "Dimension: ${sign.dimension ?: 'Unknown'}\n"
            }.join('\n---\n')

        if (model.signs.size() > 10) {
            textArea.text += "\n\n... and ${model.signs.size() - 10} more"
        }

        signsTab.content = textArea
    }

    /**
     * Update the Items tab.
     */
    private void updateItemsTab() {
        Tab itemsTab = contentTabs.tabs[2]

        if (!model.itemDatabase) {
            itemsTab.content = new Label('No item database found in output folder').with {
                it.style = '-fx-text-fill: gray; -fx-font-style: italic;'
                new BorderPane(it).with { BorderPane.setAlignment(it, Pos.CENTER); it }
            }
            return
        }

        TextArea textArea = new TextArea()
        textArea.editable = false
        textArea.wrapText = true

        List<Map> summary = model.itemDatabase.getSummary()
        textArea.text = "Item Database Summary\n\n" +
            "Total item types: ${summary.size()}\n" +
            "Total items indexed: ${model.metadata.totalItemsIndexed}\n" +
            "Total items found: ${model.metadata.totalItemCount}\n\n" +
            "Top 20 item types:\n" +
            summary.take(20).collect { item ->
                "  ${item.item_id}: ${item.total_count} total, ${item.indexed_count} indexed" +
                    (item.limit_reached ? ' (LIMIT REACHED)' : '')
            }.join('\n')

        itemsTab.content = textArea
    }

    /**
     * Update the Blocks tab with embedded BlockGridViewer.
     */
    private void updateBlocksTab() {
        Tab blocksTab = contentTabs.tabs[3]

        if (!model.blockDatabase) {
            blocksTab.content = new Label('No block database found in output folder').with {
                it.style = '-fx-text-fill: gray; -fx-font-style: italic;'
                new BorderPane(it).with { BorderPane.setAlignment(it, Pos.CENTER); it }
            }
            return
        }

        try {
            // Find the block database file
            File dbFile = new File(model.outputFolder, 'block_index.db')
            if (!dbFile.exists()) {
                dbFile = new File(model.outputFolder, 'blocks.db')
            }

            if (dbFile.exists()) {
                BlockGridViewer blockViewer = new BlockGridViewer()
                boolean loaded = blockViewer.loadBlocks(dbFile)
                if (loaded) {
                    blocksTab.content = blockViewer
                    LOGGER.info('BlockGridViewer integrated')
                    return
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to create BlockGridViewer: ${e.message}", e)
        }

        // Fallback to simple text display
        TextArea textArea = new TextArea()
        textArea.editable = false
        textArea.wrapText = true

        List<Map> summary = model.blockDatabase.getSummary()
        textArea.text = "Block Database Summary\n\n" +
            "Total block types: ${summary.size()}\n" +
            "Total blocks indexed: ${model.metadata.totalBlocksIndexed}\n\n" +
            "All block types:\n" +
            summary.collect { block ->
                "  ${block.block_type}: ${block.total_found} total, ${block.indexed_count} indexed" +
                    (block.limit_reached ? ' (LIMIT REACHED)' : '')
            }.join('\n')

        blocksTab.content = textArea
    }

    /**
     * Update the Portals tab.
     */
    private void updatePortalsTab() {
        if (model.portals.isEmpty()) {
            return
        }

        Tab portalsTab = contentTabs.tabs[4]
        TextArea textArea = new TextArea()
        textArea.editable = false
        textArea.wrapText = true
        textArea.text = "Loaded ${model.portals.size()} portals\n\n" +
            model.portals.collect { portal ->
                "Portal ID: ${portal.portal_id}\n" +
                "Dimension: ${portal.dimension}\n" +
                "Blocks: ${portal.block_count}\n" +
                "Center: (${portal.center_x}, ${portal.center_y}, ${portal.center_z})\n" +
                "Bounds: X[${portal.min_x}..${portal.max_x}] Y[${portal.min_y}..${portal.max_y}] Z[${portal.min_z}..${portal.max_z}]\n"
            }.join('\n---\n')

        portalsTab.content = textArea
    }

    /**
     * Update the Statistics tab with embedded StatsDashboard.
     */
    private void updateStatisticsTab() {
        Tab statsTab = contentTabs.tabs[6]

        try {
            // Create StatsDashboard and populate with data
            StatsDashboard dashboard = new StatsDashboard()

            // Convert books to the format StatsDashboard expects (grouped by author)
            Map<String, List<Map>> booksByAuthor = model.books.groupBy { it.author ?: 'Unknown' }

            // Create a simple hash map for signs
            Map<String, Map> signsByHash = [:]
            model.signs.eachWithIndex { sign, index ->
                signsByHash["sign_${index}"] = sign
            }

            // Update dashboard with model data
            dashboard.updateData(
                booksByAuthor,
                signsByHash,
                model.itemDatabase,
                model.blockDatabase,
                model.portals
            )

            statsTab.content = dashboard
            LOGGER.info('StatsDashboard integrated')
        } catch (Exception e) {
            LOGGER.error("Failed to create StatsDashboard: ${e.message}", e)
            // Fallback to simple text display
            TextArea textArea = new TextArea()
            textArea.editable = false
            textArea.wrapText = true

            StringBuilder stats = new StringBuilder('Output Data Statistics\n\n')
            stats.append("Books: ${model.metadata.booksCount}\n")
            stats.append("Signs: ${model.metadata.signsCount}\n")
            stats.append("Custom Names: ${model.metadata.customNamesCount}\n")
            stats.append("Portals: ${model.metadata.portalsCount}\n")
            stats.append("Block Results: ${model.metadata.blockResultsCount}\n")

            if (model.itemDatabase) {
                stats.append("\nItem Database:\n")
                stats.append("  Item Types: ${model.metadata.itemTypesCount}\n")
                stats.append("  Items Indexed: ${model.metadata.totalItemsIndexed}\n")
                stats.append("  Total Items Found: ${model.metadata.totalItemCount}\n")
            }

            if (model.blockDatabase) {
                stats.append("\nBlock Database:\n")
                stats.append("  Block Types: ${model.metadata.blockTypesCount}\n")
                stats.append("  Blocks Indexed: ${model.metadata.totalBlocksIndexed}\n")
            }

            stats.append("\nOutput Folder: ${model.outputFolder?.absolutePath ?: 'Not loaded'}\n")
            textArea.text = stats.toString()
            statsTab.content = textArea
        }
    }

    /**
     * Update theme toggle button text to show current theme.
     */
    private void updateThemeButtonText(Button button) {
        String icon = ThemeManager.isDark() ? '☀' : '☾'
        button.text = "${icon} ${ThemeManager.getThemeDisplayName()}"
    }

    /**
     * Show an error dialog.
     */
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR)
        alert.title = title
        alert.headerText = null
        alert.contentText = message
        alert.showAndWait()
    }

    /**
     * Show an info dialog.
     */
    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION)
        alert.title = title
        alert.headerText = null
        alert.contentText = message
        alert.showAndWait()
    }

    /**
     * Data class for file tree items.
     */
    static class FileTreeItem {

        String name
        File file

        FileTreeItem(String name, File file) {
            this.name = name
            this.file = file
        }

        @Override
        String toString() {
            return name
        }
    }
}

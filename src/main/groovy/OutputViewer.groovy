import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.layout.*
import javafx.stage.DirectoryChooser
import javafx.stage.Stage
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import viewers.BlockGridViewer
import viewers.BookViewer
import viewers.GlobalSearch
import viewers.ItemGridViewer
import viewers.MapViewer
import viewers.PortalViewer
import viewers.SignViewer
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
    GlobalSearch globalSearch

    // Embedded specialized viewers (created lazily so we can support navigation + avoid re-allocations)
    private BookViewer bookViewer
    private BorderPane bookViewerUI
    private SignViewer signViewer
    private BorderPane signViewerUI
    private PortalViewer portalViewer
    private MapViewer mapViewer
    private StatsDashboard statsDashboard

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
        globalSearch = new GlobalSearch(model, { GlobalSearch.SearchResult result ->
            navigateToResult(result)
        })

        Label filesLabel = new Label('Output Files:')
        filesLabel.style = '-fx-font-weight: bold;'

        VBox leftPane = new VBox(10)
        leftPane.children.addAll(globalSearch, new Separator(), filesLabel, folderTree)
        leftPane.padding = new Insets(10)
        VBox.setVgrow(globalSearch, Priority.ALWAYS)
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

        // Auto-refresh checkbox
        CheckBox autoRefreshCheckBox = new CheckBox('Auto-refresh')
        autoRefreshCheckBox.tooltip = new Tooltip('Automatically reload data every 30 seconds')
        autoRefreshCheckBox.disable = true

        // Auto-refresh timer
        javafx.animation.Timeline autoRefreshTimer = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(30), { event ->
                if (autoRefreshCheckBox.selected && folderField.text) {
                    javafx.application.Platform.runLater { loadFolder() }
                }
            })
        )
        autoRefreshTimer.cycleCount = javafx.animation.Timeline.INDEFINITE

        autoRefreshCheckBox.selectedProperty().addListener { obs, oldVal, newVal ->
            if (newVal) {
                autoRefreshTimer.play()
            } else {
                autoRefreshTimer.stop()
            }

            // Keep status bar suffix stable (no repeated appends)
            if (statusBar) {
                String base = (statusBar.text ?: '').replace(' (auto-refresh enabled)', '')
                statusBar.text = newVal ? (base + ' (auto-refresh enabled)') : base
            }
        }

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
            autoRefreshCheckBox.disable = !hasFolder
        }

        toolbar.children.addAll(label, folderField, browseButton, loadButton, refreshButton, autoRefreshCheckBox, new Separator(), themeToggleButton)
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
            createPlaceholderTab('Map', '🗺️ Map Viewer\n\nLoad an output folder, then use “Load Map Image…” to open a PNG/JPG exported from tools like uNmINeD or Dynmap.\n\nMarkers for signs and portals will appear after a map is loaded.'),
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
                        LOGGER.info("Load complete: ${folder.absolutePath}")
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

        // Update global search model (and clear old query/results)
        globalSearch?.updateModel(model)

        // Update tabs (will be implemented by sub-viewers)
        updateBooksTab()
        updateSignsTab()
        updateItemsTab()
        updateBlocksTab()
        updatePortalsTab()
        updateMapTab()
        updateStatisticsTab()
    }

    /**
     * Navigate to a GlobalSearch selection.
     */
    private void navigateToResult(GlobalSearch.SearchResult result) {
        if (!result || result.type == 'header') return

        switch (result.type) {
            case 'book':
                navigateToBook(result.data as Map)
                break
            case 'sign':
                navigateToSign(result.data as Map)
                break
            case 'item':
                navigateToItem(result.data as Map)
                break
            case 'portal':
                navigateToPortal(result.data as Map)
                break
            case 'custom_name':
                navigateToCustomName(result.data as Map)
                break
            default:
                LOGGER.debug("Unhandled search result type: ${result.type}")
        }
    }

    private void navigateToBook(Map book) {
        contentTabs.selectionModel.select(0)
        try {
            // Ensure viewer is initialized before navigation
            if (!bookViewer && !model.books.isEmpty()) {
                updateBooksTab()
            }
            bookViewer?.displayBook(book)
        } catch (Exception e) {
            LOGGER.debug("Failed to navigate to book: ${e.message}")
        }
    }

    private void navigateToSign(Map sign) {
        contentTabs.selectionModel.select(1)
        try {
            // Ensure viewer is initialized before navigation
            if (!signViewer && !model.signs.isEmpty()) {
                updateSignsTab()
            }
            signViewer?.highlightSign(sign as Map<String, Object>)
        } catch (Exception e) {
            LOGGER.debug("Failed to navigate to sign: ${e.message}")
        }
    }

    private void navigateToItem(Map item) {
        contentTabs.selectionModel.select(2)

        try {
            String itemId = item.item_id ?: item.itemId ?: 'Unknown'
            def x = item.x
            def y = item.y
            def z = item.z
            String dimension = item.dimension ?: item.dim ?: item.Dimension ?: ''
            String customName = item.custom_name ?: item.customName ?: ''

            String tp = (x != null && y != null && z != null) ? "/tp @s ${x} ${y} ${z}" : null

            // Find the DB file on disk (needed to launch ItemGridViewer)
            File dbFile = new File(model.outputFolder, 'item_index.db')
            if (!dbFile.exists()) {
                dbFile = new File(model.outputFolder, 'items.db')
            }

            Alert dialog = new Alert(Alert.AlertType.INFORMATION)
            dialog.title = 'Item'
            dialog.headerText = itemId

            StringBuilder sb = new StringBuilder()
            if (customName) sb.append("Name: ${customName}\n")
            if (item.count != null) sb.append("Count: ${item.count}\n")
            if (dimension) sb.append("Dimension: ${dimension}\n")
            if (x != null && y != null && z != null) sb.append("Location: (${x}, ${y}, ${z})\n")
            if (tp) sb.append("\nTeleport:\n${tp}\n")

            TextArea details = new TextArea(sb.toString())
            details.editable = false
            details.wrapText = true
            details.prefRowCount = 8
            dialog.dialogPane.content = details

            ButtonType openBtn = new ButtonType('Open Item Grid Viewer…', ButtonBar.ButtonData.OK_DONE)
            ButtonType copyTpBtn = new ButtonType('Copy Teleport', ButtonBar.ButtonData.OTHER)
            dialog.buttonTypes.setAll(openBtn, copyTpBtn, ButtonType.CLOSE)

            Node openNode = dialog.dialogPane.lookupButton(openBtn)
            if (openNode) openNode.disable = !dbFile.exists()

            Node copyNode = dialog.dialogPane.lookupButton(copyTpBtn)
            if (copyNode) copyNode.disable = (tp == null)

            dialog.showAndWait().ifPresent { result ->
                if (result == openBtn) {
                    openItemGridViewer(dbFile, itemId)
                } else if (result == copyTpBtn) {
                    copyToClipboard(tp)
                    if (statusBar) statusBar.text = "Copied: ${tp}"
                }
            }
        } catch (Exception ignored) {
            // no-op
        }
    }

    private void navigateToPortal(Map portal) {
        contentTabs.selectionModel.select(4)
        try {
            String dim = (portal.dimension ?: 'unknown') as String
            def cx = portal.center_x ?: portal.center?.x ?: portal.centerX
            def cy = portal.center_y ?: portal.center?.y ?: portal.centerY
            def cz = portal.center_z ?: portal.center?.z ?: portal.centerZ

            String tp = (cx != null && cy != null && cz != null) ? "/tp @s ${cx} ${cy} ${cz}" : null

            Alert dialog = new Alert(Alert.AlertType.INFORMATION)
            dialog.title = 'Portal'
            dialog.headerText = "Portal (${dim})"

            StringBuilder sb = new StringBuilder()
            sb.append("Dimension: ${dim}\n")
            if (portal.block_count != null) sb.append("Blocks: ${portal.block_count}\n")
            if (portal.width != null || portal.height != null) sb.append("Size: ${portal.width ?: '?'}×${portal.height ?: '?'}\n")
            if (portal.axis) sb.append("Axis: ${portal.axis}\n")
            if (cx != null && cy != null && cz != null) sb.append("Center: (${cx}, ${cy}, ${cz})\n")
            if (tp) sb.append("\nTeleport:\n${tp}\n")

            TextArea details = new TextArea(sb.toString())
            details.editable = false
            details.wrapText = true
            details.prefRowCount = 8
            dialog.dialogPane.content = details

            ButtonType copyTpBtn = new ButtonType('Copy Teleport', ButtonBar.ButtonData.OK_DONE)
            dialog.buttonTypes.setAll(copyTpBtn, ButtonType.CLOSE)

            Node copyNode = dialog.dialogPane.lookupButton(copyTpBtn)
            if (copyNode) copyNode.disable = (tp == null)

            dialog.showAndWait().ifPresent { result ->
                if (result == copyTpBtn && tp) {
                    copyToClipboard(tp)
                    if (statusBar) statusBar.text = "Copied: ${tp}"
                }
            }
        } catch (Exception ignored) {
            // no-op
        }
    }

    private void navigateToCustomName(Map customName) {
        // No dedicated tab yet; show details + TP helper
        try {
            String name = customName.name ?: customName.customName ?: 'Custom Name'
            String type = customName.type ?: 'unknown'
            String itemId = customName.item_id ?: customName.itemOrEntityId ?: ''
            def x = customName.x
            def y = customName.y
            def z = customName.z

            StringBuilder msg = new StringBuilder()
            msg.append("Name: ${name}\n")
            msg.append("Type: ${type}\n")
            if (itemId) msg.append("ID: ${itemId}\n")
            if (x != null && y != null && z != null) {
                msg.append("\nTeleport:\n/tp @s ${x} ${y} ${z}")
            }

            showInfo('Custom Name', msg.toString())
        } catch (Exception e) {
            LOGGER.debug("Failed to show custom name dialog: ${e.message}", e)
        }
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
            // Create BookViewer once and reuse it for navigation/highlighting
            if (!bookViewer) {
                bookViewer = new BookViewer()
                bookViewerUI = bookViewer.initializeUI()
            }

            // Reset filters on reload to ensure fresh state
            try {
                if (bookViewer.searchField) bookViewer.searchField.clear()
                if (bookViewer.authorFilterCombo) bookViewer.authorFilterCombo.value = 'All Authors'
            } catch (Exception ignored) {
                // Ignore if filter components not accessible
            }

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
     * Update the Signs tab with search/filter capability.
     */
    private void updateSignsTab() {
        Tab signsTab = contentTabs.tabs[1]

        if (model.signs.isEmpty()) {
            Label placeholder = new Label('No signs found in output folder')
            placeholder.style = '-fx-text-fill: gray; -fx-font-style: italic;'
            signsTab.content = new BorderPane(placeholder)
            return
        }

        try {
            if (!signViewer) {
                signViewer = new SignViewer()
                signViewerUI = signViewer.initializeUI()
            }

            // Reset filters on reload to ensure fresh state
            try {
                if (signViewer.searchField) signViewer.searchField.clear()
                if (signViewer.dimensionFilter) signViewer.dimensionFilter.value = 'All'
            } catch (Exception ignored) {
                // Ignore if filter components not accessible
            }

            signViewer.setSigns(model.signs as List<Map<String, Object>>)
            signsTab.content = signViewerUI
            LOGGER.info("SignViewer integrated with ${model.signs.size()} signs")
        } catch (Exception e) {
            LOGGER.error("Failed to create SignViewer: ${e.message}", e)

            // Fallback: simple list view
            BorderPane signPane = new BorderPane()
            signPane.padding = new Insets(10)

            ListView<String> signListView = new ListView<>()
            signListView.cellFactory = { param ->
                new javafx.scene.control.ListCell<String>() {
                    @Override
                    protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty)
                        text = empty ? null : item
                        style = '-fx-font-family: monospace;'
                    }
                }
            }

            signListView.items.addAll(
                model.signs.collect { sign ->
                    "[${sign.dimension ?: '?'}] (${sign.x}, ${sign.y}, ${sign.z}): " +
                        "${sign.line1 ?: ''} | ${sign.line2 ?: ''} | ${sign.line3 ?: ''} | ${sign.line4 ?: ''}"
                }
            )

            signPane.center = signListView
            signsTab.content = signPane
        }
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

        // Find the DB file on disk (needed to launch ItemGridViewer)
        File dbFile = new File(model.outputFolder, 'item_index.db')
        if (!dbFile.exists()) {
            dbFile = new File(model.outputFolder, 'items.db')
        }

        // Summary (lightweight, quick)
        TextArea textArea = new TextArea(editable: false, wrapText: true)

        List<Map> summary = model.itemDatabase.getSummary()
        textArea.text = "Item Database Summary\n\n" +
            "Total item types: ${summary.size()}\n" +
            "Total items indexed: ${model.metadata.totalItemsIndexed}\n" +
            "Total items found: ${model.metadata.totalItemCount}\n\n" +
            "Top 20 item types:\n" +
            summary.take(20).collect { item ->
                "  ${item.item_id}: ${item.total_count} total, ${item.unique_locations} unique locations" +
                    (item.limit_reached ? ' (LIMIT REACHED)' : '')
            }.join('\n')

        Button openGridBtn = new Button('Open Item Grid Viewer…')
        openGridBtn.disable = !dbFile.exists()
        openGridBtn.tooltip = new Tooltip(dbFile.exists() ? 'Open JEI-style item grid (may take a moment for huge databases)' :
            'Database file not found on disk (expected item_index.db or items.db)')

        openGridBtn.onAction = { event ->
            openItemGridViewer(dbFile, null, openGridBtn)
        }

        VBox container = new VBox(10)
        container.padding = new Insets(10)
        container.children.addAll(openGridBtn, textArea)
        VBox.setVgrow(textArea, Priority.ALWAYS)

        itemsTab.content = container
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
        Tab portalsTab = contentTabs.tabs[4]
        if (model.portals.isEmpty()) {
            Label placeholder = new Label('No portals found in output folder')
            placeholder.style = '-fx-text-fill: gray; -fx-font-style: italic;'
            portalsTab.content = new BorderPane(placeholder)
            return
        }

        try {
            List<PortalDetector.Portal> portalObjs = []
            model.portals.each { Map p ->
                try {
                    int anchorX = (p.anchor_x ?: p.anchor?.x ?: p.anchorX ?: 0) as int
                    int anchorY = (p.anchor_y ?: p.anchor?.y ?: p.anchorY ?: 0) as int
                    int anchorZ = (p.anchor_z ?: p.anchor?.z ?: p.anchorZ ?: 0) as int
                    int width = (p.width ?: p.size?.width ?: 0) as int
                    int height = (p.height ?: p.size?.height ?: 0) as int
                    String axis = (p.axis ?: 'z') as String
                    int blockCount = (p.block_count ?: p.blockCount ?: 0) as int

                    double centerX = (p.center_x ?: p.center?.x ?: p.centerX ?: 0.0) as double
                    double centerY = (p.center_y ?: p.center?.y ?: p.centerY ?: 0.0) as double
                    double centerZ = (p.center_z ?: p.center?.z ?: p.centerZ ?: 0.0) as double

                    portalObjs << new PortalDetector.Portal(
                        (p.dimension ?: 'overworld') as String,
                        anchorX, anchorY, anchorZ,
                        width, height, axis,
                        blockCount,
                        centerX, centerY, centerZ
                    )
                } catch (Exception ignored) {
                    // Skip malformed portal rows
                }
            }

            portalViewer = new PortalViewer(portalObjs)
            portalsTab.content = portalViewer
            LOGGER.info("PortalViewer integrated with ${portalObjs.size()} portals")
        } catch (Exception e) {
            LOGGER.error("Failed to create PortalViewer: ${e.message}", e)

            // Fallback to text
            TextArea textArea = new TextArea(editable: false, wrapText: true)
            textArea.text = "Loaded ${model.portals.size()} portals\n\n" +
                model.portals.collect { portal ->
                    "Portal ID: ${portal.portal_id}\n" +
                        "Dimension: ${portal.dimension}\n" +
                        "Blocks: ${portal.block_count}\n" +
                        "Center: (${portal.center_x}, ${portal.center_y}, ${portal.center_z})\n"
                }.join('\n---\n')
            portalsTab.content = textArea
        }
    }

    /**
     * Update the Map tab with embedded MapViewer and populate markers.
     */
    private void updateMapTab() {
        Tab mapTab = contentTabs.tabs[5]

        try {
            if (!mapViewer) {
                mapViewer = new MapViewer()
            }

            // Update markers from current model (markers render when a map image is loaded)
            mapViewer.clearMarkers()
            List<MapViewer.MapMarker> markers = []

            model.signs.each { Map sign ->
                try {
                    markers << new MapViewer.MapMarker(
                        'sign',
                        (sign.x ?: 0) as int,
                        (sign.z ?: 0) as int,
                        (sign.y ?: 0) as int,
                        'Sign',
                        [
                            dimension: sign.dimension,
                            lines: (sign.lines ?: [sign.line1, sign.line2, sign.line3, sign.line4]).findAll { it != null }
                        ]
                    )
                } catch (Exception ignored) {
                    // ignore malformed
                }
            }

            model.portals.each { Map portal ->
                try {
                    markers << new MapViewer.MapMarker(
                        'portal',
                        (portal.center_x ?: portal.center?.x ?: 0) as int,
                        (portal.center_z ?: portal.center?.z ?: 0) as int,
                        (portal.center_y ?: portal.center?.y ?: 0) as int,
                        "Portal ${(portal.width ?: portal.size?.width ?: '?')}×${(portal.height ?: portal.size?.height ?: '?')}",
                        [
                            dimension: portal.dimension,
                            width: portal.width ?: portal.size?.width,
                            height: portal.height ?: portal.size?.height,
                            axis: portal.axis
                        ]
                    )
                } catch (Exception ignored) {
                    // ignore malformed
                }
            }

            // Add custom-name markers (best-effort, capped to avoid UI freezes)
            int maxCustomNameMarkers = 500
            int addedCustom = 0
            model.customNames?.each { Map cn ->
                if (addedCustom >= maxCustomNameMarkers) {
                    return
                }
                try {
                    def x = cn.x
                    def y = cn.y
                    def z = cn.z
                    if (x == null || z == null) {
                        return
                    }
                    markers << new MapViewer.MapMarker(
                        'item',
                        (x ?: 0) as int,
                        (z ?: 0) as int,
                        (y ?: 0) as int,
                        (cn.name ?: cn.customName ?: 'Custom Name') as String,
                        [
                            dimension : cn.dimension ?: cn.Dimension,
                            customName: cn.name ?: cn.customName,
                            itemId    : cn.item_id ?: cn.itemId,
                            type      : cn.type,
                            x         : x,
                            y         : y,
                            z         : z
                        ]
                    )
                    addedCustom++
                } catch (Exception ignored) {
                    // ignore malformed
                }
            }

            if (!markers.isEmpty()) {
                mapViewer.addMarkers(markers)
            }

            mapTab.content = mapViewer
        } catch (Exception e) {
            LOGGER.error("Failed to create MapViewer: ${e.message}", e)
            // Keep the existing placeholder if something goes wrong
        }
    }

    private void openItemGridViewer(File dbFile, String prefillQuery = null, Button sourceButton = null) {
        if (!dbFile?.exists()) {
            showError('Item database not found', 'Expected item_index.db or items.db in the output folder.')
            return
        }

        sourceButton?.disable = true
        if (statusBar) {
            statusBar.text = 'Preparing Item Grid Viewer...'
        }

        Thread.start {
            try {
                ItemGridViewer viewer = new ItemGridViewer(dbFile)
                if (prefillQuery) {
                    viewer.setSearchText(prefillQuery)
                }
                javafx.application.Platform.runLater {
                    try {
                        viewer.show()
                    } finally {
                        sourceButton?.disable = false
                        if (statusBar) {
                            statusBar.text = model.getSummaryText()
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Failed to open ItemGridViewer: ${e.message}", e)
                javafx.application.Platform.runLater {
                    sourceButton?.disable = false
                    if (statusBar) {
                        statusBar.text = model.getSummaryText()
                    }
                    showError('Failed to open Item Grid Viewer', e.message ?: 'Unknown error')
                }
            }
        }
    }

    private static void copyToClipboard(String text) {
        if (!text) return
        try {
            ClipboardContent content = new ClipboardContent()
            content.putString(text)
            Clipboard.systemClipboard.setContent(content)
        } catch (Exception ignored) {
            // Clipboard can be unavailable in some environments; don't fail navigation on that.
        }
    }

    /**
     * Update the Statistics tab with embedded StatsDashboard.
     */
    private void updateStatisticsTab() {
        Tab statsTab = contentTabs.tabs[6]

        try {
            // Create StatsDashboard once and update it
            if (!statsDashboard) {
                statsDashboard = new StatsDashboard()
            }

            // Convert books to the format StatsDashboard expects (grouped by author)
            Map<String, List<Map>> booksByAuthor = model.books.groupBy { it.author ?: 'Unknown' }

            // Create a simple hash map for signs
            Map<String, Map> signsByHash = [:]
            model.signs.eachWithIndex { sign, index ->
                signsByHash["sign_${index}"] = sign
            }

            // Update dashboard with model data
            statsDashboard.updateData(
                booksByAuthor,
                signsByHash,
                model.itemDatabase,
                model.blockDatabase,
                model.portals
            )

            statsTab.content = statsDashboard
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

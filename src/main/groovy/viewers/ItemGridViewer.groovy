package viewers

import groovy.json.JsonSlurper
import javafx.application.Application
import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.KeyCombination
import javafx.scene.input.MouseButton
import javafx.scene.image.ImageView
import javafx.scene.layout.*
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import javafx.scene.text.TextFlow
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import javafx.stage.Stage
import org.fxmisc.flowless.Cell
import org.fxmisc.flowless.VirtualFlow
import org.fxmisc.flowless.VirtualizedScrollPane
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.awt.Desktop
import java.io.File

/**
 * JEI-style Item Grid Viewer for Minecraft item database.
 *
 * Features:
 * - Virtual scrolling for 100k+ items
 * - 48x48px item slots with Minecraft-style rendering
 * - Enchantment glint animation
 * - Rich tooltips with item details
 * - JEI-style search syntax (@player, #enchantment, $named, ~dimension)
 * - Category tabs for filtering
 * - Keyboard shortcuts [R] Details, [C] Copy teleport command
 */
class ItemGridViewer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ItemGridViewer)

    // UI Constants
    private static final int SLOT_SIZE = 48
    private static final int SLOT_PADDING = 2
    private static final int ICON_SIZE = 32

    // Database
    private ItemDatabase database

    // UI Components
    private Stage stage
    private TextField searchField
    private ToggleGroup categoryGroup
    // Note: historically a ScrollPane; now a Flowless VirtualizedScrollPane, but tests stub it as a ScrollPane.
    private def scrollPane
    // Container for the virtualized grid (kept as a field for GUI tests)
    private StackPane itemGrid
    private def statusLabel
    private Tooltip itemTooltip

    // Virtualized grid (Flowless)
    private final ObservableList<RowModel> rowModels = FXCollections.observableArrayList()
    private VirtualFlow<RowModel, Cell<RowModel, Node>> rowFlow
    private VirtualizedScrollPane<VirtualFlow<RowModel, Cell<RowModel, Node>>> rowFlowScroll
    private int currentColumns = 0
    private Label emptyOverlayLabel

    // Selection + details panel
    private Map selectedItem = null
    private Node selectedSlotNode = null
    private VBox detailsPanel
    private ImageView detailsIcon
    private TextFlow detailsTitleFlow
    private Label detailsSubtitleLabel
    private TextArea detailsMetaText
    private Button copyTpButton
    private Button copyIdButton

    // Data
    private List<Map> allItems = []
    private List<Map> filteredItems = []
    private String currentSearchText = ''
    private String currentCategory = 'All'

    private def textureSourceLabel
    private boolean gridDirty = true

    private static class RowModel {
        List<Map> items
        RowModel(List<Map> items) {
            this.items = items
        }
    }

    /**
     * Constructor - opens existing database for viewing.
     */
    ItemGridViewer(File dbFile) {
        this.database = ItemDatabase.openForQuery(dbFile)
        if (!database) {
            throw new IllegalArgumentException("Database file not found: ${dbFile.absolutePath}")
        }
        loadAllItems()
    }

    /**
     * Load all items from database into memory.
     */
    private void loadAllItems() {
        LOGGER.info('Loading items from database...')
        long start = System.currentTimeMillis()

        // Get all item types from summary
        List<Map> summary = database.getSummary()

        // Load full details for each item type
        summary.each { Map itemSummary ->
            String itemId = itemSummary.item_id as String
            List<Map> itemRecords = database.queryByItemType(itemId)
            allItems.addAll(itemRecords)
        }

        long elapsed = System.currentTimeMillis() - start
        LOGGER.info("Loaded ${allItems.size()} items in ${elapsed}ms")

        filteredItems = new ArrayList<>(allItems)
    }

    /**
     * Show the item grid viewer window.
     */
    void show() {
        stage = new Stage()
        stage.title = 'Item Atlas - Indexed Items'

        // Theme + Minecraft CSS
        ThemeManager.initialize()

        BorderPane root = new BorderPane()

        // Top: Menu + search + categories
        MenuBar menuBar = createMenuBar()

        VBox topControls = new VBox(8)
        topControls.padding = new Insets(10)

        HBox searchBox = createSearchBar()
        HBox categoryTabs = createCategoryTabs()
        topControls.children.addAll(searchBox, categoryTabs)

        root.top = new VBox(menuBar, topControls)

        // Center: Virtualized grid + details
        rowFlow = VirtualFlow.createVertical(rowModels, { RowModel row ->
            Cell.wrapNode(createRowNode(row))
        })
        rowFlowScroll = new VirtualizedScrollPane<>(rowFlow)
        scrollPane = rowFlowScroll

        itemGrid = new StackPane(rowFlowScroll)
        // Keep background concrete to avoid CSS lookup recursion in some JavaFX theme/test setups.
        itemGrid.style = '-fx-background-color: #1d1d1d;'

        emptyOverlayLabel = new Label('No items match the current filters')
        emptyOverlayLabel.style = '-fx-text-fill: gray; -fx-font-style: italic; -fx-font-size: 13px;'
        emptyOverlayLabel.visible = false
        itemGrid.children.add(emptyOverlayLabel)
        StackPane.setAlignment(emptyOverlayLabel, Pos.CENTER)

        detailsPanel = createDetailsPanel()

        SplitPane split = new SplitPane()
        split.items.addAll(itemGrid, detailsPanel)
        split.dividerPositions = [0.72] as double[]
        root.center = split

        // Bottom: Status bar
        statusLabel = new Label()
        statusLabel.style = '-fx-font-size: 11px; -fx-text-fill: #b0b0b0;'

        textureSourceLabel = new Label("Textures: ${IconManager.getTextureSourceLabel()}")
        textureSourceLabel.style = '-fx-font-size: 11px; -fx-text-fill: #b0b0b0;'

        Region spacer = new Region()
        HBox.setHgrow(spacer, Priority.ALWAYS)

        HBox bottomBar = new HBox(10, statusLabel, spacer, textureSourceLabel)
        bottomBar.padding = new Insets(6, 10, 6, 10)
        bottomBar.alignment = Pos.CENTER_LEFT
        bottomBar.style = '-fx-background-color: #1d1d1d;'
        root.bottom = bottomBar

        Scene scene = new Scene(root, 1200, 760)
        ThemeManager.registerScene(scene)
        stage.scene = scene

        // Keyboard shortcuts
        scene.accelerators.put(new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN), { ->
            focusSearch()
        })

        scene.onKeyPressed = { event ->
            if (event.code == KeyCode.C && selectedItem != null) {
                copyTeleportCommand(selectedItem)
            } else if (event.code == KeyCode.R && selectedItem != null) {
                showItemDetails(selectedItem)
            } else if (event.code == KeyCode.ESCAPE) {
                clearSelection()
            }
        }

        // Reflow grid on resize
        scrollPane.widthProperty().addListener { obs, oldVal, newVal ->
            Platform.runLater { updateGrid(false) }
        }

        stage.show()

        // Initial render (respect any pre-set search text)
        // IMPORTANT: schedule after the current FX pulse so show() returns quickly (keeps GUI tests reliable).
        Platform.runLater {
            if (currentSearchText?.trim()) {
                // Triggers applyFilters() via listener
                searchField.text = currentSearchText
            } else {
                updateGrid(true)
                updateStatus()
            }
        }

        // Cleanup on close
        stage.onCloseRequest = { event ->
            ThemeManager.unregisterScene(scene)
            database?.close()
        }
    }

    /**
     * Set search text programmatically (useful for GlobalSearch navigation).
     * Can be called before or after show().
     */
    void setSearchText(String text) {
        currentSearchText = text ?: ''
        if (searchField) {
            // Triggers applyFilters() via listener
            searchField.text = currentSearchText
        }
    }

    private void focusSearch() {
        if (!searchField) return
        searchField.requestFocus()
        searchField.selectAll()
    }

    private MenuBar createMenuBar() {
        MenuBar bar = new MenuBar()

        // Textures menu (user-provided assets only; we do not ship MC textures)
        Menu texturesMenu = new Menu('Textures')

        MenuItem loadJar = new MenuItem('Load Minecraft Client .jar...')
        loadJar.onAction = { event ->
            FileChooser chooser = new FileChooser()
            chooser.title = 'Select Minecraft client .jar (e.g., .minecraft/versions/<ver>/<ver>.jar)'
            chooser.extensionFilters.add(new FileChooser.ExtensionFilter('Minecraft client jar', '*.jar'))
            chooser.extensionFilters.add(new FileChooser.ExtensionFilter('Archives', '*.jar', '*.zip'))

            File selected = chooser.showOpenDialog(stage)
            if (selected) {
                boolean ok = IconManager.setExternalTextureSource(selected)
                if (!ok) {
                    showErrorDialog("Failed to load textures from: ${selected.absolutePath}")
                }
                updateGrid(true)
                updateStatus()
            }
        }

        MenuItem loadZip = new MenuItem('Load Resource Pack .zip...')
        loadZip.onAction = { event ->
            FileChooser chooser = new FileChooser()
            chooser.title = 'Select a resource pack .zip (must contain assets/minecraft/textures/)'
            chooser.extensionFilters.add(new FileChooser.ExtensionFilter('Resource pack zip', '*.zip'))
            chooser.extensionFilters.add(new FileChooser.ExtensionFilter('Archives', '*.zip', '*.jar'))

            File selected = chooser.showOpenDialog(stage)
            if (selected) {
                boolean ok = IconManager.setExternalTextureSource(selected)
                if (!ok) {
                    showErrorDialog("Failed to load textures from: ${selected.absolutePath}")
                }
                updateGrid(true)
                updateStatus()
            }
        }

        MenuItem loadFolder = new MenuItem('Load Extracted Assets Folder...')
        loadFolder.onAction = { event ->
            DirectoryChooser chooser = new DirectoryChooser()
            chooser.title = 'Select a folder containing assets/minecraft/textures/'

            File selected = chooser.showDialog(stage)
            if (selected) {
                boolean ok = IconManager.setExternalTextureSource(selected)
                if (!ok) {
                    showErrorDialog("Failed to load textures from: ${selected.absolutePath}")
                }
                updateGrid(true)
                updateStatus()
            }
        }

        MenuItem useBuiltIn = new MenuItem('Use Built-in Placeholders')
        useBuiltIn.onAction = { event ->
            IconManager.clearExternalTextureSource()
            updateGrid(true)
            updateStatus()
        }

        texturesMenu.items.addAll(loadJar, loadZip, loadFolder, new SeparatorMenuItem(), useBuiltIn)

        // View menu
        Menu viewMenu = new Menu('View')
        MenuItem toggleTheme = new MenuItem('Toggle Theme')
        toggleTheme.onAction = { event ->
            ThemeManager.toggleTheme()
        }
        viewMenu.items.add(toggleTheme)

        // Help menu
        Menu helpMenu = new Menu('Help')
        MenuItem syntaxItem = new MenuItem('Search Syntax...')
        syntaxItem.onAction = { event ->
            Alert alert = new Alert(Alert.AlertType.INFORMATION)
            alert.title = 'Item Search Syntax'
            alert.headerText = 'JEI-style search'
            alert.contentText = [
                'Examples:',
                '  diamond',
                '  @<player_uuid>     (player inventory owner)',
                '  #sharpness        (enchantment text)',
                '  $named            (only custom-named items)',
                '  ~nether           (dimension filter)',
                '',
                'Tip: Right-click an item for quick actions.'
            ].join('\n')
            alert.showAndWait()
        }
        helpMenu.items.add(syntaxItem)

        bar.menus.addAll(texturesMenu, viewMenu, helpMenu)
        return bar
    }

    private VBox createDetailsPanel() {
        VBox panel = new VBox(10)
        panel.padding = new Insets(12)
        panel.minWidth = 340
        panel.prefWidth = 360
        // Keep background concrete to avoid CSS lookup recursion in some JavaFX theme/test setups.
        panel.style = '-fx-background-color: #2b2b2b;'

        Label header = new Label('Details')
        header.font = Font.font('System', FontWeight.BOLD, 14)

        // Title row
        detailsIcon = new ImageView()
        detailsIcon.fitWidth = 32
        detailsIcon.fitHeight = 32
        detailsIcon.preserveRatio = false
        detailsIcon.smooth = false

        detailsTitleFlow = new TextFlow()
        detailsSubtitleLabel = new Label('Select an item')
        detailsSubtitleLabel.style = '-fx-font-size: 11px; -fx-text-fill: #b0b0b0;'

        VBox titleText = new VBox(2, detailsTitleFlow, detailsSubtitleLabel)
        HBox titleRow = new HBox(10, detailsIcon, titleText)
        titleRow.alignment = Pos.CENTER_LEFT

        // Buttons
        copyTpButton = new Button('Copy /tp')
        copyTpButton.disable = true
        copyTpButton.onAction = { event ->
            if (selectedItem) {
                copyTeleportCommand(selectedItem)
            }
        }

        copyIdButton = new Button('Copy ID')
        copyIdButton.disable = true
        copyIdButton.onAction = { event ->
            if (selectedItem) {
                copyTextToClipboard(selectedItem.item_id as String)
                statusLabel?.setText("Copied: ${selectedItem.item_id}")
            }
        }

        HBox buttonRow = new HBox(8, copyTpButton, copyIdButton)
        buttonRow.alignment = Pos.CENTER_LEFT

        // Meta details
        detailsMetaText = new TextArea()
        detailsMetaText.editable = false
        detailsMetaText.wrapText = true
        detailsMetaText.prefRowCount = 18
        detailsMetaText.style = '-fx-font-family: Consolas, monospace; -fx-font-size: 12px;'
        VBox.setVgrow(detailsMetaText, Priority.ALWAYS)

        panel.children.addAll(header, titleRow, buttonRow, new Separator(), detailsMetaText)

        updateDetailsPanel(null)
        return panel
    }

    private void updateDetailsPanel(Map item) {
        if (!detailsMetaText || !detailsSubtitleLabel || !detailsIcon || !detailsTitleFlow) {
            return
        }

        if (!item) {
            detailsIcon.image = null
            detailsTitleFlow.children.clear()
            detailsTitleFlow.children.addAll(MinecraftTextRenderer.render('§7Select an item§r').children)
            detailsSubtitleLabel.text = 'Tip: Double-click for full details'
            detailsMetaText.text = [
                '• Left click: select',
                '• Double click: open details dialog',
                '• Right click: context menu',
                '',
                'Keyboard:',
                '  Ctrl+F  focus search',
                '  R       details',
                '  C       copy /tp',
                '  Esc     clear selection'
            ].join('\n')

            copyTpButton.disable = true
            copyIdButton.disable = true
            return
        }

        String itemId = (item.item_id ?: 'Unknown') as String
        detailsIcon.image = IconManager.getIcon(itemId)

        detailsTitleFlow.children.clear()
        String titleText = hasCustomName(item) ? "§e${item.custom_name}§r" : "§f${itemId}§r"
        detailsTitleFlow.children.addAll(MinecraftTextRenderer.render(titleText).children)

        String dim = (item.dimension ?: 'unknown') as String
        detailsSubtitleLabel.text = "${dim}  (${item.x}, ${item.y}, ${item.z})"

        detailsMetaText.text = buildDetailsText(item)

        copyIdButton.disable = !(itemId?.trim())
        copyTpButton.disable = (item.x == null || item.y == null || item.z == null)
    }

    private String buildDetailsText(Map item) {
        StringBuilder sb = new StringBuilder()

        sb.append("ID: ${item.item_id}\n")
        if (item.count != null) sb.append("Count: ${item.count}\n")
        if (item.custom_name) sb.append("Name: ${item.custom_name}\n")

        sb.append('\n')
        sb.append("Dimension: ${item.dimension ?: 'unknown'}\n")
        sb.append("Location: (${item.x}, ${item.y}, ${item.z})\n")
        if (item.container_type) sb.append("Container: ${item.container_type}\n")
        if (item.player_uuid) sb.append("Player: ${item.player_uuid}\n")

        if (hasEnchantments(item)) {
            sb.append('\n')
            sb.append('Enchantments:\n')
            if (item.enchantments) {
                parseEnchantments(item.enchantments as String).each { ench, level ->
                    sb.append("  - ${ench} ${level}\n")
                }
            }
            if (item.stored_enchantments) {
                parseEnchantments(item.stored_enchantments as String).each { ench, level ->
                    sb.append("  - ${ench} ${level} (stored)\n")
                }
            }
        }

        if (item.lore) {
            List<String> lore = parseLore(item.lore as String)
            if (lore) {
                sb.append('\n')
                sb.append('Lore:\n')
                lore.each { line -> sb.append("  ${line}\n") }
            }
        }

        sb.append('\n')
        sb.append('Teleport:\n')
        if (item.x != null && item.y != null && item.z != null) {
            sb.append("/tp @s ${item.x} ${item.y} ${item.z}\n")
        } else {
            sb.append('(no coordinates)\n')
        }

        return sb.toString().trim()
    }

    /**
     * Create search bar with JEI-style syntax support.
     */
    private HBox createSearchBar() {
        HBox searchBox = new HBox(10)
        searchBox.alignment = Pos.CENTER_LEFT

        Label searchLabel = new Label('Search:')
        searchLabel.minWidth = 60

        searchField = new TextField()
        searchField.promptText = 'diamond, @steve, #sharpness, $named, ~nether'
        searchField.styleClass.add('search-field')
        HBox.setHgrow(searchField, Priority.ALWAYS)

        searchField.textProperty().addListener { obs, oldVal, newVal ->
            currentSearchText = newVal ?: ''
            applyFilters()
        }

        Button clearBtn = new Button('Clear')
        clearBtn.onAction = { searchField.text = '' }

        searchBox.children.addAll(searchLabel, searchField, clearBtn)
        return searchBox
    }

    /**
     * Create category filter tabs.
     */
    private HBox createCategoryTabs() {
        HBox tabBox = new HBox(5)
        tabBox.alignment = Pos.CENTER_LEFT

        categoryGroup = new ToggleGroup()

        List<String> categories = ['All', 'Tools', 'Weapons', 'Armor', 'Blocks', 'Materials', 'Food', 'Misc']

        categories.each { String category ->
            ToggleButton btn = new ToggleButton(category)
            btn.toggleGroup = categoryGroup
            btn.userData = category
            btn.minWidth = 80
            btn.styleClass.add('category-tab')

            if (category == 'All') {
                btn.selected = true
            }

            tabBox.children.add(btn)
        }

        categoryGroup.selectedToggleProperty().addListener { obs, oldVal, newVal ->
            if (newVal) {
                currentCategory = newVal.userData as String
                applyFilters()
            }
        }

        return tabBox
    }

    /**
     * Apply search and category filters.
     */
    private void applyFilters() {
        filteredItems.clear()
        gridDirty = true

        // Parse search syntax
        Map<String, String> searchCriteria = parseSearchText(currentSearchText)

        allItems.each { Map item ->
            // Category filter
            if (currentCategory != 'All' && !matchesCategory(item, currentCategory)) {
                return
            }

            // Search filters
            if (searchCriteria.name && !matchesName(item, searchCriteria.name)) {
                return
            }
            if (searchCriteria.player && !matchesPlayer(item, searchCriteria.player)) {
                return
            }
            if (searchCriteria.enchantment && !matchesEnchantment(item, searchCriteria.enchantment)) {
                return
            }
            if (searchCriteria.dimension && !matchesDimension(item, searchCriteria.dimension)) {
                return
            }
            if (searchCriteria.named && !hasCustomName(item)) {
                return
            }

            filteredItems.add(item)
        }

        updateGrid(false)
        updateStatus()
    }

    /**
     * Parse JEI-style search text into criteria.
     */
    private Map<String, String> parseSearchText(String text) {
        Map<String, String> criteria = [:]

        if (!text?.trim()) {
            return criteria
        }

        String[] tokens = text.toLowerCase().split(/\s+/)

        tokens.each { String token ->
            if (token.startsWith('@')) {
                criteria.player = token.substring(1)
            } else if (token.startsWith('#')) {
                criteria.enchantment = token.substring(1)
            } else if (token.startsWith('$named')) {
                criteria.named = 'true'
            } else if (token.startsWith('~')) {
                criteria.dimension = token.substring(1)
            } else if (!token.empty) {
                criteria.name = token
            }
        }

        return criteria
    }

    /**
     * Check if item matches category.
     */
    private boolean matchesCategory(Map item, String category) {
        String itemId = (item.item_id as String).toLowerCase()

        switch (category) {
            case 'Tools':
                return itemId.contains('pickaxe') || itemId.contains('axe') || itemId.contains('shovel') ||
                       itemId.contains('hoe') || itemId.contains('shears') || itemId.contains('fishing_rod')
            case 'Weapons':
                return itemId.contains('sword') || itemId.contains('bow') || itemId.contains('crossbow') ||
                       itemId.contains('trident') || itemId.contains('mace')
            case 'Armor':
                return itemId.contains('helmet') || itemId.contains('chestplate') || itemId.contains('leggings') ||
                       itemId.contains('boots') || itemId.contains('elytra')
            case 'Blocks':
                return itemId.contains('_ore') || itemId.contains('stone') || itemId.contains('wood') ||
                       itemId.contains('planks') || itemId.contains('log') || itemId.contains('dirt')
            case 'Materials':
                return itemId.contains('ingot') || itemId.contains('gem') || itemId.contains('dust') ||
                       itemId.contains('nugget') || itemId.contains('stick') || itemId.contains('string') ||
                       // Gems/minerals (avoid matching tools like diamond_sword)
                       itemId == 'minecraft:diamond' || itemId == 'minecraft:emerald' ||
                       itemId == 'minecraft:lapis_lazuli' || itemId == 'minecraft:amethyst_shard' ||
                       itemId == 'minecraft:quartz'
            case 'Food':
                return itemId.contains('apple') || itemId.contains('bread') ||
                       itemId.contains('beef') || itemId.contains('pork') || itemId.contains('porkchop') ||
                       itemId.contains('chicken') || itemId.contains('mutton') || itemId.contains('rabbit') ||
                       itemId.contains('fish') || itemId.contains('cod') || itemId.contains('salmon') ||
                       itemId.contains('carrot') || itemId.contains('potato') || itemId.contains('beetroot') ||
                       itemId.contains('stew') || itemId.contains('cookie') || itemId.contains('cake') ||
                       itemId.contains('pumpkin_pie') || itemId.contains('golden_apple')
            case 'Misc':
            case 'All':
            default:
                return true
        }
    }

    private boolean matchesName(Map item, String name) {
        String itemId = (item.item_id as String).toLowerCase()
        String customName = (item.custom_name as String)?.toLowerCase() ?: ''
        return itemId.contains(name) || customName.contains(name)
    }

    private boolean matchesPlayer(Map item, String player) {
        String playerUuid = (item.player_uuid as String)?.toLowerCase() ?: ''
        return playerUuid.contains(player)
    }

    private boolean matchesEnchantment(Map item, String enchantment) {
        String enchantments = (item.enchantments as String)?.toLowerCase() ?: ''
        String storedEnchantments = (item.stored_enchantments as String)?.toLowerCase() ?: ''
        return enchantments.contains(enchantment) || storedEnchantments.contains(enchantment)
    }

    private boolean matchesDimension(Map item, String dimension) {
        String itemDim = (item.dimension as String)?.toLowerCase() ?: ''
        return itemDim.contains(dimension)
    }

    private boolean hasCustomName(Map item) {
        return item.custom_name != null && !(item.custom_name as String).trim().empty
    }

    /**
     * Update the item grid display.
     */
    private void updateGrid(boolean force = false) {
        if (rowModels == null) {
            return
        }

        int cols = calculateColumns()
        if (force || cols != currentColumns) {
            currentColumns = cols
            gridDirty = true
        }

        if (!gridDirty) {
            return
        }

        // Rebuild row models (VirtualFlow only renders visible rows)
        rowModels.setAll(buildRows(filteredItems, currentColumns))
        gridDirty = false

        if (emptyOverlayLabel) {
            emptyOverlayLabel.visible = (filteredItems == null || filteredItems.isEmpty())
        }
    }

    private List<RowModel> buildRows(List<Map> items, int cols) {
        List<RowModel> rows = []
        if (!items || cols <= 0) {
            return rows
        }

        for (int i = 0; i < items.size(); i += cols) {
            int end = Math.min(i + cols, items.size())
            rows << new RowModel(items.subList(i, end))
        }

        return rows
    }

    private Node createRowNode(RowModel row) {
        HBox box = new HBox(SLOT_PADDING)
        box.alignment = Pos.CENTER_LEFT
        box.padding = new Insets(0, 10, 0, 10)

        row.items.each { Map item ->
            box.children.add(createItemSlotNode(item))
        }

        return box
    }

    /**
     * Calculate number of columns based on window width.
     */
    private int calculateColumns() {
        double width = scrollPane?.width ?: 0
        if (width <= 0) {
            width = 950
        }
        int columns = ((width - 40) / (SLOT_SIZE + SLOT_PADDING)) as int
        return Math.max(1, columns)
    }

    /**
     * Create a single JEI-style item slot node.
     */
    private Node createItemSlotNode(Map item) {
        StackPane slot = new StackPane()
        slot.prefWidth = SLOT_SIZE
        slot.prefHeight = SLOT_SIZE
        slot.minWidth = SLOT_SIZE
        slot.minHeight = SLOT_SIZE
        slot.maxWidth = SLOT_SIZE
        slot.maxHeight = SLOT_SIZE

        slot.styleClass.add('item-slot')
        if (hasEnchantments(item)) {
            slot.styleClass.add('enchanted')
        }
        if (hasCustomName(item)) {
            slot.styleClass.add('named')
        }

        // Icon
        String itemId = (item.item_id ?: '') as String
        ImageView icon = new ImageView(IconManager.getIcon(itemId))
        icon.fitWidth = ICON_SIZE
        icon.fitHeight = ICON_SIZE
        icon.preserveRatio = false
        icon.smooth = false
        icon.userData = itemId
        slot.children.add(icon)
        StackPane.setAlignment(icon, Pos.CENTER)

        // Glint overlay (animated, only for enchanted items)
        if (hasEnchantments(item)) {
            Node glint = IconManager.createEnchantGlint()
            glint.mouseTransparent = true
            slot.children.add(glint)
            StackPane.setAlignment(glint, Pos.CENTER)
        }

        // Count overlay (bottom-right)
        int count = (item.count ?: 1) as int
        if (count > 1) {
            Label countLabel = new Label(formatCount(count))
            countLabel.styleClass.add('item-count')
            slot.children.add(countLabel)
            StackPane.setAlignment(countLabel, Pos.BOTTOM_RIGHT)
            StackPane.setMargin(countLabel, new Insets(0, 2, 2, 0))
        }

        Tooltip.install(slot, createTooltip(item))

        // Interactions
        slot.onMouseClicked = { event ->
            if (event.button == MouseButton.PRIMARY) {
                selectItem(item, slot)
                if (event.clickCount == 2) {
                    showItemDetails(item)
                }
            } else if (event.button == MouseButton.SECONDARY) {
                showContextMenu(item, slot, event.screenX, event.screenY)
            }
        }

        return slot
    }

    private void selectItem(Map item, Node slotNode) {
        if (selectedSlotNode) {
            try {
                selectedSlotNode.styleClass.remove('selected')
            } catch (Exception ignored) {
                // ignore
            }
        }

        selectedItem = item
        selectedSlotNode = slotNode
        try {
            selectedSlotNode.styleClass.add('selected')
        } catch (Exception ignored) {
            // ignore
        }

        updateDetailsPanel(item)
    }

    private void clearSelection() {
        if (selectedSlotNode) {
            try {
                selectedSlotNode.styleClass.remove('selected')
            } catch (Exception ignored) {
                // ignore
            }
        }
        selectedItem = null
        selectedSlotNode = null
        updateDetailsPanel(null)
    }

    private Tooltip createTooltip(Map item) {
        Tooltip tooltip = new Tooltip()
        tooltip.text = buildTooltipText(item)
        tooltip.styleClass.add('minecraft-tooltip')
        return tooltip
    }

    private void showContextMenu(Map item, Node anchor, double screenX, double screenY) {
        ContextMenu menu = new ContextMenu()

        MenuItem copyId = new MenuItem('Copy Item ID')
        copyId.onAction = { evt ->
            copyTextToClipboard(item.item_id as String)
            statusLabel?.setText("Copied: ${item.item_id}")
        }

        MenuItem copyTp = new MenuItem('Copy Teleport (/tp)')
        copyTp.disable = (item.x == null || item.y == null || item.z == null)
        copyTp.onAction = { evt ->
            copyTeleportCommand(item)
        }

        MenuItem filterToThis = new MenuItem('Filter to this Item ID')
        filterToThis.onAction = { evt ->
            if (searchField) {
                searchField.text = (item.item_id ?: '') as String
            } else {
                setSearchText(item.item_id as String)
            }
        }

        MenuItem details = new MenuItem('Show Details...')
        details.onAction = { evt -> showItemDetails(item) }

        menu.items.addAll(copyId, copyTp, new SeparatorMenuItem(), filterToThis, details)
        menu.show(anchor, screenX, screenY)
    }

    private static void copyTextToClipboard(String text) {
        if (!text) return
        try {
            ClipboardContent content = new ClipboardContent()
            content.putString(text)
            Clipboard.systemClipboard.setContent(content)
        } catch (Exception ignored) {
            // clipboard can fail in CI/headless
        }
    }

    private static String formatCount(int count) {
        if (count >= 1000000) return String.format('%.1fm', count / 1000000.0)
        if (count >= 10000) return String.format('%.1fk', count / 1000.0)
        if (count >= 1000) return "${(int) (count / 1000)}k"
        return count.toString()
    }

    /**
     * Extract readable item name from item ID.
     */
    private String extractItemName(String itemId) {
        String name = itemId.replace('minecraft:', '')
        name = name.replace('_', ' ')
        return name.take(12)
    }

    /**
     * Check if item has enchantments.
     */
    private boolean hasEnchantments(Map item) {
        String enchantments = item.enchantments as String
        String storedEnchantments = item.stored_enchantments as String
        return (enchantments && enchantments != '{}' && enchantments != '[]') ||
               (storedEnchantments && storedEnchantments != '{}' && storedEnchantments != '[]')
    }

    /**
     * Build tooltip text for item.
     */
    private String buildTooltipText(Map item) {
        StringBuilder sb = new StringBuilder()

        // Item name
        String itemId = item.item_id as String
        String displayName = extractItemName(itemId)

        if (hasCustomName(item)) {
            sb.append("§6${item.custom_name}§r\n")
            sb.append("§7${displayName}§r\n")
        } else {
            sb.append("§f${displayName}§r\n")
        }

        // Enchantments
        if (hasEnchantments(item)) {
            sb.append('\n')
            if (item.enchantments) {
                parseEnchantments(item.enchantments as String).each { ench, level ->
                    sb.append("§9${ench} ${level}§r\n")
                }
            }
            if (item.stored_enchantments) {
                parseEnchantments(item.stored_enchantments as String).each { ench, level ->
                    sb.append("§9${ench} ${level} (Stored)§r\n")
                }
            }
        }

        // Lore
        if (item.lore) {
            sb.append('\n')
            List<String> loreLines = parseLore(item.lore as String)
            loreLines.each { line ->
                sb.append("§5§o${line}§r\n")
            }
        }

        // Location
        sb.append('\n')
        sb.append("§7Location: ${item.dimension ?: 'unknown'} (${item.x}, ${item.y}, ${item.z})§r\n")

        if (item.container_type) {
            sb.append("§7Container: ${item.container_type}§r\n")
        }

        // Keyboard hints
        sb.append('\n')
        sb.append('§8[R] Details  [C] Copy TP§r')

        return sb.toString().replace('§', '')  // Remove color codes for now
    }

    /**
     * Parse enchantments JSON.
     */
    private Map<String, Integer> parseEnchantments(String json) {
        try {
            if (json && json != '{}' && json != '[]') {
                return new JsonSlurper().parseText(json) as Map<String, Integer>
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to parse enchantments: ${json}", e)
        }
        return [:]
    }

    /**
     * Parse lore JSON.
     */
    private List<String> parseLore(String json) {
        try {
            if (json && json != '[]') {
                return new JsonSlurper().parseText(json) as List<String>
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to parse lore: ${json}", e)
        }
        return []
    }

    /**
     * Handle item click (show details or copy).
     */
    private void handleItemClick(Map item, def event) {
        if (event.clickCount == 2) {
            showItemDetails(item)
        }
    }

    /**
     * Show detailed item information dialog.
     */
    private void showItemDetails(Map item) {
        Alert dialog = new Alert(Alert.AlertType.INFORMATION)
        dialog.title = 'Item Details'
        dialog.headerText = item.item_id as String

        TextArea content = new TextArea()
        content.editable = false
        content.wrapText = true
        content.prefColumnCount = 40
        content.prefRowCount = 20

        StringBuilder sb = new StringBuilder()
        item.each { key, value ->
            sb.append("${key}: ${value}\n")
        }
        content.text = sb.toString()

        dialog.dialogPane.content = content
        dialog.dialogPane.prefWidth = 600

        ButtonType copyTpBtn = new ButtonType('Copy Teleport', ButtonBar.ButtonData.OK_DONE)
        dialog.buttonTypes.setAll(copyTpBtn, ButtonType.CLOSE)

        dialog.showAndWait().ifPresent { result ->
            if (result == copyTpBtn) {
                copyTeleportCommand(item)
            }
        }
    }

    /**
     * Copy teleport command to clipboard.
     */
    private String copyTeleportCommand(Map item) {
        int x = item.x as Integer
        int y = item.y as Integer
        int z = item.z as Integer
        String command = "/tp @s ${x} ${y} ${z}"

        try {
            ClipboardContent content = new ClipboardContent()
            content.putString(command)
            Clipboard.systemClipboard.setContent(content)
        } catch (Exception e) {
            // Clipboard access can fail in CI/headless/remote environments or when not on the FX thread.
            LOGGER.debug("Failed to copy teleport command to clipboard: ${e.message}")
        }

        if (statusLabel) {
            statusLabel.text = "Copied: ${command}"
        }
        LOGGER.info("Copied teleport command: ${command}")
        return command
    }

    /**
     * Update status bar.
     */
    private void updateStatus() {
        if (!statusLabel) {
            return
        }
        statusLabel.text = "${filteredItems.size()} / ${allItems.size()} items"
        if (textureSourceLabel) {
            textureSourceLabel.text = "Textures: ${IconManager.getTextureSourceLabel()}"
        }
    }

    /**
     * Launch standalone viewer.
     */
    static void main(String[] args) {
        javafx.application.Application.launch(ItemGridViewerApp, args)
    }
}

/**
 * Standalone JavaFX application wrapper.
 */
class ItemGridViewerApp extends javafx.application.Application {

    void start(javafx.stage.Stage primaryStage) throws Exception {
        Application.Parameters params = parameters
        List<String> args = params.raw

        if (args.empty) {
            showUsageDialog()
            javafx.application.Platform.exit()
            return
        }

        File dbFile = new File(args[0])
        if (!dbFile.exists()) {
            showErrorDialog("Database file not found: ${dbFile.absolutePath}")
            javafx.application.Platform.exit()
            return
        }

        ItemGridViewer viewer = new ItemGridViewer(dbFile)
        viewer.show()
    }

    private void showUsageDialog() {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION)
        alert.title = 'Item Grid Viewer'
        alert.headerText = 'Usage'
        alert.contentText = 'java -jar ItemGridViewer.jar <path-to-items.db>'
        alert.showAndWait()
    }

    private void showErrorDialog(String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR)
        alert.title = 'Error'
        alert.headerText = 'Failed to open database'
        alert.contentText = message
        alert.showAndWait()
    }
}

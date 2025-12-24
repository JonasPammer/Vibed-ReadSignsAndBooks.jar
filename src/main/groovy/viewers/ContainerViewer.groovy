package viewers

// ItemDatabase is in root package, no need for import
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Alert
import javafx.scene.control.Label
import javafx.scene.control.Tooltip
import javafx.scene.image.ImageView
import javafx.scene.layout.GridPane
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.stage.Stage

import java.util.function.Consumer

/**
 * Popup window showing all items in a specific container with Minecraft-style inventory grid layout.
 *
 * Features:
 * - Grid layout matching container type (chest, shulker, hopper, etc.)
 * - Item icons with counts and enchantment glints
 * - Tooltips showing item details
 * - Click items to see full details
 */
class ContainerViewer extends Stage {

    /** Container type to grid size mapping [columns, rows] */
    static final Map<String, int[]> CONTAINER_SIZES = [
        'chest': [9, 3],
        'double_chest': [9, 6],
        'trapped_chest': [9, 3],
        'shulker_box': [9, 3],
        'hopper': [5, 1],
        'dispenser': [3, 3],
        'dropper': [3, 3],
        'barrel': [9, 3],
        'player': [9, 4],
        'furnace': [3, 1],
        'smoker': [3, 1],
        'blast_furnace': [3, 1],
        'brewing_stand': [5, 1],
        'minecart': [9, 3],  // Same as chest
        'default': [9, 3]  // Fallback
    ]

    GridPane itemGrid
    List<Map> items
    String containerType
    Consumer<Map> onItemSelected

    /**
     * Create container viewer popup.
     *
     * @param containerType Type of container (chest, shulker_box, etc.)
     * @param items List of item maps with itemId, count, customName, enchantments, etc.
     * @param title Window title (optional, defaults to container type)
     */
    ContainerViewer(String containerType, List<Map> items, String title = null) {
        this.containerType = containerType ?: 'chest'
        this.items = items ?: []

        this.title = title ?: "${this.containerType.replace('_', ' ').capitalize()} Contents"
        initOwner(null)

        buildUI()
    }

    /**
     * Build the inventory grid UI.
     */
    private void buildUI() {
        // Get grid dimensions for container type
        int[] size = CONTAINER_SIZES[containerType] ?: CONTAINER_SIZES['default']
        int cols = size[0]
        int rows = size[1]

        // Create inventory grid
        itemGrid = new GridPane()
        itemGrid.hgap = 2
        itemGrid.vgap = 2
        itemGrid.padding = new Insets(10)
        itemGrid.style = '-fx-background-color: #c6c6c6;'  // Minecraft inventory background

        // Create all slots
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int slotIndex = row * cols + col

                StackPane slot = createSlot(slotIndex)
                itemGrid.add(slot, col, row)
            }
        }

        // Container info label
        Label infoLabel = new Label("${items.size()} items in ${containerType.replace('_', ' ')}")
        infoLabel.style = '-fx-font-size: 11px; -fx-text-fill: gray;'

        // Layout
        VBox root = new VBox(10, itemGrid, infoLabel)
        root.padding = new Insets(15)
        root.alignment = Pos.CENTER

        scene = new Scene(root)
        ThemeManager.registerScene(scene)

        // Size based on container dimensions
        width = cols * 50 + 40
        height = rows * 50 + 100
        resizable = false
    }

    /**
     * Create a single inventory slot with item icon, count, and interactions.
     */
    private StackPane createSlot(int index) {
        StackPane slot = new StackPane()
        slot.prefWidth = 48
        slot.prefHeight = 48
        slot.styleClass.add('item-slot')
        slot.style = '-fx-background-color: #8b8b8b; -fx-border-color: #373737; -fx-border-width: 2;'

        // If slot has item, populate it
        if (index < items.size()) {
            Map item = items[index]

            // Item icon
            ImageView icon = new ImageView(IconManager.getIcon(item.itemId as String))
            icon.fitWidth = 32
            icon.fitHeight = 32
            icon.preserveRatio = true

            // Item count badge
            Label count = new Label()
            if ((item.count ?: 1) > 1) {
                count.text = item.count.toString()
                count.styleClass.add('item-count')
                count.style = '-fx-font-size: 10px; -fx-text-fill: white; -fx-font-weight: bold; ' +
                             '-fx-background-color: rgba(0,0,0,0.5); -fx-padding: 1 3 1 3; -fx-background-radius: 3;'
            }
            StackPane.setAlignment(count, Pos.BOTTOM_RIGHT)
            StackPane.setMargin(count, new Insets(0, 2, 2, 0))

            slot.children.addAll(icon, count)

            // Enchantment glint overlay
            if (item.enchantments) {
                def glint = IconManager.createEnchantGlint()
                slot.children.add(glint)
                slot.styleClass.add('enchanted')
            }

            // Click handler - notify listener
            slot.onMouseClicked = { event ->
                if (onItemSelected) {
                    onItemSelected.accept(item)
                }
            }

            // Hover effect
            slot.onMouseEntered = { event ->
                slot.style = '-fx-background-color: #afafaf; -fx-border-color: #373737; -fx-border-width: 2;'
            }
            slot.onMouseExited = { event ->
                slot.style = '-fx-background-color: #8b8b8b; -fx-border-color: #373737; -fx-border-width: 2;'
            }

            // Tooltip with item details
            Tooltip tooltip = new Tooltip(buildTooltip(item))
            tooltip.showDelay = javafx.util.Duration.millis(300)
            Tooltip.install(slot, tooltip)
        } else {
            // Empty slot styling
            slot.style = '-fx-background-color: #8b8b8b; -fx-border-color: #555555; -fx-border-width: 1;'
        }

        return slot
    }

    /**
     * Build tooltip text for an item showing name, enchantments, and count.
     */
    private String buildTooltip(Map item) {
        def sb = new StringBuilder()

        // Item name
        String itemName = (item.itemId as String)
            .replace('minecraft:', '')
            .replace('_', ' ')
            .split(' ')
            .collect { it.capitalize() }
            .join(' ')

        sb.append(itemName)

        // Custom name (if present)
        if (item.customName) {
            sb.append("\n\"${item.customName}\"")
        }

        // Enchantments
        if (item.enchantments) {
            sb.append("\n")
            (item.enchantments as Map).each { k, v ->
                String enchName = (k as String)
                    .replace('minecraft:', '')
                    .replace('_', ' ')
                    .split(' ')
                    .collect { it.capitalize() }
                    .join(' ')
                sb.append("\n  ${enchName} ${v}")
            }
        }

        // Item count
        int itemCount = (item.count ?: 1) as int
        if (itemCount > 1) {
            sb.append("\n\nCount: ${itemCount}")
        }

        // Coordinates (if available)
        if (item.x != null && item.y != null && item.z != null) {
            sb.append("\nLocation: (${item.x}, ${item.y}, ${item.z})")
        }

        return sb.toString()
    }

    /**
     * Set callback for when an item is clicked.
     */
    void setOnItemSelected(Consumer<Map> callback) {
        this.onItemSelected = callback
    }

    /**
     * Static factory method to show container contents at specific coordinates.
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @param dimension Dimension name (overworld, nether, end)
     * @param db ItemDatabase instance
     */
    static void showContainerAt(int x, int y, int z, String dimension, ItemDatabase db) {
        // Query items at this location
        def items = db.getItemsAt(x, y, z, dimension)

        if (items.empty) {
            new Alert(
                Alert.AlertType.INFORMATION,
                "No items found at coordinates ($x, $y, $z) in ${dimension}"
            ).showAndWait()
            return
        }

        // Determine container type from first item's container field
        String containerType = (items[0].container ?: 'chest') as String

        // Create and show viewer
        def viewer = new ContainerViewer(
            containerType,
            items,
            "Container at ($x, $y, $z) in ${dimension}"
        )
        viewer.show()
    }

    /**
     * Static factory method to show container contents for a specific container.
     *
     * @param containerType Type of container (chest, shulker_box, etc.)
     * @param items List of item maps
     * @param title Optional window title
     */
    static void showContainer(String containerType, List<Map> items, String title = null) {
        if (items.empty) {
            new Alert(
                Alert.AlertType.INFORMATION,
                "This container is empty"
            ).showAndWait()
            return
        }

        def viewer = new ContainerViewer(containerType, items, title)
        viewer.show()
    }
}

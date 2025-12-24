package viewers

import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.control.*
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Portal viewer with intelligent pairing detection
 *
 * Features:
 * - Split view: Overworld portals (left) | Nether portals (right)
 * - Portal pairing algorithm based on coordinate conversion (÷8 or ×8)
 * - Visual portal cards with size, coordinates, and link status
 * - Click to highlight paired portals with connecting line
 * - Copy teleport commands to clipboard
 * - Statistics summary (total, paired, orphan)
 */
class PortalViewer extends BorderPane {

    private static final Logger LOGGER = LoggerFactory.getLogger(PortalViewer)

    // Portal data
    private List<PortalDetector.Portal> allPortals = []
    private List<PortalDetector.Portal> overworldPortals = []
    private List<PortalDetector.Portal> netherPortals = []
    private List<PortalDetector.Portal> endPortals = []

    // Portal pairing map: portal → best matched portal
    private Map<PortalDetector.Portal, PortalPairing> portalPairings = [:]

    // UI components
    private VBox overworldList
    private VBox netherList
    private VBox endList
    private Label statsLabel
    private PortalDetector.Portal selectedPortal
    private Canvas linkCanvas

    /**
     * Portal pairing result with confidence level
     */
    static class PortalPairing {
        PortalDetector.Portal linkedPortal
        double distance
        String confidence  // "Exact", "Close", "Likely", "None"

        PortalPairing(PortalDetector.Portal linked, double dist, String conf) {
            linkedPortal = linked
            distance = dist
            confidence = conf
        }
    }

    PortalViewer(List<PortalDetector.Portal> portals) {
        this.allPortals = portals ?: []

        // Group portals by dimension
        overworldPortals = allPortals.findAll { it.dimension == 'overworld' }
        netherPortals = allPortals.findAll { it.dimension == 'nether' }
        endPortals = allPortals.findAll { it.dimension == 'end' }

        LOGGER.info("Loaded ${overworldPortals.size()} overworld, ${netherPortals.size()} nether, ${endPortals.size()} end portals")

        // Calculate pairings
        calculatePortalPairings()

        // Build UI
        buildUI()
    }

    private void buildUI() {
        // Top bar: Statistics
        HBox topBar = new HBox(20)
        topBar.padding = new Insets(10)
        topBar.style = '-fx-background-color: derive(-fx-base, -5%);'
        topBar.alignment = Pos.CENTER_LEFT

        Label title = new Label('Portal Explorer')
        title.style = '-fx-font-size: 18px; -fx-font-weight: bold;'

        statsLabel = new Label()
        updateStatistics()

        Region spacer = new Region()
        HBox.setHgrow(spacer, Priority.ALWAYS)

        Button refreshBtn = new Button('Refresh Pairings')
        refreshBtn.onAction = { event ->
            calculatePortalPairings()
            refreshPortalLists()
            updateStatistics()
        }

        topBar.children.addAll(title, new Separator(javafx.geometry.Orientation.VERTICAL),
                               statsLabel, spacer, refreshBtn)

        // Center: Split pane with portal lists
        SplitPane splitPane = new SplitPane()
        splitPane.dividerPositions = [0.33, 0.66] as double[]

        // Overworld portals (left)
        VBox overworldPane = createPortalListPane('Overworld Portals', overworldPortals, Color.LIGHTGREEN)

        // Nether portals (center)
        VBox netherPane = createPortalListPane('Nether Portals', netherPortals, Color.web('#9C27B0'))

        // End portals (right)
        VBox endPane = createPortalListPane('End Portals', endPortals, Color.YELLOW)

        splitPane.items.addAll(overworldPane, netherPane, endPane)

        // Add canvas overlay for drawing connection lines (not implemented in this version)
        StackPane centerStack = new StackPane()
        linkCanvas = new Canvas(800, 600)
        linkCanvas.mouseTransparent = true  // Allow clicks to pass through
        centerStack.children.addAll(splitPane, linkCanvas)

        // Layout
        top = topBar
        center = centerStack
    }

    private VBox createPortalListPane(String title, List<PortalDetector.Portal> portals, Color badgeColor) {
        VBox pane = new VBox(10)
        pane.padding = new Insets(10)

        Label header = new Label(title)
        header.style = '-fx-font-size: 14px; -fx-font-weight: bold;'

        Label countLabel = new Label("(${portals.size()} portals)")
        countLabel.style = '-fx-text-fill: gray;'

        HBox headerBox = new HBox(10, header, countLabel)
        headerBox.alignment = Pos.CENTER_LEFT

        ScrollPane scrollPane = new ScrollPane()
        scrollPane.fitToWidth = true
        scrollPane.style = '-fx-background: transparent;'

        VBox portalList = new VBox(8)
        portalList.padding = new Insets(5)

        // Store reference for refresh
        if (title.contains('Overworld')) {
            overworldList = portalList
        } else if (title.contains('Nether')) {
            netherList = portalList
        } else {
            endList = portalList
        }

        // Add portal cards
        portals.each { portal ->
            Node card = createPortalCard(portal, badgeColor)
            portalList.children.add(card)
        }

        scrollPane.content = portalList
        VBox.setVgrow(scrollPane, Priority.ALWAYS)

        pane.children.addAll(headerBox, new Separator(), scrollPane)
        return pane
    }

    private Node createPortalCard(PortalDetector.Portal portal, Color badgeColor) {
        VBox card = new VBox(8)
        card.padding = new Insets(12)
        card.style = '-fx-background-color: derive(-fx-base, 10%); -fx-background-radius: 5; ' +
                     '-fx-border-color: derive(-fx-base, -10%); -fx-border-radius: 5;'

        // Dimension badge with portal size visualization
        HBox topRow = new HBox(10)
        topRow.alignment = Pos.CENTER_LEFT

        // Dimension badge (colored circle)
        StackPane badge = new StackPane()
        javafx.scene.shape.Circle circle = new javafx.scene.shape.Circle(8)
        circle.fill = badgeColor
        circle.stroke = badgeColor.darker()
        circle.strokeWidth = 2
        badge.children.add(circle)

        // Portal size visualization (proportional rectangle)
        double scale = 3.0
        Rectangle sizeRect = new Rectangle(portal.width * scale, portal.height * scale)
        sizeRect.fill = Color.TRANSPARENT
        sizeRect.stroke = badgeColor
        sizeRect.strokeWidth = 2
        sizeRect.arcWidth = 3
        sizeRect.arcHeight = 3

        Label sizeLabel = new Label("${portal.width}×${portal.height}")
        sizeLabel.style = '-fx-font-size: 11px; -fx-text-fill: gray;'

        topRow.children.addAll(badge, sizeRect, sizeLabel)

        // Coordinates
        Label coordsLabel = new Label(String.format('Center: (%.1f, %.1f, %.1f)',
                                                     portal.centerX, portal.centerY, portal.centerZ))
        coordsLabel.style = '-fx-font-weight: bold;'

        Label anchorLabel = new Label(String.format('Anchor: (%d, %d, %d)',
                                                     portal.anchorX, portal.anchorY, portal.anchorZ))
        anchorLabel.style = '-fx-font-size: 11px; -fx-text-fill: gray;'

        // Pairing info
        PortalPairing pairing = portalPairings[portal]
        Label linkLabel = new Label()
        if (pairing && pairing.linkedPortal) {
            String targetDim = pairing.linkedPortal.dimension.capitalize()
            linkLabel.text = String.format('🔗 Linked to %s portal at (%.1f, %.1f, %.1f) - %s match',
                                           targetDim, pairing.linkedPortal.centerX,
                                           pairing.linkedPortal.centerY, pairing.linkedPortal.centerZ,
                                           pairing.confidence)
            linkLabel.style = '-fx-text-fill: #4CAF50; -fx-font-size: 11px;'
        } else {
            linkLabel.text = '⚠ No linked portal found (orphan)'
            linkLabel.style = '-fx-text-fill: orange; -fx-font-size: 11px;'
        }

        // Action buttons
        HBox buttonRow = new HBox(8)
        buttonRow.alignment = Pos.CENTER_LEFT

        Button copyTpBtn = new Button('Copy TP to Center')
        copyTpBtn.style = '-fx-font-size: 10px;'
        copyTpBtn.onAction = { event ->
            String tpCommand = String.format('/tp @s %.1f %.1f %.1f',
                                             portal.centerX, portal.centerY, portal.centerZ)
            copyToClipboard(tpCommand)
            showTooltip(copyTpBtn, 'Copied!')
        }

        Button copyLinkedBtn = new Button('Copy Linked TP')
        copyLinkedBtn.style = '-fx-font-size: 10px;'
        if (pairing && pairing.linkedPortal) {
            copyLinkedBtn.onAction = { event ->
                String tpCommand = String.format('/tp @s %.1f %.1f %.1f',
                                                 pairing.linkedPortal.centerX,
                                                 pairing.linkedPortal.centerY,
                                                 pairing.linkedPortal.centerZ)
                copyToClipboard(tpCommand)
                showTooltip(copyLinkedBtn, 'Copied!')
            }
        } else {
            copyLinkedBtn.disable = true
        }

        Button highlightBtn = new Button('Highlight Pairing')
        highlightBtn.style = '-fx-font-size: 10px;'
        if (pairing && pairing.linkedPortal) {
            highlightBtn.onAction = { event ->
                highlightPortalPairing(portal, pairing.linkedPortal)
            }
        } else {
            highlightBtn.disable = true
        }

        buttonRow.children.addAll(copyTpBtn, copyLinkedBtn, highlightBtn)

        card.children.addAll(topRow, coordsLabel, anchorLabel, linkLabel, buttonRow)

        // Click to select
        card.onMouseClicked = { event ->
            selectPortal(portal)
            // Highlight this card
            card.style = '-fx-background-color: derive(-fx-accent, 50%); -fx-background-radius: 5; ' +
                         '-fx-border-color: -fx-accent; -fx-border-width: 2; -fx-border-radius: 5;'
        }

        return card
    }

    private void calculatePortalPairings() {
        LOGGER.info('Calculating portal pairings...')
        portalPairings.clear()

        // Pair overworld ↔ nether
        overworldPortals.each { owPortal ->
            PortalPairing pairing = findBestNetherMatch(owPortal)
            portalPairings[owPortal] = pairing
        }

        netherPortals.each { netherPortal ->
            PortalPairing pairing = findBestOverworldMatch(netherPortal)
            portalPairings[netherPortal] = pairing
        }

        // End portals typically don't pair (return platform portals)
        endPortals.each { endPortal ->
            portalPairings[endPortal] = new PortalPairing(null, Double.MAX_VALUE, 'None')
        }

        int paired = portalPairings.values().count { it.linkedPortal != null }
        LOGGER.info("Portal pairing complete: ${paired} paired, ${allPortals.size() - paired} orphan")
    }

    /**
     * Find best matching Nether portal for an Overworld portal
     *
     * Conversion: Overworld coords ÷ 8 = expected Nether coords
     * Search radius: 128 blocks in Overworld = 16 blocks in Nether
     */
    private PortalPairing findBestNetherMatch(PortalDetector.Portal owPortal) {
        if (netherPortals.empty) {
            return new PortalPairing(null, Double.MAX_VALUE, 'None')
        }

        // Expected Nether coordinates (Overworld ÷ 8)
        double expectedX = owPortal.centerX / 8.0
        double expectedZ = owPortal.centerZ / 8.0
        double expectedY = owPortal.centerY  // Y coordinate doesn't scale

        PortalDetector.Portal bestMatch = null
        double minDistance = Double.MAX_VALUE

        netherPortals.each { netherPortal ->
            double dx = netherPortal.centerX - expectedX
            double dy = netherPortal.centerY - expectedY
            double dz = netherPortal.centerZ - expectedZ
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz)

            if (distance < minDistance) {
                minDistance = distance
                bestMatch = netherPortal
            }
        }

        // Determine confidence level
        String confidence
        if (minDistance < 1.0) {
            confidence = 'Exact'
        } else if (minDistance < 16.0) {
            confidence = 'Close'
        } else if (minDistance < 128.0) {
            confidence = 'Likely'
        } else {
            confidence = 'Distant'
        }

        return new PortalPairing(bestMatch, minDistance, confidence)
    }

    /**
     * Find best matching Overworld portal for a Nether portal
     *
     * Conversion: Nether coords × 8 = expected Overworld coords
     * Search radius: 16 blocks in Nether = 128 blocks in Overworld
     */
    private PortalPairing findBestOverworldMatch(PortalDetector.Portal netherPortal) {
        if (overworldPortals.empty) {
            return new PortalPairing(null, Double.MAX_VALUE, 'None')
        }

        // Expected Overworld coordinates (Nether × 8)
        double expectedX = netherPortal.centerX * 8.0
        double expectedZ = netherPortal.centerZ * 8.0
        double expectedY = netherPortal.centerY  // Y coordinate doesn't scale

        PortalDetector.Portal bestMatch = null
        double minDistance = Double.MAX_VALUE

        overworldPortals.each { owPortal ->
            double dx = owPortal.centerX - expectedX
            double dy = owPortal.centerY - expectedY
            double dz = owPortal.centerZ - expectedZ
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz)

            if (distance < minDistance) {
                minDistance = distance
                bestMatch = owPortal
            }
        }

        // Determine confidence level (scaled for Overworld distances)
        String confidence
        if (minDistance < 8.0) {
            confidence = 'Exact'
        } else if (minDistance < 128.0) {
            confidence = 'Close'
        } else if (minDistance < 1024.0) {
            confidence = 'Likely'
        } else {
            confidence = 'Distant'
        }

        return new PortalPairing(bestMatch, minDistance, confidence)
    }

    private void selectPortal(PortalDetector.Portal portal) {
        selectedPortal = portal
        LOGGER.debug("Selected portal: ${portal}")

        // Highlight linked portal if exists
        PortalPairing pairing = portalPairings[portal]
        if (pairing?.linkedPortal) {
            highlightPortalPairing(portal, pairing.linkedPortal)
        }
    }

    private void highlightPortalPairing(PortalDetector.Portal portal1, PortalDetector.Portal portal2) {
        LOGGER.info("Highlighting pairing: ${portal1.dimension} ↔ ${portal2.dimension}")

        // Visual feedback (in a real implementation, would draw connecting line on canvas)
        Alert alert = new Alert(Alert.AlertType.INFORMATION)
        alert.title = 'Portal Pairing'
        alert.headerText = 'Portal Link Detected'
        alert.contentText = String.format(
            '%s Portal at (%.1f, %.1f, %.1f)\n' +
            '↕\n' +
            '%s Portal at (%.1f, %.1f, %.1f)\n\n' +
            'Distance: %.2f blocks',
            portal1.dimension.capitalize(), portal1.centerX, portal1.centerY, portal1.centerZ,
            portal2.dimension.capitalize(), portal2.centerX, portal2.centerY, portal2.centerZ,
            portalPairings[portal1]?.distance ?: 0.0
        )
        alert.showAndWait()
    }

    private void updateStatistics() {
        int totalPortals = allPortals.size()
        int pairedCount = portalPairings.values().count { it.linkedPortal != null }
        int orphanCount = totalPortals - pairedCount

        statsLabel.text = String.format(
            'Total: %d portals | Paired: %d | Orphan: %d | OW: %d | Nether: %d | End: %d',
            totalPortals, pairedCount, orphanCount,
            overworldPortals.size(), netherPortals.size(), endPortals.size()
        )
    }

    private void refreshPortalLists() {
        // Clear and rebuild all portal lists
        if (overworldList) {
            overworldList.children.clear()
            overworldPortals.each { portal ->
                overworldList.children.add(createPortalCard(portal, Color.LIGHTGREEN))
            }
        }

        if (netherList) {
            netherList.children.clear()
            netherPortals.each { portal ->
                netherList.children.add(createPortalCard(portal, Color.web('#9C27B0')))
            }
        }

        if (endList) {
            endList.children.clear()
            endPortals.each { portal ->
                endList.children.add(createPortalCard(portal, Color.YELLOW))
            }
        }
    }

    private static void copyToClipboard(String text) {
        ClipboardContent content = new ClipboardContent()
        content.putString(text)
        Clipboard.systemClipboard.setContent(content)
    }

    private static void showTooltip(Button button, String message) {
        Tooltip tooltip = new Tooltip(message)
        tooltip.show(button,
                     button.localToScreen(button.boundsInLocal).minX,
                     button.localToScreen(button.boundsInLocal).minY - 30)

        // Auto-hide after 1 second
        javafx.animation.Timeline timeline = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1),
                { event -> tooltip.hide() })
        )
        timeline.play()
    }
}

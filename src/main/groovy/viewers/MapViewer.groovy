package viewers

import groovy.json.JsonSlurper
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Point2D
import javafx.geometry.Pos
import javafx.scene.Cursor
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.input.ScrollEvent
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.shape.Circle
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import javafx.scene.text.Text
import javafx.stage.FileChooser
import javafx.stage.Stage
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Interactive map viewer for Minecraft world data visualization.
 *
 * Features:
 * - Load and display map images from external tools (uNmINeD, Dynmap, etc.)
 * - Pan and zoom controls with mouse interactions
 * - Marker overlay system for signs, portals, items, and custom blocks
 * - Grid-based marker clustering at low zoom levels with spiderfying
 * - Coordinate display and jump-to-coordinate functionality
 * - Map metadata (JSON sidecar) for coordinate mapping
 *
 * Usage:
 * - Load a PNG/JPG map image via File → Load Map Image
 * - Optionally load a metadata JSON file with origin coordinates and scale
 * - Markers can be added programmatically or loaded from extraction results
 *
 * Map Metadata Format (mapname.json):
 * {
 *   "origin": {"x": -1000, "z": -1000},  // Top-left corner world coordinates
 *   "scale": 1,                           // Pixels per block (1 = 1:1, 2 = 1:2)
 *   "dimension": "overworld"              // overworld, nether, end
 * }
 *
 * Coordinate System:
 * - Image: Top-left (0,0), X right, Y down
 * - Minecraft: X east, Z south, Y up (Y not mapped to image)
 * - Transform: imageX = (mcX - originX) / scale
 *              imageZ = (mcZ - originZ) / scale
 */
class MapViewer extends BorderPane {

    private static final Logger LOGGER = LoggerFactory.getLogger(MapViewer)

    // UI Components
    private ScrollPane mapScrollPane
    private StackPane mapContainer
    private ImageView mapImage
    private Pane markerLayer
    private Label coordsLabel
    private Label zoomLabel
    private Button zoomInBtn
    private Button zoomOutBtn
    private Button zoomFitBtn
    private Button loadMapBtn
    private Button jumpToBtn
    private Button toggleMarkersBtn

    // Map state
    private double currentZoom = 1.0
    private static final double MIN_ZOOM = 0.1
    private static final double MAX_ZOOM = 10.0
    private static final double ZOOM_STEP = 0.1

    // Pan state
    private Point2D lastMousePosition = null
    private boolean isPanning = false

    // Map metadata
    private MapMetadata metadata = new MapMetadata()

    // Marker data
    private List<MapMarker> allMarkers = []
    private boolean markersVisible = true

    // Clustering
    private static final double CLUSTER_GRID_SIZE = 50  // Grid cell size in pixels for clustering
    private static final int MAX_SPIDERFY_CIRCLE = 9    // Max markers for circular spiderfy layout
    private Map<String, MarkerCluster> visibleClusters = [:]

    /**
     * Map metadata container
     */
    static class MapMetadata {
        int originX = 0           // World X coordinate of top-left corner
        int originZ = 0           // World Z coordinate of top-left corner
        double scale = 1.0        // Pixels per block (1 = 1:1 scale)
        String dimension = 'overworld'

        /**
         * Load metadata from JSON file
         */
        static MapMetadata fromJson(File jsonFile) {
            JsonSlurper slurper = new JsonSlurper()
            Map data = slurper.parse(jsonFile) as Map

            MapMetadata meta = new MapMetadata()
            if (data.origin) {
                meta.originX = data.origin.x as int
                meta.originZ = data.origin.z as int
            }
            if (data.scale) {
                meta.scale = data.scale as double
            }
            if (data.dimension) {
                meta.dimension = data.dimension as String
            }

            return meta
        }
    }

    /**
     * Marker data class
     */
    static class MapMarker {
        String type          // 'sign', 'portal', 'item', 'block'
        int worldX
        int worldZ
        int worldY
        String label
        Map<String, Object> data  // Type-specific data (sign text, portal details, etc.)
        String iconEmoji     // Unicode emoji for marker icon

        MapMarker(String type, int worldX, int worldZ, int worldY, String label, Map<String, Object> data) {
            this.type = type
            this.worldX = worldX
            this.worldZ = worldZ
            this.worldY = worldY
            this.label = label
            this.data = data ?: [:]
            this.iconEmoji = getDefaultIcon(type)
        }

        private static String getDefaultIcon(String type) {
            switch (type) {
                case 'sign': return '🪧'
                case 'portal': return '🟣'
                case 'item': return '⭐'
                case 'block': return '📍'
                default: return '•'
            }
        }
    }

    /**
     * Marker cluster container
     */
    static class MarkerCluster {
        List<MapMarker> markers
        double centerX
        double centerZ
        boolean spiderfied = false

        MarkerCluster(List<MapMarker> markers) {
            this.markers = markers
            // Calculate cluster center (average position)
            this.centerX = markers.collect { it.worldX }.sum() / markers.size()
            this.centerZ = markers.collect { it.worldZ }.sum() / markers.size()
        }
    }

    /**
     * Constructor - builds the map viewer UI
     */
    MapViewer() {
        buildUI()
        setupEventHandlers()
    }

    /**
     * Build the user interface
     */
    private void buildUI() {
        // Top toolbar
        HBox toolbar = new HBox(10)
        toolbar.padding = new Insets(10)
        toolbar.alignment = Pos.CENTER_LEFT
        toolbar.style = '-fx-background-color: derive(-fx-base, -5%);'

        loadMapBtn = new Button('Load Map Image...')
        loadMapBtn.onAction = { loadMapImage() }

        zoomInBtn = new Button('Zoom In (+)')
        zoomInBtn.onAction = { zoom(ZOOM_STEP) }

        zoomOutBtn = new Button('Zoom Out (-)')
        zoomOutBtn.onAction = { zoom(-ZOOM_STEP) }

        zoomFitBtn = new Button('Fit to Window')
        zoomFitBtn.onAction = { zoomToFit() }

        zoomLabel = new Label('Zoom: 100%')
        zoomLabel.minWidth = 100

        Separator sep1 = new Separator()
        sep1.orientation = javafx.geometry.Orientation.VERTICAL

        jumpToBtn = new Button('Jump to Coordinates...')
        jumpToBtn.onAction = { showJumpToDialog() }

        toggleMarkersBtn = new Button('Hide Markers')
        toggleMarkersBtn.onAction = { toggleMarkers() }

        Separator sep2 = new Separator()
        sep2.orientation = javafx.geometry.Orientation.VERTICAL

        coordsLabel = new Label('World: (?, ?)')
        coordsLabel.minWidth = 150

        toolbar.children.addAll(
            loadMapBtn, sep1,
            zoomInBtn, zoomOutBtn, zoomFitBtn, zoomLabel, sep2,
            jumpToBtn, toggleMarkersBtn, sep2,
            coordsLabel
        )

        // Map container with overlay layers
        mapContainer = new StackPane()
        mapContainer.style = '-fx-background-color: #2b2b2b;'

        // Base map image layer
        mapImage = new ImageView()
        mapImage.preserveRatio = true
        mapImage.smooth = true

        // Marker overlay layer
        markerLayer = new Pane()
        markerLayer.mouseTransparent = false  // Allow marker clicks

        mapContainer.children.addAll(mapImage, markerLayer)

        // ScrollPane for pan/zoom
        mapScrollPane = new ScrollPane(mapContainer)
        mapScrollPane.pannable = false  // We handle panning manually for better control
        mapScrollPane.hbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
        mapScrollPane.vbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
        mapScrollPane.fitToWidth = false
        mapScrollPane.fitToHeight = false

        // Layout
        setTop(toolbar)
        setCenter(mapScrollPane)

        // Initial state
        updateZoomLabel()
    }

    /**
     * Setup event handlers for pan/zoom interactions
     */
    private void setupEventHandlers() {
        // Mouse wheel zoom
        mapScrollPane.addEventFilter(ScrollEvent.SCROLL, this.&handleScrollZoom)

        // Mouse drag pan
        mapImage.onMousePressed = this.&handleMousePressed
        mapImage.onMouseDragged = this.&handleMouseDragged
        mapImage.onMouseReleased = this.&handleMouseReleased

        // Coordinate tracking
        mapImage.onMouseMoved = this.&handleMouseMoved

        // Cursor feedback
        mapImage.cursor = Cursor.CROSSHAIR
    }

    /**
     * Handle scroll wheel zoom
     */
    private void handleScrollZoom(ScrollEvent event) {
        if (event.deltaY == 0) {
            return
        }

        double zoomFactor = event.deltaY > 0 ? ZOOM_STEP : -ZOOM_STEP
        zoom(zoomFactor)

        event.consume()
    }

    /**
     * Zoom in/out by specified factor
     * Package-private for testing
     */
    void zoom(double factor) {
        double newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, currentZoom + factor))

        if (newZoom != currentZoom) {
            currentZoom = newZoom
            applyZoom()
            updateZoomLabel()
            updateMarkers()  // Recalculate marker positions and clustering
        }
    }

    /**
     * Zoom to fit entire map in viewport
     * Package-private for testing
     */
    void zoomToFit() {
        if (!mapImage.image) {
            return
        }

        double viewportWidth = mapScrollPane.viewportBounds.width
        double viewportHeight = mapScrollPane.viewportBounds.height
        double imageWidth = mapImage.image.width
        double imageHeight = mapImage.image.height

        double scaleX = viewportWidth / imageWidth
        double scaleY = viewportHeight / imageHeight
        double fitZoom = Math.min(scaleX, scaleY) * 0.95  // 95% to leave margin

        currentZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, fitZoom))
        applyZoom()
        updateZoomLabel()
        updateMarkers()

        // Center the map
        Platform.runLater {
            mapScrollPane.hvalue = 0.5
            mapScrollPane.vvalue = 0.5
        }
    }

    /**
     * Apply current zoom level to image
     */
    private void applyZoom() {
        if (mapImage.image) {
            double scaledWidth = mapImage.image.width * currentZoom
            double scaledHeight = mapImage.image.height * currentZoom

            mapImage.fitWidth = scaledWidth
            mapImage.fitHeight = scaledHeight

            // Update container size to match
            mapContainer.prefWidth = scaledWidth
            mapContainer.prefHeight = scaledHeight
        }
    }

    /**
     * Update zoom label
     */
    private void updateZoomLabel() {
        int zoomPercent = (int) (currentZoom * 100)
        zoomLabel.text = "Zoom: ${zoomPercent}%"
    }

    /**
     * Handle mouse press for panning
     */
    private void handleMousePressed(MouseEvent event) {
        if (event.button == MouseButton.PRIMARY) {
            isPanning = true
            lastMousePosition = new Point2D(event.sceneX, event.sceneY)
            mapImage.cursor = Cursor.CLOSED_HAND
        }
    }

    /**
     * Handle mouse drag for panning
     */
    private void handleMouseDragged(MouseEvent event) {
        if (isPanning && lastMousePosition) {
            double deltaX = event.sceneX - lastMousePosition.x
            double deltaY = event.sceneY - lastMousePosition.y

            // Adjust scroll position (invert for natural panning)
            double hValue = mapScrollPane.hvalue
            double vValue = mapScrollPane.vvalue

            double hMax = mapScrollPane.hmax
            double vMax = mapScrollPane.vmax

            // Calculate new scroll position
            double newH = hValue - (deltaX / mapScrollPane.viewportBounds.width) * (hMax - mapScrollPane.hmin)
            double newV = vValue - (deltaY / mapScrollPane.viewportBounds.height) * (vMax - mapScrollPane.vmin)

            mapScrollPane.hvalue = Math.max(mapScrollPane.hmin, Math.min(hMax, newH))
            mapScrollPane.vvalue = Math.max(mapScrollPane.vmin, Math.min(vMax, newV))

            lastMousePosition = new Point2D(event.sceneX, event.sceneY)
        }
    }

    /**
     * Handle mouse release
     */
    private void handleMouseReleased(MouseEvent event) {
        isPanning = false
        lastMousePosition = null
        mapImage.cursor = Cursor.CROSSHAIR
    }

    /**
     * Handle mouse move for coordinate display
     */
    private void handleMouseMoved(MouseEvent event) {
        if (mapImage.image) {
            // Convert mouse position to image coordinates
            double imageX = event.x / currentZoom
            double imageZ = event.y / currentZoom

            // Convert to world coordinates
            Point2D worldCoords = imageToWorld(imageX, imageZ)

            coordsLabel.text = "World: (${(int) worldCoords.x}, ${(int) worldCoords.y})"
        }
    }

    /**
     * Load a map image from file
     */
    private void loadMapImage() {
        FileChooser chooser = new FileChooser()
        chooser.title = 'Select Map Image'
        chooser.extensionFilters.addAll(
            new FileChooser.ExtensionFilter('Image Files', '*.png', '*.jpg', '*.jpeg', '*.gif'),
            new FileChooser.ExtensionFilter('All Files', '*.*')
        )

        File imageFile = chooser.showOpenDialog(scene.window)
        if (imageFile && imageFile.exists()) {
            try {
                Image image = new Image(imageFile.toURI().toString())
                mapImage.image = image

                LOGGER.info("Loaded map image: ${imageFile.name} (${image.width}x${image.height})")

                // Try to load metadata JSON sidecar
                String baseName = imageFile.name.replaceFirst(/\.[^.]+$/, '')
                File metadataFile = new File(imageFile.parentFile, "${baseName}.json")

                if (metadataFile.exists()) {
                    metadata = MapMetadata.fromJson(metadataFile)
                    LOGGER.info("Loaded map metadata: origin=(${metadata.originX}, ${metadata.originZ}), " +
                               "scale=${metadata.scale}, dimension=${metadata.dimension}")
                } else {
                    LOGGER.info('No metadata JSON found, using default (origin=0,0, scale=1:1)')
                    metadata = new MapMetadata()  // Reset to defaults
                }

                // Fit to window on load
                Platform.runLater { zoomToFit() }

            } catch (Exception e) {
                LOGGER.error("Failed to load map image: ${e.message}", e)
                showAlert('Error', "Failed to load map image:\n${e.message}", Alert.AlertType.ERROR)
            }
        }
    }

    /**
     * Show jump-to-coordinate dialog
     */
    private void showJumpToDialog() {
        if (!mapImage.image) {
            showAlert('No Map Loaded', 'Please load a map image first.', Alert.AlertType.WARNING)
            return
        }

        Dialog<Point2D> dialog = new Dialog<>()
        dialog.title = 'Jump to Coordinates'
        dialog.headerText = 'Enter Minecraft world coordinates (X, Z)'

        // Set the button types
        ButtonType jumpButtonType = new ButtonType('Jump', ButtonBar.ButtonData.OK_DONE)
        dialog.dialogPane.buttonTypes.addAll(jumpButtonType, ButtonType.CANCEL)

        // Create the coordinate input fields
        GridPane grid = new GridPane()
        grid.hgap = 10
        grid.vgap = 10
        grid.padding = new Insets(20, 150, 10, 10)

        TextField xField = new TextField()
        xField.promptText = 'X coordinate'
        TextField zField = new TextField()
        zField.promptText = 'Z coordinate'

        grid.add(new Label('X:'), 0, 0)
        grid.add(xField, 1, 0)
        grid.add(new Label('Z:'), 0, 1)
        grid.add(zField, 1, 1)

        dialog.dialogPane.content = grid

        // Request focus on X field
        Platform.runLater { xField.requestFocus() }

        // Convert result to Point2D
        dialog.resultConverter = { dialogButton ->
            if (dialogButton == jumpButtonType) {
                try {
                    double x = Double.parseDouble(xField.text)
                    double z = Double.parseDouble(zField.text)
                    return new Point2D(x, z)
                } catch (NumberFormatException e) {
                    return null
                }
            }
            return null
        }

        dialog.showAndWait().ifPresent { coords ->
            if (coords) {
                jumpToWorldCoordinates((int) coords.x, (int) coords.y)
            } else {
                showAlert('Invalid Input', 'Please enter valid numeric coordinates.', Alert.AlertType.ERROR)
            }
        }
    }

    /**
     * Jump map view to specific world coordinates
     * Package-private for testing
     */
    void jumpToWorldCoordinates(int worldX, int worldZ) {
        Point2D imageCoords = worldToImage(worldX, worldZ)

        // Convert to zoomed coordinates
        double scaledX = imageCoords.x * currentZoom
        double scaledY = imageCoords.y * currentZoom

        // Calculate scroll position to center the coordinates
        double viewportWidth = mapScrollPane.viewportBounds.width
        double viewportHeight = mapScrollPane.viewportBounds.height

        double hValue = (scaledX - viewportWidth / 2) / (mapImage.fitWidth - viewportWidth)
        double vValue = (scaledY - viewportHeight / 2) / (mapImage.fitHeight - viewportHeight)

        mapScrollPane.hvalue = Math.max(0, Math.min(1, hValue))
        mapScrollPane.vvalue = Math.max(0, Math.min(1, vValue))

        LOGGER.info("Jumped to world coordinates (${worldX}, ${worldZ})")

        // Flash marker at target location (temporary visual feedback)
        flashTargetMarker(imageCoords.x, imageCoords.y)
    }

    /**
     * Flash a temporary marker at target location
     */
    private void flashTargetMarker(double imageX, double imageZ) {
        Circle flash = new Circle(imageX * currentZoom, imageZ * currentZoom, 20)
        flash.fill = Color.TRANSPARENT
        flash.stroke = Color.RED
        flash.strokeWidth = 3

        markerLayer.children.add(flash)

        // Fade out and remove after 2 seconds
        javafx.animation.FadeTransition fade = new javafx.animation.FadeTransition(
            javafx.util.Duration.seconds(2), flash
        )
        fade.fromValue = 1.0
        fade.toValue = 0.0
        fade.onFinished = { markerLayer.children.remove(flash) }
        fade.play()
    }

    /**
     * Toggle marker visibility
     */
    private void toggleMarkers() {
        markersVisible = !markersVisible
        toggleMarkersBtn.text = markersVisible ? 'Hide Markers' : 'Show Markers'
        updateMarkers()
    }

    /**
     * Convert world coordinates to image pixel coordinates
     * Package-private for testing
     */
    Point2D worldToImage(int worldX, int worldZ) {
        double imageX = (worldX - metadata.originX) * metadata.scale
        double imageZ = (worldZ - metadata.originZ) * metadata.scale
        return new Point2D(imageX, imageZ)
    }

    /**
     * Convert image pixel coordinates to world coordinates
     * Package-private for testing
     */
    Point2D imageToWorld(double imageX, double imageZ) {
        double worldX = (imageX / metadata.scale) + metadata.originX
        double worldZ = (imageZ / metadata.scale) + metadata.originZ
        return new Point2D(worldX, worldZ)
    }

    /**
     * Add a marker to the map
     */
    void addMarker(MapMarker marker) {
        allMarkers.add(marker)
        updateMarkers()
    }

    /**
     * Add multiple markers at once
     */
    void addMarkers(List<MapMarker> markers) {
        allMarkers.addAll(markers)
        updateMarkers()
    }

    /**
     * Clear all markers
     */
    void clearMarkers() {
        allMarkers.clear()
        updateMarkers()
    }

    /**
     * Update marker rendering with clustering
     */
    private void updateMarkers() {
        markerLayer.children.clear()
        visibleClusters.clear()

        if (!markersVisible || allMarkers.empty || !mapImage.image) {
            return
        }

        // Determine if clustering should be applied (zoom level threshold)
        boolean shouldCluster = currentZoom < 0.5

        if (shouldCluster) {
            renderClustered()
        } else {
            renderIndividual()
        }
    }

    /**
     * Render individual markers (no clustering)
     */
    private void renderIndividual() {
        allMarkers.each { marker ->
            Point2D imagePos = worldToImage(marker.worldX, marker.worldZ)
            double scaledX = imagePos.x * currentZoom
            double scaledY = imagePos.y * currentZoom

            StackPane markerNode = createMarkerNode(marker, false)
            markerNode.layoutX = scaledX - 12  // Center marker (icon is ~24px)
            markerNode.layoutY = scaledY - 12

            markerLayer.children.add(markerNode)
        }
    }

    /**
     * Render markers with grid-based clustering
     */
    private void renderClustered() {
        // Group markers into grid cells
        Map<String, List<MapMarker>> grid = [:]

        allMarkers.each { marker ->
            Point2D imagePos = worldToImage(marker.worldX, marker.worldZ)
            double scaledX = imagePos.x * currentZoom
            double scaledY = imagePos.y * currentZoom

            int gridX = (int) (scaledX / CLUSTER_GRID_SIZE)
            int gridY = (int) (scaledY / CLUSTER_GRID_SIZE)
            String gridKey = "${gridX},${gridY}"

            grid.computeIfAbsent(gridKey, { [] }).add(marker)
        }

        // Render clusters
        grid.each { gridKey, List<MapMarker> markers ->
            if (markers.size() == 1) {
                // Single marker, render normally
                MapMarker marker = markers[0]
                Point2D imagePos = worldToImage(marker.worldX, marker.worldZ)
                double scaledX = imagePos.x * currentZoom
                double scaledY = imagePos.y * currentZoom

                StackPane markerNode = createMarkerNode(marker, false)
                markerNode.layoutX = scaledX - 12
                markerNode.layoutY = scaledY - 12

                markerLayer.children.add(markerNode)
            } else {
                // Multiple markers, create cluster
                MarkerCluster cluster = new MarkerCluster(markers)
                visibleClusters[gridKey] = cluster

                Point2D clusterImagePos = worldToImage((int) cluster.centerX, (int) cluster.centerZ)
                double scaledX = clusterImagePos.x * currentZoom
                double scaledY = clusterImagePos.y * currentZoom

                StackPane clusterNode = createClusterNode(cluster, gridKey)
                clusterNode.layoutX = scaledX - 15  // Center cluster badge
                clusterNode.layoutY = scaledY - 15

                markerLayer.children.add(clusterNode)
            }
        }
    }

    /**
     * Create a marker node (icon + click handler)
     */
    private StackPane createMarkerNode(MapMarker marker, boolean inSpiderfy) {
        StackPane stack = new StackPane()

        // Background circle for visibility
        Circle bg = new Circle(12)
        bg.fill = getMarkerColor(marker.type)
        bg.stroke = Color.WHITE
        bg.strokeWidth = 2

        // Emoji icon
        Text icon = new Text(marker.iconEmoji)
        icon.font = Font.font('Segoe UI Emoji', 16)

        stack.children.addAll(bg, icon)
        stack.cursor = Cursor.HAND

        // Click handler
        stack.onMouseClicked = { event ->
            if (event.button == MouseButton.PRIMARY) {
                showMarkerPopup(marker)
            }
        }

        // Hover effect
        stack.onMouseEntered = { bg.strokeWidth = 3 }
        stack.onMouseExited = { bg.strokeWidth = 2 }

        return stack
    }

    /**
     * Create a cluster node (count badge + click handler for spiderfying)
     */
    private StackPane createClusterNode(MarkerCluster cluster, String gridKey) {
        StackPane stack = new StackPane()

        // Background circle
        Circle bg = new Circle(15)
        bg.fill = Color.PURPLE
        bg.stroke = Color.WHITE
        bg.strokeWidth = 2

        // Count label
        Text count = new Text("${cluster.markers.size()}")
        count.font = Font.font('Arial', FontWeight.BOLD, 14)
        count.fill = Color.WHITE

        stack.children.addAll(bg, count)
        stack.cursor = Cursor.HAND

        // Click handler: spiderfy or un-spiderfy
        stack.onMouseClicked = { event ->
            if (event.button == MouseButton.PRIMARY) {
                if (cluster.spiderfied) {
                    // Un-spiderfy: redraw all markers
                    updateMarkers()
                } else {
                    // Spiderfy: spread markers in circle/spiral
                    spiderfyCluster(cluster, gridKey)
                }
            }
        }

        // Hover effect
        stack.onMouseEntered = { bg.strokeWidth = 3 }
        stack.onMouseExited = { bg.strokeWidth = 2 }

        return stack
    }

    /**
     * Spiderfy a cluster (spread markers in circular or spiral layout)
     */
    private void spiderfyCluster(MarkerCluster cluster, String gridKey) {
        cluster.spiderfied = true

        Point2D clusterImagePos = worldToImage((int) cluster.centerX, (int) cluster.centerZ)
        double centerX = clusterImagePos.x * currentZoom
        double centerY = clusterImagePos.y * currentZoom

        int markerCount = cluster.markers.size()

        if (markerCount <= MAX_SPIDERFY_CIRCLE) {
            // Circular layout
            double radius = 40 + (markerCount * 5)  // Grow radius with count
            double angleStep = 2 * Math.PI / markerCount

            cluster.markers.eachWithIndex { marker, index ->
                double angle = angleStep * index
                double x = centerX + radius * Math.cos(angle)
                double y = centerY + radius * Math.sin(angle)

                StackPane markerNode = createMarkerNode(marker, true)
                markerNode.layoutX = x - 12
                markerNode.layoutY = y - 12

                // Draw connecting line
                javafx.scene.shape.Line line = new javafx.scene.shape.Line(centerX, centerY, x, y)
                line.stroke = Color.gray(0.5, 0.5)
                line.strokeWidth = 1

                markerLayer.children.addAll(line, markerNode)
            }
        } else {
            // Spiral layout (for 10+ markers)
            double spiralSpacing = 10
            double angleIncrement = 0.5

            cluster.markers.eachWithIndex { marker, index ->
                double angle = angleIncrement * index
                double radius = 20 + spiralSpacing * (index / 3)  // Spiral outward
                double x = centerX + radius * Math.cos(angle)
                double y = centerY + radius * Math.sin(angle)

                StackPane markerNode = createMarkerNode(marker, true)
                markerNode.layoutX = x - 12
                markerNode.layoutY = y - 12

                markerLayer.children.add(markerNode)
            }
        }

        // Add close button to un-spiderfy
        Button closeBtn = new Button('✖')
        closeBtn.style = '-fx-font-size: 10px; -fx-padding: 2 5 2 5;'
        closeBtn.layoutX = centerX + 20
        closeBtn.layoutY = centerY - 30
        closeBtn.onAction = { updateMarkers() }  // Redraw to un-spiderfy

        markerLayer.children.add(closeBtn)
    }

    /**
     * Get marker color based on type
     */
    private Color getMarkerColor(String type) {
        switch (type) {
            case 'sign': return Color.BROWN
            case 'portal': return Color.PURPLE
            case 'item': return Color.GOLD
            case 'block': return Color.LIGHTBLUE
            default: return Color.GRAY
        }
    }

    /**
     * Show marker popup with details
     */
    private void showMarkerPopup(MapMarker marker) {
        Alert popup = new Alert(Alert.AlertType.INFORMATION)
        popup.title = "${marker.type.capitalize()} Details"
        popup.headerText = marker.label

        StringBuilder content = new StringBuilder()
        content.append("Coordinates: (${marker.worldX}, ${marker.worldY}, ${marker.worldZ})\n")
        content.append("Type: ${marker.type}\n\n")

        // Type-specific content
        switch (marker.type) {
            case 'sign':
                content.append('Sign Text:\n')
                if (marker.data.lines) {
                    marker.data.lines.each { line ->
                        content.append("  ${line}\n")
                    }
                }
                break

            case 'portal':
                content.append("Dimension: ${marker.data.dimension ?: 'Unknown'}\n")
                content.append("Size: ${marker.data.width}×${marker.data.height}\n")
                content.append("Axis: ${marker.data.axis ?: 'Unknown'}\n")
                content.append("\nTeleport Command:\n")
                content.append("/tp @s ${marker.worldX} ${marker.worldY} ${marker.worldZ}")
                break

            case 'item':
                content.append("Item: ${marker.data.itemId ?: 'Unknown'}\n")
                if (marker.data.customName) {
                    content.append("Name: ${marker.data.customName}\n")
                }
                if (marker.data.enchantments) {
                    content.append("Enchantments: ${marker.data.enchantments}\n")
                }
                if (marker.data.container) {
                    content.append("Found in: ${marker.data.container}\n")
                }
                break

            case 'block':
                content.append("Block: ${marker.data.blockType ?: 'Unknown'}\n")
                if (marker.data.properties) {
                    content.append("Properties: ${marker.data.properties}\n")
                }
                break
        }

        popup.contentText = content.toString()
        popup.showAndWait()
    }

    /**
     * Show alert dialog
     */
    private static void showAlert(String title, String message, Alert.AlertType type) {
        Platform.runLater {
            Alert alert = new Alert(type)
            alert.title = title
            alert.headerText = null
            alert.contentText = message
            alert.showAndWait()
        }
    }

    /**
     * Load markers from extraction results (example integration)
     */
    void loadMarkersFromExtractionResults(Map<String, Object> results) {
        // Signs
        if (results.signs) {
            results.signs.each { signData ->
                MapMarker marker = new MapMarker(
                    'sign',
                    signData.x as int,
                    signData.z as int,
                    signData.y as int,
                    'Sign',
                    [lines: signData.lines]
                )
                addMarker(marker)
            }
        }

        // Portals
        if (results.portals) {
            results.portals.each { portalData ->
                MapMarker marker = new MapMarker(
                    'portal',
                    portalData.centerX as int,
                    portalData.centerZ as int,
                    portalData.centerY as int,
                    "Portal ${portalData.width}×${portalData.height}",
                    [
                        dimension: portalData.dimension,
                        width: portalData.width,
                        height: portalData.height,
                        axis: portalData.axis
                    ]
                )
                addMarker(marker)
            }
        }

        // Custom named items
        if (results.items) {
            results.items.each { itemData ->
                MapMarker marker = new MapMarker(
                    'item',
                    itemData.x as int,
                    itemData.z as int,
                    itemData.y as int,
                    itemData.customName ?: itemData.itemId,
                    [
                        itemId: itemData.itemId,
                        customName: itemData.customName,
                        enchantments: itemData.enchantments,
                        container: itemData.container
                    ]
                )
                addMarker(marker)
            }
        }

        LOGGER.info("Loaded ${allMarkers.size()} markers from extraction results")
    }

    /**
     * Example usage in standalone window
     */
    static void main(String[] args) {
        javafx.application.Application.launch(MapViewerApp, args)
    }

    /**
     * Standalone JavaFX Application for testing
     */
    static class MapViewerApp extends javafx.application.Application {
        @Override
        void start(Stage stage) {
            MapViewer viewer = new MapViewer()

            // Example: Add test markers
            viewer.addMarker(new MapMarker('sign', 100, 200, 64, 'Test Sign', [lines: ['Line 1', 'Line 2']]))
            viewer.addMarker(new MapMarker('portal', 500, 600, 70, 'Portal 4×5', [width: 4, height: 5, axis: 'x']))
            viewer.addMarker(new MapMarker('item', 300, 400, 80, 'Diamond Sword', [itemId: 'diamond_sword']))

            Scene scene = new Scene(viewer, 1200, 800)
            stage.scene = scene
            stage.title = 'Minecraft Map Viewer'
            stage.show()
        }
    }
}

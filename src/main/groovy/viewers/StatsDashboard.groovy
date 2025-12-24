package viewers

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.chart.BarChart
import javafx.scene.chart.CategoryAxis
import javafx.scene.chart.NumberAxis
import javafx.scene.chart.PieChart
import javafx.scene.chart.XYChart
import javafx.scene.control.Label
import javafx.scene.control.Tooltip
import javafx.scene.layout.BorderPane
import javafx.scene.layout.FlowPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.TilePane
import javafx.scene.layout.VBox
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Analytics dashboard with KPI cards and interactive charts for Minecraft extraction data.
 *
 * Features:
 * - KPI cards for books, signs, items, portals
 * - Interactive PieCharts (books by author, items by category)
 * - BarCharts (items by dimension, enchantment frequency, container distribution)
 * - Click-to-filter functionality
 * - Responsive layout with AtlantaFX theme support
 */
class StatsDashboard extends BorderPane {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatsDashboard)

    // Data sources (will be injected)
    private Map<String, List<Map>> booksByAuthor = [:]
    private Map<String, Map> signsByHash = [:]
    private ItemDatabase itemDatabase
    private BlockDatabase blockDatabase
    private List<Map> portalResults = []

    // Charts
    private PieChart booksByAuthorChart
    private PieChart itemsByCategoryChart
    private BarChart<String, Number> itemsByDimensionChart
    private BarChart<String, Number> enchantmentFreqChart
    private BarChart<String, Number> containerDistChart

    // KPI cards
    private KpiCard totalBooksCard
    private KpiCard totalSignsCard
    private KpiCard totalItemsCard
    private KpiCard totalPortalsCard

    // Filter callback (called when user clicks chart to filter main viewer)
    private Closure filterCallback

    /**
     * Create statistics dashboard.
     */
    StatsDashboard() {
        buildUI()
    }

    /**
     * Set filter callback for chart interactions.
     *
     * @param callback Closure receiving (filterType, filterValue) where:
     *   filterType: 'author', 'item_category', 'dimension', 'enchantment', 'container'
     *   filterValue: String value to filter by
     */
    void setFilterCallback(Closure callback) {
        this.filterCallback = callback
    }

    /**
     * Update dashboard with extraction data.
     *
     * @param booksByAuthor Map of author -> List of book metadata
     * @param signsByHash Map of sign hash -> sign metadata
     * @param itemDatabase ItemDatabase instance (may be null)
     * @param blockDatabase BlockDatabase instance (may be null)
     * @param portalResults List of portal metadata (may be null)
     */
    void updateData(Map<String, List<Map>> booksByAuthor, Map<String, Map> signsByHash,
                    ItemDatabase itemDatabase = null, BlockDatabase blockDatabase = null,
                    List<Map> portalResults = null) {
        this.booksByAuthor = booksByAuthor ?: [:]
        this.signsByHash = signsByHash ?: [:]
        this.itemDatabase = itemDatabase
        this.blockDatabase = blockDatabase
        this.portalResults = portalResults ?: []

        // Update KPIs
        updateKpiCards()

        // Update charts
        updateBooksByAuthorChart()
        updateItemsByCategoryChart()
        updateItemsByDimensionChart()
        updateEnchantmentFrequencyChart()
        updateContainerDistributionChart()

        LOGGER.debug('Dashboard data updated')
    }

    /**
     * Build the UI layout.
     */
    private void buildUI() {
        padding = new Insets(15)

        // KPI cards row at top
        FlowPane kpiRow = new FlowPane(15, 15)
        kpiRow.alignment = Pos.CENTER
        kpiRow.padding = new Insets(0, 0, 20, 0)

        totalBooksCard = new KpiCard('📚 BOOKS', '0', 'No authors')
        totalSignsCard = new KpiCard('🪧 SIGNS', '0', 'No unique signs')
        totalItemsCard = new KpiCard('⚔️ ITEMS', '0', 'No enchanted items')
        totalPortalsCard = new KpiCard('🌀 PORTALS', '0', 'Not paired')

        kpiRow.children.addAll(totalBooksCard, totalSignsCard, totalItemsCard, totalPortalsCard)

        // Charts grid (responsive TilePane)
        TilePane chartsGrid = new TilePane(15, 15)
        chartsGrid.prefColumns = 2
        chartsGrid.alignment = Pos.CENTER
        chartsGrid.padding = new Insets(10, 0, 0, 0)

        // Create charts
        booksByAuthorChart = createBooksByAuthorChart()
        itemsByCategoryChart = createItemsByCategoryChart()
        itemsByDimensionChart = createItemsByDimensionChart()
        enchantmentFreqChart = createEnchantmentFrequencyChart()
        containerDistChart = createContainerDistributionChart()

        // Wrap each chart in a container with title
        VBox booksChartBox = createChartBox('Books by Author', booksByAuthorChart)
        VBox itemsCatChartBox = createChartBox('Items by Category', itemsByCategoryChart)
        VBox itemsDimChartBox = createChartBox('Items by Dimension', itemsByDimensionChart)
        VBox enchantChartBox = createChartBox('Top Enchantments', enchantmentFreqChart)
        VBox containerChartBox = createChartBox('Container Distribution', containerDistChart)

        // Set preferred sizes (responsive)
        [booksChartBox, itemsCatChartBox, itemsDimChartBox, enchantChartBox, containerChartBox].each { box ->
            box.prefWidth = 400
            box.prefHeight = 350
        }

        chartsGrid.children.addAll(booksChartBox, itemsCatChartBox, itemsDimChartBox, enchantChartBox, containerChartBox)

        // Main layout
        top = kpiRow
        center = chartsGrid

        LOGGER.debug('StatsDashboard UI built')
    }

    /**
     * Create a chart container with title.
     */
    private VBox createChartBox(String title, javafx.scene.Node chart) {
        VBox box = new VBox(10)
        box.padding = new Insets(10)
        box.style = '-fx-background-color: derive(-fx-base, 5%); -fx-background-radius: 8; ' +
                    '-fx-border-color: derive(-fx-base, -10%); -fx-border-radius: 8;'

        Label titleLabel = new Label(title)
        titleLabel.font = Font.font('System', FontWeight.BOLD, 14)
        titleLabel.style = '-fx-text-fill: -fx-text-base-color;'

        VBox.setVgrow(chart, Priority.ALWAYS)
        box.children.addAll(titleLabel, chart)

        return box
    }

    /**
     * Update KPI cards with current data.
     */
    private void updateKpiCards() {
        // Books card
        int totalBooks = booksByAuthor.values().sum { it.size() } ?: 0
        int uniqueAuthors = booksByAuthor.size()
        totalBooksCard.setValue(totalBooks.toString())
        totalBooksCard.setSubtitle(uniqueAuthors == 0 ? 'No authors' :
            uniqueAuthors == 1 ? '1 author' : "${uniqueAuthors} authors")

        // Signs card
        int totalSigns = signsByHash.size()
        totalSignsCard.setValue(totalSigns.toString())
        totalSignsCard.setSubtitle(totalSigns == 0 ? 'No unique signs' :
            totalSigns == 1 ? '1 unique sign' : "${totalSigns} unique signs")

        // Items card
        if (itemDatabase) {
            int totalItems = itemDatabase.totalItemsIndexed
            List<Map> enchantedItems = itemDatabase.queryEnchantedItems()
            int enchantedCount = enchantedItems.size()

            totalItemsCard.setValue(totalItems.toString())
            totalItemsCard.setSubtitle(enchantedCount == 0 ? 'No enchanted items' :
                "${enchantedCount} enchanted")
        } else {
            totalItemsCard.setValue('N/A')
            totalItemsCard.setSubtitle('No item database')
        }

        // Portals card
        int totalPortals = portalResults.size()
        int pairedPortals = portalResults.count { it.paired_portal } ?: 0
        double pairedPercent = totalPortals > 0 ? (pairedPortals / totalPortals * 100) : 0

        totalPortalsCard.setValue(totalPortals.toString())
        totalPortalsCard.setSubtitle(totalPortals == 0 ? 'Not paired' :
            String.format('%.0f%% paired', pairedPercent))
    }

    /**
     * Create books by author pie chart.
     */
    private PieChart createBooksByAuthorChart() {
        PieChart chart = new PieChart()
        chart.legendVisible = false
        chart.animated = false

        // Click handler
        chart.setOnMouseClicked { event ->
            if (event.target instanceof PieChart.Data) {
                PieChart.Data data = (PieChart.Data) event.target
                String author = data.name
                if (filterCallback) {
                    filterCallback('author', author)
                }
                LOGGER.debug("Filter by author: ${author}")
            }
        }

        return chart
    }

    /**
     * Update books by author chart.
     */
    private void updateBooksByAuthorChart() {
        booksByAuthorChart.data.clear()

        if (booksByAuthor.isEmpty()) {
            PieChart.Data noData = new PieChart.Data('No Data', 1)
            booksByAuthorChart.data.add(noData)
            return
        }

        // Get top 10 authors by book count
        List<Map.Entry<String, List<Map>>> sortedAuthors = booksByAuthor.entrySet().toList()
            .sort { a, b -> b.value.size() <=> a.value.size() }

        List<Map.Entry<String, List<Map>>> top10 = sortedAuthors.take(10)
        List<Map.Entry<String, List<Map>>> others = sortedAuthors.drop(10)

        // Add top 10 authors
        top10.each { entry ->
            String author = entry.key
            int count = entry.value.size()
            PieChart.Data slice = new PieChart.Data(author, count)

            // Add hover tooltip
            Tooltip tooltip = new Tooltip("${author}: ${count} book${count == 1 ? '' : 's'}")
            Tooltip.install(slice.node, tooltip)

            booksByAuthorChart.data.add(slice)
        }

        // Add "Others" if needed
        if (others) {
            int othersCount = others.sum { it.value.size() }
            PieChart.Data othersSlice = new PieChart.Data("Others (${others.size()})", othersCount)
            Tooltip othersTooltip = new Tooltip("Others: ${othersCount} books from ${others.size()} authors")
            Tooltip.install(othersSlice.node, othersTooltip)
            booksByAuthorChart.data.add(othersSlice)
        }
    }

    /**
     * Create items by category pie chart.
     */
    private PieChart createItemsByCategoryChart() {
        PieChart chart = new PieChart()
        chart.legendVisible = false
        chart.animated = false

        // Click handler
        chart.setOnMouseClicked { event ->
            if (event.target instanceof PieChart.Data) {
                PieChart.Data data = (PieChart.Data) event.target
                String category = data.name
                if (filterCallback) {
                    filterCallback('item_category', category)
                }
                LOGGER.debug("Filter by item category: ${category}")
            }
        }

        return chart
    }

    /**
     * Update items by category chart.
     */
    private void updateItemsByCategoryChart() {
        itemsByCategoryChart.data.clear()

        if (!itemDatabase) {
            PieChart.Data noData = new PieChart.Data('No Data', 1)
            itemsByCategoryChart.data.add(noData)
            return
        }

        // Get item summary and categorize
        List<Map> summary = itemDatabase.summary
        Map<String, Integer> categories = [
            'Tools': 0,
            'Weapons': 0,
            'Armor': 0,
            'Food': 0,
            'Blocks': 0,
            'Other': 0
        ]

        summary.each { item ->
            String itemId = item.item_id
            int count = item.unique_locations as int

            String category = categorizeItem(itemId)
            categories[category] += count
        }

        // Add non-zero categories
        categories.findAll { it.value > 0 }.each { category, count ->
            PieChart.Data slice = new PieChart.Data(category, count)
            Tooltip tooltip = new Tooltip("${category}: ${count} item${count == 1 ? '' : 's'}")
            Tooltip.install(slice.node, tooltip)
            itemsByCategoryChart.data.add(slice)
        }

        if (itemsByCategoryChart.data.isEmpty()) {
            PieChart.Data noData = new PieChart.Data('No Data', 1)
            itemsByCategoryChart.data.add(noData)
        }
    }

    /**
     * Categorize item by ID.
     */
    private String categorizeItem(String itemId) {
        String name = itemId.toLowerCase()

        if (name.contains('sword') || name.contains('bow') || name.contains('trident') ||
            name.contains('crossbow') || name.contains('arrow')) {
            return 'Weapons'
        }
        if (name.contains('pickaxe') || name.contains('axe') || name.contains('shovel') ||
            name.contains('hoe') || name.contains('shears') || name.contains('fishing_rod')) {
            return 'Tools'
        }
        if (name.contains('helmet') || name.contains('chestplate') || name.contains('leggings') ||
            name.contains('boots') || name.contains('elytra')) {
            return 'Armor'
        }
        if (name.contains('bread') || name.contains('apple') || name.contains('meat') ||
            name.contains('fish') || name.contains('stew') || name.contains('carrot') ||
            name.contains('potato') || name.contains('beetroot')) {
            return 'Food'
        }
        if (name.contains('_ore') || name.contains('stone') || name.contains('dirt') ||
            name.contains('log') || name.contains('plank') || name.contains('glass') ||
            name.contains('brick') || name.contains('wool')) {
            return 'Blocks'
        }

        return 'Other'
    }

    /**
     * Create items by dimension bar chart.
     */
    private BarChart<String, Number> createItemsByDimensionChart() {
        CategoryAxis xAxis = new CategoryAxis()
        xAxis.label = 'Dimension'

        NumberAxis yAxis = new NumberAxis()
        yAxis.label = 'Item Count'

        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis)
        chart.legendVisible = false
        chart.animated = false

        return chart
    }

    /**
     * Update items by dimension chart.
     */
    private void updateItemsByDimensionChart() {
        itemsByDimensionChart.data.clear()

        if (!itemDatabase) {
            return
        }

        Map<String, Integer> dimensionCounts = [
            'overworld': 0,
            'nether': 0,
            'end': 0
        ]

        // Query items by dimension
        ['overworld', 'nether', 'end'].each { dimension ->
            List<Map> items = itemDatabase.queryByItemType('%', dimension)
            dimensionCounts[dimension] = items.size()
        }

        XYChart.Series<String, Number> series = new XYChart.Series<>()
        dimensionCounts.each { dimension, count ->
            XYChart.Data<String, Number> data = new XYChart.Data<>(dimension.capitalize(), count)
            series.data.add(data)
        }

        itemsByDimensionChart.data.add(series)
    }

    /**
     * Create enchantment frequency bar chart.
     */
    private BarChart<String, Number> createEnchantmentFrequencyChart() {
        CategoryAxis xAxis = new CategoryAxis()
        xAxis.label = 'Enchantment'

        NumberAxis yAxis = new NumberAxis()
        yAxis.label = 'Count'

        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis)
        chart.legendVisible = false
        chart.animated = false

        return chart
    }

    /**
     * Update enchantment frequency chart.
     */
    private void updateEnchantmentFrequencyChart() {
        enchantmentFreqChart.data.clear()

        if (!itemDatabase) {
            return
        }

        // Get all enchanted items and count enchantments
        List<Map> enchantedItems = itemDatabase.queryEnchantedItems()
        Map<String, Integer> enchantmentCounts = [:]

        enchantedItems.each { item ->
            String enchantmentsJson = item.enchantments
            String storedEnchJson = item.stored_enchantments

            [enchantmentsJson, storedEnchJson].findAll { it }.each { json ->
                try {
                    def enchantments = new groovy.json.JsonSlurper().parseText(json)
                    if (enchantments instanceof Map) {
                        enchantments.keySet().each { enchantName ->
                            String name = enchantName.toString().replaceAll('minecraft:', '')
                            enchantmentCounts[name] = (enchantmentCounts[name] ?: 0) + 1
                        }
                    }
                } catch (Exception e) {
                    LOGGER.debug("Failed to parse enchantments JSON: ${e.message}")
                }
            }
        }

        // Get top 10 enchantments
        List<Map.Entry<String, Integer>> sortedEnchants = enchantmentCounts.entrySet().toList()
            .sort { a, b -> b.value <=> a.value }
            .take(10)

        XYChart.Series<String, Number> series = new XYChart.Series<>()
        sortedEnchants.each { entry ->
            XYChart.Data<String, Number> data = new XYChart.Data<>(entry.key, entry.value)
            series.data.add(data)
        }

        enchantmentFreqChart.data.add(series)
    }

    /**
     * Create container distribution bar chart.
     */
    private BarChart<String, Number> createContainerDistributionChart() {
        CategoryAxis xAxis = new CategoryAxis()
        xAxis.label = 'Container Type'

        NumberAxis yAxis = new NumberAxis()
        yAxis.label = 'Item Count'

        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis)
        chart.legendVisible = false
        chart.animated = false

        return chart
    }

    /**
     * Update container distribution chart.
     */
    private void updateContainerDistributionChart() {
        containerDistChart.data.clear()

        if (!itemDatabase) {
            return
        }

        // Get container type counts from database
        Map<String, Integer> containerCounts = [:]

        List<Map> allItems = itemDatabase.summary
        allItems.each { item ->
            String itemId = item.item_id
            // Query items to get container types
            List<Map> items = itemDatabase.queryByItemType(itemId)
            items.each { itemEntry ->
                String containerType = itemEntry.container_type ?: 'Unknown'
                containerCounts[containerType] = (containerCounts[containerType] ?: 0) + 1
            }
        }

        // Get top 10 container types
        List<Map.Entry<String, Integer>> sortedContainers = containerCounts.entrySet().toList()
            .sort { a, b -> b.value <=> a.value }
            .take(10)

        XYChart.Series<String, Number> series = new XYChart.Series<>()
        sortedContainers.each { entry ->
            String containerName = entry.key.replaceAll('minecraft:', '')
            XYChart.Data<String, Number> data = new XYChart.Data<>(containerName, entry.value)
            series.data.add(data)
        }

        containerDistChart.data.add(series)
    }

    /**
     * KPI Card component for displaying key metrics.
     */
    static class KpiCard extends VBox {

        private Label titleLabel
        private Label valueLabel
        private Label subtitleLabel

        KpiCard(String title, String value, String subtitle) {
            super(8)
            alignment = Pos.CENTER
            padding = new Insets(15)
            prefWidth = 160
            prefHeight = 110
            style = '-fx-background-color: derive(-fx-base, 8%); -fx-background-radius: 10; ' +
                    '-fx-border-color: derive(-fx-accent, 50%); -fx-border-width: 2; -fx-border-radius: 10;'

            titleLabel = new Label(title)
            titleLabel.font = Font.font('System', FontWeight.BOLD, 12)
            titleLabel.style = '-fx-text-fill: derive(-fx-accent, 30%);'

            valueLabel = new Label(value)
            valueLabel.font = Font.font('System', FontWeight.BOLD, 32)
            valueLabel.style = '-fx-text-fill: -fx-text-base-color;'

            subtitleLabel = new Label(subtitle)
            subtitleLabel.font = Font.font('System', 10)
            subtitleLabel.style = '-fx-text-fill: derive(-fx-text-base-color, 30%);'

            children.addAll(titleLabel, valueLabel, subtitleLabel)
        }

        void setValue(String value) {
            valueLabel.text = value
        }

        void setSubtitle(String subtitle) {
            subtitleLabel.text = subtitle
        }

    }

}

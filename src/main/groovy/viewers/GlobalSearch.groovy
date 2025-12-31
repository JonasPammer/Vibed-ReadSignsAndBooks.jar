package viewers

import javafx.animation.PauseTransition
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.KeyCombination
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import javafx.util.Duration
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Global Search component - searches across all data types (books, signs, items, custom names, portals).
 *
 * Features:
 * - Single search bar for unified search experience
 * - Results grouped by type with emoji icons
 * - Relevance ranking (exact match > starts with > contains > fuzzy)
 * - Keyboard shortcut: Ctrl+F from anywhere
 * - Click result to navigate to appropriate viewer and highlight item
 * - Real-time search as you type (debounced)
 * - Result limit to prevent UI freeze on broad queries
 */
class GlobalSearch extends VBox {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalSearch)

    // Constants
    private static final int MAX_RESULTS = 50
    private static final int MIN_QUERY_LENGTH = 2
    private static final long SEARCH_DEBOUNCE_MS = 300

    // UI Components
    private TextField searchField
    private ListView<SearchResult> resultsList
    private Label statusLabel
    private ProgressIndicator loadingIndicator

    // Data
    private OutputViewerModel model
    private List<SearchResult> allResults = []
    private String lastQuery = ''
    private String pendingQuery = ''
    private PauseTransition debounceTimer
    private volatile long currentSearchToken = 0

    // Callback for result selection
    private Closure<Void> onResultSelected

    /**
     * Create a new Global Search component.
     *
     * @param model The data model to search
     * @param onResultSelected Callback invoked when user selects a result
     */
    GlobalSearch(OutputViewerModel model, Closure<Void> onResultSelected) {
        this.model = model
        this.onResultSelected = onResultSelected

        setupUI()
        setupKeyboardShortcuts()
        setupDebounce()
    }

    private void setupDebounce() {
        debounceTimer = new PauseTransition(Duration.millis(SEARCH_DEBOUNCE_MS))
        debounceTimer.onFinished = { event ->
            startSearch(pendingQuery)
        }
    }

    private void setupUI() {
        padding = new Insets(10)
        spacing = 10
        style = '-fx-background-color: derive(-fx-base, 5%);'

        // Header
        Label headerLabel = new Label('Global Search')
        headerLabel.font = Font.font('System', FontWeight.BOLD, 14)
        headerLabel.textFill = Color.web('#4CAF50')

        // Search field with icon
        HBox searchBox = new HBox(5)
        searchBox.alignment = Pos.CENTER_LEFT

        Label searchIcon = new Label('\uD83D\uDD0D')  // 🔍 magnifying glass
        searchIcon.font = Font.font(16)

        searchField = new TextField()
        searchField.promptText = 'Search books, signs, items, portals...'
        HBox.setHgrow(searchField, Priority.ALWAYS)

        loadingIndicator = new ProgressIndicator()
        loadingIndicator.maxWidth = 20
        loadingIndicator.maxHeight = 20
        loadingIndicator.visible = false

        Button clearButton = new Button('✕')
        clearButton.style = '-fx-background-color: transparent; -fx-text-fill: gray;'
        clearButton.onAction = { event -> clearSearch() }

        searchBox.children.addAll(searchIcon, searchField, loadingIndicator, clearButton)

        // Results list
        resultsList = new ListView<>()
        resultsList.cellFactory = { listView -> new SearchResultCell() }
        resultsList.placeholder = new Label('Enter at least 2 characters to search')
        VBox.setVgrow(resultsList, Priority.ALWAYS)

        // Status label
        statusLabel = new Label('')
        statusLabel.style = '-fx-text-fill: gray; -fx-font-size: 11px;'

        // Event handlers
        searchField.textProperty().addListener { obs, oldVal, newVal ->
            handleSearchInput(newVal)
        }

        searchField.onKeyPressed = { event ->
            if (event.code == KeyCode.ESCAPE) {
                clearSearch()
            } else if (event.code == KeyCode.DOWN) {
                resultsList.requestFocus()
                if (!resultsList.items.isEmpty()) {
                    resultsList.selectionModel.selectFirst()
                }
            }
        }

        resultsList.onKeyPressed = { event ->
            if (event.code == KeyCode.UP && resultsList.selectionModel.selectedIndex == 0) {
                searchField.requestFocus()
            } else if (event.code == KeyCode.ENTER) {
                handleResultSelection()
            }
        }

        resultsList.onMouseClicked = { event ->
            if (event.clickCount == 2) {
                handleResultSelection()
            }
        }

        children.addAll(headerLabel, searchBox, resultsList, statusLabel)
    }

    private void setupKeyboardShortcuts() {
        // Ctrl+F to focus search field
        KeyCodeCombination ctrlF = new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN)

        sceneProperty().addListener { obs, oldScene, newScene ->
            if (newScene) {
                newScene.accelerators.put(ctrlF, { -> focusSearch() })
            }
        }
    }

    /**
     * Focus the search field and select all text.
     */
    void focusSearch() {
        searchField.requestFocus()
        searchField.selectAll()
    }

    /**
     * Clear the search and reset UI.
     */
    void clearSearch() {
        pendingQuery = ''
        lastQuery = ''
        currentSearchToken = 0
        debounceTimer?.stop()
        loadingIndicator.visible = false

        searchField.clear()
        resultsList.items.clear()
        allResults.clear()
        statusLabel.text = ''
    }

    /**
     * Handle search input with debouncing.
     */
    private void handleSearchInput(String query) {
        String trimmed = query?.trim() ?: ''
        if (trimmed.length() < MIN_QUERY_LENGTH) {
            pendingQuery = ''
            debounceTimer?.stop()
            currentSearchToken = 0
            loadingIndicator.visible = false
            resultsList.items.clear()
            allResults.clear()
            statusLabel.text = trimmed ? 'Enter at least 2 characters' : ''
            return
        }

        pendingQuery = trimmed

        // Debounce: wait for user to stop typing before searching
        debounceTimer.stop()
        debounceTimer.playFromStart()
    }

    private void startSearch(String query) {
        String q = query?.trim() ?: ''
        if (q.length() < MIN_QUERY_LENGTH) {
            return
        }

        // Avoid re-searching the same query unless the model changed (updateModel() clears lastQuery)
        if (q == lastQuery) {
            return
        }
        lastQuery = q

        long token = System.nanoTime()
        currentSearchToken = token

        Thread.start {
            performSearch(q, token)
        }
    }

    /**
     * Perform the actual search across all data types.
     */
    private void performSearch(String query, long token) {
        Platform.runLater {
            if (token == currentSearchToken) {
                loadingIndicator.visible = true
            }
        }

        List<SearchResult> results = []
        String lowerQuery = query.toLowerCase()

        try {
            // Search books
            model.books.each { book ->
                int score = 0

                // Search in title
                if (book.title) {
                    score = Math.max(score, calculateRelevanceScore(book.title.toString(), lowerQuery))
                }

                // Search in author
                if (book.author) {
                    score = Math.max(score, calculateRelevanceScore(book.author.toString(), lowerQuery) - 10)
                }

                // Search in pages (lower priority)
                if (book.pages instanceof List) {
                    for (page in book.pages) {
                        if (page?.toString()?.toLowerCase()?.contains(lowerQuery)) {
                            score = Math.max(score, 20)
                            break
                        }
                    }
                }

                if (score > 0) {
                    String displayText = book.title ?: 'Untitled Book'
                    String subtitle = "by ${book.author ?: 'Unknown'} | ${book.pages?.size() ?: 0} pages"
                    if (book.location) {
                        subtitle += " | ${book.location}"
                    }
                    results << new SearchResult('book', book, displayText, subtitle, score)
                }
            }

            // Search signs
            model.signs.each { sign ->
                int score = 0

                // Search all four lines
                ['line1', 'line2', 'line3', 'line4'].each { lineKey ->
                    if (sign[lineKey]) {
                        score = Math.max(score, calculateRelevanceScore(sign[lineKey].toString(), lowerQuery))
                    }
                }

                if (score > 0) {
                    // Build display text from non-empty lines
                    List<String> lines = []
                    ['line1', 'line2', 'line3', 'line4'].each { lineKey ->
                        if (sign[lineKey]) {
                            lines << sign[lineKey].toString()
                        }
                    }
                    String displayText = lines.join(' | ') ?: 'Empty Sign'
                    String subtitle = "at (${sign.x}, ${sign.y}, ${sign.z})"
                    if (sign.dimension) {
                        subtitle += " in ${sign.dimension}"
                    }
                    results << new SearchResult('sign', sign, displayText, subtitle, score)
                }
            }

            // Search custom names
            model.customNames.each { item ->
                int score = 0

                // Search in custom name
                if (item.name) {
                    score = Math.max(score, calculateRelevanceScore(item.name.toString(), lowerQuery))
                }

                // Search in type
                if (item.type) {
                    score = Math.max(score, calculateRelevanceScore(item.type.toString(), lowerQuery) - 10)
                }

                // Search in item_id
                if (item.item_id) {
                    score = Math.max(score, calculateRelevanceScore(item.item_id.toString(), lowerQuery) - 20)
                }

                if (score > 0) {
                    String displayText = item.name ?: 'Unnamed Item'
                    String subtitle = "${item.type ?: 'Unknown'}"
                    if (item.item_id) {
                        subtitle += " (${item.item_id})"
                    }
                    if (item.x != null && item.y != null && item.z != null) {
                        subtitle += " at (${item.x}, ${item.y}, ${item.z})"
                    }
                    results << new SearchResult('custom_name', item, displayText, subtitle, score)
                }
            }

            // Search portals
            model.portals.each { portal ->
                int score = 0

                // Search in dimension
                if (portal.dimension) {
                    score = Math.max(score, calculateRelevanceScore(portal.dimension.toString(), lowerQuery))
                }

                // Search in portal_id (lower priority)
                if (portal.portal_id) {
                    score = Math.max(score, calculateRelevanceScore(portal.portal_id.toString(), lowerQuery) - 20)
                }

                if (score > 0) {
                    String displayText = "Portal #${portal.portal_id ?: 'Unknown'}"
                    String subtitle = "${portal.dimension ?: 'Unknown dimension'} | ${portal.block_count ?: 0} blocks"
                    if (portal.center_x != null) {
                        subtitle += " | center: (${portal.center_x}, ${portal.center_y}, ${portal.center_z})"
                    }
                    results << new SearchResult('portal', portal, displayText, subtitle, score)
                }
            }

            // Search item database (if available)
            if (model.itemDatabase) {
                try {
                    List<Map> itemMatches = model.itemDatabase.searchByItemId(query)
                    itemMatches?.take(10)?.each { item ->
                        int score = calculateRelevanceScore(item.item_id.toString(), lowerQuery)
                        String displayText = item.item_id ?: 'Unknown Item'
                        String subtitle = "Count: ${item.count ?: 1}"
                        if (item.custom_name) {
                            subtitle += " | Name: ${item.custom_name}"
                        }
                        if (item.x != null) {
                            subtitle += " | at (${item.x}, ${item.y}, ${item.z})"
                        }
                        results << new SearchResult('item', item, displayText, subtitle, score)
                    }
                } catch (Exception e) {
                    LOGGER.warn("Failed to search item database: ${e.message}")
                }
            }

            // Sort by relevance score (highest first)
            results.sort { -it.score }

            // Limit results
            if (results.size() > MAX_RESULTS) {
                results = results.take(MAX_RESULTS)
            }

            // Update UI on JavaFX thread
            Platform.runLater {
                if (token != currentSearchToken) {
                    return
                }
                allResults = results
                updateResultsList()
                loadingIndicator.visible = false

                if (results.isEmpty()) {
                    statusLabel.text = "No results found for '${query}'"
                } else {
                    int totalFound = results.size()
                    String limitNote = totalFound == MAX_RESULTS ? " (showing first ${MAX_RESULTS})" : ""
                    statusLabel.text = "${totalFound} result${totalFound == 1 ? '' : 's'} found${limitNote}"
                }
            }

        } catch (Exception e) {
            LOGGER.error("Search failed: ${e.message}", e)
            Platform.runLater {
                if (token != currentSearchToken) {
                    return
                }
                loadingIndicator.visible = false
                statusLabel.text = "Search error: ${e.message}"
            }
        }
    }

    /**
     * Calculate relevance score for a text match.
     * Higher score = more relevant.
     */
    private int calculateRelevanceScore(String text, String query) {
        if (!text) return 0

        String lowerText = text.toLowerCase()

        // Exact match
        if (lowerText == query) {
            return 100
        }

        // Starts with
        if (lowerText.startsWith(query)) {
            return 80
        }

        // Contains substring
        if (lowerText.contains(query)) {
            return 50
        }

        // Fuzzy match (all query characters present in order)
        if (fuzzyMatch(lowerText, query)) {
            return 10
        }

        return 0
    }

    /**
     * Check if all characters in query appear in text in order (fuzzy match).
     */
    private boolean fuzzyMatch(String text, String query) {
        int textIndex = 0
        for (int i = 0; i < query.length(); i++) {
            String ch = query.substring(i, i + 1)
            textIndex = text.indexOf(ch, textIndex)
            if (textIndex == -1) {
                return false
            }
            textIndex += 1
        }
        return true
    }

    /**
     * Update the results list with grouped results.
     */
    private void updateResultsList() {
        resultsList.items.clear()

        // Group results by type
        Map<String, List<SearchResult>> grouped = allResults.groupBy { it.type }

        // Add results with group headers
        ['book', 'sign', 'custom_name', 'item', 'portal'].each { type ->
            List<SearchResult> typeResults = grouped[type]
            if (typeResults) {
                // Add group header
                String headerText = getTypeHeader(type, typeResults.size())
                resultsList.items.add(new SearchResult('header', null, headerText, '', 0))

                // Add individual results
                resultsList.items.addAll(typeResults)
            }
        }
    }

    /**
     * Get header text for a result type.
     */
    private String getTypeHeader(String type, int count) {
        Map<String, String> headers = [
            'book': "\uD83D\uDCDA BOOKS",           // 📚
            'sign': "\uD83E\uDEA7 SIGNS",           // 🪧
            'custom_name': "\uD83C\uDFF7️ CUSTOM NAMES",  // 🏷️
            'item': "⚔️ ITEMS",
            'portal': "\uD83D\uDEAA PORTALS"        // 🚪
        ]
        String header = headers[type] ?: type.toUpperCase()
        return "${header} (${count} result${count == 1 ? '' : 's'})"
    }

    /**
     * Handle result selection (double-click or Enter).
     */
    private void handleResultSelection() {
        SearchResult selected = resultsList.selectionModel.selectedItem
        if (selected && selected.type != 'header' && onResultSelected) {
            onResultSelected(selected)
        }
    }

    /**
     * Update model reference (for when data is reloaded).
     */
    void updateModel(OutputViewerModel newModel) {
        this.model = newModel
        clearSearch()
    }

    /**
     * Search result data class.
     */
    static class SearchResult {
        String type         // 'book', 'sign', 'custom_name', 'item', 'portal', 'header'
        Object data         // Original data object
        String displayText  // Main display text
        String subtitle     // Secondary info text
        int score           // Relevance score

        SearchResult(String type, Object data, String displayText, String subtitle, int score) {
            this.type = type
            this.data = data
            this.displayText = displayText
            this.subtitle = subtitle
            this.score = score
        }

        @Override
        String toString() {
            return displayText
        }
    }

    /**
     * Custom list cell for search results.
     */
    private static class SearchResultCell extends ListCell<SearchResult> {

        @Override
        protected void updateItem(SearchResult result, boolean empty) {
            super.updateItem(result, empty)

            if (empty || !result) {
                text = null
                graphic = null
                style = ''
                return
            }

            // Group headers have special styling
            if (result.type == 'header') {
                text = result.displayText
                graphic = null
                style = '-fx-font-weight: bold; -fx-background-color: derive(-fx-base, -10%); -fx-text-fill: #4CAF50;'
                return
            }

            // Regular result
            VBox content = new VBox(2)
            content.padding = new Insets(3, 5, 3, 15)

            Label mainLabel = new Label(result.displayText)
            mainLabel.style = '-fx-font-weight: bold;'

            Label subtitleLabel = new Label(result.subtitle)
            subtitleLabel.style = '-fx-font-size: 10px; -fx-text-fill: gray;'

            content.children.addAll(mainLabel, subtitleLabel)

            text = null
            graphic = content
            style = '-fx-padding: 0;'
        }
    }
}

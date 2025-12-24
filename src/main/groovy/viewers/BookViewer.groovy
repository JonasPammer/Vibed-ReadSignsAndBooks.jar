package viewers

import groovy.json.JsonSlurper
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.application.Application
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.input.KeyCode
import javafx.scene.input.ScrollEvent
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.FontPosture
import javafx.scene.text.FontWeight
import javafx.scene.text.Text
import javafx.scene.text.TextFlow
import javafx.stage.FileChooser
import javafx.stage.Stage
import javafx.util.Duration

import java.util.concurrent.ThreadLocalRandom

/**
 * Minecraft-styled book viewer with full formatting support.
 *
 * Features:
 * - Two-page spread layout mimicking in-game written book
 * - Full Minecraft formatting code support (colors, bold, italic, underline, strikethrough, obfuscated)
 * - Page navigation (buttons, keyboard arrows, scroll wheel)
 * - Book list sidebar with filtering by author, title search, sort options
 * - Parchment/paper background texture
 */
class BookViewer extends Application {

    // Book data
    private List<Map> allBooks = []
    private List<Map> filteredBooks = []
    private Map currentBook = null
    private int currentPageIndex = 0

    // UI Components
    private TextFlow leftPageFlow
    private TextFlow rightPageFlow
    private Label pageNumberLabel
    private Label bookTitleLabel
    private Label bookAuthorLabel
    private ListView<String> bookListView
    private TextField searchField
    private ComboBox<String> authorFilterCombo
    private ComboBox<String> sortCombo
    private Button prevButton
    private Button nextButton

    // Obfuscated text animation
    private Timeline obfuscationTimeline
    private List<Text> obfuscatedTexts = []

    // Minecraft color mappings (§ codes)
    private static final Map<String, Color> COLOR_MAP = [
        '0': Color.web('#000000'),  // Black
        '1': Color.web('#0000AA'),  // Dark Blue
        '2': Color.web('#00AA00'),  // Dark Green
        '3': Color.web('#00AAAA'),  // Dark Aqua
        '4': Color.web('#AA0000'),  // Dark Red
        '5': Color.web('#AA00AA'),  // Dark Purple
        '6': Color.web('#FFAA00'),  // Gold
        '7': Color.web('#AAAAAA'),  // Gray
        '8': Color.web('#555555'),  // Dark Gray
        '9': Color.web('#5555FF'),  // Blue
        'a': Color.web('#55FF55'),  // Green
        'b': Color.web('#55FFFF'),  // Aqua
        'c': Color.web('#FF5555'),  // Red
        'd': Color.web('#FF55FF'),  // Light Purple
        'e': Color.web('#FFFF55'),  // Yellow
        'f': Color.web('#FFFFFF')   // White
    ]

    // Parchment background color
    private static final String PARCHMENT_COLOR = '#E8D5B3'
    private static final String BORDER_COLOR = '#8B7355'

    void start(Stage primaryStage) {
        primaryStage.title = 'Minecraft Book Viewer'

        // Initialize UI components
        BorderPane root = initializeUI()

        Scene scene = new Scene(root, 1100, 700)

        // Keyboard navigation
        scene.onKeyPressed = { event ->
            if (event.code == KeyCode.LEFT || event.code == KeyCode.A) {
                previousPage()
            } else if (event.code == KeyCode.RIGHT || event.code == KeyCode.D) {
                nextPage()
            }
        }

        primaryStage.scene = scene
        primaryStage.show()

        // Try to load default book file (all_books_stendhal.json)
        tryLoadDefaultBooks()
    }

    /**
     * Initialize UI components. Can be called directly for testing
     * without launching the full Application.
     * @return The root BorderPane containing the full UI
     */
    BorderPane initializeUI() {
        BorderPane root = new BorderPane()

        // Create sidebar
        VBox sidebar = createSidebar()
        sidebar.prefWidth = 300
        sidebar.minWidth = 250
        sidebar.maxWidth = 400

        // Create book viewer area
        VBox bookViewer = createBookViewer()

        root.left = sidebar
        root.center = bookViewer

        return root
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox(10)
        sidebar.padding = new Insets(15)
        sidebar.style = '-fx-background-color: #f5f5f5; -fx-border-color: #cccccc; -fx-border-width: 0 1 0 0;'

        // Title
        Label sidebarTitle = new Label('Book Library')
        sidebarTitle.style = '-fx-font-size: 18px; -fx-font-weight: bold;'

        // Load button
        Button loadButton = new Button('Load Books JSON')
        loadButton.maxWidth = Double.MAX_VALUE
        loadButton.onAction = { loadBooksFile(sidebarTitle.scene.window as Stage) }

        // Search field
        searchField = new TextField()
        searchField.promptText = 'Search titles...'
        searchField.textProperty().addListener { obs, old, newVal -> filterBooks() }

        // Author filter
        Label authorLabel = new Label('Filter by Author:')
        authorFilterCombo = new ComboBox<>()
        authorFilterCombo.maxWidth = Double.MAX_VALUE
        authorFilterCombo.onAction = { filterBooks() }

        // Sort options
        Label sortLabel = new Label('Sort by:')
        sortCombo = new ComboBox<>()
        sortCombo.items.addAll('Title (A-Z)', 'Title (Z-A)', 'Author (A-Z)', 'Page Count (High-Low)', 'Page Count (Low-High)')
        sortCombo.value = 'Title (A-Z)'
        sortCombo.maxWidth = Double.MAX_VALUE
        sortCombo.onAction = { filterBooks() }

        // Book list
        bookListView = new ListView<>()
        bookListView.selectionModel.selectedItemProperty().addListener { obs, old, newVal ->
            if (newVal) {
                selectBook(newVal)
            }
        }
        VBox.setVgrow(bookListView, Priority.ALWAYS)

        // Stats label
        Label statsLabel = new Label('No books loaded')
        statsLabel.style = '-fx-font-size: 11px; -fx-text-fill: gray;'
        statsLabel.id = 'statsLabel'  // For easy reference

        sidebar.children.addAll(
            sidebarTitle,
            loadButton,
            new Separator(),
            searchField,
            authorLabel,
            authorFilterCombo,
            sortLabel,
            sortCombo,
            new Separator(),
            bookListView,
            statsLabel
        )

        return sidebar
    }

    private VBox createBookViewer() {
        VBox viewer = new VBox(15)
        viewer.padding = new Insets(20)
        viewer.alignment = Pos.TOP_CENTER
        viewer.style = '-fx-background-color: #4a4a4a;'  // Dark background like in-game

        // Book info area
        bookTitleLabel = new Label('')
        bookTitleLabel.style = '-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: white;'

        bookAuthorLabel = new Label('')
        bookAuthorLabel.style = '-fx-font-size: 12px; -fx-text-fill: #cccccc;'

        VBox infoBox = new VBox(5, bookTitleLabel, bookAuthorLabel)
        infoBox.alignment = Pos.CENTER

        // Two-page spread container
        HBox pageSpread = new HBox(20)
        pageSpread.alignment = Pos.CENTER
        pageSpread.maxWidth = 800
        pageSpread.maxHeight = 500

        // Left page
        leftPageFlow = new TextFlow()
        leftPageFlow.prefHeight = 440
        leftPageFlow.maxWidth = 340
        VBox leftPage = createPage(leftPageFlow)

        // Right page
        rightPageFlow = new TextFlow()
        rightPageFlow.prefHeight = 440
        rightPageFlow.maxWidth = 340
        VBox rightPage = createPage(rightPageFlow)

        pageSpread.children.addAll(leftPage, rightPage)

        // Scroll wheel navigation
        pageSpread.onScroll = { ScrollEvent event ->
            if (event.deltaY > 0) {
                previousPage()
            } else if (event.deltaY < 0) {
                nextPage()
            }
        }

        // Navigation controls
        HBox navControls = new HBox(20)
        navControls.alignment = Pos.CENTER

        prevButton = new Button('◀ Previous')
        prevButton.onAction = { previousPage() }
        prevButton.disable = true

        pageNumberLabel = new Label('Page -')
        pageNumberLabel.style = '-fx-text-fill: white; -fx-font-size: 13px;'

        nextButton = new Button('Next ▶')
        nextButton.onAction = { nextPage() }
        nextButton.disable = true

        navControls.children.addAll(prevButton, pageNumberLabel, nextButton)

        viewer.children.addAll(infoBox, pageSpread, navControls)

        return viewer
    }

    private VBox createPage(TextFlow pageFlow) {
        VBox page = new VBox()
        page.prefWidth = 380
        page.prefHeight = 480
        page.style = "-fx-background-color: ${PARCHMENT_COLOR}; -fx-border-color: ${BORDER_COLOR}; -fx-border-width: 2; -fx-padding: 20;"

        page.children.add(pageFlow)

        return page
    }

    private void loadBooksFile(Stage ownerStage) {
        FileChooser fileChooser = new FileChooser()
        fileChooser.title = 'Select Books JSON File'
        fileChooser.extensionFilters.add(
            new FileChooser.ExtensionFilter('JSON Files', '*.json')
        )

        File selectedFile = fileChooser.showOpenDialog(ownerStage)
        if (selectedFile) {
            loadBooks(selectedFile)
        }
    }

    private void tryLoadDefaultBooks() {
        // Try to find all_books_stendhal.json in current directory or test resources
        List<String> searchPaths = [
            'all_books_stendhal.json',
            'output/all_books_stendhal.json',
            'ReadBooks/all_books_stendhal.json',
            '../ReadBooks/all_books_stendhal.json',
            'src/test/resources/1_21_10-44-3/ReadBooks/2025-12-23/all_books_stendhal.json'
        ]

        for (String path : searchPaths) {
            File file = new File(path)
            if (file.exists()) {
                loadBooks(file)
                return
            }
        }
    }

    private void loadBooks(File file) {
        try {
            JsonSlurper jsonSlurper = new JsonSlurper()
            def json = jsonSlurper.parse(file)

            allBooks.clear()

            if (json instanceof List) {
                allBooks.addAll(json as List<Map>)
            } else if (json instanceof Map) {
                // Handle single book or wrapped format
                if (json.books) {
                    allBooks.addAll(json.books as List<Map>)
                } else {
                    allBooks.add(json as Map)
                }
            }

            // Update author filter
            Set<String> authors = allBooks.collect { it.author ?: 'Unknown' } as Set
            authorFilterCombo.items.clear()
            authorFilterCombo.items.add('All Authors')
            authorFilterCombo.items.addAll(authors.sort())
            authorFilterCombo.value = 'All Authors'

            filterBooks()

            // Update stats
            updateStats()

            showAlert('Success', "Loaded ${allBooks.size()} books from ${file.name}", Alert.AlertType.INFORMATION)

        } catch (Exception e) {
            showAlert('Error', "Failed to load books: ${e.message}", Alert.AlertType.ERROR)
            e.printStackTrace()
        }
    }

    private void filterBooks() {
        filteredBooks.clear()

        String searchTerm = searchField.text?.toLowerCase() ?: ''
        String authorFilter = authorFilterCombo.value

        filteredBooks = allBooks.findAll { book ->
            boolean matchesSearch = searchTerm.empty || book.title?.toLowerCase()?.contains(searchTerm)
            boolean matchesAuthor = !authorFilter || authorFilter == 'All Authors' || book.author == authorFilter
            return matchesSearch && matchesAuthor
        }

        // Sort
        String sortOption = sortCombo.value
        switch (sortOption) {
            case 'Title (A-Z)':
                filteredBooks.sort { it.title ?: '' }
                break
            case 'Title (Z-A)':
                filteredBooks.sort { it.title ?: '' }
                filteredBooks.reverse(true)
                break
            case 'Author (A-Z)':
                filteredBooks.sort { it.author ?: '' }
                break
            case 'Page Count (High-Low)':
                filteredBooks.sort { -(it.pages?.size() ?: 0) }
                break
            case 'Page Count (Low-High)':
                filteredBooks.sort { it.pages?.size() ?: 0 }
                break
        }

        // Update list view
        bookListView.items.clear()
        filteredBooks.each { book ->
            String displayName = "${book.title ?: 'Untitled'} - ${book.author ?: 'Unknown'} (${book.pages?.size() ?: 0} pages)"
            bookListView.items.add(displayName)
        }

        updateStats()
    }

    private void updateStats() {
        Label statsLabel = bookListView.parent.parent.lookup('#statsLabel') as Label
        if (statsLabel) {
            statsLabel.text = "${filteredBooks.size()} of ${allBooks.size()} books shown"
        }
    }

    private void selectBook(String displayName) {
        // Find the book from filtered list by matching display name
        int index = bookListView.items.indexOf(displayName)
        if (index >= 0 && index < filteredBooks.size()) {
            currentBook = filteredBooks[index]
            currentPageIndex = 0
            displayCurrentPages()
        }
    }

    private void displayCurrentPages() {
        if (!currentBook) {
            return
        }

        // Update book info
        bookTitleLabel.text = currentBook.title ?: 'Untitled Book'
        bookAuthorLabel.text = "by ${currentBook.author ?: 'Unknown'}"

        List pages = currentBook.pages ?: []
        int totalPages = pages.size()

        // Clear previous obfuscated texts
        stopObfuscation()

        // Display left page (even page numbers: 0, 2, 4...)
        if (currentPageIndex < totalPages) {
            String leftPageText = pages[currentPageIndex] as String
            renderPage(leftPageFlow, leftPageText)
        } else {
            leftPageFlow.children.clear()
        }

        // Display right page (odd page numbers: 1, 3, 5...)
        if (currentPageIndex + 1 < totalPages) {
            String rightPageText = pages[currentPageIndex + 1] as String
            renderPage(rightPageFlow, rightPageText)
        } else {
            rightPageFlow.children.clear()
        }

        // Update page number label
        int leftPageNum = currentPageIndex + 1
        int rightPageNum = Math.min(currentPageIndex + 2, totalPages)
        if (currentPageIndex + 1 >= totalPages) {
            pageNumberLabel.text = "Page ${leftPageNum} of ${totalPages}"
        } else {
            pageNumberLabel.text = "Pages ${leftPageNum}-${rightPageNum} of ${totalPages}"
        }

        // Update navigation buttons
        prevButton.disable = (currentPageIndex == 0)
        nextButton.disable = (currentPageIndex + 2 >= totalPages)

        // Start obfuscation animation
        startObfuscation()
    }

    private void renderPage(TextFlow textFlow, String pageText) {
        textFlow.children.clear()

        if (!pageText) {
            return
        }

        // Parse and render text with Minecraft formatting codes
        List<Text> textNodes = parseMinecraftText(pageText)
        textFlow.children.addAll(textNodes)
    }

    /**
     * Parse Minecraft text with § formatting codes.
     * Returns list of Text nodes with proper styling.
     */
    private List<Text> parseMinecraftText(String input) {
        List<Text> result = []

        if (!input) {
            return result
        }

        // Current formatting state
        Color currentColor = Color.BLACK
        boolean bold = false
        boolean italic = false
        boolean underline = false
        boolean strikethrough = false
        boolean obfuscated = false

        StringBuilder textBuffer = new StringBuilder()

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i)

            // Check for formatting code (§ followed by code character)
            if (c == '\u00A7' && i + 1 < input.length()) {
                // Flush current text buffer
                if (textBuffer.length() > 0) {
                    Text textNode = createStyledText(textBuffer.toString(), currentColor, bold, italic, underline, strikethrough, obfuscated)
                    result.add(textNode)
                    textBuffer.setLength(0)
                }

                char code = input.charAt(i + 1)
                String codeStr = String.valueOf(code)
                i++  // Skip the code character

                // Reset code
                if (codeStr == 'r') {
                    currentColor = Color.BLACK
                    bold = false
                    italic = false
                    underline = false
                    strikethrough = false
                    obfuscated = false
                }
                // Color codes
                else if (COLOR_MAP.containsKey(codeStr)) {
                    currentColor = COLOR_MAP[codeStr]
                    // Colors reset formatting in Minecraft
                    bold = false
                    italic = false
                    underline = false
                    strikethrough = false
                    obfuscated = false
                }
                // Formatting codes
                else if (codeStr == 'l') {
                    bold = true
                }
                else if (codeStr == 'o') {
                    italic = true
                }
                else if (codeStr == 'n') {
                    underline = true
                }
                else if (codeStr == 'm') {
                    strikethrough = true
                }
                else if (codeStr == 'k') {
                    obfuscated = true
                }

                continue
            }

            textBuffer.append(c)
        }

        // Flush remaining text
        if (textBuffer.length() > 0) {
            Text textNode = createStyledText(textBuffer.toString(), currentColor, bold, italic, underline, strikethrough, obfuscated)
            result.add(textNode)
        }

        return result
    }

    private Text createStyledText(String content, Color color, boolean bold, boolean italic,
                                   boolean underline, boolean strikethrough, boolean obfuscated) {
        Text text = new Text(content)
        text.fill = color

        // Font styling
        FontWeight weight = bold ? FontWeight.BOLD : FontWeight.NORMAL
        FontPosture posture = italic ? FontPosture.ITALIC : FontPosture.REGULAR
        text.font = Font.font('Serif', weight, posture, 14)

        text.underline = underline
        text.strikethrough = strikethrough

        // Track obfuscated texts for animation
        if (obfuscated) {
            text.userData = content  // Store original content for randomization
            obfuscatedTexts.add(text)
        }

        return text
    }

    private void startObfuscation() {
        if (obfuscatedTexts.empty) {
            return
        }

        // Animate obfuscated text at 50ms intervals (20 FPS)
        obfuscationTimeline = new Timeline(new KeyFrame(Duration.millis(50), { event ->
            obfuscatedTexts.each { Text text ->
                String original = text.userData as String
                if (original) {
                    String randomized = randomizeText(original)
                    text.text = randomized
                }
            }
        }))
        obfuscationTimeline.cycleCount = Timeline.INDEFINITE
        obfuscationTimeline.play()
    }

    private void stopObfuscation() {
        if (obfuscationTimeline) {
            obfuscationTimeline.stop()
        }
        obfuscatedTexts.clear()
    }

    private String randomizeText(String text) {
        StringBuilder result = new StringBuilder(text.length())
        String chars = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()_+-=[]{}|;:,.<>?'

        for (int i = 0; i < text.length(); i++) {
            char original = text.charAt(i)
            if (original == ' ' || original == '\n') {
                result.append(original)
            } else {
                int randomIndex = ThreadLocalRandom.current().nextInt(chars.length())
                result.append(chars.charAt(randomIndex))
            }
        }

        return result.toString()
    }

    private void previousPage() {
        if (currentPageIndex > 0) {
            currentPageIndex -= 2
            if (currentPageIndex < 0) {
                currentPageIndex = 0
            }
            displayCurrentPages()
        }
    }

    private void nextPage() {
        List pages = currentBook?.pages ?: []
        if (currentPageIndex + 2 < pages.size()) {
            currentPageIndex += 2
            displayCurrentPages()
        }
    }

    private void showAlert(String title, String message, Alert.AlertType type) {
        Platform.runLater {
            Alert alert = new Alert(type)
            alert.title = title
            alert.headerText = null
            alert.contentText = message
            alert.showAndWait()
        }
    }

    static void main(String[] args) {
        launch(BookViewer, args)
    }

    /**
     * Cleanup on application stop
     */
    void stop() {
        stopObfuscation()
    }
}

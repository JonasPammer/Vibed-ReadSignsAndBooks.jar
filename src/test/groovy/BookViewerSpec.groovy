import groovy.json.JsonBuilder
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.ListView
import javafx.scene.control.TextField
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.FontPosture
import javafx.scene.text.FontWeight
import javafx.scene.text.Text
import javafx.scene.text.TextFlow
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise
import spock.lang.TempDir
import viewers.BookViewer

import java.awt.GraphicsEnvironment
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Comprehensive GUI Integration Tests for BookViewer
 *
 * Tests the BookViewer component with mock book data to verify:
 * - Book loading from JSON
 * - Page navigation (next/previous)
 * - Text rendering with Minecraft formatting codes (§ colors)
 * - Search within book functionality
 * - Edge cases (empty book, single page, many pages)
 *
 * IMPORTANT: These tests require a display to run. They will be skipped
 * automatically in headless environments (CI without xvfb, etc.).
 */
@IgnoreIf({ GraphicsEnvironment.headless })
@Stepwise
class BookViewerSpec extends Specification {

    @Shared
    static boolean jfxInitialized = false

    @TempDir
    Path tempDir

    File testFolder

    static {
        // Initialize JavaFX toolkit once for all tests
        if (!jfxInitialized) {
            new JFXPanel()
            jfxInitialized = true
            println 'JavaFX toolkit initialized for BookViewer tests'
        }
    }

    def setup() {
        testFolder = tempDir.toFile()
    }

    // =========================================================================
    // BookViewer Creation Tests
    // =========================================================================

    def "BookViewer can be instantiated without error"() {
        given: 'A countdown latch for JavaFX thread'
        CountDownLatch latch = new CountDownLatch(1)

        when: 'We create a BookViewer instance on JavaFX thread'
        BookViewer viewer = null
        Platform.runLater({
            try {
                viewer = new BookViewer()
                viewer.initializeUI()  // Initialize UI components for testing
                latch.countDown()
            } catch (Exception e) {
                println "ERROR creating BookViewer: ${e.message}"
                e.printStackTrace()
                latch.countDown()
            }
        } as Runnable)

        and: 'Wait for creation to complete'
        boolean completed = latch.await(5, TimeUnit.SECONDS)

        then: 'BookViewer should be created successfully'
        completed
        viewer != null
    }

    def "BookViewer has all required UI components"() {
        given: 'A countdown latch'
        CountDownLatch latch = new CountDownLatch(1)
        BookViewer viewer = null

        when: 'We create a BookViewer and check components'
        Platform.runLater({
            try {
                viewer = new BookViewer()
                viewer.initializeUI()  // Initialize UI components for testing
                latch.countDown()
            } catch (Exception e) {
                println "ERROR checking components: ${e.message}"
                e.printStackTrace()
                latch.countDown()
            }
        } as Runnable)

        and: 'Wait for initialization'
        boolean completed = latch.await(5, TimeUnit.SECONDS)

        then: 'All required UI components should exist'
        completed
        viewer.leftPageFlow != null
        viewer.rightPageFlow != null
        viewer.pageNumberLabel != null
        viewer.bookTitleLabel != null
        viewer.bookAuthorLabel != null
        viewer.bookListView != null
        viewer.searchField != null
        viewer.authorFilterCombo != null
        viewer.sortCombo != null
        viewer.prevButton != null
        viewer.nextButton != null
    }

    // =========================================================================
    // Book Loading Tests
    // =========================================================================

    def "BookViewer loads books from JSON file (array format)"() {
        given: 'A books JSON file with array format'
        def booksData = [
            [
                title: 'Test Book 1',
                author: 'Test Author 1',
                pages: ['Page 1 content', 'Page 2 content']
            ],
            [
                title: 'Test Book 2',
                author: 'Test Author 2',
                pages: ['Single page']
            ]
        ]
        File booksFile = new File(testFolder, 'test_books.json')
        booksFile.text = new JsonBuilder(booksData).toPrettyString()

        and: 'A countdown latch'
        CountDownLatch latch = new CountDownLatch(1)
        BookViewer viewer = null

        when: 'We load the books file'
        Platform.runLater({
            try {
                viewer = new BookViewer()
                viewer.initializeUI()
                viewer.loadBooks(booksFile)
                latch.countDown()
            } catch (Exception e) {
                println "ERROR loading books: ${e.message}"
                e.printStackTrace()
                latch.countDown()
            }
        } as Runnable)

        and: 'Wait for loading'
        boolean completed = latch.await(5, TimeUnit.SECONDS)

        then: 'Books should be loaded successfully'
        completed
        viewer.allBooks.size() == 2
        viewer.allBooks[0].title == 'Test Book 1'
        viewer.allBooks[1].author == 'Test Author 2'
    }

    def "BookViewer loads books from JSON file (wrapped format)"() {
        given: 'A books JSON file with wrapped format'
        def booksData = [
            books: [
                [
                    title: 'Wrapped Book',
                    author: 'Wrapped Author',
                    pages: ['Content']
                ]
            ]
        ]
        File booksFile = new File(testFolder, 'wrapped_books.json')
        booksFile.text = new JsonBuilder(booksData).toPrettyString()

        and: 'A countdown latch'
        CountDownLatch latch = new CountDownLatch(1)
        BookViewer viewer = null

        when: 'We load the wrapped format'
        Platform.runLater({
            try {
                viewer = new BookViewer()
                viewer.initializeUI()
                viewer.loadBooks(booksFile)
                latch.countDown()
            } catch (Exception e) {
                println "ERROR loading wrapped books: ${e.message}"
                e.printStackTrace()
                latch.countDown()
            }
        } as Runnable)

        and: 'Wait for loading'
        boolean completed = latch.await(5, TimeUnit.SECONDS)

        then: 'Books should be loaded from wrapped format'
        completed
        viewer.allBooks.size() == 1
        viewer.allBooks[0].title == 'Wrapped Book'
    }

    def "BookViewer updates author filter after loading books"() {
        given: 'A books JSON with multiple authors'
        def booksData = [
            [title: 'Book A', author: 'Alice', pages: ['Content']],
            [title: 'Book B', author: 'Bob', pages: ['Content']],
            [title: 'Book C', author: 'Alice', pages: ['Content']]
        ]
        File booksFile = new File(testFolder, 'multi_author.json')
        booksFile.text = new JsonBuilder(booksData).toPrettyString()

        and: 'A countdown latch'
        CountDownLatch latch = new CountDownLatch(1)
        BookViewer viewer = null
        int authorCount = 0

        when: 'We load the books'
        Platform.runLater({
            try {
                viewer = new BookViewer()
                viewer.initializeUI()
                viewer.loadBooks(booksFile)
                authorCount = viewer.authorFilterCombo.items.size()
                latch.countDown()
            } catch (Exception e) {
                println "ERROR loading multi-author books: ${e.message}"
                e.printStackTrace()
                latch.countDown()
            }
        } as Runnable)

        and: 'Wait for loading'
        boolean completed = latch.await(5, TimeUnit.SECONDS)

        then: 'Author filter should contain unique authors plus "All Authors"'
        completed
        authorCount == 3  // 'All Authors' + 'Alice' + 'Bob'
        viewer.authorFilterCombo.items.contains('All Authors')
        viewer.authorFilterCombo.items.contains('Alice')
        viewer.authorFilterCombo.items.contains('Bob')
    }

    // =========================================================================
    // Book Selection and Display Tests
    // =========================================================================

    def "Selecting a book displays its title and author"() {
        given: 'A books JSON file'
        def booksData = [
            [title: 'Selected Book', author: 'Selected Author', pages: ['Page 1', 'Page 2']]
        ]
        File booksFile = new File(testFolder, 'select_test.json')
        booksFile.text = new JsonBuilder(booksData).toPrettyString()

        and: 'A countdown latch'
        CountDownLatch latch = new CountDownLatch(1)
        BookViewer viewer = null
        String displayedTitle = null
        String displayedAuthor = null

        when: 'We load books and select the first one'
        Platform.runLater({
            try {
                viewer = new BookViewer()
                viewer.initializeUI()
                viewer.loadBooks(booksFile)
                // Select first book by simulating list selection
                String displayName = viewer.bookListView.items[0]
                viewer.selectBook(displayName)
                displayedTitle = viewer.bookTitleLabel.text
                displayedAuthor = viewer.bookAuthorLabel.text
                latch.countDown()
            } catch (Exception e) {
                println "ERROR selecting book: ${e.message}"
                e.printStackTrace()
                latch.countDown()
            }
        } as Runnable)

        and: 'Wait for selection'
        boolean completed = latch.await(5, TimeUnit.SECONDS)

        then: 'Book title and author should be displayed'
        completed
        displayedTitle == 'Selected Book'
        displayedAuthor == 'by Selected Author'
    }

    // =========================================================================
    // Page Navigation Tests
    // =========================================================================

    def "Next button navigates to next page spread"() {
        given: 'A book with 4 pages'
        def booksData = [
            [title: 'Nav Test', author: 'Author', pages: ['Page 1', 'Page 2', 'Page 3', 'Page 4']]
        ]
        File booksFile = new File(testFolder, 'nav_test.json')
        booksFile.text = new JsonBuilder(booksData).toPrettyString()

        and: 'A countdown latch'
        CountDownLatch latch = new CountDownLatch(1)
        BookViewer viewer = null
        int initialPage = 0
        int afterNextPage = 0

        when: 'We navigate to next page'
        Platform.runLater({
            try {
                viewer = new BookViewer()
                viewer.initializeUI()
                viewer.loadBooks(booksFile)
                viewer.selectBook(viewer.bookListView.items[0])
                initialPage = viewer.currentPageIndex

                // Click next button
                viewer.nextPage()
                afterNextPage = viewer.currentPageIndex

                latch.countDown()
            } catch (Exception e) {
                println "ERROR navigating next: ${e.message}"
                e.printStackTrace()
                latch.countDown()
            }
        } as Runnable)

        and: 'Wait for navigation'
        boolean completed = latch.await(5, TimeUnit.SECONDS)

        then: 'Page index should advance by 2 (two-page spread)'
        completed
        initialPage == 0
        afterNextPage == 2
    }

    def "Previous button navigates to previous page spread"() {
        given: 'A book with 4 pages'
        def booksData = [
            [title: 'Nav Test', author: 'Author', pages: ['Page 1', 'Page 2', 'Page 3', 'Page 4']]
        ]
        File booksFile = new File(testFolder, 'prev_test.json')
        booksFile.text = new JsonBuilder(booksData).toPrettyString()

        and: 'A countdown latch'
        CountDownLatch latch = new CountDownLatch(1)
        BookViewer viewer = null
        int afterNextPage = 0
        int afterPrevPage = 0

        when: 'We navigate next then previous'
        Platform.runLater({
            try {
                viewer = new BookViewer()
                viewer.initializeUI()
                viewer.loadBooks(booksFile)
                viewer.selectBook(viewer.bookListView.items[0])

                viewer.nextPage()
                afterNextPage = viewer.currentPageIndex

                viewer.previousPage()
                afterPrevPage = viewer.currentPageIndex

                latch.countDown()
            } catch (Exception e) {
                println "ERROR navigating previous: ${e.message}"
                e.printStackTrace()
                latch.countDown()
            }
        } as Runnable)

        and: 'Wait for navigation'
        boolean completed = latch.await(5, TimeUnit.SECONDS)

        then: 'Should navigate forward and back'
        completed
        afterNextPage == 2
        afterPrevPage == 0
    }

    def "Navigation buttons are disabled at boundaries"() {
        given: 'A book with 2 pages'
        def booksData = [
            [title: 'Boundary Test', author: 'Author', pages: ['Page 1', 'Page 2']]
        ]
        File booksFile = new File(testFolder, 'boundary_test.json')
        booksFile.text = new JsonBuilder(booksData).toPrettyString()

        and: 'A countdown latch'
        CountDownLatch latch = new CountDownLatch(1)
        BookViewer viewer = null
        boolean prevDisabledAtStart = false
        boolean nextDisabledAtEnd = false

        when: 'We check button states at start and end'
        Platform.runLater({
            try {
                viewer = new BookViewer()
                viewer.initializeUI()
                viewer.loadBooks(booksFile)
                viewer.selectBook(viewer.bookListView.items[0])

                prevDisabledAtStart = viewer.prevButton.isDisabled()
                nextDisabledAtEnd = viewer.nextButton.isDisabled()

                latch.countDown()
            } catch (Exception e) {
                println "ERROR checking boundaries: ${e.message}"
                e.printStackTrace()
                latch.countDown()
            }
        } as Runnable)

        and: 'Wait for check'
        boolean completed = latch.await(5, TimeUnit.SECONDS)

        then: 'Prev should be disabled at start, next at end'
        completed
        prevDisabledAtStart == true
        nextDisabledAtEnd == true  // Only 2 pages, both shown at once
    }

    // =========================================================================
    // Minecraft Formatting Code Tests
    // =========================================================================

    def "parseMinecraftText correctly parses color codes"() {
        given: 'A countdown latch'
        CountDownLatch latch = new CountDownLatch(1)
        BookViewer viewer = null
        List<Text> textNodes = null

        when: 'We parse text with color codes'
        Platform.runLater({
            try {
                viewer = new BookViewer()
                viewer.initializeUI()
                String input = '§cRed text§r normal §9Blue text'
                textNodes = viewer.parseMinecraftText(input)
                latch.countDown()
            } catch (Exception e) {
                println "ERROR parsing color codes: ${e.message}"
                e.printStackTrace()
                latch.countDown()
            }
        } as Runnable)

        and: 'Wait for parsing'
        boolean completed = latch.await(5, TimeUnit.SECONDS)

        then: 'Should create multiple Text nodes with correct colors'
        completed
        textNodes != null
        textNodes.size() == 3
        textNodes[0].text == 'Red text'
        textNodes[0].fill == Color.web('#FF5555')  // Red (§c)
        textNodes[1].text == ' normal '
        textNodes[1].fill == Color.BLACK  // Reset (§r)
        textNodes[2].text == 'Blue text'
        textNodes[2].fill == Color.web('#5555FF')  // Blue (§9)
    }

    def "parseMinecraftText correctly parses formatting codes"() {
        given: 'A countdown latch'
        CountDownLatch latch = new CountDownLatch(1)
        BookViewer viewer = null
        List<Text> textNodes = null

        when: 'We parse text with formatting codes'
        Platform.runLater({
            try {
                viewer = new BookViewer()
                viewer.initializeUI()
                String input = '§lBold§r §oItalic§r §nUnderline§r §mStrikethrough'
                textNodes = viewer.parseMinecraftText(input)
                latch.countDown()
            } catch (Exception e) {
                println "ERROR parsing formatting codes: ${e.message}"
                e.printStackTrace()
                latch.countDown()
            }
        } as Runnable)

        and: 'Wait for parsing'
        boolean completed = latch.await(5, TimeUnit.SECONDS)

        then: 'Should create Text nodes with correct formatting'
        completed
        textNodes != null
        textNodes.size() >= 7

        // Bold text
        textNodes[0].text == 'Bold'
        textNodes[0].font.style.contains('Bold') || textNodes[0].font.name.contains('Bold')

        // Underline text (after reset and space)
        def underlineNode = textNodes.find { it.text == 'Underline' }
        underlineNode != null
        underlineNode.isUnderline()

        // Strikethrough text
        def strikeNode = textNodes.find { it.text == 'Strikethrough' }
        strikeNode != null
        strikeNode.isStrikethrough()
    }

    def "parseMinecraftText handles obfuscated text"() {
        given: 'A countdown latch'
        CountDownLatch latch = new CountDownLatch(1)
        BookViewer viewer = null
        List<Text> textNodes = null

        when: 'We parse text with obfuscation code'
        Platform.runLater({
            try {
                viewer = new BookViewer()
                viewer.initializeUI()
                String input = '§kObfuscated text'
                textNodes = viewer.parseMinecraftText(input)
                latch.countDown()
            } catch (Exception e) {
                println "ERROR parsing obfuscated: ${e.message}"
                e.printStackTrace()
                latch.countDown()
            }
        } as Runnable)

        and: 'Wait for parsing'
        boolean completed = latch.await(5, TimeUnit.SECONDS)

        then: 'Should track obfuscated text for animation'
        completed
        textNodes != null
        textNodes.size() == 1
        textNodes[0].userData != null  // Original text stored for randomization
    }

    def "Color codes reset formatting in Minecraft style"() {
        given: 'A countdown latch'
        CountDownLatch latch = new CountDownLatch(1)
        BookViewer viewer = null
        List<Text> textNodes = null

        when: 'We parse bold text followed by color code'
        Platform.runLater({
            try {
                viewer = new BookViewer()
                viewer.initializeUI()
                String input = '§l§nBold Underline§cRed resets formatting'
                textNodes = viewer.parseMinecraftText(input)
                latch.countDown()
            } catch (Exception e) {
                println "ERROR parsing color reset: ${e.message}"
                e.printStackTrace()
                latch.countDown()
            }
        } as Runnable)

        and: 'Wait for parsing'
        boolean completed = latch.await(5, TimeUnit.SECONDS)

        then: 'Color code should reset formatting'
        completed
        textNodes != null
        textNodes.size() == 2

        // First node is bold and underlined
        textNodes[0].text == 'Bold Underline'
        textNodes[0].isUnderline()

        // Second node after color code is not bold/underlined
        textNodes[1].text == 'Red resets formatting'
        textNodes[1].fill == Color.web('#FF5555')  // Red color
        !textNodes[1].isUnderline()  // Formatting reset
    }

    // =========================================================================
    // Search and Filter Tests
    // =========================================================================

    def "Search field filters books by title"() {
        given: 'Multiple books with different titles'
        def booksData = [
            [title: 'Adventure Story', author: 'Alice', pages: ['Content']],
            [title: 'Mystery Novel', author: 'Bob', pages: ['Content']],
            [title: 'Adventure Guide', author: 'Charlie', pages: ['Content']]
        ]
        File booksFile = new File(testFolder, 'search_test.json')
        booksFile.text = new JsonBuilder(booksData).toPrettyString()

        and: 'A countdown latch'
        CountDownLatch latch = new CountDownLatch(1)
        BookViewer viewer = null
        int filteredCount = 0

        when: 'We search for "Adventure"'
        Platform.runLater({
            try {
                viewer = new BookViewer()
                viewer.initializeUI()
                viewer.loadBooks(booksFile)
                viewer.searchField.text = 'Adventure'
                viewer.filterBooks()
                filteredCount = viewer.filteredBooks.size()
                latch.countDown()
            } catch (Exception e) {
                println "ERROR searching books: ${e.message}"
                e.printStackTrace()
                latch.countDown()
            }
        } as Runnable)

        and: 'Wait for search'
        boolean completed = latch.await(5, TimeUnit.SECONDS)

        then: 'Should find 2 books with "Adventure" in title'
        completed
        filteredCount == 2
    }

    def "Search is case-insensitive"() {
        given: 'Books with various titles'
        def booksData = [
            [title: 'UPPERCASE TITLE', author: 'Alice', pages: ['Content']],
            [title: 'lowercase title', author: 'Bob', pages: ['Content']]
        ]
        File booksFile = new File(testFolder, 'case_test.json')
        booksFile.text = new JsonBuilder(booksData).toPrettyString()

        and: 'A countdown latch'
        CountDownLatch latch = new CountDownLatch(1)
        BookViewer viewer = null
        int filteredCount = 0

        when: 'We search for "title" (lowercase)'
        Platform.runLater({
            try {
                viewer = new BookViewer()
                viewer.initializeUI()
                viewer.loadBooks(booksFile)
                viewer.searchField.text = 'title'
                viewer.filterBooks()
                filteredCount = viewer.filteredBooks.size()
                latch.countDown()
            } catch (Exception e) {
                println "ERROR in case test: ${e.message}"
                e.printStackTrace()
                latch.countDown()
            }
        } as Runnable)

        and: 'Wait for search'
        boolean completed = latch.await(5, TimeUnit.SECONDS)

        then: 'Should find both books regardless of case'
        completed
        filteredCount == 2
    }

    def "Author filter filters books correctly"() {
        given: 'Books from multiple authors'
        def booksData = [
            [title: 'Book 1', author: 'Alice', pages: ['Content']],
            [title: 'Book 2', author: 'Bob', pages: ['Content']],
            [title: 'Book 3', author: 'Alice', pages: ['Content']]
        ]
        File booksFile = new File(testFolder, 'author_filter_test.json')
        booksFile.text = new JsonBuilder(booksData).toPrettyString()

        and: 'A countdown latch'
        CountDownLatch latch = new CountDownLatch(1)
        BookViewer viewer = null
        int filteredCount = 0

        when: 'We filter by author "Alice"'
        Platform.runLater({
            try {
                viewer = new BookViewer()
                viewer.initializeUI()
                viewer.loadBooks(booksFile)
                viewer.authorFilterCombo.value = 'Alice'
                viewer.filterBooks()
                filteredCount = viewer.filteredBooks.size()
                latch.countDown()
            } catch (Exception e) {
                println "ERROR in author filter: ${e.message}"
                e.printStackTrace()
                latch.countDown()
            }
        } as Runnable)

        and: 'Wait for filter'
        boolean completed = latch.await(5, TimeUnit.SECONDS)

        then: 'Should show only Alice\'s 2 books'
        completed
        filteredCount == 2
    }

    def "Sort by title works correctly"() {
        given: 'Books with unsorted titles'
        def booksData = [
            [title: 'Zebra Book', author: 'Author', pages: ['Content']],
            [title: 'Apple Book', author: 'Author', pages: ['Content']],
            [title: 'Middle Book', author: 'Author', pages: ['Content']]
        ]
        File booksFile = new File(testFolder, 'sort_test.json')
        booksFile.text = new JsonBuilder(booksData).toPrettyString()

        and: 'A countdown latch'
        CountDownLatch latch = new CountDownLatch(1)
        BookViewer viewer = null
        List<String> sortedTitles = []

        when: 'We sort by title A-Z'
        Platform.runLater({
            try {
                viewer = new BookViewer()
                viewer.initializeUI()
                viewer.loadBooks(booksFile)
                viewer.sortCombo.value = 'Title (A-Z)'
                viewer.filterBooks()
                sortedTitles = viewer.filteredBooks.collect { it.title }
                latch.countDown()
            } catch (Exception e) {
                println "ERROR in sort test: ${e.message}"
                e.printStackTrace()
                latch.countDown()
            }
        } as Runnable)

        and: 'Wait for sort'
        boolean completed = latch.await(5, TimeUnit.SECONDS)

        then: 'Books should be sorted alphabetically'
        completed
        sortedTitles == ['Apple Book', 'Middle Book', 'Zebra Book']
    }

    def "Sort by page count works correctly"() {
        given: 'Books with different page counts'
        def booksData = [
            [title: 'Short', author: 'Author', pages: ['Page 1']],
            [title: 'Long', author: 'Author', pages: ['Page 1', 'Page 2', 'Page 3', 'Page 4', 'Page 5']],
            [title: 'Medium', author: 'Author', pages: ['Page 1', 'Page 2', 'Page 3']]
        ]
        File booksFile = new File(testFolder, 'page_count_sort.json')
        booksFile.text = new JsonBuilder(booksData).toPrettyString()

        and: 'A countdown latch'
        CountDownLatch latch = new CountDownLatch(1)
        BookViewer viewer = null
        List<Integer> pageCounts = []

        when: 'We sort by page count (high to low)'
        Platform.runLater({
            try {
                viewer = new BookViewer()
                viewer.initializeUI()
                viewer.loadBooks(booksFile)
                viewer.sortCombo.value = 'Page Count (High-Low)'
                viewer.filterBooks()
                pageCounts = viewer.filteredBooks.collect { it.pages.size() }
                latch.countDown()
            } catch (Exception e) {
                println "ERROR in page count sort: ${e.message}"
                e.printStackTrace()
                latch.countDown()
            }
        } as Runnable)

        and: 'Wait for sort'
        boolean completed = latch.await(5, TimeUnit.SECONDS)

        then: 'Books should be sorted by page count descending'
        completed
        pageCounts == [5, 3, 1]
    }

    // =========================================================================
    // Edge Case Tests
    // =========================================================================

    def "Empty book (no pages) is handled gracefully"() {
        given: 'A book with empty pages array'
        def booksData = [
            [title: 'Empty Book', author: 'Author', pages: []]
        ]
        File booksFile = new File(testFolder, 'empty_book.json')
        booksFile.text = new JsonBuilder(booksData).toPrettyString()

        and: 'A countdown latch'
        CountDownLatch latch = new CountDownLatch(1)
        BookViewer viewer = null
        String pageLabel = null

        when: 'We select the empty book'
        Platform.runLater({
            try {
                viewer = new BookViewer()
                viewer.initializeUI()
                viewer.loadBooks(booksFile)
                viewer.selectBook(viewer.bookListView.items[0])
                pageLabel = viewer.pageNumberLabel.text
                latch.countDown()
            } catch (Exception e) {
                println "ERROR with empty book: ${e.message}"
                e.printStackTrace()
                latch.countDown()
            }
        } as Runnable)

        and: 'Wait for selection'
        boolean completed = latch.await(5, TimeUnit.SECONDS)

        then: 'Should handle empty book without error'
        completed
        pageLabel.contains('0')  // Should show page 1 of 0 or similar
    }

    def "Single page book displays correctly"() {
        given: 'A book with only one page'
        def booksData = [
            [title: 'Single Page', author: 'Author', pages: ['Only page']]
        ]
        File booksFile = new File(testFolder, 'single_page.json')
        booksFile.text = new JsonBuilder(booksData).toPrettyString()

        and: 'A countdown latch'
        CountDownLatch latch = new CountDownLatch(1)
        BookViewer viewer = null
        boolean nextDisabled = false
        boolean prevDisabled = false

        when: 'We select the single-page book'
        Platform.runLater({
            try {
                viewer = new BookViewer()
                viewer.initializeUI()
                viewer.loadBooks(booksFile)
                viewer.selectBook(viewer.bookListView.items[0])
                nextDisabled = viewer.nextButton.isDisabled()
                prevDisabled = viewer.prevButton.isDisabled()
                latch.countDown()
            } catch (Exception e) {
                println "ERROR with single page: ${e.message}"
                e.printStackTrace()
                latch.countDown()
            }
        } as Runnable)

        and: 'Wait for selection'
        boolean completed = latch.await(5, TimeUnit.SECONDS)

        then: 'Both navigation buttons should be disabled'
        completed
        nextDisabled == true
        prevDisabled == true
    }

    def "Book with many pages (50+) navigates correctly"() {
        given: 'A book with 50 pages'
        def pages = (1..50).collect { "Page ${it} content" }
        def booksData = [
            [title: 'Long Book', author: 'Author', pages: pages]
        ]
        File booksFile = new File(testFolder, 'long_book.json')
        booksFile.text = new JsonBuilder(booksData).toPrettyString()

        and: 'A countdown latch'
        CountDownLatch latch = new CountDownLatch(1)
        BookViewer viewer = null
        int finalPageIndex = 0

        when: 'We navigate to the end'
        Platform.runLater({
            try {
                viewer = new BookViewer()
                viewer.initializeUI()
                viewer.loadBooks(booksFile)
                viewer.selectBook(viewer.bookListView.items[0])

                // Navigate to end (25 clicks for 50 pages)
                while (!viewer.nextButton.isDisabled()) {
                    viewer.nextPage()
                }
                finalPageIndex = viewer.currentPageIndex

                latch.countDown()
            } catch (Exception e) {
                println "ERROR with long book: ${e.message}"
                e.printStackTrace()
                latch.countDown()
            }
        } as Runnable)

        and: 'Wait for navigation'
        boolean completed = latch.await(10, TimeUnit.SECONDS)  // Longer timeout

        then: 'Should navigate to last page spread'
        completed
        finalPageIndex == 48  // Last spread starts at page 48 (pages 49-50)
    }

    def "Book with null author is handled gracefully"() {
        given: 'A book with null author'
        def booksData = [
            [title: 'No Author', author: null, pages: ['Content']]
        ]
        File booksFile = new File(testFolder, 'null_author.json')
        booksFile.text = new JsonBuilder(booksData).toPrettyString()

        and: 'A countdown latch'
        CountDownLatch latch = new CountDownLatch(1)
        BookViewer viewer = null
        String authorText = null

        when: 'We select the book'
        Platform.runLater({
            try {
                viewer = new BookViewer()
                viewer.initializeUI()
                viewer.loadBooks(booksFile)
                viewer.selectBook(viewer.bookListView.items[0])
                authorText = viewer.bookAuthorLabel.text
                latch.countDown()
            } catch (Exception e) {
                println "ERROR with null author: ${e.message}"
                e.printStackTrace()
                latch.countDown()
            }
        } as Runnable)

        and: 'Wait for selection'
        boolean completed = latch.await(5, TimeUnit.SECONDS)

        then: 'Should display "Unknown" as author'
        completed
        authorText == 'by Unknown'
    }

    // =========================================================================
    // Obfuscation Animation Tests
    // =========================================================================

    def "Obfuscated text animation starts and stops"() {
        given: 'A book with obfuscated text'
        def booksData = [
            [title: 'Secret', author: 'Author', pages: ['§kSecret text']]
        ]
        File booksFile = new File(testFolder, 'obfuscated.json')
        booksFile.text = new JsonBuilder(booksData).toPrettyString()

        and: 'A countdown latch'
        CountDownLatch latch = new CountDownLatch(1)
        BookViewer viewer = null
        int obfuscatedCount = 0

        when: 'We select the book with obfuscated text'
        Platform.runLater({
            try {
                viewer = new BookViewer()
                viewer.initializeUI()
                viewer.loadBooks(booksFile)
                viewer.selectBook(viewer.bookListView.items[0])
                obfuscatedCount = viewer.obfuscatedTexts.size()
                latch.countDown()
            } catch (Exception e) {
                println "ERROR with obfuscation: ${e.message}"
                e.printStackTrace()
                latch.countDown()
            }
        } as Runnable)

        and: 'Wait for selection'
        boolean completed = latch.await(5, TimeUnit.SECONDS)

        then: 'Obfuscated texts should be tracked for animation'
        completed
        obfuscatedCount > 0
        viewer.obfuscationTimeline != null
    }

    def "randomizeText preserves spaces and newlines"() {
        given: 'A countdown latch'
        CountDownLatch latch = new CountDownLatch(1)
        BookViewer viewer = null
        String randomized = null
        String input = 'Hello World\nNew line'

        when: 'We randomize text with spaces'
        Platform.runLater({
            try {
                viewer = new BookViewer()
                viewer.initializeUI()
                randomized = viewer.randomizeText(input)
                latch.countDown()
            } catch (Exception e) {
                println "ERROR randomizing: ${e.message}"
                e.printStackTrace()
                latch.countDown()
            }
        } as Runnable)

        and: 'Wait for randomization'
        boolean completed = latch.await(5, TimeUnit.SECONDS)

        then: 'Spaces and newlines should be preserved'
        completed
        randomized.charAt(5) == ' '  // Space preserved
        randomized.charAt(11) == '\n'  // Newline preserved
        randomized.length() == input.length()
    }

    // =========================================================================
    // Error Handling Tests
    // =========================================================================

    def "Malformed JSON is handled gracefully"() {
        given: 'A malformed JSON file'
        File booksFile = new File(testFolder, 'malformed.json')
        booksFile.text = '{ invalid json }'

        and: 'A countdown latch'
        CountDownLatch latch = new CountDownLatch(1)
        BookViewer viewer = null
        boolean loadFailed = false

        when: 'We try to load the malformed file'
        Platform.runLater({
            try {
                viewer = new BookViewer()
                viewer.initializeUI()
                viewer.loadBooks(booksFile)
                // If we get here, check if books are empty
                loadFailed = viewer.allBooks.empty
                latch.countDown()
            } catch (Exception e) {
                // Exception is expected
                loadFailed = true
                println "Expected error loading malformed JSON: ${e.message}"
                latch.countDown()
            }
        } as Runnable)

        and: 'Wait for loading attempt'
        boolean completed = latch.await(5, TimeUnit.SECONDS)

        then: 'Should fail gracefully without crashing'
        completed
        loadFailed || viewer.allBooks.empty
    }

    def "Non-existent file is handled gracefully"() {
        given: 'A non-existent file'
        File booksFile = new File(testFolder, 'nonexistent.json')

        and: 'A countdown latch'
        CountDownLatch latch = new CountDownLatch(1)
        BookViewer viewer = null
        boolean loadFailed = false

        when: 'We try to load the non-existent file'
        Platform.runLater({
            try {
                viewer = new BookViewer()
                viewer.initializeUI()
                viewer.loadBooks(booksFile)
                loadFailed = viewer.allBooks.empty
                latch.countDown()
            } catch (Exception e) {
                loadFailed = true
                println "Expected error loading nonexistent file: ${e.message}"
                latch.countDown()
            }
        } as Runnable)

        and: 'Wait for loading attempt'
        boolean completed = latch.await(5, TimeUnit.SECONDS)

        then: 'Should fail gracefully'
        completed
        loadFailed || viewer.allBooks.empty
    }
}

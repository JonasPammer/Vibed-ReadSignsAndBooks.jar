import groovy.json.JsonBuilder
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import spock.lang.Specification
import spock.lang.TempDir
import viewers.GlobalSearch

import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Comprehensive tests for GlobalSearch component.
 *
 * Tests search functionality, query parsing, result ranking, filtering,
 * and edge case handling.
 */
class GlobalSearchSpec extends Specification {

    @TempDir
    Path tempDir

    File outputFolder
    OutputViewerModel model
    GlobalSearch globalSearch
    List<GlobalSearch.SearchResult> capturedResults = []

    def setupSpec() {
        // Initialize JavaFX toolkit (required for JavaFX components)
        new JFXPanel()
    }

    def setup() {
        outputFolder = tempDir.toFile()
        model = new OutputViewerModel()
        capturedResults.clear()
    }

    def cleanup() {
        model?.close()
    }

    // =========================================================================
    // Instantiation and Initialization Tests
    // =========================================================================

    def "GlobalSearch can be instantiated with model and callback"() {
        given: 'A model and callback closure'
        def callback = { result -> capturedResults << result }

        when: 'Creating GlobalSearch'
        def search = createGlobalSearchOnFxThread(callback)

        then: 'Instance is created successfully'
        search != null
        search instanceof GlobalSearch
    }

    def "GlobalSearch can be instantiated with null callback"() {
        when: 'Creating GlobalSearch with null callback'
        def search = createGlobalSearchOnFxThread(null)

        then: 'Instance is created successfully'
        search != null
        notThrown(Exception)
    }

    // =========================================================================
    // Search Query Parsing Tests
    // =========================================================================

    def "Regular text search finds matches in books"() {
        given: 'Books with searchable content'
        createTestBooks([
            [title: 'Diamond Guide', author: 'Steve', pages: ['How to mine diamonds']],
            [title: 'Redstone Basics', author: 'Alex', pages: ['Redstone tutorial']]
        ])
        model.loadFromFolder(outputFolder)

        and: 'GlobalSearch instance'
        def search = createGlobalSearchOnFxThread({ result -> capturedResults << result })

        when: 'Searching for "diamond"'
        performSearchOnFxThread(search, 'diamond')

        then: 'Book with "Diamond" in title is found'
        capturedResults.any { it.type == 'book' && it.displayText == 'Diamond Guide' }
    }

    def "Search is case-insensitive"() {
        given: 'Books with mixed-case titles'
        createTestBooks([
            [title: 'UPPERCASE', author: 'Author1', pages: ['Content']],
            [title: 'lowercase', author: 'Author2', pages: ['Content']],
            [title: 'MixedCase', author: 'Author3', pages: ['Content']]
        ])
        model.loadFromFolder(outputFolder)

        and: 'GlobalSearch instance'
        def search = createGlobalSearchOnFxThread({ result -> capturedResults << result })

        when: 'Searching with lowercase query'
        performSearchOnFxThread(search, 'uppercase')

        then: 'Finds UPPERCASE book'
        capturedResults.any { it.displayText == 'UPPERCASE' }

        when: 'Searching with uppercase query'
        capturedResults.clear()
        performSearchOnFxThread(search, 'LOWERCASE')

        then: 'Finds lowercase book'
        capturedResults.any { it.displayText == 'lowercase' }

        when: 'Searching with mixed case query'
        capturedResults.clear()
        performSearchOnFxThread(search, 'MiXeDcAsE')

        then: 'Finds MixedCase book'
        capturedResults.any { it.displayText == 'MixedCase' }
    }

    def "Minimum query length enforced (2 characters)"() {
        given: 'Books in model'
        createTestBooks([
            [title: 'Test Book', author: 'Author', pages: ['Content']]
        ])
        model.loadFromFolder(outputFolder)

        and: 'GlobalSearch instance'
        def search = createGlobalSearchOnFxThread({ result -> capturedResults << result })

        when: 'Searching with 1 character'
        performSearchOnFxThread(search, 'a')

        then: 'No search is performed'
        capturedResults.empty

        when: 'Searching with 2 characters'
        performSearchOnFxThread(search, 'te')

        then: 'Search is performed'
        !capturedResults.empty
    }

    def "Empty query clears results"() {
        given: 'Books in model'
        createTestBooks([
            [title: 'Test Book', author: 'Author', pages: ['Content']]
        ])
        model.loadFromFolder(outputFolder)

        and: 'GlobalSearch instance with initial results'
        def search = createGlobalSearchOnFxThread({ result -> capturedResults << result })
        performSearchOnFxThread(search, 'test')

        when: 'Clearing search with empty string'
        runOnFxThreadAndWait {
            search.clearSearch()
        }

        then: 'Results are cleared'
        runOnFxThreadAndGet { search.resultsList.items.empty }
    }

    // =========================================================================
    // Search Across Different Data Types Tests
    // =========================================================================

    def "Search finds matches in books by title"() {
        given: 'Books with distinctive titles'
        createTestBooks([
            [title: 'Diamond Guide', author: 'Steve', pages: ['Content']],
            [title: 'Emerald Tutorial', author: 'Alex', pages: ['Content']]
        ])
        model.loadFromFolder(outputFolder)

        and: 'GlobalSearch instance'
        def search = createGlobalSearchOnFxThread({ result -> capturedResults << result })

        when: 'Searching for "diamond"'
        performSearchOnFxThread(search, 'diamond')

        then: 'Only Diamond Guide is found'
        capturedResults.size() == 1
        capturedResults[0].type == 'book'
        capturedResults[0].displayText == 'Diamond Guide'
    }

    def "Search finds matches in books by author"() {
        given: 'Books with distinctive authors'
        createTestBooks([
            [title: 'Book 1', author: 'Notch', pages: ['Content']],
            [title: 'Book 2', author: 'Jeb', pages: ['Content']]
        ])
        model.loadFromFolder(outputFolder)

        and: 'GlobalSearch instance'
        def search = createGlobalSearchOnFxThread({ result -> capturedResults << result })

        when: 'Searching for "notch"'
        performSearchOnFxThread(search, 'notch')

        then: 'Book by Notch is found'
        capturedResults.any { it.subtitle.contains('Notch') }
    }

    def "Search finds matches in book pages content"() {
        given: 'Books with distinctive content'
        createTestBooks([
            [title: 'Book 1', author: 'Author', pages: ['This book contains unique keyword']],
            [title: 'Book 2', author: 'Author', pages: ['Generic content']]
        ])
        model.loadFromFolder(outputFolder)

        and: 'GlobalSearch instance'
        def search = createGlobalSearchOnFxThread({ result -> capturedResults << result })

        when: 'Searching for text in pages'
        performSearchOnFxThread(search, 'unique')

        then: 'Book with matching content is found'
        capturedResults.any { it.displayText == 'Book 1' }
    }

    def "Search finds matches in signs"() {
        given: 'Signs with distinctive text'
        createTestSigns([
            [dimension: 'overworld', x: 100, y: 64, z: 200, line1: 'Welcome', line2: 'to spawn', line3: '', line4: ''],
            [dimension: 'nether', x: 12, y: 64, z: 25, line1: 'Nether Hub', line2: '', line3: '', line4: '']
        ])
        model.loadFromFolder(outputFolder)

        and: 'GlobalSearch instance'
        def search = createGlobalSearchOnFxThread({ result -> capturedResults << result })

        when: 'Searching for "welcome"'
        performSearchOnFxThread(search, 'welcome')

        then: 'Sign with "Welcome" is found'
        capturedResults.any { it.type == 'sign' && it.displayText.contains('Welcome') }
    }

    def "Search finds matches in custom names"() {
        given: 'Custom named items'
        createTestCustomNames([
            [name: 'Excalibur', type: 'item', item_id: 'minecraft:diamond_sword', x: 100, y: 64, z: 200],
            [name: 'Steve', type: 'entity', entity_type: 'minecraft:player']
        ])
        model.loadFromFolder(outputFolder)

        and: 'GlobalSearch instance'
        def search = createGlobalSearchOnFxThread({ result -> capturedResults << result })

        when: 'Searching for "excalibur"'
        performSearchOnFxThread(search, 'excalibur')

        then: 'Custom named item is found'
        capturedResults.any { it.type == 'custom_name' && it.displayText == 'Excalibur' }
    }

    def "Search finds matches in portals"() {
        given: 'Portals in different dimensions'
        createTestPortals([
            [dimension: 'overworld', portal_id: 1, center_x: 100, center_y: 66, center_z: 200, block_count: 20],
            [dimension: 'nether', portal_id: 2, center_x: 12, center_y: 66, center_z: 25, block_count: 20]
        ])
        model.loadFromFolder(outputFolder)

        and: 'GlobalSearch instance'
        def search = createGlobalSearchOnFxThread({ result -> capturedResults << result })

        when: 'Searching for "nether"'
        performSearchOnFxThread(search, 'nether')

        then: 'Nether portal is found'
        capturedResults.any { it.type == 'portal' && it.subtitle.contains('nether') }
    }

    def "Search across all data types simultaneously"() {
        given: 'Multiple data types with matching keyword'
        createTestBooks([
            [title: 'Test Book', author: 'Author', pages: ['Content']]
        ])
        createTestSigns([
            [dimension: 'overworld', x: 100, y: 64, z: 200, line1: 'Test Sign', line2: '', line3: '', line4: '']
        ])
        createTestCustomNames([
            [name: 'Test Item', type: 'item', item_id: 'minecraft:diamond_sword']
        ])
        model.loadFromFolder(outputFolder)

        and: 'GlobalSearch instance'
        def search = createGlobalSearchOnFxThread({ result -> capturedResults << result })

        when: 'Searching for "test"'
        performSearchOnFxThread(search, 'test')

        then: 'Results from all data types are found'
        capturedResults.any { it.type == 'book' }
        capturedResults.any { it.type == 'sign' }
        capturedResults.any { it.type == 'custom_name' }
    }

    // =========================================================================
    // Result Ranking and Sorting Tests
    // =========================================================================

    def "Exact match ranks highest (score 100)"() {
        given: 'Books with various matches'
        createTestBooks([
            [title: 'diamond', author: 'Author1', pages: ['Content']],           // Exact match
            [title: 'Diamond Guide', author: 'Author2', pages: ['Content']],     // Starts with
            [title: 'About diamond', author: 'Author3', pages: ['Content']]      // Contains
        ])
        model.loadFromFolder(outputFolder)

        and: 'GlobalSearch instance'
        def search = createGlobalSearchOnFxThread({ result -> capturedResults << result })

        when: 'Searching for "diamond"'
        performSearchOnFxThread(search, 'diamond')

        then: 'Exact match has highest score'
        def exactMatch = capturedResults.find { it.displayText == 'diamond' }
        exactMatch != null
        exactMatch.score == 100
    }

    def "Starts with match ranks high (score 80)"() {
        given: 'Book starting with query'
        createTestBooks([
            [title: 'Diamond Guide', author: 'Author', pages: ['Content']]
        ])
        model.loadFromFolder(outputFolder)

        and: 'GlobalSearch instance'
        def search = createGlobalSearchOnFxThread({ result -> capturedResults << result })

        when: 'Searching for "diamond"'
        performSearchOnFxThread(search, 'diamond')

        then: 'Result has starts-with score'
        capturedResults[0].score == 80
    }

    def "Contains match ranks lower (score 50)"() {
        given: 'Book containing query'
        createTestBooks([
            [title: 'The diamond guide', author: 'Author', pages: ['Content']]
        ])
        model.loadFromFolder(outputFolder)

        and: 'GlobalSearch instance'
        def search = createGlobalSearchOnFxThread({ result -> capturedResults << result })

        when: 'Searching for "diamond"'
        performSearchOnFxThread(search, 'diamond')

        then: 'Result has contains score'
        capturedResults[0].score == 50
    }

    def "Results sorted by relevance score descending"() {
        given: 'Books with different match types'
        createTestBooks([
            [title: 'Contains test here', author: 'Author1', pages: ['Content']],  // Score: 50
            [title: 'test', author: 'Author2', pages: ['Content']],                // Score: 100
            [title: 'Test Guide', author: 'Author3', pages: ['Content']]           // Score: 80
        ])
        model.loadFromFolder(outputFolder)

        and: 'GlobalSearch instance'
        def search = createGlobalSearchOnFxThread({ result -> capturedResults << result })

        when: 'Searching for "test"'
        performSearchOnFxThread(search, 'test')

        then: 'Results are sorted by score descending'
        capturedResults[0].displayText == 'test'         // Score 100
        capturedResults[1].displayText == 'Test Guide'   // Score 80
        capturedResults[2].displayText == 'Contains test here'  // Score 50
    }

    def "Fuzzy match ranks lowest but still matches (score 10)"() {
        given: 'Book where query characters appear in order but non-contiguous'
        createTestBooks([
            [title: 'DxIxAxMxOxNxD', author: 'Author', pages: ['Content']]  // 'd', 'i', 'a', 'm', 'o', 'n', 'd' in order
        ])
        model.loadFromFolder(outputFolder)

        and: 'GlobalSearch instance'
        def search = createGlobalSearchOnFxThread({ result -> capturedResults << result })

        when: 'Searching for "diamond"'
        performSearchOnFxThread(search, 'diamond')

        then: 'Fuzzy match is found with lowest score'
        capturedResults.size() == 1
        capturedResults[0].score == 10
    }

    // =========================================================================
    // Filter Combinations Tests
    // =========================================================================

    def "Multiple filters can be combined in search"() {
        given: 'Multiple data types'
        createTestBooks([
            [title: 'Diamond Book', author: 'Steve', pages: ['Content']]
        ])
        createTestSigns([
            [dimension: 'overworld', x: 100, y: 64, z: 200, line1: 'Diamond Sign', line2: '', line3: '', line4: '']
        ])
        model.loadFromFolder(outputFolder)

        and: 'GlobalSearch instance'
        def search = createGlobalSearchOnFxThread({ result -> capturedResults << result })

        when: 'Searching for "diamond"'
        performSearchOnFxThread(search, 'diamond')

        then: 'Results from multiple types match'
        capturedResults.size() == 2
        capturedResults.any { it.type == 'book' }
        capturedResults.any { it.type == 'sign' }
    }

    def "Search matches author field in books"() {
        given: 'Books with distinctive authors'
        createTestBooks([
            [title: 'Guide 1', author: 'Steve', pages: ['Content']],
            [title: 'Guide 2', author: 'Alex', pages: ['Content']]
        ])
        model.loadFromFolder(outputFolder)

        and: 'GlobalSearch instance'
        def search = createGlobalSearchOnFxThread({ result -> capturedResults << result })

        when: 'Searching for author name'
        performSearchOnFxThread(search, 'steve')

        then: 'Book by Steve is found'
        capturedResults.size() == 1
        capturedResults[0].subtitle.contains('Steve')
    }

    def "Search matches item_id in custom names"() {
        given: 'Custom names with specific item IDs'
        createTestCustomNames([
            [name: 'Sword 1', type: 'item', item_id: 'minecraft:diamond_sword'],
            [name: 'Pickaxe 1', type: 'item', item_id: 'minecraft:diamond_pickaxe']
        ])
        model.loadFromFolder(outputFolder)

        and: 'GlobalSearch instance'
        def search = createGlobalSearchOnFxThread({ result -> capturedResults << result })

        when: 'Searching for item ID'
        performSearchOnFxThread(search, 'pickaxe')

        then: 'Custom name with matching item_id is found'
        capturedResults.size() == 1
        capturedResults[0].subtitle.contains('pickaxe')
    }

    // =========================================================================
    // Empty Search Behavior Tests
    // =========================================================================

    def "Empty data model returns no results"() {
        given: 'Empty model'
        // No data files created

        and: 'GlobalSearch instance'
        def search = createGlobalSearchOnFxThread({ result -> capturedResults << result })

        when: 'Searching'
        performSearchOnFxThread(search, 'anything')

        then: 'No results found'
        capturedResults.empty
    }

    def "No matches returns empty results"() {
        given: 'Books without matching content'
        createTestBooks([
            [title: 'Book A', author: 'Author', pages: ['Content']]
        ])
        model.loadFromFolder(outputFolder)

        and: 'GlobalSearch instance'
        def search = createGlobalSearchOnFxThread({ result -> capturedResults << result })

        when: 'Searching for non-existent term'
        performSearchOnFxThread(search, 'nonexistent')

        then: 'No results found'
        capturedResults.empty
    }

    def "Whitespace-only query treated as empty"() {
        given: 'Books in model'
        createTestBooks([
            [title: 'Test Book', author: 'Author', pages: ['Content']]
        ])
        model.loadFromFolder(outputFolder)

        and: 'GlobalSearch instance'
        def search = createGlobalSearchOnFxThread({ result -> capturedResults << result })

        when: 'Searching with whitespace only'
        performSearchOnFxThread(search, '   ')

        then: 'No search is performed'
        capturedResults.empty
    }

    // =========================================================================
    // Edge Cases and Special Characters Tests
    // =========================================================================

    def "Search handles special characters in query"() {
        given: 'Books with special characters'
        createTestBooks([
            [title: 'Book: Special!', author: 'Author', pages: ['Content']]
        ])
        model.loadFromFolder(outputFolder)

        and: 'GlobalSearch instance'
        def search = createGlobalSearchOnFxThread({ result -> capturedResults << result })

        when: 'Searching for text with special characters'
        performSearchOnFxThread(search, 'special!')

        then: 'Match is found'
        capturedResults.any { it.displayText.contains('Special!') }
    }

    def "Search handles Unicode characters"() {
        given: 'Books with Unicode'
        createTestBooks([
            [title: 'Book with 日本語', author: 'Author', pages: ['Content']]
        ])
        model.loadFromFolder(outputFolder)

        and: 'GlobalSearch instance'
        def search = createGlobalSearchOnFxThread({ result -> capturedResults << result })

        when: 'Searching for Unicode text'
        performSearchOnFxThread(search, '日本語')

        then: 'Match is found'
        capturedResults.any { it.displayText.contains('日本語') }
    }

    def "Search handles very long query strings"() {
        given: 'Books with normal titles'
        createTestBooks([
            [title: 'Short Title', author: 'Author', pages: ['Content']]
        ])
        model.loadFromFolder(outputFolder)

        and: 'GlobalSearch instance'
        def search = createGlobalSearchOnFxThread({ result -> capturedResults << result })

        when: 'Searching with very long query'
        def longQuery = 'a' * 1000
        performSearchOnFxThread(search, longQuery)

        then: 'Search completes without error'
        notThrown(Exception)
        // No results expected since query doesn't match
        capturedResults.empty
    }

    def "Search handles null or missing fields gracefully"() {
        given: 'Books with missing fields'
        createTestBooks([
            [title: null, author: 'Author', pages: ['Content']],
            [title: 'Title', author: null, pages: null]
        ])
        model.loadFromFolder(outputFolder)

        and: 'GlobalSearch instance'
        def search = createGlobalSearchOnFxThread({ result -> capturedResults << result })

        when: 'Searching'
        performSearchOnFxThread(search, 'author')

        then: 'Search completes without error'
        notThrown(Exception)
        capturedResults.any { it.subtitle.contains('Author') }
    }

    def "Search result limit prevents UI freeze (max 50 results)"() {
        given: '100 books all matching the query'
        def books = (1..100).collect { i ->
            [title: "Test Book ${i}", author: 'Author', pages: ['Content']]
        }
        createTestBooks(books)
        model.loadFromFolder(outputFolder)

        and: 'GlobalSearch instance'
        def search = createGlobalSearchOnFxThread({ result -> capturedResults << result })

        when: 'Searching for "test"'
        performSearchOnFxThread(search, 'test')

        then: 'Results are limited to 50'
        capturedResults.size() == 50
    }

    def "Search handles regex-special characters safely"() {
        given: 'Books with regex metacharacters'
        createTestBooks([
            [title: 'Book [Test]', author: 'Author', pages: ['Content']],
            [title: 'Book (Test)', author: 'Author', pages: ['Content']],
            [title: 'Book .* Test', author: 'Author', pages: ['Content']]
        ])
        model.loadFromFolder(outputFolder)

        and: 'GlobalSearch instance'
        def search = createGlobalSearchOnFxThread({ result -> capturedResults << result })

        when: 'Searching for regex metacharacters'
        performSearchOnFxThread(search, '[test]')

        then: 'Search treats as literal characters'
        notThrown(Exception)
        capturedResults.any { it.displayText.contains('[Test]') }
    }

    def "Multiple searches update results correctly"() {
        given: 'Books with different content'
        createTestBooks([
            [title: 'Diamond Book', author: 'Author', pages: ['Content']],
            [title: 'Emerald Book', author: 'Author', pages: ['Content']]
        ])
        model.loadFromFolder(outputFolder)

        and: 'GlobalSearch instance'
        def search = createGlobalSearchOnFxThread({ result -> capturedResults << result })

        when: 'First search'
        performSearchOnFxThread(search, 'diamond')

        then: 'Diamond results found'
        capturedResults.any { it.displayText == 'Diamond Book' }

        when: 'Second search'
        capturedResults.clear()
        performSearchOnFxThread(search, 'emerald')

        then: 'Emerald results found, diamond results replaced'
        capturedResults.any { it.displayText == 'Emerald Book' }
        !capturedResults.any { it.displayText == 'Diamond Book' }
    }

    def "Search excludes header results from captured results"() {
        given: 'Books and signs'
        createTestBooks([
            [title: 'Test Book', author: 'Author', pages: ['Content']]
        ])
        createTestSigns([
            [dimension: 'overworld', x: 100, y: 64, z: 200, line1: 'Test', line2: '', line3: '', line4: '']
        ])
        model.loadFromFolder(outputFolder)

        and: 'GlobalSearch instance'
        def search = createGlobalSearchOnFxThread({ result -> capturedResults << result })

        when: 'Searching'
        performSearchOnFxThread(search, 'test')

        then: 'Captured results do not include headers'
        capturedResults.every { it.type != 'header' }
    }

    // =========================================================================
    // Programmatic Control Tests
    // =========================================================================

    def "focusSearch() focuses the search field"() {
        given: 'GlobalSearch instance'
        def search = createGlobalSearchOnFxThread({ result -> capturedResults << result })

        when: 'Calling focusSearch()'
        runOnFxThreadAndWait {
            search.focusSearch()
        }

        then: 'Search field is focused'
        runOnFxThreadAndGet { search.searchField.focused }
    }

    def "clearSearch() clears query and results"() {
        given: 'Books in model'
        createTestBooks([
            [title: 'Test Book', author: 'Author', pages: ['Content']]
        ])
        model.loadFromFolder(outputFolder)

        and: 'GlobalSearch instance with search performed'
        def search = createGlobalSearchOnFxThread({ result -> capturedResults << result })
        performSearchOnFxThread(search, 'test')

        when: 'Calling clearSearch()'
        runOnFxThreadAndWait {
            search.clearSearch()
        }

        then: 'Query and results are cleared'
        runOnFxThreadAndGet { search.searchField.text == '' }
        runOnFxThreadAndGet { search.resultsList.items.empty }
    }

    def "updateModel() replaces model and clears search"() {
        given: 'Original model with books'
        createTestBooks([
            [title: 'Old Book', author: 'Author', pages: ['Content']]
        ])
        model.loadFromFolder(outputFolder)

        and: 'GlobalSearch instance with search performed'
        def search = createGlobalSearchOnFxThread({ result -> capturedResults << result })
        performSearchOnFxThread(search, 'old')

        when: 'Creating new model and updating'
        File newFolder = new File(tempDir.toFile(), 'new')
        newFolder.mkdirs()
        def newBooksData = [books: [[title: 'New Book', author: 'Author', pages: ['Content']]]]
        new File(newFolder, 'all_books_stendhal.json').text = new JsonBuilder(newBooksData).toPrettyString()
        def newModel = new OutputViewerModel()
        newModel.loadFromFolder(newFolder)

        runOnFxThreadAndWait {
            search.updateModel(newModel)
        }

        then: 'Model is updated and search is cleared'
        runOnFxThreadAndGet { search.searchField.text == '' }
        runOnFxThreadAndGet { search.resultsList.items.empty }

        when: 'Searching again'
        performSearchOnFxThread(search, 'new')

        then: 'New model data is searched'
        capturedResults.any { it.displayText == 'New Book' }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * Create GlobalSearch instance on JavaFX thread.
     */
    private GlobalSearch createGlobalSearchOnFxThread(Closure callback) {
        GlobalSearch search = null
        CountDownLatch latch = new CountDownLatch(1)
        Platform.runLater {
            search = new GlobalSearch(model, callback)
            latch.countDown()
        }
        latch.await(5, TimeUnit.SECONDS)
        return search
    }

    /**
     * Perform search on JavaFX thread and wait for completion.
     */
    private void performSearchOnFxThread(GlobalSearch search, String query) {
        CountDownLatch latch = new CountDownLatch(1)
        Platform.runLater {
            search.searchField.text = query
        }
        // Wait for search to complete (debounce + execution)
        Thread.sleep(500)
        // Trigger search immediately by simulating input
        Platform.runLater {
            search.handleSearchInput(query)
            latch.countDown()
        }
        latch.await(5, TimeUnit.SECONDS)
        // Additional wait for background search thread
        Thread.sleep(1000)
    }

    /**
     * Run code on JavaFX thread and wait for completion.
     */
    private void runOnFxThreadAndWait(Closure closure) {
        CountDownLatch latch = new CountDownLatch(1)
        Platform.runLater {
            closure()
            latch.countDown()
        }
        latch.await(5, TimeUnit.SECONDS)
    }

    /**
     * Run code on JavaFX thread and return result.
     */
    private <T> T runOnFxThreadAndGet(Closure<T> closure) {
        T result = null
        CountDownLatch latch = new CountDownLatch(1)
        Platform.runLater {
            result = closure()
            latch.countDown()
        }
        latch.await(5, TimeUnit.SECONDS)
        return result
    }

    /**
     * Create test books JSON file.
     */
    private void createTestBooks(List<Map> books) {
        def booksData = [books: books]
        File booksFile = new File(outputFolder, 'all_books_stendhal.json')
        booksFile.text = new JsonBuilder(booksData).toPrettyString()
    }

    /**
     * Create test signs CSV file.
     */
    private void createTestSigns(List<Map> signs) {
        File signsFile = new File(outputFolder, 'all_signs.csv')
        def csvLines = ['dimension,x,y,z,line1,line2,line3,line4']
        signs.each { sign ->
            csvLines << "${sign.dimension},${sign.x},${sign.y},${sign.z},\"${sign.line1}\",\"${sign.line2}\",\"${sign.line3}\",\"${sign.line4}\""
        }
        signsFile.text = csvLines.join('\n')
    }

    /**
     * Create test custom names JSON file.
     */
    private void createTestCustomNames(List<Map> customNames) {
        File customNamesFile = new File(outputFolder, 'custom_names.json')
        customNamesFile.text = new JsonBuilder(customNames).toPrettyString()
    }

    /**
     * Create test portals JSON file.
     */
    private void createTestPortals(List<Map> portals) {
        def portalsData = portals.collect { portal ->
            [
                dimension: portal.dimension,
                portal_id: portal.portal_id,
                anchor: [x: portal.center_x as int, y: portal.center_y as int - 2, z: portal.center_z as int],
                center: [x: portal.center_x, y: portal.center_y, z: portal.center_z],
                size: [width: 4, height: 5],
                axis: 'z',
                block_count: portal.block_count
            ]
        }
        File portalsFile = new File(outputFolder, 'portals.json')
        portalsFile.text = new JsonBuilder(portalsData).toPrettyString()
    }
}

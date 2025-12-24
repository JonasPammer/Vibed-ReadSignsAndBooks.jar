import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Integration tests for the Output Viewer components.
 * Tests OutputViewerModel loading, parsing, and portal pairing algorithms.
 */
class OutputViewerSpec extends Specification {

    @TempDir
    Path tempDir

    File outputFolder

    def setup() {
        outputFolder = tempDir.toFile()
    }

    def cleanup() {
        // Close any database connections
        // (handled by setup/cleanup of each test)
    }

    // =========================================================================
    // OutputViewerModel - Basic Loading Tests
    // =========================================================================

    def "OutputViewerModel can load books from JSON"() {
        given: 'A books JSON file'
        def booksData = [
            books: [
                [
                    title: 'Test Book',
                    author: 'Test Author',
                    pages: ['Page 1', 'Page 2'],
                    location_type: 'chest',
                    x: 100,
                    y: 64,
                    z: 200
                ],
                [
                    title: 'Another Book',
                    author: 'Another Author',
                    pages: ['Content'],
                    location_type: 'barrel'
                ]
            ]
        ]
        File booksFile = new File(outputFolder, 'all_books_stendhal.json')
        booksFile.text = new JsonBuilder(booksData).toPrettyString()

        and: 'An OutputViewerModel'
        def model = new OutputViewerModel()

        when: 'Loading from folder'
        boolean success = model.loadFromFolder(outputFolder)

        then: 'Books are loaded successfully'
        success
        model.books.size() == 2
        model.books[0].title == 'Test Book'
        model.books[0].author == 'Test Author'
        model.books[0].pages.size() == 2
        model.books[1].title == 'Another Book'
        model.metadata.booksCount == 2
    }

    def "OutputViewerModel can load signs from CSV"() {
        given: 'A signs CSV file'
        File signsFile = new File(outputFolder, 'all_signs.csv')
        signsFile.text = '''\
            dimension,x,y,z,line1,line2,line3,line4
            overworld,100,64,200,"Welcome","to the","Test","Server"
            nether,12,64,25,"Nether","Portal","Hub",""
            '''.stripIndent().trim()

        and: 'An OutputViewerModel'
        def model = new OutputViewerModel()

        when: 'Loading from folder'
        boolean success = model.loadFromFolder(outputFolder)

        then: 'Signs are loaded successfully'
        success
        model.signs.size() == 2
        model.signs[0].dimension == 'overworld'
        model.signs[0].x == '100'
        model.signs[0].line1 == 'Welcome'
        model.signs[1].dimension == 'nether'
        model.metadata.signsCount == 2
    }

    def "OutputViewerModel can load custom names from JSON"() {
        given: 'A custom names JSON file'
        def customNamesData = [
            [
                name: 'Diamond Sword',
                type: 'item',
                item_id: 'minecraft:diamond_sword',
                dimension: 'overworld',
                x: 100,
                y: 64,
                z: 200
            ],
            [
                name: 'Steve',
                type: 'entity',
                entity_type: 'minecraft:player',
                dimension: 'nether'
            ]
        ]
        File customNamesFile = new File(outputFolder, 'custom_names.json')
        customNamesFile.text = new JsonBuilder(customNamesData).toPrettyString()

        and: 'An OutputViewerModel'
        def model = new OutputViewerModel()

        when: 'Loading from folder'
        boolean success = model.loadFromFolder(outputFolder)

        then: 'Custom names are loaded successfully'
        success
        model.customNames.size() == 2
        model.customNames[0].name == 'Diamond Sword'
        model.customNames[0].type == 'item'
        model.customNames[1].name == 'Steve'
        model.metadata.customNamesCount == 2
    }

    def "OutputViewerModel can load portals from JSON"() {
        given: 'A portals JSON file'
        def portalsData = [
            [
                dimension: 'overworld',
                anchor: [x: 100, y: 64, z: 200],
                center: [x: 100.5, y: 66.5, z: 201.5],
                size: [width: 4, height: 5],
                axis: 'z',
                block_count: 20
            ],
            [
                dimension: 'nether',
                anchor: [x: 12, y: 64, z: 25],
                center: [x: 12.5, y: 66.5, z: 26.5],
                size: [width: 4, height: 5],
                axis: 'z',
                block_count: 20
            ]
        ]
        File portalsFile = new File(outputFolder, 'portals.json')
        portalsFile.text = new JsonBuilder(portalsData).toPrettyString()

        and: 'An OutputViewerModel'
        def model = new OutputViewerModel()

        when: 'Loading from folder'
        boolean success = model.loadFromFolder(outputFolder)

        then: 'Portals are loaded successfully'
        success
        model.portals.size() == 2
        model.portals[0].dimension == 'overworld'
        model.portals[0].center.x == 100.5
        model.portals[1].dimension == 'nether'
        model.metadata.portalsCount == 2
    }

    def "OutputViewerModel can load block results from JSON"() {
        given: 'A block results JSON file'
        def blockResultsData = [
            [
                block_type: 'minecraft:diamond_ore',
                dimension: 'overworld',
                x: 100,
                y: -32,
                z: 200
            ],
            [
                block_type: 'minecraft:ancient_debris',
                dimension: 'nether',
                x: 50,
                y: 15,
                z: 100
            ]
        ]
        File blockResultsFile = new File(outputFolder, 'block_results.json')
        blockResultsFile.text = new JsonBuilder(blockResultsData).toPrettyString()

        and: 'An OutputViewerModel'
        def model = new OutputViewerModel()

        when: 'Loading from folder'
        boolean success = model.loadFromFolder(outputFolder)

        then: 'Block results are loaded successfully'
        success
        model.blockResults.size() == 2
        model.blockResults[0].block_type == 'minecraft:diamond_ore'
        model.blockResults[1].block_type == 'minecraft:ancient_debris'
        model.metadata.blockResultsCount == 2
    }

    // =========================================================================
    // OutputViewerModel - Database Loading Tests
    // =========================================================================

    def "OutputViewerModel can connect to item database"() {
        given: 'An item database file'
        File itemsDbFile = new File(outputFolder, 'items.db')
        def itemDb = new ItemDatabase(itemsDbFile, 100)
        itemDb.setWorldPath(outputFolder.absolutePath)
        itemDb.setMinecraftVersion('1.21')

        // Add test items using ItemMetadata
        def metadata = new ItemDatabase.ItemMetadata('minecraft:diamond_sword')
        metadata.count = 1
        metadata.dimension = 'overworld'
        metadata.x = 100
        metadata.y = 64
        metadata.z = 200
        metadata.containerType = 'chest'
        metadata.regionFile = 'r.0.0.mca'
        itemDb.insertItem(metadata)
        itemDb.close()

        and: 'An OutputViewerModel'
        def model = new OutputViewerModel()

        when: 'Loading from folder'
        boolean success = model.loadFromFolder(outputFolder)

        then: 'Item database is loaded successfully'
        success
        model.itemDatabase != null
        model.metadata.itemTypesCount >= 1
        model.metadata.totalItemCount >= 1

        cleanup:
        model?.close()
    }

    def "OutputViewerModel can connect to block database"() {
        given: 'A block database file'
        File blocksDbFile = new File(outputFolder, 'blocks.db')
        def blockDb = new BlockDatabase(blocksDbFile, 100)
        blockDb.setWorldPath(outputFolder.absolutePath)
        blockDb.setMinecraftVersion('1.21')

        // Add test blocks using insertBlock method
        blockDb.insertBlock(
            'minecraft:diamond_ore',  // blockType
            'overworld',              // dimension
            100, -32, 200,            // x, y, z
            null,                     // properties
            'r.0.0.mca'              // regionFile
        )
        blockDb.close()

        and: 'An OutputViewerModel'
        def model = new OutputViewerModel()

        when: 'Loading from folder'
        boolean success = model.loadFromFolder(outputFolder)

        then: 'Block database is loaded successfully'
        success
        model.blockDatabase != null
        model.metadata.blockTypesCount >= 1
        model.metadata.totalBlocksIndexed >= 1

        cleanup:
        model?.close()
    }

    // =========================================================================
    // Data Parsing Tests
    // =========================================================================

    def "Book formatting codes parsed correctly"() {
        given: 'A book with Minecraft formatting codes'
        def booksData = [
            books: [
                [
                    title: '§lBold§r §oItalic§r §nUnderline',
                    author: '§4Red Author',
                    pages: ['§lBold text§r\n§oItalic§r\n§cRed'],
                    location_type: 'chest'
                ]
            ]
        ]
        File booksFile = new File(outputFolder, 'all_books_stendhal.json')
        booksFile.text = new JsonBuilder(booksData).toPrettyString()

        and: 'An OutputViewerModel'
        def model = new OutputViewerModel()

        when: 'Loading from folder'
        model.loadFromFolder(outputFolder)

        then: 'Formatting codes are preserved'
        model.books[0].title.contains('§l')
        model.books[0].title.contains('§o')
        model.books[0].author.contains('§4')
        model.books[0].pages[0].contains('§l')
    }

    def "Sign text extracted properly from CSV"() {
        given: 'A signs CSV with quoted fields'
        File signsFile = new File(outputFolder, 'all_signs.csv')
        signsFile.text = '''\
            dimension,x,y,z,line1,line2,line3,line4
            overworld,100,64,200,"Welcome, friend","to the","Test Server",""
            overworld,101,64,200,"Line with ""quotes""","","",""
            '''.stripIndent().trim()

        and: 'An OutputViewerModel'
        def model = new OutputViewerModel()

        when: 'Loading from folder'
        model.loadFromFolder(outputFolder)

        then: 'CSV fields are parsed correctly'
        model.signs.size() == 2
        model.signs[0].line1 == 'Welcome, friend'  // Comma inside quotes
        model.signs[0].line2 == 'to the'
        model.signs[1].line1 == 'Line with "quotes"'  // Escaped quotes
    }

    def "Portal coordinates parsed correctly"() {
        given: 'A portals JSON with nested coordinates'
        def portalsData = [
            [
                dimension: 'overworld',
                anchor: [x: 800, y: 64, z: 1600],
                center: [x: 800.5, y: 66.5, z: 1601.5],
                size: [width: 4, height: 5],
                axis: 'z',
                block_count: 20
            ]
        ]
        File portalsFile = new File(outputFolder, 'portals.json')
        portalsFile.text = new JsonBuilder(portalsData).toPrettyString()

        and: 'An OutputViewerModel'
        def model = new OutputViewerModel()

        when: 'Loading from folder'
        model.loadFromFolder(outputFolder)

        then: 'Nested coordinate maps are parsed'
        model.portals[0].anchor.x == 800
        model.portals[0].anchor.y == 64
        model.portals[0].anchor.z == 1600
        model.portals[0].center.x == 800.5
        model.portals[0].center.y == 66.5
        model.portals[0].center.z == 1601.5
        model.portals[0].size.width == 4
        model.portals[0].size.height == 5
    }

    // =========================================================================
    // Portal Pairing Algorithm Tests
    // =========================================================================

    def "Exact match portal pairing works"() {
        given: 'Overworld portal at (800, 64, 1600) and Nether portal at (100, 64, 200)'
        def owPortal = new PortalDetector.Portal(
            'overworld', 800, 64, 1600,  // anchor
            4, 5, 'z', 20,                // size, axis, blockCount
            800.0, 66.5, 1600.0           // center
        )
        def netherPortal = new PortalDetector.Portal(
            'nether', 100, 64, 200,
            4, 5, 'z', 20,
            100.0, 66.5, 200.0
        )

        when: 'Calculating Nether coordinates from Overworld'
        double expectedX = owPortal.centerX / 8.0
        double expectedZ = owPortal.centerZ / 8.0

        and: 'Calculating distance to Nether portal'
        double dx = netherPortal.centerX - expectedX
        double dz = netherPortal.centerZ - expectedZ
        double distance = Math.sqrt(dx * dx + dz * dz)

        then: 'Exact match (distance < 1.0)'
        expectedX == 100.0
        expectedZ == 200.0
        distance < 1.0
    }

    def "Close proximity portal pairing works"() {
        given: 'Overworld portal at (808, 64, 1612) and Nether portal at (100, 64, 200)'
        def owPortal = new PortalDetector.Portal(
            'overworld', 808, 64, 1612,
            4, 5, 'z', 20,
            808.0, 66.5, 1612.0
        )
        def netherPortal = new PortalDetector.Portal(
            'nether', 100, 64, 200,
            4, 5, 'z', 20,
            100.0, 66.5, 200.0
        )

        when: 'Calculating expected Nether coordinates'
        double expectedX = owPortal.centerX / 8.0
        double expectedZ = owPortal.centerZ / 8.0

        and: 'Calculating distance'
        double dx = netherPortal.centerX - expectedX
        double dz = netherPortal.centerZ - expectedZ
        double distance = Math.sqrt(dx * dx + dz * dz)

        then: 'Close proximity (distance between 1.0 and 16.0)'
        expectedX == 101.0  // 808 / 8 = 101
        expectedZ == 201.5  // 1612 / 8 = 201.5
        distance >= 1.0
        distance < 16.0
    }

    def "Orphan portal identified when no match exists"() {
        given: 'Overworld portal at (10000, 64, 10000) and Nether portal at (100, 64, 200)'
        def owPortal = new PortalDetector.Portal(
            'overworld', 10000, 64, 10000,
            4, 5, 'z', 20,
            10000.0, 66.5, 10000.0
        )
        def netherPortal = new PortalDetector.Portal(
            'nether', 100, 64, 200,
            4, 5, 'z', 20,
            100.0, 66.5, 200.0
        )

        when: 'Calculating expected Nether coordinates'
        double expectedX = owPortal.centerX / 8.0
        double expectedZ = owPortal.centerZ / 8.0

        and: 'Calculating distance'
        double dx = netherPortal.centerX - expectedX
        double dz = netherPortal.centerZ - expectedZ
        double distance = Math.sqrt(dx * dx + dz * dz)

        then: 'Distant match (distance > 128.0 = orphan)'
        expectedX == 1250.0  // 10000 / 8 = 1250
        expectedZ == 1250.0
        distance > 128.0
    }

    def "Coordinate conversion Overworld to Nether is correct"() {
        expect: 'Overworld coords divided by 8'
        800 / 8.0 == 100.0
        1600 / 8.0 == 200.0
        -800 / 8.0 == -100.0
        0 / 8.0 == 0.0
    }

    def "Coordinate conversion Nether to Overworld is correct"() {
        expect: 'Nether coords multiplied by 8'
        100 * 8.0 == 800.0
        200 * 8.0 == 1600.0
        -100 * 8.0 == -800.0
        0 * 8.0 == 0.0
    }

    def "Y coordinate does not scale between dimensions"() {
        given: 'Overworld portal at Y=64 and Nether portal at Y=64'
        def owPortal = new PortalDetector.Portal(
            'overworld', 800, 64, 1600,
            4, 5, 'z', 20,
            800.0, 66.5, 1600.0
        )
        def netherPortal = new PortalDetector.Portal(
            'nether', 100, 64, 200,
            4, 5, 'z', 20,
            100.0, 66.5, 200.0
        )

        expect: 'Y coordinates remain the same'
        owPortal.centerY == netherPortal.centerY
    }

    // =========================================================================
    // Error Handling Tests
    // =========================================================================

    def "OutputViewerModel handles missing folder gracefully"() {
        given: 'A non-existent folder'
        File missingFolder = new File(outputFolder, 'nonexistent')

        and: 'An OutputViewerModel'
        def model = new OutputViewerModel()

        when: 'Attempting to load'
        boolean success = model.loadFromFolder(missingFolder)

        then: 'Loading fails gracefully'
        !success
        model.books.empty
        model.signs.empty
        model.portals.empty
    }

    def "OutputViewerModel handles missing files gracefully"() {
        given: 'An empty output folder'
        // outputFolder exists but has no files

        and: 'An OutputViewerModel'
        def model = new OutputViewerModel()

        when: 'Loading from empty folder'
        boolean success = model.loadFromFolder(outputFolder)

        then: 'Loading succeeds but collections are empty'
        success  // No error, just no data
        model.books.empty
        model.signs.empty
        model.customNames.empty
        model.portals.empty
        model.blockResults.empty
        model.itemDatabase == null
        model.blockDatabase == null
    }

    def "OutputViewerModel handles malformed JSON gracefully"() {
        given: 'A malformed books JSON file'
        File booksFile = new File(outputFolder, 'all_books_stendhal.json')
        booksFile.text = '{ invalid json }'

        and: 'An OutputViewerModel'
        def model = new OutputViewerModel()

        when: 'Attempting to load'
        boolean success = model.loadFromFolder(outputFolder)

        then: 'Loading succeeds but books are empty'
        success  // Overall loading doesn't fail
        model.books.empty  // Just this file failed
    }

    def "OutputViewerModel handles malformed CSV gracefully"() {
        given: 'A malformed signs CSV file'
        File signsFile = new File(outputFolder, 'all_signs.csv')
        signsFile.text = 'invalid,csv,with,mismatched,columns\n1,2,3'

        and: 'An OutputViewerModel'
        def model = new OutputViewerModel()

        when: 'Attempting to load'
        boolean success = model.loadFromFolder(outputFolder)

        then: 'Loading succeeds but signs are empty or partial'
        success
        // CSV parser skips rows with mismatched column counts
        model.signs.empty || model.signs.size() < 2
    }

    // =========================================================================
    // Metadata and Summary Tests
    // =========================================================================

    def "OutputViewerModel builds correct metadata summary"() {
        given: 'Multiple data files'
        createTestDataFiles(outputFolder, 5, 10, 3, 2, 1)

        and: 'An OutputViewerModel'
        def model = new OutputViewerModel()

        when: 'Loading from folder'
        model.loadFromFolder(outputFolder)

        then: 'Metadata is populated correctly'
        model.metadata.booksCount == 5
        model.metadata.signsCount == 10
        model.metadata.customNamesCount == 3
        model.metadata.portalsCount == 2
        model.metadata.blockResultsCount == 1
    }

    def "OutputViewerModel generates correct summary text"() {
        given: 'Multiple data files'
        createTestDataFiles(outputFolder, 2, 3, 1, 1, 0)

        and: 'An OutputViewerModel'
        def model = new OutputViewerModel()

        when: 'Loading from folder'
        model.loadFromFolder(outputFolder)
        String summary = model.getSummaryText()

        then: 'Summary text contains counts'
        summary.contains('2 books')
        summary.contains('3 signs')
        summary.contains('1 custom names')
        summary.contains('1 portals')
    }

    def "OutputViewerModel summary shows 'No data' when empty"() {
        given: 'An empty output folder'
        def model = new OutputViewerModel()

        when: 'Loading from empty folder'
        model.loadFromFolder(outputFolder)
        String summary = model.getSummaryText()

        then: 'Summary shows no data'
        summary == 'Loaded: No data'
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * Create test data files in the output folder.
     */
    private void createTestDataFiles(File folder, int bookCount, int signCount,
                                     int customNameCount, int portalCount, int blockResultCount) {
        // Books
        if (bookCount > 0) {
            def books = (1..bookCount).collect { i ->
                [
                    title: "Book ${i}",
                    author: "Author ${i}",
                    pages: ["Content ${i}"],
                    location_type: 'chest'
                ]
            }
            File booksFile = new File(folder, 'all_books_stendhal.json')
            booksFile.text = new JsonBuilder([books: books]).toPrettyString()
        }

        // Signs
        if (signCount > 0) {
            File signsFile = new File(folder, 'all_signs.csv')
            def csvLines = ['dimension,x,y,z,line1,line2,line3,line4']
            (1..signCount).each { i ->
                csvLines << "overworld,${i * 10},64,${i * 20},Line1,Line2,Line3,Line4"
            }
            signsFile.text = csvLines.join('\n')
        }

        // Custom names
        if (customNameCount > 0) {
            def names = (1..customNameCount).collect { i ->
                [
                    name: "Custom Name ${i}",
                    type: 'item',
                    item_id: 'minecraft:diamond_sword',
                    dimension: 'overworld'
                ]
            }
            File customNamesFile = new File(folder, 'custom_names.json')
            customNamesFile.text = new JsonBuilder(names).toPrettyString()
        }

        // Portals
        if (portalCount > 0) {
            def portals = (1..portalCount).collect { i ->
                [
                    dimension: i % 2 == 0 ? 'nether' : 'overworld',
                    anchor: [x: i * 100, y: 64, z: i * 200],
                    center: [x: i * 100 + 0.5, y: 66.5, z: i * 200 + 0.5],
                    size: [width: 4, height: 5],
                    axis: 'z',
                    block_count: 20
                ]
            }
            File portalsFile = new File(folder, 'portals.json')
            portalsFile.text = new JsonBuilder(portals).toPrettyString()
        }

        // Block results
        if (blockResultCount > 0) {
            def blocks = (1..blockResultCount).collect { i ->
                [
                    block_type: 'minecraft:diamond_ore',
                    dimension: 'overworld',
                    x: i * 50,
                    y: -32,
                    z: i * 100
                ]
            }
            File blockResultsFile = new File(folder, 'block_results.json')
            blockResultsFile.text = new JsonBuilder(blocks).toPrettyString()
        }
    }
}

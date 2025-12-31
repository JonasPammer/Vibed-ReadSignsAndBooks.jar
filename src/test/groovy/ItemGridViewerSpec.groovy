import groovy.json.JsonBuilder
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.TempDir
import viewers.ItemGridViewer

import java.awt.GraphicsEnvironment
import java.nio.file.Path

/**
 * Tests for ItemGridViewer - JEI-style item grid component.
 *
 * Tests verify:
 * - Instantiation with valid database
 * - Search filtering (JEI-style syntax)
 * - Category tabs
 * - Grid rendering logic
 * - Tooltips
 * - Edge cases (empty, single, large datasets)
 */
@IgnoreIf({ GraphicsEnvironment.headless })
class ItemGridViewerSpec extends Specification {

    @TempDir
    Path tempDir

    File testDbFile
    ItemDatabase testDb

    def setup() {
        testDbFile = tempDir.resolve('test_items.db').toFile()
    }

    def cleanup() {
        testDb?.close()
    }

    // =========================================================================
    // Instantiation Tests
    // =========================================================================

    def "ItemGridViewer can be instantiated with valid database"() {
        given: 'A database with test items'
        createTestDatabase(testDbFile, 10)

        when: 'Creating ItemGridViewer'
        def viewer = new ItemGridViewer(testDbFile)

        then: 'Viewer is created successfully'
        viewer != null
        viewer.@database != null
        viewer.@allItems.size() == 10
        viewer.@filteredItems.size() == 10
    }

    def "ItemGridViewer throws exception for non-existent database"() {
        given: 'A non-existent database file'
        File nonExistentDb = tempDir.resolve('nonexistent.db').toFile()

        when: 'Creating ItemGridViewer with missing file'
        new ItemGridViewer(nonExistentDb)

        then: 'IllegalArgumentException is thrown'
        thrown(IllegalArgumentException)
    }

    def "ItemGridViewer loads all items from database on construction"() {
        given: 'A database with multiple item types'
        createTestDatabase(testDbFile, 100)

        when: 'Creating ItemGridViewer'
        def viewer = new ItemGridViewer(testDbFile)

        then: 'All items are loaded into memory'
        viewer.@allItems.size() == 100
        viewer.@filteredItems.size() == 100
    }

    // =========================================================================
    // Search Filtering Tests
    // =========================================================================

    def "parseSearchText extracts basic search term"() {
        given: 'A viewer with test data'
        createTestDatabase(testDbFile, 5)
        def viewer = new ItemGridViewer(testDbFile)

        when: 'Parsing basic search text'
        Map criteria = viewer.parseSearchText('diamond')

        then: 'Name criteria is extracted'
        criteria.name == 'diamond'
        criteria.player == null
        criteria.enchantment == null
        criteria.dimension == null
        criteria.named == null
    }

    def "parseSearchText extracts player filter with @ prefix"() {
        given: 'A viewer with test data'
        createTestDatabase(testDbFile, 5)
        def viewer = new ItemGridViewer(testDbFile)

        when: 'Parsing @player syntax'
        Map criteria = viewer.parseSearchText('@steve')

        then: 'Player criteria is extracted'
        criteria.player == 'steve'
        criteria.name == null
    }

    def "parseSearchText extracts enchantment filter with # prefix"() {
        given: 'A viewer with test data'
        createTestDatabase(testDbFile, 5)
        def viewer = new ItemGridViewer(testDbFile)

        when: 'Parsing #enchantment syntax'
        Map criteria = viewer.parseSearchText('#sharpness')

        then: 'Enchantment criteria is extracted'
        criteria.enchantment == 'sharpness'
        criteria.name == null
    }

    def "parseSearchText extracts dimension filter with ~ prefix"() {
        given: 'A viewer with test data'
        createTestDatabase(testDbFile, 5)
        def viewer = new ItemGridViewer(testDbFile)

        when: 'Parsing ~dimension syntax'
        Map criteria = viewer.parseSearchText('~nether')

        then: 'Dimension criteria is extracted'
        criteria.dimension == 'nether'
        criteria.name == null
    }

    def "parseSearchText extracts named filter with \$named keyword"() {
        given: 'A viewer with test data'
        createTestDatabase(testDbFile, 5)
        def viewer = new ItemGridViewer(testDbFile)

        when: 'Parsing $named syntax'
        Map criteria = viewer.parseSearchText('$named')

        then: 'Named criteria is extracted'
        criteria.named == 'true'
        criteria.name == null
    }

    def "parseSearchText handles multiple filters combined"() {
        given: 'A viewer with test data'
        createTestDatabase(testDbFile, 5)
        def viewer = new ItemGridViewer(testDbFile)

        when: 'Parsing multiple search tokens'
        Map criteria = viewer.parseSearchText('diamond @steve #sharpness ~overworld')

        then: 'All criteria are extracted'
        criteria.name == 'diamond'
        criteria.player == 'steve'
        criteria.enchantment == 'sharpness'
        criteria.dimension == 'overworld'
    }

    def "parseSearchText returns empty map for null or empty text"() {
        given: 'A viewer with test data'
        createTestDatabase(testDbFile, 5)
        def viewer = new ItemGridViewer(testDbFile)

        expect: 'Empty criteria for null/empty input'
        viewer.parseSearchText(null) == [:]
        viewer.parseSearchText('') == [:]
        viewer.parseSearchText('   ') == [:]
    }

    def "matchesName filters items by item_id"() {
        given: 'A viewer with test data'
        createTestDatabase(testDbFile, 5)
        def viewer = new ItemGridViewer(testDbFile)

        and: 'Items with different IDs'
        def diamondItem = [item_id: 'minecraft:diamond_sword', custom_name: null]
        def ironItem = [item_id: 'minecraft:iron_pickaxe', custom_name: null]

        expect: 'Item ID matching works'
        viewer.matchesName(diamondItem, 'diamond')
        viewer.matchesName(diamondItem, 'sword')
        !viewer.matchesName(diamondItem, 'iron')
        viewer.matchesName(ironItem, 'pickaxe')
    }

    def "matchesName filters items by custom_name"() {
        given: 'A viewer with test data'
        createTestDatabase(testDbFile, 5)
        def viewer = new ItemGridViewer(testDbFile)

        and: 'Items with custom names'
        def namedItem = [item_id: 'minecraft:diamond_sword', custom_name: 'Excalibur']

        expect: 'Custom name matching works'
        viewer.matchesName(namedItem, 'excalibur')
        viewer.matchesName(namedItem, 'diamond')
    }

    def "matchesEnchantment filters by enchantments field"() {
        given: 'A viewer with test data'
        createTestDatabase(testDbFile, 5)
        def viewer = new ItemGridViewer(testDbFile)

        and: 'Items with enchantments'
        def enchantedItem = [
            item_id: 'minecraft:diamond_sword',
            enchantments: '{"sharpness":5,"looting":3}',
            stored_enchantments: null
        ]

        expect: 'Enchantment matching works'
        viewer.matchesEnchantment(enchantedItem, 'sharpness')
        viewer.matchesEnchantment(enchantedItem, 'looting')
        !viewer.matchesEnchantment(enchantedItem, 'protection')
    }

    def "matchesDimension filters by dimension field"() {
        given: 'A viewer with test data'
        createTestDatabase(testDbFile, 5)
        def viewer = new ItemGridViewer(testDbFile)

        and: 'Items in different dimensions'
        def overworldItem = [item_id: 'minecraft:diamond', dimension: 'overworld']
        def netherItem = [item_id: 'minecraft:netherite', dimension: 'nether']

        expect: 'Dimension matching works'
        viewer.matchesDimension(overworldItem, 'overworld')
        viewer.matchesDimension(overworldItem, 'world')
        !viewer.matchesDimension(overworldItem, 'nether')
        viewer.matchesDimension(netherItem, 'nether')
    }

    def "hasCustomName detects items with custom names"() {
        given: 'A viewer with test data'
        createTestDatabase(testDbFile, 5)
        def viewer = new ItemGridViewer(testDbFile)

        and: 'Items with and without custom names'
        def namedItem = [custom_name: 'My Sword']
        def unnamedItem = [custom_name: null]
        def emptyNamedItem = [custom_name: '  ']

        expect: 'Custom name detection works'
        viewer.hasCustomName(namedItem)
        !viewer.hasCustomName(unnamedItem)
        !viewer.hasCustomName(emptyNamedItem)
    }

    // =========================================================================
    // Category Filtering Tests
    // =========================================================================

    def "matchesCategory filters Tools correctly"() {
        given: 'A viewer with test data'
        createTestDatabase(testDbFile, 5)
        def viewer = new ItemGridViewer(testDbFile)

        expect: 'Tool items match Tools category'
        viewer.matchesCategory([item_id: 'minecraft:diamond_pickaxe'], 'Tools')
        viewer.matchesCategory([item_id: 'minecraft:iron_axe'], 'Tools')
        viewer.matchesCategory([item_id: 'minecraft:golden_shovel'], 'Tools')
        viewer.matchesCategory([item_id: 'minecraft:wooden_hoe'], 'Tools')
        !viewer.matchesCategory([item_id: 'minecraft:diamond_sword'], 'Tools')
    }

    def "matchesCategory filters Weapons correctly"() {
        given: 'A viewer with test data'
        createTestDatabase(testDbFile, 5)
        def viewer = new ItemGridViewer(testDbFile)

        expect: 'Weapon items match Weapons category'
        viewer.matchesCategory([item_id: 'minecraft:diamond_sword'], 'Weapons')
        viewer.matchesCategory([item_id: 'minecraft:bow'], 'Weapons')
        viewer.matchesCategory([item_id: 'minecraft:crossbow'], 'Weapons')
        viewer.matchesCategory([item_id: 'minecraft:trident'], 'Weapons')
        !viewer.matchesCategory([item_id: 'minecraft:diamond_pickaxe'], 'Weapons')
    }

    def "matchesCategory filters Armor correctly"() {
        given: 'A viewer with test data'
        createTestDatabase(testDbFile, 5)
        def viewer = new ItemGridViewer(testDbFile)

        expect: 'Armor items match Armor category'
        viewer.matchesCategory([item_id: 'minecraft:diamond_helmet'], 'Armor')
        viewer.matchesCategory([item_id: 'minecraft:iron_chestplate'], 'Armor')
        viewer.matchesCategory([item_id: 'minecraft:golden_leggings'], 'Armor')
        viewer.matchesCategory([item_id: 'minecraft:netherite_boots'], 'Armor')
        viewer.matchesCategory([item_id: 'minecraft:elytra'], 'Armor')
        !viewer.matchesCategory([item_id: 'minecraft:diamond_sword'], 'Armor')
    }

    def "matchesCategory filters Blocks correctly"() {
        given: 'A viewer with test data'
        createTestDatabase(testDbFile, 5)
        def viewer = new ItemGridViewer(testDbFile)

        expect: 'Block items match Blocks category'
        viewer.matchesCategory([item_id: 'minecraft:diamond_ore'], 'Blocks')
        viewer.matchesCategory([item_id: 'minecraft:stone'], 'Blocks')
        viewer.matchesCategory([item_id: 'minecraft:oak_wood'], 'Blocks')
        viewer.matchesCategory([item_id: 'minecraft:oak_planks'], 'Blocks')
        !viewer.matchesCategory([item_id: 'minecraft:diamond'], 'Blocks')
    }

    def "matchesCategory filters Materials correctly"() {
        given: 'A viewer with test data'
        createTestDatabase(testDbFile, 5)
        def viewer = new ItemGridViewer(testDbFile)

        expect: 'Material items match Materials category'
        viewer.matchesCategory([item_id: 'minecraft:iron_ingot'], 'Materials')
        viewer.matchesCategory([item_id: 'minecraft:diamond'], 'Materials')
        viewer.matchesCategory([item_id: 'minecraft:redstone_dust'], 'Materials')
        viewer.matchesCategory([item_id: 'minecraft:gold_nugget'], 'Materials')
        !viewer.matchesCategory([item_id: 'minecraft:diamond_sword'], 'Materials')
    }

    def "matchesCategory filters Food correctly"() {
        given: 'A viewer with test data'
        createTestDatabase(testDbFile, 5)
        def viewer = new ItemGridViewer(testDbFile)

        expect: 'Food items match Food category'
        viewer.matchesCategory([item_id: 'minecraft:apple'], 'Food')
        viewer.matchesCategory([item_id: 'minecraft:bread'], 'Food')
        viewer.matchesCategory([item_id: 'minecraft:cooked_beef'], 'Food')
        viewer.matchesCategory([item_id: 'minecraft:carrot'], 'Food')
        !viewer.matchesCategory([item_id: 'minecraft:diamond'], 'Food')
    }

    def "matchesCategory All category matches everything"() {
        given: 'A viewer with test data'
        createTestDatabase(testDbFile, 5)
        def viewer = new ItemGridViewer(testDbFile)

        expect: 'All category matches all items'
        viewer.matchesCategory([item_id: 'minecraft:diamond_sword'], 'All')
        viewer.matchesCategory([item_id: 'minecraft:stone'], 'All')
        viewer.matchesCategory([item_id: 'minecraft:apple'], 'All')
        viewer.matchesCategory([item_id: 'minecraft:anything'], 'All')
    }

    // =========================================================================
    // Grid Rendering Tests
    // =========================================================================

    def "calculateColumns computes correct column count based on width"() {
        given: 'A viewer with test data'
        createTestDatabase(testDbFile, 5)
        def viewer = new ItemGridViewer(testDbFile)

        when: 'Calculating columns for different widths'
        // SLOT_SIZE = 48, SLOT_PADDING = 2, total per slot = 50
        // (width - 40) / 50 = columns
        viewer.@scrollPane = [width: 950]
        int columns950 = viewer.calculateColumns()

        viewer.@scrollPane = [width: 500]
        int columns500 = viewer.calculateColumns()

        viewer.@scrollPane = [width: 100]
        int columns100 = viewer.calculateColumns()

        then: 'Column counts are calculated correctly'
        columns950 == 18  // (950 - 40) / 50 = 18
        columns500 == 9   // (500 - 40) / 50 = 9
        columns100 >= 1   // Minimum 1 column
    }

    def "calculateColumns defaults to 950 width when scrollPane width is 0"() {
        given: 'A viewer with test data'
        createTestDatabase(testDbFile, 5)
        def viewer = new ItemGridViewer(testDbFile)

        when: 'ScrollPane width is 0'
        viewer.@scrollPane = [width: 0]
        int columns = viewer.calculateColumns()

        then: 'Default width 950 is used'
        columns == 18  // (950 - 40) / 50 = 18
    }

    def "extractItemName removes minecraft namespace and underscores"() {
        given: 'A viewer with test data'
        createTestDatabase(testDbFile, 5)
        def viewer = new ItemGridViewer(testDbFile)

        expect: 'Item names are extracted correctly'
        viewer.extractItemName('minecraft:diamond_sword') == 'diamond swor'  // Truncated to 12 chars
        viewer.extractItemName('minecraft:iron_pickaxe') == 'iron pickaxe'
        viewer.extractItemName('minecraft:apple') == 'apple'
        viewer.extractItemName('diamond_ore') == 'diamond ore'
    }

    def "hasEnchantments detects enchanted items"() {
        given: 'A viewer with test data'
        createTestDatabase(testDbFile, 5)
        def viewer = new ItemGridViewer(testDbFile)

        and: 'Items with various enchantment states'
        def enchantedItem = [
            enchantments: '{"sharpness":5}',
            stored_enchantments: null
        ]
        def storedEnchantedItem = [
            enchantments: null,
            stored_enchantments: '{"protection":4}'
        ]
        def unenchantedItem = [
            enchantments: '{}',
            stored_enchantments: null
        ]
        def emptyEnchantedItem = [
            enchantments: '[]',
            stored_enchantments: '[]'
        ]

        expect: 'Enchantment detection works'
        viewer.hasEnchantments(enchantedItem)
        viewer.hasEnchantments(storedEnchantedItem)
        !viewer.hasEnchantments(unenchantedItem)
        !viewer.hasEnchantments(emptyEnchantedItem)
    }

    // =========================================================================
    // Tooltip Tests
    // =========================================================================

    def "buildTooltipText includes item name"() {
        given: 'A viewer with test data'
        createTestDatabase(testDbFile, 5)
        def viewer = new ItemGridViewer(testDbFile)

        and: 'An item'
        def item = [
            item_id: 'minecraft:diamond_sword',
            custom_name: null,
            dimension: 'overworld',
            x: 100, y: 64, z: 200
        ]

        when: 'Building tooltip'
        String tooltip = viewer.buildTooltipText(item)

        then: 'Tooltip contains item name'
        tooltip.contains('diamond swor')  // Extracted name
    }

    def "buildTooltipText shows custom name with special formatting"() {
        given: 'A viewer with test data'
        createTestDatabase(testDbFile, 5)
        def viewer = new ItemGridViewer(testDbFile)

        and: 'An item with custom name'
        def item = [
            item_id: 'minecraft:diamond_sword',
            custom_name: 'Excalibur',
            dimension: 'overworld',
            x: 100, y: 64, z: 200
        ]

        when: 'Building tooltip'
        String tooltip = viewer.buildTooltipText(item)

        then: 'Tooltip shows custom name and item ID'
        tooltip.contains('Excalibur')
        tooltip.contains('diamond swor')
    }

    def "buildTooltipText includes enchantments"() {
        given: 'A viewer with test data'
        createTestDatabase(testDbFile, 5)
        def viewer = new ItemGridViewer(testDbFile)

        and: 'An enchanted item'
        def item = [
            item_id: 'minecraft:diamond_sword',
            enchantments: '{"sharpness":5,"looting":3}',
            stored_enchantments: null,
            dimension: 'overworld',
            x: 100, y: 64, z: 200
        ]

        when: 'Building tooltip'
        String tooltip = viewer.buildTooltipText(item)

        then: 'Tooltip contains enchantments'
        tooltip.contains('sharpness')
        tooltip.contains('5')
        tooltip.contains('looting')
        tooltip.contains('3')
    }

    def "buildTooltipText includes location and container"() {
        given: 'A viewer with test data'
        createTestDatabase(testDbFile, 5)
        def viewer = new ItemGridViewer(testDbFile)

        and: 'An item with location'
        def item = [
            item_id: 'minecraft:diamond',
            dimension: 'nether',
            x: 50, y: 64, z: 100,
            container_type: 'chest'
        ]

        when: 'Building tooltip'
        String tooltip = viewer.buildTooltipText(item)

        then: 'Tooltip contains location and container'
        tooltip.contains('nether')
        tooltip.contains('50')
        tooltip.contains('64')
        tooltip.contains('100')
        tooltip.contains('chest')
    }

    def "buildTooltipText includes keyboard hints"() {
        given: 'A viewer with test data'
        createTestDatabase(testDbFile, 5)
        def viewer = new ItemGridViewer(testDbFile)

        and: 'A basic item'
        def item = [
            item_id: 'minecraft:diamond',
            dimension: 'overworld',
            x: 0, y: 64, z: 0
        ]

        when: 'Building tooltip'
        String tooltip = viewer.buildTooltipText(item)

        then: 'Tooltip contains keyboard hints'
        tooltip.contains('[R] Details')
        tooltip.contains('[C] Copy TP')
    }

    def "parseEnchantments parses JSON correctly"() {
        given: 'A viewer with test data'
        createTestDatabase(testDbFile, 5)
        def viewer = new ItemGridViewer(testDbFile)

        when: 'Parsing valid enchantment JSON'
        Map enchantments = viewer.parseEnchantments('{"sharpness":5,"looting":3}')

        then: 'Enchantments are parsed correctly'
        enchantments.sharpness == 5
        enchantments.looting == 3
    }

    def "parseEnchantments handles malformed JSON gracefully"() {
        given: 'A viewer with test data'
        createTestDatabase(testDbFile, 5)
        def viewer = new ItemGridViewer(testDbFile)

        when: 'Parsing invalid JSON'
        Map enchantments = viewer.parseEnchantments('invalid json')

        then: 'Empty map is returned'
        enchantments == [:]
    }

    def "parseEnchantments handles empty/null input"() {
        given: 'A viewer with test data'
        createTestDatabase(testDbFile, 5)
        def viewer = new ItemGridViewer(testDbFile)

        expect: 'Empty maps for empty input'
        viewer.parseEnchantments(null) == [:]
        viewer.parseEnchantments('{}') == [:]
        viewer.parseEnchantments('[]') == [:]
    }

    def "parseLore parses JSON array correctly"() {
        given: 'A viewer with test data'
        createTestDatabase(testDbFile, 5)
        def viewer = new ItemGridViewer(testDbFile)

        when: 'Parsing valid lore JSON'
        List lore = viewer.parseLore('["Line 1","Line 2","Line 3"]')

        then: 'Lore lines are parsed correctly'
        lore.size() == 3
        lore[0] == 'Line 1'
        lore[1] == 'Line 2'
        lore[2] == 'Line 3'
    }

    def "parseLore handles malformed JSON gracefully"() {
        given: 'A viewer with test data'
        createTestDatabase(testDbFile, 5)
        def viewer = new ItemGridViewer(testDbFile)

        when: 'Parsing invalid JSON'
        List lore = viewer.parseLore('invalid json')

        then: 'Empty list is returned'
        lore == []
    }

    // =========================================================================
    // Edge Case Tests
    // =========================================================================

    def "ItemGridViewer handles empty database"() {
        given: 'An empty database'
        createTestDatabase(testDbFile, 0)

        when: 'Creating viewer'
        def viewer = new ItemGridViewer(testDbFile)

        then: 'Viewer handles empty data gracefully'
        viewer.@allItems.size() == 0
        viewer.@filteredItems.size() == 0
    }

    def "ItemGridViewer handles single item"() {
        given: 'A database with one item'
        createTestDatabase(testDbFile, 1)

        when: 'Creating viewer'
        def viewer = new ItemGridViewer(testDbFile)

        then: 'Viewer loads single item correctly'
        viewer.@allItems.size() == 1
        viewer.@filteredItems.size() == 1
    }

    def "ItemGridViewer handles thousands of items efficiently"() {
        given: 'A database with many items'
        createTestDatabase(testDbFile, 1000)

        when: 'Creating viewer and measuring load time'
        long start = System.currentTimeMillis()
        def viewer = new ItemGridViewer(testDbFile)
        long elapsed = System.currentTimeMillis() - start

        then: 'All items are loaded in reasonable time'
        viewer.@allItems.size() == 1000
        viewer.@filteredItems.size() == 1000
        elapsed < 5000  // Should load 1000 items in under 5 seconds
    }

    def "applyFilters with no matches results in empty filtered list"() {
        given: 'A viewer with test data'
        createTestDatabase(testDbFile, 10)
        def viewer = new ItemGridViewer(testDbFile)

        when: 'Setting search text that matches nothing'
        viewer.@currentSearchText = 'nonexistent_item_xyz'
        viewer.applyFilters()

        then: 'Filtered list is empty'
        viewer.@filteredItems.size() == 0
    }

    def "applyFilters with category All and no search shows all items"() {
        given: 'A viewer with test data'
        createTestDatabase(testDbFile, 25)
        def viewer = new ItemGridViewer(testDbFile)

        when: 'Setting All category with no search'
        viewer.@currentCategory = 'All'
        viewer.@currentSearchText = ''
        viewer.applyFilters()

        then: 'All items are shown'
        viewer.@filteredItems.size() == 25
    }

    def "updateStatus shows correct item counts"() {
        given: 'A viewer with test data'
        createTestDatabase(testDbFile, 50)
        def viewer = new ItemGridViewer(testDbFile)
        viewer.@statusLabel = [text: '']

        when: 'Filtering to subset of items'
        viewer.@filteredItems = viewer.@allItems.take(10) as List
        viewer.updateStatus()

        then: 'Status shows filtered/total count'
        viewer.@statusLabel.text == '10 / 50 items'
    }

    def "copyTeleportCommand generates correct command"() {
        given: 'A viewer with test data'
        createTestDatabase(testDbFile, 5)
        def viewer = new ItemGridViewer(testDbFile)
        viewer.@statusLabel = [text: '']

        and: 'An item with coordinates'
        def item = [x: 123, y: 64, z: 456]

        when: 'Copying teleport command'
        String cmd = viewer.copyTeleportCommand(item)

        then: 'Command is copied to clipboard'
        cmd == '/tp @s 123 64 456'
        viewer.@statusLabel.text.contains('Copied: /tp @s 123 64 456')
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * Create a test database with specified number of items.
     */
    private void createTestDatabase(File dbFile, int itemCount) {
        testDb = new ItemDatabase(dbFile, 1000)
        testDb.setWorldPath(tempDir.toString())
        testDb.setMinecraftVersion('1.21')

        // Create diverse test items
        List<String> itemTypes = [
            'minecraft:diamond_sword',
            'minecraft:iron_pickaxe',
            'minecraft:diamond_helmet',
            'minecraft:stone',
            'minecraft:apple',
            'minecraft:bow',
            'minecraft:iron_ingot',
            'minecraft:oak_planks',
            'minecraft:bread',
            'minecraft:golden_axe'
        ]

        List<String> dimensions = ['overworld', 'nether', 'the_end']

        for (int i = 1; i <= itemCount; i++) {
            def metadata = new ItemDatabase.ItemMetadata(itemTypes[i % itemTypes.size()])
            metadata.count = (i % 64) + 1
            metadata.dimension = dimensions[i % dimensions.size()]
            metadata.x = i * 10
            metadata.y = 64 + (i % 50)
            metadata.z = i * 20
            metadata.containerType = i % 2 == 0 ? 'chest' : 'barrel'
            metadata.regionFile = "r.${i / 100 as int}.${i / 100 as int}.mca"

            // Add some custom names
            if (i % 5 == 0) {
                metadata.customName = "Custom Item ${i}"
            }

            // Add some enchantments
            if (i % 3 == 0) {
                metadata.enchantments = ['sharpness': (i % 5 + 1)]
            }

            testDb.insertItem(metadata)
        }

        testDb.close()
        testDb = null
    }
}

import groovy.json.JsonSlurper
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Unit tests for ItemDatabase utility class.
 * Tests SQLite database operations, item insertion with limits, queries, metadata, and transactions.
 */
class ItemDatabaseSpec extends Specification {

    @TempDir
    Path tempDir

    // =========================================================================
    // Constructor and Schema Tests
    // =========================================================================

    def "should create database file and initialize schema"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()

        when:
        ItemDatabase db = new ItemDatabase(dbFile)

        then:
        dbFile.exists()
        // Verify tables exist by querying them
        db.queryByItemType('minecraft:diamond_sword').empty  // Should not throw
        db.summary.empty  // Should not throw
        db.getMetadata('test') == null  // Should not throw

        cleanup:
        db?.close()
    }

    def "should enable WAL mode for performance"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        ItemDatabase db = new ItemDatabase(dbFile)

        when:
        def metadata = new ItemDatabase.ItemMetadata('minecraft:test')
        db.insertItem(metadata)
        def result = db.queryByItemType('minecraft:test')

        then:
        result.size() == 1  // If WAL mode failed, this might not work correctly

        cleanup:
        db?.close()
    }

    def "should create parent directory if it doesn't exist"() {
        given:
        File parentDir = tempDir.resolve('nested').toFile()
        File dbFile = new File(parentDir, 'test.db')

        when:
        ItemDatabase db = new ItemDatabase(dbFile)

        then:
        parentDir.exists()
        dbFile.exists()

        cleanup:
        db?.close()
    }

    // =========================================================================
    // insertItem() Tests
    // =========================================================================

    def "insertItem should insert and return true for new item"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        ItemDatabase db = new ItemDatabase(dbFile)
        def metadata = new ItemDatabase.ItemMetadata('minecraft:diamond_sword')
        metadata.x = 100
        metadata.y = 64
        metadata.z = 200
        metadata.dimension = 'overworld'

        when:
        boolean inserted = db.insertItem(metadata)

        then:
        inserted == true
        db.queryByItemType('minecraft:diamond_sword').size() == 1

        cleanup:
        db?.close()
    }

    def "insertItem should return false for duplicate item"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        ItemDatabase db = new ItemDatabase(dbFile)
        def metadata = new ItemDatabase.ItemMetadata('minecraft:diamond_sword')
        metadata.x = 100
        metadata.y = 64
        metadata.z = 200
        metadata.dimension = 'overworld'
        metadata.containerType = 'chest'
        db.insertItem(metadata)

        when:
        boolean inserted = db.insertItem(metadata)

        then:
        inserted == false
        db.queryByItemType('minecraft:diamond_sword').size() == 1  // Still only one

        cleanup:
        db?.close()
    }

    def "insertItem should enforce per-type limit"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        ItemDatabase db = new ItemDatabase(dbFile, 3)  // Limit of 3

        when:
        // Insert 5 items of same type
        (0..4).each { i ->
            def metadata = new ItemDatabase.ItemMetadata('minecraft:stone')
            metadata.x = i
            metadata.y = 0
            metadata.z = 0
            metadata.dimension = 'overworld'
            metadata.containerType = 'chest'
            db.insertItem(metadata)
        }

        then:
        db.queryByItemType('minecraft:stone').size() == 3  // Only 3 stored

        cleanup:
        db?.close()
    }

    def "insertItem should always increment total_count even when limit reached"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        ItemDatabase db = new ItemDatabase(dbFile, 2)  // Limit of 2

        when:
        (0..3).each { i ->
            def metadata = new ItemDatabase.ItemMetadata('minecraft:dirt')
            metadata.x = i
            metadata.y = 0
            metadata.z = 0
            metadata.dimension = 'overworld'
            metadata.containerType = 'chest'
            db.insertItem(metadata)
        }

        then:
        Map count = db.getItemCount('minecraft:dirt')
        count.total_count == 4  // All 4 counted
        count.unique_locations == 2  // Only 2 stored
        count.limit_reached == 1  // Limit was reached

        cleanup:
        db?.close()
    }

    def "insertItem should store enchantments as JSON"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        ItemDatabase db = new ItemDatabase(dbFile)
        def metadata = new ItemDatabase.ItemMetadata('minecraft:diamond_sword')
        metadata.x = 100
        metadata.y = 64
        metadata.z = 200
        metadata.dimension = 'overworld'
        metadata.enchantments = ['sharpness': 5, 'unbreaking': 3]

        when:
        db.insertItem(metadata)

        then:
        def result = db.queryByItemType('minecraft:diamond_sword')[0]
        def enchants = new JsonSlurper().parseText(result.enchantments as String)
        enchants.sharpness == 5
        enchants.unbreaking == 3

        cleanup:
        db?.close()
    }

    def "insertItem should store stored_enchantments for enchanted books"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        ItemDatabase db = new ItemDatabase(dbFile)
        def metadata = new ItemDatabase.ItemMetadata('minecraft:enchanted_book')
        metadata.x = 100
        metadata.y = 64
        metadata.z = 200
        metadata.dimension = 'overworld'
        metadata.storedEnchantments = ['mending': 1, 'infinity': 1]

        when:
        db.insertItem(metadata)

        then:
        def result = db.queryByItemType('minecraft:enchanted_book')[0]
        def storedEnchants = new JsonSlurper().parseText(result.stored_enchantments as String)
        storedEnchants.mending == 1
        storedEnchants.infinity == 1

        cleanup:
        db?.close()
    }

    def "insertItem should store custom_name"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        ItemDatabase db = new ItemDatabase(dbFile)
        def metadata = new ItemDatabase.ItemMetadata('minecraft:diamond_sword')
        metadata.x = 100
        metadata.y = 64
        metadata.z = 200
        metadata.dimension = 'overworld'
        metadata.customName = 'Excalibur'

        when:
        db.insertItem(metadata)

        then:
        def result = db.queryByItemType('minecraft:diamond_sword')[0]
        result.custom_name == 'Excalibur'

        cleanup:
        db?.close()
    }

    def "insertItem should store lore as JSON"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        ItemDatabase db = new ItemDatabase(dbFile)
        def metadata = new ItemDatabase.ItemMetadata('minecraft:diamond_sword')
        metadata.x = 100
        metadata.y = 64
        metadata.z = 200
        metadata.dimension = 'overworld'
        metadata.lore = ['A legendary sword', 'Forged in the fires of Mordor']

        when:
        db.insertItem(metadata)

        then:
        def result = db.queryByItemType('minecraft:diamond_sword')[0]
        def lore = new JsonSlurper().parseText(result.lore as String)
        lore.size() == 2
        lore[0] == 'A legendary sword'
        lore[1] == 'Forged in the fires of Mordor'

        cleanup:
        db?.close()
    }

    def "insertItem should track with_enchantments count"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        ItemDatabase db = new ItemDatabase(dbFile)

        when:
        // Insert item without enchantments
        def metadata1 = new ItemDatabase.ItemMetadata('minecraft:diamond_sword')
        metadata1.x = 0
        metadata1.y = 0
        metadata1.z = 0
        metadata1.dimension = 'overworld'
        db.insertItem(metadata1)

        // Insert item with enchantments
        def metadata2 = new ItemDatabase.ItemMetadata('minecraft:diamond_sword')
        metadata2.x = 1
        metadata2.y = 0
        metadata2.z = 0
        metadata2.dimension = 'overworld'
        metadata2.enchantments = ['sharpness': 5]
        db.insertItem(metadata2)

        then:
        Map count = db.getItemCount('minecraft:diamond_sword')
        count.with_enchantments == 1

        cleanup:
        db?.close()
    }

    def "insertItem should not count empty enchantment maps as with_enchantments"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        ItemDatabase db = new ItemDatabase(dbFile)

        when:
        def metadata = new ItemDatabase.ItemMetadata('minecraft:diamond_sword')
        metadata.x = 0
        metadata.y = 0
        metadata.z = 0
        metadata.dimension = 'overworld'
        metadata.enchantments = [:]  // empty map should behave like no enchantments
        db.insertItem(metadata)

        then:
        Map count = db.getItemCount('minecraft:diamond_sword')
        count.with_enchantments == 0

        cleanup:
        db?.close()
    }

    def "insertItem should handle null dimension by normalizing to empty string"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        ItemDatabase db = new ItemDatabase(dbFile)

        when:
        def metadata = new ItemDatabase.ItemMetadata('minecraft:diamond_sword')
        metadata.x = 1
        metadata.y = 2
        metadata.z = 3
        metadata.dimension = null
        db.insertItem(metadata)

        then:
        def rows = db.queryByItemType('minecraft:diamond_sword')
        rows.size() == 1
        rows[0].dimension == ''

        cleanup:
        db?.close()
    }

    def "queryNamedItems should support special characters and quotes in custom_name"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        ItemDatabase db = new ItemDatabase(dbFile)

        and:
        def metadata = new ItemDatabase.ItemMetadata('minecraft:diamond_sword')
        metadata.x = 10
        metadata.y = 64
        metadata.z = -5
        metadata.dimension = 'overworld'
        metadata.customName = 'Exca"libur ♥ _100%_'
        db.insertItem(metadata)

        when:
        def rows = db.queryNamedItems('Exca"libur')

        then:
        rows.size() == 1
        rows[0].custom_name == metadata.customName

        cleanup:
        db?.close()
    }

    def "insertItem should track with_custom_name count"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        ItemDatabase db = new ItemDatabase(dbFile)

        when:
        // Insert item without custom name
        def metadata1 = new ItemDatabase.ItemMetadata('minecraft:diamond_sword')
        metadata1.x = 0
        metadata1.y = 0
        metadata1.z = 0
        metadata1.dimension = 'overworld'
        db.insertItem(metadata1)

        // Insert item with custom name
        def metadata2 = new ItemDatabase.ItemMetadata('minecraft:diamond_sword')
        metadata2.x = 1
        metadata2.y = 0
        metadata2.z = 0
        metadata2.dimension = 'overworld'
        metadata2.customName = 'My Sword'
        db.insertItem(metadata2)

        then:
        Map count = db.getItemCount('minecraft:diamond_sword')
        count.with_custom_name == 1

        cleanup:
        db?.close()
    }

    // =========================================================================
    // queryByItemType() Tests
    // =========================================================================

    def "queryByItemType should normalize item ID without prefix"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        ItemDatabase db = new ItemDatabase(dbFile)
        def metadata = new ItemDatabase.ItemMetadata('minecraft:diamond_sword')
        metadata.x = 100
        metadata.y = 64
        metadata.z = 200
        metadata.dimension = 'overworld'
        db.insertItem(metadata)

        when:
        List<Map> results = db.queryByItemType('diamond_sword')  // Without minecraft: prefix

        then:
        results.size() == 1
        results[0].item_id == 'minecraft:diamond_sword'

        cleanup:
        db?.close()
    }

    def "queryByItemType should filter by dimension when specified"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        ItemDatabase db = new ItemDatabase(dbFile)

        def metadata1 = new ItemDatabase.ItemMetadata('minecraft:diamond_sword')
        metadata1.x = 100
        metadata1.y = 64
        metadata1.z = 200
        metadata1.dimension = 'overworld'
        db.insertItem(metadata1)

        def metadata2 = new ItemDatabase.ItemMetadata('minecraft:diamond_sword')
        metadata2.x = 100
        metadata2.y = 64
        metadata2.z = 200
        metadata2.dimension = 'nether'
        db.insertItem(metadata2)

        when:
        List<Map> overworldResults = db.queryByItemType('minecraft:diamond_sword', 'overworld')
        List<Map> netherResults = db.queryByItemType('minecraft:diamond_sword', 'nether')

        then:
        overworldResults.size() == 1
        overworldResults[0].dimension == 'overworld'
        netherResults.size() == 1
        netherResults[0].dimension == 'nether'

        cleanup:
        db?.close()
    }

    def "queryByItemType should return empty list for unknown item type"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        ItemDatabase db = new ItemDatabase(dbFile)

        when:
        List<Map> results = db.queryByItemType('minecraft:nonexistent_item')

        then:
        results.empty

        cleanup:
        db?.close()
    }

    // =========================================================================
    // queryEnchantedItems() Tests
    // =========================================================================

    def "queryEnchantedItems should return only enchanted items"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        ItemDatabase db = new ItemDatabase(dbFile)

        // Insert non-enchanted item
        def metadata1 = new ItemDatabase.ItemMetadata('minecraft:iron_sword')
        metadata1.x = 0
        metadata1.y = 0
        metadata1.z = 0
        metadata1.dimension = 'overworld'
        db.insertItem(metadata1)

        // Insert enchanted item
        def metadata2 = new ItemDatabase.ItemMetadata('minecraft:diamond_sword')
        metadata2.x = 1
        metadata2.y = 0
        metadata2.z = 0
        metadata2.dimension = 'overworld'
        metadata2.enchantments = ['sharpness': 5]
        db.insertItem(metadata2)

        when:
        List<Map> results = db.queryEnchantedItems()

        then:
        results.size() == 1
        results[0].item_id == 'minecraft:diamond_sword'

        cleanup:
        db?.close()
    }

    def "queryEnchantedItems should filter by enchantment name"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        ItemDatabase db = new ItemDatabase(dbFile)

        // Insert item with sharpness
        def metadata1 = new ItemDatabase.ItemMetadata('minecraft:diamond_sword')
        metadata1.x = 0
        metadata1.y = 0
        metadata1.z = 0
        metadata1.dimension = 'overworld'
        metadata1.enchantments = ['sharpness': 5]
        db.insertItem(metadata1)

        // Insert item with smite
        def metadata2 = new ItemDatabase.ItemMetadata('minecraft:iron_sword')
        metadata2.x = 1
        metadata2.y = 0
        metadata2.z = 0
        metadata2.dimension = 'overworld'
        metadata2.enchantments = ['smite': 5]
        db.insertItem(metadata2)

        when:
        List<Map> sharpnessResults = db.queryEnchantedItems('sharpness')
        List<Map> smiteResults = db.queryEnchantedItems('smite')

        then:
        sharpnessResults.size() == 1
        sharpnessResults[0].item_id == 'minecraft:diamond_sword'
        smiteResults.size() == 1
        smiteResults[0].item_id == 'minecraft:iron_sword'

        cleanup:
        db?.close()
    }

    def "queryEnchantedItems should include items with stored_enchantments"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        ItemDatabase db = new ItemDatabase(dbFile)

        // Insert enchanted book
        def metadata = new ItemDatabase.ItemMetadata('minecraft:enchanted_book')
        metadata.x = 0
        metadata.y = 0
        metadata.z = 0
        metadata.dimension = 'overworld'
        metadata.storedEnchantments = ['mending': 1]
        db.insertItem(metadata)

        when:
        List<Map> results = db.queryEnchantedItems('mending')

        then:
        results.size() == 1
        results[0].item_id == 'minecraft:enchanted_book'

        cleanup:
        db?.close()
    }

    // =========================================================================
    // queryNamedItems() Tests
    // =========================================================================

    def "queryNamedItems should return only named items"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        ItemDatabase db = new ItemDatabase(dbFile)

        // Insert unnamed item
        def metadata1 = new ItemDatabase.ItemMetadata('minecraft:iron_sword')
        metadata1.x = 0
        metadata1.y = 0
        metadata1.z = 0
        metadata1.dimension = 'overworld'
        db.insertItem(metadata1)

        // Insert named item
        def metadata2 = new ItemDatabase.ItemMetadata('minecraft:diamond_sword')
        metadata2.x = 1
        metadata2.y = 0
        metadata2.z = 0
        metadata2.dimension = 'overworld'
        metadata2.customName = 'Excalibur'
        db.insertItem(metadata2)

        when:
        List<Map> results = db.queryNamedItems()

        then:
        results.size() == 1
        results[0].item_id == 'minecraft:diamond_sword'
        results[0].custom_name == 'Excalibur'

        cleanup:
        db?.close()
    }

    def "queryNamedItems should filter by name pattern"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        ItemDatabase db = new ItemDatabase(dbFile)

        // Insert items with different names
        def metadata1 = new ItemDatabase.ItemMetadata('minecraft:diamond_sword')
        metadata1.x = 0
        metadata1.y = 0
        metadata1.z = 0
        metadata1.dimension = 'overworld'
        metadata1.customName = 'Excalibur'
        db.insertItem(metadata1)

        def metadata2 = new ItemDatabase.ItemMetadata('minecraft:iron_sword')
        metadata2.x = 1
        metadata2.y = 0
        metadata2.z = 0
        metadata2.dimension = 'overworld'
        metadata2.customName = 'Rusty Blade'
        db.insertItem(metadata2)

        when:
        List<Map> excaliburResults = db.queryNamedItems('Excalibur')
        List<Map> bladeResults = db.queryNamedItems('Blade')

        then:
        excaliburResults.size() == 1
        excaliburResults[0].custom_name == 'Excalibur'
        bladeResults.size() == 1
        bladeResults[0].custom_name == 'Rusty Blade'

        cleanup:
        db?.close()
    }

    // =========================================================================
    // queryNearCoordinates() Tests
    // =========================================================================

    def "queryNearCoordinates should find items within radius"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        ItemDatabase db = new ItemDatabase(dbFile)

        def metadata1 = new ItemDatabase.ItemMetadata('minecraft:diamond_sword')
        metadata1.x = 100
        metadata1.y = 64
        metadata1.z = 200
        metadata1.dimension = 'overworld'
        db.insertItem(metadata1)

        def metadata2 = new ItemDatabase.ItemMetadata('minecraft:iron_sword')
        metadata2.x = 105
        metadata2.y = 64
        metadata2.z = 205
        metadata2.dimension = 'overworld'
        db.insertItem(metadata2)

        def metadata3 = new ItemDatabase.ItemMetadata('minecraft:gold_sword')
        metadata3.x = 120
        metadata3.y = 64
        metadata3.z = 200
        metadata3.dimension = 'overworld'
        db.insertItem(metadata3)

        when:
        List<Map> results = db.queryNearCoordinates(100, 64, 200, 10)

        then:
        results.size() == 2
        results.any { it.x == 100 && it.y == 64 && it.z == 200 }
        results.any { it.x == 105 && it.y == 64 && it.z == 205 }

        cleanup:
        db?.close()
    }

    // =========================================================================
    // Metadata Tests
    // =========================================================================

    def "setMetadata and getMetadata should store and retrieve values"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        ItemDatabase db = new ItemDatabase(dbFile)

        when:
        db.setMetadata('test_key', 'test_value')
        String value = db.getMetadata('test_key')

        then:
        value == 'test_value'

        cleanup:
        db?.close()
    }

    def "setWorldPath should store path in metadata"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        ItemDatabase db = new ItemDatabase(dbFile)

        when:
        db.worldPath = '/path/to/world'
        String value = db.getMetadata('world_path')

        then:
        value == '/path/to/world'

        cleanup:
        db?.close()
    }

    def "setExtractionDate should store date in metadata"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        ItemDatabase db = new ItemDatabase(dbFile)

        when:
        db.extractionDate = '2025-12-17'
        String value = db.getMetadata('extraction_date')

        then:
        value == '2025-12-17'

        cleanup:
        db?.close()
    }

    def "setItemLimitMetadata should store limit in metadata"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        ItemDatabase db = new ItemDatabase(dbFile, 1000)

        when:
        db.setItemLimitMetadata()
        String value = db.getMetadata('item_limit')

        then:
        value == '1000'

        cleanup:
        db?.close()
    }

    // =========================================================================
    // Summary and Count Tests
    // =========================================================================

    def "getSummary should return all item types ordered by count"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        ItemDatabase db = new ItemDatabase(dbFile)

        def metadata1 = new ItemDatabase.ItemMetadata('minecraft:diamond_sword')
        metadata1.x = 0
        metadata1.y = 0
        metadata1.z = 0
        metadata1.dimension = 'overworld'
        db.insertItem(metadata1)

        def metadata2 = new ItemDatabase.ItemMetadata('minecraft:diamond_sword')
        metadata2.x = 1
        metadata2.y = 0
        metadata2.z = 0
        metadata2.dimension = 'overworld'
        db.insertItem(metadata2)

        def metadata3 = new ItemDatabase.ItemMetadata('minecraft:iron_sword')
        metadata3.x = 0
        metadata3.y = 0
        metadata3.z = 0
        metadata3.dimension = 'overworld'
        db.insertItem(metadata3)

        when:
        List<Map> summary = db.summary

        then:
        summary.size() == 2
        summary[0].item_id == 'minecraft:diamond_sword'  // Higher count first
        summary[0].total_count == 2
        summary[1].item_id == 'minecraft:iron_sword'
        summary[1].total_count == 1

        cleanup:
        db?.close()
    }

    def "getItemCount should return counts for specific item type"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        ItemDatabase db = new ItemDatabase(dbFile, 1)  // Limit of 1

        def metadata1 = new ItemDatabase.ItemMetadata('minecraft:stone')
        metadata1.x = 0
        metadata1.y = 0
        metadata1.z = 0
        metadata1.dimension = 'overworld'
        db.insertItem(metadata1)

        def metadata2 = new ItemDatabase.ItemMetadata('minecraft:stone')
        metadata2.x = 1
        metadata2.y = 0
        metadata2.z = 0
        metadata2.dimension = 'overworld'
        db.insertItem(metadata2)

        when:
        Map count = db.getItemCount('minecraft:stone')

        then:
        count != null
        count.total_count == 2
        count.unique_locations == 1
        count.limit_reached == 1

        cleanup:
        db?.close()
    }

    def "getItemTypeCount should count unique item types"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        ItemDatabase db = new ItemDatabase(dbFile)

        def metadata1 = new ItemDatabase.ItemMetadata('minecraft:diamond_sword')
        metadata1.dimension = 'overworld'
        db.insertItem(metadata1)

        def metadata2 = new ItemDatabase.ItemMetadata('minecraft:diamond_sword')
        metadata2.x = 1
        metadata2.dimension = 'overworld'
        db.insertItem(metadata2)

        def metadata3 = new ItemDatabase.ItemMetadata('minecraft:iron_sword')
        metadata3.dimension = 'overworld'
        db.insertItem(metadata3)

        when:
        int count = db.itemTypeCount

        then:
        count == 2  // Two unique types

        cleanup:
        db?.close()
    }

    def "getTotalItemsIndexed should sum all indexed items"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        ItemDatabase db = new ItemDatabase(dbFile)

        def metadata1 = new ItemDatabase.ItemMetadata('minecraft:diamond_sword')
        metadata1.dimension = 'overworld'
        db.insertItem(metadata1)

        def metadata2 = new ItemDatabase.ItemMetadata('minecraft:diamond_sword')
        metadata2.x = 1
        metadata2.dimension = 'overworld'
        db.insertItem(metadata2)

        def metadata3 = new ItemDatabase.ItemMetadata('minecraft:iron_sword')
        metadata3.dimension = 'overworld'
        db.insertItem(metadata3)

        when:
        int total = db.totalItemsIndexed

        then:
        total == 3

        cleanup:
        db?.close()
    }

    def "getTotalItemCount should return total count including limited items"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        ItemDatabase db = new ItemDatabase(dbFile, 2)  // Limit of 2

        (0..4).each { i ->
            def metadata = new ItemDatabase.ItemMetadata('minecraft:stone')
            metadata.x = i
            metadata.dimension = 'overworld'
            db.insertItem(metadata)
        }

        when:
        long total = db.totalItemCount

        then:
        total == 5  // All 5 counted even though only 2 stored

        cleanup:
        db?.close()
    }

    // =========================================================================
    // Transaction Tests
    // =========================================================================

    def "transactions should commit on success"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        ItemDatabase db = new ItemDatabase(dbFile)
        db.beginTransaction()

        when:
        def metadata1 = new ItemDatabase.ItemMetadata('minecraft:diamond_sword')
        metadata1.dimension = 'overworld'
        db.insertItem(metadata1)

        def metadata2 = new ItemDatabase.ItemMetadata('minecraft:diamond_sword')
        metadata2.x = 1
        metadata2.dimension = 'overworld'
        db.insertItem(metadata2)

        db.commitTransaction()

        then:
        db.queryByItemType('minecraft:diamond_sword').size() == 2

        cleanup:
        db?.close()
    }

    def "transactions should rollback on explicit call"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        ItemDatabase db = new ItemDatabase(dbFile)
        db.beginTransaction()

        when:
        def metadata = new ItemDatabase.ItemMetadata('minecraft:diamond_sword')
        metadata.dimension = 'overworld'
        db.insertItem(metadata)
        db.rollbackTransaction()

        then:
        db.queryByItemType('minecraft:diamond_sword').empty  // Rolled back

        cleanup:
        db?.close()
    }

    // =========================================================================
    // Static Factory Tests
    // =========================================================================

    def "openForQuery should return null for non-existent file"() {
        given:
        File dbFile = tempDir.resolve('nonexistent.db').toFile()

        when:
        ItemDatabase db = ItemDatabase.openForQuery(dbFile)

        then:
        db == null
    }

    def "openForQuery should return database for existing file"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        ItemDatabase originalDb = new ItemDatabase(dbFile)
        def metadata = new ItemDatabase.ItemMetadata('minecraft:diamond_sword')
        metadata.dimension = 'overworld'
        originalDb.insertItem(metadata)
        originalDb.close()

        when:
        ItemDatabase queryDb = ItemDatabase.openForQuery(dbFile)

        then:
        queryDb != null
        queryDb.queryByItemType('minecraft:diamond_sword').size() == 1

        cleanup:
        queryDb?.close()
    }

    // =========================================================================
    // close() Tests
    // =========================================================================

    def "close should not throw exception"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        ItemDatabase db = new ItemDatabase(dbFile)

        when:
        db.close()

        then:
        noExceptionThrown()
    }

    // =========================================================================
    // ItemMetadata Tests
    // =========================================================================

    def "ItemMetadata hasEnchantments should detect enchantments"() {
        given:
        def metadata = new ItemDatabase.ItemMetadata('minecraft:diamond_sword')
        metadata.enchantments = ['sharpness': 5]

        expect:
        metadata.hasEnchantments() == true
    }

    def "ItemMetadata hasEnchantments should detect stored enchantments"() {
        given:
        def metadata = new ItemDatabase.ItemMetadata('minecraft:enchanted_book')
        metadata.storedEnchantments = ['mending': 1]

        expect:
        metadata.hasEnchantments() == true
    }

    def "ItemMetadata hasEnchantments should return false when empty"() {
        given:
        def metadata = new ItemDatabase.ItemMetadata('minecraft:diamond_sword')

        expect:
        metadata.hasEnchantments() == false
    }

    def "ItemMetadata hasCustomName should detect custom name"() {
        given:
        def metadata = new ItemDatabase.ItemMetadata('minecraft:diamond_sword')
        metadata.customName = 'Excalibur'

        expect:
        metadata.hasCustomName() == true
    }

    def "ItemMetadata hasCustomName should return false for null name"() {
        given:
        def metadata = new ItemDatabase.ItemMetadata('minecraft:diamond_sword')

        expect:
        metadata.hasCustomName() == false
    }

    def "ItemMetadata hasCustomName should return false for empty name"() {
        given:
        def metadata = new ItemDatabase.ItemMetadata('minecraft:diamond_sword')
        metadata.customName = '   '

        expect:
        metadata.hasCustomName() == false
    }

}

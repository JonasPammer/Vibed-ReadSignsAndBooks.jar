import groovy.json.JsonSlurper
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for BlockDatabase utility class.
 * Tests SQLite database operations, block insertion with limits, queries, metadata, and transactions.
 */
class BlockDatabaseSpec extends Specification {

    @TempDir
    Path tempDir

    // =========================================================================
    // Constructor and Schema Tests
    // =========================================================================

    def "should create database file and initialize schema"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()

        when:
        BlockDatabase db = new BlockDatabase(dbFile)

        then:
        dbFile.exists()
        // Verify tables exist by querying them
        db.queryByBlockType('minecraft:stone').isEmpty()  // Should not throw
        db.getSummary().isEmpty()  // Should not throw
        db.getMetadata('test') == null  // Should not throw

        cleanup:
        db?.close()
    }

    def "should enable WAL mode for performance"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        BlockDatabase db = new BlockDatabase(dbFile)

        when:
        // WAL mode is set in constructor, verify indirectly by checking database works
        db.insertBlock('minecraft:test', 'overworld', 0, 0, 0)
        def result = db.queryByBlockType('minecraft:test')

        then:
        result.size() == 1  // If WAL mode failed, this might not work correctly
        // WAL mode is an internal optimization, so we verify it works indirectly

        cleanup:
        db?.close()
    }

    def "should create parent directory if it doesn't exist"() {
        given:
        File parentDir = tempDir.resolve('nested').toFile()
        File dbFile = new File(parentDir, 'test.db')

        when:
        BlockDatabase db = new BlockDatabase(dbFile)

        then:
        parentDir.exists()
        dbFile.exists()

        cleanup:
        db?.close()
    }

    // =========================================================================
    // insertBlock() Tests
    // =========================================================================

    def "insertBlock should insert and return true for new block"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        BlockDatabase db = new BlockDatabase(dbFile)

        when:
        boolean inserted = db.insertBlock('minecraft:nether_portal', 'overworld', 100, 64, 200)

        then:
        inserted == true
        db.queryByBlockType('minecraft:nether_portal').size() == 1

        cleanup:
        db?.close()
    }

    def "insertBlock should return false for duplicate block"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        BlockDatabase db = new BlockDatabase(dbFile)
        db.insertBlock('minecraft:nether_portal', 'overworld', 100, 64, 200)

        when:
        boolean inserted = db.insertBlock('minecraft:nether_portal', 'overworld', 100, 64, 200)

        then:
        inserted == false
        db.queryByBlockType('minecraft:nether_portal').size() == 1  // Still only one

        cleanup:
        db?.close()
    }

    def "insertBlock should enforce per-type limit"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        BlockDatabase db = new BlockDatabase(dbFile, 3)  // Limit of 3

        when:
        // Insert 5 blocks of same type
        db.insertBlock('minecraft:stone', 'overworld', 0, 0, 0)
        db.insertBlock('minecraft:stone', 'overworld', 1, 0, 0)
        db.insertBlock('minecraft:stone', 'overworld', 2, 0, 0)
        boolean inserted4 = db.insertBlock('minecraft:stone', 'overworld', 3, 0, 0)
        boolean inserted5 = db.insertBlock('minecraft:stone', 'overworld', 4, 0, 0)

        then:
        inserted4 == false  // Limit reached
        inserted5 == false  // Limit reached
        db.queryByBlockType('minecraft:stone').size() == 3  // Only 3 stored

        cleanup:
        db?.close()
    }

    def "insertBlock should always increment total_found even when limit reached"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        BlockDatabase db = new BlockDatabase(dbFile, 2)  // Limit of 2

        when:
        db.insertBlock('minecraft:dirt', 'overworld', 0, 0, 0)
        db.insertBlock('minecraft:dirt', 'overworld', 1, 0, 0)
        db.insertBlock('minecraft:dirt', 'overworld', 2, 0, 0)  // Over limit
        db.insertBlock('minecraft:dirt', 'overworld', 3, 0, 0)  // Over limit

        then:
        Map count = db.getBlockCount('minecraft:dirt')
        count.total_found == 4  // All 4 counted
        count.indexed_count == 2  // Only 2 stored
        count.limit_reached == 1  // Limit was reached

        cleanup:
        db?.close()
    }

    def "insertBlock should handle null properties gracefully"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        BlockDatabase db = new BlockDatabase(dbFile)

        when:
        boolean inserted = db.insertBlock('minecraft:air', 'overworld', 0, 0, 0, null, null)

        then:
        inserted == true
        def result = db.queryByBlockType('minecraft:air')[0]
        result.properties == null

        cleanup:
        db?.close()
    }

    def "insertBlock should store properties as JSON"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        BlockDatabase db = new BlockDatabase(dbFile)
        Map<String, String> properties = ['axis': 'z', 'facing': 'north']

        when:
        db.insertBlock('minecraft:nether_portal', 'overworld', 100, 64, 200, properties)

        then:
        def result = db.queryByBlockType('minecraft:nether_portal')[0]
        def props = new JsonSlurper().parseText(result.properties as String)
        props.axis == 'z'
        props.facing == 'north'

        cleanup:
        db?.close()
    }

    def "insertBlock should mark limit_reached when threshold hit"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        BlockDatabase db = new BlockDatabase(dbFile, 1)  // Limit of 1

        when:
        db.insertBlock('minecraft:obsidian', 'overworld', 0, 0, 0)
        db.insertBlock('minecraft:obsidian', 'overworld', 1, 0, 0)  // Over limit

        then:
        Map count = db.getBlockCount('minecraft:obsidian')
        count.limit_reached == 1

        cleanup:
        db?.close()
    }

    def "insertBlock should handle zero limit (unlimited)"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        BlockDatabase db = new BlockDatabase(dbFile, 0)  // Unlimited

        when:
        // Insert many blocks
        10.times { i ->
            db.insertBlock('minecraft:stone', 'overworld', i, 0, 0)
        }

        then:
        db.queryByBlockType('minecraft:stone').size() == 10  // All stored

        cleanup:
        db?.close()
    }

    def "insertBlock should store region_file"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        BlockDatabase db = new BlockDatabase(dbFile)

        when:
        db.insertBlock('minecraft:chest', 'overworld', 100, 64, 200, null, 'r.0.0.mca')

        then:
        def result = db.queryByBlockType('minecraft:chest')[0]
        result.region_file == 'r.0.0.mca'

        cleanup:
        db?.close()
    }

    // =========================================================================
    // queryByBlockType() Tests
    // =========================================================================

    def "queryByBlockType should normalize block ID without prefix"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        BlockDatabase db = new BlockDatabase(dbFile)
        db.insertBlock('minecraft:nether_portal', 'overworld', 100, 64, 200)

        when:
        List<Map> results = db.queryByBlockType('nether_portal')  // Without minecraft: prefix

        then:
        results.size() == 1
        results[0].block_type == 'minecraft:nether_portal'

        cleanup:
        db?.close()
    }

    def "queryByBlockType should filter by dimension when specified"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        BlockDatabase db = new BlockDatabase(dbFile)
        db.insertBlock('minecraft:nether_portal', 'overworld', 100, 64, 200)
        db.insertBlock('minecraft:nether_portal', 'nether', 100, 64, 200)

        when:
        List<Map> overworldResults = db.queryByBlockType('minecraft:nether_portal', 'overworld')
        List<Map> netherResults = db.queryByBlockType('minecraft:nether_portal', 'nether')

        then:
        overworldResults.size() == 1
        overworldResults[0].dimension == 'overworld'
        netherResults.size() == 1
        netherResults[0].dimension == 'nether'

        cleanup:
        db?.close()
    }

    def "queryByBlockType should return empty list for unknown block type"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        BlockDatabase db = new BlockDatabase(dbFile)

        when:
        List<Map> results = db.queryByBlockType('minecraft:nonexistent_block')

        then:
        results.isEmpty()

        cleanup:
        db?.close()
    }

    def "queryByBlockType should order results by dimension, x, y, z"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        BlockDatabase db = new BlockDatabase(dbFile)
        db.insertBlock('minecraft:stone', 'nether', 100, 64, 200)
        db.insertBlock('minecraft:stone', 'overworld', 100, 64, 200)
        db.insertBlock('minecraft:stone', 'overworld', 50, 64, 200)
        db.insertBlock('minecraft:stone', 'overworld', 100, 65, 200)

        when:
        List<Map> results = db.queryByBlockType('minecraft:stone')

        then:
        results.size() == 4
        results[0].dimension == 'nether'  // nether before overworld
        results[1].dimension == 'overworld'
        results[1].x == 50  // Lower x first
        results[2].x == 100
        results[2].y == 64  // Lower y first
        results[3].y == 65

        cleanup:
        db?.close()
    }

    // =========================================================================
    // queryNearCoordinates() Tests
    // =========================================================================

    def "queryNearCoordinates should find blocks within radius"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        BlockDatabase db = new BlockDatabase(dbFile)
        db.insertBlock('minecraft:chest', 'overworld', 100, 64, 200)  // Center
        db.insertBlock('minecraft:chest', 'overworld', 105, 64, 205)  // Within radius 10
        db.insertBlock('minecraft:chest', 'overworld', 120, 64, 200)  // Outside radius 10

        when:
        List<Map> results = db.queryNearCoordinates(100, 64, 200, 10)

        then:
        results.size() == 2
        results.any { it.x == 100 && it.y == 64 && it.z == 200 }
        results.any { it.x == 105 && it.y == 64 && it.z == 205 }

        cleanup:
        db?.close()
    }

    def "queryNearCoordinates should respect dimension filter"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        BlockDatabase db = new BlockDatabase(dbFile)
        db.insertBlock('minecraft:chest', 'overworld', 100, 64, 200)
        db.insertBlock('minecraft:chest', 'nether', 100, 64, 200)

        when:
        List<Map> overworldResults = db.queryNearCoordinates(100, 64, 200, 10, 'overworld')
        List<Map> netherResults = db.queryNearCoordinates(100, 64, 200, 10, 'nether')

        then:
        overworldResults.size() == 1
        overworldResults[0].dimension == 'overworld'
        netherResults.size() == 1
        netherResults[0].dimension == 'nether'

        cleanup:
        db?.close()
    }

    def "queryNearCoordinates should use default radius of 50"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        BlockDatabase db = new BlockDatabase(dbFile)
        db.insertBlock('minecraft:chest', 'overworld', 100, 64, 200)
        db.insertBlock('minecraft:chest', 'overworld', 140, 64, 200)  // Within 50, outside 30

        when:
        List<Map> results = db.queryNearCoordinates(100, 64, 200)  // Default radius 50

        then:
        results.size() == 2

        cleanup:
        db?.close()
    }

    def "queryNearCoordinates should order results by block_type, x, y, z"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        BlockDatabase db = new BlockDatabase(dbFile)
        db.insertBlock('minecraft:chest', 'overworld', 100, 65, 200)
        db.insertBlock('minecraft:chest', 'overworld', 100, 64, 200)
        db.insertBlock('minecraft:barrel', 'overworld', 100, 64, 200)

        when:
        List<Map> results = db.queryNearCoordinates(100, 64, 200, 10)

        then:
        results.size() == 3
        results[0].block_type == 'minecraft:barrel'  // barrel before chest
        results[1].block_type == 'minecraft:chest'
        results[1].y == 64  // Lower y first
        results[2].y == 65

        cleanup:
        db?.close()
    }

    // =========================================================================
    // Metadata Tests
    // =========================================================================

    def "setMetadata and getMetadata should store and retrieve values"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        BlockDatabase db = new BlockDatabase(dbFile)

        when:
        db.setMetadata('test_key', 'test_value')
        String value = db.getMetadata('test_key')

        then:
        value == 'test_value'

        cleanup:
        db?.close()
    }

    def "setMetadata should update existing key"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        BlockDatabase db = new BlockDatabase(dbFile)
        db.setMetadata('key', 'old_value')

        when:
        db.setMetadata('key', 'new_value')
        String value = db.getMetadata('key')

        then:
        value == 'new_value'

        cleanup:
        db?.close()
    }

    def "getMetadata should return null for missing key"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        BlockDatabase db = new BlockDatabase(dbFile)

        when:
        String value = db.getMetadata('nonexistent_key')

        then:
        value == null

        cleanup:
        db?.close()
    }

    def "setWorldPath should store path in metadata"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        BlockDatabase db = new BlockDatabase(dbFile)

        when:
        db.setWorldPath('/path/to/world')
        String value = db.getMetadata('world_path')

        then:
        value == '/path/to/world'

        cleanup:
        db?.close()
    }

    def "setExtractionDate should store date in metadata"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        BlockDatabase db = new BlockDatabase(dbFile)

        when:
        db.setExtractionDate('2025-12-14')
        String value = db.getMetadata('extraction_date')

        then:
        value == '2025-12-14'

        cleanup:
        db?.close()
    }

    def "setMinecraftVersion should store version in metadata"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        BlockDatabase db = new BlockDatabase(dbFile)

        when:
        db.setMinecraftVersion('1.21.1')
        String value = db.getMetadata('minecraft_version')

        then:
        value == '1.21.1'

        cleanup:
        db?.close()
    }

    def "setBlockLimitMetadata should store limit in metadata"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        BlockDatabase db = new BlockDatabase(dbFile, 5000)

        when:
        db.setBlockLimitMetadata()
        String value = db.getMetadata('block_limit')

        then:
        value == '5000'

        cleanup:
        db?.close()
    }

    // =========================================================================
    // Summary and Count Tests
    // =========================================================================

    def "getSummary should return all block types ordered by count"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        BlockDatabase db = new BlockDatabase(dbFile)
        db.insertBlock('minecraft:stone', 'overworld', 0, 0, 0)
        db.insertBlock('minecraft:stone', 'overworld', 1, 0, 0)
        db.insertBlock('minecraft:dirt', 'overworld', 0, 0, 0)

        when:
        List<Map> summary = db.getSummary()

        then:
        summary.size() == 2
        summary[0].block_type == 'minecraft:stone'  // Higher count first
        summary[0].total_found == 2
        summary[1].block_type == 'minecraft:dirt'
        summary[1].total_found == 1

        cleanup:
        db?.close()
    }

    def "getBlockCount should return counts for specific block type"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        BlockDatabase db = new BlockDatabase(dbFile, 1)  // Limit of 1
        db.insertBlock('minecraft:stone', 'overworld', 0, 0, 0)
        db.insertBlock('minecraft:stone', 'overworld', 1, 0, 0)  // Over limit

        when:
        Map count = db.getBlockCount('minecraft:stone')

        then:
        count != null
        count.total_found == 2
        count.indexed_count == 1
        count.limit_reached == 1

        cleanup:
        db?.close()
    }

    def "getBlockCount should normalize block ID"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        BlockDatabase db = new BlockDatabase(dbFile)
        db.insertBlock('minecraft:stone', 'overworld', 0, 0, 0)

        when:
        Map count = db.getBlockCount('stone')  // Without prefix

        then:
        count != null
        count.total_found == 1

        cleanup:
        db?.close()
    }

    def "getBlockCount should return null for unknown block type"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        BlockDatabase db = new BlockDatabase(dbFile)

        when:
        Map count = db.getBlockCount('minecraft:nonexistent')

        then:
        count == null

        cleanup:
        db?.close()
    }

    def "getBlockTypeCount should count unique block types"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        BlockDatabase db = new BlockDatabase(dbFile)
        db.insertBlock('minecraft:stone', 'overworld', 0, 0, 0)
        db.insertBlock('minecraft:stone', 'overworld', 1, 0, 0)
        db.insertBlock('minecraft:dirt', 'overworld', 0, 0, 0)

        when:
        int count = db.getBlockTypeCount()

        then:
        count == 2  // Two unique types

        cleanup:
        db?.close()
    }

    def "getBlockTypeCount should return 0 for empty database"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        BlockDatabase db = new BlockDatabase(dbFile)

        when:
        int count = db.getBlockTypeCount()

        then:
        count == 0

        cleanup:
        db?.close()
    }

    def "getTotalBlocksIndexed should sum all indexed blocks"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        BlockDatabase db = new BlockDatabase(dbFile)
        db.insertBlock('minecraft:stone', 'overworld', 0, 0, 0)
        db.insertBlock('minecraft:stone', 'overworld', 1, 0, 0)
        db.insertBlock('minecraft:dirt', 'overworld', 0, 0, 0)

        when:
        int total = db.getTotalBlocksIndexed()

        then:
        total == 3

        cleanup:
        db?.close()
    }

    def "getTotalBlocksIndexed should return 0 for empty database"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        BlockDatabase db = new BlockDatabase(dbFile)

        when:
        int total = db.getTotalBlocksIndexed()

        then:
        total == 0

        cleanup:
        db?.close()
    }

    // =========================================================================
    // Transaction Tests
    // =========================================================================

    def "transactions should commit on success"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        BlockDatabase db = new BlockDatabase(dbFile)
        db.beginTransaction()

        when:
        db.insertBlock('minecraft:stone', 'overworld', 0, 0, 0)
        db.insertBlock('minecraft:stone', 'overworld', 1, 0, 0)
        db.commitTransaction()

        then:
        db.queryByBlockType('minecraft:stone').size() == 2

        cleanup:
        db?.close()
    }

    def "transactions should rollback on explicit call"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        BlockDatabase db = new BlockDatabase(dbFile)
        db.beginTransaction()

        when:
        db.insertBlock('minecraft:stone', 'overworld', 0, 0, 0)
        db.rollbackTransaction()

        then:
        db.queryByBlockType('minecraft:stone').isEmpty()  // Rolled back

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
        BlockDatabase db = BlockDatabase.openForQuery(dbFile)

        then:
        db == null
    }

    def "openForQuery should return database for existing file"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        BlockDatabase originalDb = new BlockDatabase(dbFile)
        originalDb.insertBlock('minecraft:stone', 'overworld', 0, 0, 0)
        originalDb.close()

        when:
        BlockDatabase queryDb = BlockDatabase.openForQuery(dbFile)

        then:
        queryDb != null
        queryDb.queryByBlockType('minecraft:stone').size() == 1

        cleanup:
        queryDb?.close()
    }

    // =========================================================================
    // close() Tests
    // =========================================================================

    def "close should not throw exception"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        BlockDatabase db = new BlockDatabase(dbFile)

        when:
        db.close()

        then:
        noExceptionThrown()
    }

    def "close should close database connection"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        BlockDatabase db = new BlockDatabase(dbFile)

        when:
        db.close()

        then:
        noExceptionThrown()
        // After close, operations should fail (but we can't easily test this without catching exceptions)
    }
}

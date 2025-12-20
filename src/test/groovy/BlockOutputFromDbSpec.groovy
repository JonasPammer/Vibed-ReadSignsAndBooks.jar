import groovy.json.JsonSlurper
import groovy.sql.Sql
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Tests for error-path handling in Main.writeBlock*FromDb() methods.
 * Specifically tests fallback behavior when properties JSON is corrupted.
 */
class BlockOutputFromDbSpec extends Specification {

    @TempDir
    Path tempDir

    def setup() {
        Main.resetState()
    }

    def cleanup() {
        Main.resetState()
    }

    // =========================================================================
    // writeBlockCsvFromDb error-path tests
    // =========================================================================

    def "writeBlockCsvFromDb should fallback to raw string when properties JSON is corrupted"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        File outputDir = tempDir.resolve('output').toFile()
        outputDir.mkdirs()

        BlockDatabase db = new BlockDatabase(dbFile)
        db.insertBlock('minecraft:chest', 'overworld', 100, 64, 200, [facing: 'north'], 'r.0.0.mca')
        db.insertBlock('minecraft:barrel', 'overworld', 50, 32, 100, null, 'r.0.1.mca')
        db.close()

        // Corrupt the properties column via direct SQL
        Sql sql = Sql.newInstance("jdbc:sqlite:${dbFile.absolutePath}", 'org.sqlite.JDBC')
        sql.executeUpdate("UPDATE blocks SET properties = 'not valid json {{{' WHERE block_type = 'minecraft:chest'")
        sql.close()

        // Reopen for reading
        BlockDatabase readDb = BlockDatabase.openForQuery(dbFile)

        when:
        Main.writeBlockCsvFromDb(outputDir.absolutePath, readDb)

        then:
        File csvFile = new File(outputDir, 'blocks.csv')
        csvFile.exists()
        String content = csvFile.text
        // Should contain the corrupted row with raw string fallback
        content.contains('minecraft:chest')
        content.contains('minecraft:barrel')
        content.contains('not valid json {{{')  // Raw fallback for corrupted JSON
        // Lines should still be valid CSV format
        content.readLines().size() == 3  // Header + 2 data rows

        cleanup:
        readDb?.close()
    }

    def "writeBlockCsvFromDb should handle empty properties gracefully"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        File outputDir = tempDir.resolve('output').toFile()
        outputDir.mkdirs()

        BlockDatabase db = new BlockDatabase(dbFile)
        db.insertBlock('minecraft:stone', 'overworld', 0, 0, 0, null, null)
        db.close()

        BlockDatabase readDb = BlockDatabase.openForQuery(dbFile)

        when:
        Main.writeBlockCsvFromDb(outputDir.absolutePath, readDb)

        then:
        File csvFile = new File(outputDir, 'blocks.csv')
        csvFile.exists()
        String content = csvFile.text
        content.contains('minecraft:stone')
        // Empty properties should result in empty field, not exception
        content.readLines()[1].contains('minecraft:stone,overworld,0,0,0,,')

        cleanup:
        readDb?.close()
    }

    // =========================================================================
    // writeBlockTxtFromDb error-path tests
    // =========================================================================

    def "writeBlockTxtFromDb should skip corrupted properties without exception"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        File outputDir = tempDir.resolve('output').toFile()
        outputDir.mkdirs()

        BlockDatabase db = new BlockDatabase(dbFile)
        db.insertBlock('minecraft:chest', 'overworld', 100, 64, 200, [facing: 'north'], 'r.0.0.mca')
        db.close()

        // Corrupt the properties
        Sql sql = Sql.newInstance("jdbc:sqlite:${dbFile.absolutePath}", 'org.sqlite.JDBC')
        sql.executeUpdate("UPDATE blocks SET properties = 'corrupted_json' WHERE block_type = 'minecraft:chest'")
        sql.close()

        BlockDatabase readDb = BlockDatabase.openForQuery(dbFile)

        when:
        Main.writeBlockTxtFromDb(outputDir.absolutePath, readDb)

        then:
        File txtFile = new File(outputDir, 'blocks.txt')
        txtFile.exists()
        String content = txtFile.text
        // Report should still be generated
        content.contains('Block Search Report')
        content.contains('minecraft:chest')
        content.contains('overworld: (100, 64, 200)')
        // No properties should appear (since parsing failed, propsStr stays empty)
        !content.contains('facing=north')
        !content.contains('corrupted_json')

        cleanup:
        readDb?.close()
    }

    def "writeBlockTxtFromDb should produce valid report for empty database"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        File outputDir = tempDir.resolve('output').toFile()
        outputDir.mkdirs()

        BlockDatabase db = new BlockDatabase(dbFile)
        db.close()

        BlockDatabase readDb = BlockDatabase.openForQuery(dbFile)

        when:
        Main.writeBlockTxtFromDb(outputDir.absolutePath, readDb)

        then:
        File txtFile = new File(outputDir, 'blocks.txt')
        txtFile.exists()
        String content = txtFile.text
        content.contains('Block Search Report')
        content.contains('Total blocks indexed: 0')

        cleanup:
        readDb?.close()
    }

    // =========================================================================
    // writeBlockJsonFromDb error-path tests
    // =========================================================================

    def "writeBlockJsonFromDb should produce empty properties object when JSON is corrupted"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        File outputDir = tempDir.resolve('output').toFile()
        outputDir.mkdirs()

        BlockDatabase db = new BlockDatabase(dbFile)
        db.insertBlock('minecraft:chest', 'overworld', 100, 64, 200, [facing: 'north'], 'r.0.0.mca')
        db.close()

        // Corrupt the properties
        Sql sql = Sql.newInstance("jdbc:sqlite:${dbFile.absolutePath}", 'org.sqlite.JDBC')
        sql.executeUpdate("UPDATE blocks SET properties = '{{invalid}}' WHERE block_type = 'minecraft:chest'")
        sql.close()

        BlockDatabase readDb = BlockDatabase.openForQuery(dbFile)

        when:
        Main.writeBlockJsonFromDb(outputDir.absolutePath, readDb)

        then:
        File jsonFile = new File(outputDir, 'blocks.json')
        jsonFile.exists()

        // Parse the output JSON
        def json = new JsonSlurper().parseText(jsonFile.text)

        // JSON should still be valid
        json.blocks.size() == 1
        json.blocks[0].type == 'minecraft:chest'
        json.blocks[0].coordinates.x == 100
        json.blocks[0].coordinates.y == 64
        json.blocks[0].coordinates.z == 200
        // Properties should be empty object (fallback when parse fails)
        json.blocks[0].properties == [:]

        cleanup:
        readDb?.close()
    }

    def "writeBlockJsonFromDb should produce valid JSON even with mixed valid and corrupted properties"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        File outputDir = tempDir.resolve('output').toFile()
        outputDir.mkdirs()

        BlockDatabase db = new BlockDatabase(dbFile)
        db.insertBlock('minecraft:chest', 'overworld', 100, 64, 200, [facing: 'north'], 'r.0.0.mca')
        db.insertBlock('minecraft:barrel', 'overworld', 50, 32, 100, [open: 'true'], 'r.0.1.mca')
        db.insertBlock('minecraft:hopper', 'nether', 0, 0, 0, null, null)
        db.close()

        // Corrupt only one row's properties
        Sql sql = Sql.newInstance("jdbc:sqlite:${dbFile.absolutePath}", 'org.sqlite.JDBC')
        sql.executeUpdate("UPDATE blocks SET properties = 'broken' WHERE block_type = 'minecraft:chest'")
        sql.close()

        BlockDatabase readDb = BlockDatabase.openForQuery(dbFile)

        when:
        Main.writeBlockJsonFromDb(outputDir.absolutePath, readDb)

        then:
        File jsonFile = new File(outputDir, 'blocks.json')
        jsonFile.exists()

        def json = new JsonSlurper().parseText(jsonFile.text)

        // Should have all 3 blocks
        json.blocks.size() == 3

        // Find each block and verify properties
        def barrel = json.blocks.find { it.type == 'minecraft:barrel' }
        def chest = json.blocks.find { it.type == 'minecraft:chest' }
        def hopper = json.blocks.find { it.type == 'minecraft:hopper' }

        barrel.properties.open == 'true'  // Valid properties preserved
        chest.properties == [:]  // Corrupted properties become empty
        hopper.properties == [:]  // Null properties also empty

        cleanup:
        readDb?.close()
    }

    def "writeBlockJsonFromDb should produce valid JSON for empty database"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        File outputDir = tempDir.resolve('output').toFile()
        outputDir.mkdirs()

        BlockDatabase db = new BlockDatabase(dbFile)
        db.close()

        BlockDatabase readDb = BlockDatabase.openForQuery(dbFile)

        when:
        Main.writeBlockJsonFromDb(outputDir.absolutePath, readDb)

        then:
        File jsonFile = new File(outputDir, 'blocks.json')
        jsonFile.exists()

        def json = new JsonSlurper().parseText(jsonFile.text)
        json.blocks == []
        json.summary.total_blocks == 0
        json.summary.mode == 'index-all'

        cleanup:
        readDb?.close()
    }

    // =========================================================================
    // Edge case tests
    // =========================================================================

    def "all output methods should handle special characters in properties"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        File outputDir = tempDir.resolve('output').toFile()
        outputDir.mkdirs()

        BlockDatabase db = new BlockDatabase(dbFile)
        // Properties with special characters that might break JSON or CSV
        db.insertBlock('minecraft:sign', 'overworld', 0, 0, 0, [text: 'Hello "World"', color: 'red'], 'r.0.0.mca')
        db.close()

        BlockDatabase readDb = BlockDatabase.openForQuery(dbFile)

        when:
        Main.writeBlockCsvFromDb(outputDir.absolutePath, readDb)
        Main.writeBlockJsonFromDb(outputDir.absolutePath, readDb)
        Main.writeBlockTxtFromDb(outputDir.absolutePath, readDb)

        then:
        // All files should be created without throwing
        new File(outputDir, 'blocks.csv').exists()
        new File(outputDir, 'blocks.json').exists()
        new File(outputDir, 'blocks.txt').exists()

        // JSON should still be parseable
        def json = new JsonSlurper().parseText(new File(outputDir, 'blocks.json').text)
        json.blocks.size() == 1
        json.blocks[0].properties.text == 'Hello "World"'
        json.blocks[0].properties.color == 'red'

        cleanup:
        readDb?.close()
    }

    def "all output methods should handle unicode characters in properties"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        File outputDir = tempDir.resolve('output').toFile()
        outputDir.mkdirs()

        BlockDatabase db = new BlockDatabase(dbFile)
        // Properties with unicode
        db.insertBlock('minecraft:sign', 'overworld', 0, 0, 0, [text: 'こんにちは', emoji: '🎮'], 'r.0.0.mca')
        db.close()

        BlockDatabase readDb = BlockDatabase.openForQuery(dbFile)

        when:
        Main.writeBlockCsvFromDb(outputDir.absolutePath, readDb)
        Main.writeBlockJsonFromDb(outputDir.absolutePath, readDb)
        Main.writeBlockTxtFromDb(outputDir.absolutePath, readDb)

        then:
        // All files should be created
        new File(outputDir, 'blocks.csv').exists()
        new File(outputDir, 'blocks.json').exists()
        new File(outputDir, 'blocks.txt').exists()

        // JSON should preserve unicode
        def json = new JsonSlurper().parseText(new File(outputDir, 'blocks.json').text)
        json.blocks[0].properties.text == 'こんにちは'
        json.blocks[0].properties.emoji == '🎮'

        // TXT should also have the unicode
        String txtContent = new File(outputDir, 'blocks.txt').text
        txtContent.contains('こんにちは')

        cleanup:
        readDb?.close()
    }

    def "output methods should handle very long properties strings"() {
        given:
        File dbFile = tempDir.resolve('test.db').toFile()
        File outputDir = tempDir.resolve('output').toFile()
        outputDir.mkdirs()

        BlockDatabase db = new BlockDatabase(dbFile)
        String longValue = 'a' * 10000  // 10K character string
        db.insertBlock('minecraft:command_block', 'overworld', 0, 0, 0, [command: longValue], 'r.0.0.mca')
        db.close()

        BlockDatabase readDb = BlockDatabase.openForQuery(dbFile)

        when:
        Main.writeBlockCsvFromDb(outputDir.absolutePath, readDb)
        Main.writeBlockJsonFromDb(outputDir.absolutePath, readDb)
        Main.writeBlockTxtFromDb(outputDir.absolutePath, readDb)

        then:
        // All files should be created
        new File(outputDir, 'blocks.csv').exists()
        new File(outputDir, 'blocks.json').exists()
        new File(outputDir, 'blocks.txt').exists()

        // CSV should contain the long value
        String csvContent = new File(outputDir, 'blocks.csv').text
        csvContent.contains('a' * 100)  // At least part of it

        // JSON should be valid and contain the long value
        def json = new JsonSlurper().parseText(new File(outputDir, 'blocks.json').text)
        json.blocks[0].properties.command.length() == 10000

        cleanup:
        readDb?.close()
    }
}

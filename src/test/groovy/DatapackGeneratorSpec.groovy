import groovy.json.JsonSlurper
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import java.nio.file.Path

/**
 * Unit tests for DatapackGenerator utility class.
 * Tests datapack structure creation, pack.mcmeta generation, and version mapping.
 */
class DatapackGeneratorSpec extends Specification {

    @TempDir
    Path tempDir

    // =========================================================================
    // setupDatapackStructure() Tests
    // =========================================================================

    def "setupDatapackStructure should create functions directory for pre-1.21"() {
        given:
        String baseDir = tempDir
        String outputFolder = 'output'

        when:
        File functionDir = DatapackGenerator.setupDatapackStructure(baseDir, outputFolder, '1_13')

        then:
        functionDir.exists()
        functionDir.directory
        functionDir.name == 'functions'  // Plural for pre-1.21
        functionDir.parentFile.name == 'readbooks'
        functionDir.parentFile.parentFile.name == 'data'
    }

    def "setupDatapackStructure should create function directory for 1.21"() {
        given:
        String baseDir = tempDir
        String outputFolder = 'output'

        when:
        File functionDir = DatapackGenerator.setupDatapackStructure(baseDir, outputFolder, '1_21')

        then:
        functionDir.exists()
        functionDir.directory
        functionDir.name == 'function'  // Singular for 1.21+
        functionDir.parentFile.name == 'readbooks'
    }

    def "setupDatapackStructure should create correct datapack name"() {
        given:
        String baseDir = tempDir
        String outputFolder = 'output'

        when:
        File functionDir = DatapackGenerator.setupDatapackStructure(baseDir, outputFolder, '1_20_5')

        then:
        functionDir.absolutePath.contains('readbooks_datapack_1_20_5')
    }

    // =========================================================================
    // writePackMcmeta() Tests
    // =========================================================================

    def "writePackMcmeta should create pack.mcmeta file"() {
        given:
        String baseDir = tempDir
        String outputFolder = 'output'
        DatapackGenerator.setupDatapackStructure(baseDir, outputFolder, '1_13')

        when:
        DatapackGenerator.writePackMcmeta(baseDir, outputFolder, '1_13', 4, 'Test description')

        then:
        File packMcmeta = new File(tempDir.toFile(), "output${File.separator}readbooks_datapack_1_13${File.separator}pack.mcmeta")
        packMcmeta.exists()
        // JSON is pretty-printed, so check for formatted version
        packMcmeta.text.contains('"pack_format": 4') || packMcmeta.text.contains('"pack_format":4')
        packMcmeta.text.contains('"description": "Test description"') || packMcmeta.text.contains('"description":"Test description"')
    }

    def "writePackMcmeta should create valid JSON"() {
        given:
        String baseDir = tempDir
        String outputFolder = 'output'
        DatapackGenerator.setupDatapackStructure(baseDir, outputFolder, '1_21')

        when:
        DatapackGenerator.writePackMcmeta(baseDir, outputFolder, '1_21', 48, 'Test')

        then:
        File packMcmeta = new File(tempDir.toFile(), "output${File.separator}readbooks_datapack_1_21${File.separator}pack.mcmeta")
        def json = new JsonSlurper().parseText(packMcmeta.text)
        json.pack.pack_format == 48
        json.pack.description == 'Test'
    }

    // =========================================================================
    // getPackFormat() Tests
    // =========================================================================

    @Unroll
    def "getPackFormat should return #expectedFormat for version #version"() {
        expect:
        DatapackGenerator.getPackFormat(version) == expectedFormat

        where:
        version  | expectedFormat
        '1_13'   | 4
        '1_14'   | 4
        '1_20'   | 15
        '1_20_5' | 41
        '1_21'   | 48
    }

    def "getPackFormat should default to 48 for unknown version"() {
        expect:
        DatapackGenerator.getPackFormat('unknown') == 48
    }

    // =========================================================================
    // getVersionDescription() Tests
    // =========================================================================

    @Unroll
    def "getVersionDescription should return correct description for #version"() {
        when:
        String description = DatapackGenerator.getVersionDescription(version)

        then:
        description.contains(expectedVersionRange)
        description.contains(expectedPackFormat.toString())
        description.contains(expectedDirName)

        where:
        version  | expectedVersionRange | expectedPackFormat | expectedDirName
        '1_13'   | '1.13-1.14.3'       | 4                  | 'functions/'
        '1_14'   | '1.14.4-1.19.4'     | 4                  | 'functions/'
        '1_20'   | '1.20-1.20.4'       | 15                 | 'functions/'
        '1_20_5' | '1.20.5-1.20.6'     | 41                 | 'functions/'
        '1_21'   | '1.21+'             | 48                 | 'function/'
    }

    def "getVersionDescription should return generic description for unknown version"() {
        when:
        String description = DatapackGenerator.getVersionDescription('unknown')

        then:
        description == 'Minecraft unknown'
    }

    // =========================================================================
    // getSupportedVersions() Tests
    // =========================================================================

    def "getSupportedVersions should return list of supported versions"() {
        when:
        List<String> versions = DatapackGenerator.supportedVersions

        then:
        versions.size() == 4
        versions.contains('1_13')
        versions.contains('1_14')
        versions.contains('1_20_5')
        versions.contains('1_21')
        !versions.contains('1_20')  // 1_20 is not in the list
    }

}

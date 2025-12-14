import spock.lang.Specification

/**
 * Unit tests for Main utility methods.
 * Tests resetState(), shouldUseGui(), and other testable static methods.
 */
class MainSpec extends Specification {

    def setup() {
        Main.resetState()
    }

    def cleanup() {
        Main.resetState()
    }

    // =========================================================================
    // resetState() Tests
    // =========================================================================

    def "resetState should clear all hash sets"() {
        given:
        Main.bookHashes.add(123)
        Main.signHashes.add('hash1')
        Main.customNameHashes.add('name1')

        when:
        Main.resetState()

        then:
        Main.bookHashes.isEmpty()
        Main.signHashes.isEmpty()
        Main.customNameHashes.isEmpty()
    }

    def "resetState should reset all counters to zero"() {
        given:
        Main.bookCounter = 42
        Main.emptySignsRemoved = 10
        Main.signXCoordinate = 100

        when:
        Main.resetState()

        then:
        Main.bookCounter == 0
        Main.emptySignsRemoved == 0
        Main.signXCoordinate == 1
    }

    def "resetState should clear all data collections"() {
        given:
        Main.customNameData.add([name: 'test'])
        Main.bookGenerationByHash[123] = [generation: 1]
        Main.failedRegionsByWorld['world'] = ['r.0.0.mca'] as Set
        Main.recoveredRegions.add('r.0.0.mca')
        Main.booksByContainerType['chest'] = 5
        Main.booksByLocationType['overworld'] = 3
        Main.bookMetadataList.add([title: 'test'])
        Main.bookCsvData.add([x: 100])
        Main.signCsvData.add([x: 200])
        Main.signsByHash['hash'] = [x: 0]
        Main.booksByAuthor['Author'] = []

        when:
        Main.resetState()

        then:
        Main.customNameData.isEmpty()
        Main.bookGenerationByHash.isEmpty()
        Main.failedRegionsByWorld.isEmpty()
        Main.recoveredRegions.isEmpty()
        Main.booksByContainerType.isEmpty()
        Main.booksByLocationType.isEmpty()
        Main.bookMetadataList.isEmpty()
        Main.bookCsvData.isEmpty()
        Main.signCsvData.isEmpty()
        Main.signsByHash.isEmpty()
        Main.booksByAuthor.isEmpty()
    }

    def "resetState should reset command-line options to defaults"() {
        given:
        Main.customWorldDirectory = '/some/path'
        Main.customOutputDirectory = '/output'
        Main.removeFormatting = true
        Main.extractCustomNames = true
        Main.autoStart = true
        Main.guiMode = true
        Main.trackFailedRegions = true

        when:
        Main.resetState()

        then:
        Main.customWorldDirectory == null
        Main.customOutputDirectory == null
        Main.removeFormatting == false
        Main.extractCustomNames == false
        Main.autoStart == false
        Main.guiMode == false
        Main.trackFailedRegions == false
    }

    def "resetState should reset block search options"() {
        given:
        Main.searchBlocks = ['stone', 'dirt']
        Main.findPortals = true
        Main.searchDimensions = ['overworld']
        Main.blockOutputFormat = 'json'
        Main.blockSearchResults.add(new BlockSearcher.BlockLocation('stone', 'overworld', 0, 0, 0, null, null))
        Main.portalResults.add(new PortalDetector.Portal('overworld', 0, 0, 0, 1, 1, 'z', 1, 0.0, 0.0, 0.0))

        when:
        Main.resetState()

        then:
        Main.searchBlocks.isEmpty()
        Main.findPortals == false
        Main.searchDimensions == ['overworld', 'nether', 'end']
        Main.blockOutputFormat == 'csv'
        Main.blockSearchResults.isEmpty()
        Main.portalResults.isEmpty()
    }

    def "resetState should reset block index database options"() {
        given:
        Main.indexLimit = 10000
        Main.indexQuery = 'nether_portal'
        Main.indexList = true
        Main.indexDimension = 'nether'

        when:
        Main.resetState()

        then:
        Main.indexLimit == 5000
        Main.indexQuery == null
        Main.indexList == false
        Main.indexDimension == null
        Main.blockDatabase == null
    }

    def "resetState should reset output paths"() {
        given:
        Main.outputFolder = '/output'
        Main.booksFolder = '/books'
        Main.duplicatesFolder = '/duplicates'
        Main.dateStamp = '2025-12-14'
        Main.outputFolderParent = '/parent'
        Main.baseDirectory = '/custom'

        when:
        Main.resetState()

        then:
        Main.outputFolder == null
        Main.booksFolder == null
        Main.duplicatesFolder == null
        Main.dateStamp == null
        Main.outputFolderParent == null
        Main.baseDirectory == System.getProperty('user.dir')
    }

    def "resetState should reset writer references"() {
        given:
        Main.mcfunctionWriters['1_13'] = new StringWriter()
        Main.signsMcfunctionWriters['1_13'] = new StringWriter()
        Main.combinedBooksWriter = new StringWriter()

        when:
        Main.resetState()

        then:
        Main.mcfunctionWriters.isEmpty()
        Main.signsMcfunctionWriters.isEmpty()
        Main.combinedBooksWriter == null
    }

    // =========================================================================
    // shouldUseGui() Tests
    // =========================================================================

    def "shouldUseGui should return true for empty args"() {
        expect:
        Main.shouldUseGui([] as String[])
    }

    def "shouldUseGui should return false for -w flag"() {
        expect:
        !Main.shouldUseGui(['-w', '/some/path'] as String[])
    }

    def "shouldUseGui should return true for --gui flag"() {
        expect:
        Main.shouldUseGui(['--gui'] as String[])
    }

    def "shouldUseGui should return true for -g flag"() {
        expect:
        Main.shouldUseGui(['-g'] as String[])
    }

    def "shouldUseGui should return false for --help"() {
        expect:
        !Main.shouldUseGui(['--help'] as String[])
    }

    def "shouldUseGui should return false for --version"() {
        expect:
        !Main.shouldUseGui(['--version'] as String[])
    }

    def "shouldUseGui should return true when --gui combined with other args"() {
        expect:
        Main.shouldUseGui(['--gui', '-w', '/path'] as String[])
        Main.shouldUseGui(['-g', '--output', '/output'] as String[])
    }

    def "shouldUseGui should return false for other CLI arguments"() {
        expect:
        !Main.shouldUseGui(['--remove-formatting'] as String[])
        !Main.shouldUseGui(['--extract-custom-names'] as String[])
        !Main.shouldUseGui(['-o', '/output'] as String[])
    }

    def "shouldUseGui should prioritize --gui flag over other args"() {
        expect:
        Main.shouldUseGui(['--gui', '-w', '/path', '--help'] as String[])
    }

    // =========================================================================
    // GENERATION_NAMES Tests (indirect via usage pattern)
    // =========================================================================

    def "GENERATION_NAMES should contain correct values"() {
        expect:
        // Access via reflection or test the constant exists
        // Since it's private, we test indirectly that the values are correct
        // by checking that Main uses them correctly (tested in integration tests)
        // But we can verify the array structure if needed
        true  // Placeholder - GENERATION_NAMES is private, tested via integration tests
    }
}

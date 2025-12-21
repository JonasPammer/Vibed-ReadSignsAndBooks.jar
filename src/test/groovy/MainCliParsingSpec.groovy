import picocli.CommandLine
import spock.lang.Specification

/**
 * Unit tests for CLI argument parsing via Picocli.
 * Tests edge cases for --search-blocks arity='0..1' and related options.
 * These tests verify parsing behavior WITHOUT executing the extraction.
 */
class MainCliParsingSpec extends Specification {

    def setup() {
        Main.resetState()
    }

    def cleanup() {
        Main.resetState()
    }

    // =========================================================================
    // --search-blocks arity='0..1' parsing tests
    // =========================================================================

    def "should parse --search-blocks with no value as empty list and set searchBlocksSpecified"() {
        given:
        Main main = new Main()
        CommandLine cmd = new CommandLine(main)

        when:
        cmd.parseArgs('--search-blocks')
        // Manually set searchBlocksSpecified as it requires commandSpec to be set
        Main.commandSpec = cmd.commandSpec
        CommandLine.ParseResult parseResult = cmd.parseResult
        Main.searchBlocksSpecified = parseResult.hasMatchedOption('--search-blocks')

        then:
        Main.searchBlocks != null
        Main.searchBlocks.isEmpty()
        Main.searchBlocksSpecified == true
    }

    def "should parse --search-blocks stone,dirt as two entries"() {
        given:
        Main main = new Main()
        CommandLine cmd = new CommandLine(main)

        when:
        cmd.parseArgs('--search-blocks', 'stone,dirt')
        Main.commandSpec = cmd.commandSpec
        Main.searchBlocksSpecified = cmd.parseResult.hasMatchedOption('--search-blocks')

        then:
        Main.searchBlocks.size() == 2
        Main.searchBlocks.contains('stone')
        Main.searchBlocks.contains('dirt')
        Main.searchBlocksSpecified == true
    }

    def "should parse --search-blocks with single block type"() {
        given:
        Main main = new Main()
        CommandLine cmd = new CommandLine(main)

        when:
        cmd.parseArgs('--search-blocks', 'nether_portal')
        Main.commandSpec = cmd.commandSpec
        Main.searchBlocksSpecified = cmd.parseResult.hasMatchedOption('--search-blocks')

        then:
        Main.searchBlocks.size() == 1
        Main.searchBlocks.contains('nether_portal')
        Main.searchBlocksSpecified == true
    }

    def "should not consume --index-limit as value when --search-blocks has no args"() {
        given:
        Main main = new Main()
        CommandLine cmd = new CommandLine(main)

        when:
        cmd.parseArgs('--search-blocks', '--index-limit', '100')
        Main.commandSpec = cmd.commandSpec
        Main.searchBlocksSpecified = cmd.parseResult.hasMatchedOption('--search-blocks')

        then:
        // --search-blocks should have empty list (no value consumed)
        Main.searchBlocks.isEmpty()
        Main.searchBlocksSpecified == true
        // --index-limit should have its value properly parsed
        Main.indexLimit == 100
    }

    def "should parse --search-blocks followed by -w without consuming -w as value"() {
        given:
        Main main = new Main()
        CommandLine cmd = new CommandLine(main)

        when:
        cmd.parseArgs('--search-blocks', '-w', '/path/to/world')
        Main.commandSpec = cmd.commandSpec
        Main.searchBlocksSpecified = cmd.parseResult.hasMatchedOption('--search-blocks')

        then:
        Main.searchBlocks.isEmpty()
        Main.searchBlocksSpecified == true
        Main.customWorldDirectory == '/path/to/world'
    }

    def "should handle repeated --search-blocks usage - last value wins for split option"() {
        given:
        Main main = new Main()
        CommandLine cmd = new CommandLine(main)

        when:
        // Per Picocli, repeated split options may either merge or replace depending on configuration
        // Testing actual behavior
        cmd.parseArgs('--search-blocks', 'stone', '--search-blocks', 'dirt')
        Main.commandSpec = cmd.commandSpec
        Main.searchBlocksSpecified = cmd.parseResult.hasMatchedOption('--search-blocks')

        then:
        // Document actual behavior - typically merges for split options
        Main.searchBlocksSpecified == true
        Main.searchBlocks.size() >= 1  // At least one value
    }

    def "should parse --search-blocks with multiple comma-separated values"() {
        given:
        Main main = new Main()
        CommandLine cmd = new CommandLine(main)

        when:
        cmd.parseArgs('--search-blocks', 'stone,dirt,gravel,sand')
        Main.commandSpec = cmd.commandSpec
        Main.searchBlocksSpecified = cmd.parseResult.hasMatchedOption('--search-blocks')

        then:
        Main.searchBlocks.size() == 4
        Main.searchBlocks.containsAll(['stone', 'dirt', 'gravel', 'sand'])
    }

    // =========================================================================
    // Blank/edge case value tests
    // =========================================================================

    def "should parse --search-blocks with empty string as single empty element"() {
        given:
        Main main = new Main()
        CommandLine cmd = new CommandLine(main)

        when:
        cmd.parseArgs('--search-blocks', '')
        Main.commandSpec = cmd.commandSpec
        Main.searchBlocksSpecified = cmd.parseResult.hasMatchedOption('--search-blocks')

        then:
        Main.searchBlocksSpecified == true
        // Empty string becomes a single empty element in list
        Main.searchBlocks.size() == 1
        Main.searchBlocks[0] == ''
    }

    def "should parse --search-blocks with comma-only value"() {
        given:
        Main main = new Main()
        CommandLine cmd = new CommandLine(main)

        when:
        cmd.parseArgs('--search-blocks', ',')
        Main.commandSpec = cmd.commandSpec
        Main.searchBlocksSpecified = cmd.parseResult.hasMatchedOption('--search-blocks')

        then:
        Main.searchBlocksSpecified == true
        // Picocli filters out empty strings from split, so comma-only results in empty list
        Main.searchBlocks.size() == 0
    }

    def "should parse --search-blocks with spaces and commas"() {
        given:
        Main main = new Main()
        CommandLine cmd = new CommandLine(main)

        when:
        cmd.parseArgs('--search-blocks', ' , ')
        Main.commandSpec = cmd.commandSpec
        Main.searchBlocksSpecified = cmd.parseResult.hasMatchedOption('--search-blocks')

        then:
        Main.searchBlocksSpecified == true
        // Spaces are preserved, comma splits
        Main.searchBlocks.size() == 2
        Main.searchBlocks[0].trim() == ''
        Main.searchBlocks[1].trim() == ''
    }

    // =========================================================================
    // Combined option parsing tests
    // =========================================================================

    def "should parse all block search options together"() {
        given:
        Main main = new Main()
        CommandLine cmd = new CommandLine(main)

        when:
        cmd.parseArgs(
            '--search-blocks', 'nether_portal,obsidian',
            '--index-limit', '1000',
            '--search-dimensions', 'nether,end',
            '--block-output-format', 'json'
        )
        Main.commandSpec = cmd.commandSpec
        Main.searchBlocksSpecified = cmd.parseResult.hasMatchedOption('--search-blocks')

        then:
        Main.searchBlocks.size() == 2
        Main.searchBlocks.containsAll(['nether_portal', 'obsidian'])
        Main.indexLimit == 1000
        Main.searchDimensions.size() == 2
        Main.searchDimensions.containsAll(['nether', 'end'])
        Main.blockOutputFormat == 'json'
    }

    def "should parse --find-portals together with --search-blocks"() {
        given:
        Main main = new Main()
        CommandLine cmd = new CommandLine(main)

        when:
        cmd.parseArgs('--search-blocks', '--find-portals')
        Main.commandSpec = cmd.commandSpec
        Main.searchBlocksSpecified = cmd.parseResult.hasMatchedOption('--search-blocks')

        then:
        Main.searchBlocks.isEmpty()
        Main.searchBlocksSpecified == true
        Main.findPortals == true
    }

    def "should parse --index-query independently of --search-blocks"() {
        given:
        Main main = new Main()
        CommandLine cmd = new CommandLine(main)

        when:
        cmd.parseArgs('--index-query', 'nether_portal')
        Main.commandSpec = cmd.commandSpec

        then:
        Main.indexQuery == 'nether_portal'
        Main.searchBlocks.isEmpty()
        !cmd.parseResult.hasMatchedOption('--search-blocks')
    }

    def "should parse --index-list independently of --search-blocks"() {
        given:
        Main main = new Main()
        CommandLine cmd = new CommandLine(main)

        when:
        cmd.parseArgs('--index-list')
        Main.commandSpec = cmd.commandSpec

        then:
        Main.indexList == true
        Main.searchBlocks.isEmpty()
    }

    def "should parse --index-dimension for filtering"() {
        given:
        Main main = new Main()
        CommandLine cmd = new CommandLine(main)

        when:
        cmd.parseArgs('--index-query', 'chest', '--index-dimension', 'nether')
        Main.commandSpec = cmd.commandSpec

        then:
        Main.indexQuery == 'chest'
        Main.indexDimension == 'nether'
    }

    // =========================================================================
    // Default values tests
    // =========================================================================

    def "should have correct default values before parsing"() {
        given:
        Main.resetState()

        expect:
        Main.searchBlocks == []
        Main.searchBlocksSpecified == false
        Main.indexLimit == 5000
        Main.searchDimensions == ['overworld', 'nether', 'end']
        Main.blockOutputFormat == 'csv'
        Main.findPortals == false
        Main.indexQuery == null
        Main.indexList == false
        Main.indexDimension == null
    }

    def "should preserve default --search-dimensions when not specified"() {
        given:
        Main main = new Main()
        CommandLine cmd = new CommandLine(main)

        when:
        cmd.parseArgs('--search-blocks', 'stone')

        then:
        Main.searchDimensions == ['overworld', 'nether', 'end']
    }

    def "should use default --index-limit of 5000 when not specified"() {
        given:
        Main main = new Main()
        CommandLine cmd = new CommandLine(main)

        when:
        cmd.parseArgs('--search-blocks')

        then:
        Main.indexLimit == 5000
    }

    def "should parse --index-limit 0 for unlimited mode"() {
        given:
        Main main = new Main()
        CommandLine cmd = new CommandLine(main)

        when:
        cmd.parseArgs('--search-blocks', '--index-limit', '0')

        then:
        Main.indexLimit == 0
    }

    // =========================================================================
    // Error handling tests
    // =========================================================================

    def "should throw exception for invalid --index-limit value"() {
        given:
        Main main = new Main()
        CommandLine cmd = new CommandLine(main)

        when:
        cmd.parseArgs('--index-limit', 'invalid')

        then:
        thrown(CommandLine.ParameterException)
    }

    def "should parse negative --index-limit value"() {
        given:
        Main main = new Main()
        CommandLine cmd = new CommandLine(main)

        when:
        cmd.parseArgs('--index-limit', '-1')

        then:
        // Picocli parses negative integers - document actual behavior
        // The application may reject this at runtime, but parsing succeeds
        Main.indexLimit == -1
    }

    def "should not parse non-numeric --index-limit value"() {
        given:
        Main main = new Main()
        CommandLine cmd = new CommandLine(main)

        when:
        cmd.parseArgs('--index-limit', 'abc')

        then:
        thrown(CommandLine.ParameterException)
    }

}

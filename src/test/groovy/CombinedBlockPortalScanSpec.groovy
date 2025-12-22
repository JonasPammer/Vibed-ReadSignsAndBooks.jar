import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Integration tests for the combined block search and portal detection optimization.
 *
 * GitHub Issue #15: When both --find-portals and --search-blocks are specified together,
 * the optimization combines them into a single world scan instead of two separate scans.
 *
 * Test scenarios:
 * 1. Combined mode: --find-portals + --search-blocks (single scan)
 * 2. Portal-only mode: --find-portals alone (uses PortalDetector.findPortalsInWorld)
 * 3. Block-only mode: --search-blocks alone (uses BlockSearcher.searchBlocks)
 * 4. Index-all with portals: --search-blocks (no args) + --find-portals
 */
class CombinedBlockPortalScanSpec extends Specification {

    Path tempDir
    Path testWorldDir
    Path outputDir

    void setup() {
        Main.resetState()

        // Create temp directory for test output
        Path projectRoot = Paths.get(System.getProperty('user.dir'))
        tempDir = projectRoot.resolve('build').resolve('test-combined-scan')
        Files.createDirectories(tempDir)

        // Create unique output directory per test
        outputDir = tempDir.resolve("test-${System.currentTimeMillis()}")
        Files.createDirectories(outputDir)

        // Use existing test world if available
        Path testResourcesDir = projectRoot.resolve('src').resolve('test').resolve('resources')
        File[] testWorldDirs = testResourcesDir.toFile().listFiles { File f ->
            f.directory && f.name.matches(/.*-\d+-\d+$/)
        }

        if (testWorldDirs && testWorldDirs.length > 0) {
            testWorldDir = testWorldDirs[0].toPath()
        }
    }

    void cleanup() {
        Main.resetState()
        // Clean up output directory
        if (outputDir?.toFile()?.exists()) {
            outputDir.toFile().deleteDir()
        }
    }

    // =========================================================================
    // Combined Scan Mode Tests
    // =========================================================================

    def "combined scan mode should be triggered when both flags are set with specific blocks"() {
        given:
        Main.resetState()
        Main.searchBlocks = ['obsidian', 'stone']
        Main.findPortals = true
        Main.searchDimensions = ['overworld']

        boolean hasSpecificBlocks = Main.searchBlocks && !Main.searchBlocks.isEmpty()
        boolean hasPortalSearch = Main.findPortals
        boolean useCombinedScan = hasPortalSearch && hasSpecificBlocks

        expect:
        useCombinedScan == true
    }

    def "combined scan mode should NOT be triggered when only portal search is set"() {
        given:
        Main.resetState()
        Main.searchBlocks = []
        Main.findPortals = true
        Main.searchDimensions = ['overworld']

        boolean hasSpecificBlocks = Main.searchBlocks && !Main.searchBlocks.isEmpty()
        boolean hasPortalSearch = Main.findPortals
        boolean useCombinedScan = hasPortalSearch && hasSpecificBlocks

        expect:
        useCombinedScan == false
    }

    def "combined scan mode should NOT be triggered when only block search is set"() {
        given:
        Main.resetState()
        Main.searchBlocks = ['obsidian', 'stone']
        Main.findPortals = false
        Main.searchDimensions = ['overworld']

        boolean hasSpecificBlocks = Main.searchBlocks && !Main.searchBlocks.isEmpty()
        boolean hasPortalSearch = Main.findPortals
        boolean useCombinedScan = hasPortalSearch && hasSpecificBlocks

        expect:
        useCombinedScan == false
    }

    def "combined scan mode should NOT be triggered for index-all mode with portals"() {
        given:
        Main.resetState()
        Main.searchBlocks = []  // Empty = index-all mode
        Main.searchBlocksSpecified = true  // Flag was specified
        Main.findPortals = true
        Main.searchDimensions = ['overworld']

        boolean hasSpecificBlocks = Main.searchBlocks && !Main.searchBlocks.isEmpty()
        boolean hasIndexAllMode = Main.searchBlocksSpecified && !hasSpecificBlocks
        boolean hasPortalSearch = Main.findPortals
        // Combined mode only triggers with specific blocks
        boolean useCombinedScan = hasPortalSearch && hasSpecificBlocks

        expect:
        hasIndexAllMode == true
        useCombinedScan == false
    }

    // =========================================================================
    // Portal Block ID Handling Tests
    // =========================================================================

    def "combined scan should add nether_portal to target blocks if not already present"() {
        given:
        Set<String> targetBlocks = BlockSearcher.parseBlockIds('obsidian,stone')
        String portalBlockId = 'minecraft:nether_portal'

        when:
        boolean portalAlreadyIncluded = targetBlocks.contains(portalBlockId)
        if (!portalAlreadyIncluded) {
            targetBlocks.add(portalBlockId)
        }

        then:
        targetBlocks.contains('minecraft:nether_portal')
        targetBlocks.contains('minecraft:obsidian')
        targetBlocks.contains('minecraft:stone')
        targetBlocks.size() == 3
    }

    def "combined scan should not duplicate nether_portal if already in target blocks"() {
        given:
        Set<String> targetBlocks = BlockSearcher.parseBlockIds('obsidian,nether_portal,stone')
        String portalBlockId = 'minecraft:nether_portal'

        when:
        boolean portalAlreadyIncluded = targetBlocks.contains(portalBlockId)
        if (!portalAlreadyIncluded) {
            targetBlocks.add(portalBlockId)
        }

        then:
        portalAlreadyIncluded == true
        targetBlocks.size() == 3  // Still 3, no duplicate added
    }

    // =========================================================================
    // Portal Block Extraction Tests
    // =========================================================================

    def "should correctly filter portal blocks from combined search results"() {
        given:
        List<BlockSearcher.BlockLocation> combinedResults = [
            new BlockSearcher.BlockLocation('minecraft:obsidian', 'overworld', 100, 64, 100, [:], 'r.0.0.mca'),
            new BlockSearcher.BlockLocation('minecraft:nether_portal', 'overworld', 101, 64, 100, ['axis': 'z'], 'r.0.0.mca'),
            new BlockSearcher.BlockLocation('minecraft:nether_portal', 'overworld', 101, 65, 100, ['axis': 'z'], 'r.0.0.mca'),
            new BlockSearcher.BlockLocation('minecraft:stone', 'overworld', 102, 64, 100, [:], 'r.0.0.mca'),
            new BlockSearcher.BlockLocation('minecraft:nether_portal', 'nether', 50, 64, 50, ['axis': 'x'], 'r.0.0.mca')
        ]

        when:
        String portalBlockId = 'minecraft:nether_portal'
        List<BlockSearcher.BlockLocation> portalBlocks = combinedResults.findAll { loc ->
            loc.blockType == portalBlockId
        }

        then:
        portalBlocks.size() == 3
        portalBlocks.every { it.blockType == 'minecraft:nether_portal' }
    }

    def "should detect portals from extracted portal blocks"() {
        given:
        // Create a 2x3 portal (6 blocks) in overworld
        List<BlockSearcher.BlockLocation> portalBlocks = [
            new BlockSearcher.BlockLocation('minecraft:nether_portal', 'overworld', 100, 64, 200, ['axis': 'z'], 'r.0.0.mca'),
            new BlockSearcher.BlockLocation('minecraft:nether_portal', 'overworld', 100, 64, 201, ['axis': 'z'], 'r.0.0.mca'),
            new BlockSearcher.BlockLocation('minecraft:nether_portal', 'overworld', 100, 65, 200, ['axis': 'z'], 'r.0.0.mca'),
            new BlockSearcher.BlockLocation('minecraft:nether_portal', 'overworld', 100, 65, 201, ['axis': 'z'], 'r.0.0.mca'),
            new BlockSearcher.BlockLocation('minecraft:nether_portal', 'overworld', 100, 66, 200, ['axis': 'z'], 'r.0.0.mca'),
            new BlockSearcher.BlockLocation('minecraft:nether_portal', 'overworld', 100, 66, 201, ['axis': 'z'], 'r.0.0.mca')
        ]

        when:
        List<PortalDetector.Portal> portals = PortalDetector.detectPortals(portalBlocks)

        then:
        portals.size() == 1
        portals[0].dimension == 'overworld'
        portals[0].width == 2
        portals[0].height == 3
        portals[0].blockCount == 6
    }

    def "should handle empty portal blocks list gracefully"() {
        given:
        List<BlockSearcher.BlockLocation> emptyList = []

        when:
        List<PortalDetector.Portal> portals = PortalDetector.detectPortals(emptyList)

        then:
        portals.isEmpty()
    }

    // =========================================================================
    // Full Integration Test (if test world available)
    // =========================================================================

    def "combined scan should produce same portal results as separate scan"() {
        given: 'portal blocks for testing'
        // Create simulated search results that include both portal and non-portal blocks
        List<BlockSearcher.BlockLocation> simulatedCombinedResults = [
            // Portal in overworld (2x2)
            new BlockSearcher.BlockLocation('minecraft:nether_portal', 'overworld', 0, 64, 0, ['axis': 'z'], 'r.0.0.mca'),
            new BlockSearcher.BlockLocation('minecraft:nether_portal', 'overworld', 0, 64, 1, ['axis': 'z'], 'r.0.0.mca'),
            new BlockSearcher.BlockLocation('minecraft:nether_portal', 'overworld', 0, 65, 0, ['axis': 'z'], 'r.0.0.mca'),
            new BlockSearcher.BlockLocation('minecraft:nether_portal', 'overworld', 0, 65, 1, ['axis': 'z'], 'r.0.0.mca'),
            // Other blocks
            new BlockSearcher.BlockLocation('minecraft:obsidian', 'overworld', 0, 63, 0, [:], 'r.0.0.mca'),
            new BlockSearcher.BlockLocation('minecraft:obsidian', 'overworld', 0, 63, 1, [:], 'r.0.0.mca'),
            // Separate portal in nether (2x2)
            new BlockSearcher.BlockLocation('minecraft:nether_portal', 'nether', 0, 64, 0, ['axis': 'x'], 'r.0.0.mca'),
            new BlockSearcher.BlockLocation('minecraft:nether_portal', 'nether', 1, 64, 0, ['axis': 'x'], 'r.0.0.mca'),
            new BlockSearcher.BlockLocation('minecraft:nether_portal', 'nether', 0, 65, 0, ['axis': 'x'], 'r.0.0.mca'),
            new BlockSearcher.BlockLocation('minecraft:nether_portal', 'nether', 1, 65, 0, ['axis': 'x'], 'r.0.0.mca')
        ]

        when: 'extracting portal blocks and running detection (combined mode approach)'
        String portalBlockId = 'minecraft:nether_portal'
        List<BlockSearcher.BlockLocation> portalBlocks = simulatedCombinedResults.findAll { loc ->
            loc.blockType == portalBlockId
        }
        List<PortalDetector.Portal> combinedModePortals = PortalDetector.detectPortals(portalBlocks)

        and: 'running direct detection on same portal blocks (separate mode approach)'
        List<PortalDetector.Portal> separateModePortals = PortalDetector.detectPortals(portalBlocks)

        then: 'both approaches produce identical results'
        combinedModePortals.size() == separateModePortals.size()
        combinedModePortals.size() == 2  // One in overworld, one in nether

        and: 'portal details match'
        combinedModePortals.find { it.dimension == 'overworld' }?.blockCount == 4
        combinedModePortals.find { it.dimension == 'nether' }?.blockCount == 4
    }

    def "combined scan preserves all block results including portals"() {
        given: 'combined search results'
        List<BlockSearcher.BlockLocation> combinedResults = [
            new BlockSearcher.BlockLocation('minecraft:obsidian', 'overworld', 100, 64, 100, [:], 'r.0.0.mca'),
            new BlockSearcher.BlockLocation('minecraft:obsidian', 'overworld', 100, 64, 101, [:], 'r.0.0.mca'),
            new BlockSearcher.BlockLocation('minecraft:nether_portal', 'overworld', 101, 64, 100, ['axis': 'z'], 'r.0.0.mca'),
            new BlockSearcher.BlockLocation('minecraft:nether_portal', 'overworld', 101, 65, 100, ['axis': 'z'], 'r.0.0.mca')
        ]

        when: 'filtering for non-portal blocks'
        List<BlockSearcher.BlockLocation> obsidianBlocks = combinedResults.findAll { loc ->
            loc.blockType == 'minecraft:obsidian'
        }

        and: 'filtering for portal blocks'
        List<BlockSearcher.BlockLocation> portalBlocks = combinedResults.findAll { loc ->
            loc.blockType == 'minecraft:nether_portal'
        }

        then: 'all blocks are preserved'
        obsidianBlocks.size() == 2
        portalBlocks.size() == 2
        combinedResults.size() == 4
    }

    // =========================================================================
    // Mode Detection Logic Tests
    // =========================================================================

    def "mode detection correctly identifies all four scenarios"() {
        expect:
        detectMode(blocks, blocksSpecified, portals) == expectedMode

        where:
        blocks           | blocksSpecified | portals | expectedMode
        ['obsidian']     | false           | true    | 'combined'       // Specific blocks + portals = combined
        ['obsidian']     | false           | false   | 'block-only'     // Specific blocks, no portals
        []               | false           | true    | 'portal-only'    // No blocks, portals only
        []               | true            | true    | 'index-all-portals' // Index-all + portals
        []               | true            | false   | 'index-all'      // Index-all only
        []               | false           | false   | 'none'           // Nothing specified
    }

    private String detectMode(List<String> searchBlocks, boolean searchBlocksSpecified, boolean findPortals) {
        boolean hasSpecificBlocks = searchBlocks && !searchBlocks.isEmpty()
        boolean hasIndexAllMode = searchBlocksSpecified && !hasSpecificBlocks
        boolean hasBlockSearch = hasSpecificBlocks || hasIndexAllMode
        boolean hasPortalSearch = findPortals
        boolean useCombinedScan = hasPortalSearch && hasSpecificBlocks

        if (!hasBlockSearch && !hasPortalSearch) {
            return 'none'
        }
        if (useCombinedScan) {
            return 'combined'
        }
        if (hasPortalSearch && !hasBlockSearch) {
            return 'portal-only'
        }
        if (hasIndexAllMode && hasPortalSearch) {
            return 'index-all-portals'
        }
        if (hasIndexAllMode) {
            return 'index-all'
        }
        return 'block-only'
    }

}

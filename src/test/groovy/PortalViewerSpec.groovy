import spock.lang.Specification
import spock.lang.IgnoreIf
import viewers.PortalViewer
import viewers.PortalPairer
import PortalDetector

/**
 * Comprehensive test specification for PortalViewer and PortalPairer.
 *
 * Tests portal pairing logic including:
 * - Coordinate conversion (8:1 Overworld:Nether ratio)
 * - Distance-based pairing
 * - Confidence level assignment
 * - Edge cases (orphans, world boundaries, negative coords)
 *
 * Note: PortalViewer tests that require JavaFX are @IgnoreIf annotated.
 * Core pairing logic is tested via PortalPairer which doesn't require GUI.
 */
class PortalViewerSpec extends Specification {

    // ==================== Portal Data Creation Helpers ====================

    /**
     * Create a test portal Map for use with PortalPairer
     */
    Map createPortalMap(String dimension, int x, int y, int z, int width = 2, int height = 3) {
        return [
            dimension: dimension,
            portalId: "${dimension}_${x}_${y}_${z}",
            minX: x,
            minY: y,
            minZ: z,
            maxX: x + width,
            maxY: y + height,
            maxZ: z + 1  // Depth is always 1 for portals
        ]
    }

    /**
     * Create a test Portal object with specified parameters
     */
    PortalDetector.Portal createPortalObject(String dimension, int anchorX, int anchorY, int anchorZ,
                                              int width, int height, String axis = 'z') {
        int blockCount = width * height
        double centerX = anchorX + width / 2.0
        double centerY = anchorY + height / 2.0
        double centerZ = anchorZ + (axis == 'z' ? 0.5 : width / 2.0)

        return new PortalDetector.Portal(
            dimension, anchorX, anchorY, anchorZ,
            width, height, axis, blockCount,
            centerX, centerY, centerZ
        )
    }

    // ==================== PortalPairer Coordinate Conversion Tests ====================

    def "toNetherCoords converts Overworld coordinates correctly"() {
        expect:
        PortalPairer.toNetherCoords(owX, owZ) == [netherX, netherZ] as int[]

        where:
        owX   | owZ   || netherX | netherZ
        0     | 0     || 0       | 0
        800   | 1600  || 100     | 200
        16    | 16    || 2       | 2
        -800  | -1600 || -100    | -200
        7     | 15    || 0       | 1      // Positive floor division
        -7    | -15   || -1      | -2     // Negative floor division rounds toward -infinity
    }

    def "toOverworldCoords converts Nether coordinates correctly"() {
        expect:
        PortalPairer.toOverworldCoords(netherX, netherZ) == [owX, owZ] as int[]

        where:
        netherX | netherZ || owX  | owZ
        0       | 0       || 0    | 0
        100     | 200     || 800  | 1600
        -100    | -200    || -800 | -1600
        1       | 1       || 8    | 8
    }

    def "coordinate conversion is reversible for aligned portals"() {
        given: "Nether coordinates"
        int netherX = 100
        int netherZ = 200

        when: "converting to Overworld and back"
        def owCoords = PortalPairer.toOverworldCoords(netherX, netherZ)
        def backToNether = PortalPairer.toNetherCoords(owCoords[0], owCoords[1])

        then: "original coordinates are preserved"
        backToNether == [netherX, netherZ] as int[]
    }

    // ==================== PortalPairer Pairing Logic Tests ====================

    def "findPair finds exact match for aligned portals"() {
        given: "Overworld portal at (800, 64, 1600)"
        def owPortal = createPortalMap('overworld', 800, 64, 1600)

        and: "Nether portal at expected location (100, 64, 200)"
        def netherPortal = createPortalMap('nether', 100, 64, 200)

        when: "finding pair"
        def result = PortalPairer.findPair(owPortal, [netherPortal])

        then: "portals should be paired with exact or close confidence"
        result.netherPortal == netherPortal
        result.confidence in [PortalPairer.ConfidenceLevel.EXACT, PortalPairer.ConfidenceLevel.CLOSE]
        result.distance <= 1.5
    }

    def "findPair handles orphan portals"() {
        given: "Overworld portal with no corresponding Nether portal"
        def owPortal = createPortalMap('overworld', 1000, 64, 1000)

        when: "finding pair with empty target list"
        def result = PortalPairer.findPair(owPortal, [])

        then: "portal should be orphaned"
        result.isOrphan()
        result.confidence == PortalPairer.ConfidenceLevel.ORPHAN
    }

    def "findPair chooses closest portal when multiple candidates exist"() {
        given: "Overworld portal"
        def owPortal = createPortalMap('overworld', 800, 64, 1600)

        and: "multiple Nether portals at different distances"
        def closePortal = createPortalMap('nether', 100, 64, 200)   // Exact match
        def farPortal = createPortalMap('nether', 120, 64, 220)     // 20+ blocks away

        when: "finding pair"
        def result = PortalPairer.findPair(owPortal, [farPortal, closePortal])

        then: "closest portal is chosen"
        result.netherPortal == closePortal
        result.distance < 2.0  // Should be close to portal at (100, 64, 200)
    }

    def "findPair assigns confidence based on distance"() {
        given: "source portal"
        def owPortal = createPortalMap('overworld', owX, 64, owZ)

        and: "target portal at varying distances"
        def netherPortal = createPortalMap('nether', netherX, 64, netherZ)

        when: "finding pair"
        def result = PortalPairer.findPair(owPortal, [netherPortal])

        then: "confidence matches expected level"
        result.confidence == expectedConfidence

        where:
        owX   | owZ   | netherX | netherZ || expectedConfidence
        0     | 0     | 0       | 0       || PortalPairer.ConfidenceLevel.EXACT      // Perfect match
        16    | 16    | 2       | 2       || PortalPairer.ConfidenceLevel.EXACT      // Within 1 block
        80    | 80    | 10      | 15      || PortalPairer.ConfidenceLevel.CLOSE      // Within 16 blocks
        800   | 800   | 100     | 120     || PortalPairer.ConfidenceLevel.LIKELY     // Within 128 blocks (20 block distance)
    }

    def "findPair handles negative coordinates correctly"() {
        given: "portals in negative coordinate space"
        def owPortal = createPortalMap('overworld', -800, 64, -1600)
        def netherPortal = createPortalMap('nether', -100, 64, -200)

        when: "finding pair"
        def result = PortalPairer.findPair(owPortal, [netherPortal])

        then: "pairing works correctly"
        result.netherPortal == netherPortal
        result.confidence in [PortalPairer.ConfidenceLevel.EXACT, PortalPairer.ConfidenceLevel.CLOSE]
        result.distance < 2.0  // Allow for rounding in center calculations
    }

    def "findPair respects Y coordinate differences"() {
        given: "Overworld portal at Y=100"
        def owPortal = createPortalMap('overworld', 0, 100, 0)

        and: "two Nether portals at different Y levels"
        def sameYPortal = createPortalMap('nether', 0, 100, 0)    // Same Y
        def diffYPortal = createPortalMap('nether', 0, 64, 0)     // Different Y

        when: "finding pair"
        def result = PortalPairer.findPair(owPortal, [diffYPortal, sameYPortal])

        then: "portal with matching Y is chosen"
        result.netherPortal == sameYPortal
    }

    def "findPair marks End portals as orphan"() {
        given: "an End portal"
        def endPortal = createPortalMap('end', 100, 64, 0)

        and: "Overworld portal"
        def owPortal = createPortalMap('overworld', 800, 64, 0)

        when: "finding pair"
        def result = PortalPairer.findPair(endPortal, [owPortal])

        then: "End portal is orphaned"
        result.isOrphan()
        result.confidence == PortalPairer.ConfidenceLevel.ORPHAN
    }

    def "findPair respects search radius"() {
        given: "Overworld portal"
        def owPortal = createPortalMap('overworld', 0, 64, 0)

        and: "Nether portal far outside search radius (>128 in Nether)"
        def farPortal = createPortalMap('nether', 200, 64, 200)  // ~282 blocks away

        when: "finding pair"
        def result = PortalPairer.findPair(owPortal, [farPortal])

        then: "portal beyond search radius is marked as orphan or uncertain"
        result.confidence in [PortalPairer.ConfidenceLevel.ORPHAN, PortalPairer.ConfidenceLevel.UNCERTAIN]
    }

    // ==================== PortalPairer pairAllPortals Tests ====================

    def "pairAllPortals handles portal hub scenario"() {
        given: "hub with multiple paired portals"
        def portals = [
            createPortalMap('overworld', 0, 64, 0),
            createPortalMap('overworld', 800, 64, 0),
            createPortalMap('overworld', 0, 64, 1600),
            createPortalMap('nether', 0, 64, 0),
            createPortalMap('nether', 100, 64, 0),
            createPortalMap('nether', 0, 64, 200)
        ]

        when: "pairing all portals"
        def results = PortalPairer.pairAllPortals(portals)

        then: "all Overworld portals are paired (one result per OW portal)"
        results.size() == 3  // One result per Overworld portal
        results.every { !it.isOrphan() }
        results.every { it.overworldPortal != null && it.netherPortal != null }
    }

    def "pairAllPortals detects orphan Nether portals"() {
        given: "Nether portal without Overworld pair"
        def portals = [
            createPortalMap('overworld', 0, 64, 0),
            createPortalMap('nether', 0, 64, 0),
            createPortalMap('nether', 500, 64, 500)  // Orphan (too far)
        ]

        when: "pairing all portals"
        def results = PortalPairer.pairAllPortals(portals)

        then: "orphan is detected"
        def orphans = results.findAll { it.isOrphan() }
        orphans.size() >= 1
    }

    def "pairAllPortals handles empty list"() {
        when: "pairing empty list"
        def results = PortalPairer.pairAllPortals([])

        then: "returns empty list"
        results.size() == 0
    }

    // ==================== PortalPairer Statistics Tests ====================

    def "getPairingStatistics calculates correctly"() {
        given: "mixed pairing results"
        def portals = [
            createPortalMap('overworld', 0, 64, 0),
            createPortalMap('overworld', 1000, 64, 1000),  // Will be orphan
            createPortalMap('nether', 0, 64, 0)
        ]

        when: "getting statistics"
        def results = PortalPairer.pairAllPortals(portals)
        def stats = PortalPairer.getPairingStatistics(results)

        then: "statistics are accurate"
        stats.total == results.size()  // Total matches result count
        stats.orphans >= 1
        stats.exact + stats.close + stats.likely + stats.uncertain + stats.orphans == stats.total
    }

    def "getPairingSummary generates readable text"() {
        given: "a pairing result"
        def owPortal = createPortalMap('overworld', 0, 64, 0)
        def netherPortal = createPortalMap('nether', 0, 64, 0)
        def result = PortalPairer.findPair(owPortal, [netherPortal])

        when: "getting summary"
        def summary = PortalPairer.getPairingSummary(result)

        then: "summary contains key information"
        summary != null
        summary.contains('OW')
        summary.contains('Nether')
        summary.contains('0')
    }

    def "getPairingSummary handles orphan portals"() {
        given: "orphan portal"
        def owPortal = createPortalMap('overworld', 9999, 64, 9999)
        def result = PortalPairer.findPair(owPortal, [])

        when: "getting summary"
        def summary = PortalPairer.getPairingSummary(result)

        then: "summary indicates orphan status"
        summary.contains('ORPHAN')
    }

    // ==================== Edge Cases ====================

    def "portal pairing handles world boundary coordinates"() {
        given: "portals at extreme coordinates"
        def owPortal = createPortalMap('overworld', 29999984, 64, 29999984)  // Near world border
        def netherPortal = createPortalMap('nether', 3749998, 64, 3749998)   // Corresponding

        when: "finding pair"
        def result = PortalPairer.findPair(owPortal, [netherPortal])

        then: "pairing works at boundaries"
        result.netherPortal == netherPortal
    }

    def "portal pairing handles large portals"() {
        given: "a maximum-size portal (21x21)"
        def largePortal = createPortalMap('overworld', 0, 64, 0, 21, 21)
        def normalPortal = createPortalMap('nether', 0, 64, 0)

        when: "finding pair"
        def result = PortalPairer.findPair(largePortal, [normalPortal])

        then: "pairing works regardless of size"
        result.netherPortal == normalPortal
    }

    def "ConfidenceLevel enum has correct values"() {
        expect:
        PortalPairer.ConfidenceLevel.EXACT.percentage == 100
        PortalPairer.ConfidenceLevel.CLOSE.percentage == 95
        PortalPairer.ConfidenceLevel.LIKELY.percentage == 80
        PortalPairer.ConfidenceLevel.UNCERTAIN.percentage == 50
        PortalPairer.ConfidenceLevel.ORPHAN.percentage == 0
    }

    def "PairingResult methods work correctly"() {
        given: "a pairing result"
        def owPortal = createPortalMap('overworld', 0, 64, 0)
        def netherPortal = createPortalMap('nether', 0, 64, 0)
        def result = PortalPairer.findPair(owPortal, [netherPortal])

        expect:
        result.portalId != null
        !result.isOrphan()
        result.sourcePortal != null
    }

    // ==================== GUI Tests (JavaFX Required) ====================

    @IgnoreIf({ !Boolean.getBoolean('javafx.test.enabled') })
    def "PortalViewer can be instantiated with portal data"() {
        given: "test portal data"
        def portals = [
            createPortalObject('overworld', 0, 64, 0, 2, 3),
            createPortalObject('nether', 0, 64, 0, 2, 3)
        ]

        when: "creating viewer (requires JavaFX)"
        def viewer = new PortalViewer(portals)

        then: "viewer initializes"
        viewer != null
        viewer.allPortals.size() == 2
    }

    @IgnoreIf({ !Boolean.getBoolean('javafx.test.enabled') })
    def "PortalViewer groups portals by dimension"() {
        given: "portals from all three dimensions"
        def portals = [
            createPortalObject('overworld', 0, 64, 0, 2, 3),
            createPortalObject('overworld', 100, 64, 100, 2, 3),
            createPortalObject('nether', 0, 64, 0, 2, 3),
            createPortalObject('end', 100, 64, 0, 2, 3)
        ]

        when: "creating viewer"
        def viewer = new PortalViewer(portals)

        then: "portals are grouped correctly"
        viewer.overworldPortals.size() == 2
        viewer.netherPortals.size() == 1
        viewer.endPortals.size() == 1
    }

    @IgnoreIf({ !Boolean.getBoolean('javafx.test.enabled') })
    def "PortalViewer handles empty portal list"() {
        when: "creating viewer with no portals"
        def viewer = new PortalViewer([])

        then: "viewer initializes with empty lists"
        viewer.allPortals.size() == 0
        viewer.overworldPortals.size() == 0
        viewer.netherPortals.size() == 0
        viewer.endPortals.size() == 0
    }

    @IgnoreIf({ !Boolean.getBoolean('javafx.test.enabled') })
    def "PortalViewer handles null portal list"() {
        when: "creating viewer with null"
        def viewer = new PortalViewer(null)

        then: "viewer initializes with empty lists"
        viewer.allPortals.size() == 0
    }
}

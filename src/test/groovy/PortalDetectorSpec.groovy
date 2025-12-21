import spock.lang.Specification

/**
 * Unit tests for PortalDetector utility class.
 * Tests portal clustering algorithm, grouping, and portal structure creation.
 */
class PortalDetectorSpec extends Specification {

    // =========================================================================
    // Portal class Tests
    // =========================================================================

    def "Portal.toCsvRow should generate correct CSV format"() {
        given:
        PortalDetector.Portal portal = new PortalDetector.Portal(
            'overworld', 100, 64, 200,  // anchor
            4, 5, 'z', 20,              // width, height, axis, blockCount
            100.0, 66.5, 201.5          // center
        )

        when:
        String csv = portal.toCsvRow()

        then:
        csv == 'overworld,100,64,200,4,5,z,20,100.0,66.5,201.5'
    }

    def "Portal.toMap should generate correct map structure"() {
        given:
        PortalDetector.Portal portal = new PortalDetector.Portal(
            'nether', 0, 63, 0,
            3, 4, 'x', 12,
            1.0, 64.5, 0.0
        )

        when:
        Map<String, Object> map = portal.toMap()

        then:
        map.dimension == 'nether'
        map.anchor.x == 0
        map.anchor.y == 63
        map.anchor.z == 0
        map.size.width == 3
        map.size.height == 4
        map.axis == 'x'
        map.block_count == 12
        map.center.x == 1.0
        map.center.y == 64.5
        map.center.z == 0.0
    }

    def "Portal.toString should generate readable string"() {
        given:
        PortalDetector.Portal portal = new PortalDetector.Portal(
            'overworld', 100, 64, 200,
            4, 5, 'z', 20,
            100.0, 66.5, 201.5
        )

        when:
        String str = portal

        then:
        str.contains('overworld')
        str.contains('100')
        str.contains('64')
        str.contains('200')
        str.contains('4x5')
        str.contains('z')
        str.contains('20')
    }

    // =========================================================================
    // groupByDimensionAndAxis() Tests
    // =========================================================================

    def "groupByDimensionAndAxis should group blocks by dimension"() {
        given:
        List<BlockSearcher.BlockLocation> blocks = [
            makeBlockLocation('overworld', 'z', 0, 0, 0),
            makeBlockLocation('nether', 'z', 0, 0, 0),
            makeBlockLocation('overworld', 'z', 10, 0, 10)
        ]

        when:
        Map<String, Map<String, List<BlockSearcher.BlockLocation>>> grouped =
            PortalDetector.groupByDimensionAndAxis(blocks)

        then:
        grouped.keySet().size() == 2
        grouped.containsKey('overworld')
        grouped.containsKey('nether')
        grouped['overworld']['z'].size() == 2
        grouped['nether']['z'].size() == 1
    }

    def "groupByDimensionAndAxis should group blocks by axis"() {
        given:
        List<BlockSearcher.BlockLocation> blocks = [
            makeBlockLocation('overworld', 'z', 0, 0, 0),
            makeBlockLocation('overworld', 'x', 0, 0, 0),
            makeBlockLocation('overworld', 'z', 10, 0, 10)
        ]

        when:
        Map<String, Map<String, List<BlockSearcher.BlockLocation>>> grouped =
            PortalDetector.groupByDimensionAndAxis(blocks)

        then:
        grouped['overworld'].keySet().size() == 2
        grouped['overworld'].containsKey('z')
        grouped['overworld'].containsKey('x')
        grouped['overworld']['z'].size() == 2
        grouped['overworld']['x'].size() == 1
    }

    def "groupByDimensionAndAxis should use defaults for missing properties"() {
        given:
        BlockSearcher.BlockLocation block = new BlockSearcher.BlockLocation(
            'minecraft:nether_portal', null, 0, 0, 0, null, 'test.mca'
        )

        when:
        Map<String, Map<String, List<BlockSearcher.BlockLocation>>> grouped =
            PortalDetector.groupByDimensionAndAxis([block])

        then:
        grouped.containsKey('overworld')  // Default dimension
        grouped['overworld'].containsKey('z')  // Default axis
    }

    // =========================================================================
    // getAdjacentCoords() Tests
    // =========================================================================

    def "getAdjacentCoords should return correct coords for axis z"() {
        given:
        BlockSearcher.BlockLocation block = makeBlockLocation('overworld', 'z', 100, 64, 200)

        when:
        List<String> adjacent = PortalDetector.getAdjacentCoords(block, 'z')

        then:
        adjacent.size() == 4
        adjacent.contains('100,64,199')  // north (z-1)
        adjacent.contains('100,64,201')  // south (z+1)
        adjacent.contains('100,63,200')  // down (y-1)
        adjacent.contains('100,65,200')  // up (y+1)
    }

    def "getAdjacentCoords should return correct coords for axis x"() {
        given:
        BlockSearcher.BlockLocation block = makeBlockLocation('overworld', 'x', 100, 64, 200)

        when:
        List<String> adjacent = PortalDetector.getAdjacentCoords(block, 'x')

        then:
        adjacent.size() == 4
        adjacent.contains('99,64,200')   // west (x-1)
        adjacent.contains('101,64,200')  // east (x+1)
        adjacent.contains('100,63,200')  // down (y-1)
        adjacent.contains('100,65,200')  // up (y+1)
    }

    // =========================================================================
    // clusterAdjacentBlocks() Tests
    // =========================================================================

    def "clusterAdjacentBlocks should return empty list for empty input"() {
        expect:
        PortalDetector.clusterAdjacentBlocks([], 'z').empty
    }

    def "clusterAdjacentBlocks should cluster adjacent blocks for axis z"() {
        given:
        // Create a 2x2 portal (4 blocks) aligned north-south (axis=z)
        List<BlockSearcher.BlockLocation> blocks = [
            makeBlockLocation('overworld', 'z', 100, 64, 200),  // Bottom-left
            makeBlockLocation('overworld', 'z', 100, 64, 201),  // Bottom-right
            makeBlockLocation('overworld', 'z', 100, 65, 200),  // Top-left
            makeBlockLocation('overworld', 'z', 100, 65, 201)   // Top-right
        ]

        when:
        List<Set<BlockSearcher.BlockLocation>> clusters = PortalDetector.clusterAdjacentBlocks(blocks, 'z')

        then:
        clusters.size() == 1  // All blocks should be in one cluster
        clusters[0].size() == 4
    }

    def "clusterAdjacentBlocks should separate non-adjacent blocks"() {
        given:
        // Two separate portals
        List<BlockSearcher.BlockLocation> blocks = [
            makeBlockLocation('overworld', 'z', 100, 64, 200),
            makeBlockLocation('overworld', 'z', 100, 64, 201),
            makeBlockLocation('overworld', 'z', 200, 64, 200),  // Far away
            makeBlockLocation('overworld', 'z', 200, 64, 201)
        ]

        when:
        List<Set<BlockSearcher.BlockLocation>> clusters = PortalDetector.clusterAdjacentBlocks(blocks, 'z')

        then:
        clusters.size() == 2  // Two separate clusters
        clusters[0].size() == 2
        clusters[1].size() == 2
    }

    def "clusterAdjacentBlocks should handle single block"() {
        given:
        List<BlockSearcher.BlockLocation> blocks = [
            makeBlockLocation('overworld', 'z', 100, 64, 200)
        ]

        when:
        List<Set<BlockSearcher.BlockLocation>> clusters = PortalDetector.clusterAdjacentBlocks(blocks, 'z')

        then:
        clusters.size() == 1
        clusters[0].size() == 1
    }

    // =========================================================================
    // portalFromCluster() Tests
    // =========================================================================

    def "portalFromCluster should return null for empty cluster"() {
        expect:
        PortalDetector.portalFromCluster([] as Set, 'overworld', 'z') == null
    }

    def "portalFromCluster should calculate dimensions for axis z portal"() {
        given:
        // 3x4 portal aligned north-south (axis=z)
        Set<BlockSearcher.BlockLocation> cluster = [
            makeBlockLocation('overworld', 'z', 100, 64, 200),  // minZ, minY
            makeBlockLocation('overworld', 'z', 100, 64, 201),
            makeBlockLocation('overworld', 'z', 100, 64, 202),  // maxZ
            makeBlockLocation('overworld', 'z', 100, 65, 200),
            makeBlockLocation('overworld', 'z', 100, 66, 200),
            makeBlockLocation('overworld', 'z', 100, 67, 200)  // maxY
        ] as Set

        when:
        PortalDetector.Portal portal = PortalDetector.portalFromCluster(cluster, 'overworld', 'z')

        then:
        portal != null
        portal.dimension == 'overworld'
        portal.anchorX == 100
        portal.anchorY == 64
        portal.anchorZ == 200
        portal.width == 3   // maxZ - minZ + 1 = 202 - 200 + 1
        portal.height == 4  // maxY - minY + 1 = 67 - 64 + 1
        portal.axis == 'z'
        portal.blockCount == 6
        portal.centerX == 100.0  // X is constant for axis=z
        portal.centerY == 65.5   // minY + (height-1)/2 = 64 + 1.5
        portal.centerZ == 201.0   // minZ + (width-1)/2 = 200 + 1.0
    }

    def "portalFromCluster should calculate dimensions for axis x portal"() {
        given:
        // 2x3 portal aligned east-west (axis=x)
        Set<BlockSearcher.BlockLocation> cluster = [
            makeBlockLocation('nether', 'x', 100, 64, 200),  // minX, minY, Z constant
            makeBlockLocation('nether', 'x', 101, 64, 200),  // maxX
            makeBlockLocation('nether', 'x', 100, 65, 200),
            makeBlockLocation('nether', 'x', 100, 66, 200)  // maxY
        ] as Set

        when:
        PortalDetector.Portal portal = PortalDetector.portalFromCluster(cluster, 'nether', 'x')

        then:
        portal != null
        portal.width == 2   // maxX - minX + 1 = 101 - 100 + 1
        portal.height == 3  // maxY - minY + 1 = 66 - 64 + 1
        portal.anchorZ == 200  // Z is constant for axis=x
        portal.centerX == 100.5  // minX + (width-1)/2 = 100 + 0.5
        portal.centerZ == 200.0   // Z is constant
    }

    def "portalFromCluster should handle single block portal"() {
        given:
        Set<BlockSearcher.BlockLocation> cluster = [
            makeBlockLocation('overworld', 'z', 100, 64, 200)
        ] as Set

        when:
        PortalDetector.Portal portal = PortalDetector.portalFromCluster(cluster, 'overworld', 'z')

        then:
        portal != null
        portal.width == 1
        portal.height == 1
        portal.blockCount == 1
    }

    // =========================================================================
    // detectPortals() Tests
    // =========================================================================

    def "detectPortals should return empty list for empty input"() {
        expect:
        PortalDetector.detectPortals([]).empty
    }

    def "detectPortals should return empty list for null input"() {
        expect:
        PortalDetector.detectPortals(null).empty
    }

    def "detectPortals should detect single portal"() {
        given:
        List<BlockSearcher.BlockLocation> blocks = [
            makeBlockLocation('overworld', 'z', 100, 64, 200),
            makeBlockLocation('overworld', 'z', 100, 64, 201),
            makeBlockLocation('overworld', 'z', 100, 65, 200),
            makeBlockLocation('overworld', 'z', 100, 65, 201)
        ]

        when:
        List<PortalDetector.Portal> portals = PortalDetector.detectPortals(blocks)

        then:
        portals.size() == 1
        portals[0].blockCount == 4
    }

    def "detectPortals should detect multiple separate portals"() {
        given:
        List<BlockSearcher.BlockLocation> blocks = [
            // Portal 1
            makeBlockLocation('overworld', 'z', 100, 64, 200),
            makeBlockLocation('overworld', 'z', 100, 64, 201),
            // Portal 2 (far away)
            makeBlockLocation('overworld', 'z', 200, 64, 200),
            makeBlockLocation('overworld', 'z', 200, 64, 201)
        ]

        when:
        List<PortalDetector.Portal> portals = PortalDetector.detectPortals(blocks)

        then:
        portals.size() == 2
    }

    // =========================================================================
    // CSV_HEADER Tests
    // =========================================================================

    def "CSV_HEADER should return correct CSV header"() {
        expect:
        PortalDetector.CSV_HEADER == 'dimension,x,y,z,width,height,axis,block_count,center_x,center_y,center_z'
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private BlockSearcher.BlockLocation makeBlockLocation(String dimension, String axis, int x, int y, int z) {
        Map<String, String> properties = ['axis': axis]
        return new BlockSearcher.BlockLocation(
            'minecraft:nether_portal',
            dimension,
            x, y, z,
            properties,
            'test.mca'
        )
    }

}

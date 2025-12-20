/**
 * Portal detection with intelligent clustering.
 *
 * Clusters adjacent nether_portal blocks into portal structures and calculates:
 * - Portal dimensions (width × height)
 * - Center coordinates for waypoint placement
 * - Anchor point (bottom-left corner)
 *
 * Portal Block Properties:
 * - axis: "x" (east-west) or "z" (north-south)
 * - Minimum portal: 4×5 frame → 2×3 interior (6 portal blocks)
 * - Maximum portal: 23×23 frame → 21×21 interior (441 portal blocks)
 *
 * References:
 * - Portal block: https://minecraft.wiki/w/Nether_Portal_(block)
 * - Portal structure: https://minecraft.wiki/w/Nether_portal
 */
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PortalDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger(PortalDetector)

    /**
     * Data class representing a detected portal structure
     */
    static class Portal {

        String dimension
        int anchorX, anchorY, anchorZ  // Bottom-left corner
        int width, height
        String axis
        int blockCount
        double centerX, centerY, centerZ

        Portal(String dimension, int anchorX, int anchorY, int anchorZ,
               int width, int height, String axis, int blockCount,
               double centerX, double centerY, double centerZ) {
            this.dimension = dimension
            this.anchorX = anchorX
            this.anchorY = anchorY
            this.anchorZ = anchorZ
            this.width = width
            this.height = height
            this.axis = axis
            this.blockCount = blockCount
            this.centerX = centerX
            this.centerY = centerY
            this.centerZ = centerZ
               }

        String toCsvRow() {
            return "${dimension},${anchorX},${anchorY},${anchorZ}," +
                   "${width},${height},${axis},${blockCount}," +
                   "${centerX},${centerY},${centerZ}"
        }

        Map<String, Object> toMap() {
            return [
                dimension: dimension,
                anchor: [x: anchorX, y: anchorY, z: anchorZ],
                center: [x: centerX, y: centerY, z: centerZ],
                size: [width: width, height: height],
                axis: axis,
                block_count: blockCount
            ]
        }

        @Override
        String toString() {
            return "Portal at ${dimension}:(${anchorX}, ${anchorY}, ${anchorZ}) " +
                   "${width}x${height} axis=${axis} blocks=${blockCount}"
        }

    }

    /**
     * Main entry point: Detect portals from raw nether_portal block locations
     *
     * @param portalBlocks List of BlockLocation objects (all should be minecraft:nether_portal)
     * @return List of Portal structures
     */
    static List<Portal> detectPortals(List<BlockSearcher.BlockLocation> portalBlocks) {
        if (!portalBlocks || portalBlocks.empty) {
            LOGGER.info('No portal blocks provided for clustering')
            return []
        }

        LOGGER.info("Clustering ${portalBlocks.size()} portal blocks into structures...")

        List<Portal> portals = []

        // Group blocks by dimension and axis
        Map<String, Map<String, List<BlockSearcher.BlockLocation>>> grouped = groupByDimensionAndAxis(portalBlocks)

        grouped.each { String dimension, Map<String, List<BlockSearcher.BlockLocation>> byAxis ->
            byAxis.each { String axis, List<BlockSearcher.BlockLocation> blocks ->
                LOGGER.debug("Processing ${dimension}/${axis}: ${blocks.size()} blocks")

                // Cluster adjacent blocks using flood-fill
                List<Set<BlockSearcher.BlockLocation>> clusters = clusterAdjacentBlocks(blocks, axis)

                clusters.each { Set<BlockSearcher.BlockLocation> cluster ->
                    Portal portal = portalFromCluster(cluster, dimension, axis)
                    portals.add(portal)
                    LOGGER.debug("Created portal: ${portal}")
                }
            }
        }

        LOGGER.info("Detected ${portals.size()} portal structures")
        return portals
    }

    /**
     * Group blocks by dimension and axis property
     */
    static Map<String, Map<String, List<BlockSearcher.BlockLocation>>> groupByDimensionAndAxis(
            List<BlockSearcher.BlockLocation> blocks) {

        Map<String, Map<String, List<BlockSearcher.BlockLocation>>> result = [:]

        blocks.each { BlockSearcher.BlockLocation block ->
            String dimension = block.dimension ?: 'overworld'
            String axis = block.properties?.get('axis') ?: 'z'

            if (!result.containsKey(dimension)) {
                result[dimension] = [:]
            }
            if (!result[dimension].containsKey(axis)) {
                result[dimension][axis] = []
            }
            result[dimension][axis].add(block)
        }

        return result
            }

    /**
     * Cluster adjacent blocks using flood-fill algorithm
     *
     * Adjacency is defined as blocks that share a face (not diagonal):
     * - For axis=z portals: adjacent on X or Y (portal lies in XY plane)
     * - For axis=x portals: adjacent on Z or Y (portal lies in ZY plane)
     *
     * @param blocks List of portal blocks with same dimension and axis
     * @param axis The portal axis ("x" or "z")
     * @return List of clusters, each containing connected portal blocks
     */
    static List<Set<BlockSearcher.BlockLocation>> clusterAdjacentBlocks(
            List<BlockSearcher.BlockLocation> blocks, String axis) {

        if (!blocks || blocks.empty) {
            return []
        }

        // Build coordinate lookup map for O(1) adjacency checks
        Map<String, BlockSearcher.BlockLocation> coordMap = [:]
        blocks.each { BlockSearcher.BlockLocation block ->
            String key = "${block.x},${block.y},${block.z}"
            coordMap[key] = block
        }

        Set<BlockSearcher.BlockLocation> visited = [] as Set
        List<Set<BlockSearcher.BlockLocation>> clusters = []

        blocks.each { BlockSearcher.BlockLocation block ->
            if (visited.contains(block)) {
                return
            }

            // Flood-fill from this block
            Set<BlockSearcher.BlockLocation> cluster = [] as Set
            Queue<BlockSearcher.BlockLocation> queue = [] as Queue
            queue.add(block)

            while (!queue.empty) {
                BlockSearcher.BlockLocation current = queue.poll()
                if (visited.contains(current)) {
                    continue
                }

                visited.add(current)
                cluster.add(current)

                // Check all adjacent positions
                List<String> adjacentCoords = getAdjacentCoords(current, axis)
                adjacentCoords.each { String coord ->
                    BlockSearcher.BlockLocation neighbor = coordMap[coord]
                    if (neighbor && !visited.contains(neighbor)) {
                        queue.add(neighbor)
                    }
                }
            }

            if (!cluster.empty) {
                clusters.add(cluster)
            }
        }

        return clusters
            }

    /**
     * Get adjacent coordinate strings based on portal axis
     *
     * Portal blocks are 2D planes:
     * - axis=z: Portal aligned north-south (extends along Z and Y), X is constant
     * - axis=x: Portal aligned east-west (extends along X and Y), Z is constant
     *
     * Reference: https://minecraft.wiki/w/Nether_Portal_(block)
     */
    static List<String> getAdjacentCoords(BlockSearcher.BlockLocation block, String axis) {
        int x = block.x
        int y = block.y
        int z = block.z

        if (axis == 'z') {
            // Portal aligned north-south: varies on Z (width) and Y (height), X is constant
            return [
                "${x},${y},${z - 1}".toString(),  // left (north)
                "${x},${y},${z + 1}".toString(),  // right (south)
                "${x},${y - 1},${z}".toString(),  // down
                "${x},${y + 1},${z}".toString()   // up
            ]
        }
        // Portal aligned east-west: varies on X (width) and Y (height), Z is constant
        return [
            "${x - 1},${y},${z}".toString(),  // left (west)
            "${x + 1},${y},${z}".toString(),  // right (east)
            "${x},${y - 1},${z}".toString(),  // down
            "${x},${y + 1},${z}".toString()   // up
        ]
    }

    /**
     * Create a Portal object from a cluster of blocks
     *
     * Calculates:
     * - Anchor point (minimum X/Y/Z - bottom-left corner)
     * - Width and height
     * - Center coordinates
     */
    static Portal portalFromCluster(Set<BlockSearcher.BlockLocation> cluster,
                                     String dimension, String axis) {
        if (cluster.empty) {
            return null
        }

        // Find bounding box
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE

        cluster.each { BlockSearcher.BlockLocation block ->
            minX = Math.min(minX, block.x)
            maxX = Math.max(maxX, block.x)
            minY = Math.min(minY, block.y)
            maxY = Math.max(maxY, block.y)
            minZ = Math.min(minZ, block.z)
            maxZ = Math.max(maxZ, block.z)
        }

        // Calculate dimensions based on axis
        // axis=z: Portal aligned north-south, extends on Z (width) and Y (height), X constant
        // axis=x: Portal aligned east-west, extends on X (width) and Y (height), Z constant
        int width, height
        int anchorX, anchorY, anchorZ
        double centerX, centerY, centerZ

        if (axis == 'z') {
            // Portal aligned north-south: width on Z, height on Y, X is constant
            width = maxZ - minZ + 1
            height = maxY - minY + 1
            anchorX = minX  // X is constant for axis=z portals
            anchorY = minY
            anchorZ = minZ

            centerX = minX
            centerY = minY + (height - 1) / 2.0
            centerZ = minZ + (width - 1) / 2.0
        } else {
            // Portal aligned east-west: width on X, height on Y, Z is constant
            width = maxX - minX + 1
            height = maxY - minY + 1
            anchorX = minX
            anchorY = minY
            anchorZ = minZ  // Z is constant for axis=x portals

            centerX = minX + (width - 1) / 2.0
            centerY = minY + (height - 1) / 2.0
            centerZ = minZ
        }

        return new Portal(
            dimension,
            anchorX, anchorY, anchorZ,
            width, height, axis,
            cluster.size(),
            centerX, centerY, centerZ
        )
                                           }

    /**
     * Find all portals in a world (convenience method)
     *
     * @param worldPath Path to Minecraft world
     * @param dimensions List of dimensions to search
     * @return List of detected Portal structures
     */
    static List<Portal> findPortalsInWorld(String worldPath, List<String> dimensions) {
        Set<String> targetBlocks = ['minecraft:nether_portal'] as Set

        List<BlockSearcher.BlockLocation> portalBlocks = BlockSearcher.searchBlocks(
            worldPath, targetBlocks, dimensions
        )

        return detectPortals(portalBlocks)
    }

    /**
     * CSV header for portal output
     */
    static final String CSV_HEADER = 'dimension,x,y,z,width,height,axis,block_count,center_x,center_y,center_z'

}

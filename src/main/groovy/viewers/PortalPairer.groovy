package viewers

/**
 * Utility class for pairing Minecraft portals between dimensions.
 * Implements the Minecraft portal linking algorithm with coordinate conversion,
 * distance calculation, and confidence scoring.
 */
class PortalPairer {

    /**
     * Result of a portal pairing operation
     */
    static class PairingResult {
        Map overworldPortal
        Map netherPortal
        double distance
        ConfidenceLevel confidence
        String description

        /**
         * Get the portal ID for this pairing (from whichever portal exists)
         */
        String getPortalId() {
            return overworldPortal?.portalId ?: netherPortal?.portalId
        }

        /**
         * Check if this pairing represents an orphan portal
         */
        boolean isOrphan() {
            return confidence == ConfidenceLevel.ORPHAN
        }

        /**
         * Get the source portal (the one that exists)
         */
        Map getSourcePortal() {
            return overworldPortal ?: netherPortal
        }
    }

    /**
     * Confidence level for portal pairings based on distance
     */
    enum ConfidenceLevel {
        EXACT(100, "Exact match"),
        CLOSE(95, "Very close"),
        LIKELY(80, "Likely match"),
        UNCERTAIN(50, "Uncertain"),
        ORPHAN(0, "No pair found")

        final int percentage
        final String label

        ConfidenceLevel(int pct, String lbl) {
            this.percentage = pct
            this.label = lbl
        }

        @Override
        String toString() {
            return "${label} (${percentage}%)"
        }
    }

    /**
     * Calculate expected Nether coordinates from Overworld coords.
     * Uses floor division (rounds toward negative infinity).
     *
     * @param owX Overworld X coordinate
     * @param owZ Overworld Z coordinate
     * @return [netherX, netherZ] array
     */
    static int[] toNetherCoords(int owX, int owZ) {
        return [Math.floorDiv(owX, 8), Math.floorDiv(owZ, 8)] as int[]
    }

    /**
     * Calculate expected Overworld coordinates from Nether coords.
     *
     * @param netherX Nether X coordinate
     * @param netherZ Nether Z coordinate
     * @return [overworldX, overworldZ] array
     */
    static int[] toOverworldCoords(int netherX, int netherZ) {
        return [netherX * 8, netherZ * 8] as int[]
    }

    /**
     * Calculate 3D Euclidean distance between two points
     */
    private static double calculateDistance(int x1, int y1, int z1, int x2, int y2, int z2) {
        double dx = x2 - x1
        double dy = y2 - y1
        double dz = z2 - z1
        return Math.sqrt(dx * dx + dy * dy + dz * dz)
    }

    /**
     * Get the center coordinates of a portal
     */
    private static int[] getPortalCenter(Map portal) {
        int x = ((portal.minX as int) + (portal.maxX as int)) / 2 as int
        int y = ((portal.minY as int) + (portal.maxY as int)) / 2 as int
        int z = ((portal.minZ as int) + (portal.maxZ as int)) / 2 as int
        return [x, y, z] as int[]
    }

    /**
     * Determine confidence level based on distance
     */
    private static ConfidenceLevel getConfidenceLevel(double distance) {
        if (distance <= 1) return ConfidenceLevel.EXACT
        if (distance <= 16) return ConfidenceLevel.CLOSE
        if (distance <= 128) return ConfidenceLevel.LIKELY
        return ConfidenceLevel.UNCERTAIN
    }

    /**
     * Find the linked portal for a given source portal.
     *
     * Algorithm:
     * 1. Convert source coords to expected target dimension coords
     * 2. Search within radius (128 blocks in Nether, 16 in OW due to 8:1 ratio)
     * 3. Find closest portal using 3D Euclidean distance
     * 4. Assign confidence level based on distance
     *
     * @param sourcePortal The portal to find a pair for
     * @param targetDimensionPortals List of portals in the target dimension
     * @return PairingResult with matched portal or orphan status
     */
    static PairingResult findPair(Map sourcePortal, List<Map> targetDimensionPortals) {
        def sourceDim = sourcePortal.dimension
        int[] sourceCenter = getPortalCenter(sourcePortal)
        int sourceX = sourceCenter[0]
        int sourceY = sourceCenter[1]
        int sourceZ = sourceCenter[2]

        // Calculate expected coordinates in target dimension
        int[] expectedCoords
        int searchRadius

        if (sourceDim == 'overworld') {
            expectedCoords = toNetherCoords(sourceX, sourceZ)
            searchRadius = 128  // In Nether blocks
        } else if (sourceDim == 'nether') {
            expectedCoords = toOverworldCoords(sourceX, sourceZ)
            searchRadius = 128  // In Overworld blocks (16 Nether * 8)
        } else {
            // End portals don't link to other dimensions in this algorithm
            return new PairingResult(
                overworldPortal: null,
                netherPortal: null,
                distance: -1,
                confidence: ConfidenceLevel.ORPHAN,
                description: "End portals are not paired"
            )
        }

        // Find closest portal within search radius
        Map closestPortal = null
        double minDistance = Double.MAX_VALUE

        targetDimensionPortals.each { portal ->
            int[] portalCenter = getPortalCenter(portal)
            int px = portalCenter[0]
            int py = portalCenter[1]
            int pz = portalCenter[2]

            // Calculate distance from expected coords (X/Z) and actual Y
            double distance = calculateDistance(
                expectedCoords[0], sourceY, expectedCoords[1],
                px, py, pz
            )

            if (distance < minDistance && distance <= searchRadius) {
                minDistance = distance
                closestPortal = portal
            }
        }

        // No portal found within search radius
        if (!closestPortal) {
            return new PairingResult(
                overworldPortal: sourceDim == 'overworld' ? sourcePortal : null,
                netherPortal: sourceDim == 'nether' ? sourcePortal : null,
                distance: -1,
                confidence: ConfidenceLevel.ORPHAN,
                description: "No linked portal found within ${searchRadius} blocks"
            )
        }

        // Determine confidence level
        ConfidenceLevel confidence = getConfidenceLevel(minDistance)

        // Build result
        return new PairingResult(
            overworldPortal: sourceDim == 'overworld' ? sourcePortal : closestPortal,
            netherPortal: sourceDim == 'nether' ? sourcePortal : closestPortal,
            distance: minDistance,
            confidence: confidence,
            description: "Linked at ${minDistance.round(1)} blocks (${confidence.percentage}% confidence)"
        )
    }

    /**
     * Pair all portals and return list of pairings.
     * Ensures each portal appears in exactly one pairing result.
     *
     * @param allPortals List of all portals from all dimensions
     * @return List of PairingResult objects
     */
    static List<PairingResult> pairAllPortals(List<Map> allPortals) {
        def owPortals = allPortals.findAll { it.dimension == 'overworld' }
        def netherPortals = allPortals.findAll { it.dimension == 'nether' }
        def results = []
        def pairedNetherPortals = [] as Set

        // Pair each Overworld portal
        owPortals.each { owPortal ->
            def result = findPair(owPortal, netherPortals)
            results << result

            // Track which Nether portals have been paired
            if (result.netherPortal) {
                pairedNetherPortals << result.netherPortal
            }
        }

        // Find orphan Nether portals (not paired from Overworld)
        netherPortals.findAll { !pairedNetherPortals.contains(it) }.each { orphanPortal ->
            def result = findPair(orphanPortal, owPortals)
            results << result
        }

        return results
    }

    /**
     * Generate a human-readable summary of a portal pairing
     */
    static String getPairingSummary(PairingResult result) {
        if (result.isOrphan()) {
            def portal = result.sourcePortal
            def dim = portal.dimension.capitalize()
            def center = getPortalCenter(portal)
            return "${dim} portal at (${center[0]}, ${center[1]}, ${center[2]}) - ORPHAN (no pair)"
        }

        def owCenter = getPortalCenter(result.overworldPortal)
        def netherCenter = getPortalCenter(result.netherPortal)

        return "OW(${owCenter[0]}, ${owCenter[1]}, ${owCenter[2]}) ↔ Nether(${netherCenter[0]}, ${netherCenter[1]}, ${netherCenter[2]}) - ${result.description}"
    }

    /**
     * Get statistics about portal pairings
     */
    static Map getPairingStatistics(List<PairingResult> results) {
        def stats = [
            total: results.size(),
            exact: 0,
            close: 0,
            likely: 0,
            uncertain: 0,
            orphans: 0,
            avgDistance: 0.0
        ]

        def totalDistance = 0.0
        def pairedCount = 0

        results.each { result ->
            switch (result.confidence) {
                case ConfidenceLevel.EXACT:
                    stats.exact++
                    break
                case ConfidenceLevel.CLOSE:
                    stats.close++
                    break
                case ConfidenceLevel.LIKELY:
                    stats.likely++
                    break
                case ConfidenceLevel.UNCERTAIN:
                    stats.uncertain++
                    break
                case ConfidenceLevel.ORPHAN:
                    stats.orphans++
                    break
            }

            if (result.distance >= 0) {
                totalDistance += result.distance
                pairedCount++
            }
        }

        if (pairedCount > 0) {
            stats.avgDistance = totalDistance / pairedCount
        }

        return stats
    }
}

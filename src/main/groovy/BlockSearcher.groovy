/**
 * Block search utilities for locating specific block types in Minecraft world saves.
 *
 * Features:
 * - Palette-first optimization for fast section skipping
 * - Multi-dimension support (Overworld, Nether, End)
 * - Compatible with all Minecraft versions (1.18+ using LoadFlags.RAW)
 * - Progress tracking with progress bar
 *
 * References:
 * - Chunk format: https://minecraft.wiki/w/Chunk_format
 * - Region file format: https://minecraft.wiki/w/Region_file_format
 * - Block states: https://minecraft.wiki/w/Block_states
 */
import me.tongfei.progressbar.ProgressBarBuilder
import me.tongfei.progressbar.ProgressBarStyle
import net.querz.mca.Chunk
import net.querz.mca.LoadFlags
import net.querz.mca.MCAFile
import net.querz.mca.MCAUtil
import net.querz.mca.Section
import net.querz.nbt.tag.CompoundTag
import net.querz.nbt.tag.ListTag
import net.querz.nbt.tag.StringTag
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class BlockSearcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(BlockSearcher)

    /**
     * Dimension folder paths relative to world root
     */
    static final Map<String, String> DIMENSION_FOLDERS = [
        'overworld': 'region',
        'nether'   : 'DIM-1/region',
        'end'      : 'DIM1/region'
    ]

    /**
     * Data class representing a found block location
     */
    static class BlockLocation {
        String blockType
        String dimension
        int x, y, z
        Map<String, String> properties
        String regionFile

        BlockLocation(String blockType, String dimension, int x, int y, int z,
                      Map<String, String> properties, String regionFile) {
            this.blockType = blockType
            this.dimension = dimension
            this.x = x
            this.y = y
            this.z = z
            this.properties = properties ?: [:]
            this.regionFile = regionFile
        }

        @Override
        boolean equals(Object obj) {
            if (!(obj instanceof BlockLocation)) return false
            BlockLocation other = (BlockLocation) obj
            return blockType == other.blockType &&
                   dimension == other.dimension &&
                   x == other.x && y == other.y && z == other.z
        }

        @Override
        int hashCode() {
            return Objects.hash(blockType, dimension, x, y, z)
        }

        String toCsvRow() {
            String propsStr = properties.collect { k, v -> "${k}=${v}" }.join(';')
            return "${blockType},${dimension},${x},${y},${z},${propsStr},${regionFile}"
        }

        @Override
        String toString() {
            return "BlockLocation{${blockType} at ${dimension}:(${x}, ${y}, ${z})}"
        }
    }

    /**
     * Main entry point: Search for specific block types across dimensions
     *
     * @param worldPath Path to Minecraft world save directory
     * @param targetBlocks Set of block IDs to search for (e.g., "minecraft:nether_portal")
     * @param dimensions List of dimensions to search (overworld, nether, end)
     * @return List of BlockLocation objects for all found blocks
     */
    static List<BlockLocation> searchBlocks(String worldPath, Set<String> targetBlocks, List<String> dimensions) {
        LOGGER.info("Starting block search for: ${targetBlocks}")
        LOGGER.info("Dimensions to search: ${dimensions}")

        List<BlockLocation> results = []
        Set<Integer> seenHashes = [] as Set  // Deduplication

        dimensions.each { String dimension ->
            String folderPath = DIMENSION_FOLDERS[dimension.toLowerCase()]
            if (!folderPath) {
                LOGGER.warn("Unknown dimension: ${dimension}")
                return
            }

            File regionFolder = new File(worldPath, folderPath)
            if (!regionFolder.exists() || !regionFolder.directory) {
                LOGGER.debug("Dimension folder not found: ${regionFolder.absolutePath}")
                return
            }

            List<File> regionFiles = regionFolder.listFiles()?.findAll {
                it.file && it.name.endsWith('.mca')
            } ?: []

            if (regionFiles.isEmpty()) {
                LOGGER.debug("No region files in ${dimension}")
                return
            }

            LOGGER.info("Processing ${dimension}: ${regionFiles.size()} region files")

            new ProgressBarBuilder()
                .setTaskName("${dimension}")
                .setInitialMax(regionFiles.size())
                .setStyle(ProgressBarStyle.ASCII)
                .build().withCloseable { pb ->
                    regionFiles.each { File file ->
                        try {
                            List<BlockLocation> fileResults = processRegionFile(
                                file, targetBlocks, dimension
                            )

                            // Deduplicate
                            fileResults.each { BlockLocation loc ->
                                if (seenHashes.add(loc.hashCode())) {
                                    results.add(loc)
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.warn("Failed to process ${file.name}: ${e.message}")
                        }
                        pb.step()
                    }
                }
        }

        LOGGER.info("Block search complete: found ${results.size()} blocks")
        return results
    }

    /**
     * Process a single region file with palette-first optimization
     *
     * @param regionFile The .mca region file to process
     * @param targetBlocks Set of block IDs to search for
     * @param dimension The dimension name (for results)
     * @return List of BlockLocation objects found in this region
     */
    static List<BlockLocation> processRegionFile(File regionFile, Set<String> targetBlocks, String dimension) {
        List<BlockLocation> results = []

        // Use RAW flag for compatibility with all Minecraft versions (1.18+)
        // BLOCK_STATES flag causes "Level" tag errors on newer chunk formats
        MCAFile mcaFile = MCAUtil.read(regionFile, LoadFlags.RAW)

        // Parse region coordinates from filename (r.X.Z.mca)
        String[] parts = regionFile.name.replace('.mca', '').split('\\.')
        int regionX = parts[1] as int
        int regionZ = parts[2] as int

        // Iterate all 32x32 chunks in region
        (0..31).each { int chunkLocalX ->
            (0..31).each { int chunkLocalZ ->
                Chunk chunk = mcaFile.getChunk(chunkLocalX, chunkLocalZ)
                if (!chunk) return

                // Calculate absolute chunk coordinates
                int chunkAbsX = regionX * 32 + chunkLocalX
                int chunkAbsZ = regionZ * 32 + chunkLocalZ

                List<BlockLocation> chunkResults = processChunk(
                    chunk, targetBlocks, dimension, regionFile.name,
                    chunkAbsX, chunkAbsZ
                )
                results.addAll(chunkResults)
            }
        }

        return results
    }

    /**
     * Process a single chunk with palette-first optimization
     *
     * The palette optimization works by checking if a section's palette contains
     * any of our target blocks BEFORE iterating all 4096 blocks in that section.
     * For rare blocks like nether portals, this skips 95%+ of sections.
     *
     * @param chunk The chunk to process
     * @param targetBlocks Set of block IDs to search for
     * @param dimension The dimension name
     * @param regionFileName The source region file name
     * @param chunkAbsX Absolute chunk X coordinate
     * @param chunkAbsZ Absolute chunk Z coordinate
     * @return List of BlockLocation objects found in this chunk
     */
    static List<BlockLocation> processChunk(Chunk chunk, Set<String> targetBlocks,
                                             String dimension, String regionFileName,
                                             int chunkAbsX, int chunkAbsZ) {
        List<BlockLocation> results = []

        CompoundTag chunkData = chunk.handle
        if (!chunkData) return results

        // Handle both pre-1.18 (Level wrapper) and post-1.18 (flat structure)
        CompoundTag level = chunkData.getCompoundTag('Level')
        CompoundTag chunkRoot = level ?: chunkData

        // Get sections list
        ListTag<?> sections = chunkRoot.getListTag('sections') ?: chunkRoot.getListTag('Sections')
        if (!sections || sections.size() == 0) return results

        sections.each { sectionTag ->
            if (!(sectionTag instanceof CompoundTag)) return

            CompoundTag section = (CompoundTag) sectionTag

            // Get section Y coordinate
            int sectionY = section.containsKey('Y') ?
                (section.get('Y') instanceof net.querz.nbt.tag.ByteTag ?
                    section.getByte('Y') : section.getInt('Y')) : 0

            // Get block_states compound
            CompoundTag blockStates = section.getCompoundTag('block_states')
            if (!blockStates) return

            // PALETTE-FIRST OPTIMIZATION: Check palette before iterating blocks
            ListTag<?> palette = blockStates.getListTag('palette')
            if (!palette || palette.size() == 0) return

            // Extract block names from palette
            Set<String> paletteBlocks = extractPaletteBlockNames(palette)

            // Fast rejection: skip section if no target blocks in palette
            boolean hasTargetBlocks = targetBlocks.any { target ->
                paletteBlocks.any { paletteBlock ->
                    blockMatchesTarget(paletteBlock, target)
                }
            }
            if (!hasTargetBlocks) return

            LOGGER.debug("Section Y=${sectionY} contains target blocks, scanning...")

            // Build palette index map for efficient lookup
            Map<Integer, CompoundTag> paletteMap = buildPaletteMap(palette, targetBlocks)
            if (paletteMap.isEmpty()) return

            // Get packed block data
            long[] data = null
            if (blockStates.containsKey('data')) {
                data = blockStates.getLongArray('data')
            }

            // If only one block type in palette and it's our target, all blocks match
            if (palette.size() == 1 && paletteMap.containsKey(0)) {
                CompoundTag blockTag = paletteMap[0]
                // All 4096 blocks are this type
                (0..15).each { int localY ->
                    (0..15).each { int localZ ->
                        (0..15).each { int localX ->
                            int worldX = chunkAbsX * 16 + localX
                            int worldY = sectionY * 16 + localY
                            int worldZ = chunkAbsZ * 16 + localZ

                            results.add(createBlockLocation(
                                blockTag, dimension, worldX, worldY, worldZ, regionFileName
                            ))
                        }
                    }
                }
                return
            }

            // Calculate bits per entry
            int paletteSize = palette.size()
            int bitsPerEntry = Math.max(4, (int) Math.ceil(Math.log(paletteSize) / Math.log(2)))
            int entriesPerLong = 64 / bitsPerEntry
            long mask = (1L << bitsPerEntry) - 1

            if (!data || data.length == 0) return

            // Iterate all 4096 blocks in section (YZX order)
            (0..15).each { int localY ->
                (0..15).each { int localZ ->
                    (0..15).each { int localX ->
                        int blockIndex = localY * 256 + localZ * 16 + localX
                        int longIndex = blockIndex / entriesPerLong
                        int entryIndex = blockIndex % entriesPerLong

                        if (longIndex >= data.length) return

                        int paletteIndex = (int) ((data[longIndex] >> (entryIndex * bitsPerEntry)) & mask)

                        if (paletteMap.containsKey(paletteIndex)) {
                            CompoundTag blockTag = paletteMap[paletteIndex]
                            int worldX = chunkAbsX * 16 + localX
                            int worldY = sectionY * 16 + localY
                            int worldZ = chunkAbsZ * 16 + localZ

                            results.add(createBlockLocation(
                                blockTag, dimension, worldX, worldY, worldZ, regionFileName
                            ))
                        }
                    }
                }
            }
        }

        return results
    }

    /**
     * Extract block names from a palette ListTag
     */
    static Set<String> extractPaletteBlockNames(ListTag<?> palette) {
        Set<String> names = [] as Set
        (0..<palette.size()).each { int i ->
            def entry = palette.get(i)
            if (entry instanceof CompoundTag) {
                CompoundTag tag = (CompoundTag) entry
                if (tag.containsKey('Name')) {
                    names.add(tag.getString('Name'))
                }
            }
        }
        return names
    }

    /**
     * Build a map of palette indices to block CompoundTags for target blocks only
     */
    static Map<Integer, CompoundTag> buildPaletteMap(ListTag<?> palette, Set<String> targetBlocks) {
        Map<Integer, CompoundTag> map = [:]
        (0..<palette.size()).each { int i ->
            def entry = palette.get(i)
            if (entry instanceof CompoundTag) {
                CompoundTag tag = (CompoundTag) entry
                if (tag.containsKey('Name')) {
                    String blockName = tag.getString('Name')
                    if (targetBlocks.any { target -> blockMatchesTarget(blockName, target) }) {
                        map[i] = tag
                    }
                }
            }
        }
        return map
    }

    /**
     * Check if a block name matches a target (with minecraft: prefix handling)
     */
    static boolean blockMatchesTarget(String blockName, String target) {
        // Normalize both to include minecraft: prefix
        String normalizedBlock = blockName.contains(':') ? blockName : "minecraft:${blockName}"
        String normalizedTarget = target.contains(':') ? target : "minecraft:${target}"
        return normalizedBlock == normalizedTarget
    }

    /**
     * Create a BlockLocation from a palette block tag
     */
    static BlockLocation createBlockLocation(CompoundTag blockTag, String dimension,
                                              int x, int y, int z, String regionFile) {
        String blockType = blockTag.getString('Name')

        Map<String, String> properties = [:]
        if (blockTag.containsKey('Properties')) {
            CompoundTag props = blockTag.getCompoundTag('Properties')
            props.keySet().each { String key ->
                def value = props.get(key)
                if (value instanceof StringTag) {
                    properties[key] = ((StringTag) value).getValue()
                }
            }
        }

        return new BlockLocation(blockType, dimension, x, y, z, properties, regionFile)
    }

    /**
     * Normalize block ID (add minecraft: prefix if missing)
     */
    static String normalizeBlockId(String blockId) {
        if (!blockId) return 'minecraft:air'
        return blockId.contains(':') ? blockId : "minecraft:${blockId}"
    }

    /**
     * Parse block IDs from comma-separated input
     */
    static Set<String> parseBlockIds(String input) {
        if (!input || input.trim().isEmpty()) {
            return [] as Set
        }
        return input.split(',')
            .collect { it.trim() }
            .findAll { !it.isEmpty() }
            .collect { normalizeBlockId(it) }
            .toSet()
    }
}

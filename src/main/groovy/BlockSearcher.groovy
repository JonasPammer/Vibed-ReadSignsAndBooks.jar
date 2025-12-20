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
        return searchBlocks(worldPath, targetBlocks, dimensions, null)
    }

    /**
     * Search for specific block types across dimensions with optional database indexing.
     *
     * @param worldPath Path to Minecraft world save directory
     * @param targetBlocks Set of block IDs to search for (e.g., "minecraft:nether_portal")
     * @param dimensions List of dimensions to search (overworld, nether, end)
     * @param database Optional BlockDatabase to write found blocks to (can be null)
     * @return List of BlockLocation objects for all found blocks
     */
    static List<BlockLocation> searchBlocks(String worldPath, Set<String> targetBlocks, List<String> dimensions,
                                             BlockDatabase database) {
        LOGGER.info("Starting block search for: ${targetBlocks}")
        LOGGER.info("Dimensions to search: ${dimensions}")
        if (database) {
            LOGGER.info("Building block index database...")
        }

        List<BlockLocation> results = []
        Set<Integer> seenHashes = [] as Set  // Deduplication

        // Use transaction for batch inserts if database is provided
        if (database) {
            database.beginTransaction()
        }

        try {
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

                                // Deduplicate and optionally write to database
                                fileResults.each { BlockLocation loc ->
                                    if (seenHashes.add(loc.hashCode())) {
                                        results.add(loc)

                                        // Write to database if provided
                                        if (database) {
                                            database.insertBlock(
                                                loc.blockType,
                                                loc.dimension,
                                                loc.x, loc.y, loc.z,
                                                loc.properties,
                                                loc.regionFile
                                            )
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                LOGGER.warn("Failed to process ${file.name}: ${e.message}")
                            }
                            pb.step()
                        }
                    }
            }

            // Commit transaction if database provided
            if (database) {
                database.commitTransaction()
            }
        } catch (Exception e) {
            // Rollback on error
            if (database) {
                database.rollbackTransaction()
            }
            throw e
        }

        LOGGER.info("Block search complete: found ${results.size()} blocks")
        return results
    }

    /**
     * Block types to always skip during index-all mode.
     * These are extremely common and generally not useful to index.
     */
    static final Set<String> EXCLUDED_BLOCK_TYPES = [
        'minecraft:air',
        'minecraft:cave_air'
    ] as Set

    /**
     * Index ALL blocks in the world to a database (not just specific types).
     * This is used when --search-blocks is specified without arguments (rarity-index mode).
     *
     * Features:
     * - Always skips air/cave_air blocks
     * - Tracks saturated types to avoid unnecessary database calls
     * - Uses palette-level skipping for optimal performance
     *
     * @param worldPath Path to Minecraft world save directory
     * @param dimensions List of dimensions to search (overworld, nether, end)
     * @param database BlockDatabase to write found blocks to
     */
    static void indexAllBlocks(String worldPath, List<String> dimensions, BlockDatabase database) {
        LOGGER.info("Building comprehensive block index (rarity-filtered)...")
        LOGGER.info("Dimensions to index: ${dimensions}")

        // Track block types that have reached their limit (avoid further insertBlock calls)
        Set<String> saturatedTypes = [] as Set

        // Use transaction for batch inserts
        database.beginTransaction()

        try {
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

                LOGGER.info("Indexing ${dimension}: ${regionFiles.size()} region files")

                new ProgressBarBuilder()
                    .setTaskName("${dimension}")
                    .setInitialMax(regionFiles.size())
                    .setStyle(ProgressBarStyle.ASCII)
                    .build().withCloseable { pb ->
                        regionFiles.each { File file ->
                            try {
                                indexRegionFile(file, dimension, database, saturatedTypes)
                            } catch (Exception e) {
                                LOGGER.warn("Failed to index ${file.name}: ${e.message}")
                            }
                            pb.step()
                        }
                    }
            }

            database.commitTransaction()
        } catch (Exception e) {
            database.rollbackTransaction()
            throw e
        }

        LOGGER.info("Block indexing complete: ${database.getBlockTypeCount()} block types, ${database.getTotalBlocksIndexed()} blocks indexed")
        if (!saturatedTypes.isEmpty()) {
            LOGGER.info("Saturated types (reached limit): ${saturatedTypes.size()}")
        }
    }

    /**
     * Index all blocks in a region file to the database.
     *
     * @param regionFile The .mca region file to process
     * @param dimension The dimension name
     * @param database The database to write to
     * @param saturatedTypes Set of block types that have reached their limit (updated in place)
     */
    static void indexRegionFile(File regionFile, String dimension, BlockDatabase database, Set<String> saturatedTypes) {
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

                indexChunk(chunk, dimension, regionFile.name, chunkAbsX, chunkAbsZ, database, saturatedTypes)
            }
        }
    }

    /**
     * Index all blocks in a chunk to the database with rarity filtering.
     *
     * Optimizations:
     * - Skips excluded block types (air, cave_air)
     * - Skips already-saturated block types (reached limit)
     * - Palette-level skipping: if all palette entries are excluded or saturated, skip entire section
     * - For uniform sections (single palette entry), stop early if type becomes saturated
     *
     * @param chunk The chunk to index
     * @param dimension The dimension name
     * @param regionFileName The region file name for reference
     * @param chunkAbsX Absolute chunk X coordinate
     * @param chunkAbsZ Absolute chunk Z coordinate
     * @param database The database to write to
     * @param saturatedTypes Set of block types that have reached their limit (updated in place)
     */
    static void indexChunk(Chunk chunk, String dimension, String regionFileName,
                           int chunkAbsX, int chunkAbsZ, BlockDatabase database, Set<String> saturatedTypes) {
        CompoundTag chunkData = chunk.handle
        if (!chunkData) return

        // Handle both pre-1.18 (Level wrapper) and post-1.18 (flat structure)
        CompoundTag level = chunkData.getCompoundTag('Level')
        CompoundTag chunkRoot = level ?: chunkData

        // Get sections list
        ListTag<?> sections = chunkRoot.getListTag('sections') ?: chunkRoot.getListTag('Sections')
        if (!sections || sections.size() == 0) return

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

            // Get palette
            ListTag<?> palette = blockStates.getListTag('palette')
            if (!palette || palette.size() == 0) return

            // Extract all block types from palette and check if section should be skipped
            List<String> paletteTypes = []
            boolean hasUsefulBlocks = false
            (0..<palette.size()).each { int i ->
                def entry = palette.get(i)
                if (entry instanceof CompoundTag) {
                    String blockType = ((CompoundTag) entry).getString('Name')
                    paletteTypes.add(blockType)
                    // A block is useful if it's not excluded AND not already saturated
                    if (!EXCLUDED_BLOCK_TYPES.contains(blockType) && !saturatedTypes.contains(blockType)) {
                        hasUsefulBlocks = true
                    }
                }
            }

            // Palette-level skip: if all blocks are excluded or saturated, skip entire section
            if (!hasUsefulBlocks) return

            // Get packed block data
            long[] data = null
            if (blockStates.containsKey('data')) {
                data = blockStates.getLongArray('data')
            }

            // If only one block type in palette, handle uniformly
            if (palette.size() == 1) {
                CompoundTag blockTag = (CompoundTag) palette.get(0)
                String blockType = blockTag.getString('Name')

                // Skip excluded blocks
                if (EXCLUDED_BLOCK_TYPES.contains(blockType)) return
                // Skip already saturated
                if (saturatedTypes.contains(blockType)) return

                Map<String, String> properties = extractBlockProperties(blockTag)

                // Insert blocks until type becomes saturated
                (0..15).each { int localY ->
                    if (saturatedTypes.contains(blockType)) return  // Early exit if saturated
                    (0..15).each { int localZ ->
                        if (saturatedTypes.contains(blockType)) return
                        (0..15).each { int localX ->
                            if (saturatedTypes.contains(blockType)) return
                            int worldX = chunkAbsX * 16 + localX
                            int worldY = sectionY * 16 + localY
                            int worldZ = chunkAbsZ * 16 + localZ
                            boolean inserted = database.insertBlock(blockType, dimension, worldX, worldY, worldZ, properties, regionFileName)
                            if (!inserted) {
                                // Insert returned false = limit reached, mark as saturated
                                saturatedTypes.add(blockType)
                            }
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

            // Build palette map
            Map<Integer, CompoundTag> paletteMap = [:]
            (0..<paletteSize).each { int i ->
                def entry = palette.get(i)
                if (entry instanceof CompoundTag) {
                    paletteMap[i] = (CompoundTag) entry
                }
            }

            // Iterate all 4096 blocks in section (YZX order)
            (0..15).each { int localY ->
                (0..15).each { int localZ ->
                    (0..15).each { int localX ->
                        int blockIndex = localY * 256 + localZ * 16 + localX
                        int longIndex = blockIndex / entriesPerLong
                        int entryIndex = blockIndex % entriesPerLong

                        if (longIndex >= data.length) return

                        int paletteIndex = (int) ((data[longIndex] >> (entryIndex * bitsPerEntry)) & mask)

                        CompoundTag blockTag = paletteMap[paletteIndex]
                        if (!blockTag) return

                        String blockType = blockTag.getString('Name')

                        // Skip excluded blocks
                        if (EXCLUDED_BLOCK_TYPES.contains(blockType)) return

                        // Skip already saturated blocks
                        if (saturatedTypes.contains(blockType)) return

                        Map<String, String> properties = extractBlockProperties(blockTag)

                        int worldX = chunkAbsX * 16 + localX
                        int worldY = sectionY * 16 + localY
                        int worldZ = chunkAbsZ * 16 + localZ

                        boolean inserted = database.insertBlock(blockType, dimension, worldX, worldY, worldZ, properties, regionFileName)
                        if (!inserted) {
                            // Insert returned false = limit reached, mark as saturated
                            saturatedTypes.add(blockType)
                        }
                    }
                }
            }
        }
    }

    /**
     * Extract block properties from a palette block tag.
     */
    static Map<String, String> extractBlockProperties(CompoundTag blockTag) {
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
        return properties
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

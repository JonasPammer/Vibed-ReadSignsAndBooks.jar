/**
 * Litematica (.litematic) export utilities for signs and books.
 *
 * Generates Litematica schematic files containing:
 * - Signs: Oak signs placed in a grid with original text and clickable coordinates
 * - Books: Chain command blocks that give shulker boxes organized by author
 *
 * Litematica format specification:
 * - Version 6 (current standard)
 * - GZip-compressed NBT
 * - Bit-packed block states in LongArray
 *
 * References:
 * - Litematica mod: https://github.com/maruohon/litematica
 * - Litemapy docs: https://litemapy.readthedocs.io/en/latest/litematics.html
 * - SchemConvert: https://github.com/PiTheGuy/SchemConvert
 *
 * This class is stateless - all methods are static utilities.
 */
import net.querz.nbt.io.NBTUtil
import net.querz.nbt.io.NamedTag
import net.querz.nbt.tag.CompoundTag
import net.querz.nbt.tag.IntTag
import net.querz.nbt.tag.ListTag
import net.querz.nbt.tag.LongArrayTag
import net.querz.nbt.tag.LongTag
import net.querz.nbt.tag.StringTag
import net.querz.nbt.tag.ByteTag
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class LitematicaExporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(LitematicaExporter)

    /** Litematica format version (current: 6) */
    static final int LITEMATICA_VERSION = 6

    /** Minecraft data version for 1.21 compatibility */
    static final int MINECRAFT_DATA_VERSION = 3837

    /** Block IDs */
    static final String BLOCK_AIR = 'minecraft:air'
    static final String BLOCK_OAK_SIGN = 'minecraft:oak_sign'
    static final String BLOCK_COMMAND_BLOCK = 'minecraft:command_block'
    static final String BLOCK_CHAIN_COMMAND_BLOCK = 'minecraft:chain_command_block'

    // ========== Main Export Methods ==========

    /**
     * Export signs to a Litematica file.
     *
     * Signs are placed in a grid layout:
     * - Each unique sign at a different X coordinate
     * - Duplicate signs stack along Z axis
     * - All signs at Y=0
     *
     * @param signsByHash Map of sign hash -> sign metadata (x, z, lines, originalX/Y/Z)
     * @param outputFile The output .litematic file
     * @param author Author name for metadata (default: 'ReadSignsAndBooks')
     * @param schematicName Name for the schematic (default: 'Extracted Signs')
     */
    static void exportSigns(Map<String, Map<String, Object>> signsByHash, File outputFile,
                           String author = 'ReadSignsAndBooks', String schematicName = 'Extracted Signs') {
        if (!signsByHash || signsByHash.isEmpty()) {
            LOGGER.info('No signs to export to Litematica')
            return
        }

        LOGGER.info("Exporting ${signsByHash.size()} signs to Litematica...")

        // Calculate dimensions from sign positions
        int maxX = signsByHash.values().collect { (it.x as Integer) ?: 0 }.max() + 1
        int maxZ = signsByHash.values().collect { (it.z as Integer) ?: 0 }.max() + 1
        int sizeY = 1  // Signs are flat

        int[] size = [maxX, sizeY, maxZ] as int[]
        int totalVolume = maxX * sizeY * maxZ

        // Build palette: [air, oak_sign]
        List<CompoundTag> palette = [
            createPaletteEntry(BLOCK_AIR),
            createPaletteEntry(BLOCK_OAK_SIGN)
        ]

        // Build block states array (initialized to air=0)
        int[] blockStates = new int[totalVolume]

        // Build tile entities list
        List<CompoundTag> tileEntities = []

        // Place each sign
        signsByHash.values().each { Map<String, Object> sign ->
            int x = (sign.x as Integer) ?: 0
            int z = (sign.z as Integer) ?: 0
            int y = 0

            // Set block state to oak_sign (palette index 1)
            int index = calculate3DIndex(x, y, z, maxX, maxZ)
            if (index >= 0 && index < blockStates.length) {
                blockStates[index] = 1
            }

            // Create tile entity
            List<String> lines = (sign.lines as List<String>) ?: ['', '', '', '']
            Integer origX = sign.originalX as Integer
            Integer origY = sign.originalY as Integer
            Integer origZ = sign.originalZ as Integer

            CompoundTag signEntity = createSignTileEntity(x, y, z, lines, origX, origY, origZ)
            tileEntities.add(signEntity)
        }

        // Pack block states
        long[] packed = packBlockStates(blockStates, palette.size())

        // Build region
        CompoundTag region = createRegion('Signs', [0, 0, 0] as int[], size, palette, packed, tileEntities)

        // Build root structure
        CompoundTag root = createLitematicaRoot(schematicName, author, 1, totalVolume, size)
        root.getCompoundTag('Regions').put('Signs', region)

        // Write file
        writeCompressedNBT(root, outputFile)
        LOGGER.info("Exported ${signsByHash.size()} signs to ${outputFile.absolutePath}")
    }

    /**
     * Export book commands to a Litematica file using chain command blocks.
     *
     * Command blocks are arranged in a line along the X axis:
     * - First block: Impulse command block (needs redstone/button)
     * - Subsequent blocks: Chain command blocks (auto-executing)
     *
     * @param booksByAuthor Map of author -> list of book metadata
     * @param outputFile The output .litematic file
     * @param author Author name for metadata (default: 'ReadSignsAndBooks')
     * @param schematicName Name for the schematic (default: 'Book Commands')
     * @param mcVersion Minecraft version for command format (default: '1_21')
     */
    static void exportBookCommands(Map<String, List<Map<String, Object>>> booksByAuthor, File outputFile,
                                   String author = 'ReadSignsAndBooks', String schematicName = 'Book Commands',
                                   String mcVersion = '1_21') {
        if (!booksByAuthor || booksByAuthor.isEmpty()) {
            LOGGER.info('No books to export to Litematica')
            return
        }

        LOGGER.info("Generating shulker box commands for ${booksByAuthor.size()} author(s)...")

        // Generate all shulker box commands
        List<String> commands = []

        booksByAuthor.keySet().sort().each { String bookAuthor ->
            List<Map<String, Object>> authorBooks = booksByAuthor[bookAuthor]
            int boxCount = (authorBooks.size() + 26).intdiv(27)  // Ceiling division

            (0..<boxCount).each { int boxIndex ->
                String command = ShulkerBoxGenerator.generateShulkerBoxCommand(
                    bookAuthor, authorBooks, boxIndex, mcVersion)
                if (command) {
                    commands.add(command)
                }
            }
        }

        if (commands.isEmpty()) {
            LOGGER.info('No commands generated')
            return
        }

        LOGGER.info("Exporting ${commands.size()} command blocks to Litematica...")

        // Calculate dimensions
        int sizeX = commands.size()
        int sizeY = 1
        int sizeZ = 1
        int[] size = [sizeX, sizeY, sizeZ] as int[]
        int totalVolume = sizeX * sizeY * sizeZ

        // Build palette: [air, command_block, chain_command_block]
        List<CompoundTag> palette = [
            createPaletteEntry(BLOCK_AIR),
            createCommandBlockPaletteEntry(false),  // Impulse
            createCommandBlockPaletteEntry(true)    // Chain
        ]

        // Build block states array
        int[] blockStates = new int[totalVolume]
        blockStates[0] = 1  // First: impulse command block
        (1..<commands.size()).each { int i ->
            blockStates[i] = 2  // Rest: chain command blocks
        }

        // Build tile entities
        List<CompoundTag> tileEntities = []

        commands.eachWithIndex { String command, int i ->
            CompoundTag cmdBlock = (i == 0)
                ? createImpulseCommandBlock(i, 0, 0, command)
                : createChainCommandBlock(i, 0, 0, command)
            tileEntities.add(cmdBlock)
        }

        // Pack block states
        long[] packed = packBlockStates(blockStates, palette.size())

        // Build region
        CompoundTag region = createRegion('Commands', [0, 0, 0] as int[], size, palette, packed, tileEntities)

        // Build root structure
        CompoundTag root = createLitematicaRoot(schematicName, author, 1, totalVolume, size)
        root.getCompoundTag('Regions').put('Commands', region)

        // Write file
        writeCompressedNBT(root, outputFile)
        LOGGER.info("Exported ${commands.size()} command blocks to ${outputFile.absolutePath}")
    }

    // ========== NBT Structure Building ==========

    /**
     * Create the root Litematica NBT structure.
     *
     * @param name Schematic name
     * @param author Author name
     * @param regionCount Number of regions
     * @param totalVolume Total volume in blocks
     * @param enclosingSize Size array [x, y, z]
     * @return Root CompoundTag
     */
    static CompoundTag createLitematicaRoot(String name, String author, int regionCount,
                                            int totalVolume, int[] enclosingSize) {
        CompoundTag root = new CompoundTag()

        root.putInt('MinecraftDataVersion', MINECRAFT_DATA_VERSION)
        root.putInt('Version', LITEMATICA_VERSION)

        // Metadata
        CompoundTag metadata = new CompoundTag()
        metadata.putString('Name', name)
        metadata.putString('Author', author)
        metadata.putLong('TimeCreated', System.currentTimeMillis())
        metadata.putLong('TimeModified', System.currentTimeMillis())
        metadata.putInt('RegionCount', regionCount)
        metadata.putInt('TotalVolume', totalVolume)
        metadata.putInt('TotalBlocks', totalVolume)  // Non-air blocks (approximation)

        CompoundTag enclosingSizeTag = new CompoundTag()
        enclosingSizeTag.putInt('x', enclosingSize[0])
        enclosingSizeTag.putInt('y', enclosingSize[1])
        enclosingSizeTag.putInt('z', enclosingSize[2])
        metadata.put('EnclosingSize', enclosingSizeTag)

        root.put('Metadata', metadata)

        // Empty regions compound (to be filled by caller)
        root.put('Regions', new CompoundTag())

        return root
    }

    /**
     * Create a single region with blocks, palette, and tile entities.
     *
     * @param name Region name
     * @param position Position offset [x, y, z]
     * @param size Size [x, y, z]
     * @param palette List of palette CompoundTags
     * @param blockStates Packed block state LongArray
     * @param tileEntities List of tile entity CompoundTags
     * @return Region CompoundTag
     */
    static CompoundTag createRegion(String name, int[] position, int[] size,
                                    List<CompoundTag> palette, long[] blockStates,
                                    List<CompoundTag> tileEntities) {
        CompoundTag region = new CompoundTag()

        // Position
        CompoundTag posTag = new CompoundTag()
        posTag.putInt('x', position[0])
        posTag.putInt('y', position[1])
        posTag.putInt('z', position[2])
        region.put('Position', posTag)

        // Size
        CompoundTag sizeTag = new CompoundTag()
        sizeTag.putInt('x', size[0])
        sizeTag.putInt('y', size[1])
        sizeTag.putInt('z', size[2])
        region.put('Size', sizeTag)

        // BlockStatePalette
        ListTag<CompoundTag> paletteTag = new ListTag<>(CompoundTag)
        palette.each { paletteTag.add(it) }
        region.put('BlockStatePalette', paletteTag)

        // BlockStates (packed LongArray)
        region.put('BlockStates', new LongArrayTag(blockStates))

        // TileEntities
        ListTag<CompoundTag> tileEntitiesTag = new ListTag<>(CompoundTag)
        tileEntities.each { tileEntitiesTag.add(it) }
        region.put('TileEntities', tileEntitiesTag)

        // Empty entities list
        region.put('Entities', new ListTag<>(CompoundTag))

        // PendingBlockTicks and PendingFluidTicks (empty)
        region.put('PendingBlockTicks', new ListTag<>(CompoundTag))
        region.put('PendingFluidTicks', new ListTag<>(CompoundTag))

        return region
    }

    // ========== Block State Packing ==========

    /**
     * Pack block state indices into a LongArray using Litematica's bit-packing format.
     *
     * Algorithm:
     * - Bits per value = max(2, ceil(log2(paletteSize)))
     * - Order: X -> Z -> Y (iterate X fastest, Y slowest)
     * - Pack sequentially into 64-bit longs
     *
     * @param blockStates Array of palette indices ordered [x + z*sizeX + y*sizeX*sizeZ]
     * @param paletteSize Number of entries in the block palette
     * @return LongArray for the BlockStates NBT field
     */
    static long[] packBlockStates(int[] blockStates, int paletteSize) {
        int bitsPerValue = calculateBitsPerValue(paletteSize)
        int totalBits = blockStates.length * bitsPerValue
        int numLongs = (totalBits + 63).intdiv(64)  // Ceiling division

        long[] packed = new long[numLongs]

        int bitIndex = 0
        for (int value : blockStates) {
            int longIndex = bitIndex.intdiv(64)
            int bitOffset = bitIndex % 64

            // Set bits in current long
            packed[longIndex] |= ((long) value) << bitOffset

            // Handle overflow to next long
            if (bitOffset + bitsPerValue > 64 && longIndex + 1 < numLongs) {
                int overflowBits = (bitOffset + bitsPerValue) - 64
                packed[longIndex + 1] |= ((long) value) >>> (bitsPerValue - overflowBits)
            }

            bitIndex += bitsPerValue
        }

        return packed
    }

    /**
     * Unpack block states from a LongArray.
     * Used for testing round-trip correctness.
     *
     * @param packed Packed LongArray
     * @param numBlocks Total number of blocks
     * @param paletteSize Number of entries in palette
     * @return Array of palette indices
     */
    static int[] unpackBlockStates(long[] packed, int numBlocks, int paletteSize) {
        int bitsPerValue = calculateBitsPerValue(paletteSize)
        int[] blockStates = new int[numBlocks]
        long mask = (1L << bitsPerValue) - 1

        int bitIndex = 0
        for (int i = 0; i < numBlocks; i++) {
            int longIndex = bitIndex.intdiv(64)
            int bitOffset = bitIndex % 64

            long value = (packed[longIndex] >>> bitOffset) & mask

            // Handle value spanning two longs
            if (bitOffset + bitsPerValue > 64 && longIndex + 1 < packed.length) {
                int bitsFromNext = (bitOffset + bitsPerValue) - 64
                long nextPart = (packed[longIndex + 1] & ((1L << bitsFromNext) - 1)) << (bitsPerValue - bitsFromNext)
                value |= nextPart
            }

            blockStates[i] = (int) value
            bitIndex += bitsPerValue
        }

        return blockStates
    }

    /**
     * Calculate bits per value for the given palette size.
     * Minimum is 2 bits (for palettes of size 1-4).
     *
     * @param paletteSize Number of entries in palette
     * @return Bits per value
     */
    static int calculateBitsPerValue(int paletteSize) {
        if (paletteSize <= 1) {
            return 2  // Minimum 2 bits
        }
        int bits = 32 - Integer.numberOfLeadingZeros(paletteSize - 1)
        return Math.max(2, bits)
    }

    /**
     * Calculate the 3D index for block state array.
     * Order: X -> Z -> Y (iterate X fastest, Y slowest)
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @param sizeX Size in X dimension
     * @param sizeZ Size in Z dimension
     * @return Index into block state array
     */
    static int calculate3DIndex(int x, int y, int z, int sizeX, int sizeZ) {
        return x + z * sizeX + y * sizeX * sizeZ
    }

    // ========== Palette Entry Creation ==========

    /**
     * Create a simple palette entry for a block.
     *
     * @param blockName Full block name (e.g., 'minecraft:air')
     * @return Palette entry CompoundTag
     */
    static CompoundTag createPaletteEntry(String blockName) {
        CompoundTag entry = new CompoundTag()
        entry.putString('Name', blockName)
        return entry
    }

    /**
     * Create a command block palette entry with facing direction.
     *
     * @param isChain true for chain command block, false for impulse
     * @return Palette entry CompoundTag
     */
    static CompoundTag createCommandBlockPaletteEntry(boolean isChain) {
        CompoundTag entry = new CompoundTag()
        entry.putString('Name', isChain ? BLOCK_CHAIN_COMMAND_BLOCK : BLOCK_COMMAND_BLOCK)

        // Properties for facing east (chain direction)
        CompoundTag properties = new CompoundTag()
        properties.putString('facing', 'east')
        properties.putString('conditional', 'false')
        entry.put('Properties', properties)

        return entry
    }

    // ========== Sign Tile Entity Creation ==========

    /**
     * Create a sign tile entity with text and click event.
     *
     * @param x X coordinate in schematic
     * @param y Y coordinate in schematic
     * @param z Z coordinate in schematic
     * @param lines List of 4 sign lines
     * @param origX Original world X coordinate (for clickEvent)
     * @param origY Original world Y coordinate (for clickEvent)
     * @param origZ Original world Z coordinate (for clickEvent)
     * @return Sign tile entity CompoundTag
     */
    static CompoundTag createSignTileEntity(int x, int y, int z, List<String> lines,
                                            Integer origX, Integer origY, Integer origZ) {
        CompoundTag entity = new CompoundTag()

        // Coordinates at root level (Litematica format)
        entity.putInt('x', x)
        entity.putInt('y', y)
        entity.putInt('z', z)
        entity.putString('id', 'minecraft:sign')

        // Front text with clickable first line
        CompoundTag frontText = new CompoundTag()
        ListTag<StringTag> messages = new ListTag<>(StringTag)

        (0..3).each { int i ->
            String line = (i < lines.size()) ? (lines[i] ?: '') : ''
            String escaped = escapeJsonString(line)

            String json
            if (i == 0 && origX != null && origY != null && origZ != null) {
                // First line: clickable to show coordinates, then teleport
                String tellrawCmd = "/tellraw @s {\\\"text\\\":\\\"Sign from (${origX} ${origY} ${origZ})\\\",\\\"color\\\":\\\"gray\\\",\\\"clickEvent\\\":{\\\"action\\\":\\\"run_command\\\",\\\"value\\\":\\\"/tp @s ${origX} ${origY} ${origZ}\\\"}}"
                json = "{\"text\":\"${escaped}\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"${tellrawCmd}\"}}"
            } else {
                json = "{\"text\":\"${escaped}\"}"
            }
            messages.add(new StringTag(json))
        }

        frontText.put('messages', messages)
        frontText.putString('color', 'white')
        frontText.putByte('has_glowing_text', (byte) 0)

        // Back text (empty)
        CompoundTag backText = new CompoundTag()
        ListTag<StringTag> backMessages = new ListTag<>(StringTag)
        (0..3).each { backMessages.add(new StringTag('{"text":""}')) }
        backText.put('messages', backMessages)
        backText.putString('color', 'white')
        backText.putByte('has_glowing_text', (byte) 0)

        entity.put('front_text', frontText)
        entity.put('back_text', backText)
        entity.putByte('is_waxed', (byte) 0)

        return entity
    }

    // ========== Command Block Tile Entity Creation ==========

    /**
     * Create an impulse command block tile entity (first in chain).
     * Needs redstone/button to activate.
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @param command The command to execute
     * @return Command block tile entity CompoundTag
     */
    static CompoundTag createImpulseCommandBlock(int x, int y, int z, String command) {
        CompoundTag entity = new CompoundTag()

        entity.putInt('x', x)
        entity.putInt('y', y)
        entity.putInt('z', z)
        entity.putString('id', 'minecraft:command_block')
        entity.putString('Command', command)
        entity.putByte('auto', (byte) 0)  // Needs redstone
        entity.putByte('powered', (byte) 0)
        entity.putByte('conditionMet', (byte) 0)
        entity.putInt('SuccessCount', 0)
        entity.putString('CustomName', '{"text":"Book Dispenser - Click to Start"}')
        entity.putByte('TrackOutput', (byte) 1)
        entity.putString('LastOutput', '')
        entity.putByte('UpdateLastExecution', (byte) 1)
        entity.putLong('LastExecution', 0L)

        return entity
    }

    /**
     * Create a chain command block tile entity (auto-executing).
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @param command The command to execute
     * @return Command block tile entity CompoundTag
     */
    static CompoundTag createChainCommandBlock(int x, int y, int z, String command) {
        CompoundTag entity = new CompoundTag()

        entity.putInt('x', x)
        entity.putInt('y', y)
        entity.putInt('z', z)
        entity.putString('id', 'minecraft:command_block')
        entity.putString('Command', command)
        entity.putByte('auto', (byte) 1)  // Always active
        entity.putByte('powered', (byte) 0)
        entity.putByte('conditionMet', (byte) 0)
        entity.putInt('SuccessCount', 0)
        entity.putString('CustomName', '')
        entity.putByte('TrackOutput', (byte) 1)
        entity.putString('LastOutput', '')
        entity.putByte('UpdateLastExecution', (byte) 1)
        entity.putLong('LastExecution', 0L)

        return entity
    }

    // ========== Helper Methods ==========

    /**
     * Escape a string for use in JSON.
     *
     * @param str The string to escape
     * @return Escaped string
     */
    static String escapeJsonString(String str) {
        if (!str) {
            return ''
        }
        return str
            .replace('\\', '\\\\')
            .replace('"', '\\"')
            .replace('\n', '\\n')
            .replace('\r', '\\r')
            .replace('\t', '\\t')
    }

    /**
     * Write NBT to file with GZip compression.
     *
     * @param root The root CompoundTag
     * @param outputFile The output file
     */
    static void writeCompressedNBT(CompoundTag root, File outputFile) {
        try {
            // Ensure parent directory exists
            outputFile.parentFile?.mkdirs()

            // Write with GZip compression (default for Litematica)
            NBTUtil.write(new NamedTag('', root), outputFile, true)
        } catch (IOException e) {
            LOGGER.error("Failed to write Litematica file: ${outputFile.absolutePath}", e)
            throw e
        }
    }

}

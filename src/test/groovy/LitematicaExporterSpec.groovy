import net.querz.nbt.io.NBTUtil
import net.querz.nbt.io.NamedTag
import net.querz.nbt.tag.CompoundTag
import net.querz.nbt.tag.ListTag
import net.querz.nbt.tag.StringTag
import net.querz.nbt.tag.LongArrayTag
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.GZIPInputStream

/**
 * Unit and integration tests for LitematicaExporter.
 *
 * Unit tests cover:
 * - Bit packing algorithm correctness
 * - 3D index calculation
 * - Sign tile entity creation
 * - Command block tile entity creation
 * - Litematica root structure creation
 * - Region creation
 * - Palette entry creation
 * - JSON escaping
 *
 * Integration tests cover:
 * - Full sign export workflow
 * - Full book command export workflow
 * - File structure validation
 * - GZip compression verification
 * - Edge cases (special characters, overflow, empty data)
 */
class LitematicaExporterSpec extends Specification {

    // Test directory for integration tests
    Path tempDir
    File tempFile

    void setup() {
        // Create temporary directory for test output files
        Path projectRoot = Paths.get(System.getProperty('user.dir'))
        tempDir = projectRoot.resolve('build').resolve('test-litematica')
        Files.createDirectories(tempDir)
        println "Litematica test output directory: ${tempDir.toAbsolutePath()}"
    }

    void cleanup() {
        // Clean up test files
        tempFile?.delete()
    }

    // ========== 1. Bit Packing Algorithm Tests (Unit Tests) ==========

    def "calculateBitsPerValue returns minimum 2 bits for palette sizes 1-4"() {
        expect:
        LitematicaExporter.calculateBitsPerValue(paletteSize) == expectedBits

        where:
        paletteSize | expectedBits
        1           | 2
        2           | 2
        3           | 2
        4           | 2
    }

    def "calculateBitsPerValue returns 3 bits for palette sizes 5-8"() {
        expect:
        LitematicaExporter.calculateBitsPerValue(paletteSize) == 3

        where:
        paletteSize << [5, 6, 7, 8]
    }

    def "calculateBitsPerValue returns 4 bits for palette sizes 9-16"() {
        expect:
        LitematicaExporter.calculateBitsPerValue(paletteSize) == 4

        where:
        paletteSize << [9, 10, 11, 12, 13, 14, 15, 16]
    }

    def "calculateBitsPerValue scales correctly for larger palettes"() {
        expect:
        LitematicaExporter.calculateBitsPerValue(paletteSize) == expectedBits

        where:
        paletteSize | expectedBits
        17          | 5
        32          | 5
        33          | 6
        64          | 6
        65          | 7
        128         | 7
        129         | 8
        256         | 8
        257         | 9
        512         | 9
    }

    def "packBlockStates produces correct output for simple 2-block case with palette size 2"() {
        given: "Two blocks: air (0), oak_sign (1)"
        int[] blockStates = [0, 1] as int[]
        int paletteSize = 2

        when: "Pack block states"
        long[] packed = LitematicaExporter.packBlockStates(blockStates, paletteSize)

        then: "Should use 2 bits per value"
        LitematicaExporter.calculateBitsPerValue(paletteSize) == 2

        and: "Total bits = 2 blocks * 2 bits = 4 bits, fits in 1 long"
        packed.length == 1

        and: "First value (0) at bits 0-1, second value (1) at bits 2-3"
        // Binary: 0b0100 = 0x04 (value 1 at bits 2-3, value 0 at bits 0-1)
        packed[0] == 0b0100L
    }

    def "packBlockStates handles values spanning multiple longs correctly"() {
        given: "64 blocks of palette size 2 (2 bits each)"
        int[] blockStates = new int[64]
        blockStates[0] = 0  // First block: air
        blockStates[63] = 1 // Last block: sign
        int paletteSize = 2

        when: "Pack block states"
        long[] packed = LitematicaExporter.packBlockStates(blockStates, paletteSize)

        then: "Total bits = 64 blocks * 2 bits = 128 bits = 2 longs"
        packed.length == 2

        and: "First long contains blocks 0-31 (64 bits / 2 bits per block)"
        // Block 0 at bits 0-1 should be 0
        (packed[0] & 0b11) == 0

        and: "Last block (63) at bits 62-63 of second long should be 1"
        // Block 63 is at index 63 * 2 = 126 bits
        // 126 bits = 1 full long (64 bits) + 62 bits in second long
        (packed[1] >>> 62) == 1
    }

    def "packBlockStates and unpackBlockStates round-trip correctly for various inputs"() {
        given: "Block states with different patterns"
        int[] original = blockStates as int[]
        int paletteSize = palette

        when: "Pack and unpack"
        long[] packed = LitematicaExporter.packBlockStates(original, paletteSize)
        int[] unpacked = LitematicaExporter.unpackBlockStates(packed, original.length, paletteSize)

        then: "Unpacked should match original"
        unpacked == original

        where:
        blockStates                      | palette
        [0, 1, 0, 1, 0, 1]              | 2      // Simple alternating
        [0, 1, 2, 3, 0, 1, 2, 3]        | 4      // 4-entry palette (2 bits)
        [0, 1, 2, 3, 4, 5, 6, 7]        | 8      // 8-entry palette (3 bits)
        [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]  | 16     // Large palette (4 bits)
        [0] * 100                        | 1      // All same value
        [1] * 100                        | 2      // All non-zero
        (0..99).toList()                 | 100    // Sequential values (7 bits)
    }

    // ========== 2. 3D Index Calculation Tests ==========

    def "calculate3DIndex follows X→Z→Y order (X changes fastest)"() {
        given: "Size: 4x2x3 (sizeX=4, sizeY=2, sizeZ=3)"
        int sizeX = 4
        int sizeY = 2
        int sizeZ = 3

        expect: "Index calculation follows X→Z→Y ordering"
        // Y=0, Z=0: X varies from 0-3 → indices 0-3
        LitematicaExporter.calculate3DIndex(0, 0, 0, sizeX, sizeZ) == 0
        LitematicaExporter.calculate3DIndex(1, 0, 0, sizeX, sizeZ) == 1
        LitematicaExporter.calculate3DIndex(2, 0, 0, sizeX, sizeZ) == 2
        LitematicaExporter.calculate3DIndex(3, 0, 0, sizeX, sizeZ) == 3

        // Y=0, Z=1: X varies from 0-3 → indices 4-7
        LitematicaExporter.calculate3DIndex(0, 0, 1, sizeX, sizeZ) == 4
        LitematicaExporter.calculate3DIndex(1, 0, 1, sizeX, sizeZ) == 5

        // Y=1, Z=0: X varies from 0-3 → indices 12-15
        LitematicaExporter.calculate3DIndex(0, 1, 0, sizeX, sizeZ) == 12
        LitematicaExporter.calculate3DIndex(3, 1, 0, sizeX, sizeZ) == 15
    }

    def "calculate3DIndex returns correct values for corner cases"() {
        expect:
        LitematicaExporter.calculate3DIndex(x, y, z, sizeX, sizeZ) == expectedIndex

        where:
        x | y | z | sizeX | sizeZ | expectedIndex
        0 | 0 | 0 | 1     | 1     | 0              // Minimum case (1x1x1)
        0 | 0 | 0 | 10    | 10    | 0              // Origin
        9 | 0 | 0 | 10    | 10    | 9              // Max X at Y=0, Z=0
        0 | 0 | 9 | 10    | 10    | 90             // Max Z at Y=0 (0 + 9*10)
        0 | 9 | 0 | 10    | 10    | 900            // Max Y at Z=0 (0 + 0 + 9*10*10)
        9 | 9 | 9 | 10    | 10    | 999            // Max corner (9 + 9*10 + 9*10*10)
    }

    def "calculate3DIndex matches Litematica specification"() {
        given: "Formula: x + z * sizeX + y * sizeX * sizeZ"
        int x = 2, y = 3, z = 1
        int sizeX = 5, sizeZ = 4

        when:
        int index = LitematicaExporter.calculate3DIndex(x, y, z, sizeX, sizeZ)

        then: "Matches manual calculation"
        index == (x + z * sizeX + y * sizeX * sizeZ)
        index == (2 + 1 * 5 + 3 * 5 * 4)
        index == (2 + 5 + 60)
        index == 67
    }

    // ========== 3. Sign Tile Entity Tests ==========

    def "createSignTileEntity creates valid NBT with front_text compound"() {
        given: "Sign at coordinates (1, 2, 3) with lines"
        List<String> lines = ['Line 1', 'Line 2', 'Line 3', 'Line 4']

        when:
        CompoundTag entity = LitematicaExporter.createSignTileEntity(1, 2, 3, lines, null, null, null)

        then: "Has correct coordinates"
        entity.getInt('x') == 1
        entity.getInt('y') == 2
        entity.getInt('z') == 3

        and: "Has correct ID"
        entity.getString('id') == 'minecraft:sign'

        and: "Has front_text compound"
        entity.containsKey('front_text')
        CompoundTag frontText = entity.getCompoundTag('front_text')
        frontText != null

        and: "Has back_text compound"
        entity.containsKey('back_text')
    }

    def "createSignTileEntity includes messages ListTag with 4 entries"() {
        given: "Sign with 4 lines"
        List<String> lines = ['A', 'B', 'C', 'D']

        when:
        CompoundTag entity = LitematicaExporter.createSignTileEntity(0, 0, 0, lines, null, null, null)
        CompoundTag frontText = entity.getCompoundTag('front_text')
        ListTag<StringTag> messages = frontText.getListTag('messages')

        then: "Has exactly 4 messages"
        messages.size() == 4

        and: "Messages contain the text"
        messages[0].value.contains('A')
        messages[1].value.contains('B')
        messages[2].value.contains('C')
        messages[3].value.contains('D')

        and: "Messages are valid JSON"
        messages.every { it.value.startsWith('{') && it.value.endsWith('}') }
    }

    def "createSignTileEntity adds clickEvent when original coordinates provided"() {
        given: "Sign with original coordinates"
        List<String> lines = ['Click me', '', '', '']
        Integer origX = 100, origY = 64, origZ = -200

        when:
        CompoundTag entity = LitematicaExporter.createSignTileEntity(0, 0, 0, lines, origX, origY, origZ)
        CompoundTag frontText = entity.getCompoundTag('front_text')
        ListTag<StringTag> messages = frontText.getListTag('messages')
        String firstLine = messages[0].value

        then: "First line contains clickEvent"
        firstLine.contains('clickEvent')
        firstLine.contains('run_command')

        and: "Click event contains tellraw with coordinates"
        firstLine.contains('tellraw')
        firstLine.contains("${origX} ${origY} ${origZ}")

        and: "Contains teleport command"
        firstLine.contains("/tp @s ${origX} ${origY} ${origZ}")

        and: "Other lines do not have clickEvent"
        !messages[1].value.contains('clickEvent')
        !messages[2].value.contains('clickEvent')
        !messages[3].value.contains('clickEvent')
    }

    def "createSignTileEntity escapes special characters (quotes, backslashes, newlines)"() {
        given: "Sign with special characters"
        List<String> lines = [
            'Quote: "Hello"',
            'Backslash: \\path\\file',
            'Newline: \nNext',
            'Tab: \tIndented'
        ]

        when:
        CompoundTag entity = LitematicaExporter.createSignTileEntity(0, 0, 0, lines, null, null, null)
        CompoundTag frontText = entity.getCompoundTag('front_text')
        ListTag<StringTag> messages = frontText.getListTag('messages')

        then: "Quotes are escaped"
        messages[0].value.contains('\\"')

        and: "Backslashes are escaped"
        messages[1].value.contains('\\\\')

        and: "Newlines are escaped"
        messages[2].value.contains('\\n')

        and: "Tabs are escaped"
        messages[3].value.contains('\\t')
    }

    def "createSignTileEntity handles empty lines correctly"() {
        given: "Sign with empty and null lines"
        List<String> lines = ['First', '', null, 'Fourth']

        when:
        CompoundTag entity = LitematicaExporter.createSignTileEntity(0, 0, 0, lines, null, null, null)
        CompoundTag frontText = entity.getCompoundTag('front_text')
        ListTag<StringTag> messages = frontText.getListTag('messages')

        then: "All 4 messages exist"
        messages.size() == 4

        and: "Empty/null lines become empty JSON text"
        messages[0].value.contains('First')
        messages[1].value == '{"text":""}'
        messages[2].value == '{"text":""}'
        messages[3].value.contains('Fourth')
    }

    def "createSignTileEntity handles null coordinates gracefully"() {
        given: "Sign with null original coordinates"
        List<String> lines = ['Line 1', 'Line 2', 'Line 3', 'Line 4']

        when:
        CompoundTag entity = LitematicaExporter.createSignTileEntity(5, 6, 7, lines, null, null, null)
        CompoundTag frontText = entity.getCompoundTag('front_text')
        ListTag<StringTag> messages = frontText.getListTag('messages')

        then: "Entity is created successfully"
        entity != null

        and: "No clickEvent in any line"
        messages.every { !it.value.contains('clickEvent') }

        and: "All lines are simple JSON text"
        messages[0].value == '{"text":"Line 1"}'
        messages[1].value == '{"text":"Line 2"}'
    }

    // ========== 4. Command Block Tile Entity Tests ==========

    def "createImpulseCommandBlock has auto:0b (needs redstone)"() {
        given: "Impulse command block with command"
        String command = 'give @p diamond 64'

        when:
        CompoundTag entity = LitematicaExporter.createImpulseCommandBlock(10, 20, 30, command)

        then: "Has correct coordinates"
        entity.getInt('x') == 10
        entity.getInt('y') == 20
        entity.getInt('z') == 30

        and: "Has correct ID"
        entity.getString('id') == 'minecraft:command_block'

        and: "Has auto=0 (needs redstone)"
        entity.getByte('auto') == (byte) 0

        and: "Command is preserved"
        entity.getString('Command') == command

        and: "Has CustomName"
        entity.getString('CustomName').contains('Book Dispenser')
    }

    def "createChainCommandBlock has auto:1b (always active)"() {
        given: "Chain command block with command"
        String command = 'say Hello World'

        when:
        CompoundTag entity = LitematicaExporter.createChainCommandBlock(5, 10, 15, command)

        then: "Has correct coordinates"
        entity.getInt('x') == 5
        entity.getInt('y') == 10
        entity.getInt('z') == 15

        and: "Has correct ID"
        entity.getString('id') == 'minecraft:command_block'

        and: "Has auto=1 (always active)"
        entity.getByte('auto') == (byte) 1

        and: "Command is preserved"
        entity.getString('Command') == command

        and: "Has empty CustomName"
        entity.getString('CustomName') == ''
    }

    def "Command string is preserved correctly in both types"() {
        given: "Complex command with special characters"
        String complexCommand = 'give @p written_book[written_book_content={title:"Test",author:"Me",pages:["Page 1"]}]'

        when:
        CompoundTag impulse = LitematicaExporter.createImpulseCommandBlock(0, 0, 0, complexCommand)
        CompoundTag chain = LitematicaExporter.createChainCommandBlock(0, 0, 0, complexCommand)

        then: "Both preserve the exact command"
        impulse.getString('Command') == complexCommand
        chain.getString('Command') == complexCommand
    }

    def "CustomName is set for impulse block"() {
        when:
        CompoundTag impulse = LitematicaExporter.createImpulseCommandBlock(0, 0, 0, 'test')

        then:
        impulse.getString('CustomName') == '{"text":"Book Dispenser - Click to Start"}'
    }

    // ========== 5. Litematica Root Structure Tests ==========

    def "createLitematicaRoot creates valid root with Version=6"() {
        given: "Schematic parameters"
        String name = 'Test Schematic'
        String author = 'TestAuthor'
        int regionCount = 1
        int totalVolume = 100
        int[] enclosingSize = [10, 10, 1] as int[]

        when:
        CompoundTag root = LitematicaExporter.createLitematicaRoot(name, author, regionCount, totalVolume, enclosingSize)

        then: "Has Version=6"
        root.getInt('Version') == LitematicaExporter.LITEMATICA_VERSION
        root.getInt('Version') == 6
    }

    def "createLitematicaRoot includes MinecraftDataVersion"() {
        when:
        CompoundTag root = LitematicaExporter.createLitematicaRoot('Test', 'Author', 1, 100, [10, 10, 1] as int[])

        then: "Has MinecraftDataVersion"
        root.containsKey('MinecraftDataVersion')
        root.getInt('MinecraftDataVersion') == LitematicaExporter.MINECRAFT_DATA_VERSION
        root.getInt('MinecraftDataVersion') == 3837  // Minecraft 1.21
    }

    def "createLitematicaRoot has Metadata compound with required fields"() {
        given: "Schematic parameters"
        String name = 'My Schematic'
        String author = 'Builder123'
        int regionCount = 2
        int totalVolume = 250
        int[] enclosingSize = [5, 10, 5] as int[]

        when:
        CompoundTag root = LitematicaExporter.createLitematicaRoot(name, author, regionCount, totalVolume, enclosingSize)
        CompoundTag metadata = root.getCompoundTag('Metadata')

        then: "Metadata exists"
        metadata != null

        and: "Has Name"
        metadata.getString('Name') == name

        and: "Has Author"
        metadata.getString('Author') == author

        and: "Has TimeCreated and TimeModified"
        metadata.getLong('TimeCreated') > 0
        metadata.getLong('TimeModified') > 0

        and: "Has RegionCount"
        metadata.getInt('RegionCount') == regionCount

        and: "Has TotalVolume"
        metadata.getInt('TotalVolume') == totalVolume

        and: "Has TotalBlocks"
        metadata.getInt('TotalBlocks') == totalVolume

        and: "Has EnclosingSize"
        CompoundTag enclosingSizeTag = metadata.getCompoundTag('EnclosingSize')
        enclosingSizeTag.getInt('x') == 5
        enclosingSizeTag.getInt('y') == 10
        enclosingSizeTag.getInt('z') == 5
    }

    def "createLitematicaRoot has empty Regions compound"() {
        when:
        CompoundTag root = LitematicaExporter.createLitematicaRoot('Test', 'Author', 1, 100, [10, 10, 1] as int[])

        then: "Has Regions compound"
        root.containsKey('Regions')

        and: "Regions is a CompoundTag"
        root.get('Regions') instanceof CompoundTag

        and: "Regions is empty (to be filled by caller)"
        root.getCompoundTag('Regions').size() == 0
    }

    // ========== 6. Region Creation Tests ==========

    def "createRegion includes Position compound"() {
        given: "Region parameters"
        int[] position = [5, 10, 15] as int[]
        int[] size = [3, 3, 3] as int[]
        List<CompoundTag> palette = [LitematicaExporter.createPaletteEntry('minecraft:air')]
        long[] blockStates = [0L] as long[]
        List<CompoundTag> tileEntities = []

        when:
        CompoundTag region = LitematicaExporter.createRegion('TestRegion', position, size, palette, blockStates, tileEntities)
        CompoundTag posTag = region.getCompoundTag('Position')

        then: "Position exists and has correct values"
        posTag != null
        posTag.getInt('x') == 5
        posTag.getInt('y') == 10
        posTag.getInt('z') == 15
    }

    def "createRegion includes Size compound"() {
        given: "Region parameters"
        int[] position = [0, 0, 0] as int[]
        int[] size = [20, 15, 10] as int[]
        List<CompoundTag> palette = [LitematicaExporter.createPaletteEntry('minecraft:air')]
        long[] blockStates = [0L] as long[]
        List<CompoundTag> tileEntities = []

        when:
        CompoundTag region = LitematicaExporter.createRegion('TestRegion', position, size, palette, blockStates, tileEntities)
        CompoundTag sizeTag = region.getCompoundTag('Size')

        then: "Size exists and has correct values"
        sizeTag != null
        sizeTag.getInt('x') == 20
        sizeTag.getInt('y') == 15
        sizeTag.getInt('z') == 10
    }

    def "createRegion has BlockStatePalette ListTag"() {
        given: "Region with multiple palette entries"
        int[] position = [0, 0, 0] as int[]
        int[] size = [2, 2, 2] as int[]
        List<CompoundTag> palette = [
            LitematicaExporter.createPaletteEntry('minecraft:air'),
            LitematicaExporter.createPaletteEntry('minecraft:stone'),
            LitematicaExporter.createPaletteEntry('minecraft:dirt')
        ]
        long[] blockStates = [0L] as long[]
        List<CompoundTag> tileEntities = []

        when:
        CompoundTag region = LitematicaExporter.createRegion('TestRegion', position, size, palette, blockStates, tileEntities)
        ListTag<CompoundTag> paletteTag = region.getListTag('BlockStatePalette')

        then: "Palette exists and has correct size"
        paletteTag != null
        paletteTag.size() == 3

        and: "Palette entries are CompoundTags"
        paletteTag.every { it instanceof CompoundTag }
    }

    def "createRegion has BlockStates LongArrayTag"() {
        given: "Region with block states"
        int[] position = [0, 0, 0] as int[]
        int[] size = [4, 4, 4] as int[]
        List<CompoundTag> palette = [LitematicaExporter.createPaletteEntry('minecraft:air')]
        long[] blockStates = [0x123456789ABCDEFL, 0xFEDCBA9876543210L] as long[]
        List<CompoundTag> tileEntities = []

        when:
        CompoundTag region = LitematicaExporter.createRegion('TestRegion', position, size, palette, blockStates, tileEntities)

        then: "BlockStates exists"
        region.containsKey('BlockStates')

        and: "Is a LongArrayTag"
        region.get('BlockStates') instanceof net.querz.nbt.tag.LongArrayTag

        and: "Contains the correct values"
        def longArray = region.getLongArray('BlockStates')
        longArray.length == 2
        longArray[0] == 0x123456789ABCDEFL
        longArray[1] == 0xFEDCBA9876543210L
    }

    def "createRegion has TileEntities ListTag"() {
        given: "Region with tile entities"
        int[] position = [0, 0, 0] as int[]
        int[] size = [2, 2, 2] as int[]
        List<CompoundTag> palette = [LitematicaExporter.createPaletteEntry('minecraft:air')]
        long[] blockStates = [0L] as long[]
        List<CompoundTag> tileEntities = [
            LitematicaExporter.createSignTileEntity(0, 0, 0, ['A', 'B', 'C', 'D'], null, null, null),
            LitematicaExporter.createSignTileEntity(1, 0, 0, ['E', 'F', 'G', 'H'], null, null, null)
        ]

        when:
        CompoundTag region = LitematicaExporter.createRegion('TestRegion', position, size, palette, blockStates, tileEntities)
        ListTag<CompoundTag> tileEntitiesTag = region.getListTag('TileEntities')

        then: "TileEntities exists and has correct size"
        tileEntitiesTag != null
        tileEntitiesTag.size() == 2

        and: "Entries are CompoundTags"
        tileEntitiesTag.every { it instanceof CompoundTag }
    }

    def "createRegion has empty Entities ListTag"() {
        given: "Region parameters"
        int[] position = [0, 0, 0] as int[]
        int[] size = [1, 1, 1] as int[]
        List<CompoundTag> palette = [LitematicaExporter.createPaletteEntry('minecraft:air')]
        long[] blockStates = [0L] as long[]
        List<CompoundTag> tileEntities = []

        when:
        CompoundTag region = LitematicaExporter.createRegion('TestRegion', position, size, palette, blockStates, tileEntities)

        then: "Entities exists and is empty"
        region.containsKey('Entities')
        ListTag<CompoundTag> entities = region.getListTag('Entities')
        entities.size() == 0
    }

    // ========== 7. Palette Entry Tests ==========

    def "createPaletteEntry creates correct structure for blocks"() {
        when: "Create palette entry for various blocks"
        CompoundTag air = LitematicaExporter.createPaletteEntry('minecraft:air')
        CompoundTag stone = LitematicaExporter.createPaletteEntry('minecraft:stone')
        CompoundTag sign = LitematicaExporter.createPaletteEntry('minecraft:oak_sign')

        then: "All have Name field"
        air.getString('Name') == 'minecraft:air'
        stone.getString('Name') == 'minecraft:stone'
        sign.getString('Name') == 'minecraft:oak_sign'

        and: "Simple entries have no Properties"
        !air.containsKey('Properties')
        !stone.containsKey('Properties')
        !sign.containsKey('Properties')
    }

    def "createCommandBlockPaletteEntry includes facing:east property"() {
        when: "Create command block palette entries"
        CompoundTag impulse = LitematicaExporter.createCommandBlockPaletteEntry(false)
        CompoundTag chain = LitematicaExporter.createCommandBlockPaletteEntry(true)

        then: "Both have Properties compound"
        impulse.containsKey('Properties')
        chain.containsKey('Properties')

        and: "Properties include facing=east"
        impulse.getCompoundTag('Properties').getString('facing') == 'east'
        chain.getCompoundTag('Properties').getString('facing') == 'east'
    }

    def "createCommandBlockPaletteEntry includes conditional:false property"() {
        when: "Create command block palette entries"
        CompoundTag impulse = LitematicaExporter.createCommandBlockPaletteEntry(false)
        CompoundTag chain = LitematicaExporter.createCommandBlockPaletteEntry(true)

        then: "Properties include conditional=false"
        impulse.getCompoundTag('Properties').getString('conditional') == 'false'
        chain.getCompoundTag('Properties').getString('conditional') == 'false'
    }

    def "createCommandBlockPaletteEntry distinguishes impulse vs chain"() {
        when:
        CompoundTag impulse = LitematicaExporter.createCommandBlockPaletteEntry(false)
        CompoundTag chain = LitematicaExporter.createCommandBlockPaletteEntry(true)

        then: "Impulse has correct name"
        impulse.getString('Name') == 'minecraft:command_block'

        and: "Chain has correct name"
        chain.getString('Name') == 'minecraft:chain_command_block'
    }

    // ========== 8. JSON Escaping Tests ==========

    def "escapeJsonString escapes backslashes"() {
        when:
        String result = LitematicaExporter.escapeJsonString('C:\\Users\\path')

        then:
        result == 'C:\\\\Users\\\\path'
    }

    def "escapeJsonString escapes quotes"() {
        when:
        String result = LitematicaExporter.escapeJsonString('He said "Hello"')

        then:
        result == 'He said \\"Hello\\"'
    }

    def "escapeJsonString escapes newlines and tabs"() {
        when:
        String withNewline = LitematicaExporter.escapeJsonString('Line1\nLine2')
        String withTab = LitematicaExporter.escapeJsonString('A\tB')
        String withCarriageReturn = LitematicaExporter.escapeJsonString('X\rY')

        then:
        withNewline == 'Line1\\nLine2'
        withTab == 'A\\tB'
        withCarriageReturn == 'X\\rY'
    }

    def "escapeJsonString handles null/empty strings"() {
        expect:
        LitematicaExporter.escapeJsonString(input) == expected

        where:
        input | expected
        null  | ''
        ''    | ''
        '   ' | '   '  // Preserves spaces
    }

    def "escapeJsonString handles combined special characters"() {
        when:
        String complex = LitematicaExporter.escapeJsonString('Path: "C:\\tmp\\file"\nNext: \tvalue')

        then:
        complex == 'Path: \\"C:\\\\tmp\\\\file\\"\\nNext: \\tvalue'
    }

    // ========== 9. Sign Export Integration Tests ==========

    def "exportSigns creates a valid .litematic file when given sign data"() {
        given: 'a map of sign data'
        Map<String, Map<String, Object>> signsByHash = [
            'sign1': [
                x: 0, z: 0,
                lines: ['Line 1', 'Line 2', 'Line 3', 'Line 4'],
                originalX: 100, originalY: 64, originalZ: 200
            ],
            'sign2': [
                x: 1, z: 0,
                lines: ['Test', 'Sign', 'Two', ''],
                originalX: 150, originalY: 70, originalZ: 250
            ]
        ]
        tempFile = tempDir.resolve('test_signs.litematic').toFile()

        when: 'exporting signs'
        LitematicaExporter.exportSigns(signsByHash, tempFile)

        then: 'file is created'
        tempFile.exists()
        tempFile.length() > 0
    }

    def "exportSigns file can be read back with NBTUtil.read()"() {
        given: 'exported signs file'
        Map<String, Map<String, Object>> signsByHash = [
            'sign1': [
                x: 0, z: 0,
                lines: ['Test', '', '', ''],
                originalX: 0, originalY: 0, originalZ: 0
            ]
        ]
        tempFile = tempDir.resolve('test_read_signs.litematic').toFile()
        LitematicaExporter.exportSigns(signsByHash, tempFile)

        when: 'reading file with NBTUtil'
        NamedTag namedTag = NBTUtil.read(tempFile, true) // true = compressed
        CompoundTag root = namedTag.tag as CompoundTag

        then: 'root tag is valid CompoundTag'
        root != null
        root instanceof CompoundTag
    }

    def "exportSigns root has Version=6 and MinecraftDataVersion"() {
        given: 'exported signs file'
        Map<String, Map<String, Object>> signsByHash = createMockSignData(1)
        tempFile = tempDir.resolve('test_version.litematic').toFile()
        LitematicaExporter.exportSigns(signsByHash, tempFile)

        when: 'reading root tags'
        CompoundTag root = readLitematicaFile(tempFile)

        then: 'Version and MinecraftDataVersion are correct'
        root.getInt('Version') == 6
        root.getInt('MinecraftDataVersion') == 3837
    }

    def "exportSigns Regions compound has 'Signs' region"() {
        given: 'exported signs file'
        Map<String, Map<String, Object>> signsByHash = createMockSignData(2)
        tempFile = tempDir.resolve('test_regions.litematic').toFile()
        LitematicaExporter.exportSigns(signsByHash, tempFile)

        when: 'reading regions'
        CompoundTag root = readLitematicaFile(tempFile)
        CompoundTag regions = root.getCompoundTag('Regions')

        then: 'Regions contains Signs region'
        regions != null
        regions.containsKey('Signs')
        regions.getCompoundTag('Signs') != null
    }

    def "exportSigns TileEntities count matches input sign count"() {
        given: 'exported signs file with known count'
        int signCount = 5
        Map<String, Map<String, Object>> signsByHash = createMockSignData(signCount)
        tempFile = tempDir.resolve('test_tile_entities.litematic').toFile()
        LitematicaExporter.exportSigns(signsByHash, tempFile)

        when: 'reading tile entities'
        CompoundTag root = readLitematicaFile(tempFile)
        CompoundTag signsRegion = root.getCompoundTag('Regions').getCompoundTag('Signs')
        ListTag tileEntities = signsRegion.getListTag('TileEntities')

        then: 'tile entity count matches sign count'
        tileEntities.size() == signCount
    }

    def "exportSigns each tile entity has correct front_text structure"() {
        given: 'exported signs file'
        Map<String, Map<String, Object>> signsByHash = [
            'sign1': [
                x: 0, z: 0,
                lines: ['First', 'Second', 'Third', 'Fourth'],
                originalX: 10, originalY: 20, originalZ: 30
            ]
        ]
        tempFile = tempDir.resolve('test_front_text.litematic').toFile()
        LitematicaExporter.exportSigns(signsByHash, tempFile)

        when: 'reading first tile entity'
        CompoundTag root = readLitematicaFile(tempFile)
        CompoundTag signsRegion = root.getCompoundTag('Regions').getCompoundTag('Signs')
        ListTag tileEntities = signsRegion.getListTag('TileEntities')
        CompoundTag firstSign = tileEntities.get(0) as CompoundTag

        then: 'front_text structure is correct'
        firstSign.containsKey('front_text')
        CompoundTag frontText = firstSign.getCompoundTag('front_text')
        frontText.containsKey('messages')
        ListTag messages = frontText.getListTag('messages')
        messages.size() == 4

        and: 'first line contains clickEvent'
        String firstLine = (messages.get(0) as StringTag).value
        firstLine.contains('clickEvent')
        firstLine.contains('tellraw')
        firstLine.contains('10 20 30') // Original coordinates
    }

    def "exportSigns empty signsByHash produces no file"() {
        given: 'empty sign data'
        Map<String, Map<String, Object>> signsByHash = [:]
        tempFile = tempDir.resolve('test_empty_signs.litematic').toFile()

        when: 'exporting empty signs'
        LitematicaExporter.exportSigns(signsByHash, tempFile)

        then: 'file is not created'
        !tempFile.exists()
    }

    def "exportSigns handles special characters in sign text"() {
        given: 'signs with special characters'
        Map<String, Map<String, Object>> signsByHash = [
            'sign1': [
                x: 0, z: 0,
                lines: ['Quote: "test"', 'Backslash: \\', 'Unicode: 你好', 'Newline:\ntest'],
                originalX: 0, originalY: 0, originalZ: 0
            ]
        ]
        tempFile = tempDir.resolve('test_special_chars.litematic').toFile()

        when: 'exporting signs with special characters'
        LitematicaExporter.exportSigns(signsByHash, tempFile)

        then: 'file is created without errors'
        tempFile.exists()

        and: 'can be read back'
        CompoundTag root = readLitematicaFile(tempFile)
        CompoundTag signsRegion = root.getCompoundTag('Regions').getCompoundTag('Signs')
        ListTag tileEntities = signsRegion.getListTag('TileEntities')
        tileEntities.size() == 1

        and: 'special characters are properly escaped'
        CompoundTag firstSign = tileEntities.get(0) as CompoundTag
        CompoundTag frontText = firstSign.getCompoundTag('front_text')
        ListTag messages = frontText.getListTag('messages')
        String firstLine = (messages.get(0) as StringTag).value
        String secondLine = (messages.get(1) as StringTag).value
        firstLine.contains('\\"') // Escaped quote in first line
        secondLine.contains('\\\\') // Escaped backslash in second line
    }

    def "exportSigns handles signs with empty lines"() {
        given: 'signs with empty lines'
        Map<String, Map<String, Object>> signsByHash = [
            'sign1': [
                x: 0, z: 0,
                lines: ['First', '', '', 'Fourth'],
                originalX: 0, originalY: 0, originalZ: 0
            ]
        ]
        tempFile = tempDir.resolve('test_empty_lines.litematic').toFile()

        when: 'exporting signs with empty lines'
        LitematicaExporter.exportSigns(signsByHash, tempFile)

        then: 'file is created'
        tempFile.exists()

        and: 'empty lines are preserved'
        CompoundTag root = readLitematicaFile(tempFile)
        CompoundTag signsRegion = root.getCompoundTag('Regions').getCompoundTag('Signs')
        ListTag tileEntities = signsRegion.getListTag('TileEntities')
        CompoundTag firstSign = tileEntities.get(0) as CompoundTag
        CompoundTag frontText = firstSign.getCompoundTag('front_text')
        ListTag messages = frontText.getListTag('messages')
        messages.size() == 4
        (messages.get(1) as StringTag).value.contains('{"text":""}')
    }

    // ========== 10. Book Command Export Integration Tests ==========

    def "exportBookCommands creates a valid .litematic file"() {
        given: 'book data by author'
        Map<String, List<Map<String, Object>>> booksByAuthor = createMockBooksByAuthor(1, 3)
        tempFile = tempDir.resolve('test_books.litematic').toFile()

        when: 'exporting book commands'
        LitematicaExporter.exportBookCommands(booksByAuthor, tempFile)

        then: 'file is created'
        tempFile.exists()
        tempFile.length() > 0
    }

    def "exportBookCommands file can be read back"() {
        given: 'exported book commands file'
        Map<String, List<Map<String, Object>>> booksByAuthor = createMockBooksByAuthor(1, 1)
        tempFile = tempDir.resolve('test_read_books.litematic').toFile()
        LitematicaExporter.exportBookCommands(booksByAuthor, tempFile)

        when: 'reading file with NBTUtil'
        NamedTag namedTag = NBTUtil.read(tempFile, true)
        CompoundTag root = namedTag.tag as CompoundTag

        then: 'root tag is valid'
        root != null
        root instanceof CompoundTag
    }

    def "exportBookCommands command block chain is correctly structured"() {
        given: 'book data that generates multiple shulker boxes'
        Map<String, List<Map<String, Object>>> booksByAuthor = createMockBooksByAuthor(1, 50) // Will create 2 shulker boxes
        tempFile = tempDir.resolve('test_chain.litematic').toFile()
        LitematicaExporter.exportBookCommands(booksByAuthor, tempFile)

        when: 'reading command blocks'
        CompoundTag root = readLitematicaFile(tempFile)
        CompoundTag commandsRegion = root.getCompoundTag('Regions').getCompoundTag('Commands')
        ListTag tileEntities = commandsRegion.getListTag('TileEntities')

        then: 'at least one command block exists'
        tileEntities.size() >= 1

        and: 'block state palette contains command blocks'
        ListTag palette = commandsRegion.getListTag('BlockStatePalette')
        palette.size() >= 2 // air + at least command_block
        boolean hasCommandBlock = palette.any { tag ->
            CompoundTag ct = tag as CompoundTag
            String name = ct.getString('Name')
            name.contains('command_block')
        }
        hasCommandBlock
    }

    def "exportBookCommands first command block has auto:0b"() {
        given: 'exported book commands'
        Map<String, List<Map<String, Object>>> booksByAuthor = createMockBooksByAuthor(1, 5)
        tempFile = tempDir.resolve('test_auto_first.litematic').toFile()
        LitematicaExporter.exportBookCommands(booksByAuthor, tempFile)

        when: 'reading first command block'
        CompoundTag root = readLitematicaFile(tempFile)
        CompoundTag commandsRegion = root.getCompoundTag('Regions').getCompoundTag('Commands')
        ListTag tileEntities = commandsRegion.getListTag('TileEntities')
        CompoundTag firstCommandBlock = tileEntities.get(0) as CompoundTag

        then: 'first command block is impulse (auto:0b)'
        firstCommandBlock.getByte('auto') == (byte) 0
    }

    def "exportBookCommands remaining command blocks have auto:1b"() {
        given: 'exported book commands with multiple shulker boxes'
        Map<String, List<Map<String, Object>>> booksByAuthor = createMockBooksByAuthor(2, 30) // 2 authors, 2+ boxes each
        tempFile = tempDir.resolve('test_auto_chain.litematic').toFile()
        LitematicaExporter.exportBookCommands(booksByAuthor, tempFile)

        when: 'reading command blocks'
        CompoundTag root = readLitematicaFile(tempFile)
        CompoundTag commandsRegion = root.getCompoundTag('Regions').getCompoundTag('Commands')
        ListTag tileEntities = commandsRegion.getListTag('TileEntities')

        then: 'all command blocks after the first have auto:1b'
        tileEntities.size() >= 2
        (1..<tileEntities.size()).every { int i ->
            CompoundTag cmdBlock = tileEntities.get(i) as CompoundTag
            cmdBlock.getByte('auto') == (byte) 1
        }
    }

    def "exportBookCommands commands contain shulker box /give syntax"() {
        given: 'exported book commands'
        Map<String, List<Map<String, Object>>> booksByAuthor = createMockBooksByAuthor(1, 10)
        tempFile = tempDir.resolve('test_give_syntax.litematic').toFile()
        LitematicaExporter.exportBookCommands(booksByAuthor, tempFile)

        when: 'reading commands'
        CompoundTag root = readLitematicaFile(tempFile)
        CompoundTag commandsRegion = root.getCompoundTag('Regions').getCompoundTag('Commands')
        ListTag tileEntities = commandsRegion.getListTag('TileEntities')

        then: 'commands contain give and shulker_box'
        tileEntities.every { tag ->
            CompoundTag cmdBlock = tag as CompoundTag
            String command = cmdBlock.getString('Command')
            command.contains('give') && command.contains('shulker_box')
        }
    }

    def "exportBookCommands TileEntities count equals number of shulker boxes generated"() {
        given: 'book data with known shulker box count'
        int booksPerAuthor = 30
        int authorCount = 2
        int expectedShulkerBoxes = authorCount * ((booksPerAuthor + 26) / 27) // Ceiling division (27 per box)
        Map<String, List<Map<String, Object>>> booksByAuthor = createMockBooksByAuthor(authorCount, booksPerAuthor)
        tempFile = tempDir.resolve('test_count.litematic').toFile()
        LitematicaExporter.exportBookCommands(booksByAuthor, tempFile)

        when: 'reading tile entities'
        CompoundTag root = readLitematicaFile(tempFile)
        CompoundTag commandsRegion = root.getCompoundTag('Regions').getCompoundTag('Commands')
        ListTag tileEntities = commandsRegion.getListTag('TileEntities')

        then: 'tile entity count matches shulker box count'
        tileEntities.size() == expectedShulkerBoxes
    }

    def "exportBookCommands empty booksByAuthor produces no file"() {
        given: 'empty book data'
        Map<String, List<Map<String, Object>>> booksByAuthor = [:]
        tempFile = tempDir.resolve('test_empty_books.litematic').toFile()

        when: 'exporting empty books'
        LitematicaExporter.exportBookCommands(booksByAuthor, tempFile)

        then: 'file is not created'
        !tempFile.exists()
    }

    // ========== 11. Full File Structure Validation Tests ==========

    def "exportSigns file is GZip compressed (starts with 0x1F 0x8B magic bytes)"() {
        given: 'exported signs file'
        Map<String, Map<String, Object>> signsByHash = createMockSignData(1)
        tempFile = tempDir.resolve('test_gzip_signs.litematic').toFile()
        LitematicaExporter.exportSigns(signsByHash, tempFile)

        when: 'reading first two bytes'
        byte[] header = new byte[2]
        new FileInputStream(tempFile).withCloseable { stream ->
            stream.read(header)
        }

        then: 'file starts with GZip magic bytes'
        header[0] == (byte) 0x1F
        header[1] == (byte) 0x8B
    }

    def "exportBookCommands file is GZip compressed"() {
        given: 'exported book commands file'
        Map<String, List<Map<String, Object>>> booksByAuthor = createMockBooksByAuthor(1, 5)
        tempFile = tempDir.resolve('test_gzip_books.litematic').toFile()
        LitematicaExporter.exportBookCommands(booksByAuthor, tempFile)

        when: 'reading first two bytes'
        byte[] header = new byte[2]
        new FileInputStream(tempFile).withCloseable { stream ->
            stream.read(header)
        }

        then: 'file starts with GZip magic bytes'
        header[0] == (byte) 0x1F
        header[1] == (byte) 0x8B
    }

    def "exportSigns file can be decompressed with GZIPInputStream"() {
        given: 'exported signs file'
        Map<String, Map<String, Object>> signsByHash = createMockSignData(1)
        tempFile = tempDir.resolve('test_decompress.litematic').toFile()
        LitematicaExporter.exportSigns(signsByHash, tempFile)

        when: 'decompressing file'
        boolean canDecompress = false
        new FileInputStream(tempFile).withCloseable { fis ->
            new GZIPInputStream(fis).withCloseable { gis ->
                byte[] buffer = new byte[1024]
                int bytesRead = gis.read(buffer)
                canDecompress = (bytesRead > 0)
            }
        }

        then: 'file can be decompressed'
        canDecompress
    }

    def "exportSigns all required Litematica fields are present"() {
        given: 'exported signs file'
        Map<String, Map<String, Object>> signsByHash = createMockSignData(2)
        tempFile = tempDir.resolve('test_required_fields.litematic').toFile()
        LitematicaExporter.exportSigns(signsByHash, tempFile)

        when: 'reading structure'
        CompoundTag root = readLitematicaFile(tempFile)

        then: 'all required root fields are present'
        root.containsKey('Version')
        root.containsKey('MinecraftDataVersion')
        root.containsKey('Metadata')
        root.containsKey('Regions')

        and: 'metadata has required fields'
        CompoundTag metadata = root.getCompoundTag('Metadata')
        metadata.containsKey('Name')
        metadata.containsKey('Author')
        metadata.containsKey('TimeCreated')
        metadata.containsKey('TimeModified')
        metadata.containsKey('RegionCount')
        metadata.containsKey('TotalVolume')
        metadata.containsKey('EnclosingSize')

        and: 'region has required fields'
        CompoundTag signsRegion = root.getCompoundTag('Regions').getCompoundTag('Signs')
        signsRegion.containsKey('Position')
        signsRegion.containsKey('Size')
        signsRegion.containsKey('BlockStatePalette')
        signsRegion.containsKey('BlockStates')
        signsRegion.containsKey('TileEntities')
    }

    def "exportSigns BlockStates LongArray has correct length for volume"() {
        given: 'exported signs file with known dimensions'
        Map<String, Map<String, Object>> signsByHash = [
            'sign1': [x: 0, z: 0, lines: ['', '', '', ''], originalX: 0, originalY: 0, originalZ: 0],
            'sign2': [x: 1, z: 0, lines: ['', '', '', ''], originalX: 0, originalY: 0, originalZ: 0],
            'sign3': [x: 0, z: 1, lines: ['', '', '', ''], originalX: 0, originalY: 0, originalZ: 0]
        ]
        tempFile = tempDir.resolve('test_blockstates_length.litematic').toFile()
        LitematicaExporter.exportSigns(signsByHash, tempFile)

        when: 'reading block states'
        CompoundTag root = readLitematicaFile(tempFile)
        CompoundTag signsRegion = root.getCompoundTag('Regions').getCompoundTag('Signs')
        CompoundTag size = signsRegion.getCompoundTag('Size')
        int sizeX = size.getInt('x')
        int sizeY = size.getInt('y')
        int sizeZ = size.getInt('z')
        int totalVolume = sizeX * sizeY * sizeZ

        LongArrayTag blockStatesTag = signsRegion.getLongArrayTag('BlockStates')
        long[] blockStates = blockStatesTag.value

        ListTag palette = signsRegion.getListTag('BlockStatePalette')
        int paletteSize = palette.size()
        int bitsPerValue = LitematicaExporter.calculateBitsPerValue(paletteSize)
        int expectedLength = (totalVolume * bitsPerValue + 63) / 64 // Ceiling division

        then: 'block states array has correct length'
        blockStates.length == expectedLength
    }

    // ========== 12. Edge Cases Tests ==========

    def "exportBookCommands handles author with exactly 27 books (one full shulker)"() {
        given: 'author with exactly 27 books'
        Map<String, List<Map<String, Object>>> booksByAuthor = createMockBooksByAuthor(1, 27)
        tempFile = tempDir.resolve('test_exact_27.litematic').toFile()
        LitematicaExporter.exportBookCommands(booksByAuthor, tempFile)

        when: 'reading command blocks'
        CompoundTag root = readLitematicaFile(tempFile)
        CompoundTag commandsRegion = root.getCompoundTag('Regions').getCompoundTag('Commands')
        ListTag tileEntities = commandsRegion.getListTag('TileEntities')

        then: 'exactly one shulker box is generated'
        tileEntities.size() == 1
    }

    def "exportBookCommands handles author with 28 books (two shulkers)"() {
        given: 'author with 28 books'
        Map<String, List<Map<String, Object>>> booksByAuthor = createMockBooksByAuthor(1, 28)
        tempFile = tempDir.resolve('test_28_books.litematic').toFile()
        LitematicaExporter.exportBookCommands(booksByAuthor, tempFile)

        when: 'reading command blocks'
        CompoundTag root = readLitematicaFile(tempFile)
        CompoundTag commandsRegion = root.getCompoundTag('Regions').getCompoundTag('Commands')
        ListTag tileEntities = commandsRegion.getListTag('TileEntities')

        then: 'exactly two shulker boxes are generated'
        tileEntities.size() == 2
    }

    def "exportBookCommands handles author with 100+ books (multiple shulkers)"() {
        given: 'author with 100 books'
        int bookCount = 100
        int expectedShulkers = (bookCount + 26) / 27 // Ceiling division
        Map<String, List<Map<String, Object>>> booksByAuthor = createMockBooksByAuthor(1, bookCount)
        tempFile = tempDir.resolve('test_100_books.litematic').toFile()
        LitematicaExporter.exportBookCommands(booksByAuthor, tempFile)

        when: 'reading command blocks'
        CompoundTag root = readLitematicaFile(tempFile)
        CompoundTag commandsRegion = root.getCompoundTag('Regions').getCompoundTag('Commands')
        ListTag tileEntities = commandsRegion.getListTag('TileEntities')

        then: 'correct number of shulker boxes is generated'
        tileEntities.size() == expectedShulkers
    }

    def "exportBookCommands handles multiple authors with varying book counts"() {
        given: 'multiple authors with different book counts'
        Map<String, List<Map<String, Object>>> booksByAuthor = [
            'Author A': createMockBooks(5),
            'Author B': createMockBooks(30),
            'Author C': createMockBooks(1),
            'Author D': createMockBooks(54) // 2 shulker boxes
        ]
        int expectedTotal = 1 + 2 + 1 + 2 // 6 shulker boxes total
        tempFile = tempDir.resolve('test_multiple_authors.litematic').toFile()
        LitematicaExporter.exportBookCommands(booksByAuthor, tempFile)

        when: 'reading command blocks'
        CompoundTag root = readLitematicaFile(tempFile)
        CompoundTag commandsRegion = root.getCompoundTag('Regions').getCompoundTag('Commands')
        ListTag tileEntities = commandsRegion.getListTag('TileEntities')

        then: 'total shulker boxes matches expectation'
        tileEntities.size() == expectedTotal
    }

    def "exportBookCommands very long book commands fit in command blocks"() {
        given: 'books with very long pages'
        List<Map<String, Object>> books = createMockBooks(1)
        books[0].pages = createLongPages(10) // 10 pages with 100 characters each
        Map<String, List<Map<String, Object>>> booksByAuthor = ['Test Author': books]
        tempFile = tempDir.resolve('test_long_command.litematic').toFile()

        when: 'exporting long commands'
        LitematicaExporter.exportBookCommands(booksByAuthor, tempFile)

        then: 'file is created without errors'
        tempFile.exists()

        and: 'command is stored in command block'
        CompoundTag root = readLitematicaFile(tempFile)
        CompoundTag commandsRegion = root.getCompoundTag('Regions').getCompoundTag('Commands')
        ListTag tileEntities = commandsRegion.getListTag('TileEntities')
        CompoundTag firstCommandBlock = tileEntities.get(0) as CompoundTag
        String command = firstCommandBlock.getString('Command')
        command.length() > 0
    }

    // ========== Helper Methods ==========

    /**
     * Create mock sign data for testing.
     *
     * @param count Number of signs to create
     * @return Map of sign hash -> sign data
     */
    private Map<String, Map<String, Object>> createMockSignData(int count) {
        Map<String, Map<String, Object>> signs = [:]
        for (int i = 0; i < count; i++) {
            signs["sign${i}"] = [
                x: i % 10,
                z: i / 10,
                lines: ["Line ${i}A", "Line ${i}B", "Line ${i}C", "Line ${i}D"],
                originalX: i * 10,
                originalY: 64 + i,
                originalZ: i * 20
            ]
        }
        return signs
    }

    /**
     * Create mock book data organized by author.
     *
     * @param authorCount Number of authors
     * @param booksPerAuthor Books per author
     * @return Map of author -> list of books
     */
    private Map<String, List<Map<String, Object>>> createMockBooksByAuthor(int authorCount, int booksPerAuthor) {
        Map<String, List<Map<String, Object>>> booksByAuthor = [:]
        for (int a = 0; a < authorCount; a++) {
            String authorName = a < 26 ? "Author ${(char) ((int)'A' + a)}" : "Author ${a}"
            booksByAuthor[authorName] = createMockBooks(booksPerAuthor)
        }
        return booksByAuthor
    }

    /**
     * Create mock book data.
     *
     * @param count Number of books to create
     * @return List of book maps
     */
    private List<Map<String, Object>> createMockBooks(int count) {
        List<Map<String, Object>> books = []
        for (int i = 0; i < count; i++) {
            ListTag<StringTag> pages = new ListTag<>(StringTag)
            pages.add(new StringTag("Page 1 of book ${i}"))
            pages.add(new StringTag("Page 2 of book ${i}"))

            books << [
                title: "Book ${i}",
                author: "Test Author",
                pages: pages,
                generation: 0
            ]
        }
        return books
    }

    /**
     * Create long pages for testing command length limits.
     *
     * @param pageCount Number of pages
     * @return ListTag of pages with long text
     */
    private ListTag<StringTag> createLongPages(int pageCount) {
        ListTag<StringTag> pages = new ListTag<>(StringTag)
        for (int i = 0; i < pageCount; i++) {
            String longText = 'X' * 100 // 100 characters per page
            pages.add(new StringTag(longText))
        }
        return pages
    }

    /**
     * Read and parse a Litematica file.
     *
     * @param file The .litematic file
     * @return Root CompoundTag
     */
    private CompoundTag readLitematicaFile(File file) {
        NamedTag namedTag = NBTUtil.read(file, true) // true = compressed
        return namedTag.tag as CompoundTag
    }
}

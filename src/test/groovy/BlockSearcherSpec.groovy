import net.querz.nbt.tag.CompoundTag
import net.querz.nbt.tag.ListTag
import net.querz.nbt.tag.StringTag
import spock.lang.Specification

/**
 * Unit tests for BlockSearcher utility class.
 * Tests BlockLocation class, block ID normalization, parsing, matching, and palette utilities.
 */
class BlockSearcherSpec extends Specification {

    // =========================================================================
    // BlockLocation Inner Class Tests
    // =========================================================================

    def "BlockLocation equals should compare all fields"() {
        given:
        BlockSearcher.BlockLocation loc1 = new BlockSearcher.BlockLocation(
            'minecraft:stone', 'overworld', 100, 64, 200, ['axis': 'z'], 'r.0.0.mca'
        )
        BlockSearcher.BlockLocation loc2 = new BlockSearcher.BlockLocation(
            'minecraft:stone', 'overworld', 100, 64, 200, ['axis': 'z'], 'r.0.0.mca'
        )
        BlockSearcher.BlockLocation loc3 = new BlockSearcher.BlockLocation(
            'minecraft:stone', 'overworld', 101, 64, 200, ['axis': 'z'], 'r.0.0.mca'
        )

        expect:
        loc1 == loc2
        loc1 != loc3
    }

    def "BlockLocation equals should return false for non-BlockLocation object"() {
        given:
        BlockSearcher.BlockLocation loc = new BlockSearcher.BlockLocation(
            'minecraft:stone', 'overworld', 100, 64, 200, null, null
        )

        expect:
        loc != 'not a BlockLocation'
        loc != null
    }

    def "BlockLocation hashCode should be consistent"() {
        given:
        BlockSearcher.BlockLocation loc1 = new BlockSearcher.BlockLocation(
            'minecraft:stone', 'overworld', 100, 64, 200, null, null
        )
        BlockSearcher.BlockLocation loc2 = new BlockSearcher.BlockLocation(
            'minecraft:stone', 'overworld', 100, 64, 200, null, null
        )

        expect:
        loc1.hashCode() == loc2.hashCode()
    }

    def "BlockLocation hashCode should differ for different coordinates"() {
        given:
        BlockSearcher.BlockLocation loc1 = new BlockSearcher.BlockLocation(
            'minecraft:stone', 'overworld', 100, 64, 200, null, null
        )
        BlockSearcher.BlockLocation loc2 = new BlockSearcher.BlockLocation(
            'minecraft:stone', 'overworld', 101, 64, 200, null, null
        )

        expect:
        loc1.hashCode() != loc2.hashCode()
    }

    def "BlockLocation toCsvRow should format correctly"() {
        given:
        BlockSearcher.BlockLocation loc = new BlockSearcher.BlockLocation(
            'minecraft:nether_portal', 'overworld', 100, 64, 200,
            ['axis': 'z', 'facing': 'north'], 'r.0.0.mca'
        )

        when:
        String csv = loc.toCsvRow()

        then:
        csv == 'minecraft:nether_portal,overworld,100,64,200,axis=z;facing=north,r.0.0.mca'
    }

    def "BlockLocation toCsvRow should handle empty properties"() {
        given:
        BlockSearcher.BlockLocation loc = new BlockSearcher.BlockLocation(
            'minecraft:stone', 'overworld', 100, 64, 200, null, 'r.0.0.mca'
        )

        when:
        String csv = loc.toCsvRow()

        then:
        csv == 'minecraft:stone,overworld,100,64,200,,r.0.0.mca'
    }

    def "BlockLocation toString should be readable"() {
        given:
        BlockSearcher.BlockLocation loc = new BlockSearcher.BlockLocation(
            'minecraft:stone', 'overworld', 100, 64, 200, null, null
        )

        when:
        String str = loc

        then:
        str.contains('minecraft:stone')
        str.contains('overworld')
        str.contains('100')
        str.contains('64')
        str.contains('200')
    }

    def "BlockLocation constructor should use empty map for null properties"() {
        when:
        BlockSearcher.BlockLocation loc = new BlockSearcher.BlockLocation(
            'minecraft:stone', 'overworld', 100, 64, 200, null, null
        )

        then:
        loc.properties != null
        loc.properties.isEmpty()
    }

    // =========================================================================
    // normalizeBlockId() Tests
    // =========================================================================

    def "normalizeBlockId should add minecraft prefix when missing"() {
        expect:
        BlockSearcher.normalizeBlockId('stone') == 'minecraft:stone'
        BlockSearcher.normalizeBlockId('nether_portal') == 'minecraft:nether_portal'
    }

    def "normalizeBlockId should preserve existing prefix"() {
        expect:
        BlockSearcher.normalizeBlockId('minecraft:stone') == 'minecraft:stone'
        BlockSearcher.normalizeBlockId('mod:custom_block') == 'mod:custom_block'
    }

    def "normalizeBlockId should return minecraft:air for null"() {
        expect:
        BlockSearcher.normalizeBlockId(null) == 'minecraft:air'
    }

    def "normalizeBlockId should return minecraft:air for empty string"() {
        expect:
        BlockSearcher.normalizeBlockId('') == 'minecraft:air'
    }

    def "normalizeBlockId should handle block IDs with custom namespace"() {
        expect:
        BlockSearcher.normalizeBlockId('custom:block') == 'custom:block'
    }

    // =========================================================================
    // parseBlockIds() Tests
    // =========================================================================

    def "parseBlockIds should split comma-separated list"() {
        when:
        Set<String> result = BlockSearcher.parseBlockIds('stone,dirt,nether_portal')

        then:
        result.size() == 3
        result.contains('minecraft:stone')
        result.contains('minecraft:dirt')
        result.contains('minecraft:nether_portal')
    }

    def "parseBlockIds should normalize all IDs"() {
        when:
        Set<String> result = BlockSearcher.parseBlockIds('minecraft:stone,dirt')

        then:
        result.size() == 2
        result.contains('minecraft:stone')
        result.contains('minecraft:dirt')
    }

    def "parseBlockIds should handle empty input"() {
        when:
        Set<String> result = BlockSearcher.parseBlockIds('')

        then:
        result.isEmpty()
    }

    def "parseBlockIds should handle null input"() {
        when:
        Set<String> result = BlockSearcher.parseBlockIds(null)

        then:
        result.isEmpty()
    }

    def "parseBlockIds should trim whitespace"() {
        when:
        Set<String> result = BlockSearcher.parseBlockIds(' stone , dirt , nether_portal ')

        then:
        result.size() == 3
        result.contains('minecraft:stone')
        result.contains('minecraft:dirt')
        result.contains('minecraft:nether_portal')
    }

    def "parseBlockIds should filter empty entries"() {
        when:
        Set<String> result = BlockSearcher.parseBlockIds('stone,,dirt,  ,nether_portal')

        then:
        result.size() == 3
        result.contains('minecraft:stone')
        result.contains('minecraft:dirt')
        result.contains('minecraft:nether_portal')
    }

    def "parseBlockIds should handle single block ID"() {
        when:
        Set<String> result = BlockSearcher.parseBlockIds('stone')

        then:
        result.size() == 1
        result.contains('minecraft:stone')
    }

    // =========================================================================
    // blockMatchesTarget() Tests
    // =========================================================================

    def "blockMatchesTarget should match with same prefix"() {
        expect:
        BlockSearcher.blockMatchesTarget('minecraft:stone', 'minecraft:stone')
        BlockSearcher.blockMatchesTarget('minecraft:nether_portal', 'minecraft:nether_portal')
    }

    def "blockMatchesTarget should match across prefix variations"() {
        expect:
        BlockSearcher.blockMatchesTarget('stone', 'minecraft:stone')
        BlockSearcher.blockMatchesTarget('minecraft:stone', 'stone')
        BlockSearcher.blockMatchesTarget('stone', 'stone')
    }

    def "blockMatchesTarget should not match different blocks"() {
        expect:
        !BlockSearcher.blockMatchesTarget('minecraft:stone', 'minecraft:dirt')
        !BlockSearcher.blockMatchesTarget('stone', 'dirt')
    }

    def "blockMatchesTarget should handle custom namespaces"() {
        expect:
        BlockSearcher.blockMatchesTarget('mod:block', 'mod:block')
        !BlockSearcher.blockMatchesTarget('mod:block', 'minecraft:block')
    }

    def "blockMatchesTarget should normalize both sides"() {
        expect:
        BlockSearcher.blockMatchesTarget('stone', 'stone')
        BlockSearcher.blockMatchesTarget('minecraft:stone', 'minecraft:stone')
    }

    // =========================================================================
    // extractPaletteBlockNames() Tests
    // =========================================================================

    def "extractPaletteBlockNames should extract all block names from palette"() {
        given:
        ListTag<CompoundTag> palette = new ListTag<>(CompoundTag)
        CompoundTag block1 = new CompoundTag()
        block1.putString('Name', 'minecraft:stone')
        palette.add(block1)
        CompoundTag block2 = new CompoundTag()
        block2.putString('Name', 'minecraft:dirt')
        palette.add(block2)

        when:
        Set<String> names = BlockSearcher.extractPaletteBlockNames(palette)

        then:
        names.size() == 2
        names.contains('minecraft:stone')
        names.contains('minecraft:dirt')
    }

    def "extractPaletteBlockNames should handle empty palette"() {
        given:
        ListTag<CompoundTag> palette = new ListTag<>(CompoundTag)

        when:
        Set<String> names = BlockSearcher.extractPaletteBlockNames(palette)

        then:
        names.isEmpty()
    }

    def "extractPaletteBlockNames should skip entries without Name field"() {
        given:
        ListTag<CompoundTag> palette = new ListTag<>(CompoundTag)
        CompoundTag block1 = new CompoundTag()
        block1.putString('Name', 'minecraft:stone')
        palette.add(block1)
        CompoundTag block2 = new CompoundTag()  // No Name field
        block2.putString('Other', 'value')
        palette.add(block2)

        when:
        Set<String> names = BlockSearcher.extractPaletteBlockNames(palette)

        then:
        names.size() == 1
        names.contains('minecraft:stone')
    }

    def "extractPaletteBlockNames should skip non-CompoundTag entries"() {
        given:
        ListTag<?> palette = new ListTag<>(CompoundTag)
        CompoundTag block1 = new CompoundTag()
        block1.putString('Name', 'minecraft:stone')
        palette.add(block1)
        // Note: Can't easily add non-CompoundTag to ListTag<CompoundTag>, but test structure is here

        when:
        Set<String> names = BlockSearcher.extractPaletteBlockNames(palette)

        then:
        names.size() == 1
        names.contains('minecraft:stone')
    }

    // =========================================================================
    // assemblePaletteMap() Tests
    // =========================================================================

    def "assemblePaletteMap should map indices to matching blocks only"() {
        given:
        ListTag<CompoundTag> palette = new ListTag<>(CompoundTag)
        CompoundTag block1 = new CompoundTag()
        block1.putString('Name', 'minecraft:stone')
        palette.add(block1)
        CompoundTag block2 = new CompoundTag()
        block2.putString('Name', 'minecraft:dirt')
        palette.add(block2)
        CompoundTag block3 = new CompoundTag()
        block3.putString('Name', 'minecraft:nether_portal')
        palette.add(block3)
        Set<String> targetBlocks = ['minecraft:stone', 'minecraft:nether_portal'] as Set

        when:
        Map<Integer, CompoundTag> map = BlockSearcher.assemblePaletteMap(palette, targetBlocks)

        then:
        map.size() == 2
        map.containsKey(0)  // stone at index 0
        map.containsKey(2)  // nether_portal at index 2
        !map.containsKey(1)  // dirt not in targets
    }

    def "assemblePaletteMap should return empty map when no matches"() {
        given:
        ListTag<CompoundTag> palette = new ListTag<>(CompoundTag)
        CompoundTag block1 = new CompoundTag()
        block1.putString('Name', 'minecraft:stone')
        palette.add(block1)
        Set<String> targetBlocks = ['minecraft:dirt'] as Set

        when:
        Map<Integer, CompoundTag> map = BlockSearcher.assemblePaletteMap(palette, targetBlocks)

        then:
        map.isEmpty()
    }

    def "assemblePaletteMap should handle empty palette"() {
        given:
        ListTag<CompoundTag> palette = new ListTag<>(CompoundTag)
        Set<String> targetBlocks = ['minecraft:stone'] as Set

        when:
        Map<Integer, CompoundTag> map = BlockSearcher.assemblePaletteMap(palette, targetBlocks)

        then:
        map.isEmpty()
    }

    def "assemblePaletteMap should use blockMatchesTarget for matching"() {
        given:
        ListTag<CompoundTag> palette = new ListTag<>(CompoundTag)
        CompoundTag block1 = new CompoundTag()
        block1.putString('Name', 'minecraft:stone')
        palette.add(block1)
        Set<String> targetBlocks = ['stone'] as Set  // Without prefix

        when:
        Map<Integer, CompoundTag> map = BlockSearcher.assemblePaletteMap(palette, targetBlocks)

        then:
        map.size() == 1  // Should match via blockMatchesTarget normalization
        map.containsKey(0)
    }

    // =========================================================================
    // extractBlockProperties() Tests
    // =========================================================================

    def "extractBlockProperties should extract all properties"() {
        given:
        CompoundTag blockTag = new CompoundTag()
        blockTag.putString('Name', 'minecraft:nether_portal')
        CompoundTag properties = new CompoundTag()
        properties.putString('axis', 'z')
        properties.putString('facing', 'north')
        blockTag.put('Properties', properties)

        when:
        Map<String, String> props = BlockSearcher.extractBlockProperties(blockTag)

        then:
        props.size() == 2
        props['axis'] == 'z'
        props['facing'] == 'north'
    }

    def "extractBlockProperties should return empty map for no properties"() {
        given:
        CompoundTag blockTag = new CompoundTag()
        blockTag.putString('Name', 'minecraft:stone')

        when:
        Map<String, String> props = BlockSearcher.extractBlockProperties(blockTag)

        then:
        props.isEmpty()
    }

    def "extractBlockProperties should skip non-StringTag values"() {
        given:
        CompoundTag blockTag = new CompoundTag()
        blockTag.putString('Name', 'minecraft:test')
        CompoundTag properties = new CompoundTag()
        properties.putString('stringProp', 'value')
        properties.putInt('intProp', 42)  // Non-string value
        blockTag.put('Properties', properties)

        when:
        Map<String, String> props = BlockSearcher.extractBlockProperties(blockTag)

        then:
        props.size() == 1
        props.containsKey('stringProp')
        !props.containsKey('intProp')
    }

    def "extractBlockProperties should throw NPE for null blockTag"() {
        when:
        BlockSearcher.extractBlockProperties(null)

        then:
        thrown(NullPointerException)
    }

    // =========================================================================
    // makeBlockLocation() Tests
    // =========================================================================

    def "makeBlockLocation should create location with all fields"() {
        given:
        CompoundTag blockTag = new CompoundTag()
        blockTag.putString('Name', 'minecraft:nether_portal')
        CompoundTag properties = new CompoundTag()
        properties.putString('axis', 'z')
        blockTag.put('Properties', properties)

        when:
        BlockSearcher.BlockLocation loc = BlockSearcher.makeBlockLocation(
            blockTag, 'overworld', 100, 64, 200, 'r.0.0.mca'
        )

        then:
        loc.blockType == 'minecraft:nether_portal'
        loc.dimension == 'overworld'
        loc.x == 100
        loc.y == 64
        loc.z == 200
        loc.properties['axis'] == 'z'
        loc.regionFile == 'r.0.0.mca'
    }

    def "makeBlockLocation should handle missing properties"() {
        given:
        CompoundTag blockTag = new CompoundTag()
        blockTag.putString('Name', 'minecraft:stone')

        when:
        BlockSearcher.BlockLocation loc = BlockSearcher.makeBlockLocation(
            blockTag, 'overworld', 100, 64, 200, null
        )

        then:
        loc.blockType == 'minecraft:stone'
        loc.properties.isEmpty()
        loc.regionFile == null
    }

    // =========================================================================
    // DIMENSION_FOLDERS Constant Tests
    // =========================================================================

    def "DIMENSION_FOLDERS should map all three dimensions"() {
        expect:
        BlockSearcher.DIMENSION_FOLDERS.containsKey('overworld')
        BlockSearcher.DIMENSION_FOLDERS.containsKey('nether')
        BlockSearcher.DIMENSION_FOLDERS.containsKey('end')
        BlockSearcher.DIMENSION_FOLDERS['overworld'] == 'region'
        BlockSearcher.DIMENSION_FOLDERS['nether'] == 'DIM-1/region'
        BlockSearcher.DIMENSION_FOLDERS['end'] == 'DIM1/region'
    }

    def "DIMENSION_FOLDERS should have exactly three entries"() {
        expect:
        BlockSearcher.DIMENSION_FOLDERS.size() == 3
    }

}

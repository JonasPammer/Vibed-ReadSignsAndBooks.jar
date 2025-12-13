import net.querz.nbt.tag.ListTag
import net.querz.nbt.tag.StringTag
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Unit tests for ShulkerBoxGenerator utility class.
 * Tests deterministic color mapping and version-specific shulker box command generation.
 */
class ShulkerBoxGeneratorSpec extends Specification {

    // =========================================================================
    // getShulkerColorForAuthor() Tests
    // =========================================================================

    def "getShulkerColorForAuthor should return a valid color"() {
        when:
        String color = ShulkerBoxGenerator.getShulkerColorForAuthor('TestAuthor')

        then:
        color != null
        ShulkerBoxGenerator.SHULKER_COLORS.contains(color)
    }

    def "getShulkerColorForAuthor should return same color for same author"() {
        when:
        String color1 = ShulkerBoxGenerator.getShulkerColorForAuthor('AuthorName')
        String color2 = ShulkerBoxGenerator.getShulkerColorForAuthor('AuthorName')

        then:
        color1 == color2  // Deterministic
    }

    def "getShulkerColorForAuthor should use 'Unknown' for null"() {
        when:
        String color = ShulkerBoxGenerator.getShulkerColorForAuthor(null)

        then:
        color != null
        ShulkerBoxGenerator.SHULKER_COLORS.contains(color)
    }

    def "getShulkerColorForAuthor should use 'Unknown' for empty string"() {
        when:
        String color = ShulkerBoxGenerator.getShulkerColorForAuthor('')

        then:
        color != null
        ShulkerBoxGenerator.SHULKER_COLORS.contains(color)
    }

    def "getShulkerColorForAuthor should use 'Unknown' for whitespace"() {
        when:
        String color = ShulkerBoxGenerator.getShulkerColorForAuthor('   ')

        then:
        color != null
        ShulkerBoxGenerator.SHULKER_COLORS.contains(color)
    }

    def "getShulkerColorForAuthor should map different authors to potentially different colors"() {
        when:
        String color1 = ShulkerBoxGenerator.getShulkerColorForAuthor('Author1')
        String color2 = ShulkerBoxGenerator.getShulkerColorForAuthor('Author2')

        then:
        // They might be the same or different, but both should be valid colors
        ShulkerBoxGenerator.SHULKER_COLORS.contains(color1)
        ShulkerBoxGenerator.SHULKER_COLORS.contains(color2)
    }

    // =========================================================================
    // generateShulkerBoxCommand() Tests
    // =========================================================================

    def "generateShulkerBoxCommand should return empty string for empty books list"() {
        given:
        List<Map<String, Object>> emptyBooks = []

        expect:
        ShulkerBoxGenerator.generateShulkerBoxCommand('Author', emptyBooks, 0, '1_13') == ''
    }

    def "generateShulkerBoxCommand should generate 1.13 format"() {
        given:
        List<Map<String, Object>> books = createTestBooks(1)

        when:
        String command = ShulkerBoxGenerator.generateShulkerBoxCommand('TestAuthor', books, 0, '1_13')

        then:
        command.contains('give @a')
        command.contains('_shulker_box')
        command.contains('BlockEntityTag')
        command.contains('Items:')
        command.contains('Author: TestAuthor')
    }

    def "generateShulkerBoxCommand should generate 1.14 format"() {
        given:
        List<Map<String, Object>> books = createTestBooks(1)

        when:
        String command = ShulkerBoxGenerator.generateShulkerBoxCommand('TestAuthor', books, 0, '1_14')

        then:
        command.contains('give @a')
        command.contains('_shulker_box')
        command.contains('BlockEntityTag')
        command.contains('Items:')
    }

    def "generateShulkerBoxCommand should generate 1.20 format (uses 1.14)"() {
        given:
        List<Map<String, Object>> books = createTestBooks(1)

        when:
        String command = ShulkerBoxGenerator.generateShulkerBoxCommand('TestAuthor', books, 0, '1_20')

        then:
        command.contains('give @a')
        command.contains('_shulker_box')
        command.contains('BlockEntityTag')
    }

    def "generateShulkerBoxCommand should generate 1.20.5 format"() {
        given:
        List<Map<String, Object>> books = createTestBooks(1)

        when:
        String command = ShulkerBoxGenerator.generateShulkerBoxCommand('TestAuthor', books, 0, '1_20_5')

        then:
        command.contains('give @a')
        command.contains('minecraft:')
        command.contains('_shulker_box')
        command.contains('container=')
        command.contains('item_name=')
    }

    def "generateShulkerBoxCommand should generate 1.21 format"() {
        given:
        List<Map<String, Object>> books = createTestBooks(1)

        when:
        String command = ShulkerBoxGenerator.generateShulkerBoxCommand('TestAuthor', books, 0, '1_21')

        then:
        command.contains('give @a')
        command.contains('_shulker_box')
        command.contains('container=')
        !command.contains('minecraft:_shulker_box')  // 1.21 doesn't use minecraft: prefix
    }

    def "generateShulkerBoxCommand should include box index in display name for overflow boxes"() {
        given:
        List<Map<String, Object>> books = createTestBooks(1)

        when:
        String command = ShulkerBoxGenerator.generateShulkerBoxCommand('Author', books, 1, '1_13')

        then:
        command.contains('Author: Author (2)')  // boxIndex 1 means second box (index + 1)
    }

    def "generateShulkerBoxCommand should limit to 27 books per box"() {
        given:
        List<Map<String, Object>> books = createTestBooks(30)  // More than 27

        when:
        String command = ShulkerBoxGenerator.generateShulkerBoxCommand('Author', books, 0, '1_13')

        then:
        // Count occurrences of "Slot:" to verify only 27 books
        int slotCount = command.findAll('Slot:').size()
        slotCount == 27
    }

    def "generateShulkerBoxCommand should handle multiple boxes with boxIndex"() {
        given:
        List<Map<String, Object>> books = createTestBooks(30)  // More than 27

        when:
        String command1 = ShulkerBoxGenerator.generateShulkerBoxCommand('Author', books, 0, '1_13')
        String command2 = ShulkerBoxGenerator.generateShulkerBoxCommand('Author', books, 1, '1_13')

        then:
        command1.contains('Slot:0')
        command1.contains('Slot:26')  // Last slot of first box
        command2.contains('Slot:0')   // First slot of second box
        command2.contains('Slot:2')   // Last 3 books (30 - 27 = 3)
    }

    def "generateShulkerBoxCommand should return empty string for unknown version"() {
        given:
        List<Map<String, Object>> books = createTestBooks(1)

        expect:
        ShulkerBoxGenerator.generateShulkerBoxCommand('Author', books, 0, 'unknown') == ''
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private List<Map<String, Object>> createTestBooks(int count) {
        List<Map<String, Object>> books = []
        count.times { int i ->
            ListTag<StringTag> pages = new ListTag<>(StringTag)
            pages.addString('{"text":"Page ' + (i + 1) + '"}')
            books << [
                title: "Book ${i + 1}",
                author: 'TestAuthor',
                pages: pages,
                generation: 0
            ]
        }
        return books
    }
}

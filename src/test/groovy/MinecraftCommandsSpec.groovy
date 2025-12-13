import net.querz.nbt.tag.ListTag
import net.querz.nbt.tag.StringTag
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Unit tests for MinecraftCommands utility class.
 * Tests version-specific command generation for books and signs.
 */
class MinecraftCommandsSpec extends Specification {

    // =========================================================================
    // escapeForMinecraftCommand() Tests
    // =========================================================================

    def "escapeForMinecraftCommand should return empty string for null"() {
        expect:
        MinecraftCommands.escapeForMinecraftCommand(null, '1_13') == ''
    }

    def "escapeForMinecraftCommand should return empty string for empty string"() {
        expect:
        MinecraftCommands.escapeForMinecraftCommand('', '1_13') == ''
    }

    @Unroll
    def "escapeForMinecraftCommand should use double backslash for version #version"() {
        given:
        String text = 'Text with "quotes" and \\backslash'

        when:
        String escaped = MinecraftCommands.escapeForMinecraftCommand(text, version)

        then:
        escaped.contains('\\\\')  // Double backslash
        escaped.contains('\\"')  // Escaped quotes

        where:
        version << ['1_13', '1_14', '1_20']
    }

    @Unroll
    def "escapeForMinecraftCommand should use single backslash for version #version"() {
        given:
        String text = 'Text with "quotes" and \\backslash'

        when:
        String escaped = MinecraftCommands.escapeForMinecraftCommand(text, version)

        then:
        escaped.contains('\\\\')  // Single backslash (escaped once)
        escaped.contains('\\"')  // Escaped quotes

        where:
        version << ['1_20_5', '1_21']
    }

    def "escapeForMinecraftCommand should replace newlines"() {
        given:
        String text = "Line1\nLine2\rLine3"

        when:
        String escaped = MinecraftCommands.escapeForMinecraftCommand(text, '1_13')

        then:
        !escaped.contains('\n')
        !escaped.contains('\r')
    }

    // =========================================================================
    // convertFormattingCodesToJson() Tests
    // =========================================================================

    def "convertFormattingCodesToJson should wrap plain text in JSON"() {
        expect:
        MinecraftCommands.convertFormattingCodesToJson('Plain text') == '{"text":"Plain text"}'
    }

    def "convertFormattingCodesToJson should remove formatting codes"() {
        given:
        String text = '\u00A7cRed text\u00A7r normal'

        when:
        String result = MinecraftCommands.convertFormattingCodesToJson(text)

        then:
        result == '{"text":"Red text normal"}'
    }

    def "convertFormattingCodesToJson should return null text for null input"() {
        expect:
        MinecraftCommands.convertFormattingCodesToJson(null) == '{"text":"null"}'
    }

    // =========================================================================
    // mapColorCode() Tests
    // =========================================================================

    @Unroll
    def "mapColorCode should map color code #code to #expectedColor"() {
        expect:
        MinecraftCommands.mapColorCode(code as char) == expectedColor

        where:
        code | expectedColor
        '0'  | 'black'
        '1'  | 'dark_blue'
        '2'  | 'dark_green'
        '3'  | 'dark_aqua'
        '4'  | 'dark_red'
        '5'  | 'dark_purple'
        '6'  | 'gold'
        '7'  | 'gray'
        '8'  | 'dark_gray'
        '9'  | 'blue'
        'a'  | 'green'
        'b'  | 'aqua'
        'c'  | 'red'
        'd'  | 'light_purple'
        'e'  | 'yellow'
        'f'  | 'white'
    }

    @Unroll
    def "mapColorCode should return null for formatting code #code"() {
        expect:
        MinecraftCommands.mapColorCode(code as char) == null

        where:
        code << ['k', 'l', 'm', 'n', 'o', 'r']
    }

    def "mapColorCode should return null for unknown code"() {
        expect:
        MinecraftCommands.mapColorCode('x' as char) == null
    }

    // =========================================================================
    // generateBookCommand() Tests
    // =========================================================================

    def "generateBookCommand should generate 1.13 format"() {
        given:
        ListTag<StringTag> pages = new ListTag<>(StringTag)
        pages.addString('{"text":"Page 1"}')
        pages.addString('{"text":"Page 2"}')

        when:
        String command = MinecraftCommands.generateBookCommand('Test Book', 'Author', pages, '1_13')

        then:
        command.contains('give @p written_book')
        command.contains('title:"Test Book"')
        command.contains('author:"Author"')
        command.contains('generation:0')
        command.contains('pages:[')
    }

    def "generateBookCommand should generate 1.14 format"() {
        given:
        ListTag<StringTag> pages = new ListTag<>(StringTag)
        pages.addString('{"text":"Page 1"}')

        when:
        String command = MinecraftCommands.generateBookCommand('Test Book', 'Author', pages, '1_14')

        then:
        command.contains('give @p written_book')
        command.contains('title:"Test Book"')
    }

    def "generateBookCommand should generate 1.20 format"() {
        given:
        ListTag<StringTag> pages = new ListTag<>(StringTag)
        pages.addString('{"text":"Page 1"}')

        when:
        String command = MinecraftCommands.generateBookCommand('Test Book', 'Author', pages, '1_20')

        then:
        command.contains('give @p written_book')
        command.contains('title:"Test Book"')
    }

    def "generateBookCommand should generate 1.20.5 format"() {
        given:
        ListTag<StringTag> pages = new ListTag<>(StringTag)
        pages.addString('{"text":"Page 1"}')

        when:
        String command = MinecraftCommands.generateBookCommand('Test Book', 'Author', pages, '1_20_5')

        then:
        command.contains('give @p written_book[minecraft:written_book_content=')
        command.contains('title:"Test Book"')
    }

    def "generateBookCommand should generate 1.21 format"() {
        given:
        ListTag<StringTag> pages = new ListTag<>(StringTag)
        pages.addString('{"text":"Page 1"}')

        when:
        String command = MinecraftCommands.generateBookCommand('Test Book', 'Author', pages, '1_21')

        then:
        command.contains('give @p written_book[written_book_content=')
        command.contains('title:"Test Book"')
        !command.contains('minecraft:written_book_content')  // 1.21 doesn't use minecraft: prefix
    }

    def "generateBookCommand should use default title and author for null"() {
        given:
        ListTag<StringTag> pages = new ListTag<>(StringTag)
        pages.addString('{"text":"Page 1"}')

        when:
        String command = MinecraftCommands.generateBookCommand(null, null, pages, '1_13')

        then:
        command.contains('title:"Untitled"')
        command.contains('author:"Unknown"')
    }

    def "generateBookCommand should include generation"() {
        given:
        ListTag<StringTag> pages = new ListTag<>(StringTag)
        pages.addString('{"text":"Page 1"}')

        when:
        String command = MinecraftCommands.generateBookCommand('Test', 'Author', pages, '1_13', 2)

        then:
        command.contains('generation:2')
    }

    // =========================================================================
    // generateSignCommand() Tests
    // =========================================================================

    def "generateSignCommand should return empty string for empty lines"() {
        expect:
        MinecraftCommands.generateSignCommand([], [x: 0, z: 0], '1_13') == ''
    }

    def "generateSignCommand should generate 1.13 format"() {
        given:
        List<String> lines = ['Line 1', 'Line 2', 'Line 3', 'Line 4']
        Map<String, Object> position = [x: 10, z: 20]

        when:
        String command = MinecraftCommands.generateSignCommand(lines, position, '1_13')

        then:
        command.contains('setblock ~10 ~ ~20 oak_sign')
        command.contains('Text1:')
        command.contains('Text2:')
        command.contains('Text3:')
        command.contains('Text4:')
    }

    def "generateSignCommand should generate 1.14 format"() {
        given:
        List<String> lines = ['Line 1', 'Line 2']
        Map<String, Object> position = [x: 5, z: 15]

        when:
        String command = MinecraftCommands.generateSignCommand(lines, position, '1_14')

        then:
        command.contains('setblock ~5 ~ ~15 oak_sign')
        command.contains('Text1:')
    }

    def "generateSignCommand should generate 1.20 format with front_text"() {
        given:
        List<String> lines = ['Line 1', 'Line 2']
        Map<String, Object> position = [x: 0, z: 0]

        when:
        String command = MinecraftCommands.generateSignCommand(lines, position, '1_20')

        then:
        command.contains('setblock ~0 ~ ~0 oak_sign')
        command.contains('front_text:')
        command.contains('messages:')
    }

    def "generateSignCommand should generate 1.20.5 format"() {
        given:
        List<String> lines = ['Line 1']
        Map<String, Object> position = [x: 0, z: 0]

        when:
        String command = MinecraftCommands.generateSignCommand(lines, position, '1_20_5')

        then:
        command.contains('setblock ~0 ~ ~0 oak_sign')
        command.contains('front_text:')
    }

    def "generateSignCommand should generate 1.21 format"() {
        given:
        List<String> lines = ['Line 1']
        Map<String, Object> position = [x: 0, z: 0]

        when:
        String command = MinecraftCommands.generateSignCommand(lines, position, '1_21')

        then:
        command.contains('setblock ~0 ~ ~0 oak_sign')
        command.contains('front_text:')
    }

    def "generateSignCommand should include clickEvent with original coordinates"() {
        given:
        List<String> lines = ['Line 1']
        Map<String, Object> position = [
            x: 0, z: 0,
            originalX: 100, originalY: 75, originalZ: -200
        ]

        when:
        String command = MinecraftCommands.generateSignCommand(lines, position, '1_13')

        then:
        command.contains('clickEvent')
        command.contains('100')
        command.contains('75')
        command.contains('-200')
    }

    def "generateSignCommand should handle back lines for 1.20+"() {
        given:
        List<String> frontLines = ['Front 1']
        List<String> backLines = ['Back 1']
        Map<String, Object> position = [x: 0, z: 0]

        when:
        String command = MinecraftCommands.generateSignCommand(frontLines, position, '1_20', backLines)

        then:
        command.contains('back_text:')
        command.contains('messages:')
    }

    def "generateSignCommand should return empty string for unknown version"() {
        given:
        List<String> lines = ['Line 1']
        Map<String, Object> position = [x: 0, z: 0]

        expect:
        MinecraftCommands.generateSignCommand(lines, position, 'unknown') == ''
    }

    // =========================================================================
    // generateBookNBT() Tests
    // =========================================================================

    def "generateBookNBT should generate NBT format"() {
        given:
        ListTag<StringTag> pages = new ListTag<>(StringTag)
        pages.addString('{"text":"Page 1"}')

        when:
        String nbt = MinecraftCommands.generateBookNBT('Test', 'Author', pages, '1_13')

        then:
        nbt.contains('title:"Test"')
        nbt.contains('author:"Author"')
        nbt.contains('pages:[')
        !nbt.contains('give @p')  // NBT format, not command
    }

    // =========================================================================
    // generateBookComponents() Tests
    // =========================================================================

    def "generateBookComponents should generate components format"() {
        given:
        ListTag<StringTag> pages = new ListTag<>(StringTag)
        pages.addString('{"text":"Page 1"}')

        when:
        String components = MinecraftCommands.generateBookComponents('Test', 'Author', pages, '1_20_5')

        then:
        components.contains('title:"Test"')
        components.contains('author:"Author"')
        components.contains('pages:[')
    }
}

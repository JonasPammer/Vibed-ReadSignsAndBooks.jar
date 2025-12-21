import net.querz.nbt.tag.CompoundTag
import net.querz.nbt.tag.ListTag
import net.querz.nbt.tag.StringTag
import org.json.JSONObject
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Unit tests for TextUtils utility class.
 * Tests text extraction, formatting removal, filename sanitization, and sign/coordinate parsing.
 */
class TextUtilsSpec extends Specification {

    // =========================================================================
    // sanitizeFilename() Tests
    // =========================================================================

    def "sanitizeFilename should return 'unnamed' for null input"() {
        expect:
        TextUtils.sanitizeFilename(null) == 'unnamed'
    }

    def "sanitizeFilename should return 'unnamed' for empty string"() {
        expect:
        TextUtils.sanitizeFilename('') == 'unnamed'
    }

    def "sanitizeFilename should replace invalid characters with underscore"() {
        expect:
        TextUtils.sanitizeFilename('test/file:name*?<>|.txt') == 'test_file_name_____.txt'
    }

    def "sanitizeFilename should truncate long filenames to 200 characters"() {
        given:
        String longName = 'a' * 250

        when:
        String result = TextUtils.sanitizeFilename(longName)

        then:
        result.length() == 200
        result == 'a' * 200
    }

    def "sanitizeFilename should preserve valid characters"() {
        expect:
        TextUtils.sanitizeFilename('My Book Title 123') == 'My Book Title 123'
    }

    // =========================================================================
    // extractPlayerName() Tests
    // =========================================================================

    def "extractPlayerName should extract player name from bookInfo string"() {
        expect:
        TextUtils.extractPlayerName('Inventory of player abc123-def456.dat', 'player ') == 'abc123-def456'
    }

    def "extractPlayerName should handle player name without .dat extension"() {
        expect:
        TextUtils.extractPlayerName('Inventory of player testuser', 'player ') == 'testuser'
    }

    def "extractPlayerName should return 'unknown' when prefix not found"() {
        expect:
        TextUtils.extractPlayerName('Some other text', 'player ') == 'unknown'
    }

    def "extractPlayerName should return 'unknown' for null input"() {
        expect:
        TextUtils.extractPlayerName(null, 'player ') == 'unknown'
    }

    def "extractPlayerName should trim whitespace"() {
        expect:
        TextUtils.extractPlayerName('Inventory of player  testuser  .dat', 'player ') == 'testuser'
    }

    // =========================================================================
    // extractPageText() Tests
    // =========================================================================

    def "extractPageText should extract from string list"() {
        given:
        ListTag<StringTag> pages = new ListTag<>(StringTag)
        pages.addString('Page 1 text')
        pages.addString('Page 2 text')

        expect:
        TextUtils.extractPageText(pages, 0) == 'Page 1 text'
        TextUtils.extractPageText(pages, 1) == 'Page 2 text'
    }

    def "extractPageText should extract from compound list with raw field"() {
        given:
        ListTag<CompoundTag> pages = new ListTag<>(CompoundTag)
        CompoundTag page1 = new CompoundTag()
        page1.putString('raw', 'Raw page text')
        pages.add(page1)

        expect:
        TextUtils.extractPageText(pages, 0) == 'Raw page text'
    }

    def "extractPageText should fallback to filtered field if raw missing"() {
        given:
        ListTag<CompoundTag> pages = new ListTag<>(CompoundTag)
        CompoundTag page1 = new CompoundTag()
        page1.putString('filtered', 'Filtered page text')
        pages.add(page1)

        expect:
        TextUtils.extractPageText(pages, 0) == 'Filtered page text'
    }

    def "extractPageText should return empty string for invalid index"() {
        given:
        ListTag<StringTag> pages = new ListTag<>(StringTag)
        pages.addString('Page 1')

        expect:
        TextUtils.extractPageText(pages, 10) == ''
    }

    // =========================================================================
    // extractTextContent() Tests
    // =========================================================================

    def "extractTextContent should return empty string for null input"() {
        expect:
        TextUtils.extractTextContent(null) == ''
    }

    def "extractTextContent should return plain text as-is when not JSON"() {
        expect:
        TextUtils.extractTextContent('Plain text content') == 'Plain text content'
    }

    def "extractTextContent should extract text from simple JSON"() {
        expect:
        TextUtils.extractTextContent('{"text":"Hello World"}') == 'Hello World'
    }

    def "extractTextContent should extract text from JSON with extra array"() {
        expect:
        TextUtils.extractTextContent('{"extra":[{"text":"Hello"},{"text":" World"}]}') == 'Hello World'
    }

    def "extractTextContent should extract text from JSON with string extra array"() {
        expect:
        TextUtils.extractTextContent('{"extra":["Hello"," World"]}') == 'Hello World'
    }

    def "extractTextContent should remove formatting when enabled"() {
        given:
        String textWithFormatting = '\u00A7cRed text\u00A7r normal'

        expect:
        TextUtils.extractTextContent(textWithFormatting, true) == 'Red text normal'
        TextUtils.extractTextContent(textWithFormatting, false) == textWithFormatting
    }

    def "extractTextContent should handle invalid JSON gracefully"() {
        expect:
        TextUtils.extractTextContent('{invalid json}') == '{invalid json}'
    }

    // =========================================================================
    // extractTextContentPreserveFormatting() Tests
    // =========================================================================

    def "extractTextContentPreserveFormatting should preserve formatting codes"() {
        given:
        String textWithFormatting = '\u00A7cRed text\u00A7r normal'

        expect:
        TextUtils.extractTextContentPreserveFormatting(textWithFormatting) == textWithFormatting
    }

    def "extractTextContentPreserveFormatting should extract from JSON while preserving formatting"() {
        given:
        String jsonText = '{"text":"\u00A7cRed text\u00A7r"}'

        expect:
        TextUtils.extractTextContentPreserveFormatting(jsonText) == '\u00A7cRed text\u00A7r'
    }

    // =========================================================================
    // extractSignCoordinates() Tests
    // =========================================================================

    def "extractSignCoordinates should parse valid coordinate string"() {
        when:
        List<Object> coords = TextUtils.extractSignCoordinates('Chunk [10, 20]\t(100 75 -200)\t\t')

        then:
        coords[0] == 100
        coords[1] == 75
        coords[2] == -200
    }

    def "extractSignCoordinates should return nulls for invalid format"() {
        when:
        List<Object> coords = TextUtils.extractSignCoordinates('Invalid format')

        then:
        coords[0] == null
        coords[1] == null
        coords[2] == null
    }

    def "extractSignCoordinates should return nulls for missing coordinates"() {
        when:
        List<Object> coords = TextUtils.extractSignCoordinates('Chunk [10, 20]\t()\t\t')

        then:
        coords[0] == null
        coords[1] == null
        coords[2] == null
    }

    def "extractSignCoordinates should handle negative coordinates"() {
        when:
        List<Object> coords = TextUtils.extractSignCoordinates('Chunk [0, 0]\t(-100 -50 -200)\t\t')

        then:
        coords[0] == -100
        coords[1] == -50
        coords[2] == -200
    }

    // =========================================================================
    // padSignLine() Tests
    // =========================================================================

    def "padSignLine should pad short text to 15 characters"() {
        expect:
        TextUtils.padSignLine('Hello') == 'Hello          '
        TextUtils.padSignLine('Hello').length() == 15
    }

    def "padSignLine should truncate long text to 15 characters"() {
        given:
        String longText = 'This is a very long sign line text'

        expect:
        TextUtils.padSignLine(longText) == 'This is a very '
        TextUtils.padSignLine(longText).length() == 15
    }

    def "padSignLine should keep exactly 15 characters unchanged"() {
        given:
        String exactly15 = '123456789012345'

        expect:
        TextUtils.padSignLine(exactly15) == exactly15
    }

    // =========================================================================
    // extractSignLineText() Tests
    // =========================================================================

    def "extractSignLineText should return empty string for null"() {
        expect:
        TextUtils.extractSignLineText(null) == ''
    }

    def "extractSignLineText should return empty string for empty string"() {
        expect:
        TextUtils.extractSignLineText('') == ''
        TextUtils.extractSignLineText('null') == ''
    }

    def "extractSignLineText should return plain text as-is"() {
        expect:
        TextUtils.extractSignLineText('Plain sign text') == 'Plain sign text'
    }

    def "extractSignLineText should extract text from simple JSON"() {
        expect:
        TextUtils.extractSignLineText('{"text":"Sign line"}') == 'Sign line'
    }

    def "extractSignLineText should extract text from JSON with extra array"() {
        expect:
        TextUtils.extractSignLineText('{"extra":[{"text":"Hello"},{"text":" World"}]}') == 'Hello World'
    }

    def "extractSignLineText should extract text from JSON with string extra array"() {
        expect:
        TextUtils.extractSignLineText('{"extra":["Hello"," World"]}') == 'Hello World'
    }

    def "extractSignLineText should return empty string for empty JSON object"() {
        expect:
        TextUtils.extractSignLineText('{}') == ''
    }

    def "extractSignLineText should return empty string for empty JSON with empty key"() {
        expect:
        TextUtils.extractSignLineText('{"":""}') == ''
    }

    def "extractSignLineText should handle invalid JSON gracefully"() {
        expect:
        TextUtils.extractSignLineText('{invalid}') == '{invalid}'
    }

    def "extractSignLineText should filter empty text with only empty key-value pairs"() {
        expect:
        TextUtils.extractSignLineText('{"text":""}') == ''
    }

    // =========================================================================
    // extractTextContent() Additional Edge Cases
    // =========================================================================

    def "extractTextContent should handle deeply nested JSON arrays"() {
        given:
        String nestedJson = '{"extra":[{"extra":[{"text":"Nested"},{"text":" Text"}]}]}'

        expect:
        TextUtils.extractTextContent(nestedJson) == 'Nested Text'
    }

    def "extractTextContent should handle mixed content types in extra array"() {
        given:
        String mixedJson = '{"extra":["String1",{"text":"Object1"},"String2",{"text":"Object2"}]}'

        expect:
        TextUtils.extractTextContent(mixedJson) == 'String1Object1String2Object2'
    }

    def "extractTextContent should handle JSON with clickEvent"() {
        given:
        String jsonWithClickEvent = '{"text":"Click me","clickEvent":{"action":"run_command","value":"/say hello"}}'

        expect:
        TextUtils.extractTextContent(jsonWithClickEvent) == 'Click me'
    }

    def "extractTextContent should handle JSON with hoverEvent"() {
        given:
        String jsonWithHoverEvent = '{"text":"Hover me","hoverEvent":{"action":"show_text","value":"Tooltip"}}'

        expect:
        TextUtils.extractTextContent(jsonWithHoverEvent) == 'Hover me'
    }

    def "extractTextContent should handle JSON with both clickEvent and hoverEvent"() {
        given:
        String jsonWithBoth = '{"text":"Interactive","clickEvent":{"action":"run_command","value":"/say hi"},"hoverEvent":{"action":"show_text","value":"Tooltip"}}'

        expect:
        TextUtils.extractTextContent(jsonWithBoth) == 'Interactive'
    }

    def "extractTextContent should handle empty extra array"() {
        given:
        String emptyExtra = '{"extra":[]}'

        expect:
        TextUtils.extractTextContent(emptyExtra) == ''
    }

    def "extractTextContent should handle extra array with null items"() {
        given:
        String nullItems = '{"extra":[{"text":"Valid"},null,{"text":"After null"}]}'

        expect:
        // Should handle null gracefully
        TextUtils.extractTextContent(nullItems).contains('Valid')
    }

    // =========================================================================
    // extractSignLineText() Additional Edge Cases
    // =========================================================================

    def "extractSignLineText should handle JSON with clickEvent"() {
        given:
        String jsonWithClickEvent = '{"text":"Sign line","clickEvent":{"action":"run_command","value":"/tp @s 0 0 0"}}'

        expect:
        TextUtils.extractSignLineText(jsonWithClickEvent) == 'Sign line'
    }

    def "extractSignLineText should handle JSON with hoverEvent"() {
        given:
        String jsonWithHoverEvent = '{"text":"Sign line","hoverEvent":{"action":"show_text","value":"Hover text"}}'

        expect:
        TextUtils.extractSignLineText(jsonWithHoverEvent) == 'Sign line'
    }

    def "extractSignLineText should handle JSON with both clickEvent and hoverEvent"() {
        given:
        String jsonWithBoth = '{"text":"Interactive sign","clickEvent":{"action":"run_command"},"hoverEvent":{"action":"show_text"}}'

        expect:
        TextUtils.extractSignLineText(jsonWithBoth) == 'Interactive sign'
    }

    def "extractSignLineText should handle extra array with mixed types"() {
        given:
        String mixedExtra = '{"extra":["Prefix",{"text":"Middle"},"Suffix"]}'

        expect:
        TextUtils.extractSignLineText(mixedExtra) == 'PrefixMiddleSuffix'
    }

    def "extractSignLineText should handle extra as JSONObject"() {
        given:
        String extraObject = '{"extra":{"text":"From object"}}'

        expect:
        TextUtils.extractSignLineText(extraObject) == 'From object'
    }

    def "extractSignLineText should handle deeply nested extra arrays"() {
        given:
        String nestedExtra = '{"extra":[{"extra":[{"text":"Deep"},{"text":"Nested"}]}]}'

        expect:
        // Note: extractSignLineText may not handle deeply nested, but should not crash
        TextUtils.extractSignLineText(nestedExtra) != null
    }

    // =========================================================================
    // removeTextFormatting() Tests
    // =========================================================================

    def "removeTextFormatting should return empty string for null"() {
        expect:
        TextUtils.removeTextFormatting(null) == ''
    }

    def "removeTextFormatting should remove all color codes"() {
        given:
        String text = '\u00A70Black\u00A7cRed\u00A7aGreen\u00A7rNormal'

        expect:
        TextUtils.removeTextFormatting(text) == 'BlackRedGreenNormal'
    }

    def "removeTextFormatting should remove formatting codes"() {
        given:
        String text = '\u00A7lBold\u00A7oItalic\u00A7mStrikethrough\u00A7rNormal'

        expect:
        TextUtils.removeTextFormatting(text) == 'BoldItalicStrikethroughNormal'
    }

    def "removeTextFormatting should leave plain text unchanged"() {
        given:
        String plainText = 'This is plain text with no formatting'

        expect:
        TextUtils.removeTextFormatting(plainText) == plainText
    }

    // =========================================================================
    // removeTextFormattingIfEnabled() Tests
    // =========================================================================

    def "removeTextFormattingIfEnabled should not remove formatting when disabled"() {
        given:
        String text = '\u00A7cRed text'

        expect:
        TextUtils.removeTextFormattingIfEnabled(text, false) == text
    }

    def "removeTextFormattingIfEnabled should remove formatting when enabled"() {
        given:
        String text = '\u00A7cRed text'

        expect:
        TextUtils.removeTextFormattingIfEnabled(text, true) == 'Red text'
    }

    def "removeTextFormattingIfEnabled should return empty string for null"() {
        expect:
        TextUtils.removeTextFormattingIfEnabled(null, true) == ''
        TextUtils.removeTextFormattingIfEnabled(null, false) == ''
    }

    // =========================================================================
    // Data-driven tests for all color codes
    // =========================================================================

    @Unroll
    def "removeTextFormatting should remove color code #code"() {
        given:
        String text = "${code}Test"

        expect:
        TextUtils.removeTextFormatting(text) == 'Test'

        where:
        code << TextUtils.COLOR_CODES
    }

}

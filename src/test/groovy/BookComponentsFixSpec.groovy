import net.querz.nbt.tag.ListTag
import net.querz.nbt.tag.StringTag
import spock.lang.Specification

/**
 * Test the fix for book components in 1.20.5+ shulker boxes.
 * Issue: Components must wrap book data in written_book_content.
 */
class BookComponentsFixSpec extends Specification {

    def "generateBookComponents should wrap in minecraft:written_book_content for 1.20.5"() {
        given:
        ListTag<StringTag> pages = new ListTag<>(StringTag)
        pages.addString('{"text":"Test Page"}')

        when:
        String components = MinecraftCommands.generateBookComponents('Test Book', 'Author', pages, '1_20_5')

        then:
        components.contains('"minecraft:written_book_content"')
        components.contains('title:"Test Book"')
        components.contains('author:"Author"')
    }

    def "generateBookComponents should wrap in written_book_content for 1.21"() {
        given:
        ListTag<StringTag> pages = new ListTag<>(StringTag)
        pages.addString('{"text":"Test Page"}')

        when:
        String components = MinecraftCommands.generateBookComponents('Test Book', 'Author', pages, '1_21')

        then:
        components.contains('written_book_content:')
        !components.contains('"minecraft:written_book_content"')  // 1.21 doesn't use minecraft: prefix
        components.contains('title:"Test Book"')
        components.contains('author:"Author"')
    }

    def "shulker box command should use wrapped book components for 1.20.5"() {
        given:
        ListTag<StringTag> pages = new ListTag<>(StringTag)
        pages.addString('{"text":"Page 1"}')

        List<Map<String, Object>> books = [[
            title: 'Test Book',
            author: 'Test Author',
            pages: pages,
            generation: 0
        ]]

        when:
        String command = ShulkerBoxGenerator.generateShulkerBoxCommand('Test Author', books, 0, '1_20_5')

        then:
        command.contains('minecraft:container=')
        command.contains('components:{')
        command.contains('"minecraft:written_book_content"')
    }

    def "shulker box command should use wrapped book components for 1.21"() {
        given:
        ListTag<StringTag> pages = new ListTag<>(StringTag)
        pages.addString('{"text":"Page 1"}')

        List<Map<String, Object>> books = [[
            title: 'Test Book',
            author: 'Test Author',
            pages: pages,
            generation: 0
        ]]

        when:
        String command = ShulkerBoxGenerator.generateShulkerBoxCommand('Test Author', books, 0, '1_21')

        then:
        command.contains('container=')
        command.contains('components:{')
        command.contains('written_book_content:')
        !command.contains('"minecraft:written_book_content"')
    }
}

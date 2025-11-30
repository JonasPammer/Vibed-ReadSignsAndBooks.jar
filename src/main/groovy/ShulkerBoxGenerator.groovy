/**
 * Shulker box command generation for organizing books by author.
 *
 * Generates version-specific /give commands for shulker boxes containing books:
 * - 1.13: BlockEntityTag format with single-quoted JSON display names
 * - 1.14: BlockEntityTag format with array-wrapped display names
 * - 1.20.5+: Component format with container arrays
 * - 1.21+: Same as 1.20.5 without minecraft: prefix
 *
 * Each author gets a deterministic color based on their name's hash code.
 * Multiple shulker boxes are generated when an author has more than 27 books.
 *
 * This class is stateless - all methods are static utilities.
 */
import net.querz.nbt.tag.ListTag
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ShulkerBoxGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShulkerBoxGenerator)

    /**
     * 16 Minecraft shulker box colors for deterministic author-to-color mapping.
     */
    static final List<String> SHULKER_COLORS = [
        'white', 'orange', 'magenta', 'light_blue',
        'yellow', 'lime', 'pink', 'gray',
        'light_gray', 'cyan', 'purple', 'blue',
        'brown', 'green', 'red', 'black'
    ]

    /**
     * Map author name to a shulker box color deterministically using hash.
     * Uses author name's hash code to select one of 16 colors consistently.
     *
     * @param author The author name (null/empty becomes 'Unknown')
     * @return A Minecraft color name
     */
    static String getShulkerColorForAuthor(String author) {
        if (!author || author.trim().isEmpty()) {
            author = 'Unknown'
        }
        int colorIndex = Math.abs(author.hashCode() % SHULKER_COLORS.size())
        return SHULKER_COLORS[colorIndex]
    }

    /**
     * Generate a Minecraft /give command for a shulker box containing books.
     * Books are organized by author, with overflow into multiple boxes.
     *
     * @param authorName Author name to display on shulker box
     * @param books List of book maps [{title, author, pages, generation}, ...]
     * @param boxIndex Index for this author's shulker box (0 for first, 1+ for overflow)
     * @param version Minecraft version ('1_13', '1_14', '1_20_5', '1_21')
     * @return The complete /give command
     */
    static String generateShulkerBoxCommand(String authorName, List<Map<String, Object>> books, int boxIndex, String version) {
        String boxColor = getShulkerColorForAuthor(authorName)

        // Cap at 27 books per shulker (slots 0-26)
        List<Map<String, Object>> booksForBox = books.drop(boxIndex * 27).take(27)

        if (booksForBox.isEmpty()) {
            return ''
        }

        String displayName = "Author: ${authorName}${boxIndex > 0 ? " (${boxIndex + 1})" : ''}"

        switch (version) {
            case '1_13':
                return generateShulkerBox_1_13(boxColor, authorName, displayName, booksForBox)
            case '1_14':
            case '1_20':
                return generateShulkerBox_1_14(boxColor, authorName, displayName, booksForBox)
            case '1_20_5':
                return generateShulkerBox_1_20_5(boxColor, authorName, displayName, booksForBox)
            case '1_21':
                return generateShulkerBox_1_21(boxColor, authorName, displayName, booksForBox)
            default:
                return ''
        }
    }

    /**
     * Generate shulker box command for Minecraft 1.13.
     * Format: /give @a color_shulker_box{BlockEntityTag:{Items:[...]},display:{Name:'{"text":"..."}'}}
     */
    static String generateShulkerBox_1_13(String color, String author, String displayName, List<Map<String, Object>> books) {
        StringBuilder itemsStr = new StringBuilder()

        books.eachWithIndex { Map<String, Object> book, int index ->
            if (index > 0) itemsStr.append(',')
            int gen = (book.generation as Integer) ?: 0
            String bookNBT = MinecraftCommands.generateBookNBT(book.title as String, book.author as String, book.pages as ListTag<?>, '1_13', gen)
            itemsStr.append("{Slot:${index},id:written_book,Count:1,tag:${bookNBT}}")
        }

        // 1.13 display name uses single-quoted JSON
        String escapedDisplayName = displayName.replace('\\', '\\\\').replace('"', '\\"')
        String displayJson = '{"text":"' + escapedDisplayName + '","italic":false}'

        return "give @a ${color}_shulker_box{BlockEntityTag:{Items:[${itemsStr}]},display:{Name:'${displayJson}'}}"
    }

    /**
     * Generate shulker box command for Minecraft 1.14.
     * Format: /give @a color_shulker_box{BlockEntityTag:{Items:[...]},display:{Name:'["",{"text":"..."}]'}}
     */
    static String generateShulkerBox_1_14(String color, String author, String displayName, List<Map<String, Object>> books) {
        StringBuilder itemsStr = new StringBuilder()

        books.eachWithIndex { Map<String, Object> book, int index ->
            if (index > 0) itemsStr.append(',')
            int gen = (book.generation as Integer) ?: 0
            String bookNBT = MinecraftCommands.generateBookNBT(book.title as String, book.author as String, book.pages as ListTag<?>, '1_14', gen)
            itemsStr.append("{Slot:${index},id:written_book,Count:1,tag:${bookNBT}}")
        }

        // 1.14 uses single quotes with JSON inside
        String escapedDisplayName = displayName.replace('"', '\\"')
        String displayJson = '["",{"text":"' + escapedDisplayName + '","italic":false}]'

        return "give @a ${color}_shulker_box{BlockEntityTag:{Items:[${itemsStr}]},display:{Name:'${displayJson}'}}"
    }

    /**
     * Generate shulker box command for Minecraft 1.20.5+.
     * Format: /give @a minecraft:color_shulker_box[minecraft:container=[...],item_name='...']
     */
    static String generateShulkerBox_1_20_5(String color, String author, String displayName, List<Map<String, Object>> books) {
        StringBuilder containerStr = new StringBuilder()

        books.eachWithIndex { Map<String, Object> book, int index ->
            if (index > 0) containerStr.append(',')
            int gen = (book.generation as Integer) ?: 0
            String bookComponents = MinecraftCommands.generateBookComponents(book.title as String, book.author as String, book.pages as ListTag<?>, '1_20_5', gen)
            containerStr.append("{slot:${index},item:{id:written_book,count:1,components:${bookComponents}}}")
        }

        // 1.20.5+ uses escaped JSON for item_name
        String escapedDisplayName = displayName.replace('"', '\\"')
        String nameJson = "'[\"\":{\"text\":\"${escapedDisplayName}\",\"italic\":false}]'"

        return "give @a minecraft:${color}_shulker_box[minecraft:container=[${containerStr}],item_name=${nameJson}]"
    }

    /**
     * Generate shulker box command for Minecraft 1.21+.
     * Same as 1.20.5 but without minecraft: prefix for shulker_box.
     */
    static String generateShulkerBox_1_21(String color, String author, String displayName, List<Map<String, Object>> books) {
        StringBuilder containerStr = new StringBuilder()

        books.eachWithIndex { Map<String, Object> book, int index ->
            if (index > 0) containerStr.append(',')
            int gen = (book.generation as Integer) ?: 0
            String bookComponents = MinecraftCommands.generateBookComponents(book.title as String, book.author as String, book.pages as ListTag<?>, '1_21', gen)
            containerStr.append("{slot:${index},item:{id:written_book,count:1,components:${bookComponents}}}")
        }

        String escapedDisplayName = displayName.replace('"', '\\"')
        String nameJson = "'[\"\":{\"text\":\"${escapedDisplayName}\",\"italic\":false}]'"

        return "give @a ${color}_shulker_box[container=[${containerStr}],item_name=${nameJson}]"
    }

}

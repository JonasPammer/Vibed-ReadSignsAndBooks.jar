/**
 * Minecraft command generation utilities for books and signs.
 *
 * Generates version-specific commands for:
 * - Written book /give commands (1.13, 1.14, 1.20, 1.20.5, 1.21)
 * - Sign /setblock commands (1.13, 1.14, 1.20, 1.20.5, 1.21)
 *
 * References:
 * - Sign NBT format: https://minecraft.wiki/w/Sign#Block_data
 * - setblock command: https://minecraft.wiki/w/Commands/setblock
 * - tellraw command: https://minecraft.wiki/w/Commands/tellraw
 * - JSON text format: https://minecraft.wiki/w/Raw_JSON_text_format
 * - clickEvent documentation: https://minecraft.wiki/w/Raw_JSON_text_format#Java_Edition
 *
 * This class is stateless - all methods are static utilities.
 */
import net.querz.nbt.tag.ListTag

class MinecraftCommands {

    /**
     * Escape text for use in Minecraft commands.
     * Different versions require different escaping rules.
     *
     * @param text The text to escape
     * @param version The Minecraft version ('1_13', '1_14', '1_20', '1_20_5', '1_21')
     * @return The escaped text
     */
    static String escapeForMinecraftCommand(String text, String version) {
        if (!text) {
            return ''
        }

        // Remove newlines and carriage returns first
        String escaped = text.replace('\n', ' ').replace('\r', '')

        // Version-specific escaping
        if (version == '1_13' || version == '1_14' || version == '1_20') {
            // 1.13/1.14/1.20 uses double backslash for escape sequences
            escaped = escaped.replace('\\', '\\\\\\\\')  // \ -> \\\\
            escaped = escaped.replace('"', '\\"')
            escaped = escaped.replace("'", "\\'")
            escaped = escaped.replace('\n', '\\\\n')
        } else {
            // 1.20.5+ uses single backslash escaping
            escaped = escaped.replace('\\', '\\\\')
            escaped = escaped.replace('"', '\\"')
            escaped = escaped.replace("'", "\\'")
            escaped = escaped.replace('\n', '\\n')
        }

        return escaped
    }

    /**
     * Convert Minecraft formatting codes (section symbol) to JSON text components.
     * For now, extracts just the text content (formatting is lost).
     *
     * @param text The text with formatting codes
     * @return A JSON text component wrapper
     */
    static String convertFormattingCodesToJson(String text) {
        if (!text || !text.contains('\u00A7')) {
            // Already plain text - wrap in JSON
            return "{\"text\":\"${text}\"}"
        }

        // Remove all section symbol formatting codes
        String plainText = text.replaceAll(/\u00A7./, '')

        // Return as plain text in JSON wrapper
        return "{\"text\":\"${plainText}\"}"
    }

    /**
     * Map Minecraft color code character to color name.
     *
     * @param code The color code character (0-9, a-f, k-o, r)
     * @return The color name or null for formatting codes
     */
    static String mapColorCode(char code) {
        switch (code) {
            case '0': return 'black'
            case '1': return 'dark_blue'
            case '2': return 'dark_green'
            case '3': return 'dark_aqua'
            case '4': return 'dark_red'
            case '5': return 'dark_purple'
            case '6': return 'gold'
            case '7': return 'gray'
            case '8': return 'dark_gray'
            case '9': return 'blue'
            case 'a': return 'green'
            case 'b': return 'aqua'
            case 'c': return 'red'
            case 'd': return 'light_purple'
            case 'e': return 'yellow'
            case 'f': return 'white'
            // Formatting codes (not colors)
            case 'k': return null  // obfuscated
            case 'l': return null  // bold
            case 'm': return null  // strikethrough
            case 'n': return null  // underline
            case 'o': return null  // italic
            case 'r': return null  // reset
            default: return null
        }
    }

    // ========== Book Command Generation ==========

    /**
     * Generate a Minecraft /give command for a written book.
     * Supports versions: 1.13+, 1.14+, 1.20, 1.20.5+, 1.21+
     *
     * @param title The book title
     * @param author The book author
     * @param pages The book pages as NBT ListTag
     * @param version The Minecraft version
     * @param generation The book generation (0=Original, 1=Copy of Original, etc.)
     * @return The complete /give command
     */
    static String generateBookCommand(String title, String author, ListTag<?> pages, String version, int generation = 0) {
        String escapedTitle = escapeForMinecraftCommand(title ?: 'Untitled', version)
        String escapedAuthor = escapeForMinecraftCommand(author ?: 'Unknown', version)

        String pagesStr

        switch (version) {
            case '1_20':
                // 1.20 uses same format as 1.14
                pagesStr = (0..<pages.size()).collect { int i ->
                    String rawText = NbtUtils.getStringAt(pages, i)
                    String jsonArray
                    if (rawText.startsWith('[')) {
                        jsonArray = rawText
                    } else if (rawText.startsWith('{')) {
                        jsonArray = "[${rawText}]"
                    } else {
                        jsonArray = "[\"${rawText}\"]"
                    }
                    String escaped = jsonArray.replace('\\', '\\\\')
                    "'${escaped}'"
                }.join(',')
                return "give @p written_book{title:\"${escapedTitle}\",author:\"${escapedAuthor}\",generation:${generation},pages:[${pagesStr}]}"

            case '1_13':
                // 1.13: /give @p written_book{title:"Title",author:"Author",generation:N,pages:['{"text":"page1"}']}
                pagesStr = (0..<pages.size()).collect { int i ->
                    String rawText = NbtUtils.getStringAt(pages, i)
                    String jsonComponent = rawText.startsWith('{') ? rawText : "{\"text\":\"${rawText}\"}"
                    String escaped = jsonComponent.replace('\\', '\\\\')
                    "'${escaped}'"
                }.join(',')
                return "give @p written_book{title:\"${escapedTitle}\",author:\"${escapedAuthor}\",generation:${generation},pages:[${pagesStr}]}"

            case '1_14':
                // 1.14: /give @p written_book{title:"Title",author:"Author",generation:N,pages:['["page1"]']}
                pagesStr = (0..<pages.size()).collect { int i ->
                    String rawText = NbtUtils.getStringAt(pages, i)
                    String jsonArray
                    if (rawText.startsWith('[')) {
                        jsonArray = rawText
                    } else if (rawText.startsWith('{')) {
                        jsonArray = "[${rawText}]"
                    } else {
                        jsonArray = "[\"${rawText}\"]"
                    }
                    String escaped = jsonArray.replace('\\', '\\\\')
                    "'${escaped}'"
                }.join(',')
                return "give @p written_book{title:\"${escapedTitle}\",author:\"${escapedAuthor}\",generation:${generation},pages:[${pagesStr}]}"

            case '1_20_5':
                // 1.20.5: /give @p written_book[minecraft:written_book_content={...}]
                pagesStr = (0..<pages.size()).collect { int i ->
                    String rawText = NbtUtils.getStringAt(pages, i)
                    String jsonComponent = rawText.startsWith('{') ? rawText : "{\"text\":\"${rawText}\"}"
                    String escaped = jsonComponent.replace('\\', '\\\\').replace('"', '\\"').replace('\n', '\\n').replace('\r', '\\r')
                    "\"${escaped}\""
                }.join(',')
                return "give @p written_book[minecraft:written_book_content={title:\"${escapedTitle}\",author:\"${escapedAuthor}\",generation:${generation},pages:[${pagesStr}]}]"

            case '1_21':
                // 1.21: /give @p written_book[written_book_content={...}] (no minecraft: prefix)
                pagesStr = (0..<pages.size()).collect { int i ->
                    String rawText = NbtUtils.getStringAt(pages, i)
                    String jsonComponent = rawText.startsWith('{') ? rawText : "{\"text\":\"${rawText}\"}"
                    String escaped = jsonComponent.replace('\\', '\\\\').replace('"', '\\"').replace('\n', '\\n').replace('\r', '\\r')
                    "\"${escaped}\""
                }.join(',')
                return "give @p written_book[written_book_content={title:\"${escapedTitle}\",author:\"${escapedAuthor}\",generation:${generation},pages:[${pagesStr}]}]"

            default:
                return ''
        }
    }

    /**
     * Generate book NBT tag (pre-1.20.5 format).
     * Used for creating book entries in shulker boxes for versions 1.13-1.20.4.
     *
     * @param title The book title
     * @param author The book author
     * @param pages The book pages as NBT ListTag
     * @param version The Minecraft version
     * @param generation The book generation
     * @return The NBT tag string
     */
    static String generateBookNBT(String title, String author, ListTag<?> pages, String version, int generation = 0) {
        String escapedTitle = escapeForMinecraftCommand(title ?: 'Untitled', version)
        String escapedAuthor = escapeForMinecraftCommand(author ?: 'Unknown', version)

        String pagesStr = (0..<pages.size()).collect { int i ->
            String rawText = NbtUtils.getStringAt(pages, i)
            String jsonComponent = rawText.startsWith('{') ? rawText : "{\"text\":\"${rawText}\"}"
            String escaped = jsonComponent.replace('\\', '\\\\').replace("'", "\\'").replace('\n', '\\n').replace('\r', '\\r')
            "'${escaped}'"
        }.join(',')

        return "{title:\"${escapedTitle}\",author:\"${escapedAuthor}\",generation:${generation},pages:[${pagesStr}]}"
    }

    /**
     * Generate book components (1.20.5+ format).
     * Used for creating book entries in shulker boxes for versions 1.20.5+.
     *
     * @param title The book title
     * @param author The book author
     * @param pages The book pages as NBT ListTag
     * @param version The Minecraft version
     * @param generation The book generation
     * @return The components string
     */
    static String generateBookComponents(String title, String author, ListTag<?> pages, String version, int generation = 0) {
        String escapedTitle = escapeForMinecraftCommand(title ?: 'Untitled', version)
        String escapedAuthor = escapeForMinecraftCommand(author ?: 'Unknown', version)

        String pagesStr = (0..<pages.size()).collect { int i ->
            String rawText = NbtUtils.getStringAt(pages, i)
            String jsonComponent = rawText.startsWith('{') ? rawText : "{\"text\":\"${rawText}\"}"
            String escaped = jsonComponent.replace('\\', '\\\\').replace('"', '\\"').replace('\n', '\\n').replace('\r', '\\r')
            "\"${escaped}\""
        }.join(',')

        return "{title:\"${escapedTitle}\",author:\"${escapedAuthor}\",generation:${generation},pages:[${pagesStr}]}"
    }

    // ========== Sign Command Generation ==========

    /**
     * Generate a Minecraft setblock command for a sign.
     * Dispatches to version-specific generators.
     *
     * @param frontLines List of front text lines (4 lines max)
     * @param position Map containing x, z coordinates and original position info
     * @param version The Minecraft version
     * @param backLines Optional list of back text lines (1.20+ only)
     * @return The complete /setblock command
     */
    static String generateSignCommand(List<String> frontLines, Map<String, Object> position, String version, List<String> backLines = null) {
        if (!frontLines || frontLines.size() == 0) {
            return ''
        }

        int x = position.x as int
        int z = position.z as int

        switch (version) {
            case '1_13':
                return generateSignCommand_1_13(frontLines, x, z, position)
            case '1_14':
                return generateSignCommand_1_14(frontLines, x, z, position)
            case '1_20':
                return generateSignCommand_1_20(frontLines, x, z, position, backLines)
            case '1_20_5':
                return generateSignCommand_1_20_5(frontLines, x, z, position, backLines)
            case '1_21':
                return generateSignCommand_1_21(frontLines, x, z, position, backLines)
            default:
                return ''
        }
    }

    /**
     * Generate sign for Minecraft 1.13-1.19 (old format with Text1-Text4).
     * Includes clickEvent on first line for interactive teleport.
     */
    static String generateSignCommand_1_13(List<String> frontLines, int x, int z, Map<String, Object> position) {
        Integer origX = position.originalX as Integer
        Integer origY = position.originalY as Integer
        Integer origZ = position.originalZ as Integer

        // Build tellraw command for click action
        String tellrawCmd = origX != null && origY != null && origZ != null
            ? "/tellraw @s {\\\"text\\\":\\\"Sign from (${origX} ${origY} ${origZ})\\\",\\\"color\\\":\\\"gray\\\",\\\"clickEvent\\\":{\\\"action\\\":\\\"run_command\\\",\\\"value\\\":\\\"/tp @s ${origX} ${origY} ${origZ}\\\"}}"
            : ''

        // Generate Text1-Text4 with clickEvent on first line
        String textTags = (0..3).collect { int i ->
            String line = i < frontLines.size() ? frontLines[i] : ''
            String escaped = escapeForMinecraftCommand(line, '1_13')

            if (i == 0 && line && tellrawCmd) {
                // First line with clickEvent
                "Text${i + 1}:'{\"text\":\"${escaped}\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"${tellrawCmd}\"}}'"
            } else {
                "Text${i + 1}:'{\"text\":\"${escaped}\"}'"
            }
        }.join(',')

        return "setblock ~${x} ~ ~${z} oak_sign{${textTags}} replace"
    }

    /**
     * Generate sign for Minecraft 1.14-1.19 (array format).
     * Includes clickEvent on first line for interactive teleport.
     */
    static String generateSignCommand_1_14(List<String> frontLines, int x, int z, Map<String, Object> position) {
        Integer origX = position.originalX as Integer
        Integer origY = position.originalY as Integer
        Integer origZ = position.originalZ as Integer

        String tellrawCmd = origX != null && origY != null && origZ != null
            ? "/tellraw @s {\\\"text\\\":\\\"Sign from (${origX} ${origY} ${origZ})\\\",\\\"color\\\":\\\"gray\\\",\\\"clickEvent\\\":{\\\"action\\\":\\\"run_command\\\",\\\"value\\\":\\\"/tp @s ${origX} ${origY} ${origZ}\\\"}}"
            : ''

        String textTags = (0..3).collect { int i ->
            String line = i < frontLines.size() ? frontLines[i] : ''
            String escaped = escapeForMinecraftCommand(line, '1_14')

            if (i == 0 && line && tellrawCmd) {
                "Text${i + 1}:'[\"\",{\"text\":\"${escaped}\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"${tellrawCmd}\"}}]'"
            } else {
                "Text${i + 1}:'[\"\",{\"text\":\"${escaped}\"}]'"
            }
        }.join(',')

        return "setblock ~${x} ~ ~${z} oak_sign{${textTags}} replace"
    }

    /**
     * Generate sign for Minecraft 1.20-1.20.4 (front_text/back_text format).
     * Includes clickEvent on first line for interactive teleport.
     */
    static String generateSignCommand_1_20(List<String> frontLines, int x, int z, Map<String, Object> position, List<String> backLines = null) {
        Integer origX = position.originalX as Integer
        Integer origY = position.originalY as Integer
        Integer origZ = position.originalZ as Integer

        String tellrawCmd = origX != null && origY != null && origZ != null
            ? "/tellraw @s {\\\"text\\\":\\\"Sign from (${origX} ${origY} ${origZ})\\\",\\\"color\\\":\\\"gray\\\",\\\"clickEvent\\\":{\\\"action\\\":\\\"run_command\\\",\\\"value\\\":\\\"/tp @s ${origX} ${origY} ${origZ}\\\"}}"
            : ''

        // Generate front messages with clickEvent on first line
        String frontMessages = (0..3).collect { int i ->
            String line = i < frontLines.size() ? frontLines[i] : ''
            String escaped = escapeForMinecraftCommand(line, '1_20')

            if (i == 0 && line && tellrawCmd) {
                "'[\"\",{\"text\":\"${escaped}\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"${tellrawCmd}\"}}]'"
            } else {
                "'[\"\",{\"text\":\"${escaped}\"}]'"
            }
        }.join(',')

        String backMessages
        if (backLines && backLines.any { String line -> line }) {
            backMessages = (0..3).collect { int i ->
                String line = i < backLines.size() ? backLines[i] : ''
                String escaped = escapeForMinecraftCommand(line, '1_20')
                "'[\"\",{\"text\":\"${escaped}\"}]'"
            }.join(',')
        } else {
            backMessages = "'[\"\",{\"text\":\"\"}]', '[\"\",{\"text\":\"\"}]', '[\"\",{\"text\":\"\"}]', '[\"\",{\"text\":\"\"}]'"
    }

        return "setblock ~${x} ~ ~${z} oak_sign[rotation=0,waterlogged=false]{front_text:{messages:[${frontMessages}],has_glowing_text:0},back_text:{messages:[${backMessages}],has_glowing_text:0},is_waxed:0} replace"
}

    /**
     * Generate sign for Minecraft 1.20.5+ (component format).
     * Includes clickEvent on first line for interactive teleport.
     */
    static String generateSignCommand_1_20_5(List<String> frontLines, int x, int z, Map<String, Object> position, List<String> backLines = null) {
        Integer origX = position.originalX as Integer
        Integer origY = position.originalY as Integer
        Integer origZ = position.originalZ as Integer

        String tellrawCmd = origX != null && origY != null && origZ != null
            ? "/tellraw @s {\"text\":\"Sign from (${origX} ${origY} ${origZ})\",\"color\":\"gray\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/tp @s ${origX} ${origY} ${origZ}\"}}"
            : ''

        String frontMessages = (0..3).collect { int i ->
            String line = i < frontLines.size() ? frontLines[i] : ''
            String escaped = escapeForMinecraftCommand(line, '1_20_5')

            if (i == 0 && line && tellrawCmd) {
                '[[{"text":"' + escaped + '","clickEvent":{"action":"run_command","value":"' + tellrawCmd + '"}}]]'
            } else {
                '[[{"text":"' + escaped + '"}]]'
            }
        }.join(',')

        String backMessages
        if (backLines && backLines.any { String line -> line }) {
            backMessages = (0..3).collect { int i ->
                String line = i < backLines.size() ? backLines[i] : ''
                String escaped = escapeForMinecraftCommand(line, '1_20_5')
                '[[{"text":"' + escaped + '"}]]'
            }.join(',')
        } else {
            backMessages = '[[{"text":""}]], [[{"text":""}]], [[{"text":""}]], [[{"text":""}]]'
    }

        return "setblock ~${x} ~ ~${z} oak_sign[rotation=0,waterlogged=false]{front_text:{messages:[${frontMessages}],has_glowing_text:0},back_text:{messages:[${backMessages}],has_glowing_text:0},is_waxed:0} replace"
    }

    /**
     * Generate sign for Minecraft 1.21+ (same as 1.20.5).
     */
    static String generateSignCommand_1_21(List<String> frontLines, int x, int z, Map<String, Object> position, List<String> backLines = null) {
        return generateSignCommand_1_20_5(frontLines, x, z, position, backLines)
    }

}

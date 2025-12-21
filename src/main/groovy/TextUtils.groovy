/**
 * Text extraction and formatting utilities for Minecraft content processing.
 *
 * Provides utilities for:
 * - Extracting text content from Minecraft JSON text components
 * - Removing Minecraft formatting codes (section symbol codes)
 * - Sanitizing filenames for safe file system operations
 * - Extracting metadata from book/sign info strings
 * - Parsing sign line text from various formats
 *
 * This class is mostly stateless - methods that need configuration receive it as parameters.
 */
import groovy.json.JsonSlurper
import net.querz.nbt.tag.CompoundTag
import net.querz.nbt.tag.ListTag
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class TextUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(TextUtils)
    private static final JsonSlurper JSON_SLURPER = new JsonSlurper()

    /**
     * Minecraft color and formatting codes (section symbol followed by code character).
     * Used for optional removal of formatting from output text.
     */
    static final String[] COLOR_CODES = [
        '\u00A70', '\u00A71', '\u00A72', '\u00A73', '\u00A74', '\u00A75',
        '\u00A76', '\u00A77', '\u00A78', '\u00A79', '\u00A7a', '\u00A7b',
        '\u00A7c', '\u00A7d', '\u00A7e', '\u00A7f', '\u00A7k', '\u00A7l',
        '\u00A7m', '\u00A7n', '\u00A7o', '\u00A7r'
    ]

    /**
     * Sanitize a string for use as a filename.
     * Replaces invalid characters and limits length.
     *
     * @param name The string to sanitize
     * @return A safe filename string
     */
    static String sanitizeFilename(String name) {
        if (!name) {
            return 'unnamed'
        }
        return name.replaceAll(/[\\/:*?<>|]/, '_').take(200)
    }

    /**
     * Extract player name from a bookInfo string.
     * Example: "Inventory of player abc123-def456.dat" -> "abc123-def456"
     *
     * @param bookInfo The full book info string
     * @param prefix The prefix to search for (e.g., "player ")
     * @return The extracted player name or 'unknown'
     */
    static String extractPlayerName(String bookInfo, String prefix) {
        if (bookInfo == null) {
            return 'unknown'
        }
        try {
            int startIndex = bookInfo.indexOf(prefix)
            if (startIndex >= 0) {
                String remainder = bookInfo.substring(startIndex + prefix.length())
                // Remove .dat extension if present
                if (remainder.endsWith('.dat')) {
                    remainder = remainder.substring(0, remainder.length() - 4)
                }
                return remainder.trim()
            }
        } catch (StringIndexOutOfBoundsException e) {
            // Catch string index errors from substring operations
            LOGGER.debug("Failed to extract player name from: ${bookInfo}", e)
        }
        return 'unknown'
    }

    /**
     * Extract page text from a book page ListTag (handles both string and compound formats).
     *
     * PAGE FORMAT CHANGES (1.20.5):
     * - Pre-1.20.5: Pages are string list
     * - 1.20.5+: Pages are compound list with "raw"/"filtered" fields
     *
     * @param pages The pages ListTag
     * @param index The page index to extract
     * @return The page text content
     */
    static String extractPageText(ListTag<?> pages, int index) {
        if (NbtUtils.isStringList(pages)) {
            return NbtUtils.getStringAt(pages, index)
        } else if (NbtUtils.isCompoundList(pages)) {
            CompoundTag pageCompound = NbtUtils.getCompoundAt(pages, index)
            return pageCompound.getString('raw') ?: pageCompound.getString('filtered') ?: ''
        }
        return ''
    }

    /**
     * Extract text content from page text, optionally removing formatting codes.
     * Parses JSON text components and extracts the plain text.
     * Handles nested extra arrays and null items gracefully.
     *
     * @param pageText The page text (may be JSON or plain text)
     * @param removeFormatting If true, remove Minecraft formatting codes
     * @return The extracted plain text
     */
    static String extractTextContent(String pageText, boolean removeFormatting = false) {
        if (!pageText) {
            return ''
        }

        if (!pageText.startsWith('{')) {
            return removeTextFormattingIfEnabled(pageText, removeFormatting)
        }

        try {
            Object pageJSON = JSON_SLURPER.parseText(pageText)

            // Check if 'extra' key exists (even if empty array)
            if (pageJSON instanceof Map && pageJSON.containsKey('extra')) {
                Object extra = pageJSON.get('extra')

                // Handle extra as a list/array
                if (extra instanceof List) {
                    List extraList = extra as List
                    // Return empty string for empty extra array
                    if (extraList.empty) {
                        return ''
                    }
                    return extraList.collect { Object item ->
                        extractTextFromItem(item, removeFormatting)
                    }.join('')
                }
                // extra is a single object, recursively extract
                return extractTextFromItem(extra, removeFormatting)
            } else if (pageJSON.text) {
                return removeTextFormattingIfEnabled(pageJSON.text, removeFormatting)
            }
        } catch (groovy.json.JsonException e) {
            LOGGER.debug("Page text is not valid JSON, returning as-is: ${e.message}")
        }

        return removeTextFormattingIfEnabled(pageText, removeFormatting)
    }

    /**
     * Helper method to extract text from a single item in a JSON structure.
     * Handles strings, objects with nested extra arrays, and null values.
     *
     * @param item The item to extract text from
     * @param removeFormatting If true, remove formatting codes
     * @return The extracted text
     */
    private static String extractTextFromItem(Object item, boolean removeFormatting) {
        switch (item) {
            case null:
                return ''
            case String:
                return removeTextFormattingIfEnabled((String) item, removeFormatting)
            case Map:
                Map itemMap = item as Map
                // Recursively handle nested extra arrays
                if (itemMap.containsKey('extra')) {
                    Object extra = itemMap.get('extra')
                    if (extra instanceof List) {
                        List extraList = extra as List
                        if (extraList.empty) {
                            return ''
                        }
                        return extraList.collect { Object nestedItem ->
                            extractTextFromItem(nestedItem, removeFormatting)
                        }.join('')
                    }
                    return extractTextFromItem(extra, removeFormatting)
                }
                if (itemMap.containsKey('text')) {
                    return removeTextFormattingIfEnabled(itemMap.get('text') as String ?: '', removeFormatting)
                }
                return ''
            default:
                return ''
        }
    }

    /**
     * Extract text content from page text while preserving formatting codes.
     * Used for .stendhal files where formatting is retained.
     * Handles nested extra arrays and null items gracefully.
     *
     * @param pageText The page text (may be JSON or plain text)
     * @return The extracted text with formatting preserved
     */
    static String extractTextContentPreserveFormatting(String pageText) {
        if (!pageText) {
            return ''
        }

        if (!pageText.startsWith('{')) {
            return pageText
        }

        try {
            Object pageJSON = JSON_SLURPER.parseText(pageText)

            // Check if 'extra' key exists (even if empty array)
            if (pageJSON instanceof Map && pageJSON.containsKey('extra')) {
                Object extra = pageJSON.get('extra')

                if (extra instanceof List) {
                    List extraList = extra as List
                    if (extraList.empty) {
                        return ''
                    }
                    return extraList.collect { Object item ->
                        extractTextFromItemPreserveFormatting(item)
                    }.join('')
                }
                return extractTextFromItemPreserveFormatting(extra)
            } else if (pageJSON.text) {
                return pageJSON.text
            }
        } catch (groovy.json.JsonException e) {
            LOGGER.debug("Page text is not valid JSON, returning as-is: ${e.message}")
        }

        return pageText
    }

    /**
     * Helper method to extract text from a single item while preserving formatting.
     * Handles strings, objects with nested extra arrays, and null values.
     *
     * @param item The item to extract text from
     * @return The extracted text with formatting preserved
     */
    private static String extractTextFromItemPreserveFormatting(Object item) {
        switch (item) {
            case null:
                return ''
            case String:
                return (String) item
            case Map:
                Map itemMap = item as Map
                // Recursively handle nested extra arrays
                if (itemMap.containsKey('extra')) {
                    Object extra = itemMap.get('extra')
                    if (extra instanceof List) {
                        List extraList = extra as List
                        if (extraList.empty) {
                            return ''
                        }
                        return extraList.collect { Object nestedItem ->
                            extractTextFromItemPreserveFormatting(nestedItem)
                        }.join('')
                    }
                    return extractTextFromItemPreserveFormatting(extra)
                }
                if (itemMap.containsKey('text')) {
                    return itemMap.get('text') as String ?: ''
                }
                return ''
            default:
                return ''
        }
    }

    /**
     * Extract coordinates from signInfo string.
     * signInfo format: "Chunk [x, z]\t(X Y Z)\t\t"
     *
     * @param signInfo The sign info string
     * @return [x, y, z] coordinates or [null, null, null] if parsing fails
     */
    static List<Object> extractSignCoordinates(String signInfo) {
        Integer x = null
        Integer y = null
        Integer z = null

        try {
            // Extract coordinates from format: "Chunk [x, z]\t(X Y Z)\t\t"
            int startParen = signInfo.indexOf('(')
            int endParen = signInfo.indexOf(')')
            if (startParen >= 0 && endParen > startParen) {
                String coords = signInfo.substring(startParen + 1, endParen)
                String[] parts = coords.split(' ')
                if (parts.length >= 3) {
                    x = Integer.parseInt(parts[0])
                    y = Integer.parseInt(parts[1])
                    z = Integer.parseInt(parts[2])
                }
            }
        } catch (StringIndexOutOfBoundsException | NumberFormatException e) {
            // Catch string index errors and number parsing errors from coordinate extraction
            LOGGER.debug("Failed to parse sign coordinates from: ${signInfo}")
        }

        return [x, y, z]
    }

    /**
     * Pad sign line to exactly 15 characters (Minecraft's max sign line width).
     *
     * @param text The text to pad
     * @return Text padded/truncated to 15 characters
     */
    static String padSignLine(String text) {
        if (text.length() >= 15) {
            return text.substring(0, 15)
        }
        return text.padRight(15, ' ')
    }

    /**
     * Extract text from a sign line (handles JSON text components).
     *
     * @param line The sign line text (may be JSON or plain text)
     * @return The extracted plain text
     */
    static String extractSignLineText(String line) {
        if (!line || line == '' || line == 'null') {
            return ''
        }
        if (!line.startsWith('{')) {
            return line
        }

        try {
            JSONObject json = new JSONObject(line)

            if (json.has('extra')) {
                Object extra = json.get('extra')
                if (extra instanceof JSONArray) {
                    StringBuilder sb = new StringBuilder()
                    JSONArray extraArray = (JSONArray) extra
                    for (int i = 0; i < extraArray.length(); i++) {
                        Object item = extraArray.get(i)
                        if (item instanceof String) {
                            sb.append(item)
                        } else if (item instanceof JSONObject) {
                            JSONObject temp = (JSONObject) item
                            if (temp.has('text')) {
                                sb.append(temp.get('text'))
                            }
                        }
                    }
                    return sb.toString()
                } else if (extra instanceof JSONObject) {
                    JSONObject extraObj = (JSONObject) extra
                    if (extraObj.has('text')) {
                        return String.valueOf(extraObj.get('text'))
                    }
                }
            } else if (json.has('text')) {
                String text = String.valueOf(json.get('text'))
                // Filter out empty text with only empty key-value pairs
                if (text == '' && json.length() == 1) {
                    return ''
                }
                return text
            }
        } catch (JSONException e) {
            LOGGER.debug("Sign line is not valid JSON: ${e.message}")
        }

        // Filter out empty JSON objects like {"":""} or {}
        if (line == '{}' || line == '{"":""}') {
            return ''
        }

        return line
    }

    /**
     * Remove Minecraft text formatting codes (section symbol codes) from text.
     *
     * @param text The text to process
     * @param removeFormatting If true, remove formatting codes; if false, return unchanged
     * @return The processed text
     */
    static String removeTextFormattingIfEnabled(String text, boolean removeFormatting) {
        if (!text) {
            return ''
        }
        if (!removeFormatting) {
            return text
        }
        return COLOR_CODES.inject(text) { String result, String code -> result.replace(code, '') }
    }

    /**
     * Remove Minecraft text formatting codes unconditionally.
     *
     * @param text The text to process
     * @return The text with all formatting codes removed
     */
    static String removeTextFormatting(String text) {
        if (!text) {
            return ''
        }
        return COLOR_CODES.inject(text) { String result, String code -> result.replace(code, '') }
    }

}

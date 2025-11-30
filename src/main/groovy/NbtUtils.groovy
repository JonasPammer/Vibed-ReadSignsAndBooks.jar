/**
 * NBT (Named Binary Tag) utility methods for Minecraft world data processing.
 *
 * Provides null-safe wrappers around the Querz NBT library for:
 * - Reading compressed NBT files
 * - Safe nested tag access with default values
 * - Format detection for different Minecraft versions
 * - NBT to JSON conversion for text components
 *
 * This class is stateless - all methods are static utilities.
 */
import net.querz.nbt.io.NBTUtil
import net.querz.nbt.tag.CompoundTag
import net.querz.nbt.tag.ListTag
import net.querz.nbt.tag.NumberTag
import net.querz.nbt.tag.StringTag
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class NbtUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(NbtUtils)

    /**
     * Read a compressed NBT file and return the root CompoundTag.
     *
     * @param file The .dat or .nbt file to read
     * @return The root CompoundTag from the file
     */
    static CompoundTag readCompressedNBT(File file) {
        net.querz.nbt.io.NamedTag namedTag = NBTUtil.read(file)
        return (CompoundTag) namedTag.tag
    }

    /**
     * Check if a CompoundTag contains a key.
     *
     * @param tag The tag to check (can be null)
     * @param key The key to look for
     * @return true if the tag is non-null and contains the key
     */
    static boolean hasKey(CompoundTag tag, String key) {
        return tag != null && tag.containsKey(key)
    }

    /**
     * Get a nested CompoundTag, returning empty tag if not found.
     *
     * @param tag Parent tag (can be null)
     * @param key Key of the nested tag
     * @return The nested CompoundTag or an empty CompoundTag
     */
    static CompoundTag getCompoundTag(CompoundTag tag, String key) {
        if (!tag) {
            return new CompoundTag()
        }
        return tag.getCompoundTag(key) ?: new CompoundTag()
    }

    /**
     * Get a ListTag of CompoundTags, returning empty list if not found.
     *
     * @param tag Parent tag (can be null)
     * @param key Key of the list
     * @return The ListTag<CompoundTag> or an empty list
     */
    static ListTag<CompoundTag> getCompoundTagList(CompoundTag tag, String key) {
        if (!tag || !tag.containsKey(key)) {
            return new ListTag<>(CompoundTag)
        }
        ListTag<?> list = tag.getListTag(key)
        if (!list || list.size() == 0) {
            return new ListTag<>(CompoundTag)
        }
        try {
            return list.asCompoundTagList()
        } catch (ClassCastException e) {
            return new ListTag<>(CompoundTag)
        }
    }

    /**
     * Get a ListTag of any type, returning empty list if not found.
     *
     * @param tag Parent tag (can be null)
     * @param key Key of the list
     * @return The ListTag or an empty ListTag
     */
    static ListTag<?> getListTag(CompoundTag tag, String key) {
        if (!tag || !tag.containsKey(key)) {
            return ListTag.createUnchecked(Object)
        }
        return tag.getListTag(key) ?: ListTag.createUnchecked(Object)
    }

    /**
     * Get a double value from a ListTag at the specified index.
     *
     * @param list The ListTag (can be null)
     * @param index The index to read from
     * @return The double value or 0.0 if not found/invalid
     */
    static double getDoubleAt(ListTag<?> list, int index) {
        if (!list || index < 0 || index >= list.size()) {
            return 0.0
        }

        try {
            net.querz.nbt.tag.Tag<?> tag = list.get(index)
            if (tag == null) {
                return 0.0
            }
            switch (tag) {
                case NumberTag:
                    return ((NumberTag<?>) tag).asDouble()
                case StringTag:
                    return Double.parseDouble(((StringTag) tag).value)
                default:
                    return 0.0
            }
        } catch (NumberFormatException e) {
            LOGGER.debug("Invalid number format in NBT tag, returning default: ${e.message}")
            return 0.0
        }
    }

    /**
     * Get a string value from a ListTag at the specified index.
     * Handles StringTag, CompoundTag (with 'raw' field for 1.20.5+), and other types.
     *
     * @param list The ListTag (can be null)
     * @param index The index to read from
     * @return The string value or empty string if not found
     */
    static String getStringAt(ListTag<?> list, int index) {
        if (!list || index < 0 || index >= list.size()) {
            return ''
        }

        try {
            net.querz.nbt.tag.Tag<?> tag = list.get(index)
            if (tag == null) {
                return ''
            }
            switch (tag) {
                case StringTag:
                    return ((StringTag) tag).value
                case CompoundTag:
                    // For 1.20.5+ page CompoundTags, extract the 'raw' field directly
                    // which already contains the properly formatted JSON text components
                    CompoundTag compound = (CompoundTag) tag
                    if (compound.containsKey('raw')) {
                        net.querz.nbt.tag.Tag<?> rawTag = compound.get('raw')
                        if (rawTag instanceof StringTag) {
                            return ((StringTag) rawTag).value
                        }
                    }
                    // Fallback: convert entire CompoundTag to JSON
                    return convertNbtToJson((CompoundTag) tag).toString()
                default:
                    return tag.valueToString()
            }
        } catch (ClassCastException e) {
            LOGGER.debug("Error casting NBT tag at index ${index}: ${e.message}")
            return ''
        }
    }

    /**
     * Get a string value from a CompoundTag by key.
     *
     * @param tag The CompoundTag (can be null)
     * @param key The key to look up
     * @return The string value or empty string if not found
     */
    static String getStringFrom(CompoundTag tag, String key) {
        if (!tag || !tag.containsKey(key)) {
            return ''
        }

        try {
            net.querz.nbt.tag.Tag<?> value = tag.get(key)
            if (value == null) {
                return ''
            }

            LOGGER.debug("getStringFrom() - key: ${key}, value type: ${value.getClass().name}, value: ${value}")

            // Check if it's a StringTag and get the value
            if (value instanceof StringTag) {
                String result = ((StringTag) value).value
                LOGGER.debug("getStringFrom() - returning: ${result}")
                return result
            }

            LOGGER.debug('getStringFrom() - value is not a StringTag, returning empty string')
            return ''
        } catch (ClassCastException e) {
            LOGGER.error("getStringFrom() - error casting value for key '${key}': ${e.message}", e)
            return ''
        }
    }

    /**
     * Get a CompoundTag from a ListTag at the specified index.
     *
     * @param list The ListTag (can be null)
     * @param index The index to read from
     * @return The CompoundTag or an empty CompoundTag if not found
     */
    static CompoundTag getCompoundAt(ListTag<?> list, int index) {
        if (!list || index < 0 || index >= list.size()) {
            return new CompoundTag()
        }

        net.querz.nbt.tag.Tag<?> tag = list.get(index)
        if (tag instanceof CompoundTag) {
            return (CompoundTag) tag
        }
        return new CompoundTag()
    }

    /**
     * Check if a ListTag contains StringTags.
     *
     * @param list The ListTag to check
     * @return true if the list is non-empty and contains StringTags
     */
    static boolean isStringList(ListTag<?> list) {
        return list && list.size() > 0 && list.typeClass == StringTag
    }

    /**
     * Check if a ListTag contains CompoundTags.
     *
     * @param list The ListTag to check
     * @return true if the list is non-empty and contains CompoundTags
     */
    static boolean isCompoundList(ListTag<?> list) {
        return list && list.size() > 0 && list.typeClass == CompoundTag
    }

    /**
     * Converts a CompoundTag (NBT) to a JSONObject.
     * This handles the NBT -> JSON conversion for Minecraft text components.
     *
     * @param tag The CompoundTag to convert
     * @return A JSONObject representation of the tag
     */
    static JSONObject convertNbtToJson(CompoundTag tag) {
        JSONObject json = new JSONObject()

        tag.forEach { String key, net.querz.nbt.tag.Tag<?> value ->
            switch (value) {
                case StringTag:
                    json.put(key, ((StringTag) value).value)
                    break
                case NumberTag:
                    json.put(key, ((NumberTag) value).asNumber())
                    break
                case CompoundTag:
                    json.put(key, convertNbtToJson((CompoundTag) value))
                    break
                case ListTag:
                    json.put(key, convertNbtListToJsonArray((ListTag<?>) value))
                    break
                default:
                    json.put(key, value.value)
            }
        }

        return json
    }

    /**
     * Converts a ListTag (NBT) to a JSONArray.
     *
     * @param list The ListTag to convert
     * @return A JSONArray representation of the list
     */
    static JSONArray convertNbtListToJsonArray(ListTag<?> list) {
        JSONArray array = new JSONArray()

        for (int i = 0; i < list.size(); i++) {
            net.querz.nbt.tag.Tag<?> tag = list.get(i)

            switch (tag) {
                case StringTag:
                    array.put(((StringTag) tag).value)
                    break
                case NumberTag:
                    array.put(((NumberTag) tag).asNumber())
                    break
                case CompoundTag:
                    array.put(convertNbtToJson((CompoundTag) tag))
                    break
                case ListTag:
                    array.put(convertNbtListToJsonArray((ListTag<?>) tag))
                    break
                default:
                    array.put(tag.value)
            }
        }

        return array
    }

}

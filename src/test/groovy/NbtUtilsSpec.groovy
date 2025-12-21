import net.querz.nbt.tag.CompoundTag
import net.querz.nbt.tag.DoubleTag
import net.querz.nbt.tag.IntTag
import net.querz.nbt.tag.ListTag
import net.querz.nbt.tag.StringTag
import org.json.JSONArray
import org.json.JSONObject
import spock.lang.Specification

/**
 * Unit tests for NbtUtils utility class.
 * Tests null-safe NBT tag access, type detection, and NBT to JSON conversion.
 */
class NbtUtilsSpec extends Specification {

    // =========================================================================
    // hasKey() Tests
    // =========================================================================

    def "hasKey should return false for null tag"() {
        expect:
        !NbtUtils.hasKey(null, 'key')
    }

    def "hasKey should return false when key not present"() {
        given:
        CompoundTag tag = new CompoundTag()
        tag.putString('other', 'value')

        expect:
        !NbtUtils.hasKey(tag, 'missing')
    }

    def "hasKey should return true when key exists"() {
        given:
        CompoundTag tag = new CompoundTag()
        tag.putString('test', 'value')

        expect:
        NbtUtils.hasKey(tag, 'test')
    }

    // =========================================================================
    // getCompoundTag() Tests
    // =========================================================================

    def "getCompoundTag should return empty tag for null parent"() {
        when:
        CompoundTag result = NbtUtils.getCompoundTag(null, 'key')

        then:
        result != null
        result.size() == 0
    }

    def "getCompoundTag should return empty tag for missing key"() {
        given:
        CompoundTag parent = new CompoundTag()

        when:
        CompoundTag result = NbtUtils.getCompoundTag(parent, 'missing')

        then:
        result != null
        result.size() == 0
    }

    def "getCompoundTag should return nested compound tag"() {
        given:
        CompoundTag parent = new CompoundTag()
        CompoundTag nested = new CompoundTag()
        nested.putString('nestedKey', 'value')
        parent.put('nested', nested)

        when:
        CompoundTag result = NbtUtils.getCompoundTag(parent, 'nested')

        then:
        result != null
        result.getString('nestedKey') == 'value'
    }

    // =========================================================================
    // getCompoundTagList() Tests
    // =========================================================================

    def "getCompoundTagList should return empty list for null parent"() {
        when:
        ListTag<CompoundTag> result = NbtUtils.getCompoundTagList(null, 'key')

        then:
        result != null
        result.size() == 0
    }

    def "getCompoundTagList should return empty list for missing key"() {
        given:
        CompoundTag parent = new CompoundTag()

        when:
        ListTag<CompoundTag> result = NbtUtils.getCompoundTagList(parent, 'missing')

        then:
        result != null
        result.size() == 0
    }

    def "getCompoundTagList should return compound list"() {
        given:
        CompoundTag parent = new CompoundTag()
        ListTag<CompoundTag> list = new ListTag<>(CompoundTag)
        CompoundTag item1 = new CompoundTag()
        item1.putString('name', 'item1')
        list.add(item1)
        parent.put('list', list)

        when:
        ListTag<CompoundTag> result = NbtUtils.getCompoundTagList(parent, 'list')

        then:
        result != null
        result.size() == 1
        result.get(0).getString('name') == 'item1'
    }

    def "getCompoundTagList should return empty list for non-compound list"() {
        given:
        CompoundTag parent = new CompoundTag()
        ListTag<StringTag> stringList = new ListTag<>(StringTag)
        stringList.addString('test')
        parent.put('list', stringList)

        when:
        ListTag<CompoundTag> result = NbtUtils.getCompoundTagList(parent, 'list')

        then:
        result != null
        result.size() == 0
    }

    // =========================================================================
    // getListTag() Tests
    // =========================================================================

    def "getListTag should return empty list for null parent"() {
        when:
        ListTag<?> result = NbtUtils.getListTag(null, 'key')

        then:
        result != null
        result.size() == 0
    }

    def "getListTag should return empty list for missing key"() {
        given:
        CompoundTag parent = new CompoundTag()

        when:
        ListTag<?> result = NbtUtils.getListTag(parent, 'missing')

        then:
        result != null
        result.size() == 0
    }

    def "getListTag should return existing list"() {
        given:
        CompoundTag parent = new CompoundTag()
        ListTag<StringTag> list = new ListTag<>(StringTag)
        list.addString('test')
        parent.put('list', list)

        when:
        ListTag<?> result = NbtUtils.getListTag(parent, 'list')

        then:
        result != null
        result.size() == 1
    }

    // =========================================================================
    // getDoubleAt() Tests
    // =========================================================================

    def "getDoubleAt should return 0.0 for null list"() {
        expect:
        NbtUtils.getDoubleAt(null, 0) == 0.0
    }

    def "getDoubleAt should return 0.0 for invalid index"() {
        given:
        ListTag<IntTag> list = new ListTag<>(IntTag)
        list.addInt(42)

        expect:
        NbtUtils.getDoubleAt(list, -1) == 0.0
        NbtUtils.getDoubleAt(list, 10) == 0.0
    }

    def "getDoubleAt should extract double from NumberTag"() {
        given:
        ListTag<DoubleTag> list = new ListTag<>(DoubleTag)
        list.addDouble(3.14159)

        expect:
        NbtUtils.getDoubleAt(list, 0) == 3.14159
    }

    def "getDoubleAt should parse double from StringTag"() {
        given:
        ListTag<StringTag> list = new ListTag<>(StringTag)
        list.addString('2.71828')

        expect:
        NbtUtils.getDoubleAt(list, 0) == 2.71828
    }

    def "getDoubleAt should return 0.0 for invalid string format"() {
        given:
        ListTag<StringTag> list = new ListTag<>(StringTag)
        list.addString('not a number')

        expect:
        NbtUtils.getDoubleAt(list, 0) == 0.0
    }

    // =========================================================================
    // getStringAt() Tests
    // =========================================================================

    def "getStringAt should return empty string for null list"() {
        expect:
        NbtUtils.getStringAt(null, 0) == ''
    }

    def "getStringAt should return empty string for invalid index"() {
        given:
        ListTag<StringTag> list = new ListTag<>(StringTag)
        list.addString('test')

        expect:
        NbtUtils.getStringAt(list, -1) == ''
        NbtUtils.getStringAt(list, 10) == ''
    }

    def "getStringAt should extract string from StringTag"() {
        given:
        ListTag<StringTag> list = new ListTag<>(StringTag)
        list.addString('test string')

        expect:
        NbtUtils.getStringAt(list, 0) == 'test string'
    }

    def "getStringAt should extract raw field from CompoundTag"() {
        given:
        ListTag<CompoundTag> list = new ListTag<>(CompoundTag)
        CompoundTag compound = new CompoundTag()
        compound.putString('raw', 'raw text content')
        list.add(compound)

        expect:
        NbtUtils.getStringAt(list, 0) == 'raw text content'
    }

    def "getStringAt should convert CompoundTag to JSON when raw missing"() {
        given:
        ListTag<CompoundTag> list = new ListTag<>(CompoundTag)
        CompoundTag compound = new CompoundTag()
        compound.putString('text', 'Hello')
        list.add(compound)

        when:
        String result = NbtUtils.getStringAt(list, 0)

        then:
        result.contains('"text":"Hello"')
    }

    def "getStringAt should use valueToString for other types"() {
        given:
        ListTag<IntTag> list = new ListTag<>(IntTag)
        list.addInt(42)

        expect:
        NbtUtils.getStringAt(list, 0) == '42'
    }

    // =========================================================================
    // getStringFrom() Tests
    // =========================================================================

    def "getStringFrom should return empty string for null tag"() {
        expect:
        NbtUtils.getStringFrom(null, 'key') == ''
    }

    def "getStringFrom should return empty string for missing key"() {
        given:
        CompoundTag tag = new CompoundTag()

        expect:
        NbtUtils.getStringFrom(tag, 'missing') == ''
    }

    def "getStringFrom should extract string value"() {
        given:
        CompoundTag tag = new CompoundTag()
        tag.putString('test', 'value')

        expect:
        NbtUtils.getStringFrom(tag, 'test') == 'value'
    }

    def "getStringFrom should return empty string for non-StringTag"() {
        given:
        CompoundTag tag = new CompoundTag()
        tag.putInt('number', 42)

        expect:
        NbtUtils.getStringFrom(tag, 'number') == ''
    }

    // =========================================================================
    // getCompoundAt() Tests
    // =========================================================================

    def "getCompoundAt should return empty compound for null list"() {
        when:
        CompoundTag result = NbtUtils.getCompoundAt(null, 0)

        then:
        result != null
        result.size() == 0
    }

    def "getCompoundAt should return empty compound for invalid index"() {
        given:
        ListTag<CompoundTag> list = new ListTag<>(CompoundTag)
        list.add(new CompoundTag())

        when:
        CompoundTag result = NbtUtils.getCompoundAt(list, 10)

        then:
        result != null
        result.size() == 0
    }

    def "getCompoundAt should return compound at index"() {
        given:
        ListTag<CompoundTag> list = new ListTag<>(CompoundTag)
        CompoundTag compound = new CompoundTag()
        compound.putString('name', 'test')
        list.add(compound)

        when:
        CompoundTag result = NbtUtils.getCompoundAt(list, 0)

        then:
        result != null
        result.getString('name') == 'test'
    }

    def "getCompoundAt should return empty compound for non-CompoundTag"() {
        given:
        ListTag<StringTag> list = new ListTag<>(StringTag)
        list.addString('test')

        when:
        CompoundTag result = NbtUtils.getCompoundAt(list, 0)

        then:
        result != null
        result.size() == 0
    }

    // =========================================================================
    // isStringList() Tests
    // =========================================================================

    def "isStringList should return false for null list"() {
        expect:
        !NbtUtils.isStringList(null)
    }

    def "isStringList should return false for empty list"() {
        given:
        ListTag<StringTag> list = new ListTag<>(StringTag)

        expect:
        !NbtUtils.isStringList(list)
    }

    def "isStringList should return true for string list"() {
        given:
        ListTag<StringTag> list = new ListTag<>(StringTag)
        list.addString('test')

        expect:
        NbtUtils.isStringList(list)
    }

    def "isStringList should return false for compound list"() {
        given:
        ListTag<CompoundTag> list = new ListTag<>(CompoundTag)
        list.add(new CompoundTag())

        expect:
        !NbtUtils.isStringList(list)
    }

    // =========================================================================
    // isCompoundList() Tests
    // =========================================================================

    def "isCompoundList should return false for null list"() {
        expect:
        !NbtUtils.isCompoundList(null)
    }

    def "isCompoundList should return false for empty list"() {
        given:
        ListTag<CompoundTag> list = new ListTag<>(CompoundTag)

        expect:
        !NbtUtils.isCompoundList(list)
    }

    def "isCompoundList should return true for compound list"() {
        given:
        ListTag<CompoundTag> list = new ListTag<>(CompoundTag)
        list.add(new CompoundTag())

        expect:
        NbtUtils.isCompoundList(list)
    }

    def "isCompoundList should return false for string list"() {
        given:
        ListTag<StringTag> list = new ListTag<>(StringTag)
        list.addString('test')

        expect:
        !NbtUtils.isCompoundList(list)
    }

    // =========================================================================
    // convertNbtToJson() Tests
    // =========================================================================

    def "convertNbtToJson should convert StringTag to JSON"() {
        given:
        CompoundTag tag = new CompoundTag()
        tag.putString('text', 'Hello')

        when:
        JSONObject json = NbtUtils.convertNbtToJson(tag)

        then:
        json.getString('text') == 'Hello'
    }

    def "convertNbtToJson should convert NumberTag to JSON"() {
        given:
        CompoundTag tag = new CompoundTag()
        tag.putInt('number', 42)

        when:
        JSONObject json = NbtUtils.convertNbtToJson(tag)

        then:
        json.getInt('number') == 42
    }

    def "convertNbtToJson should convert nested CompoundTag to JSON"() {
        given:
        CompoundTag parent = new CompoundTag()
        CompoundTag nested = new CompoundTag()
        nested.putString('nested', 'value')
        parent.put('child', nested)

        when:
        JSONObject json = NbtUtils.convertNbtToJson(parent)

        then:
        json.getJSONObject('child').getString('nested') == 'value'
    }

    def "convertNbtToJson should convert ListTag to JSONArray"() {
        given:
        CompoundTag tag = new CompoundTag()
        ListTag<StringTag> list = new ListTag<>(StringTag)
        list.addString('item1')
        list.addString('item2')
        tag.put('list', list)

        when:
        JSONObject json = NbtUtils.convertNbtToJson(tag)

        then:
        JSONArray array = json.getJSONArray('list')
        array.length() == 2
        array.getString(0) == 'item1'
        array.getString(1) == 'item2'
    }

    // =========================================================================
    // convertNbtListToJsonArray() Tests
    // =========================================================================

    def "convertNbtListToJsonArray should convert string list to JSONArray"() {
        given:
        ListTag<StringTag> list = new ListTag<>(StringTag)
        list.addString('a')
        list.addString('b')

        when:
        JSONArray array = NbtUtils.convertNbtListToJsonArray(list)

        then:
        array.length() == 2
        array.getString(0) == 'a'
        array.getString(1) == 'b'
    }

    def "convertNbtListToJsonArray should convert number list to JSONArray"() {
        given:
        ListTag<IntTag> list = new ListTag<>(IntTag)
        list.addInt(1)
        list.addInt(2)

        when:
        JSONArray array = NbtUtils.convertNbtListToJsonArray(list)

        then:
        array.length() == 2
        array.getInt(0) == 1
        array.getInt(1) == 2
    }

    def "convertNbtListToJsonArray should convert compound list to JSONArray"() {
        given:
        ListTag<CompoundTag> list = new ListTag<>(CompoundTag)
        CompoundTag item = new CompoundTag()
        item.putString('name', 'test')
        list.add(item)

        when:
        JSONArray array = NbtUtils.convertNbtListToJsonArray(list)

        then:
        array.length() == 1
        array.getJSONObject(0).getString('name') == 'test'
    }

    def "convertNbtListToJsonArray should handle nested lists"() {
        given:
        ListTag<ListTag> outerList = new ListTag<>(ListTag)
        ListTag<StringTag> innerList = new ListTag<>(StringTag)
        innerList.addString('nested')
        outerList.add(innerList)

        when:
        JSONArray array = NbtUtils.convertNbtListToJsonArray(outerList)

        then:
        array.length() == 1
        array.getJSONArray(0).getString(0) == 'nested'
    }

    // =========================================================================
    // getDoubleAt() Additional Edge Cases
    // =========================================================================

    def "getDoubleAt should handle IntTag correctly"() {
        given:
        ListTag<IntTag> list = new ListTag<>(IntTag)
        list.addInt(42)

        expect:
        NbtUtils.getDoubleAt(list, 0) == 42.0
    }

    def "getDoubleAt should handle LongTag"() {
        given:
        ListTag<net.querz.nbt.tag.LongTag> list = new ListTag<>(net.querz.nbt.tag.LongTag)
        list.addLong(123456789L)

        expect:
        NbtUtils.getDoubleAt(list, 0) == 123456789.0
    }

    def "getDoubleAt should handle FloatTag"() {
        given:
        ListTag<net.querz.nbt.tag.FloatTag> list = new ListTag<>(net.querz.nbt.tag.FloatTag)
        list.addFloat(3.14f)

        expect:
        // Float precision may vary, so check it's close to 3.14
        Math.abs(NbtUtils.getDoubleAt(list, 0) - 3.14) < 0.0001
    }

    def "getDoubleAt should handle ShortTag"() {
        given:
        ListTag<net.querz.nbt.tag.ShortTag> list = new ListTag<>(net.querz.nbt.tag.ShortTag)
        list.addShort(100 as short)

        expect:
        NbtUtils.getDoubleAt(list, 0) == 100.0
    }

    def "getDoubleAt should handle ByteTag"() {
        given:
        ListTag<net.querz.nbt.tag.ByteTag> list = new ListTag<>(net.querz.nbt.tag.ByteTag)
        list.addByte(10 as byte)

        expect:
        NbtUtils.getDoubleAt(list, 0) == 10.0
    }

    // =========================================================================
    // convertNbtToJson() Additional Edge Cases
    // =========================================================================

    def "convertNbtToJson should handle byte arrays"() {
        given:
        CompoundTag tag = new CompoundTag()
        byte[] byteArray = [1, 2, 3, 4, 5] as byte[]
        tag.putByteArray('bytes', byteArray)

        when:
        JSONObject json = NbtUtils.convertNbtToJson(tag)

        then:
        // Arrays are stored as raw Java arrays in the default case, not JSONArrays
        // Check that the value exists and is an array
        json.has('bytes')
        def value = json.get('bytes')
        value instanceof byte[]
        ((byte[]) value).length == 5
        ((byte[]) value)[0] == 1
        ((byte[]) value)[4] == 5
    }

    def "convertNbtToJson should handle int arrays"() {
        given:
        CompoundTag tag = new CompoundTag()
        int[] intArray = [100, 200, 300]
        tag.putIntArray('ints', intArray)

        when:
        JSONObject json = NbtUtils.convertNbtToJson(tag)

        then:
        // Arrays are stored as raw Java arrays
        json.has('ints')
        def value = json.get('ints')
        value instanceof int[]
        ((int[]) value).length == 3
        ((int[]) value)[0] == 100
        ((int[]) value)[2] == 300
    }

    def "convertNbtToJson should handle long arrays"() {
        given:
        CompoundTag tag = new CompoundTag()
        long[] longArray = [1000L, 2000L, 3000L]
        tag.putLongArray('longs', longArray)

        when:
        JSONObject json = NbtUtils.convertNbtToJson(tag)

        then:
        // Arrays are stored as raw Java arrays
        json.has('longs')
        def value = json.get('longs')
        value instanceof long[]
        ((long[]) value).length == 3
        ((long[]) value)[0] == 1000L
        ((long[]) value)[2] == 3000L
    }

    def "convertNbtToJson should handle empty arrays"() {
        given:
        CompoundTag tag = new CompoundTag()
        tag.putByteArray('empty', [] as byte[])

        when:
        JSONObject json = NbtUtils.convertNbtToJson(tag)

        then:
        // Arrays are stored as raw Java arrays
        json.has('empty')
        def value = json.get('empty')
        value instanceof byte[]
        ((byte[]) value).length == 0
    }

}

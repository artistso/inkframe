package com.inkframe.core.common

sealed interface JsonValue {
    data class Obj(val entries: Map<String, JsonValue>) : JsonValue
    data class Arr(val items: List<JsonValue>) : JsonValue
    data class Str(val value: String) : JsonValue
    data class Num(val value: Double) : JsonValue
    data class Bool(val value: Boolean) : JsonValue
    data object Null : JsonValue
    fun toJsonString(pretty: Boolean = false): String = StringBuilder().also { write(it, this, pretty, 0) }.toString()
    companion object {
        fun of(v: String) = Str(v); fun of(v: Int) = Num(v.toDouble()); fun of(v: Long) = Num(v.toDouble())
        fun of(v: Float) = Num(v.toDouble()); fun of(v: Boolean) = Bool(v)
        fun obj(vararg p: Pair<String, JsonValue>) = Obj(linkedMapOf(*p)); fun arr(i: List<JsonValue>) = Arr(i)
    }
}
fun JsonValue.asObj() = this as JsonValue.Obj; fun JsonValue.asArr() = this as JsonValue.Arr
fun JsonValue.asString() = (this as JsonValue.Str).value; fun JsonValue.asDouble() = (this as JsonValue.Num).value
fun JsonValue.asInt() = asDouble().toInt(); fun JsonValue.asLong() = asDouble().toLong()
fun JsonValue.asFloat() = asDouble().toFloat(); fun JsonValue.asBool() = (this as JsonValue.Bool).value
operator fun JsonValue.get(k: String) = asObj().entries[k]!!; fun JsonValue.optional(k: String) = (this as? JsonValue.Obj)?.entries?.get(k)
private fun write(sb: StringBuilder, v: JsonValue, pretty: Boolean, depth: Int) {
    when (v) {
        is JsonValue.Obj -> {
            sb.append('{'); val it = v.entries.entries.iterator()
            while (it.hasNext()) {
                val (k, valV) = it.next(); if (pretty) { sb.append('\n'); repeat(depth + 1) { sb.append("  ") } }
                sb.append('"').append(k).append('"').append(if (pretty) ": " else ":"); write(sb, valV, pretty, depth + 1)
                if (it.hasNext()) sb.append(',')
            }
            if (pretty) { sb.append('\n'); repeat(depth) { sb.append("  ") } }; sb.append('}')
        }
        is JsonValue.Arr -> {
            sb.append('['); for (i in v.items.indices) {
                if (pretty) { sb.append('\n'); repeat(depth + 1) { sb.append("  ") } }
                write(sb, v.items[i], pretty, depth + 1); if (i != v.items.lastIndex) sb.append(',')
            }
            if (pretty) { sb.append('\n'); repeat(depth) { sb.append("  ") } }; sb.append(']')
        }
        is JsonValue.Str -> sb.append('"').append(v.value).append('"')
        is JsonValue.Num -> sb.append(if (v.value == v.value.toLong().toDouble()) v.value.toLong().toString() else v.value.toString())
        is JsonValue.Bool -> sb.append(v.value.toString()); JsonValue.Null -> sb.append("null")
    }
}
fun parseJson(t: String): JsonValue = JsonParser(t).parseDocument()
class JsonParseException(m: String) : RuntimeException(m)
private class JsonParser(private val s: String) {
    private var i = 0
    fun parseDocument(): JsonValue { skipWs(); val v = parseValue(); skipWs(); return v }
    private fun parseValue(): JsonValue {
        skipWs(); return when (s[i]) {
            '{' -> parseObject(); '[' -> parseArray(); '"' -> JsonValue.Str(parseString())
            't', 'f' -> parseBool(); 'n' -> parseNull(); else -> parseNumber()
        }
    }
    private fun parseObject(): JsonValue.Obj {
        i++; val map = LinkedHashMap<String, JsonValue>(); skipWs()
        if (peek() == '}') { i++; return JsonValue.Obj(map) }
        while (true) {
            val k = parseString(); skipWs(); i++; val v = parseValue(); map[k] = v; skipWs()
            if (peek() == ',') { i++; continue } else if (peek() == '}') { i++; break } else throw JsonParseException("Err")
        }
        return JsonValue.Obj(map)
    }
    private fun parseArray(): JsonValue.Arr {
        i++; val list = ArrayList<JsonValue>(); skipWs()
        if (peek() == ']') { i++; return JsonValue.Arr(list) }
        while (true) {
            list.add(parseValue()); skipWs()
            if (peek() == ',') { i++; continue } else if (peek() == ']') { i++; break } else throw JsonParseException("Err")
        }
        return JsonValue.Arr(list)
    }
    private fun parseString(): String {
        i++; val sb = StringBuilder()
        while (s[i] != '"') {
            if (s[i] == '\\') { i++; when (s[i]) { 'n' -> sb.append('\n'); else -> sb.append(s[i]) } }
            else sb.append(s[i]); i++
        }
        i++; return sb.toString()
    }
    private fun parseNumber(): JsonValue.Num {
        val start = i; while (i < s.length && (s[i].isDigit() || s[i] in "-.eE+")) i++
        return JsonValue.Num(s.substring(start, i).toDouble())
    }
    private fun parseBool() = if (s.startsWith("true", i)) { i += 4; JsonValue.Bool(true) } else { i += 5; JsonValue.Bool(false) }
    private fun parseNull() = { i += 4; JsonValue.Null }()
    private fun peek() = if (i < s.length) s[i] else '\u0000'
    private fun skipWs() { while (i < s.length && s[i].isWhitespace()) i++ }
}

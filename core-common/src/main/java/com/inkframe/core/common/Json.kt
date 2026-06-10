package com.inkframe.core.common

/**
 * A tiny, dependency-free JSON model + parser + writer.
 */
sealed interface JsonValue {
    data class Obj(val entries: Map<String, JsonValue>) : JsonValue
    data class Arr(val items: List<JsonValue>) : JsonValue
    data class Str(val value: String) : JsonValue
    data class Num(val value: Double) : JsonValue
    data class Bool(val value: Boolean) : JsonValue
    data object Null : JsonValue

    fun toJsonString(pretty: Boolean = false): String =
        StringBuilder().also { write(it, this, pretty, 0) }.toString()

    companion object {
        fun of(value: String): JsonValue = Str(value)
        fun of(value: Int): JsonValue = Num(value.toDouble())
        fun of(value: Long): JsonValue = Num(value.toDouble())
        fun of(value: Float): JsonValue = Num(value.toDouble())
        fun of(value: Boolean): JsonValue = Bool(value)
        fun obj(vararg pairs: Pair<String, JsonValue>): Obj = Obj(linkedMapOf(*pairs))
        fun arr(items: List<JsonValue>): Arr = Arr(items)
    }
}

fun JsonValue.asObj(): JsonValue.Obj = this as? JsonValue.Obj ?: error("Expected JSON object")
fun JsonValue.asArr(): JsonValue.Arr = this as? JsonValue.Arr ?: error("Expected JSON array")
fun JsonValue.asString(): String = (this as? JsonValue.Str)?.value ?: error("Expected JSON string")
fun JsonValue.asDouble(): Double = (this as? JsonValue.Num)?.value ?: error("Expected JSON number")
fun JsonValue.asInt(): Int = asDouble().toInt()
fun JsonValue.asLong(): Long = asDouble().toLong()
fun JsonValue.asFloat(): Float = asDouble().toFloat()
fun JsonValue.asBool(): Boolean = (this as? JsonValue.Bool)?.value ?: error("Expected JSON boolean")

operator fun JsonValue.get(key: String): JsonValue = asObj().entries[key] ?: error("Missing JSON key: '$key'")
fun JsonValue.optional(key: String): JsonValue? = (this as? JsonValue.Obj)?.entries?.get(key)

private fun write(sb: StringBuilder, v: JsonValue, pretty: Boolean, depth: Int) {
    when (v) {
        is JsonValue.Obj -> {
            if (v.entries.isEmpty()) { sb.append("{}"); return }
            sb.append('{')
            val it = v.entries.entries.iterator()
            while (it.hasNext()) {
                val (k, value) = it.next()
                if (pretty) { sb.append('\n'); repeat(depth + 1) { sb.append("  ") } }
                sb.append('"').append(k).append('"').append(if (pretty) ": " else ":")
                write(sb, value, pretty, depth + 1)
                if (it.hasNext()) sb.append(',')
            }
            if (pretty) { sb.append('\n'); repeat(depth) { sb.append("  ") } }
            sb.append('}')
        }
        is JsonValue.Arr -> {
            if (v.items.isEmpty()) { sb.append("[]"); return }
            sb.append('[')
            for (i in v.items.indices) {
                if (pretty) { sb.append('\n'); repeat(depth + 1) { sb.append("  ") } }
                write(sb, v.items[i], pretty, depth + 1)
                if (i != v.items.lastIndex) sb.append(',')
            }
            if (pretty) { sb.append('\n'); repeat(depth) { sb.append("  ") } }
            sb.append(']')
        }
        is JsonValue.Str -> sb.append('"').append(v.value).append('"')
        is JsonValue.Num -> sb.append(if (v.value == v.value.toLong().toDouble()) v.value.toLong().toString() else v.value.toString())
        is JsonValue.Bool -> sb.append(if (v.value) "true" else "false")
        JsonValue.Null -> sb.append("null")
    }
}

fun parseJson(text: String): JsonValue = JsonParser(text).parseDocument()

class JsonParseException(message: String) : RuntimeException(message)

private class JsonParser(private val s: String) {
    private var i = 0

    fun parseDocument(): JsonValue {
        skipWs()
        val v = parseValue()
        skipWs()
        if (i != s.length) fail("Trailing characters")
        return v
    }

    private fun parseValue(): JsonValue {
        skipWs()
        if (i >= s.length) fail("Unexpected end")
        return when (s[i]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> JsonValue.Str(parseString())
            't', 'f' -> parseBool()
            'n' -> parseNull()
            else -> parseNumber()
        }
    }

    private fun parseObject(): JsonValue.Obj {
        expect('{')
        val map = LinkedHashMap<String, JsonValue>()
        skipWs()
        if (peek() == '}') { i++; return JsonValue.Obj(map) }
        while (true) {
            skipWs()
            val key = parseString()
            skipWs(); expect(':')
            val value = parseValue()
            map[key] = value
            skipWs()
            when (peek()) {
                ',' -> { i++; continue }
                '}' -> { i++; break }
                else -> fail("Expected ',' or '}'")
            }
        }
        return JsonValue.Obj(map)
    }

    private fun parseArray(): JsonValue.Arr {
        expect('[')
        val list = ArrayList<JsonValue>()
        skipWs()
        if (peek() == ']') { i++; return JsonValue.Arr(list) }
        while (true) {
            list.add(parseValue())
            skipWs()
            when (peek()) {
                ',' -> { i++; continue }
                ']' -> { i++; break }
                else -> fail("Expected ',' or ']'")
            }
        }
        return JsonValue.Arr(list)
    }

    private fun parseString(): String {
        expect('"')
        val sb = StringBuilder()
        while (true) {
            if (i >= s.length) fail("Unterminated string")
            val c = s[i++]
            if (c == '"') break
            if (c == '\\') {
                if (i >= s.length) fail("Unterminated escape")
                when (val e = s[i++]) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    'n' -> sb.append('\n')
                    else -> sb.append(e)
                }
            } else sb.append(c)
        }
        return sb.toString()
    }

    private fun parseNumber(): JsonValue.Num {
        val start = i
        if (peek() == '-') i++
        while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
        val token = s.substring(start, i)
        return JsonValue.Num(token.toDoubleOrNull() ?: 0.0)
    }

    private fun parseBool(): JsonValue.Bool = when {
        s.startsWith("true", i) -> { i += 4; JsonValue.Bool(true) }
        s.startsWith("false", i) -> { i += 5; JsonValue.Bool(false) }
        else -> { fail("Invalid bool") }
    }

    private fun parseNull(): JsonValue {
        if (s.startsWith("null", i)) { i += 4; return JsonValue.Null }
        fail("Invalid null")
    }

    private fun peek(): Char = if (i < s.length) s[i] else '\u0000'
    private fun expect(c: Char) { if (peek() != c) fail("Expected '$c'"); i++ }
    private fun skipWs() { while (i < s.length && s[i].isWhitespace()) i++ }
    private fun fail(msg: String): Nothing = throw JsonParseException("$msg at $i")
}

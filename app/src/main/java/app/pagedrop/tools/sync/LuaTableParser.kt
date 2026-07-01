package app.pagedrop.tools.sync

/**
 * Minimal Lua table parser for KOReader metadata files.
 * Handles flat string keys and nested tables with { ... } delimiters.
 */
object LuaTableParser {

    fun parseFlatTable(contents: String, tableName: String? = null): Map<String, String>? {
        val section = if (tableName != null) {
            extractTableSection(contents, tableName) ?: return null
        } else contents

        val map = mutableMapOf<String, String>()
        val keyValRegex = Regex("""\["([^"]+)"\]\s*=\s*(.+?)(?:,|\n|$)""")
        val simpleRegex = Regex("""(\w+)\s*=\s*(.+?)(?:,|\n|$)""")

        for (match in keyValRegex.findAll(section)) {
            val key = match.groupValues[1]
            val value = cleanValue(match.groupValues[2])
            map[key] = value
        }
        for (match in simpleRegex.findAll(section)) {
            val key = match.groupValues[1]
            if (key !in map) {
                map[key] = cleanValue(match.groupValues[2])
            }
        }
        return if (map.isEmpty()) null else map
    }

    fun extractSection(contents: String, sectionName: String): String? {
        val regex = Regex("""\["$sectionName"\]\s*=\s*\{""")
        val startMatch = regex.find(contents) ?: return null
        return extractBalancedBraces(contents, startMatch.range.last + 1)
    }

    fun extractBalancedBraces(contents: String, startOffset: Int): String {
        var depth = 1
        var pos = startOffset
        var inString = false
        var escapeNext = false

        while (pos < contents.length && depth > 0) {
            val c = contents[pos]
            if (escapeNext) {
                escapeNext = false
            } else when {
                c == '\\' -> escapeNext = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> depth--
            }
            pos++
        }
        return contents.substring(startOffset, pos - 1).trim()
    }

    fun extractTableSection(contents: String, tableName: String): String? {
        // Match both `tableName = {` and `["tableName"] = {`
        val pattern = Regex("""(?:$tableName|\["$tableName"\])\s*=\s*\{""")
        val startMatch = pattern.find(contents) ?: return null
        val bracketStart = startMatch.value.lastIndexOf('{')
        return extractBalancedBraces(contents, startMatch.range.first + bracketStart + 1)
    }

    fun splitLuaEntries(section: String): List<String> {
        val results = mutableListOf<String>()
        var depth = 0
        var start = -1
        var inString = false
        var escapeNext = false

        for ((i, c) in section.withIndex()) {
            if (escapeNext) { escapeNext = false; continue }
            when {
                c == '\\' -> escapeNext = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> {
                    if (depth == 0) start = i + 1
                    depth++
                }
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        results.add(section.substring(start, i).trim())
                        start = -1
                    }
                }
            }
        }
        return results
    }

    fun cleanValue(raw: String): String {
        var value = raw.trim()
        if (value.endsWith(",")) value = value.dropLast(1).trim()
        if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.removeSurrounding("\"").trim()
        }
        return value
    }
}

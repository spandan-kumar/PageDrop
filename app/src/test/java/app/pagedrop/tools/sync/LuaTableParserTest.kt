package app.pagedrop.tools.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LuaTableParserTest {

    @Test
    fun parseFlatTable_basicStringKeys() {
        val lua = """
            ["title"] = "Gravity's Rainbow",
            ["authors"] = "Thomas Pynchon",
            ["language"] = "en",
        """.trimIndent()

        val result = LuaTableParser.parseFlatTable(lua)
        assertNotNull(result)
        assertEquals("Gravity's Rainbow", result!!["title"])
        assertEquals("Thomas Pynchon", result["authors"])
        assertEquals("en", result["language"])
    }

    @Test
    fun parseFlatTable_simpleAssignmentKeys() {
        val lua = """
            title = "1984",
            author = "George Orwell",
        """.trimIndent()

        val result = LuaTableParser.parseFlatTable(lua)
        assertNotNull(result)
        assertEquals("1984", result!!["title"])
        assertEquals("George Orwell", result["author"])
    }

    @Test
    fun parseFlatTable_mixedKeys_prefersBracket() {
        val lua = """
            title = "lowercase",
            ["title"] = "bracket-wins",
        """.trimIndent()

        val result = LuaTableParser.parseFlatTable(lua)
        assertEquals("bracket-wins", result!!["title"])
    }

    @Test
    fun parseFlatTable_cleansValues() {
        val lua = """["name"] = "  spaced  ","""
        val result = LuaTableParser.parseFlatTable(lua)
        assertEquals("spaced", result!!["name"])
    }

    @Test
    fun extractSection_basic() {
        val lua = """stats = { ["pages"] = "42", }"""
        val section = LuaTableParser.extractTableSection(lua, "stats")
        assertNotNull(section)
        assertTrue(section!!.contains("\"42\""))
    }

    @Test
    fun extractSection_withBracketNotation() {
        val lua = """["bookmarks"] = { { ["text"] = "hello" }, { ["text"] = "world" } }"""
        val section = LuaTableParser.extractSection(lua, "bookmarks")
        assertNotNull(section)
        assertTrue(section!!.contains("hello"))
        assertTrue(section.contains("world"))
    }

    @Test
    fun extractSection_nestedBraces() {
        val lua = """data = { ["nested"] = { ["a"] = "1" }, ["outer"] = "2" }"""
        val section = LuaTableParser.extractTableSection(lua, "data")
        assertNotNull(section)
        assertTrue(section!!.contains("\"outer\""))
    }

    @Test
    fun splitLuaEntries_twoEntries() {
        val section = """{ ["text"] = "first" }, { ["text"] = "second" }"""
        val entries = LuaTableParser.splitLuaEntries(section)
        assertEquals(2, entries.size)
        assertTrue(entries[0].contains("first"))
        assertTrue(entries[1].contains("second"))
    }

    @Test
    fun cleanValue_removesQuotes() {
        assertEquals("value", LuaTableParser.cleanValue("\"value\""))
        assertEquals("42", LuaTableParser.cleanValue("42"))
    }

    @Test
    fun cleanValue_removesTrailingComma() {
        assertEquals("value", LuaTableParser.cleanValue("\"value\","))
        assertEquals("42", LuaTableParser.cleanValue("42,"))
    }
}

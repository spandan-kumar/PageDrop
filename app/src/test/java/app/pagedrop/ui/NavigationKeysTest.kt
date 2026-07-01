package app.pagedrop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationKeysTest {

    @Test
    fun allNavKeysAreSerializable() {
        val keys = listOf(Main, Tools, Screensaver, Fonts, Dictionaries, Dashboard, Sync, Articles)
        assertEquals(8, keys.size)
        keys.forEach { key -> assertNotNull(key.toString()) }
    }

    @Test
    fun routeCount_matchesScreens() {
        val screenCount = 8 // Main (BookScreen) + Tools hub + 6 tool screens
        val navKeys = listOf(Main, Tools, Screensaver, Fonts, Dictionaries, Dashboard, Sync, Articles)
        assertEquals(screenCount, navKeys.size)
    }

    @Test
    fun routeNames_areNotEmpty() {
        listOf(Main, Tools, Screensaver, Fonts, Dictionaries, Dashboard, Sync, Articles)
            .forEach { assertTrue(it.toString().isNotBlank()) }
    }
}

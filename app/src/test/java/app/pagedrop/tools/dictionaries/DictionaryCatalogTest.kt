package app.pagedrop.tools.dictionaries

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryCatalogTest {

    @Test
    fun catalog_containsExpectedDictionaries() {
        val ids = DictionaryCatalog.items.map { it.id }
        assertTrue(ids.contains("gcide"))
        assertTrue(ids.contains("wordnet"))
        assertTrue(ids.contains("websters_1913"))
    }

    @Test
    fun allDictionaries_haveNamesAndLicenses() {
        DictionaryCatalog.items.forEach { dict ->
            assertTrue(dict.name.isNotBlank())
            assertTrue(dict.language.isNotBlank())
            assertTrue(dict.license.isNotBlank())
            assertTrue(dict.url.isNotBlank())
        }
    }

    @Test
    fun allDictionaries_areStardictFormat() {
        DictionaryCatalog.items.forEach { dict ->
            assertEquals("stardict", dict.format)
        }
    }

    @Test
    fun gcide_isEnglish() {
        val gcide = DictionaryCatalog.items.find { it.id == "gcide" }
        assertNotNull(gcide)
        assertEquals("English", gcide!!.language)
    }

    @Test
    fun websters1913_isPublicDomain() {
        val webster = DictionaryCatalog.items.find { it.id == "websters_1913" }
        assertNotNull(webster)
        assertEquals("Public Domain", webster!!.license)
    }
}

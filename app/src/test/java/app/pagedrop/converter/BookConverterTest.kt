package app.pagedrop.converter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookConverterTest {

    @Test
    fun canConvert_epub() {
        assertTrue(BookConverter.canConvert("EPUB"))
        assertTrue(BookConverter.canConvert("epub"))
    }

    @Test
    fun canConvert_pdf() {
        assertTrue(BookConverter.canConvert("PDF"))
    }

    @Test
    fun canConvert_txt() {
        assertTrue(BookConverter.canConvert("TXT"))
    }

    @Test
    fun canConvert_unsupportedFormats() {
        assertFalse(BookConverter.canConvert("MOBI"))
        assertFalse(BookConverter.canConvert("AZW3"))
        assertFalse(BookConverter.canConvert("KFX"))
        assertFalse(BookConverter.canConvert("PRC"))
        assertFalse(BookConverter.canConvert("DOCX"))
    }

    @Test
    fun canConvert_caseInsensitive() {
        assertTrue(BookConverter.canConvert("ePuB"))
        assertTrue(BookConverter.canConvert("Pdf"))
        assertTrue(BookConverter.canConvert("txt"))
    }
}

package app.pagedrop.data.local.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookEntityTest {

    @Test
    fun book_constructor_setsDefaults() {
        val book = Book(
            title = "Test",
            author = "Author",
            fileName = "test.mobi",
            filePath = "/path/test.mobi",
            format = "MOBI",
            fileSize = 1024
        )
        assertEquals("Test", book.title)
        assertEquals("MOBI", book.format)
        assertEquals(0, book.uid)
        assertEquals(null, book.coverPath)
        assertEquals(null, book.kindleUuid)
        assertEquals(null, book.lastTransferred)
        assertTrue(book.addedDate > 0)
    }

    @Test
    fun book_withAllFields() {
        val t = System.currentTimeMillis()
        val book = Book(
            uid = 42,
            title = "Gravity's Rainbow",
            author = "Thomas Pynchon",
            fileName = "gravity.mobi",
            filePath = "/books/gravity.mobi",
            format = "MOBI",
            fileSize = 2_500_000,
            coverPath = "/covers/gravity.jpg",
            kindleUuid = "abc-def-123",
            addedDate = t,
            lastTransferred = t
        )
        assertEquals(42, book.uid)
        assertEquals("/covers/gravity.jpg", book.coverPath)
        assertEquals("abc-def-123", book.kindleUuid)
        assertEquals(t, book.lastTransferred)
    }

    @Test
    fun book_equality() {
        val a = Book(title = "T", author = "A", fileName = "f", filePath = "p", format = "MOBI", fileSize = 100)
        val b = Book(title = "T", author = "A", fileName = "f", filePath = "p", format = "MOBI", fileSize = 100)
        assertEquals(a, b)
    }

    @Test
    fun book_differentFormatNotEqual() {
        val a = Book(title = "T", author = "A", fileName = "f", filePath = "p", format = "MOBI", fileSize = 100)
        val b = Book(title = "T", author = "A", fileName = "f", filePath = "p", format = "PDF", fileSize = 100)
        assertTrue(a != b)
    }

    @Test
    fun book_copyWithUid() {
        val book = Book(title = "T", author = "A", fileName = "f", filePath = "p", format = "MOBI", fileSize = 100)
        val copied = book.copy(uid = 1, coverPath = "/covers/c.jpg")
        assertEquals(1, copied.uid)
        assertEquals("/covers/c.jpg", copied.coverPath)
    }

    @Test
    fun kindleUuid_storedInBook() {
        val book = Book(title = "T", author = "A", fileName = "f", filePath = "p",
            format = "MOBI", fileSize = 100, kindleUuid = "test-uuid")
        assertEquals("test-uuid", book.kindleUuid)
    }

    @Test
    fun lastTransferred_nullByDefault() {
        val book = Book(title = "T", author = "A", fileName = "f", filePath = "p",
            format = "MOBI", fileSize = 100)
        assertNull(book.lastTransferred)
    }
}

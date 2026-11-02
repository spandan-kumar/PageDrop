package app.pagedrop.ui.discover

import app.pagedrop.data.local.database.Book
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultSheetTest {
    private fun book(lastTransferred: Long? = null) = Book(
        title = "Pride and Prejudice",
        author = "Jane Austen",
        fileName = "pride-and-prejudice.azw3",
        filePath = "/books/pride-and-prejudice.azw3",
        format = "AZW3",
        fileSize = 1L,
        lastTransferred = lastTransferred,
    )

    @Test
    fun sendAgainIsNotOfferedForBookNotInLibrary() {
        assertFalse(shouldOfferSendAgain(null))
    }

    @Test
    fun sendAgainIsNotOfferedForBookThatHasNeverBeenTransferred() {
        assertFalse(shouldOfferSendAgain(book()))
    }

    @Test
    fun sendAgainIsOfferedAfterSuccessfulTransferTimestampExists() {
        assertTrue(shouldOfferSendAgain(book(lastTransferred = 1_730_000_000_000L)))
    }
}

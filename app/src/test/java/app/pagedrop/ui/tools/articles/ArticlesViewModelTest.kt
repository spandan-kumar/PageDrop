package app.pagedrop.ui.tools.articles

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticlesViewModelTest {

    @Test
    fun initialState() = runTest {
        val state = ArticlesViewModel.ArticlesState()
        assertEquals(false, state.isProcessing)
        assertEquals("Extracting...", state.progressMessage)
        assertNull(state.extractedArticle)
        assertNull(state.error)
        assertNull(state.success)
    }

    @Test
    fun processingState() = runTest {
        val state = ArticlesViewModel.ArticlesState(isProcessing = true, progressMessage = "Converting...")
        assertEquals(true, state.isProcessing)
        assertEquals("Converting...", state.progressMessage)
    }

    @Test
    fun extractedArticleState() = runTest {
        val article = app.pagedrop.tools.articles.ArticleExtractor.ExtractedArticle(
            title = "Test Article",
            author = "Author",
            content = "<p>Test</p>",
            leadImageUrl = null,
            siteName = "TestSite"
        )
        val state = ArticlesViewModel.ArticlesState(extractedArticle = article)
        assertEquals("Test Article", state.extractedArticle?.title)
        assertEquals("Author", state.extractedArticle?.author)
    }

    @Test
    fun errorState() = runTest {
        val state = ArticlesViewModel.ArticlesState(error = "Failed to extract URL")
        assertEquals("Failed to extract URL", state.error)
    }

    @Test
    fun successState() = runTest {
        val state = ArticlesViewModel.ArticlesState(success = "Article sent to Kindle")
        assertEquals("Article sent to Kindle", state.success)
    }

    @Test
    fun cannotHaveSuccessAndError() = runTest {
        val state = ArticlesViewModel.ArticlesState(success = "S", error = "E")
        assertTrue(state.success != null && state.error != null)
    }
}

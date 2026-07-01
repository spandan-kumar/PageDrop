package app.pagedrop.ui.tools.articles

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pagedrop.data.BookRepository
import app.pagedrop.data.KindleSettings
import app.pagedrop.data.local.database.Book
import app.pagedrop.tools.articles.ArticleExtractor
import app.pagedrop.tools.articles.ArticleMobiConverter
import app.pagedrop.transfer.sftp.KindleSftpClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ArticlesViewModel @Inject constructor(
    application: Application,
    private val kindleSettings: KindleSettings,
    private val bookRepository: BookRepository
) : AndroidViewModel(application) {

    data class ArticlesState(
        val isProcessing: Boolean = false,
        val progressMessage: String = "Extracting...",
        val extractedArticle: ArticleExtractor.ExtractedArticle? = null,
        val error: String? = null,
        val success: String? = null
    )

    private val _state = MutableStateFlow(ArticlesState())
    val state: StateFlow<ArticlesState> = _state.asStateFlow()

    fun extractAndSend(url: String) {
        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, progressMessage = "Extracting...", error = null, success = null) }

            // Step 1: Extract article
            val extractResult = ArticleExtractor.extract(url)
            if (extractResult.isFailure) {
                _state.update {
                    it.copy(
                        isProcessing = false,
                        error = "Failed to extract: ${extractResult.exceptionOrNull()?.localizedMessage}"
                    )
                }
                return@launch
            }

            val article = extractResult.getOrThrow()
            _state.update { it.copy(extractedArticle = article, progressMessage = "Converting to MOBI...") }

            // Step 2: Convert to MOBI
            val app = getApplication<Application>()
            val articlesDir = File(app.filesDir, "articles")
            articlesDir.mkdirs()
            val safeName = article.title.replace(Regex("[^a-zA-Z0-9\\s-]"), "").trim().take(50).ifBlank { "article" }
            val mobiFile = File(articlesDir, "${safeName}.mobi")

            val converted = ArticleMobiConverter.convertArticleToMobi(article, mobiFile)
            if (!converted) {
                _state.update { it.copy(isProcessing = false, error = "MOBI conversion failed") }
                return@launch
            }

            _state.update { it.copy(progressMessage = "Sending to Kindle...") }

            val book = Book(
                title = article.title,
                author = article.author ?: (article.siteName ?: "Unknown Author"),
                fileName = mobiFile.name,
                filePath = mobiFile.absolutePath,
                format = "MOBI",
                fileSize = mobiFile.length()
            )

            val bookId = bookRepository.addBookFromFile(book)
            val bookWithId = book.copy(uid = bookId.toInt())

            val result = KindleSftpClient.transferBooks(
                books = listOf(bookWithId),
                host = kindleSettings.host,
                port = kindleSettings.port,
                user = kindleSettings.username,
                pass = kindleSettings.password,
                directory = kindleSettings.targetDirectory,
                triggerRescan = kindleSettings.triggerRescan
            ) { _, _, message ->
                _state.update { it.copy(progressMessage = message) }
            }

            if (result.isSuccess) {
                bookRepository.markTransferred(bookId.toInt())
                _state.update {
                    it.copy(
                        isProcessing = false,
                        extractedArticle = null,
                        success = "\"${article.title}\" sent to Kindle"
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        isProcessing = false,
                        error = "Transfer failed: ${result.exceptionOrNull()?.localizedMessage}"
                    )
                }
            }
        }
    }
}

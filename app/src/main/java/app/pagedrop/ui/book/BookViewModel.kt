/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.pagedrop.ui.book

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import app.pagedrop.data.BookRepository
import app.pagedrop.data.local.database.Book
import app.pagedrop.ui.book.LibraryUiState.Error
import app.pagedrop.ui.book.LibraryUiState.Loading
import app.pagedrop.ui.book.LibraryUiState.Success
import javax.inject.Inject

@HiltViewModel
class BookViewModel @Inject constructor(
    private val bookRepository: BookRepository
) : ViewModel() {

    val uiState: StateFlow<LibraryUiState> = bookRepository
        .getBooks()
        .map<List<Book>, LibraryUiState>(::Success)
        .catch { emit(Error(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Loading)

    private val _transferQueue = MutableStateFlow<List<Book>>(emptyList())
    val transferQueue: StateFlow<List<Book>> = _transferQueue.asStateFlow()

    private val _isConverting = MutableStateFlow(false)
    val isConverting: StateFlow<Boolean> = _isConverting.asStateFlow()

    private val _conversionError = MutableStateFlow<String?>(null)
    val conversionError: StateFlow<String?> = _conversionError.asStateFlow()

    fun addBook(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                bookRepository.addBook(context, uri)
            } catch (e: Exception) {
                // Error will surface through uiState flow
            }
        }
    }

    fun addBookWithConversion(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isConverting.value = true
            _conversionError.value = null
            try {
                bookRepository.convertAndAddBook(context, uri)
            } catch (e: Exception) {
                _conversionError.value = e.localizedMessage ?: "Conversion failed"
            } finally {
                _isConverting.value = false
            }
        }
    }

    fun clearConversionError() {
        _conversionError.value = null
    }

    fun deleteBook(book: Book) {
        viewModelScope.launch {
            // Also remove from transfer queue if queued
            _transferQueue.update { queue -> queue.filter { it.uid != book.uid } }
            bookRepository.deleteBook(book)
        }
    }

    fun queueForTransfer(book: Book) {
        _transferQueue.update { queue ->
            if (queue.any { it.uid == book.uid }) queue
            else queue + book
        }
    }

    fun removeFromQueue(book: Book) {
        _transferQueue.update { queue -> queue.filter { it.uid != book.uid } }
    }

    fun isQueued(book: Book): Boolean {
        return _transferQueue.value.any { it.uid == book.uid }
    }

    fun toggleQueued(book: Book) {
        if (isQueued(book)) removeFromQueue(book) else queueForTransfer(book)
    }

    fun clearQueue() {
        _transferQueue.update { emptyList() }
    }
}

sealed interface LibraryUiState {
    object Loading : LibraryUiState
    data class Error(val throwable: Throwable) : LibraryUiState
    data class Success(val data: List<Book>) : LibraryUiState
}

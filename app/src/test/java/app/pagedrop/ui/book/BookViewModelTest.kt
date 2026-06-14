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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import app.pagedrop.data.BookRepository
import app.pagedrop.data.local.database.Book

@OptIn(ExperimentalCoroutinesApi::class)
class BookViewModelTest {
    @Test
    fun uiState_initiallyLoading() = runTest {
        val viewModel = BookViewModel(FakeBookRepository())
        assertEquals(viewModel.uiState.first(), LibraryUiState.Loading)
    }
}

private class FakeBookRepository : BookRepository {

    private val data = mutableListOf<Book>()

    override fun getBooks(): Flow<List<Book>> = flowOf(data.toList())

    override suspend fun addBook(context: Context, uri: Uri): Book {
        val book = Book(
            title = "Test Book",
            author = "Test Author",
            fileName = "test.azw3",
            filePath = "/fake/test.azw3",
            format = "AZW3",
            fileSize = 1000L
        )
        data.add(book)
        return book
    }

    override suspend fun deleteBook(book: Book) {
        data.removeAll { it.uid == book.uid }
    }

    override suspend fun getBookById(id: Int): Book? {
        return data.find { it.uid == id }
    }

    override suspend fun markTransferred(uid: Int) {}

    override suspend fun convertAndAddBook(context: Context, uri: Uri): Book {
        return addBook(context, uri) // Just delegate for tests
    }
}

/*
 * Copyright (C) 2026 The Android Open Source Project
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

package app.pagedrop.ui.transfer

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import app.pagedrop.data.BookRepository
import app.pagedrop.data.local.database.Book
import app.pagedrop.transfer.hotspot.HotspotHelper
import app.pagedrop.transfer.service.TransferService
import javax.inject.Inject

@HiltViewModel
class TransferViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val hotspotHelper: HotspotHelper,
) : ViewModel() {

    private val _serverRunning = MutableStateFlow(false)
    val serverRunning: StateFlow<Boolean> = _serverRunning.asStateFlow()

    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl: StateFlow<String?> = _serverUrl.asStateFlow()

    private val _queuedBooks = MutableStateFlow<List<Book>>(emptyList())
    val queuedBooks: StateFlow<List<Book>> = _queuedBooks.asStateFlow()

    companion object {
        private const val SERVER_PORT = 8080
    }

    init {
        refreshIpAddress()
    }

    fun setQueuedBooks(books: List<Book>) {
        _queuedBooks.value = books
    }

    fun startServer(context: Context) {
        TransferService.startService(context)
        _serverRunning.value = true
        refreshIpAddress()
    }

    fun stopServer(context: Context) {
        TransferService.stopService(context)
        _serverRunning.value = false
    }

    fun toggleServer(context: Context) {
        if (_serverRunning.value) stopServer(context) else startServer(context)
    }

    fun refreshIpAddress() {
        val ip = hotspotHelper.getDeviceIpAddress()
        _serverUrl.value = if (ip != null) {
            hotspotHelper.getServerUrl(SERVER_PORT)
        } else {
            null
        }
    }
}

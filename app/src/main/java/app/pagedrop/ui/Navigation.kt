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

package app.pagedrop.ui

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import app.pagedrop.ui.book.BookScreen
import app.pagedrop.ui.tools.dashboard.DashboardScreen
import app.pagedrop.ui.tools.fonts.FontsScreen
import app.pagedrop.ui.tools.dictionaries.DictionariesScreen
import app.pagedrop.ui.tools.screensavers.ScreensaverScreen
import app.pagedrop.ui.tools.sync.SyncScreen
import app.pagedrop.ui.tools.articles.ArticlesScreen
import app.pagedrop.ui.connection.ConnectionScreen

@Composable
fun MainNavigation() {
    val backStack = rememberNavBackStack(Main)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Main> {
                BookScreen(
                    modifier = Modifier.safeDrawingPadding(),
                    onNavigateToTools = { backStack.addLast(Tools) }
                )
            }
            entry<Tools> {
                val modifier = Modifier.safeDrawingPadding()
                ToolsScreen(
                    modifier = modifier,
                    onBack = { backStack.removeLastOrNull() },
                    onNavigateToScreensaver = { backStack.addLast(Screensaver) },
                    onNavigateToFonts = { backStack.addLast(Fonts) },
                    onNavigateToDictionaries = { backStack.addLast(Dictionaries) },
                    onNavigateToDashboard = { backStack.addLast(Dashboard) },
                    onNavigateToSync = { backStack.addLast(Sync) },
                    onNavigateToArticles = { backStack.addLast(Articles) },
                    onNavigateToConnection = { backStack.addLast(Connection) }
                )
            }
            entry<Screensaver> {
                ScreensaverScreen(
                    modifier = Modifier.safeDrawingPadding(),
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<Fonts> {
                FontsScreen(
                    modifier = Modifier.safeDrawingPadding(),
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<Dictionaries> {
                DictionariesScreen(
                    modifier = Modifier.safeDrawingPadding(),
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<Dashboard> {
                DashboardScreen(
                    modifier = Modifier.safeDrawingPadding(),
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<Sync> {
                SyncScreen(
                    modifier = Modifier.safeDrawingPadding(),
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<Articles> {
                ArticlesScreen(
                    modifier = Modifier.safeDrawingPadding(),
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<Connection> {
                ConnectionScreen(
                    modifier = Modifier.safeDrawingPadding(),
                    onBack = { backStack.removeLastOrNull() }
                )
            }
        }
    )
}

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

package app.pagedrop.ui.theme

import androidx.compose.ui.graphics.Color

// ── Light palette — pure monochrome ──

val PrimaryLight = Color(0xFF1C1B1F)           // Near-black
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFE8E8E8)  // Light gray
val OnPrimaryContainerLight = Color(0xFF1C1B1F)

val SecondaryLight = Color(0xFF49454F)         // Medium gray
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFE8E8E8)
val OnSecondaryContainerLight = Color(0xFF1C1B1F)

val TertiaryLight = Color(0xFF49454F)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFE8E8E8)
val OnTertiaryContainerLight = Color(0xFF1C1B1F)

val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)

val BackgroundLight = Color(0xFFFFFFFF)         // Pure white
val OnBackgroundLight = Color(0xFF1C1B1F)
val SurfaceLight = Color(0xFFFFFFFF)
val OnSurfaceLight = Color(0xFF1C1B1F)
val SurfaceVariantLight = Color(0xFFF3F3F3)    // Light gray surface
val OnSurfaceVariantLight = Color(0xFF49454F)
val OutlineLight = Color(0xFF79747E)
val OutlineVariantLight = Color(0xFFCAC4D0)
val SurfaceTintLight = PrimaryLight

// ── Dark palette — pure monochrome ──

val PrimaryDark = Color(0xFFE6E1E5)            // Off-white
val OnPrimaryDark = Color(0xFF1C1B1F)
val PrimaryContainerDark = Color(0xFF3E3D42)   // Dark gray
val OnPrimaryContainerDark = Color(0xFFE6E1E5)

val SecondaryDark = Color(0xFFCAC4D0)
val OnSecondaryDark = Color(0xFF1C1B1F)
val SecondaryContainerDark = Color(0xFF3E3D42)
val OnSecondaryContainerDark = Color(0xFFE6E1E5)

val TertiaryDark = Color(0xFFCAC4D0)
val OnTertiaryDark = Color(0xFF1C1B1F)
val TertiaryContainerDark = Color(0xFF3E3D42)
val OnTertiaryContainerDark = Color(0xFFE6E1E5)

val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)

val BackgroundDark = Color(0xFF1C1B1F)         // Near-black
val OnBackgroundDark = Color(0xFFE6E1E5)
val SurfaceDark = Color(0xFF1C1B1F)
val OnSurfaceDark = Color(0xFFE6E1E5)
val SurfaceVariantDark = Color(0xFF2B2930)     // Dark gray surface
val OnSurfaceVariantDark = Color(0xFFCAC4D0)
val OutlineDark = Color(0xFF938F99)
val OutlineVariantDark = Color(0xFF49454F)
val SurfaceTintDark = PrimaryDark

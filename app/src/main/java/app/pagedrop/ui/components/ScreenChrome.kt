package app.pagedrop.ui.components

import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import app.pagedrop.R
import app.pagedrop.ui.book.posterDuotone

/** The settings gear every tab shows in its top bar. */
@Composable
fun SettingsButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
}

/**
 * The screen colour with the poster drawn behind the content: full width at its own proportions, pinned to the
 * bottom. It is printed in two tones so its white parts are exactly the screen colour (no "sticker" patches),
 * at about a third strength in light mode and fainter in dark mode, so text stays readable over it.
 * Use it on a screen's root and give its Scaffold a transparent container.
 */
@Composable
fun Modifier.posterBackground(): Modifier {
    val background = MaterialTheme.colorScheme.background
    val ink = lerp(background, MaterialTheme.colorScheme.onBackground, if (background.luminance() < 0.5f) 0.16f else 0.32f)
    val filter = remember(background, ink) { posterDuotone(paper = background, ink = ink) }
    return this
        .background(background)
        .paint(
            painterResource(R.drawable.bezos_bg),
            sizeToIntrinsics = false,
            alignment = Alignment.BottomCenter,
            contentScale = ContentScale.FillWidth,
            colorFilter = filter,
        )
}

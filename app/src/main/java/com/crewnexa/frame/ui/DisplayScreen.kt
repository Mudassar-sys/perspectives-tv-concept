package com.crewnexa.frame.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay

/**
 * Display mode. This is the screen the product is actually sold on, so the
 * things that matter here are not features. They are absences.
 *
 * There must never be a spinner. A loading indicator on a wall reads as a fault,
 * not as patience, so the next image is decoded while the current one is still
 * up and the swap only happens once it is ready.
 *
 * The crossfade is long on purpose. Anything under a second reads as a slideshow
 * app. Photographs on a wall should change the way daylight changes.
 *
 * Nothing is drawn over the image except the label, and the label fades out
 * after a few seconds. The frame is meant to disappear.
 */
@Composable
fun DisplayScreen(
    items: List<DisplayItem>,
    durationSeconds: Int,
    paused: Boolean,
    onIndexChanged: (Int) -> Unit = {},
) {
    if (items.isEmpty()) return
    var index by remember { mutableIntStateOf(0) }

    LaunchedEffect(paused, durationSeconds, items.size) {
        if (paused) return@LaunchedEffect
        while (true) {
            delay(durationSeconds * 1000L)
            index = (index + 1) % items.size
            onIndexChanged(index)
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Two layers. The one underneath is always the next item, already
        // decoded by Coil into its memory cache, so the swap costs nothing.
        val next = items[(index + 1) % items.size]
        AsyncImage(
            model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(next.url)
                .crossfade(false)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        val current = items[index]
        AsyncImage(
            model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(current.url)
                .crossfade(CROSSFADE_MS)
                .build(),
            contentDescription = current.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        WallLabel(item = current)
    }
}

data class DisplayItem(
    val id: String,
    val url: String,
    val title: String,
    val credit: String,
)

private const val CROSSFADE_MS = 1200

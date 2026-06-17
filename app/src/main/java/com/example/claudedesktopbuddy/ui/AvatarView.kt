package com.example.claudedesktopbuddy.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import com.example.claudedesktopbuddy.R
import com.example.claudedesktopbuddy.buddy.BuddyState
import com.example.claudedesktopbuddy.buddy.CharacterPack
import com.example.claudedesktopbuddy.buddy.CharacterPresenter
import java.io.File
import kotlinx.coroutines.delay

/**
 * Renders the pushed character pack as an animated avatar. The animation tracks the buddy state
 * (sleep / idle / busy / attention); when a state lists several GIFs, they rotate as a carousel.
 *
 * The pack's GIFs are decoded by a Coil [ImageLoader] with the animated-image decoder. Nothing is
 * shown when the current (or fallback) state has no frames — the host only places this when a pack
 * exists, so a missing frame just yields an empty corner.
 */
@Composable
fun AvatarView(pack: CharacterPack, state: BuddyState, modifier: Modifier = Modifier) {
    val characterState = CharacterPresenter.stateFor(state)
    val frames = remember(pack, characterState) {
        CharacterPresenter.framesFor(pack.manifest, characterState)
    }
    if (frames.isEmpty()) return

    var rotation by remember(frames) { mutableIntStateOf(0) }
    // A multi-GIF state is a carousel; advance it on a steady interval (we can't see each GIF's
    // loop boundary, so a fixed step approximates the upstream "advance on loop-end" behaviour).
    if (frames.size > 1) {
        LaunchedEffect(frames) {
            while (true) {
                delay(CAROUSEL_INTERVAL_MS)
                rotation++
            }
        }
    }

    val frame = CharacterPresenter.frameAt(frames, rotation) ?: return
    val context = LocalContext.current
    val imageLoader = remember(context) { gifImageLoader(context) }
    val file = remember(pack.directoryPath, frame) { File(pack.directoryPath, frame) }

    AsyncImage(
        model = file,
        imageLoader = imageLoader,
        contentDescription = stringResource(R.string.avatar_description),
        contentScale = ContentScale.Fit,
        modifier = modifier
            .width(AVATAR_WIDTH)
            .heightIn(max = AVATAR_MAX_HEIGHT),
    )
}

/** Coil loader with animated-GIF support (the fast decoder on API 28+, the universal one below). */
private fun gifImageLoader(context: Context): ImageLoader =
    ImageLoader.Builder(context)
        .components {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                add(AnimatedImageDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
        }
        .build()

// Character art is 96px wide and up to ~140px tall; keep that aspect so it isn't distorted.
private val AVATAR_WIDTH = 96.dp
private val AVATAR_MAX_HEIGHT = 140.dp
private const val CAROUSEL_INTERVAL_MS = 5_000L

package com.guardianangel.ui

import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

/**
 * Plays one of her videos (round 77), with sound. [controls]: pause and seek, for Her videos only.
 * When she makes you watch, there are none, and [onEnd] runs when it's over (or if it can't play,
 * so a broken file never traps you).
 */
@Composable
fun ClipPlayer(file: File, modifier: Modifier = Modifier, controls: Boolean = false, loop: Boolean = false, onEnd: () -> Unit = {}) {
    val end by rememberUpdatedState(onEnd)
    AndroidView(
        factory = { ctx ->
            VideoView(ctx).apply {
                if (controls) setMediaController(MediaController(ctx).also { it.setAnchorView(this) })
                setOnPreparedListener { player -> player.isLooping = loop }
                setOnCompletionListener { if (!loop) end() }
                setOnErrorListener { _, _, _ ->
                    end()
                    true
                }
                setVideoPath(file.path)
                start()
            }
        },
        onRelease = { it.stopPlayback() },
        modifier = modifier,
    )
}

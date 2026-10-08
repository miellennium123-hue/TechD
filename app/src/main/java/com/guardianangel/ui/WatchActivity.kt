package com.guardianangel.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.guardianangel.core.Guardian
import com.guardianangel.core.SessionClips
import com.guardianangel.ui.theme.GuardianTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.TextButton
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.guardianangel.core.Line
import com.guardianangel.data.ClipSource

/**
 * Her full-screen clip (round 77, locked since round 92): a check-in clip, or one her bedtime or Caught
 * screen plays. Opening a check-in clip meets her one-minute deadline. It plays to the end with sound
 * and no controls, and until it ends her watch sends you back here from anywhere (never from calls).
 * Turn the screen off and it starts over when you're back. Back does nothing; Quit for now and an
 * emergency call are always here.
 */
class WatchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        onBackPressedDispatcher.addCallback(this) { /* Watch it first. */ }
        Guardian.init(this)
        Guardian.watchStarted()
        setContent { GuardianTheme { WatchScreen { finish() } } }
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, WatchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

@Composable
private fun WatchScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val forced = remember { Guardian.state.value.forcedClip }
    val file = remember { forced?.let { SessionClips.file(context, it.clip) }?.takeIf { it.exists() } }
    var line by remember {
        mutableStateOf(if (forced?.source == ClipSource.LOCK_SCREEN) Guardian.line(Line.WATCH_LOCKED) else Guardian.state.value.lastLine)
    }
    var ended by remember { mutableStateOf(false) }
    // Round 92: screen off, then back: the clip starts over from the beginning.
    var restarts by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var stopped = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> stopped = true
                Lifecycle.Event.ON_START -> if (stopped && !ended) {
                    stopped = false
                    restarts++
                    Guardian.clipRestarted()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun finished() {
        ended = true
        val said = Guardian.clipEnded()
        // A lock screen clip hands you straight back to her lock screen.
        if (said == null) onDone() else line = said
    }

    // Nothing to watch (already watched, or the clip was deleted): she lets it go.
    LaunchedEffect(file) {
        if (file == null) {
            if (forced != null || Guardian.state.value.watch != null) Guardian.clipEnded()
            onDone()
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeDrawingPadding().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (line.isNotBlank()) SpeechBubble(line)
            if (file != null && !ended) {
                key(restarts) {
                    ClipPlayer(file, Modifier.fillMaxWidth().weight(1f), onEnd = { finished() })
                }
                Text("Your phone is hers until it ends.", style = MaterialTheme.typography.titleMedium)
                // Calls are never blocked.
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }) { Text("Emergency call") }
            } else if (ended) {
                AngelImage(Modifier.weight(1f))
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
            }
            QuitButton(Modifier.fillMaxWidth()) {
                Guardian.quitForNow()
                onDone()
            }
        }
    }
}

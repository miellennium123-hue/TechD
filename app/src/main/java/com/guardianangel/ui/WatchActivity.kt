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

/**
 * A check-in made you watch one of your clips (round 77). Opening it meets her one-minute deadline;
 * it plays to the end with sound and no controls, then you tell her you watched. Back does nothing,
 * Quit for now is always here.
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
    val request = remember { Guardian.state.value.watch }
    val file = remember { request?.let { SessionClips.file(context, it.clip) }?.takeIf { it.exists() } }
    var line by remember { mutableStateOf(Guardian.state.value.lastLine) }
    var ended by remember { mutableStateOf(false) }

    // Nothing to watch (already watched, or the clip was deleted): she lets it go.
    LaunchedEffect(file) {
        if (file == null) {
            if (request != null) Guardian.watchFinished()
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
                ClipPlayer(file, Modifier.fillMaxWidth().weight(1f), onEnd = {
                    ended = true
                    line = Guardian.watchFinished()
                })
                Text("Watch to the end.", style = MaterialTheme.typography.titleMedium)
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

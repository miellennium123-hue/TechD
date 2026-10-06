package com.guardianangel.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.guardianangel.core.Guardian
import com.guardianangel.core.Line
import com.guardianangel.core.Rules
import com.guardianangel.core.SiteOpener
import com.guardianangel.data.Sites
import com.guardianangel.ui.theme.GuardianTheme

/**
 * Open sites: her full-screen warning with a 10 second countdown, then she opens the page.
 * Never shown on the lock screen. Quit for now is always here and ends it with no penalty.
 */
class SiteActivity : ComponentActivity() {
    /** Set once the page has been handed to the browser. This screen closes when it goes to the back. */
    private var handedOff = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        onBackPressedDispatcher.addCallback(this) { } // the countdown can't be backed out of; Quit for now can
        Guardian.visitWarned()
        setContent {
            GuardianTheme {
                SiteWarningScreen(
                    onOpened = { handedOff = true },
                    onDone = ::finish,
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Closing only after the browser is in front, so the app underneath never flashes up.
        if (handedOff || Guardian.state.value.visit == null) finish()
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, SiteActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

@Composable
private fun SiteWarningScreen(onOpened: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val config by Guardian.config.flow.collectAsState()
    val state by Guardian.state.flow.collectAsState()
    val visit = state.visit
    val now = rememberNow(250)
    var opened by remember { mutableStateOf(false) }
    val line = remember { Guardian.say(Line.SITE_WARNING) }

    LaunchedEffect(visit == null) { if (visit == null) onDone() }
    // Shown again after the page was already open: just go back to it.
    LaunchedEffect(visit?.id) {
        if (visit != null && visit.open && !opened) {
            opened = true
            SiteOpener.bringBack(context, visit.browser)
            onOpened()
        }
    }
    val left = if (visit != null && visit.warnedAt > 0) visit.warnedAt + Rules.SITE_WARNING_SECONDS * 1_000L - now else Rules.SITE_WARNING_SECONDS * 1_000L
    LaunchedEffect(left <= 0) {
        if (left <= 0 && visit != null && visit.warning && !opened) {
            opened = true
            Guardian.openVisit()
            onOpened()
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (config.discreetNotifications) {
                Text("Reminder", style = MaterialTheme.typography.headlineSmall)
                Text("Opening a page in", textAlign = TextAlign.Center)
            } else {
                AngelImage(Modifier.size(160.dp))
                SpeechBubble(line)
                Text(
                    visit?.let { "Opening ${Sites.host(it.url)} in" } ?: "Opening in",
                    textAlign = TextAlign.Center,
                )
            }
            val seconds = ((left.coerceAtLeast(0) + 999) / 1_000).toInt()
            Text(
                "$seconds",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Bold,
            )
            visit?.let {
                Muted(
                    "Stay on it for ${formatDuration(it.stayMs)}. Leaving early counts as a failure. " +
                        "Calls, the lock screen and this app pause the timer.",
                )
            }
            QuitButton(Modifier.fillMaxWidth()) {
                Guardian.quitForNow()
                onDone()
            }
            Muted("Phone and emergency calls are never blocked.")
        }
    }
}

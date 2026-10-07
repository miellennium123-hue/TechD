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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.guardianangel.core.Guardian
import com.guardianangel.core.PornBlock
import com.guardianangel.ui.theme.GuardianTheme

/**
 * Her caught screen (round 73): after Porn block catches you, it covers the home screen and every
 * app until her lock ends. Only your Always-allowed apps and the phone open from here. Back does
 * nothing; Quit for now is always here. Closes itself when the lock ends.
 */
class CaughtActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        onBackPressedDispatcher.addCallback(this) { /* Locked out: back stays here. */ }
        setContent { GuardianTheme { CaughtScreen(onOpen = ::open, onDone = ::done) } }
    }

    /** One of your Always-allowed apps, the dialer, or her app. */
    private fun open(intent: Intent) {
        runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        finish()
    }

    private fun done() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, CaughtActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

@Composable
private fun CaughtScreen(onOpen: (Intent) -> Unit, onDone: () -> Unit) {
    val config by Guardian.config.flow.collectAsState()
    val state by Guardian.state.flow.collectAsState()
    val now = rememberNow()
    val locked = PornBlock.locked(config, state, now)

    // Lock over, Porn block or she switched off, or Quit for now: she lets you go.
    LaunchedEffect(locked) { if (!locked) onDone() }

    val last = state.catches.lastOrNull()
    LockedOutScreen(
        line = last?.line.orEmpty(),
        title = when {
            last?.hiding == true -> "Caught hiding from her"
            last?.adultApp == true -> "Caught in ${last?.app}"
            else -> "Caught"
        },
        detail = "Your phone is locked for ${formatDuration(state.caughtUntil - now)}. No way in until then.",
        noApps = "None. Add some in Settings > App lockouts > Always-allowed apps, once her lock ends.",
        onOpen = onOpen,
        // Your adult apps stay locked even if Always-allowed (round 74).
        hidden = config.pornBlock.adultApps,
    )
}

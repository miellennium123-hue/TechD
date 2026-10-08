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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.guardianangel.core.Clips
import com.guardianangel.core.Guardian
import com.guardianangel.core.LockGuard
import com.guardianangel.core.SessionClips
import com.guardianangel.core.InstalledApps
import com.guardianangel.core.Line
import com.guardianangel.core.Rules
import com.guardianangel.data.ClipSource
import com.guardianangel.ui.theme.GuardianTheme
import java.io.File
import kotlin.random.Random

/**
 * Her bedtime screen (round 54): full screen over the home screen and every app bedtime blocks.
 * Only your Always-allowed apps and the phone open from here. Back does nothing; Quit for now is always here.
 * Closes itself when bedtime ends.
 */
class BedtimeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        onBackPressedDispatcher.addCallback(this) { /* Locked out: back stays here. */ }
        setContent { GuardianTheme { BedtimeScreen(onOpen = ::open, onDone = ::done) } }
    }

    /** One of your Always-allowed apps, or the dialer. */
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
            Intent(context, BedtimeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

@Composable
private fun BedtimeScreen(onOpen: (Intent) -> Unit, onDone: () -> Unit) {
    val config by Guardian.config.flow.collectAsState()
    val now = rememberNow()
    val line = remember { Guardian.say(Line.BEDTIME_SCREEN) }
    val bedtime = config.enabled && config.bedtime.screen && Rules.isBedtime(config.bedtime, Guardian.minuteOfDay())

    // Bedtime over, switched off, or Quit for now: she lets you go. Round 96: not in the middle of your
    // slow exit, so it isn't lost; it closes when you finish or leave it.
    val gstate by Guardian.state.flow.collectAsState()
    val quitting = LockGuard.quitting(gstate.quittingAt, now)
    LaunchedEffect(bedtime, quitting) { if (!bedtime && !quitting) onDone() }

    LockedOutScreen(
        line = line,
        title = "Locked out until ${formatMinuteOfDay(config.bedtime.endMinute)}",
        detail = "${formatDuration(untilEnd(config.bedtime.endMinute, now))} left. No way in until then.",
        noApps = "None. Add some in Settings > App lockouts > Always-allowed apps, outside bedtime.",
        onOpen = onOpen,
        clip = rememberLockScreenClip(),
    )
}

/**
 * Round 77: sometimes her lock screen opens by playing one of your clips (see Clips.onLockScreen).
 * Picked once each time the screen opens; null most of the time. Round 92: never within 5 minutes of
 * her last full-screen clip.
 */
@Composable
fun rememberLockScreenClip(): File? {
    val context = LocalContext.current
    return remember {
        val clips = SessionClips.videos(context)
        if (!Guardian.quitting() && Guardian.clipCooldownOver() && Clips.onLockScreen(Guardian.config.value, clips.size, Random.nextDouble())) {
            Clips.pick(clips, Random.Default)?.let { SessionClips.file(context, it.name) }
        } else {
            null
        }
    }
}

/**
 * Her full-screen lock (bedtime since round 54, her porn block since round 73): her line, how long,
 * then only your Always-allowed apps, the phone and her own app. Quit for now is always here.
 */
@Composable
fun LockedOutScreen(
    line: String,
    title: String,
    detail: String,
    noApps: String,
    onOpen: (Intent) -> Unit,
    hidden: Set<String> = emptySet(),
    clip: File? = null,
) {
    val context = LocalContext.current
    val config by Guardian.config.flow.collectAsState()
    // Round 92: her clip takes the whole screen and your phone until it ends (WatchActivity).
    LaunchedEffect(clip) {
        if (clip != null && Guardian.clipCooldownOver() && !Guardian.quitting()) {
            Guardian.forceClip(clip.name, ClipSource.LOCK_SCREEN)
            context.startActivity(WatchActivity.intent(context))
        }
    }
    val apps = remember(config.alwaysAllowed, hidden) {
        InstalledApps.launchable(context).filter { it.packageName in config.alwaysAllowed && it.packageName !in hidden }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AngelImage(Modifier.size(180.dp))
            if (line.isNotBlank()) SpeechBubble(line)
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Muted(detail)

            Text("Your unlocked apps", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (apps.isEmpty()) Muted(noApps)
            apps.forEach { app ->
                FilledTonalButton(
                    onClick = { context.packageManager.getLaunchIntentForPackage(app.packageName)?.let(onOpen) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(app.label) }
            }
            // Calls are never blocked, whatever the list says.
            OutlinedButton(onClick = { onOpen(Intent(Intent.ACTION_DIAL)) }, modifier = Modifier.fillMaxWidth()) {
                Text("Phone")
            }
            // Her own app is never blocked (round 55): her settings follow Lock guard's rules from inside it.
            OutlinedButton(onClick = { onOpen(Intent(context, MainActivity::class.java)) }, modifier = Modifier.fillMaxWidth()) {
                Text("Open Guardian Angel")
            }
            QuitButton(Modifier.fillMaxWidth()) { Guardian.quitForNow() }
        }
    }
}

/** Milliseconds from [now] until the next time the clock reads [endMinute]. */
private fun untilEnd(endMinute: Int, now: Long): Long {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
    val minute = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
    val minutes = ((endMinute - minute) + 24 * 60) % (24 * 60)
    return minutes * 60_000L - cal.get(java.util.Calendar.SECOND) * 1_000L
}

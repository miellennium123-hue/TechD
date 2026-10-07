package com.guardianangel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.guardianangel.core.Guardian
import com.guardianangel.core.Session
import com.guardianangel.core.SessionClips
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.GuardianState
import com.guardianangel.data.Sessions
import kotlin.math.roundToInt

/** Guided sessions (round 46): her own screen from Home, with Start, the kink menu and every session setting. */
@Composable
fun SessionsScreen(config: GuardianConfig, state: GuardianState, openKinks: () -> Unit, openClips: () -> Unit) {
    val context = LocalContext.current
    val update: ((GuardianConfig) -> GuardianConfig) -> Unit = { Guardian.updateConfig(it) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Both Start buttons always show (round 50), greyed out with the reason until she and sessions are on.
        val ready = config.enabled && config.session.on
        Button(
            onClick = { context.startActivity(SessionActivity.intent(context)) },
            enabled = ready,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Start a session") }
        OutlinedButton(
            onClick = { context.startActivity(SessionActivity.intent(context, quick = true)) },
            enabled = ready,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Quickshot (always ruined)") }
        Muted(
            when {
                !config.enabled -> "Switch her on (Settings, top card) to start either one."
                !config.session.on -> "Switch Guided sessions on below to start either one."
                else -> "Quickshot: about 2 minutes. Always a ruin, and she always films it."
            },
        )
        val clips = remember(state.sessions) { SessionClips.list(context).size }
        OutlinedButton(onClick = openClips, modifier = Modifier.fillMaxWidth()) { Text("Her videos ($clips)") }

        SectionCard("Settings") {
            SwitchRow(
                "Guided sessions",
                "She runs a session to her beat when you tap Start, and decides how it ends. During a chastity lock: " +
                    "cage-safe commands only, and never permission.",
                config.session.on,
            ) { v -> update { it.copy(session = it.session.copy(on = v)) } }
            Stepper(
                "Length",
                formatMinutes(config.session.minutes),
                onMinus = { update { it.copy(session = it.session.copy(minutes = (it.session.minutes - 5).coerceIn(Sessions.MIN_MINUTES, Sessions.MAX_MINUTES))) } },
                onPlus = { update { it.copy(session = it.session.copy(minutes = (it.session.minutes + 5).coerceIn(Sessions.MIN_MINUTES, Sessions.MAX_MINUTES))) } },
                enabled = config.session.on,
            )
            OutlinedButton(onClick = openKinks) { Text("Kink menu (${config.session.kinks.size} on)") }
            val (p, r, d) = Session.endingShares(config.session, caged = false)
            Text("Endings")
            EndingSlider("Permission", config.session.permissionWeight, p, config.session.on) { v ->
                update { it.copy(session = it.session.copy(permissionWeight = v)) }
            }
            EndingSlider("Ruined", config.session.ruinWeight, r, config.session.on) { v ->
                update { it.copy(session = it.session.copy(ruinWeight = v)) }
            }
            EndingSlider("Denied", config.session.denialWeight, d, config.session.on) { v ->
                update { it.copy(session = it.session.copy(denialWeight = v)) }
            }
            Muted("Set the others to 0 for one ending only. All 0 means denied. During a lock, permission never comes up.")
            SwitchRow("Beat sound", "A tick on every beat, as well as the pulse.", config.session.beatSound) { v ->
                update { it.copy(session = it.session.copy(beatSound = v)) }
            }
            Muted(
                "The camera is on for every session, so you watch yourself the whole time. She always films a ruin. " +
                    "Clips have sound if you allow the microphone, and stay private to this app.",
            )
            SwitchRow(
                "She films your edges and CBT",
                "Every edge, until you tap \"I'm at the edge\" (up to 3 minutes), and every CBT count.",
                config.session.filmTasks,
            ) { v -> update { it.copy(session = it.session.copy(filmTasks = v)) } }
            Text("She makes you watch your clips", style = MaterialTheme.typography.titleSmall)
            SwitchRow(
                "During sessions",
                "Every session, quickshots too, she stops once and plays one of your clips. You see yourself in the corner.",
                config.session.watchInSessions,
            ) { v -> update { it.copy(session = it.session.copy(watchInSessions = v)) } }
            SwitchRow(
                "At check-ins",
                "About every other check-in sends a clip. Open it within a minute or it's a failure. Never in quiet time, and only " +
                    "when she can send notifications.",
                config.session.watchAtCheckIns,
            ) { v -> update { it.copy(session = it.session.copy(watchAtCheckIns = v)) } }
            SwitchRow(
                "On her lock screens",
                "About every other time her bedtime or Caught screen opens, it plays a clip first, with sound. Phone, " +
                    "Always-allowed apps and Quit for now stay right there.",
                config.session.watchOnLockScreens,
            ) { v -> update { it.copy(session = it.session.copy(watchOnLockScreens = v)) } }
            if (config.lockGuard) Muted("Lock guard: switching check-ins or lock screens off takes 30 minutes.")
            if (state.sessions.isNotEmpty()) {
                Muted("Last sessions: " + state.sessions.takeLast(5).joinToString(", ") {
                    (if (it.quick) "quickshot " else "") + it.outcome.name.lowercase().replace('_', ' ')
                })
                TextButton(onClick = { Guardian.clearSessions() }) { Text("Clear session history") }
            }
        }
    }
}

/** One ending's weight, 0 to 100 in steps of 10, with its share right now. */
@Composable
private fun EndingSlider(label: String, weight: Int, share: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    Column {
        Text("$label: $share%", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = weight.toFloat(),
            onValueChange = { onChange((it / 10).roundToInt() * 10) },
            valueRange = 0f..100f,
            steps = 9,
            enabled = enabled,
        )
    }
}

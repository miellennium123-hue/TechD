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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.guardianangel.core.Guardian
import com.guardianangel.core.Session
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.GuardianState
import com.guardianangel.data.MotionSensitivity
import com.guardianangel.data.Sessions
import kotlin.math.roundToInt

/** Guided sessions (round 46): her own screen from Home, with Start, the kink menu and every session setting. */
@Composable
fun SessionsScreen(config: GuardianConfig, state: GuardianState, openKinks: () -> Unit) {
    val context = LocalContext.current
    val update: ((GuardianConfig) -> GuardianConfig) -> Unit = { Guardian.updateConfig(it) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            !config.enabled -> Muted("Switch her on to start a session.")
            !config.session.on -> Muted("Switch guided sessions on below to start one.")
            else -> {
                Button(
                    onClick = { context.startActivity(SessionActivity.intent(context)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Start a session") }
                OutlinedButton(
                    onClick = { context.startActivity(SessionActivity.intent(context, quick = true)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Quickshot (always ruined)") }
                Muted("About 2 minutes. Always a ruin, and she always films it, even with the camera setting off.")
            }
        }

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
            SwitchRow(
                "She watches (camera)",
                "Front camera on: if she can't see you while you should be stroking, she scolds you and adds an edge. " +
                    "She films a ruin as proof. Checked on your phone; only the ruin clip is saved, privately.",
                config.session.camera,
            ) { v -> update { it.copy(session = it.session.copy(camera = v)) } }
            SwitchRow(
                "She checks your motion",
                "With the camera on: she checks you keep her beat when stroking and stop when she says. " +
                    "Off beat or moving too long: she scolds you and adds an edge. Only numbers are kept, never frames.",
                config.session.motionChecks,
            ) { v -> update { it.copy(session = it.session.copy(motionChecks = v)) } }
            if (config.session.camera && config.session.motionChecks) {
                Text("Motion sensitivity")
                ChoiceChips(MotionSensitivity.entries, config.session.motionSensitivity, { it.label }) { level ->
                    update { it.copy(session = it.session.copy(motionSensitivity = level)) }
                }
                Muted("Caught when still? Try Low. Moving and she doesn't notice? Try High.")
            }
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

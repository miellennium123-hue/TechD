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
import com.guardianangel.core.SessionPlan
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.GuardianState
import com.guardianangel.data.SessionOutcome
import com.guardianangel.data.SessionTheme
import com.guardianangel.data.Sessions
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
            SwitchRow(
                "Her voice",
                "She says every command out loud and counts CBT for you, so you can watch yourself instead of reading. " +
                    "Uses your phone's own voice, at media volume.",
                config.session.voice,
            ) { v -> update { it.copy(session = it.session.copy(voice = v)) } }
            SwitchRow(
                "Whisper late at night",
                "From 22:00 to 06:00 her voice drops to a slow, quiet whisper.",
                config.session.whisper,
            ) { v -> update { it.copy(session = it.session.copy(whisper = v)) } }
            SwitchRow(
                "Big text",
                "Her words, the beat and the edge button big enough to read and hit from across the room.",
                config.session.bigText,
            ) { v -> update { it.copy(session = it.session.copy(bigText = v)) } }
            Text("Her plans", style = MaterialTheme.typography.titleSmall)
            SwitchRow(
                "She decides the length",
                "Each session runs ${Sessions.MIN_MINUTES} to ${Sessions.HER_MAX_MINUTES} minutes, her choice, and she doesn't tell you how long.",
                config.session.herLength,
            ) { v -> update { it.copy(session = it.session.copy(herLength = v)) } }
            SwitchRow(
                "Her training program",
                "Every week since you started: 3 more minutes, and she lets you cum less often. Up to week ${SessionPlan.MAX_WEEK}.",
                config.session.training,
            ) { v -> update { it.copy(session = it.session.copy(training = v)) } }
            if (config.session.training) {
                Muted("You're in week ${SessionPlan.week(state.trainingStart, Guardian.now()).coerceAtLeast(1)}. Switching it off and on starts over at week 1.")
            }
            SwitchRow(
                "Punishment sessions",
                "After any failure your next session is a punishment: CBT-heavy if you've allowed CBT, and always ruined.",
                config.session.punishmentSessions,
            ) { v -> update { it.copy(session = it.session.copy(punishmentSessions = v)) } }
            if (config.session.punishmentSessions && state.owedPunishment) {
                Text("You owe her a punishment session.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            SwitchRow(
                "A ruin after a Porn block catch",
                "When Porn block catches you, you owe her a ruined session within ${SessionPlan.RUIN_OWED_HOURS} hours. Miss it and it's a failure. " +
                    "Only when she can send notifications.",
                config.session.ruinAfterCatch,
            ) { v -> update { it.copy(session = it.session.copy(ruinAfterCatch = v)) } }
            if (state.ruinOwedBy > Guardian.now()) {
                val by = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(state.ruinOwedBy))
                Text("You owe her a ruin by $by.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            SwitchRow(
                "Booked sessions",
                "She books your next session a day or two ahead, at a time she picks (never in quiet hours). A reminder 15 minutes " +
                    "before; start within 15 minutes of her time or it's a failure. Then she books the next.",
                config.session.booked,
            ) { v -> update { it.copy(session = it.session.copy(booked = v)) } }
            state.booked?.takeIf { !it.kept }?.let { b ->
                val at = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(b.at))
                Text("Your next session: $at", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
            if (config.lockGuard) Muted("Lock guard: switching off her training, punishment sessions, the ruin after a catch or booked sessions takes 30 minutes.")
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
        }

        // Round 79: your sessions, with what she counted.
        if (state.sessions.isNotEmpty()) {
            SectionCard("Your sessions") {
                val all = state.sessions
                Text(
                    "${all.size} sessions · ${all.sumOf { it.edges }} edges · " +
                        (all.filter { it.fastestEdge > 0 }.minOfOrNull { it.fastestEdge }?.let { "fastest edge ${it}s · " } ?: "") +
                        "${all.count { it.outcome == SessionOutcome.RUINED }} ruined · " +
                        "${all.count { it.outcome == SessionOutcome.FINISHED }} finished · " +
                        "${all.count { it.outcome == SessionOutcome.DENIED }} denied",
                    style = MaterialTheme.typography.bodyMedium,
                )
                val date = remember { SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()) }
                all.takeLast(10).reversed().forEach { r ->
                    Column {
                        Text(
                            date.format(Date(r.at)) + if (r.quick) " · quickshot" else " · ${r.minutes} min",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Muted(
                            listOfNotNull(
                                outcomeLabel(r.outcome),
                                "${r.edges} edges".takeIf { r.edges > 0 },
                                "fastest ${r.fastestEdge}s".takeIf { r.fastestEdge > 0 },
                                r.theme.label.takeIf { r.theme != SessionTheme.YOURS },
                                r.deal.ifBlank { null },
                                "${r.filmed} saved".takeIf { r.filmed > 0 },
                                "locked".takeIf { r.caged },
                            ).joinToString(" · "),
                        )
                    }
                }
                Muted("She keeps your last ${Sessions.HISTORY}. Counts start from version 0.26.0.")
                TextButton(onClick = { Guardian.clearSessions() }) { Text("Clear session history") }
            }
        }
    }
}

private fun outcomeLabel(outcome: SessionOutcome): String = when (outcome) {
    SessionOutcome.FINISHED -> "finished"
    SessionOutcome.RUINED -> "ruined"
    SessionOutcome.RUIN_FAILED -> "couldn't stop"
    SessionOutcome.DENIED -> "denied"
    SessionOutcome.MISSED_COMMAND -> "missed her command"
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

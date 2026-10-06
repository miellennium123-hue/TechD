package com.guardianangel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.guardianangel.core.Guardian
import com.guardianangel.core.MINUTE
import com.guardianangel.core.Rules
import com.guardianangel.data.ChastitySettings
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.GuardianState
import com.guardianangel.data.ProofReason
import java.text.DateFormat
import java.util.Date

@Composable
fun ChastityScreen(config: GuardianConfig, state: GuardianState) {
    val context = LocalContext.current
    val now = rememberNow()
    val lock = state.chastity
    val settings = config.chastity
    val update: ((GuardianConfig) -> GuardianConfig) -> Unit = { Guardian.updateConfig(it) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AngelImage(Modifier.size(140.dp))
        if (state.lastLine.isNotBlank()) SpeechBubble(state.lastLine)

        when {
            !config.chastity.on && lock == null -> Muted("Chastity mode is off. Turn it on in the settings below.")
            !config.enabled && lock == null -> Muted("Switch her on first.")
            lock == null -> SectionCard("Ready to be locked?") {
                Muted(
                    "She picks a timer between ${formatMinutes(minOf(settings.minLockMinutes, settings.maxLockMinutes))} and " +
                        "${formatMinutes(maxOf(settings.minLockMinutes, settings.maxLockMinutes))}, " +
                        "never longer than ${settings.maxHours}h. You'll owe her a photo within 15 minutes.",
                )
                Button(onClick = { Guardian.startChastity() }) { Text("Lock me up") }
            }
            else -> {
                val left = lock.endsAt - now
                SectionCard("Locked") {
                    Text(
                        if (left > 0) formatDuration(left) else "Time's up",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold,
                    )
                    val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    Muted("Locked since ${fmt.format(Date(lock.startedAt))}. Ends ${fmt.format(Date(lock.endsAt))}.")
                    if (lock.addedMinutes > 0) Muted("She has added ${formatMinutes(lock.addedMinutes)}.")
                    Muted(if (lock.proofReceived) "Proof of lock received." else "She is still waiting for proof of lock.")
                }

                state.proofs.filter { it.reason == ProofReason.CHASTITY_LOCK || it.reason == ProofReason.CHASTITY_CHECK }
                    .forEach { request ->
                        Button(onClick = { context.startActivity(ProofActivity.intent(context, request.id)) }) {
                            Text("Send proof (${formatDuration(request.dueAt - now)} left)")
                        }
                    }

                if (left <= 0) {
                    Button(onClick = { Guardian.requestRelease() }) { Text("Release me") }
                } else {
                    val begAgainAt = state.lastBegAt + Rules.BEG_COOLDOWN_MINUTES * MINUTE
                    if (state.lastBegAt > 0 && now < begAgainAt) {
                        Text("You may beg again in ${formatDuration(begAgainAt - now)}")
                    } else {
                        OutlinedButton(onClick = { Guardian.requestRelease() }) { Text("Beg to be released") }
                    }
                    Muted(
                        "She decides. A strict mood makes her harder to convince." +
                            if (settings.canAddTime) " A denial may add ${formatMinutes(settings.addMinutes)}." else "",
                    )
                }
            }
        }
        Muted("\"Quit for now\" always ends the lock, with no penalty. It takes about 10 minutes.")

        SectionCard("Chastity settings") {
            SwitchRow("Chastity mode", "Turn off for days without the cage.", config.chastity.on) { v ->
                update { it.copy(chastity = it.chastity.copy(on = v)) }
            }
            Muted("She picks a lock length between these two.")
            Stepper(
                "Shortest lock",
                formatMinutes(config.chastity.minLockMinutes),
                onMinus = { update { it.copy(chastity = it.chastity.withMinLock(Rules.stepLockMinutes(it.chastity.minLockMinutes, up = false))) } },
                onPlus = { update { it.copy(chastity = it.chastity.withMinLock(Rules.stepLockMinutes(it.chastity.minLockMinutes, up = true))) } },
            )
            Stepper(
                "Longest picked lock",
                formatMinutes(config.chastity.maxLockMinutes),
                onMinus = { update { it.copy(chastity = it.chastity.withMaxLock(Rules.stepLockMinutes(it.chastity.maxLockMinutes, up = false))) } },
                onPlus = { update { it.copy(chastity = it.chastity.withMaxLock(Rules.stepLockMinutes(it.chastity.maxLockMinutes, up = true))) } },
            )
            SwitchRow(
                "She can add time",
                "For failures, missed proof, denied begging, or at her whim.",
                config.chastity.canAddTime,
            ) { v ->
                update { it.copy(chastity = it.chastity.copy(canAddTime = v)) }
            }
            Stepper(
                "Amount per addition",
                formatMinutes(config.chastity.addMinutes),
                onMinus = { update { it.copy(chastity = it.chastity.copy(addMinutes = (it.chastity.addMinutes - 15).coerceAtLeast(15))) } },
                onPlus = { update { it.copy(chastity = it.chastity.copy(addMinutes = (it.chastity.addMinutes + 15).coerceAtMost(24 * 60))) } },
                enabled = config.chastity.canAddTime,
            )
            Stepper(
                "Hard cap (incl. added time)",
                "${config.chastity.maxHours}h",
                onMinus = { update { it.copy(chastity = it.chastity.copy(maxHours = Rules.stepCapHours(it.chastity.maxHours, up = false))) } },
                onPlus = { update { it.copy(chastity = it.chastity.copy(maxHours = Rules.stepCapHours(it.chastity.maxHours, up = true))) } },
            )
            if (config.chastity.maxLockMinutes > config.chastity.maxHours * 60) {
                Text(
                    "The hard cap is shorter than your longest picked lock, so locks stop at ${config.chastity.maxHours}h.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private const val LOCK_MIN = 30
private const val LOCK_LIMIT = 7 * 24 * 60

/** Keeps shortest <= longest: moving one past the other drags it along. */
private fun ChastitySettings.withMinLock(minutes: Int): ChastitySettings {
    val m = minutes.coerceIn(LOCK_MIN, LOCK_LIMIT)
    return copy(minLockMinutes = m, maxLockMinutes = maxOf(maxLockMinutes, m))
}

private fun ChastitySettings.withMaxLock(minutes: Int): ChastitySettings {
    val m = minutes.coerceIn(LOCK_MIN, LOCK_LIMIT)
    return copy(maxLockMinutes = m, minLockMinutes = minOf(minLockMinutes, m))
}


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

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AngelImage(Modifier.size(140.dp))
        if (state.lastLine.isNotBlank()) SpeechBubble(state.lastLine)

        when {
            !config.chastity.on && lock == null -> Muted("Chastity mode is off. Turn it on in Settings.")
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
        Muted("\"Quit for now\" always ends the lock instantly, with no penalty.")
    }
}

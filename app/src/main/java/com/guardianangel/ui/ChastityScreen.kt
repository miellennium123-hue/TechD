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
import com.guardianangel.data.Intensity
import com.guardianangel.data.ProofReason
import java.text.DateFormat
import java.util.Date

@Composable
fun ChastityScreen(config: GuardianConfig, state: GuardianState) {
    val context = LocalContext.current
    val now = rememberNow()
    val lock = state.chastity
    val intensity = config.chastity.intensity

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
                val range = Rules.lockRangeMinutes(intensity)
                Text("Intensity: ${intensity.ordinal + 1} ${intensity.label}")
                Muted(
                    "She picks a timer between ${formatMinutes(range.first)} and ${formatMinutes(range.last)}, " +
                        "never longer than ${config.chastity.maxHours}h. You'll owe her a photo within 15 minutes.",
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
                    val waitUntil = state.releaseRequestedAt + Rules.EARLY_RELEASE_WAIT_MINUTES * MINUTE
                    val waiting = intensity == Intensity.FIRM && state.releaseRequestedAt > 0 && now < waitUntil
                    if (waiting) {
                        Text("You may ask again in ${formatDuration(waitUntil - now)}")
                    } else {
                        OutlinedButton(onClick = { Guardian.requestRelease() }) {
                            Text(
                                when (intensity) {
                                    Intensity.GENTLE -> "Unlock early"
                                    Intensity.FIRM -> if (state.releaseRequestedAt > 0) "Unlock now" else "Request early release"
                                    Intensity.STRICT, Intensity.ABSOLUTE -> "Beg to be released"
                                },
                            )
                        }
                    }
                    if (intensity == Intensity.ABSOLUTE) {
                        Text("Absolute: begging early counts as a failure.", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        Muted("\"Quit for now\" always ends the lock instantly, with no penalty.")
    }
}

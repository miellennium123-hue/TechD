package com.guardianangel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.guardianangel.core.Guardian
import com.guardianangel.core.Permissions
import com.guardianangel.core.Rules
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.GuardianState
import com.guardianangel.data.ProofReason
import com.guardianangel.data.TaskKind

@Composable
fun HomeScreen(config: GuardianConfig, state: GuardianState, navigate: (Screen) -> Unit) {
    val context = LocalContext.current
    val now = rememberNow()
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    val setupDone = remember(resumes) { Permissions.coreReady(context) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AngelImage(Modifier.size(180.dp).align(Alignment.CenterHorizontally))
        SpeechBubble(state.lastLine.ifBlank { "Switch me on when you're ready to be watched over, pet." })

        SectionCard("Master switch") {
            SwitchRow(
                title = if (config.enabled) "She is watching" else "She is off",
                subtitle = "You consent by turning her on. You can always turn her off.",
                checked = config.enabled,
                onChange = { Guardian.setEnabled(it) },
            )
        }

        if (!setupDone) {
            SectionCard("Finish setup") {
                Muted("She needs a few permissions to lock apps and check in on time.")
                Button(onClick = { navigate(Screen.SETUP) }) { Text("Open permissions") }
            }
        }

        if (config.enabled && state.checkInPending) {
            SectionCard("She's checking in") {
                Button(onClick = { Guardian.acknowledgeCheckIn() }) { Text("I'm here and being good") }
            }
        }

        if (state.summons != null) {
            SectionCard("She wants you") {
                val lockIn = state.summons.lockAt - now
                Text(
                    if (lockIn > 0) "Answer within ${formatDuration(lockIn)}" else "Everything is locked until you answer.",
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Bold,
                )
                Button(onClick = { context.startActivity(ShowUpActivity.intent(context)) }) { Text("Go to her") }
            }
        }

        state.task?.let { task ->
            SectionCard(if (task.kind == TaskKind.RULE) "Her rule" else "Her task") {
                Text(task.text)
                val left = (if (task.kind == TaskKind.RULE && now < task.ruleUntil) task.ruleUntil else task.dueAt) - now
                Text(
                    when {
                        task.kind == TaskKind.RULE && now < task.ruleUntil -> "${formatDuration(left)} left"
                        task.kind == TaskKind.RULE -> "Report back: ${formatDuration(left)} left"
                        else -> "Due in ${formatDuration(left)}"
                    },
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Bold,
                )
                Button(onClick = { navigate(Screen.TASK) }) { Text("Open") }
            }
        }

        state.proofs.filter { it.reason.penalized || it.dueAt > now }.forEach { request ->
            SectionCard(if (request.reason == ProofReason.TASK) "Her task" else "Photo proof requested") {
                Text("Photograph: ${request.subject}")
                if (request.explicit) Muted("Explicit. She'll check it on your phone.")
                val left = request.dueAt - now
                Text(
                    if (left > 0) "Due in ${formatDuration(left)}" else "Overdue",
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Bold,
                )
                Button(onClick = { context.startActivity(ProofActivity.intent(context, request.id)) }) {
                    Text("Take photo")
                }
            }
        }

        if (config.enabled && state.punishmentUntil > now) {
            SectionCard("Punishment lockout") {
                Text("${formatDuration(state.punishmentUntil - now)} left", fontWeight = FontWeight.Bold)
            }
        }

        if (config.enabled && Rules.isBedtime(config.bedtime, Guardian.minuteOfDay())) {
            SectionCard("Bedtime") {
                Text("Phone down until ${formatMinuteOfDay(config.bedtime.endMinute)}.")
            }
        }

        state.chastity?.let { lock ->
            SectionCard("Chastity") {
                val left = lock.endsAt - now
                Text(
                    if (left > 0) formatDuration(left) else "Time's up. Ask to be released.",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                OutlinedButton(onClick = { navigate(Screen.CHASTITY) }) { Text("Open chastity") }
            }
        }

        if (config.meritOn) {
            val level = Rules.meritLevel(state.merit)
            SectionCard("Merit") {
                Text("Level ${level.level}: ${level.title}", style = MaterialTheme.typography.titleLarge)
                Text("${state.merit} points" + (level.next?.let { " (next level at $it)" } ?: ""))
                LinearProgressIndicator(progress = { level.progress(state.merit) }, modifier = Modifier.fillMaxWidth())
            }
        }

        val photoTaskOpen = state.proofs.any { it.reason == ProofReason.TASK }
        if (config.enabled && config.tasksOn && state.task == null && !photoTaskOpen) {
            OutlinedButton(onClick = { Guardian.issueTask() }, modifier = Modifier.fillMaxWidth()) { Text("Ask her for a task") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (config.chastity.on && state.chastity == null) {
                OutlinedButton(onClick = { navigate(Screen.CHASTITY) }, modifier = Modifier.weight(1f)) { Text("Chastity") }
            }
            OutlinedButton(onClick = { navigate(Screen.GALLERY) }, modifier = Modifier.weight(1f)) { Text("Photos") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { navigate(Screen.SETTINGS) }, modifier = Modifier.weight(1f)) { Text("Settings") }
            OutlinedButton(onClick = { navigate(Screen.SETUP) }, modifier = Modifier.weight(1f)) { Text("Permissions") }
        }
        Muted("\"Quit for now\" (top right) instantly ends every lock, timer and restriction and switches her off.")
    }
}

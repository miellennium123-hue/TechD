package com.guardianangel.ui

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.guardianangel.core.Guardian
import com.guardianangel.core.Line
import com.guardianangel.core.Lines
import com.guardianangel.core.MINUTE
import com.guardianangel.core.StillnessJudge
import com.guardianangel.data.ActiveTask
import com.guardianangel.data.GuardianState
import com.guardianangel.data.RuleEnforcement
import com.guardianangel.data.TaskKind

/** Where you carry out her current rule or task. */
@Composable
fun TaskScreen(state: GuardianState) {
    val task = state.task
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AngelImage(Modifier.size(120.dp))
        if (state.lastLine.isNotBlank()) SpeechBubble(state.lastLine)
        when (task?.kind) {
            null -> Muted("No task right now. Photo tasks show up on the home screen.")
            TaskKind.RULE -> RuleCard(task)
            TaskKind.STILLNESS -> StillnessCard(task)
            TaskKind.LINES -> LinesCard(task)
            TaskKind.PHOTO -> Unit
        }
        Muted("\"Quit for now\" always ends any task instantly, with no penalty.")
    }
}

@Composable
private fun DueLine(task: ActiveTask, now: Long) {
    Text(
        if (task.dueAt > now) "Due in ${formatDuration(task.dueAt - now)}" else "Overdue",
        color = MaterialTheme.colorScheme.tertiary,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun RuleCard(task: ActiveTask) {
    val now = rememberNow()
    SectionCard("Her rule") {
        Text(task.text, style = MaterialTheme.typography.titleMedium)
        if (now < task.ruleUntil) {
            Text("${formatDuration(task.ruleUntil - now)} left", fontWeight = FontWeight.Bold)
            Muted(
                when (task.enforce) {
                    RuleEnforcement.NONE -> "On your honor. When it ends, report back within ${formatMinutes(((task.dueAt - task.ruleUntil) / MINUTE).toInt())}."
                    RuleEnforcement.SOCIAL_MEDIA -> "She's locking social media until it ends. No way in."
                    RuleEnforcement.EVERYTHING -> "She's locking everything except your Always-allowed apps until it ends. No way in."
                },
            )
        } else if (!task.enforced) {
            Text("Time's up. Did you obey?")
            DueLine(task, now)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { Guardian.finishTask() }) { Text("I obeyed") }
                OutlinedButton(onClick = { Guardian.failTask(Line.FAIL_NEUTRAL) }) { Text("I broke it") }
            }
        }
    }
}

private enum class Phase { READY, SETTLING, HOLDING }

private const val SETTLE_SECONDS = 5

@Composable
private fun StillnessCard(task: ActiveTask) {
    val context = LocalContext.current
    val view = LocalView.current
    val now = rememberNow(250)
    val sensors = remember { context.getSystemService(SensorManager::class.java) }
    val accelerometer = remember { sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    var phase by remember(task.id) { mutableStateOf(Phase.READY) }
    var phaseEndsAt by remember(task.id) { mutableLongStateOf(0L) }
    var note by remember(task.id) { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    // Leaving the screen stops the attempt. It isn't a failure; start again before the deadline.
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        if (phase != Phase.READY) {
            phase = Phase.READY
            note = "You left the screen, so that attempt doesn't count. Start again."
        }
    }
    DisposableEffect(phase == Phase.HOLDING) {
        if (phase != Phase.HOLDING || sensors == null || accelerometer == null) return@DisposableEffect onDispose { }
        val judge = StillnessJudge()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (phase != Phase.HOLDING) return
                if (judge.add(event.values[0], event.values[1], event.values[2], event.timestamp / 1_000_000)) {
                    phase = Phase.READY
                    Guardian.failTask(Line.MOVED)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensors.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sensors.unregisterListener(listener) }
    }
    LaunchedEffect(now) {
        if (phase == Phase.SETTLING && now >= phaseEndsAt) {
            phase = Phase.HOLDING
            phaseEndsAt = now + task.minutes * MINUTE
        } else if (phase == Phase.HOLDING && now >= phaseEndsAt) {
            phase = Phase.READY
            Guardian.finishTask()
        }
    }

    SectionCard("Stillness") {
        Text(task.text, style = MaterialTheme.typography.titleMedium)
        when (phase) {
            Phase.READY -> {
                DueLine(task, now)
                Muted(
                    "Hold still for ${formatMinutes(task.minutes)}. You get $SETTLE_SECONDS seconds to get into position. " +
                        "If you move, you fail. Leaving this screen just cancels the attempt.",
                )
                if (accelerometer == null) Muted("This phone has no motion sensor, so she'll trust you.")
                note?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                // Starting a hold that can't finish before the deadline would only fail you halfway through.
                val needed = task.minutes * MINUTE + SETTLE_SECONDS * 1_000L
                if (task.dueAt - now < needed) {
                    Text("Too late to finish before the deadline.", color = MaterialTheme.colorScheme.error)
                } else {
                    Button(onClick = {
                        note = null
                        phase = Phase.SETTLING
                        phaseEndsAt = System.currentTimeMillis() + SETTLE_SECONDS * 1_000L
                    }) { Text("Start") }
                    Muted("Start by ${formatDuration(task.dueAt - now - needed)} from now to finish in time.")
                }
            }
            Phase.SETTLING -> Text(
                "Get into position... ${formatDuration(phaseEndsAt - now + 999)}",
                style = MaterialTheme.typography.headlineSmall,
            )
            Phase.HOLDING -> {
                Text(
                    formatDuration(phaseEndsAt - now + 999),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Bold,
                )
                Muted("Don't move.")
            }
        }
    }
}

@Composable
private fun LinesCard(task: ActiveTask) {
    val now = rememberNow()
    var text by rememberSaveable(task.id) { mutableStateOf("") }
    var note by remember(task.id) { mutableStateOf<String?>(null) }
    // Progress lives in the saved task, so leaving the screen or the app doesn't lose it.
    val done = task.linesDone
    val total = task.lines.coerceAtLeast(1)

    SectionCard("Lines: ${Lines.LABELS[task.difficulty.coerceIn(0, 2)]}") {
        Text("Type this $total times:")
        Text(task.sentence, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        DueLine(task, now)
        Text("Line ${minOf(done + 1, total)} of $total")
        LinearProgressIndicator(progress = { done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = text,
            onValueChange = { next ->
                when (Lines.check(text, next, task.sentence)) {
                    Lines.Input.OK -> {
                        text = next
                        note = null
                    }
                    Lines.Input.LINE_DONE -> {
                        text = ""
                        note = null
                        if (done + 1 >= total) Guardian.finishTask() else Guardian.setLinesDone(done + 1)
                    }
                    Lines.Input.TYPO -> {
                        text = ""
                        Guardian.setLinesDone(0)
                        note = Guardian.say(Line.TYPO)
                    }
                    Lines.Input.PASTE -> note = "Type it yourself, one letter at a time. No pasting or suggestions."
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            // A password keyboard turns off suggestions and autocorrect; the text stays visible.
            visualTransformation = VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
            ),
        )
        note?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Muted("Any typo sends you back to line 1. Capitals don't matter. You can leave and come back; finished lines are kept.")
    }
}

package com.guardianangel.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.guardianangel.core.Guardian
import com.guardianangel.core.Permissions
import com.guardianangel.core.Session
import com.guardianangel.core.SessionScript
import com.guardianangel.core.Step
import com.guardianangel.core.StepKind
import com.guardianangel.data.Kink
import com.guardianangel.data.SessionEnding
import com.guardianangel.data.SessionOutcome
import com.guardianangel.data.SessionRecord
import com.guardianangel.ui.theme.GuardianTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.random.Random

/** A guided session: her commands, her beat, the front camera watching, and her ending. */
class SessionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        setContent { GuardianTheme { SessionScreen { finish() } } }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, SessionActivity::class.java)
    }
}

private enum class SessionPhase { SETUP, RUNNING, HONOR, DONE }

@Composable
private fun SessionScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val config by Guardian.config.flow.collectAsState()
    val settings = config.session
    val caged = remember { Guardian.caged() }

    var phase by remember { mutableStateOf(SessionPhase.SETUP) }
    var soundingReady by remember { mutableStateOf(false) }
    var hasCamera by remember { mutableStateOf(Permissions.camera(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }
    var script by remember { mutableStateOf<SessionScript?>(null) }
    var steps by remember { mutableStateOf<List<Step>>(emptyList()) }
    var index by remember { mutableIntStateOf(0) }
    var caught by remember { mutableIntStateOf(0) }
    var skipped by remember { mutableIntStateOf(0) }
    var finalLine by remember { mutableStateOf("") }
    var relockProof by remember { mutableStateOf<Long?>(null) }
    var clip by remember { mutableStateOf<File?>(null) }
    var clipFailed by remember { mutableStateOf(false) }

    val watching = settings.camera && hasCamera

    fun end(outcome: SessionOutcome) {
        val s = script ?: return
        val (line, proof) = Guardian.finishSession(
            SessionRecord(System.currentTimeMillis(), settings.minutes, s.ending, caged, caught, skipped, outcome),
        )
        finalLine = line
        relockProof = proof
        phase = SessionPhase.DONE
    }

    fun advance() {
        if (index + 1 < steps.size) {
            index++
            return
        }
        when (script?.ending) {
            SessionEnding.RUINED -> phase = SessionPhase.HONOR
            SessionEnding.PERMISSION -> end(SessionOutcome.FINISHED)
            else -> end(SessionOutcome.DENIED)
        }
    }

    BackHandler(enabled = phase == SessionPhase.RUNNING || phase == SessionPhase.HONOR) { /* Leave with "Stop" or Quit for now. */ }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeDrawingPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                !config.session.on || !config.enabled -> {
                    Text("Guided sessions are off. Turn them on in Settings, with her switched on.", Modifier.weight(1f))
                    Button(onClick = onDone) { Text("Close") }
                }
                phase == SessionPhase.SETUP -> Setup(
                    caged = caged,
                    soundingReady = soundingReady,
                    onSoundingReady = { soundingReady = it },
                    needsCamera = settings.camera && !hasCamera,
                    onAllowCamera = { permission.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.weight(1f),
                    onStart = {
                        val built = Session.build(settings, caged, Random.Default, soundingReady)
                        script = built
                        steps = built.steps
                        index = 0
                        phase = SessionPhase.RUNNING
                    },
                )
                phase == SessionPhase.RUNNING -> Running(
                    steps = steps,
                    index = index,
                    caged = caged,
                    watching = watching,
                    beatSound = settings.beatSound,
                    canCatch = watching && !caged && caught < Session.CAUGHT_LIMIT,
                    modifier = Modifier.weight(1f),
                    onNext = { advance() },
                    onSkip = {
                        skipped++
                        advance()
                    },
                    onCaught = {
                        caught++
                        steps = steps.take(index + 1) + Session.caughtSteps(caged) + steps.drop(index + 1)
                        index++
                    },
                    onClip = { file ->
                        clip = file
                        clipFailed = file == null
                    },
                )
                phase == SessionPhase.HONOR -> Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AngelImage(Modifier.size(160.dp))
                    Text("Did you ruin it like she ordered?", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    when {
                        clip != null -> Muted("She filmed it. The clip is in Photos, private to this app.")
                        clipFailed -> Muted("The clip couldn't be saved. She'll take your word for it.")
                        !watching -> Muted("The camera was off, so she takes your word for it.")
                    }
                    Button(onClick = { end(SessionOutcome.RUINED) }, modifier = Modifier.fillMaxWidth()) { Text("Ruined, as ordered") }
                    OutlinedButton(onClick = { end(SessionOutcome.RUIN_FAILED) }, modifier = Modifier.fillMaxWidth()) {
                        Text("I couldn't stop")
                    }
                }
                else -> Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AngelImage(Modifier.size(160.dp))
                    SpeechBubble(finalLine)
                    script?.let { Muted("Ending: ${it.ending.label}. Caught: $caught. Skipped: $skipped.") }
                    val proof = relockProof
                    if (proof != null) {
                        Button(onClick = {
                            context.startActivity(ProofActivity.intent(context, proof))
                            onDone()
                        }, modifier = Modifier.fillMaxWidth()) { Text("Show her the cage") }
                    } else {
                        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (phase != SessionPhase.DONE) TextButton(onClick = onDone) { Text(if (phase == SessionPhase.SETUP) "Not now" else "Stop") }
                QuitButton {
                    Guardian.quitForNow()
                    onDone()
                }
            }
        }
    }
}

@Composable
private fun Setup(
    caged: Boolean,
    soundingReady: Boolean,
    onSoundingReady: (Boolean) -> Unit,
    needsCamera: Boolean,
    onAllowCamera: () -> Unit,
    modifier: Modifier,
    onStart: () -> Unit,
) {
    val config by Guardian.config.flow.collectAsState()
    val s = config.session
    val kinks = Session.kinks(s, caged)
    val (p, r, d) = Session.endingShares(s, caged)
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Guided session", style = MaterialTheme.typography.headlineSmall)
        Text("About ${s.minutes} minutes. Endings: permission $p%, ruined $r%, denied $d%.")
        if (caged) Muted("You're locked, so only cage-safe commands until the end, and no permission.")
        Text("Kinks: " + kinks.joinToString(", ") { it.label }.ifEmpty { "none (just her basics)" })
        Muted(
            "Prop the phone up facing you, so the front camera sees you. She checks it on the phone; nothing is " +
                "saved except a ruin clip, which goes to Photos, private to this app. Stop or Quit for now any time.",
        )
        if (Kink.CBT in kinks) Muted("CBT (${s.cbt.label.lowercase()}): stop if it ever hurts sharply. \"Too much\" skips with no penalty.")
        if (Kink.SOUNDING in kinks) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = soundingReady, onCheckedChange = onSoundingReady)
                Text("My sound and lube are sterile and ready. I'll never force it.")
            }
            if (!soundingReady) Muted("Without this tick, she leaves sounding out.")
        }
        if (needsCamera) {
            Muted("She needs the camera to watch you and film a ruin.")
            OutlinedButton(onClick = onAllowCamera) { Text("Allow camera") }
        }
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Start") }
    }
}

@Composable
private fun Running(
    steps: List<Step>,
    index: Int,
    caged: Boolean,
    watching: Boolean,
    beatSound: Boolean,
    canCatch: Boolean,
    modifier: Modifier,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onCaught: () -> Unit,
    onClip: (File?) -> Unit,
) {
    val step = steps[index]
    val now = rememberNow(250)
    var startedAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var lastSeenAt by remember { mutableLongStateOf(0L) }
    var ticks by remember { mutableIntStateOf(0) }
    var line by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf<String?>(null) }
    val pulse = remember { Animatable(1f) }
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { tone?.release() } }

    // Each command: her line, then wait its time (a tap step moves on when you tap, or when time runs out).
    LaunchedEffect(index, steps) {
        startedAt = System.currentTimeMillis()
        ticks = 0
        line = Guardian.say(step.line)
        comment = step.comment?.let { Guardian.line(it) }
        delay(step.seconds * 1_000L)
        onNext()
    }

    // Her beat: a pulse (and a tick) per stroke. CBT stops ticking at its count.
    LaunchedEffect(index, steps, step.bpm) {
        if (step.bpm <= 0) return@LaunchedEffect
        val period = 60_000L / step.bpm
        while (true) {
            if (step.reps == 0 || ticks < step.reps) {
                ticks++
                if (beatSound) tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 40)
                launch {
                    pulse.snapTo(1.35f)
                    pulse.animateTo(1f, tween((period * 0.8).toInt()))
                }
            }
            delay(period)
        }
    }

    // She catches you when she can't see you for a while during a stroking command.
    LaunchedEffect(index, steps, canCatch) {
        if (!canCatch || !step.kind.stroking || !step.kind.watched) return@LaunchedEffect
        while (true) {
            delay(1_000)
            val since = maxOf(lastSeenAt, startedAt)
            if (System.currentTimeMillis() - since > Session.UNSEEN_SECONDS * 1_000L) {
                onCaught()
                return@LaunchedEffect
            }
        }
    }

    val left = ((startedAt + step.seconds * 1_000L - now) / 1_000).coerceAtLeast(0)
    val totalLeft = left + steps.drop(index + 1).sumOf { it.estimate }

    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Muted("About ${(totalLeft + 59) / 60} min left" + if (caged) " · locked" else "")
        SpeechBubble(listOfNotNull(line.ifBlank { null }, comment).joinToString("\n"))
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (watching) {
                SessionCamera(
                    mode = if (step.kind == StepKind.RUIN) CameraMode.RECORD else CameraMode.WATCH,
                    recordSeconds = Session.RUIN_CLIP_SECONDS - 1,
                    onSeen = { seen -> if (seen != false) lastSeenAt = System.currentTimeMillis() },
                    onClip = onClip,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (step.bpm > 0) {
                Box(
                    Modifier.size(120.dp).scale(pulse.value).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                )
            }
            when (step.kind) {
                StepKind.COUNTDOWN -> Text("$left", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
                StepKind.RUIN -> Text("REC", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                else -> Unit
            }
        }
        val detail = when {
            step.kind == StepKind.CBT -> "Count: ${ticks.coerceAtMost(step.reps)} of ${step.reps}"
            step.bpm > 0 -> "Follow the beat · ${step.bpm} per minute"
            step.kind.still -> "Hands off · ${left}s"
            step.kind.tap != null -> "Up to ${left / 60}:${"%02d".format(left % 60)}"
            else -> "${left}s"
        }
        Text(detail, style = MaterialTheme.typography.titleMedium)
        step.kind.tap?.let { label -> Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text(label) } }
        if (step.kind.skippable) OutlinedButton(onClick = onSkip) { Text("Too much (skip, no penalty)") }
    }
}

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
import com.guardianangel.core.ClipKind
import com.guardianangel.core.Clips
import com.guardianangel.core.Guardian
import com.guardianangel.core.Permissions
import com.guardianangel.core.Session
import com.guardianangel.core.SessionClips
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

/** A guided session: her commands, her beat, the camera showing you yourself and filming, and her ending. */
class SessionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        val quick = intent.getBooleanExtra(EXTRA_QUICK, false)
        setContent { GuardianTheme { SessionScreen(quick) { finish() } } }
    }

    companion object {
        private const val EXTRA_QUICK = "quick"

        /** [quick]: a quickshot (round 49), always ruined and always filmed. */
        fun intent(context: Context, quick: Boolean = false): Intent =
            Intent(context, SessionActivity::class.java).putExtra(EXTRA_QUICK, quick)
    }
}

private enum class SessionPhase { SETUP, RUNNING, HONOR, DONE }

@Composable
private fun SessionScreen(quick: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    val config by Guardian.config.flow.collectAsState()
    val settings = config.session
    val caged = remember { Guardian.caged() }

    var phase by remember { mutableStateOf(SessionPhase.SETUP) }
    var soundingReady by remember { mutableStateOf(false) }
    var hasCamera by remember { mutableStateOf(Permissions.camera(context)) }
    var hasMic by remember { mutableStateOf(Permissions.microphone(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasCamera = Permissions.camera(context)
        hasMic = Permissions.microphone(context)
    }
    var script by remember { mutableStateOf<SessionScript?>(null) }
    var steps by remember { mutableStateOf<List<Step>>(emptyList()) }
    var index by remember { mutableIntStateOf(0) }
    var skipped by remember { mutableIntStateOf(0) }
    var finalLine by remember { mutableStateOf("") }
    var relockProof by remember { mutableStateOf<Long?>(null) }
    var clip by remember { mutableStateOf<File?>(null) }
    var clipFailed by remember { mutableStateOf(false) }
    var filmed by remember { mutableIntStateOf(0) }
    // Round 77: the clip she plays mid-session, picked when it starts.
    var watchFile by remember { mutableStateOf<File?>(null) }

    fun end(outcome: SessionOutcome) {
        val s = script ?: return
        val (line, proof) = Guardian.finishSession(
            SessionRecord(
                System.currentTimeMillis(),
                if (s.quick) (s.estimate + 59) / 60 else settings.minutes,
                s.ending,
                caged,
                skipped = skipped,
                outcome = outcome,
                quick = s.quick,
            ),
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
                    Text("Guided sessions are off. Turn them on in Guided sessions on the home screen, with her switched on.", Modifier.weight(1f))
                    Button(onClick = onDone) { Text("Close") }
                }
                phase == SessionPhase.SETUP -> Setup(
                    quick = quick,
                    hasCamera = hasCamera,
                    hasMic = hasMic,
                    caged = caged,
                    soundingReady = soundingReady,
                    onSoundingReady = { soundingReady = it },
                    onAllowCamera = { permission.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) },
                    modifier = Modifier.weight(1f),
                    onStart = {
                        val clips = SessionClips.infos(context)
                        val built = if (quick) {
                            Session.quickshot(caged, Random.Default)
                        } else {
                            val watch = Clips.inSession(settings, clips.size, Random.Default.nextDouble())
                            Session.build(settings, caged, Random.Default, soundingReady, watchClip = watch)
                        }
                        watchFile = Clips.pick(clips, Random.Default)?.let { SessionClips.file(context, it.name) }
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
                    beatSound = settings.beatSound,
                    watchFile = watchFile,
                    backCamera = settings.backCamera,
                    onSwitchCamera = { Guardian.updateConfig { c -> c.copy(session = c.session.copy(backCamera = !c.session.backCamera)) } },
                    modifier = Modifier.weight(1f),
                    onNext = { advance() },
                    onSkip = {
                        skipped++
                        advance()
                    },
                    onClip = { kind, file ->
                        if (file != null) filmed++
                        if (kind == ClipKind.RUIN) {
                            clip = file
                            clipFailed = file == null
                        }
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
                        clip != null -> Muted("She filmed it. The clip is in Guided sessions > Her videos, private to this app.")
                        clipFailed -> Muted("The clip couldn't be saved. She'll take your word for it.")
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
                    script?.let { Muted("Ending: ${it.ending.label}. Skipped: $skipped.") }
                    if (filmed > 0) Muted("She filmed $filmed ${if (filmed == 1) "clip" else "clips"}. They're in Guided sessions > Her videos.")
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
    quick: Boolean,
    hasCamera: Boolean,
    hasMic: Boolean,
    caged: Boolean,
    soundingReady: Boolean,
    onSoundingReady: (Boolean) -> Unit,
    onAllowCamera: () -> Unit,
    modifier: Modifier,
    onStart: () -> Unit,
) {
    val config by Guardian.config.flow.collectAsState()
    val s = config.session
    val kinks = Session.kinks(s, caged)
    val (p, r, d) = Session.endingShares(s, caged)
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (quick) {
            Text("Quickshot", style = MaterialTheme.typography.headlineSmall)
            Text("About 2 minutes, fast to her beat. It always ends ruined, and she films the ruin.")
            if (caged) Muted("You're locked: she has you take the cage off first, and put it back on after.")
        } else {
            Text("Guided session", style = MaterialTheme.typography.headlineSmall)
            Text("About ${s.minutes} minutes. Endings: permission $p%, ruined $r%, denied $d%.")
            if (caged) Muted("You're locked, so only cage-safe commands until the end, and no permission.")
            Text("Kinks: " + kinks.joinToString(", ") { it.label }.ifEmpty { "none (just her basics)" })
            if (Kink.CBT in kinks) Muted("CBT (${s.cbt.label.lowercase()}): stop if it ever hurts sharply. \"Too much\" skips with no penalty.")
            if (Kink.SOUNDING in kinks) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = soundingReady, onCheckedChange = onSoundingReady)
                    Text("My sound and lube are sterile and ready. I'll never force it.")
                }
                if (!soundingReady) Muted("Without this tick, she leaves sounding out.")
            }
        }
        Muted(
            "Prop the phone up where the camera sees you: you'll watch yourself the whole time. She films your ruin" +
                (if (s.filmTasks && !quick) ", edges and CBT" else "") +
                " into Guided sessions > Her videos, private to this app. Stop or Quit for now any time.",
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Camera: ${if (s.backCamera) "back" else "front"}", Modifier.weight(1f))
            TextButton(onClick = {
                Guardian.updateConfig { c -> c.copy(session = c.session.copy(backCamera = !c.session.backCamera)) }
            }) { Text("Switch") }
        }
        if (!hasCamera || !hasMic) {
            Muted(
                if (!hasCamera) "Every session needs the camera. Allow the microphone too, so her videos have sound." else "Allow the microphone so her videos have sound. Without it she films silently.",
            )
            OutlinedButton(onClick = onAllowCamera) { Text(if (!hasCamera) "Allow camera and microphone" else "Allow microphone") }
        }
        Button(onClick = onStart, enabled = hasCamera, modifier = Modifier.fillMaxWidth()) { Text("Start") }
    }
}

@Composable
private fun Running(
    steps: List<Step>,
    index: Int,
    caged: Boolean,
    beatSound: Boolean,
    watchFile: File?,
    backCamera: Boolean,
    onSwitchCamera: () -> Unit,
    modifier: Modifier,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onClip: (ClipKind, File?) -> Unit,
) {
    val step = steps[index]
    val now = rememberNow(250)
    var startedAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var ticks by remember { mutableIntStateOf(0) }
    var line by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf<String?>(null) }
    val pulse = remember { Animatable(1f) }
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { tone?.release() } }
    val config by Guardian.config.flow.collectAsState()
    // A clip she plays mid-session; if it's gone, she moves straight on.
    val playing = step.kind == StepKind.WATCH
    val clipToPlay = watchFile?.takeIf { playing && it.exists() }

    // Each command: her line, then wait its time (a tap step moves on when you tap, or when time runs out).
    LaunchedEffect(index, steps) {
        startedAt = System.currentTimeMillis()
        ticks = 0
        line = Guardian.say(step.line)
        comment = step.comment?.let { Guardian.line(it) }
        if (step.kind == StepKind.WATCH && watchFile?.exists() != true) {
            onNext()
            return@LaunchedEffect
        }
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

    // What she films on this step: the ruin always, edges and CBT if you let her. Index keeps each step its own clip.
    val films = Clips.films(step.kind, config.session)
    val record = films?.let {
        ClipRequest(index, it, if (it == ClipKind.RUIN) Session.RUIN_CLIP_SECONDS - 1 else step.seconds)
    }

    val left = ((startedAt + step.seconds * 1_000L - now) / 1_000).coerceAtLeast(0)
    val totalLeft = (if (playing) 0 else left) + steps.drop(index + 1).sumOf { it.estimate }

    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Muted("About ${(totalLeft + 59) / 60} min left" + if (caged) " · locked" else "")
        SpeechBubble(listOfNotNull(line.ifBlank { null }, comment).joinToString("\n"))
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            // You see yourself the whole session. While she plays a clip, you shrink to the corner.
            SessionCamera(
                record = record,
                onClip = onClip,
                modifier = if (clipToPlay != null) {
                    Modifier.align(Alignment.BottomEnd).size(110.dp, 150.dp).clip(MaterialTheme.shapes.medium)
                } else {
                    Modifier.fillMaxSize()
                },
                back = backCamera,
            )
            if (clipToPlay != null) {
                ClipPlayer(clipToPlay, Modifier.fillMaxSize().padding(bottom = 160.dp), onEnd = onNext)
            }
            if (step.bpm > 0) {
                Box(
                    Modifier.size(120.dp).scale(pulse.value).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                )
            }
            when {
                step.kind == StepKind.COUNTDOWN -> Text("$left", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
                record != null -> Text(
                    "REC",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                )
                else -> Unit
            }
        }
        val detail = when {
            playing -> "Watch yourself"
            step.kind == StepKind.CBT -> "Count: ${ticks.coerceAtMost(step.reps)} of ${step.reps}"
            step.bpm > 0 -> "Follow the beat · ${step.bpm} per minute"
            step.kind.still -> "Hands off · ${left}s"
            step.kind.tap != null -> "Up to ${left / 60}:${"%02d".format(left % 60)}"
            else -> "${left}s"
        }
        Text(detail, style = MaterialTheme.typography.titleMedium)
        if (record == null && !playing) {
            TextButton(onClick = onSwitchCamera) { Text(if (backCamera) "Use front camera" else "Use back camera") }
        }
        step.kind.tap?.let { label -> Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text(label) } }
        if (step.kind.skippable) OutlinedButton(onClick = onSkip) { Text("Too much (skip, no penalty)") }
    }
}

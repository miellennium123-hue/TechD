package com.guardianangel.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.media.AudioManager
import android.media.ToneGenerator
import android.content.res.Configuration
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.guardianangel.core.ClipKind
import com.guardianangel.core.Clips
import com.guardianangel.core.Guardian
import com.guardianangel.core.Permissions
import com.guardianangel.core.Line
import com.guardianangel.core.Session
import com.guardianangel.core.BeatPattern
import com.guardianangel.core.DealChoice
import com.guardianangel.core.SessionClips
import com.guardianangel.core.SessionPlan
import com.guardianangel.core.SessionPlanned
import com.guardianangel.core.SessionReason
import com.guardianangel.core.SessionScript
import com.guardianangel.core.Step
import com.guardianangel.core.StepKind
import com.guardianangel.data.Kink
import com.guardianangel.data.SessionEnding
import com.guardianangel.data.SessionOutcome
import com.guardianangel.data.SessionRecord
import com.guardianangel.data.SessionTheme
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
    // Round 79: what she counts, her deal, your finish on command, and remarks between commands.
    var edges by remember { mutableIntStateOf(0) }
    var fastestEdge by remember { mutableIntStateOf(0) }
    var deal by remember { mutableStateOf("") }
    var finishOutcome by remember { mutableStateOf(SessionOutcome.FINISHED) }
    var remark by remember { mutableStateOf<String?>(null) }
    // Round 84: her plan for this session (theme, training, her length, what you owe).
    val gstate by Guardian.state.flow.collectAsState()
    var chosen by remember { mutableStateOf(settings.theme) }
    val lengthRoll = remember { Random.Default.nextDouble() }
    val plan = remember(chosen, config, gstate.owedPunishment, gstate.ruinOwedBy, gstate.trainingStart) {
        SessionPlan.plan(config, gstate, chosen, System.currentTimeMillis(), lengthRoll)
    }
    var running by remember { mutableStateOf<SessionPlanned?>(null) }
    // Round 85: the owed ruin always films your CBT.
    val owedRuin = running?.reason == SessionReason.RUIN_OWED
    val sizeNote = gstate.ratings.lastOrNull()?.let { "Rate me: longer than ${it.lengthPercentile}% of men." }
    val voice = remember {
        if (settings.voice) SessionVoice(context, Session.whisper(settings, Guardian.minuteOfDay())) else null
    }
    DisposableEffect(Unit) { onDispose { voice?.shutdown() } }

    fun end(outcome: SessionOutcome) {
        val s = script ?: return
        val (line, proof) = Guardian.finishSession(
            SessionRecord(
                System.currentTimeMillis(),
                if (s.quick) (s.estimate + 59) / 60 else (running?.settings?.minutes ?: settings.minutes),
                s.ending,
                caged,
                skipped = skipped,
                outcome = outcome,
                quick = s.quick,
                edges = edges,
                filmed = filmed,
                fastestEdge = fastestEdge,
                deal = deal,
                theme = running?.takeIf { !s.quick || it.reason == SessionReason.RUIN_OWED }?.theme ?: SessionTheme.YOURS,
            ),
        )
        finalLine = line
        relockProof = proof
        voice?.say(line)
        phase = SessionPhase.DONE
    }

    fun advance() {
        if (index + 1 < steps.size) {
            index++
            if (steps[index].kind == StepKind.EDGE) edges++
            return
        }
        when (script?.ending) {
            SessionEnding.RUINED -> phase = SessionPhase.HONOR
            SessionEnding.PERMISSION -> end(finishOutcome)
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
                    plan = plan,
                    chosen = chosen,
                    onTheme = { chosen = it },
                    hasCamera = hasCamera,
                    hasMic = hasMic,
                    caged = caged,
                    soundingReady = soundingReady,
                    onSoundingReady = { soundingReady = it },
                    onAllowCamera = { permission.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) },
                    modifier = Modifier.weight(1f),
                    onStart = {
                        val built = if (plan.reason == SessionReason.RUIN_OWED) {
                            // Round 85: what you owe her after a catch is always her CBT quickshot.
                            Session.owedRuin(caged, Random.Default)
                        } else if (quick) {
                            Session.quickshot(caged, Random.Default)
                        } else {
                            Session.build(plan.settings, caged, Random.Default, soundingReady, sizeKnown = sizeNote != null)
                        }
                        running = plan
                        if (!quick && plan.reason == SessionReason.CHOSEN && chosen != settings.theme) {
                            Guardian.updateConfig { c -> c.copy(session = c.session.copy(theme = chosen)) }
                        }
                        Guardian.sessionStarted()
                        // Her opening remarks: a punishment, her training week, or tonight's theme.
                        remark = listOfNotNull(
                            Guardian.say(Line.SESSION_PUNISH_START).takeIf { !quick && plan.reason == SessionReason.PUNISHMENT },
                            "Tonight: ${plan.theme.label}.".takeIf { !quick && plan.theme != SessionTheme.YOURS && plan.reason == SessionReason.CHOSEN },
                            (Guardian.line(Line.SESSION_TRAINING) + " Week ${plan.week}.").takeIf { !quick && plan.week > 0 },
                        ).joinToString(" ").ifBlank { null }
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
                    bigText = settings.bigText,
                    filmAll = owedRuin,
                    backCamera = settings.backCamera,
                    voice = voice,
                    secretLength = !quick && running?.secretLength == true,
                    sizeNote = sizeNote,
                    remark = remark,
                    onRemarkShown = { remark = null },
                    onSwitchCamera = { Guardian.updateConfig { c -> c.copy(session = c.session.copy(backCamera = !c.session.backCamera)) } },
                    modifier = Modifier.weight(1f),
                    onNext = { advance() },
                    onSkip = {
                        skipped++
                        advance()
                    },
                    onEdge = { seconds ->
                        if (fastestEdge == 0 || seconds < fastestEdge) fastestEdge = seconds
                        if (Session.edgeFast(seconds)) remark = Guardian.say(Line.SESSION_EDGE_FAST)
                        advance()
                    },
                    onDeal = { choice ->
                        val s = script
                        if (s != null) {
                            val (ending, next) = Session.takeDeal(s, steps, index, choice, Session.kinks(settings, caged), Random.Default)
                            script = s.copy(ending = ending)
                            steps = next
                            deal = if (choice == DealChoice.RUIN_NOW) "took the ruin" else "took the edges"
                            remark = Guardian.say(if (choice == DealChoice.RUIN_NOW) Line.SESSION_DEAL_RUIN else Line.SESSION_DEAL_EDGES)
                        }
                        advance()
                    },
                    onFinish = { outcome ->
                        finishOutcome = outcome
                        if (outcome == SessionOutcome.FINISHED) remark = Guardian.say(Line.SESSION_ON_COMMAND)
                        advance()
                    },
                    onClip = { take, file ->
                        if (file != null) {
                            filmed++
                            // Her caption on what she just saved.
                            if (take.caption.isNotBlank()) Guardian.captionClip(file.name, take.caption)
                        }
                        if (take.kind == ClipKind.RUIN) {
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
                    script?.let {
                        Muted(
                            listOfNotNull(
                                "Ending: ${it.ending.label}",
                                "Edges: $edges",
                                "fastest ${fastestEdge}s".takeIf { fastestEdge > 0 },
                                deal.ifBlank { null },
                                "Skipped: $skipped".takeIf { skipped > 0 },
                            ).joinToString(" · "),
                        )
                    }
                    if (filmed > 0) Muted("She saved $filmed ${if (filmed == 1) "clip" else "clips"}. They're in Guided sessions > Her videos.")
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
    plan: SessionPlanned,
    chosen: SessionTheme,
    onTheme: (SessionTheme) -> Unit,
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
    val s = plan.settings
    val kinks = Session.kinks(s, caged)
    val (p, r, d) = Session.endingShares(s, caged)
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (plan.reason == SessionReason.RUIN_OWED) {
            // Round 85: the ruin you owe her after a Porn block catch, whichever button you came from.
            Text("The ruin you owe her", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
            Text(
                "For what Porn block caught. A full minute of hard CBT that she films, then a quickshot: fast strokes, then " +
                    "the edge, her countdown, and hands off.",
            )
            Muted("Stop if it ever hurts sharply. \"Too much\" skips the CBT with no penalty.")
            if (caged) Muted("You're locked: the CBT with the cage on, then she has you take it off, and put it back on after.")
        } else if (quick) {
            Text("Quickshot", style = MaterialTheme.typography.headlineSmall)
            Text("About 2 minutes, fast to her beat. Then the edge, her countdown, and hands off: always ruined, and she films it.")
            if (caged) Muted("You're locked: she has you take the cage off first, and put it back on after.")
        } else {
            Text("Guided session", style = MaterialTheme.typography.headlineSmall)
            // Round 84: what you owe her comes first.
            when (plan.reason) {
                SessionReason.RUIN_OWED -> Text(
                    "This is the ruin you owe her for what Porn block caught. It ends ruined.",
                    color = MaterialTheme.colorScheme.error,
                )
                SessionReason.PUNISHMENT -> Text(
                    "You failed her, so this is a punishment session: CBT-heavy if you've allowed CBT, and always ruined.",
                    color = MaterialTheme.colorScheme.error,
                )
                SessionReason.CHOSEN -> {
                    Text("Theme", style = MaterialTheme.typography.titleSmall)
                    ChoiceChips(SessionTheme.entries.filter { SessionPlan.available(it, config.session) }, chosen, { it.label }) { onTheme(it) }
                    Muted(chosen.note)
                }
            }
            if (plan.week > 0) Muted("Her training: week ${plan.week} of ${SessionPlan.MAX_WEEK}. A little longer and harsher every week.")
            Text(
                (if (plan.secretLength) "She decides how long, and she won't tell you." else "About ${s.minutes} minutes.") +
                    " Endings: permission $p%, ruined $r%, denied $d%.",
            )
            if (caged) Muted("You're locked, so only cage-safe commands until the end, and no permission.")
            Text("Kinks: " + kinks.joinToString(", ") { it.label }.ifEmpty { "none (just her basics)" })
            if (Kink.CBT in kinks) Muted("CBT (${s.cbt.label.lowercase()}): stop if it ever hurts sharply. \"Too much\" skips with no penalty.")
            if (Kink.CLAMPS in kinks) Muted("Clamps: 3 minutes at most each time. Take them off early if anything goes numb; \"Too much\" skips.")
            if (Kink.SOUNDING in kinks) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = soundingReady, onCheckedChange = onSoundingReady)
                    Text("My sound and lube are sterile and ready. I'll never force it.")
                }
                if (!soundingReady) Muted("Without this tick, she leaves sounding out.")
            }
        }
        Muted(
            "Prop the phone up where the camera sees you: you'll see yourself live the whole time. She films your ruin" +
                (if (config.session.filmTasks && !quick) ", edges and CBT" else "") +
                " into Guided sessions > Her videos, private to this app. Stop or Quit for now any time.",
        )
        // Round 84: frame yourself before she starts.
        if (hasCamera) {
            SessionCamera(
                record = null,
                onClip = { _, _ -> },
                modifier = Modifier.fillMaxWidth().height(220.dp).clip(MaterialTheme.shapes.medium),
                back = config.session.backCamera,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Camera: ${if (config.session.backCamera) "back" else "front"}", Modifier.weight(1f))
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
    bigText: Boolean,
    filmAll: Boolean,
    backCamera: Boolean,
    voice: SessionVoice?,
    secretLength: Boolean,
    sizeNote: String?,
    remark: String?,
    onRemarkShown: () -> Unit,
    onSwitchCamera: () -> Unit,
    modifier: Modifier,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onEdge: (Int) -> Unit,
    onDeal: (DealChoice) -> Unit,
    onFinish: (SessionOutcome) -> Unit,
    onClip: (ClipRequest, File?) -> Unit,
) {
    val context = LocalContext.current
    val step = steps[index]
    val now = rememberNow(250)
    var startedAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var ticks by remember { mutableIntStateOf(0) }
    var count by remember { mutableIntStateOf(0) }
    var line by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf<String?>(null) }
    val pulse = remember { Animatable(1f) }
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { tone?.release() } }
    val config by Guardian.config.flow.collectAsState()
    val edgeGoal = steps.count { it.kind == StepKind.EDGE }
    val edgeNumber = steps.take(index + 1).count { it.kind == StepKind.EDGE }

    // Each command: her line (spoken too), then wait its time. Tap steps move on when you tap or time runs out.
    LaunchedEffect(index, step) {
        startedAt = System.currentTimeMillis()
        ticks = 0
        line = Guardian.say(step.line)
        val extras = listOfNotNull(
            remark,
            step.comment?.let { c -> Guardian.line(c) + if (c == Line.SESSION_SPH && sizeNote != null) " $sizeNote" else "" },
            // Round 79: her edge goal at the start.
            if (step.kind == StepKind.INTRO && edgeGoal > 0) Guardian.line(Line.SESSION_EDGE_GOAL) + " Tonight: $edgeGoal edges." else null,
            step.style?.label,
        )
        comment = extras.joinToString("\n").ifBlank { null }
        if (remark != null) onRemarkShown()
        voice?.say(listOfNotNull(remark, line).joinToString(" "))
        extras.drop(if (remark != null) 1 else 0).forEach { voice?.then(it) }
        when (step.kind) {
            StepKind.COUNTDOWN -> {
                // Her countdown, spoken. With a taunt she holds on her number while she teases you.
                for (n in step.seconds downTo 1) {
                    count = n
                    voice?.say("$n")
                    delay(1_000)
                    if (n == step.tauntAt) {
                        line = Guardian.say(Line.SESSION_TAUNT)
                        voice?.say(line)
                        delay(Session.TAUNT_SECONDS * 1_000L)
                    }
                }
                onNext()
                return@LaunchedEffect
            }
            StepKind.DEAL -> {
                delay(step.seconds * 1_000L)
                onDeal(DealChoice.RUIN_NOW) // No answer: she picks the ruin for you.
                return@LaunchedEffect
            }
            StepKind.FINISH -> {
                delay(step.seconds * 1_000L)
                onFinish(SessionOutcome.FINISHED)
                return@LaunchedEffect
            }
            else -> Unit
        }
        delay(step.seconds * 1_000L)
        onNext()
    }

    // Round 95: what she films. One take can run across commands: a ruin from 10 seconds before your edge,
    // through her countdown, to the ruin; an edge until 10 seconds after you tap. The camera cuts the start.
    val filmSettings = if (filmAll) config.session.copy(filmTasks = true) else config.session
    var take by remember { mutableStateOf<ClipRequest?>(null) }
    LaunchedEffect(index, step) {
        val t = System.currentTimeMillis()
        // You just left an edge (your tap, or her time ran out): she marks the moment.
        val prev = take?.let { p ->
            if (p.markAt == 0L && p.id == index - 1 && steps.getOrNull(index - 1)?.kind == StepKind.EDGE) {
                val edge = p.kind == ClipKind.EDGE
                p.copy(
                    markAt = t,
                    stopAt = if (edge) t + Clips.EDGE_AFTER_MS else p.stopAt,
                    caption = if (edge) Clips.caption(ClipKind.EDGE, p.number, ((t - p.startedAt) / 1_000).toInt()) else p.caption,
                )
            } else {
                p
            }
        }
        val wanted = Clips.take(steps, index, filmSettings)
        take = when {
            // The same take going on: at the ruin, it stops just before the ruin's time is up.
            wanted != null && prev != null && prev.id == wanted.id ->
                if (step.kind == StepKind.RUIN) prev.copy(stopAt = t + (Session.RUIN_CLIP_SECONDS - 1) * 1_000L) else prev
            wanted != null -> {
                val number = when (wanted.kind) {
                    ClipKind.RUIN -> SessionClips.infos(context).count { it.kind == ClipKind.RUIN } + 1
                    ClipKind.EDGE -> edgeNumber
                    else -> 0
                }
                ClipRequest(
                    id = wanted.id,
                    kind = wanted.kind,
                    startedAt = t,
                    stopAt = if (step.kind == StepKind.RUIN) t + (Session.RUIN_CLIP_SECONDS - 1) * 1_000L else 0,
                    number = number,
                    caption = when (wanted.kind) {
                        ClipKind.RUIN -> Clips.caption(ClipKind.RUIN, number, edges = edgeNumber)
                        ClipKind.CBT -> Clips.caption(ClipKind.CBT, 0, reps = step.reps)
                        else -> Clips.caption(wanted.kind, number)
                    },
                )
            }
            // An edge's 10 seconds after your tap.
            prev != null && prev.markAt > 0 && prev.stopAt > t -> prev
            else -> null
        }
    }
    LaunchedEffect(take?.id, take?.stopAt) {
        val stopAt = take?.stopAt ?: 0L
        if (stopAt <= 0) return@LaunchedEffect
        delay((stopAt - System.currentTimeMillis()).coerceAtLeast(0))
        take = null
    }

    // Round 79: eyes on the lens while she films an edge or CBT.
    val films = Clips.films(step.kind, filmSettings)
    LaunchedEffect(index, step, films) {
        if (films == null || films == ClipKind.RUIN) return@LaunchedEffect
        delay(8_000)
        val lens = Guardian.line(Line.SESSION_LENS)
        comment = listOfNotNull(comment, lens).joinToString("\n")
        voice?.say(lens)
    }

    // Her beat: a pulse (and a tick) per stroke, on her pattern, speeding up on a ramp. CBT and exact
    // counts stop at their number; she says CBT counts aloud, and every tenth stroke of a count.
    LaunchedEffect(index, step) {
        if (step.bpm <= 0) return@LaunchedEffect
        if ((step.kind == StepKind.CBT || step.kind == StepKind.COUNT) && voice != null) delay(3_000)
        var beat = 0
        fun stroke(period: Long) {
            if (step.reps != 0 && ticks >= step.reps) return
            ticks++
            if (beatSound) tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 40)
            when {
                step.kind == StepKind.CBT -> voice?.say("$ticks")
                step.kind == StepKind.COUNT && ticks % 10 == 0 -> voice?.say("$ticks")
            }
            launch {
                pulse.snapTo(1.35f)
                pulse.animateTo(1f, tween((period * 0.8).toInt()))
            }
        }
        fun rest() {
            launch {
                pulse.snapTo(1.08f)
                pulse.animateTo(1f, tween(200))
            }
        }
        while (true) {
            val bpm = step.bpmAt(System.currentTimeMillis() - startedAt).coerceAtLeast(10)
            val period = 60_000L / bpm
            when (step.pattern) {
                BeatPattern.STEADY -> {
                    stroke(period)
                    delay(period)
                }
                BeatPattern.HEARTBEAT -> {
                    stroke(period / 4)
                    delay(period / 4)
                    stroke(period / 4)
                    delay(period * 3 / 4)
                }
                BeatPattern.STUTTER -> {
                    val r = Random.nextDouble()
                    when {
                        r < 0.2 -> rest()
                        r < 0.35 -> {
                            stroke(period / 2)
                            delay(period / 2)
                            stroke(period / 2)
                        }
                        else -> stroke(period)
                    }
                    delay(period)
                }
                BeatPattern.EVERY_OTHER -> {
                    beat++
                    if (beat % 2 == 1) stroke(period) else rest()
                    delay(period)
                }
            }
        }
    }

    val record = take

    val left = ((startedAt + step.seconds * 1_000L - now) / 1_000).coerceAtLeast(0)
    val totalLeft = left + steps.drop(index + 1).sumOf { it.estimate }
    val bpmNow = step.bpmAt(now - startedAt)
    // Round 84: dark mode (only her beat on screen), the torch, and landscape.
    var dark by remember { mutableStateOf(false) }
    var torch by remember { mutableStateOf(false) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val chapter = Session.chapter(step)

    // The camera and her beat. In dark mode the screen goes black except for her beat. Round 95: no clips
    // play here any more; you only see yourself live.
    val stage: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                SessionCamera(
                    record = record,
                    onClip = onClip,
                    modifier = Modifier.fillMaxSize(),
                    back = backCamera,
                    torch = torch && backCamera,
                )
                if (dark) Box(Modifier.matchParentSize().background(Color.Black))
                if (step.bpm > 0) {
                    Box(
                        Modifier.size(if (bigText || dark) 160.dp else 120.dp).scale(pulse.value).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (dark) 0.8f else 0.55f)),
                    )
                }
                when {
                    step.kind == StepKind.COUNTDOWN -> Text("$count", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
                    record != null && !dark -> Text(
                        "REC",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    )
                    else -> Unit
                }
            }
        }
    }

    // The chapter, time left and her words.
    val header: @Composable ColumnScope.() -> Unit = {
        if (chapter.isNotBlank()) {
            Text(chapter.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Muted(
            listOfNotNull(
                "About ${(totalLeft + 59) / 60} min left".takeIf { !secretLength },
                "edge $edgeNumber of $edgeGoal".takeIf { edgeGoal > 0 && edgeNumber > 0 },
                "locked".takeIf { caged },
            ).joinToString(" · "),
        )
        if (!dark) {
            val words = listOfNotNull(line.ifBlank { null }, comment).joinToString("\n")
            if (bigText) {
                Text(words, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            } else {
                SpeechBubble(words)
            }
        }
    }

    // The count and your buttons.
    val controls: @Composable ColumnScope.() -> Unit = {
        val detail = when {
            step.kind == StepKind.CBT -> "Count: ${ticks.coerceAtMost(step.reps)} of ${step.reps}"
            step.kind == StepKind.COUNT -> "Stroke ${ticks.coerceAtMost(step.reps)} of ${step.reps}"
            step.kind == StepKind.COUNTDOWN || step.kind == StepKind.DEAL || step.kind == StepKind.FINISH -> null
            step.bpmTo > 0 && step.kind != StepKind.EDGE -> "Faster and faster · $bpmNow per minute"
            step.bpm > 0 && step.pattern != BeatPattern.STEADY -> "${step.pattern.label} · $bpmNow per minute"
            step.bpm > 0 -> "Follow the beat · $bpmNow per minute"
            step.kind == StepKind.CLAMP -> "Clamps on · ${left}s"
            step.kind.still -> "Hands off · ${left}s"
            step.kind.tap != null -> "Up to ${left / 60}:${"%02d".format(left % 60)}"
            else -> "${left}s"
        }
        detail?.let { Text(it, style = if (bigText) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium) }
        when (step.kind) {
            StepKind.EDGE -> Button(
                onClick = { onEdge(((System.currentTimeMillis() - startedAt) / 1_000).toInt()) },
                modifier = Modifier.fillMaxWidth().height(if (bigText || dark) 72.dp else 56.dp),
            ) { Text("I'm at the edge", style = MaterialTheme.typography.titleMedium) }
            StepKind.DEAL -> {
                Button(onClick = { onDeal(DealChoice.RUIN_NOW) }, modifier = Modifier.fillMaxWidth()) { Text("A sure ruin, now") }
                OutlinedButton(onClick = { onDeal(DealChoice.EDGES) }, modifier = Modifier.fillMaxWidth()) {
                    Text("${Session.DEAL_EDGES} more edges and her coin flip")
                }
                Muted("No answer in ${left}s and she picks the ruin.")
            }
            StepKind.FINISH -> {
                Button(onClick = { onFinish(SessionOutcome.FINISHED) }, modifier = Modifier.fillMaxWidth()) { Text("Right on her command") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onFinish(SessionOutcome.MISSED_COMMAND) }) { Text("Too early") }
                    OutlinedButton(onClick = { onFinish(SessionOutcome.MISSED_COMMAND) }) { Text("Too late") }
                }
                Muted("Be honest. Too early or too late is a failure.")
            }
            else -> step.kind.tap?.let { label -> Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text(label) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { dark = !dark }) { Text(if (dark) "Show camera" else "Dark") }
            if (backCamera) TextButton(onClick = { torch = !torch }) { Text(if (torch) "Torch off" else "Torch") }
            if (record == null) {
                TextButton(onClick = onSwitchCamera) { Text(if (backCamera) "Front camera" else "Back camera") }
            }
        }
        if (step.kind.skippable) OutlinedButton(onClick = onSkip) { Text("Too much (skip, no penalty)") }
    }

    if (landscape) {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            stage(Modifier.weight(1f).fillMaxHeight())
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                header()
                controls()
            }
        }
    } else {
        Column(
            modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (bigText) 8.dp else 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            header()
            stage(Modifier.weight(1f).fillMaxWidth())
            controls()
        }
    }
}

package com.guardianangel.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.guardianangel.core.Guardian
import com.guardianangel.core.Line
import com.guardianangel.core.LockGuard
import com.guardianangel.core.Lines
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private enum class ExitStep { HOLD, TYPE, WAIT, READY }

/**
 * The slow way out, full screen: hold the button, type her sentence exactly (a typo starts it
 * over), then wait with this screen open while she talks. Leaving the screen starts it all over.
 * It always finishes; [onFinished] runs when you tap the last button. [onCancel] keeps everything as it was.
 */
@Composable
fun SlowExitDialog(
    title: String,
    holdSeconds: Int,
    sentence: String,
    waitSeconds: Int,
    talk: Line,
    warning: String?,
    finishLabel: String,
    onFinished: () -> Unit,
    onCancel: () -> Unit,
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            SlowExit(title, holdSeconds, sentence, waitSeconds, talk, warning, finishLabel, onFinished, onCancel)
        }
    }
}

@Composable
private fun SlowExit(
    title: String,
    holdSeconds: Int,
    sentence: String,
    waitSeconds: Int,
    talk: Line,
    warning: String?,
    finishLabel: String,
    onFinished: () -> Unit,
    onCancel: () -> Unit,
) {
    var step by remember { mutableStateOf(ExitStep.HOLD) }
    var holding by remember { mutableStateOf(false) }
    var held by remember { mutableFloatStateOf(0f) }
    var typed by remember { mutableStateOf("") }
    var typo by remember { mutableStateOf(false) }
    var left by remember { mutableIntStateOf(waitSeconds) }
    var said by remember { mutableStateOf("") }

    fun restart() {
        step = ExitStep.HOLD
        holding = false
        held = 0f
        typed = ""
        typo = false
        left = waitSeconds
    }

    // Leaving the screen (home, another app, screen off) starts it all over.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) restart() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Holding: letting go early resets the hold.
    LaunchedEffect(holding, step) {
        if (step != ExitStep.HOLD) return@LaunchedEffect
        if (!holding) {
            held = 0f
            return@LaunchedEffect
        }
        val start = System.currentTimeMillis()
        while (isActive && holding) {
            held = ((System.currentTimeMillis() - start) / (holdSeconds * 1_000f)).coerceAtMost(1f)
            if (held >= 1f) {
                holding = false
                step = ExitStep.TYPE
                return@LaunchedEffect
            }
            delay(50)
        }
    }

    // The wait, with her talking every few seconds.
    LaunchedEffect(step) {
        if (step != ExitStep.WAIT) return@LaunchedEffect
        left = waitSeconds
        var since = LockGuard.TALK_EVERY_SECONDS
        while (left > 0) {
            if (since >= LockGuard.TALK_EVERY_SECONDS) {
                said = Guardian.line(talk)
                since = 0
            }
            delay(1_000)
            left--
            since++
        }
        step = ExitStep.READY
    }

    Column(
        Modifier.safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        warning?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
        Muted("Step ${step.ordinal.coerceAtMost(2) + 1} of 3. Leaving this screen starts it over.")
        when (step) {
            ExitStep.HOLD -> {
                Text("Press and hold for $holdSeconds seconds.", textAlign = TextAlign.Center)
                Box(
                    Modifier.size(180.dp).pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            holding = true
                            tryAwaitRelease()
                            holding = false
                        })
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        Modifier.fillMaxSize(),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = if (holding) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                if (holding) "${(holdSeconds * (1 - held)).toInt() + 1}" else "Hold",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (holding) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
                LinearProgressIndicator(progress = { held }, modifier = Modifier.fillMaxWidth())
            }
            ExitStep.TYPE -> {
                Text("Type it exactly. One typo and you start the sentence again.", textAlign = TextAlign.Center)
                Text("\"$sentence\"", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                OutlinedTextField(
                    value = typed,
                    onValueChange = { next ->
                        when (Lines.check(typed, next, sentence)) {
                            Lines.Input.OK -> {
                                typed = next
                                typo = false
                            }
                            Lines.Input.LINE_DONE -> step = ExitStep.WAIT
                            Lines.Input.TYPO -> {
                                typed = ""
                                typo = true
                            }
                            Lines.Input.PASTE -> Unit
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                )
                if (typo) Text("Typo. From the start.", color = MaterialTheme.colorScheme.error)
            }
            ExitStep.WAIT -> {
                AngelImage(Modifier.size(140.dp))
                if (said.isNotBlank()) SpeechBubble(said)
                Text(
                    "%d:%02d".format(left / 60, left % 60),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                )
                LinearProgressIndicator(progress = { 1f - left / waitSeconds.toFloat() }, modifier = Modifier.fillMaxWidth())
                Muted("Keep this screen open until it reaches zero.")
            }
            ExitStep.READY -> {
                Button(
                    onClick = onFinished,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text(finishLabel, fontWeight = FontWeight.Bold) }
            }
        }
        TextButton(onClick = onCancel) { Text("Never mind, I'll stay", color = Color.Unspecified) }
    }
}

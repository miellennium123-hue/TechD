package com.guardianangel.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.guardianangel.R
import com.guardianangel.core.AssetImages
import com.guardianangel.core.Guardian
import com.guardianangel.core.Line
import com.guardianangel.core.LockGuard
import kotlinx.coroutines.delay

@Composable
fun rememberNow(periodMs: Long = 1_000): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(periodMs)
            value = System.currentTimeMillis()
        }
    }
    return now
}

fun formatDuration(ms: Long): String {
    val total = ms.coerceAtLeast(0) / 1000
    val days = total / 86_400
    val hours = (total % 86_400) / 3_600
    val minutes = (total % 3_600) / 60
    val seconds = total % 60
    return when {
        days > 0 -> "%dd %02dh %02dm".format(days, hours, minutes)
        hours > 0 -> "%dh %02dm %02ds".format(hours, minutes, seconds)
        else -> "%02d:%02d".format(minutes, seconds)
    }
}

fun formatMinuteOfDay(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

/** Uses assets/angel.png (or .jpg/.webp) when present, otherwise the placeholder drawing. */
@Composable
fun AngelImage(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val custom = remember { AssetImages.angel(context)?.asImageBitmap() }
    if (custom != null) {
        Image(bitmap = custom, contentDescription = "Your Guardian Angel", modifier = modifier, contentScale = ContentScale.Fit)
    } else {
        Image(painter = painterResource(R.drawable.angel), contentDescription = "Your Guardian Angel", modifier = modifier)
    }
}

@Composable
fun SpeechBubble(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            style = MaterialTheme.typography.titleMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** The safety exit. Shown on every screen and never disabled. */
@Composable
fun QuitButton(modifier: Modifier = Modifier, onQuit: () -> Unit) {
    // Always here and always finishes, never punished. The user chose a slow way out (rounds 41 and 43): about 10 minutes.
    // Round 96: saved, so Android reloading the screen keeps her slow exit open.
    var open by rememberSaveable { mutableStateOf(false) }
    if (open) {
        SlowExitDialog(
            title = "Quit for now",
            holdSeconds = LockGuard.QUIT_HOLD_SECONDS,
            sentence = LockGuard.QUIT_SENTENCE,
            waitSeconds = LockGuard.QUIT_WAIT_SECONDS,
            talk = Line.QUIT_TALK,
            warning = null,
            finishLabel = "Quit for now",
            onFinished = {
                open = false
                onQuit()
            },
            onCancel = { open = false },
        )
    }
    Button(
        onClick = { open = true },
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
    ) {
        Text("Quit for now", fontWeight = FontWeight.Bold)
    }
    // Debug mode (round 56): the same exit, instantly. Only switchable while she's off.
    val config by Guardian.config.flow.collectAsState()
    if (config.debugMode) {
        OutlinedButton(onClick = onQuit, modifier = modifier) { Text("Debug: shut down") }
    }
}

/**
 * Her master on/off switch. With Lock guard on, switching her off takes the slow way (30 minutes)
 * and counts as a failure. Switching her on is always instant.
 */
@Composable
fun EnabledSwitch(title: String, subtitle: String?, checked: Boolean) {
    var slowOff by rememberSaveable { mutableStateOf(false) }
    if (slowOff) {
        SlowExitDialog(
            title = "Switch her off",
            holdSeconds = LockGuard.OFF_HOLD_SECONDS,
            sentence = LockGuard.OFF_SENTENCE,
            waitSeconds = LockGuard.OFF_WAIT_SECONDS,
            talk = Line.OFF_TALK,
            warning = "Lock guard is on. Switching her off counts as a failure.",
            finishLabel = "Switch her off",
            onFinished = {
                slowOff = false
                Guardian.slowOff()
            },
            onCancel = { slowOff = false },
        )
    }
    SwitchRow(title, subtitle, checked) { on ->
        if (!on && Guardian.offNeedsWait()) slowOff = true else Guardian.setEnabled(on)
    }
}

/**
 * Lock guard: a settings change that loosens her control waits here for the slow way
 * (30 minutes, not a failure). Cancelling keeps the setting as it was.
 */
@Composable
fun LoosenHost() {
    val refusal by Guardian.refusal.collectAsState()
    refusal?.let { text ->
        // Round 53: her block or bedtime is running, so the change can't be made at all.
        AlertDialog(
            onDismissRequest = { Guardian.refusal.value = null },
            title = { Text("Not now") },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { Guardian.refusal.value = null }) { Text("OK") } },
        )
    }
    val pending by Guardian.pendingLoosen.collectAsState()
    if (pending == null) return
    SlowExitDialog(
        title = "Loosen her control",
        holdSeconds = LockGuard.OFF_HOLD_SECONDS,
        sentence = LockGuard.LOOSEN_SENTENCE,
        waitSeconds = LockGuard.OFF_WAIT_SECONDS,
        talk = Line.LOOSEN_TALK,
        warning = "Lock guard is on. Switching off one of her controls takes 30 minutes.",
        finishLabel = "Make the change",
        onFinished = { Guardian.applyLoosen() },
        onCancel = { Guardian.cancelLoosen() },
    )
}

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

/** A small heading that groups cards in Settings. */
@Composable
fun GroupHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

/**
 * A settings card that folds shut: the title and an On/Off badge show, tap to open it.
 * [on] null shows no badge. Remembers whether it's open while you're on the screen.
 */
@Composable
fun FoldCard(title: String, on: Boolean?, content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (on != null) {
                    Text(
                        if (on) "On" else "Off",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                Icon(
                    if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (open) "Close" else "Open",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (open) content()
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(options: List<T>, selected: T, label: (T) -> String, enabled: Boolean = true, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
                enabled = enabled,
            )
        }
    }
}

@Composable
fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit, enabled: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        FilledTonalButton(onClick = onMinus, enabled = enabled) { Text("-") }
        Text(value, Modifier.padding(horizontal = 12.dp), fontWeight = FontWeight.Bold)
        FilledTonalButton(onClick = onPlus, enabled = enabled) { Text("+") }
    }
}

@Composable
fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

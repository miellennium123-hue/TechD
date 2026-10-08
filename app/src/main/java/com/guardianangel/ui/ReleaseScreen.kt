package com.guardianangel.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.guardianangel.core.DayMark
import com.guardianangel.core.Guardian
import com.guardianangel.core.Line
import com.guardianangel.core.Release
import com.guardianangel.core.SessionClips
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.GuardianState
import com.guardianangel.data.ReleaseSettings
import com.guardianangel.data.SessionOutcome
import com.guardianangel.data.WheelResult
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val Gold = Color(0xFFD4AF37)

/**
 * Her release calendar (round 104): the next release day (or "?"), your denial streak, a month of
 * days marked, release day with her reel and her wheel, confessing, and its settings.
 */
@Composable
fun ReleaseScreen(config: GuardianConfig, state: GuardianState) {
    val context = LocalContext.current
    val now = rememberNow(30_000)
    val today = remember(now) { Guardian.today() }
    LaunchedEffect(today) { Guardian.releaseTick() }
    val s = config.release
    val r = state.release.takeIf { config.enabled && s.on }
    val calendarLine = remember { Guardian.line(Line.RELEASE_CALENDAR) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var dayShown by remember { mutableLongStateOf(-1L) }
    var reel by remember { mutableStateOf(false) }
    var wheel by remember { mutableStateOf(false) }
    var confess by remember { mutableStateOf(false) }
    var said by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (r == null) {
            Text("Her release calendar", style = MaterialTheme.typography.headlineSmall)
            Text(
                "She picks the one day you may cum, at random between your shortest and longest wait. Nothing moves it. " +
                    "On release day you spin her wheel: permission, a ruin, or another week. Every other day, sessions only end " +
                    "ruined or denied.",
            )
            if (!config.enabled) Muted("Switch her on first.")
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AngelImage(Modifier.size(72.dp))
                SpeechBubble(said ?: calendarLine, Modifier.weight(1f))
            }
            val releaseDay = Release.isReleaseDay(r, today)
            val shown = Release.shownDay(r, s, today)
            Text(
                when {
                    releaseDay && Release.lastChance(r, today) -> "Release day was yesterday. Today is your last chance."
                    releaseDay -> "Today is release day."
                    shown != null -> "Next release: ${LocalDate.ofEpochDay(shown).format(DateTimeFormatter.ofPattern("EEE d MMM"))} " +
                        "(in ${Release.daysLeft(r, today)} ${if (Release.daysLeft(r, today) == 1L) "day" else "days"})"
                    else -> "Next release: ?"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            val denied = Release.deniedDays(r, today)
            Muted("Denied for $denied ${if (denied == 1) "day" else "days"} · best ${Release.bestStreak(r, today)}")

            if (releaseDay) ReleaseDayCard(r.spin, s, r.reelWatched, onReel = { reel = true }, onSpin = { wheel = true })

            MonthGrid(month, today, onMonth = { month = it }, onDay = { dayShown = it }) { day ->
                Release.marks(day, today, r, s, state.releaseLog, state.sessions, state.failures, state.chastity?.startedAt, Guardian::dayOf)
            }
            Legend()
            MonthSummary(month, today, r, s, state)
            OutlinedButton(onClick = { confess = true }, modifier = Modifier.fillMaxWidth()) { Text("Confess a release") }
        }

        ReleaseSettingsCard(config)
    }

    if (dayShown >= 0) DayDialog(dayShown, today, config, state) { dayShown = -1 }

    if (reel) {
        val clips = remember { Release.weekReel(SessionClips.infos(context), System.currentTimeMillis()) }
        if (clips.isEmpty()) {
            LaunchedEffect(Unit) {
                Guardian.releaseReelWatched()
                reel = false
            }
        } else {
            RuinReel(clips, state.clipCaptions) {
                Guardian.releaseReelWatched()
                reel = false
            }
        }
    }

    if (wheel) {
        WheelDialog(s) { result, line ->
            wheel = false
            said = line.ifBlank { null }
            if (result != WheelResult.WEEK) context.startActivity(SessionActivity.intent(context, release = result))
        }
    }

    if (confess) {
        AlertDialog(
            onDismissRequest = { confess = false },
            title = { Text("Confess a release?") },
            text = { Text("You came without her permission. It counts as a failure, and your streak starts over. Her day doesn't move.") },
            confirmButton = {
                TextButton(onClick = {
                    said = Guardian.confessRelease()
                    confess = false
                }) { Text("Confess") }
            },
            dismissButton = { TextButton(onClick = { confess = false }) { Text("Cancel") } },
        )
    }
}

/** Release day: her reel first (if set and there are ruins this week), then her wheel, then your session. */
@Composable
private fun ReleaseDayCard(spin: WheelResult?, s: ReleaseSettings, reelWatched: Boolean, onReel: () -> Unit, onSpin: () -> Unit) {
    val context = LocalContext.current
    val hasReel = remember { Release.weekReel(SessionClips.infos(context), System.currentTimeMillis()).isNotEmpty() }
    SectionCard("Release day") {
        when {
            spin == null && s.reel && !reelWatched && hasReel -> {
                Text(Guardian.line(Line.RELEASE_REEL))
                Button(onClick = onReel, modifier = Modifier.fillMaxWidth()) { Text("Watch her reel") }
            }
            spin == null -> {
                Text("Permission ${Release.permissionChance(s)}% · a ruin ${s.ruinChance}% · another week ${100 - Release.permissionChance(s) - s.ruinChance.coerceIn(0, 100)}%")
                Button(onClick = onSpin, modifier = Modifier.fillMaxWidth()) { Text("Spin her wheel") }
            }
            else -> {
                Text("Her wheel said: ${spin.label}.", fontWeight = FontWeight.Bold)
                Button(
                    onClick = { context.startActivity(SessionActivity.intent(context, release = spin)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (spin == WheelResult.RUIN) "Start your ruin" else "Start your release session") }
                Muted("Leaving the session early keeps your release day; start it again any time today.")
            }
        }
    }
}

/** Her wheel: it spins and lands on what she picked (already saved, so leaving can't change it). */
@Composable
private fun WheelDialog(s: ReleaseSettings, onDone: (WheelResult, String) -> Unit) {
    val picked = remember { Guardian.spinWheel() }
    val angle = remember { Animatable(0f) }
    var landed by remember { mutableStateOf(false) }
    val permission = Release.permissionChance(s)
    val ruin = s.ruinChance.coerceIn(0, 100)
    val week = 100 - permission - ruin
    // Slices clockwise from the top: permission, ruin, another week.
    val slices = listOf(WheelResult.PERMISSION to permission, WheelResult.RUIN to ruin, WheelResult.WEEK to week)
    val colors = mapOf(WheelResult.PERMISSION to Gold, WheelResult.RUIN to Color(0xFFB3261E), WheelResult.WEEK to Color(0xFF5F6368))

    LaunchedEffect(picked) {
        val result = picked?.first ?: return@LaunchedEffect
        var start = 0f
        var center = 0f
        for ((kind, pct) in slices) {
            val sweep = pct * 3.6f
            if (kind == result) center = start + sweep / 2
            start += sweep
        }
        angle.animateTo(360f * 6 + (360f - center), tween(4_000, easing = FastOutSlowInEasing))
        landed = true
    }

    Dialog(onDismissRequest = {}) {
        Column(
            Modifier.background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (picked == null) {
                Text("It isn't release day.")
                TextButton(onClick = { onDone(WheelResult.WEEK, "") }) { Text("Close") }
                return@Column
            }
            Text(Guardian.line(Line.RELEASE_SPIN), textAlign = TextAlign.Center)
            Box(contentAlignment = Alignment.TopCenter) {
                Canvas(Modifier.size(240.dp).rotate(angle.value)) {
                    var start = -90f
                    for ((kind, pct) in slices) {
                        if (pct <= 0) continue
                        val sweep = pct * 3.6f
                        drawArc(colors.getValue(kind), start, sweep, useCenter = true, size = Size(size.width, size.height))
                        start += sweep
                    }
                }
                // Her pointer at the top.
                Canvas(Modifier.size(24.dp)) {
                    val p = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width / 2, size.height)
                        close()
                    }
                    drawPath(p, Color.White)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                slices.filter { it.second > 0 }.forEach { (kind, pct) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(10.dp).background(colors.getValue(kind)))
                        Text("${kind.label} $pct%", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (landed) {
                Text(picked.first.label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                SpeechBubble(picked.second)
                Button(onClick = { onDone(picked.first, picked.second) }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        when (picked.first) {
                            WheelResult.PERMISSION -> "Start your release session"
                            WheelResult.RUIN -> "Start your ruin"
                            WheelResult.WEEK -> "Yes, Mistress"
                        },
                    )
                }
            }
        }
    }
}

/** A month of days, Monday first, each marked. */
@Composable
private fun MonthGrid(month: YearMonth, today: Long, onMonth: (YearMonth) -> Unit, onDay: (Long) -> Unit, marks: (Long) -> Set<DayMark>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onMonth(month.minusMonths(1)) }) { Text("<") }
            Text(
                month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + month.year,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = { onMonth(month.plusMonths(1)) }) { Text(">") }
        }
        Row {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
            }
        }
        val first = month.atDay(1)
        val lead = first.dayOfWeek.value - 1
        val cells = lead + month.lengthOfMonth()
        val rows = (cells + 6) / 7
        for (row in 0 until rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (col in 0 until 7) {
                    val n = row * 7 + col - lead + 1
                    if (n < 1 || n > month.lengthOfMonth()) {
                        Box(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val day = month.atDay(n).toEpochDay()
                        DayCell(n, day == today, marks(day), Modifier.weight(1f).aspectRatio(1f).clickable { onDay(day) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(n: Int, isToday: Boolean, marks: Set<DayMark>, modifier: Modifier) {
    val fill = when {
        DayMark.RELEASED in marks -> Gold
        DayMark.RUINED in marks -> MaterialTheme.colorScheme.errorContainer
        DayMark.DENIED in marks -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }
    val border = when {
        DayMark.RELEASE_DAY in marks -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.onSurface
        else -> Color.Transparent
    }
    Box(
        modifier.background(fill, MaterialTheme.shapes.small).border(2.dp, border, MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$n",
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (DayMark.RELEASED in marks) Color.Black else MaterialTheme.colorScheme.onSurface,
        )
        val symbols = listOfNotNull(
            "★".takeIf { DayMark.RELEASE_DAY in marks },
            "🔒".takeIf { DayMark.CAGED in marks },
            "✕".takeIf { DayMark.FAILED in marks },
        ).joinToString("")
        if (symbols.isNotEmpty()) {
            Text(symbols, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun Legend() {
    Muted("Gold: came · Red: ruined · Grey: edged or denied · ★ her release day · 🔒 caged · ✕ a failure. Tap a day for what happened.")
}

@Composable
private fun MonthSummary(month: YearMonth, today: Long, r: com.guardianangel.data.ReleaseState, s: ReleaseSettings, state: GuardianState) {
    val days = (1..month.lengthOfMonth()).map { month.atDay(it).toEpochDay() }.filter { it <= today }
    val all = days.map { Release.marks(it, today, r, s, state.releaseLog, state.sessions, state.failures, state.chastity?.startedAt, Guardian::dayOf) }
    Text(
        "This month: ${all.count { DayMark.RELEASED in it }} released · ${all.count { DayMark.RUINED in it }} ruined · " +
            "${all.count { DayMark.DENIED in it }} edged or denied · ${all.count { DayMark.FAILED in it }} with a failure",
        style = MaterialTheme.typography.bodySmall,
    )
}

/** What happened on one day. */
@Composable
private fun DayDialog(day: Long, today: Long, config: GuardianConfig, state: GuardianState, onDismiss: () -> Unit) {
    val r = state.release
    val lines = buildList {
        if (r != null && day == r.nextDay && Release.shownDay(r, config.release, today) != null) add("Her release day")
        state.releaseLog.filter { it.day == day }.forEach { add(it.kind.label) }
        state.sessions.filter { Guardian.dayOf(it.at) == day }.forEach {
            val how = when (it.outcome) {
                SessionOutcome.FINISHED -> "came on her command"
                SessionOutcome.RUINED -> "ruined"
                SessionOutcome.RUIN_FAILED -> "couldn't stop at the ruin"
                SessionOutcome.DENIED -> "denied"
                SessionOutcome.MISSED_COMMAND -> "missed her command"
            }
            add((if (it.release) "Release session" else if (it.quick) "Quickshot" else "Session") + ": $how" + if (it.edges > 0) ", ${it.edges} edges" else "")
        }
        state.failures.filter { Guardian.dayOf(it.at) == day }.forEach { add("Failure: ${it.label}") }
        state.chastity?.let { if (day in Guardian.dayOf(it.startedAt)..today) add("Caged") }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("EEEE d MMMM"))) },
        text = { Text(lines.ifEmpty { listOf("Nothing recorded.") }.joinToString("\n")) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

/** Its settings: on and off, your range, the date, her wheel, her reel, the wallpaper countdown. */
@Composable
private fun ReleaseSettingsCard(config: GuardianConfig) {
    val s = config.release
    fun update(change: (ReleaseSettings) -> ReleaseSettings) = Guardian.updateConfig { it.copy(release = change(it.release)) }
    SectionCard("Settings") {
        SwitchRow("Release calendar", "She picks your release day and keeps your calendar.", s.on) { v -> update { it.copy(on = v) } }
        Stepper(
            "Shortest wait",
            "${s.minDays} ${if (s.minDays == 1) "day" else "days"}",
            onMinus = { update { it.copy(minDays = (it.minDays - 1).coerceAtLeast(Release.MIN_DAYS)) } },
            onPlus = { update { it.copy(minDays = (it.minDays + 1).coerceAtMost(it.maxDays)) } },
            enabled = s.on,
        )
        Stepper(
            "Longest wait",
            "${s.maxDays} days",
            onMinus = { update { it.copy(maxDays = (it.maxDays - 1).coerceAtLeast(it.minDays)) } },
            onPlus = { update { it.copy(maxDays = (it.maxDays + 1).coerceAtMost(Release.MAX_DAYS)) } },
            enabled = s.on,
        )
        Muted("A new range counts from her next pick, after your next release day.")
        SwitchRow("Show the date", "Off: \"?\" until the morning of release day.", s.showDate) { v -> update { it.copy(showDate = v) } }
        Stepper(
            "Her wheel: a ruin",
            "${s.ruinChance}%",
            onMinus = { update { it.copy(ruinChance = (it.ruinChance - 5).coerceAtLeast(0)) } },
            onPlus = { update { it.copy(ruinChance = (it.ruinChance + 5).coerceAtMost(100 - it.weekChance)) } },
            enabled = s.on,
        )
        Stepper(
            "Her wheel: another week",
            "${s.weekChance}%",
            onMinus = { update { it.copy(weekChance = (it.weekChance - 5).coerceAtLeast(0)) } },
            onPlus = { update { it.copy(weekChance = (it.weekChance + 5).coerceAtMost(100 - it.ruinChance)) } },
            enabled = s.on,
        )
        Muted("Permission: ${Release.permissionChance(s)}%.")
        SwitchRow("Her reel first", "On release day, before you spin, she plays this week's ruins.", s.reel) { v -> update { it.copy(reel = v) } }
        SwitchRow(
            "Countdown on her wallpaper",
            "The days left on your home and lock screen. Needs Wallpaper control on and the date shown.",
            s.countdownWallpaper,
        ) { v -> update { it.copy(countdownWallpaper = v) } }
        if (config.lockGuard) {
            Muted("Lock guard: switching it off, showing a hidden date, a shorter wait, more permission or her reel off takes 30 minutes.")
        }
    }
}

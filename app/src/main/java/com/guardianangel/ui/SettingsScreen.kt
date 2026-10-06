package com.guardianangel.ui

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.guardianangel.core.Guardian
import com.guardianangel.core.Line
import com.guardianangel.core.Notifier
import com.guardianangel.core.Permissions
import com.guardianangel.core.Rules
import com.guardianangel.core.SiteOpener
import com.guardianangel.core.Voice
import com.guardianangel.core.WallpaperController
import com.guardianangel.data.AppLists
import com.guardianangel.data.ChastitySettings
import com.guardianangel.data.DegradationLevel
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.LockoutScope
import com.guardianangel.data.Mood
import com.guardianangel.data.ProofFrequency
import com.guardianangel.data.PunishmentLength
import com.guardianangel.data.RatingTaste
import com.guardianangel.data.Sites
import com.guardianangel.data.WallpaperMode
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(config: GuardianConfig, openAllowedApps: () -> Unit, openPrompts: () -> Unit, openTasks: () -> Unit, openQuestions: () -> Unit, openSites: () -> Unit, openLines: () -> Unit) {
    val context = LocalContext.current
    val update: ((GuardianConfig) -> GuardianConfig) -> Unit = { Guardian.updateConfig(it) }
    val state by Guardian.state.flow.collectAsState()
    var confirmReset by remember { mutableStateOf(false) }

    fun pickTime(initial: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, initial / 60, initial % 60, true).show()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard("Guardian Angel") {
            SwitchRow("Enabled", "Master on/off. Quit for now is always available too.", config.enabled) {
                Guardian.setEnabled(it)
            }
        }

        SectionCard("App lockouts") {
            SwitchRow("App lockouts", null, config.lockouts.on) { v ->
                update { it.copy(lockouts = it.lockouts.copy(on = v)) }
            }
            ChoiceChips(LockoutScope.entries, config.lockouts.scope, { it.label }) { v ->
                update { it.copy(lockouts = it.lockouts.copy(scope = v)) }
            }
            Muted(
                if (config.lockouts.scope == LockoutScope.SOCIAL_MEDIA) {
                    "Instagram, TikTok, X, Snapchat, Facebook, Reddit and YouTube."
                } else {
                    "Every app except the Always-allowed list, the phone, and this app."
                },
            )
            Muted("Hard blocked. Way in: a ${Rules.WAIT_SECONDS} second wait or an everyday photo. Trying never counts as a failure.")
            HorizontalDivider()
            SwitchRow(
                "Ask permission for guarded apps",
                "Adds an \"Ask her\" button. She may grant, deny, or demand an everyday photo.",
                config.askPermission.on,
            ) { v -> update { it.copy(askPermission = it.askPermission.copy(on = v)) } }
            HorizontalDivider()
            OutlinedButton(onClick = openAllowedApps) {
                Text("Always-allowed apps (${config.alwaysAllowed.size})")
            }
        }

        SectionCard("Bedtime") {
            SwitchRow("Bedtime", "Hard blocks everything not always-allowed in this window, with the same way in as lockouts.", config.bedtime.on) { v ->
                update { it.copy(bedtime = it.bedtime.copy(on = v)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = {
                    pickTime(config.bedtime.startMinute) { m -> update { it.copy(bedtime = it.bedtime.copy(startMinute = m)) } }
                }) { Text("From ${formatMinuteOfDay(config.bedtime.startMinute)}") }
                FilledTonalButton(onClick = {
                    pickTime(config.bedtime.endMinute) { m -> update { it.copy(bedtime = it.bedtime.copy(endMinute = m)) } }
                }) { Text("To ${formatMinuteOfDay(config.bedtime.endMinute)}") }
            }
        }

        SectionCard("Quiet hours") {
            SwitchRow(
                "Quiet hours",
                "She lets you sleep. Check-ins in this window are silent, and she never sets a task, photo or summons " +
                    "that would be due inside it. Doesn't lock anything; Bedtime does that, and counts as quiet too.",
                config.quietHours.on,
            ) { v -> update { it.copy(quietHours = it.quietHours.copy(on = v)) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = {
                    pickTime(config.quietHours.startMinute) { m -> update { it.copy(quietHours = it.quietHours.copy(startMinute = m)) } }
                }) { Text("From ${formatMinuteOfDay(config.quietHours.startMinute)}") }
                FilledTonalButton(onClick = {
                    pickTime(config.quietHours.endMinute) { m -> update { it.copy(quietHours = it.quietHours.copy(endMinute = m)) } }
                }) { Text("To ${formatMinuteOfDay(config.quietHours.endMinute)}") }
            }
            if (config.quietHours.on && config.quietHours.startMinute == config.quietHours.endMinute) {
                Text(
                    "Start and end are the same, so there are no quiet hours.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        SectionCard("Wallpaper") {
            SwitchRow("Wallpaper control", "She sets your wallpaper to her images.", config.wallpaper.on) { v ->
                update { it.copy(wallpaper = it.wallpaper.copy(on = v)) }
            }
            ChoiceChips(WallpaperMode.entries, config.wallpaper.mode, { it.label }, config.wallpaper.on) { v ->
                update { it.copy(wallpaper = it.wallpaper.copy(mode = v)) }
            }
            Muted("\"Set and lock\" puts her wallpaper back whenever it changes, while she's on.")
            OutlinedButton(
                onClick = { WallpaperController.applyAsync(context) },
                enabled = config.enabled && config.wallpaper.on,
            ) { Text("Apply now") }
        }

        SectionCard("Chastity") {
            SwitchRow("Chastity mode", "Turn off for days without the cage.", config.chastity.on) { v ->
                update { it.copy(chastity = it.chastity.copy(on = v)) }
            }
            Muted("She picks a lock length between these two.")
            Stepper(
                "Shortest lock",
                formatMinutes(config.chastity.minLockMinutes),
                onMinus = { update { it.copy(chastity = it.chastity.withMinLock(Rules.stepLockMinutes(it.chastity.minLockMinutes, up = false))) } },
                onPlus = { update { it.copy(chastity = it.chastity.withMinLock(Rules.stepLockMinutes(it.chastity.minLockMinutes, up = true))) } },
            )
            Stepper(
                "Longest picked lock",
                formatMinutes(config.chastity.maxLockMinutes),
                onMinus = { update { it.copy(chastity = it.chastity.withMaxLock(Rules.stepLockMinutes(it.chastity.maxLockMinutes, up = false))) } },
                onPlus = { update { it.copy(chastity = it.chastity.withMaxLock(Rules.stepLockMinutes(it.chastity.maxLockMinutes, up = true))) } },
            )
            SwitchRow(
                "She can add time",
                "For failures, missed proof, denied begging, or at her whim.",
                config.chastity.canAddTime,
            ) { v ->
                update { it.copy(chastity = it.chastity.copy(canAddTime = v)) }
            }
            Stepper(
                "Amount per addition",
                formatMinutes(config.chastity.addMinutes),
                onMinus = { update { it.copy(chastity = it.chastity.copy(addMinutes = (it.chastity.addMinutes - 15).coerceAtLeast(15))) } },
                onPlus = { update { it.copy(chastity = it.chastity.copy(addMinutes = (it.chastity.addMinutes + 15).coerceAtMost(24 * 60))) } },
                enabled = config.chastity.canAddTime,
            )
            Stepper(
                "Hard cap (incl. added time)",
                "${config.chastity.maxHours}h",
                onMinus = { update { it.copy(chastity = it.chastity.copy(maxHours = Rules.stepCapHours(it.chastity.maxHours, up = false))) } },
                onPlus = { update { it.copy(chastity = it.chastity.copy(maxHours = Rules.stepCapHours(it.chastity.maxHours, up = true))) } },
            )
            if (config.chastity.maxLockMinutes > config.chastity.maxHours * 60) {
                Text(
                    "The hard cap is shorter than your longest picked lock, so locks stop at ${config.chastity.maxHours}h.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        SectionCard("Photo proof and check-ins") {
            SwitchRow("Photo proof requests", "She asks for photos at check-ins.", config.photoProof.on) { v ->
                update { it.copy(photoProof = it.photoProof.copy(on = v)) }
            }
            ChoiceChips(ProofFrequency.entries, config.photoProof.frequency, { it.label }, config.photoProof.on) { v ->
                update { it.copy(photoProof = it.photoProof.copy(frequency = v)) }
            }
            Muted("Outside chastity, she picks what to photograph from your prompt list. This list is also used when you ask permission or bypass a lockout.")
            OutlinedButton(onClick = openPrompts) {
                Text("What she can ask for (${config.proofPrompts.size})")
            }
            Text("Check in at least every ${formatMinutes(config.checkInMinutes)}")
            Slider(
                value = config.checkInMinutes.toFloat(),
                onValueChange = { v ->
                    val minutes = ((v / 15f).roundToInt() * 15).coerceIn(30, 120)
                    if (minutes != config.checkInMinutes) update { it.copy(checkInMinutes = minutes) }
                },
                valueRange = 30f..120f,
                steps = 5,
            )
        }

        SectionCard("Rules & Tasks") {
            SwitchRow(
                "Rules & Tasks",
                "At about 1 in 3 check-ins she gives you a rule or task: app-enforced rules, photo tasks, stillness or lines.",
                config.tasksOn,
            ) { v -> update { it.copy(tasksOn = v) } }
            OutlinedButton(onClick = openTasks) { Text("Her list (${config.taskList.size})") }
        }

        SectionCard("Shows up") {
            SwitchRow(
                "Shows up",
                "At some check-ins she wants you. Ignore her for ${Rules.SUMMON_LOCK_MINUTES} minutes and everything locks until you answer " +
                    "(phone and Quit for now still work). ${Rules.MAX_WRONG_ANSWERS} wrong answers count as a failure.",
                config.showsUpOn,
            ) { v -> update { it.copy(showsUpOn = v) } }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = openQuestions) { Text("Her questions (${config.questions.size})") }
                OutlinedButton(
                    onClick = {
                        // Without notifications she can't call you, so open her screen straight away.
                        if (Guardian.summon() && !Notifier.canNotify(context)) context.startActivity(ShowUpActivity.intent(context))
                    },
                    enabled = config.enabled && config.showsUpOn,
                ) { Text("Try it now") }
            }
            if (!Notifier.canNotify(context)) {
                Muted("Notifications are off, so she won't show up on her own. Turn them on in Permissions.")
            }
        }

        SectionCard("Open sites") {
            SwitchRow(
                "Open sites",
                "At almost every check-in she warns you for ${Rules.SITE_WARNING_SECONDS} seconds, then opens one of your sites " +
                    "in Chrome. Stay for the set time; leaving early counts as a failure. Never on the lock screen, " +
                    "during a call, or in quiet hours or bedtime.",
                config.sitesOn,
            ) { v -> update { it.copy(sitesOn = v) } }
            Stepper(
                "Stay for",
                formatMinutes(config.siteMinutes),
                onMinus = { update { it.copy(siteMinutes = (it.siteMinutes - 1).coerceIn(Sites.MIN_MINUTES, Sites.MAX_MINUTES)) } },
                onPlus = { update { it.copy(siteMinutes = (it.siteMinutes + 1).coerceIn(Sites.MIN_MINUTES, Sites.MAX_MINUTES)) } },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = openSites) { Text("Your sites (${config.siteList.size})") }
                OutlinedButton(
                    onClick = { Guardian.startVisit(asked = true) },
                    enabled = config.enabled && config.sitesOn && config.siteList.isNotEmpty() && state.visit == null,
                ) { Text("Ask her now") }
            }
            val browser = remember { SiteOpener.browserPackage(context) }
            when {
                browser == null -> Muted("No browser found, so she can't open sites.")
                browser != SiteOpener.CHROME -> Muted("Chrome isn't installed, so she uses your default browser.")
            }
            if (!Permissions.accessibility(context)) {
                Muted("She needs Accessibility (in Permissions) to open sites and see when you leave.")
            }
        }

        SectionCard("Rate me") {
            SwitchRow(
                "Rate me",
                "She scores your measurements against published data, and how well you show her in a photo. " +
                    "The photo is checked on your phone and deleted straight after; only her scores are kept.",
                config.rating.on,
            ) { v -> update { it.copy(rating = it.rating.copy(on = v)) } }
            ChoiceChips(RatingTaste.entries, config.rating.taste, { it.label }, config.rating.on) { v ->
                update { it.copy(rating = it.rating.copy(taste = v)) }
            }
            ChoiceChips(listOf(false, true), config.rating.inches, { if (it) "Inches" else "cm" }, config.rating.on) { v ->
                update { it.copy(rating = it.rating.copy(inches = v)) }
            }
            if (state.ratings.isNotEmpty()) {
                Muted("Her last scores: " + state.ratings.takeLast(5).joinToString(", ") { "${it.score}/10" })
                TextButton(onClick = { Guardian.clearRatings() }) { Text("Clear her scores") }
            }
        }

        SectionCard("Discipline") {
            SwitchRow("Degradation on failure", null, config.degradation.on) { v ->
                update { it.copy(degradation = it.degradation.copy(on = v)) }
            }
            ChoiceChips(DegradationLevel.entries, config.degradation.level, { it.label }, config.degradation.on) { v ->
                update { it.copy(degradation = it.degradation.copy(level = v)) }
            }
            HorizontalDivider()
            SwitchRow("Lockout as punishment", "Failures lock social media (or everything) for a while.", config.punishment.on) { v ->
                update { it.copy(punishment = it.punishment.copy(on = v)) }
            }
            ChoiceChips(PunishmentLength.entries, config.punishment.length, { it.label }, config.punishment.on) { v ->
                update { it.copy(punishment = it.punishment.copy(length = v)) }
            }
        }

        SectionCard("Presentation") {
            SwitchRow("Discreet notifications", "Neutral wording, nothing explicit on the lock screen.", config.discreetNotifications) { v ->
                update { it.copy(discreetNotifications = v) }
            }
            Text("Mood")
            ChoiceChips(Mood.entries, config.mood, { it.label }) { v -> update { it.copy(mood = v) } }
            Muted("Mood only changes what she says. One exception: in a strict mood she's harsher when you beg to be released early.")
            val edited = Line.entries.count { Voice.isEdited(it, config.lineOverrides) }
            OutlinedButton(onClick = openLines) { Text(if (edited > 0) "Her lines ($edited edited)" else "Her lines") }
            Muted("Edit what she says in each situation, in both moods.")
            SwitchRow("Merit points and levels", null, config.meritOn) { v -> update { it.copy(meritOn = v) } }
        }

        OutlinedButton(onClick = { confirmReset = true }) { Text("Reset all settings to defaults") }

        SectionCard("About") {
            val version = remember {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
            }
            Text("Version $version")
            OutlinedButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LATEST_RELEASE_URL))) }
            }) { Text("Get the latest version") }
            Muted("New versions install over this one and keep your settings and photos.")
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset all settings?") },
            text = {
                Text(
                    "Every setting goes back to its default, including your photo prompts, rules and tasks, questions, sites and her lines. " +
                        "Your merit, photos and any running lock are kept.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    update { GuardianConfig(alwaysAllowed = AppLists.DEFAULT_ALLOWED) }
                    confirmReset = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

private const val LOCK_MIN = 30
private const val LOCK_LIMIT = 7 * 24 * 60

/** Keeps shortest <= longest: moving one past the other drags it along. */
private fun ChastitySettings.withMinLock(minutes: Int): ChastitySettings {
    val m = minutes.coerceIn(LOCK_MIN, LOCK_LIMIT)
    return copy(minLockMinutes = m, maxLockMinutes = maxOf(maxLockMinutes, m))
}

private fun ChastitySettings.withMaxLock(minutes: Int): ChastitySettings {
    val m = minutes.coerceIn(LOCK_MIN, LOCK_LIMIT)
    return copy(maxLockMinutes = m, minLockMinutes = minOf(minLockMinutes, m))
}

const val LATEST_RELEASE_URL = "https://github.com/miellennium123-hue/TechD/releases/latest"

fun formatMinutes(minutes: Int): String = when {
    minutes % 60 == 0 -> "${minutes / 60}h"
    minutes > 60 -> "${minutes / 60}h ${minutes % 60}m"
    else -> "${minutes}m"
}

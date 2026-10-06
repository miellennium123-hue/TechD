package com.guardianangel.ui

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.guardianangel.core.Guardian
import com.guardianangel.core.Rules
import com.guardianangel.core.WallpaperController
import com.guardianangel.data.AppLists
import com.guardianangel.data.ChastitySettings
import com.guardianangel.data.DegradationLevel
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.LockoutScope
import com.guardianangel.data.Mood
import com.guardianangel.data.ProofFrequency
import com.guardianangel.data.PunishmentLength
import com.guardianangel.data.WallpaperMode
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(config: GuardianConfig, openAllowedApps: () -> Unit, openPrompts: () -> Unit, openTasks: () -> Unit, openQuestions: () -> Unit) {
    val context = LocalContext.current
    val update: ((GuardianConfig) -> GuardianConfig) -> Unit = { Guardian.updateConfig(it) }

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
                onMinus = { update { it.copy(chastity = it.chastity.withMinLock(it.chastity.minLockMinutes - LOCK_STEP)) } },
                onPlus = { update { it.copy(chastity = it.chastity.withMinLock(it.chastity.minLockMinutes + LOCK_STEP)) } },
            )
            Stepper(
                "Longest picked lock",
                formatMinutes(config.chastity.maxLockMinutes),
                onMinus = { update { it.copy(chastity = it.chastity.withMaxLock(it.chastity.maxLockMinutes - LOCK_STEP)) } },
                onPlus = { update { it.copy(chastity = it.chastity.withMaxLock(it.chastity.maxLockMinutes + LOCK_STEP)) } },
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
                onMinus = { update { it.copy(chastity = it.chastity.copy(maxHours = (it.chastity.maxHours - 1).coerceAtLeast(1))) } },
                onPlus = { update { it.copy(chastity = it.chastity.copy(maxHours = (it.chastity.maxHours + 1).coerceAtMost(168))) } },
            )
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = openQuestions) { Text("Her questions (${config.questions.size})") }
                OutlinedButton(onClick = { Guardian.summon() }, enabled = config.enabled && config.showsUpOn) { Text("Try it now") }
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
            Text("Mood (dialogue only)")
            ChoiceChips(Mood.entries, config.mood, { it.label }) { v -> update { it.copy(mood = v) } }
            Muted("Mood only changes what she says. One exception: in a strict mood she's harsher when you beg to be released early.")
            SwitchRow("Merit points and levels", null, config.meritOn) { v -> update { it.copy(meritOn = v) } }
        }

        OutlinedButton(onClick = {
            update { GuardianConfig(alwaysAllowed = AppLists.DEFAULT_ALLOWED) }
        }) { Text("Reset all settings to defaults") }

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
}

private const val LOCK_STEP = 30
private const val LOCK_LIMIT = 7 * 24 * 60

/** Keeps shortest <= longest: moving one past the other drags it along. */
private fun ChastitySettings.withMinLock(minutes: Int): ChastitySettings {
    val m = minutes.coerceIn(LOCK_STEP, LOCK_LIMIT)
    return copy(minLockMinutes = m, maxLockMinutes = maxOf(maxLockMinutes, m))
}

private fun ChastitySettings.withMaxLock(minutes: Int): ChastitySettings {
    val m = minutes.coerceIn(LOCK_STEP, LOCK_LIMIT)
    return copy(maxLockMinutes = m, minLockMinutes = minOf(minLockMinutes, m))
}

const val LATEST_RELEASE_URL = "https://github.com/miellennium123-hue/TechD/releases/latest"

fun formatMinutes(minutes: Int): String = when {
    minutes % 60 == 0 -> "${minutes / 60}h"
    minutes > 60 -> "${minutes / 60}h ${minutes % 60}m"
    else -> "${minutes}m"
}

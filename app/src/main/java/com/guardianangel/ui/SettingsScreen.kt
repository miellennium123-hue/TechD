package com.guardianangel.ui

import android.app.TimePickerDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.guardianangel.core.Guardian
import com.guardianangel.core.WallpaperController
import com.guardianangel.data.AppLists
import com.guardianangel.data.DegradationLevel
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.LockoutScope
import com.guardianangel.data.Mood
import com.guardianangel.data.ProofFrequency
import com.guardianangel.data.PunishmentLength
import com.guardianangel.data.WallpaperMode
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(config: GuardianConfig, openAllowedApps: () -> Unit, openPrompts: () -> Unit) {
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
            IntensityPicker(config.lockouts.intensity, config.lockouts.on) { v ->
                update { it.copy(lockouts = it.lockouts.copy(intensity = v)) }
            }
            HorizontalDivider()
            SwitchRow(
                "Ask permission for guarded apps",
                "Opening a guarded app means asking her first. She may grant, deny, or demand photo proof.",
                config.askPermission.on,
            ) { v -> update { it.copy(askPermission = it.askPermission.copy(on = v)) } }
            Muted("How hard she is to convince:")
            IntensityPicker(config.askPermission.intensity, config.askPermission.on) { v ->
                update { it.copy(askPermission = it.askPermission.copy(intensity = v)) }
            }
            HorizontalDivider()
            OutlinedButton(onClick = openAllowedApps) {
                Text("Always-allowed apps (${config.alwaysAllowed.size})")
            }
        }

        SectionCard("Bedtime") {
            SwitchRow("Bedtime", "She restricts phone use during this window.", config.bedtime.on) { v ->
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
            IntensityPicker(config.bedtime.intensity, config.bedtime.on) { v ->
                update { it.copy(bedtime = it.bedtime.copy(intensity = v)) }
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
            IntensityPicker(config.chastity.intensity, config.chastity.on) { v ->
                update { it.copy(chastity = it.chastity.copy(intensity = v)) }
            }
            SwitchRow("She can add time", "For failures, missed proof, or at her whim (Strict and up).", config.chastity.canAddTime) { v ->
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
                "Longest lock (incl. added time)",
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
            Muted("Mood only changes what she says. It never changes rules, timers or punishments.")
            SwitchRow("Merit points and levels", null, config.meritOn) { v -> update { it.copy(meritOn = v) } }
        }

        OutlinedButton(onClick = {
            update { GuardianConfig(alwaysAllowed = AppLists.DEFAULT_ALLOWED) }
        }) { Text("Reset all settings to defaults") }
    }
}

fun formatMinutes(minutes: Int): String = when {
    minutes % 60 == 0 -> "${minutes / 60}h"
    minutes > 60 -> "${minutes / 60}h ${minutes % 60}m"
    else -> "${minutes}m"
}

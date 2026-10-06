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
import com.guardianangel.core.Peek
import com.guardianangel.core.ScreenPeek
import com.guardianangel.core.WallpaperController
import com.guardianangel.data.AppLists
import com.guardianangel.data.Backgrounds
import com.guardianangel.data.DegradationLevel
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.LockoutScope
import com.guardianangel.data.LockoutSettings
import com.guardianangel.data.MarkCorner
import com.guardianangel.data.Mood
import com.guardianangel.data.ProofFrequency
import com.guardianangel.data.PunishmentLength
import com.guardianangel.data.RatingTaste
import com.guardianangel.data.Sites
import com.guardianangel.data.WallpaperMode
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(config: GuardianConfig, openAllowedApps: () -> Unit, openPrompts: () -> Unit, openTasks: () -> Unit, openQuestions: () -> Unit, openSites: () -> Unit, openLines: () -> Unit, openBackgrounds: () -> Unit) {
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
        SectionCard("Her") {
            EnabledSwitch("Enabled", "Master on/off. Quit for now is always available too.", config.enabled)
            SwitchRow(
                "Lock guard",
                "While she's on: Quit for now and switching her off are slow, switching off any of her controls " +
                    "takes 30 minutes, her settings and uninstall screens are blocked, and switching off her watch is a " +
                    "failure. Turning it on is instant; turning it off takes 30 minutes too.",
                config.lockGuard,
            ) { v -> update { it.copy(lockGuard = v) } }
            if (config.lockGuard) Muted("Also make her device admin in Permissions, so uninstalling her takes extra steps.")
        }

        GroupHeader("Phone control")
        FoldCard("App lockouts", config.lockouts.on) {
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
            Muted(
                "Timed blocks: at check-ins she locks these apps for a while, between the two lengths below. " +
                    "During a block there's no waiting, no photo and no asking. The only way in: " +
                    "${Rules.BUY_MERIT} merit for ${Rules.BUY_MINUTES} minutes. Trying never counts as a failure.",
            )
            Stepper(
                "Shortest block",
                formatMinutes(config.lockouts.minBlockMinutes),
                onMinus = { update { it.copy(lockouts = it.lockouts.withMinBlock(Rules.stepLockMinutes(it.lockouts.minBlockMinutes, up = false))) } },
                onPlus = { update { it.copy(lockouts = it.lockouts.withMinBlock(Rules.stepLockMinutes(it.lockouts.minBlockMinutes, up = true))) } },
            )
            Stepper(
                "Longest block",
                formatMinutes(config.lockouts.maxBlockMinutes),
                onMinus = { update { it.copy(lockouts = it.lockouts.withMaxBlock(Rules.stepLockMinutes(it.lockouts.maxBlockMinutes, up = false))) } },
                onPlus = { update { it.copy(lockouts = it.lockouts.withMaxBlock(Rules.stepLockMinutes(it.lockouts.maxBlockMinutes, up = true))) } },
            )
            if (config.lockGuard) Muted("Lock guard: while a block runs, these settings and Always-allowed can't be changed.")
            HorizontalDivider()
            SwitchRow(
                "Ask permission for guarded apps",
                "Outside her blocks: an \"Ask her\" button. She may grant, deny, or demand an everyday photo. During a block, no asking.",
                config.askPermission.on,
            ) { v -> update { it.copy(askPermission = it.askPermission.copy(on = v)) } }
            HorizontalDivider()
            OutlinedButton(onClick = openAllowedApps) {
                Text("Always-allowed apps (${config.alwaysAllowed.size})")
            }
        }

        FoldCard("Bedtime", config.bedtime.on) {
            SwitchRow("Bedtime", "Hard blocks everything not always-allowed in this window. No way in until it ends.", config.bedtime.on) { v ->
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
            SwitchRow(
                "Bedtime screen",
                "Her full-screen \"Locked out\" screen covers your home screen during bedtime. Only Always-allowed apps and the phone open from it.",
                config.bedtime.screen,
            ) { v -> update { it.copy(bedtime = it.bedtime.copy(screen = v)) } }
        }

        FoldCard("Quiet hours", config.quietHours.on) {
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

        FoldCard("Wallpaper", config.wallpaper.on) {
            SwitchRow("Wallpaper control", "She sets your home and lock screen to her backgrounds.", config.wallpaper.on) { v ->
                update { it.copy(wallpaper = it.wallpaper.copy(on = v)) }
            }
            ChoiceChips(WallpaperMode.entries, config.wallpaper.mode, { it.label }, config.wallpaper.on) { v ->
                update { it.copy(wallpaper = it.wallpaper.copy(mode = v)) }
            }
            Muted("\"Set and lock\" puts her wallpaper back whenever it changes, while she's on.")
            SwitchRow("Cycle backgrounds", "She changes to the next background every few minutes.", config.wallpaper.cycle) { v ->
                update { it.copy(wallpaper = it.wallpaper.copy(cycle = v)) }
            }
            if (config.wallpaper.cycle) {
                Stepper(
                    "Every",
                    formatMinutes(config.wallpaper.cycleMinutes),
                    onMinus = { update { it.copy(wallpaper = it.wallpaper.copy(cycleMinutes = (it.wallpaper.cycleMinutes - 1).coerceIn(Backgrounds.MIN_CYCLE_MINUTES, Backgrounds.MAX_CYCLE_MINUTES))) } },
                    onPlus = { update { it.copy(wallpaper = it.wallpaper.copy(cycleMinutes = (it.wallpaper.cycleMinutes + 1).coerceIn(Backgrounds.MIN_CYCLE_MINUTES, Backgrounds.MAX_CYCLE_MINUTES))) } },
                )
            }
            OutlinedButton(onClick = openBackgrounds) {
                Text("Backgrounds (${Backgrounds.BUILT_IN.size - config.wallpaper.hiddenBuiltIns.size} hers, add your own)")
            }
            OutlinedButton(
                onClick = { WallpaperController.applyAsync(context) },
                enabled = config.enabled && config.wallpaper.on,
            ) { Text("Apply now") }
        }

        FoldCard("Her mark", config.mark.on) {
            SwitchRow(
                "Her mark",
                "Her small gold collar badge sits in a corner over every app while she's on (not over her own screens).",
                config.mark.on,
            ) { v -> update { it.copy(mark = it.mark.copy(on = v)) } }
            ChoiceChips(MarkCorner.entries, config.mark.corner, { it.label }, config.mark.on) { v ->
                update { it.copy(mark = it.mark.copy(corner = v)) }
            }
            SwitchRow(
                "Dark tint during her blocks",
                "While her app block, bedtime, a punishment, one of her rules or an ignored summons runs, the screen gets darker. " +
                    "Not over her own screens.",
                config.mark.tint,
            ) { v -> update { it.copy(mark = it.mark.copy(tint = v)) } }
            Muted("Taps go straight through both, so nothing under them is ever blocked. Shown by her watch (Accessibility).")
        }

        FoldCard("She peeks", config.peek.on) {
            val supported = ScreenPeek.supported()
            SwitchRow(
                "She peeks",
                "About every ${Peek.EVERY_MINUTES} minutes she captures your screen into her private gallery and comments on what you were doing.",
                config.peek.on,
            ) { v -> if (supported || !v) update { it.copy(peek = it.peek.copy(on = v)) } }
            if (!supported) {
                Text("Needs Android 11 or later.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Muted(
                "Never with the screen off or locked, never while the keyboard is up, and never at the phone, " +
                    "her own screens or your Always-allowed apps (put banking apps there). Screenshots stay in this app only, " +
                    "she keeps the newest ${Peek.KEEP}. If nothing shows up after updating, switch her watch off and on in Accessibility.",
            )
        }

        GroupHeader("Check-ins")
        FoldCard("Photo proof and check-ins", config.photoProof.on) {
            SwitchRow("Photo proof requests", "She asks for photos at check-ins.", config.photoProof.on) { v ->
                update { it.copy(photoProof = it.photoProof.copy(on = v)) }
            }
            ChoiceChips(ProofFrequency.entries, config.photoProof.frequency, { it.label }, config.photoProof.on) { v ->
                update { it.copy(photoProof = it.photoProof.copy(frequency = v)) }
            }
            Muted("Outside chastity, she picks what to photograph from your prompt list. This list is also used when you ask permission.")
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

        FoldCard("Rules & Tasks", config.tasksOn) {
            SwitchRow(
                "Rules & Tasks",
                "At about 1 in 3 check-ins she gives you a rule or task: app-enforced rules, photo tasks, stillness or lines.",
                config.tasksOn,
            ) { v -> update { it.copy(tasksOn = v) } }
            OutlinedButton(onClick = openTasks) { Text("Her list (${config.taskList.size})") }
        }

        FoldCard("Shows up", config.showsUpOn) {
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

        FoldCard("Open sites", config.sitesOn) {
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

        GroupHeader("Extras")
        Muted("Chastity and Guided sessions have their own screens on Home.")
        FoldCard("Rate me", config.rating.on) {
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

        GroupHeader("Discipline")
        FoldCard("When you fail", config.degradation.on || config.punishment.on) {
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

        GroupHeader("Her voice")
        FoldCard("Mood, lines and merit", null) {
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

        GroupHeader("App")
        SectionCard("About") {
            val version = remember {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
            }
            Text("Version $version")
            OutlinedButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LATEST_RELEASE_URL))) }
            }) { Text("Get the latest version") }
            Muted("New versions install over this one and keep your settings and photos.")
            TextButton(onClick = { confirmReset = true }) { Text("Reset all settings to defaults", color = MaterialTheme.colorScheme.error) }
            HorizontalDivider()
            SwitchRow(
                "Debug mode",
                "An instant \"Debug: shut down\" button next to Quit for now on every screen. It ends everything and switches her off. " +
                    "Lock guard still works as usual. Can only be switched while she's off.",
                config.debugMode,
            ) { v -> Guardian.setDebugMode(v) }
            if (config.enabled) Muted("She's on, so debug mode stays ${if (config.debugMode) "on" else "off"} until you switch her off.")
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

const val LATEST_RELEASE_URL = "https://github.com/miellennium123-hue/TechD/releases/latest"

fun formatMinutes(minutes: Int): String = when {
    minutes % 60 == 0 -> "${minutes / 60}h"
    minutes > 60 -> "${minutes / 60}h ${minutes % 60}m"
    else -> "${minutes}m"
}

/** Keeps the shortest block no longer than the longest (and the other way round). */
private fun LockoutSettings.withMinBlock(minutes: Int): LockoutSettings =
    copy(minBlockMinutes = minutes, maxBlockMinutes = maxOf(maxBlockMinutes, minutes))

private fun LockoutSettings.withMaxBlock(minutes: Int): LockoutSettings =
    copy(maxBlockMinutes = minutes, minBlockMinutes = minOf(minBlockMinutes, minutes))

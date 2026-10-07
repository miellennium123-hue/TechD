package com.guardianangel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.guardianangel.core.Guardian
import com.guardianangel.core.Usage
import com.guardianangel.data.DayReport
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.GuardianState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Daily report (round 68): today so far, and her reports, newest first. */
@Composable
fun ReportsScreen(config: GuardianConfig, state: GuardianState) {
    var confirmClear by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard("Today so far") {
            if (Usage.counting(config)) {
                TodayText(state)
                Muted("Her report comes at ${formatMinuteOfDay(config.report.reportMinute)}.")
            } else {
                Muted("She only counts while she's on and Daily report is on (Settings > Phone control).")
            }
        }
        if (state.reports.isEmpty()) {
            Muted("No reports yet. Her first one comes at your report time, after a day of counting.")
        }
        state.reports.asReversed().forEach { ReportCard(it) }
        if (state.reports.isNotEmpty()) {
            OutlinedButton(onClick = { confirmClear = true }) { Text("Delete all reports") }
        }
        Muted("Only counts are kept: unlocks and time in each app, never what was on screen. Her own screens, the home screen and the phone don't count.")
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Delete all reports?") },
            text = { Text("Today's count keeps going.") },
            confirmButton = {
                TextButton(onClick = {
                    Guardian.clearReports()
                    confirmClear = false
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

/** Today's unlocks and screen time. Shows zero once her day has rolled over. */
@Composable
fun TodayText(state: GuardianState) {
    Text(
        "${state.usage.unlocks} unlocks, ${formatScreenTime(state.usage.screenMs)} in apps",
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun ReportCard(report: DayReport) {
    val date = remember(report.at) { SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(report.at)) }
    SectionCard(date) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                report.grade.name,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Bold,
            )
            Text(report.line, Modifier.weight(1f))
        }
        Text("${report.unlocks} unlocks (goal ${report.unlockGoal})")
        Text("${formatScreenTime(report.screenMs)} in apps (goal ${formatMinutes(report.screenGoalMinutes)})")
        report.top.forEach { Muted("${it.app}: ${formatScreenTime(it.ms)}") }
    }
}

/** Minutes under an hour, otherwise hours and minutes. */
fun formatScreenTime(ms: Long): String {
    val minutes = (ms / 60_000).toInt()
    return if (minutes < 60) "${minutes}m" else formatMinutes(minutes)
}

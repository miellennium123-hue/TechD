package com.guardianangel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.guardianangel.core.Guardian
import com.guardianangel.data.GuardianState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Her record (round 94): every failure, newest first, with when it happened, why, and what it cost you:
 * merit, punishment lockout time (after her cap) and chastity time. She keeps the last 100.
 */
@Composable
fun RecordScreen(state: GuardianState) {
    var confirmClear by remember { mutableStateOf(false) }
    val date = remember { SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()) }
    val failures = state.failures.reversed()

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Muted("Every time you failed her, and what it cost you. She keeps the last ${Guardian.FAILURE_RECORD_KEEP}.")
        if (failures.isEmpty()) {
            Text("A clean record. For now.")
        } else {
            val day = System.currentTimeMillis() - 24 * 60 * 60_000L
            val today = state.failures.filter { it.at >= day }
            Text(
                "Last 24 hours: ${today.size} ${if (today.size == 1) "failure" else "failures"}, " +
                    "${today.sumOf { it.punishmentMinutes }.let(::formatMinutesShort)} of punishment added.",
                style = MaterialTheme.typography.titleSmall,
            )
            TextButton(onClick = { confirmClear = true }) { Text("Clear her record") }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(failures) { f ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(date.format(Date(f.at)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(f.label, style = MaterialTheme.typography.bodyLarge)
                        Muted(
                            listOfNotNull(
                                "-${f.merit} merit".takeIf { f.merit > 0 },
                                "+${formatMinutesShort(f.punishmentMinutes)} punishment".takeIf { f.punishmentMinutes > 0 },
                                "+${formatMinutesShort(f.chastityMinutes)} chastity".takeIf { f.chastityMinutes > 0 },
                            ).joinToString(" · ").ifBlank { "No lockout or chastity time added" },
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear her record?") },
            text = { Text("This only clears the list. Nothing she added is taken back.") },
            confirmButton = {
                TextButton(onClick = {
                    Guardian.clearRecord()
                    confirmClear = false
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

/** 30m, 3h, 3h 30m. */
private fun formatMinutesShort(minutes: Int): String = when {
    minutes < 60 -> "${minutes}m"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes / 60}h ${minutes % 60}m"
}

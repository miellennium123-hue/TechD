package com.guardianangel.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.guardianangel.core.Guardian
import com.guardianangel.core.Line
import com.guardianangel.core.Voice
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.Mood

/** Every situation she speaks in. Tap one to edit its lines. */
@Composable
fun LinesScreen(config: GuardianConfig, open: (Line) -> Unit) {
    var confirmReset by remember { mutableStateOf(false) }
    val edited = Line.entries.count { Voice.isEdited(it, config.lineOverrides) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Muted(
            "Everything she says, by situation. Tap one to edit her sweet and strict lines. " +
                "Situations you haven't edited keep getting her newest lines when the app updates. " +
                "With Discreet notifications on, notifications still show only neutral text.",
        )
        TextButton(onClick = { confirmReset = true }, enabled = edited > 0) {
            Text(if (edited > 0) "Reset all ($edited edited)" else "Reset all")
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Line.entries.groupBy { it.group }.forEach { (group, lines) ->
                item(key = group) {
                    Text(group, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                }
                items(lines, key = { it.name }) { line ->
                    Card(Modifier.fillMaxWidth().clickable { open(line) }) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(line.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                if (Voice.isEdited(line, config.lineOverrides)) {
                                    Text("Edited", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                            Muted(line.note)
                        }
                    }
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset all her lines?") },
            text = { Text("Every situation goes back to her built-in lines. Your edited lines are deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    Guardian.updateConfig { it.copy(lineOverrides = emptyMap()) }
                    confirmReset = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

/** One situation: her sweet and strict lines. Add, edit, delete, or reset to hers. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LineScreen(config: GuardianConfig, line: Line) {
    var mood by rememberSaveable { mutableStateOf(Mood.SWEET) }
    var editing by remember { mutableIntStateOf(-1) }
    var text by remember { mutableStateOf("") }
    val lines = Voice.shown(line, mood, config.lineOverrides)

    fun clear() {
        editing = -1
        text = ""
    }

    fun change(transform: (List<String>) -> List<String>) {
        val m = mood
        Guardian.updateConfig { c -> c.copy(lineOverrides = Voice.edit(c.lineOverrides, line, m, transform)) }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(line.label, style = MaterialTheme.typography.titleLarge)
        Muted(line.note)
        ChoiceChips(listOf(Mood.SWEET, Mood.STRICT), mood, { it.label }) {
            mood = it
            clear()
        }
        OutlinedTextField(
            text,
            { text = it },
            Modifier.fillMaxWidth(),
            label = { Text(if (editing >= 0) "Edit line" else "New ${mood.label.lowercase()} line") },
            minLines = 2,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = text.isNotBlank(),
                onClick = {
                    val index = editing
                    val new = text.trim()
                    change { list -> if (index >= 0) list.mapIndexed { i, l -> if (i == index) new else l } else list + new }
                    clear()
                },
            ) { Text(if (editing >= 0) "Save" else "Add") }
            if (editing >= 0) TextButton(onClick = { clear() }) { Text("Cancel") }
            TextButton(
                enabled = Voice.isEdited(line, config.lineOverrides),
                onClick = {
                    Guardian.updateConfig { c -> c.copy(lineOverrides = Voice.reset(c.lineOverrides, line)) }
                    clear()
                },
            ) { Text("Reset this situation") }
        }
        if (lines.isEmpty()) {
            Muted("No ${mood.label.lowercase()} lines left, so she uses her built-in ones. Add one to replace them.")
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(lines) { index, l ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(l, Modifier.weight(1f).padding(vertical = 8.dp), style = MaterialTheme.typography.bodyLarge)
                        TextButton(onClick = {
                            editing = index
                            text = l
                        }) { Text("Edit") }
                        TextButton(onClick = {
                            if (editing == index) clear() else if (editing > index) editing--
                            change { list -> list.filterIndexed { i, _ -> i != index } }
                        }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

package com.guardianangel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.guardianangel.core.Guardian
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.RuleEnforcement
import com.guardianangel.data.TaskKind
import com.guardianangel.data.TaskTemplate
import com.guardianangel.data.TaskTemplates

/** The editable list she picks rules and tasks from. Tap one to edit it. */
@Composable
fun TasksListScreen(config: GuardianConfig) {
    var editing by remember { mutableIntStateOf(-1) }
    var text by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(TaskKind.RULE) }
    var minutes by remember { mutableIntStateOf(60) }
    var enforce by remember { mutableStateOf(RuleEnforcement.NONE) }
    var explicit by remember { mutableStateOf(false) }

    fun clear() {
        editing = -1
        text = ""
        kind = TaskKind.RULE
        minutes = 60
        enforce = RuleEnforcement.NONE
        explicit = false
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Muted("At about 1 in 3 check-ins she picks one of these. Missing a deadline is a failure.")
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(if (editing >= 0) "Edit" else "New rule or task") },
            modifier = Modifier.fillMaxWidth(),
        )
        ChoiceChips(TaskKind.entries, kind, { it.label }) { kind = it }
        when (kind) {
            TaskKind.RULE -> {
                Stepper("Lasts", formatMinutes(minutes), { minutes = (minutes - 15).coerceAtLeast(15) }, { minutes = (minutes + 15).coerceAtMost(12 * 60) })
                ChoiceChips(RuleEnforcement.entries, enforce, { it.label }) { enforce = it }
            }
            TaskKind.PHOTO -> {
                Stepper("Deadline", formatMinutes(minutes), { minutes = (minutes - 15).coerceAtLeast(15) }, { minutes = (minutes + 15).coerceAtMost(12 * 60) })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = explicit, onCheckedChange = { explicit = it })
                    Text("Explicit (nudity check on the photo)")
                }
            }
            TaskKind.STILLNESS ->
                Stepper("Hold still for", formatMinutes(minutes), { minutes = (minutes - 1).coerceAtLeast(1) }, { minutes = (minutes + 1).coerceAtMost(30) })
            TaskKind.LINES -> Muted("She picks the sentence and difficulty: Easy 5, Medium 15 or Hard 30 lines.")
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = text.isNotBlank(),
                onClick = {
                    val item = TaskTemplate(
                        text = text.trim(),
                        kind = kind,
                        minutes = if (kind == TaskKind.STILLNESS) minutes.coerceAtMost(30) else minutes,
                        enforce = if (kind == TaskKind.RULE) enforce else RuleEnforcement.NONE,
                        explicit = kind == TaskKind.PHOTO && explicit,
                    )
                    val index = editing
                    Guardian.updateConfig { c ->
                        c.copy(taskList = if (index >= 0) c.taskList.mapIndexed { i, t -> if (i == index) item else t } else c.taskList + item)
                    }
                    clear()
                },
            ) { Text(if (editing >= 0) "Save" else "Add") }
            if (editing >= 0) TextButton(onClick = { clear() }) { Text("Cancel") }
            TextButton(onClick = { Guardian.updateConfig { it.copy(taskList = TaskTemplates.DEFAULTS) } }) {
                Text("Reset to defaults")
            }
        }
        if (config.taskList.isEmpty()) Muted("Empty list: she won't issue any.")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(config.taskList) { index, item ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(item.text, style = MaterialTheme.typography.bodyLarge)
                            Muted(describe(item))
                        }
                        TextButton(onClick = {
                            editing = index
                            text = item.text
                            kind = item.kind
                            minutes = item.minutes
                            enforce = item.enforce
                            explicit = item.explicit
                        }) { Text("Edit") }
                        TextButton(onClick = {
                            if (editing == index) clear() else if (editing > index) editing--
                            Guardian.updateConfig { c -> c.copy(taskList = c.taskList.filterIndexed { i, _ -> i != index }) }
                        }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

private fun describe(item: TaskTemplate): String = when (item.kind) {
    TaskKind.RULE -> "Rule, ${formatMinutes(item.minutes)}, ${item.enforce.label.lowercase()}"
    TaskKind.PHOTO -> "Photo task, ${formatMinutes(item.minutes)} to do it" + if (item.explicit) ", explicit" else ""
    TaskKind.STILLNESS -> "Stillness, ${formatMinutes(item.minutes)}"
    TaskKind.LINES -> "Lines"
}

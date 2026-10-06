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
import com.guardianangel.data.Question
import com.guardianangel.data.QuestionKind
import com.guardianangel.data.Questions

/** The editable list of questions she asks when she shows up. */
@Composable
fun QuestionsScreen(config: GuardianConfig) {
    var editing by remember { mutableIntStateOf(-1) }
    var text by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(QuestionKind.CHOICE) }
    var answer by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf("") }

    fun clear() {
        editing = -1
        text = ""
        kind = QuestionKind.CHOICE
        answer = ""
        wrong = ""
    }

    val wrongList = wrong.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val valid = text.isNotBlank() && answer.isNotBlank() && (kind == QuestionKind.PHRASE || wrongList.isNotEmpty())

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Muted("She picks one at random each time she shows up. Keep them non-explicit; they can appear while others are around.")
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text(if (editing >= 0) "Edit question" else "New question") })
        ChoiceChips(QuestionKind.entries, kind, { it.label }) { kind = it }
        OutlinedTextField(
            answer,
            { answer = it },
            Modifier.fillMaxWidth(),
            label = { Text(if (kind == QuestionKind.CHOICE) "Right answer" else "Phrase to type exactly") },
        )
        if (kind == QuestionKind.CHOICE) {
            OutlinedTextField(wrong, { wrong = it }, Modifier.fillMaxWidth(), label = { Text("Wrong answers, one per line") }, minLines = 2)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = valid,
                onClick = {
                    val item = Question(text.trim(), kind, answer.trim(), if (kind == QuestionKind.CHOICE) wrongList else emptyList())
                    val index = editing
                    Guardian.updateConfig { c ->
                        c.copy(questions = if (index >= 0) c.questions.mapIndexed { i, q -> if (i == index) item else q } else c.questions + item)
                    }
                    clear()
                },
            ) { Text(if (editing >= 0) "Save" else "Add") }
            if (editing >= 0) TextButton(onClick = { clear() }) { Text("Cancel") }
            TextButton(onClick = { Guardian.updateConfig { it.copy(questions = Questions.DEFAULTS) } }) { Text("Reset to defaults") }
        }
        if (config.questions.isEmpty()) Muted("Empty list: she'll fall back to \"${Questions.FALLBACK.text}\"")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(config.questions) { index, q ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(q.text, style = MaterialTheme.typography.bodyLarge)
                            Muted(if (q.kind == QuestionKind.CHOICE) "Answer: ${q.answer}" else "Type: ${q.answer}")
                        }
                        TextButton(onClick = {
                            editing = index
                            text = q.text
                            kind = q.kind
                            answer = q.answer
                            wrong = q.wrong.joinToString("\n")
                        }) { Text("Edit") }
                        TextButton(onClick = {
                            if (editing == index) clear() else if (editing > index) editing--
                            Guardian.updateConfig { c -> c.copy(questions = c.questions.filterIndexed { i, _ -> i != index }) }
                        }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

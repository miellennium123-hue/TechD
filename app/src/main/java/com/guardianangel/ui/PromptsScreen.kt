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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.guardianangel.core.Guardian
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.ProofPrompt
import com.guardianangel.data.ProofPrompts

/** The list of things she can demand a photo of. Chastity photos always ask for the cage. */
@Composable
fun PromptsScreen(config: GuardianConfig) {
    var text by remember { mutableStateOf("") }
    var explicit by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Muted(
            "She picks one at random when she wants photo proof outside chastity. " +
                "Tick Explicit and she'll check the photo with a nudity detector that runs on your phone. " +
                "Photos are never uploaded.",
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("New prompt") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = explicit, onCheckedChange = { explicit = it })
            Text("Explicit", Modifier.weight(1f))
            Button(
                enabled = text.isNotBlank(),
                onClick = {
                    val prompt = ProofPrompt(text.trim(), explicit)
                    Guardian.updateConfig { it.copy(proofPrompts = it.proofPrompts + prompt) }
                    text = ""
                    explicit = false
                },
            ) { Text("Add") }
        }
        TextButton(onClick = { Guardian.updateConfig { it.copy(proofPrompts = ProofPrompts.DEFAULTS) } }) {
            Text("Reset to defaults")
        }
        if (config.proofPrompts.isEmpty()) {
            Muted("Empty list: she'll fall back to \"${ProofPrompts.FALLBACK.text}\"")
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(config.proofPrompts) { index, prompt ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(prompt.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Checkbox(
                            checked = prompt.explicit,
                            onCheckedChange = { checked ->
                                Guardian.updateConfig { c ->
                                    c.copy(proofPrompts = c.proofPrompts.mapIndexed { i, p -> if (i == index) p.copy(explicit = checked) else p })
                                }
                            },
                        )
                        TextButton(onClick = {
                            Guardian.updateConfig { c -> c.copy(proofPrompts = c.proofPrompts.filterIndexed { i, _ -> i != index }) }
                        }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
        Muted("Checkbox on each prompt = Explicit.")
    }
}

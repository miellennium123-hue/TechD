package com.guardianangel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.guardianangel.core.Guardian
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.Sites

/** Your own list of sites she can open. Nothing is bundled with the app. */
@Composable
fun SitesScreen(config: GuardianConfig) {
    var text by remember { mutableStateOf("") }
    /** Index being edited, or -1 when adding. */
    var editing by remember { mutableIntStateOf(-1) }
    val cleaned = Sites.normalize(text)

    fun save() {
        val url = cleaned ?: return
        val index = editing
        Guardian.updateConfig { c ->
            c.copy(siteList = if (index in c.siteList.indices) c.siteList.mapIndexed { i, s -> if (i == index) url else s } else c.siteList + url)
        }
        text = ""
        editing = -1
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Muted("She picks one of these at random. Web addresses only; https:// is added if you leave it out.")
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(if (editing >= 0) "Edit site" else "New site") },
            placeholder = { Text("example.com/page") },
            singleLine = true,
            isError = text.isNotBlank() && cleaned == null,
            supportingText = { if (text.isNotBlank() && cleaned == null) Text("That isn't a web address.") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = cleaned != null, onClick = ::save) { Text(if (editing >= 0) "Save" else "Add") }
            if (editing >= 0) {
                TextButton(onClick = {
                    text = ""
                    editing = -1
                }) { Text("Cancel") }
            }
        }
        if (config.siteList.isEmpty()) Muted("No sites yet. She won't open anything until you add one.")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(config.siteList) { index, url ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(url, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        TextButton(onClick = {
                            text = url
                            editing = index
                        }) { Text("Edit") }
                        TextButton(onClick = {
                            Guardian.updateConfig { c -> c.copy(siteList = c.siteList.filterIndexed { i, _ -> i != index }) }
                            if (editing == index) {
                                text = ""
                                editing = -1
                            } else if (editing > index) {
                                editing--
                            }
                        }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

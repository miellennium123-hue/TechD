package com.guardianangel.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.guardianangel.core.AppEntry
import com.guardianangel.core.Guardian
import com.guardianangel.core.InstalledApps
import com.guardianangel.data.AppLists
import com.guardianangel.data.GuardianConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AllowedAppsScreen(config: GuardianConfig) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { InstalledApps.launchable(context) } }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Muted("Never locked, whatever the scope. Add your banking apps here. Phone, Settings and this app are always allowed anyway.")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search apps") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        TextButton(onClick = { Guardian.updateConfig { it.copy(alwaysAllowed = AppLists.DEFAULT_ALLOWED) } }) {
            Text("Reset to defaults")
        }
        val list = apps
        if (list == null) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        } else {
            val filtered = list.filter {
                query.isBlank() || it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
            }
            LazyColumn {
                items(filtered, key = { it.packageName }) { app ->
                    val checked = app.packageName in config.alwaysAllowed
                    val toggle = {
                        Guardian.updateConfig {
                            it.copy(alwaysAllowed = if (checked) it.alwaysAllowed - app.packageName else it.alwaysAllowed + app.packageName)
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable(onClick = toggle).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { toggle() })
                        Column {
                            Text(app.label, style = MaterialTheme.typography.bodyLarge)
                            Muted(app.packageName)
                        }
                    }
                }
            }
        }
    }
}

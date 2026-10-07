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
import com.guardianangel.core.PornBlock
import com.guardianangel.data.AppLists
import com.guardianangel.data.GuardianConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AllowedAppsScreen(config: GuardianConfig) {
    AppPicker(
        note = "Never locked, whatever the scope. Add your banking apps here. Phone, Settings and this app are always allowed anyway.",
        checked = { it.packageName in config.alwaysAllowed },
        onToggle = { app, on ->
            Guardian.updateConfig { it.copy(alwaysAllowed = if (on) it.alwaysAllowed + app.packageName else it.alwaysAllowed - app.packageName) }
        },
        reset = "Reset to defaults" to { Guardian.updateConfig { it.copy(alwaysAllowed = AppLists.DEFAULT_ALLOWED) } },
    )
}

/**
 * Porn block (round 74): which apps she checks. Browsers and social apps are ticked to start with;
 * tick any other app (games too) or untick one of hers. Ticked apps come first.
 */
@Composable
fun PornAppsScreen(config: GuardianConfig) {
    val context = LocalContext.current
    val browsers = remember { InstalledApps.browsers(context) }
    fun byDefault(app: AppEntry) = PornBlock.watchedByDefault(app.packageName, app.category, app.packageName in browsers)
    AppPicker(
        note = "She checks these apps for porn every ${PornBlock.scanSeconds(config.pornBlock)} seconds. Browsers and social apps " +
            "are ticked to start with. Always-allowed apps are never checked, even if ticked.",
        checked = { PornBlock.watches(it.packageName, it.category, it.packageName in browsers, config.pornBlock) },
        onToggle = { app, on ->
            Guardian.updateConfig { it.copy(pornBlock = PornBlock.setWatched(it.pornBlock, app.packageName, byDefault(app), on)) }
        },
        reset = "Reset to her list" to {
            Guardian.updateConfig { it.copy(pornBlock = it.pornBlock.copy(watched = emptySet(), unwatched = emptySet())) }
        },
        subtitle = { app -> if (app.packageName in config.alwaysAllowed) "Always-allowed, so never checked" else null },
    )
}

/** Porn block (round 74): opening one of these is a catch, like porn on screen. */
@Composable
fun AdultAppsScreen(config: GuardianConfig) {
    AppPicker(
        note = "Opening one of these is a catch, like porn on screen: home, the screen locked, and her Caught screen. " +
            "Always-allowed doesn't protect them.",
        checked = { it.packageName in config.pornBlock.adultApps },
        onToggle = { app, on ->
            Guardian.updateConfig {
                val apps = it.pornBlock.adultApps
                it.copy(pornBlock = it.pornBlock.copy(adultApps = if (on) apps + app.packageName else apps - app.packageName))
            }
        },
    )
}

/**
 * A searchable list of installed apps with a tick each. Apps ticked when the list opens come first, so
 * the list doesn't jump as you tick. [subtitle] adds a note under an app.
 */
@Composable
private fun AppPicker(
    note: String,
    checked: (AppEntry) -> Boolean,
    onToggle: (AppEntry, Boolean) -> Unit,
    reset: Pair<String, () -> Unit>? = null,
    subtitle: (AppEntry) -> String? = { null },
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val all = withContext(Dispatchers.IO) { InstalledApps.launchable(context) }
        apps = all.sortedBy { !checked(it) }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Muted(note)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search apps") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        reset?.let { (label, action) -> TextButton(onClick = action) { Text(label) } }
        val list = apps
        if (list == null) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        } else {
            val filtered = list.filter {
                query.isBlank() || it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
            }
            LazyColumn {
                items(filtered, key = { it.packageName }) { app ->
                    val on = checked(app)
                    val toggle = { onToggle(app, !on) }
                    Row(
                        Modifier.fillMaxWidth().clickable(onClick = toggle).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = on, onCheckedChange = { toggle() })
                        Column {
                            Text(app.label, style = MaterialTheme.typography.bodyLarge)
                            Muted(subtitle(app) ?: app.packageName)
                        }
                    }
                }
            }
        }
    }
}

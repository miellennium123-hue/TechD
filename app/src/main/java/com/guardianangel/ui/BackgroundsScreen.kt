package com.guardianangel.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.guardianangel.core.CustomBackgrounds
import com.guardianangel.core.Guardian
import com.guardianangel.core.WallpaperController
import com.guardianangel.data.BuiltInBackground
import com.guardianangel.data.Backgrounds
import com.guardianangel.data.GuardianConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Backgrounds (round 59): her built-in designs to keep or hide, and your own images to add or remove. */
@Composable
fun BackgroundsScreen(config: GuardianConfig) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val w = config.wallpaper
    var files by remember { mutableStateOf(CustomBackgrounds.list(context)) }
    var adding by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<File?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        adding = true
        scope.launch {
            val added = withContext(Dispatchers.IO) { uris.count { CustomBackgrounds.add(context, it) } }
            files = CustomBackgrounds.list(context)
            adding = false
            note = if (added == uris.size) "Added $added." else "Added $added of ${uris.size}. Some couldn't be read."
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Muted(
                    if (w.cycle) {
                        "With Wallpaper control on, she changes to the next one every ${w.cycleMinutes} min, home and lock screen."
                    } else {
                        "With Wallpaper control on, she sets one of these. Cycling is off in Settings > Wallpaper."
                    },
                )
                Button(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !adding,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (adding) "Adding..." else "Add your own") }
                note?.let { Muted(it) }
                OutlinedButton(
                    onClick = { WallpaperController.applyAsync(context, advance = true) },
                    enabled = config.enabled && w.on,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Next background now") }
                Muted("Your images are copied into the app's private storage. They never leave the phone.")
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("Yours (${files.size})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (files.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { Muted("None yet. Tap Add your own.") }
        }
        items(files, key = { it.name }) { file ->
            PhotoThumb(file, 400, Modifier.aspectRatio(9f / 16f).clip(RoundedCornerShape(8.dp)).clickable { removing = file })
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Hers (${Backgrounds.BUILT_IN.size - w.hiddenBuiltIns.size} of ${Backgrounds.BUILT_IN.size})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                SwitchRow("Use her backgrounds", "Off: only yours (and her first design if you have none).", w.builtIns) { v ->
                    Guardian.updateConfig { it.copy(wallpaper = it.wallpaper.copy(builtIns = v)) }
                }
                if (w.builtIns) Muted("Tap one to hide it or bring it back.")
            }
        }
        if (w.builtIns) {
            items(Backgrounds.BUILT_IN, key = { it.id }) { bg ->
                val hidden = bg.id in w.hiddenBuiltIns
                BuiltInPreview(
                    bg,
                    hidden,
                    Modifier.clickable {
                        Guardian.updateConfig {
                            val set = it.wallpaper.hiddenBuiltIns
                            it.copy(wallpaper = it.wallpaper.copy(hiddenBuiltIns = if (bg.id in set) set - bg.id else set + bg.id))
                        }
                    },
                )
            }
        }
    }

    removing?.let { file ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove this background?") },
            text = { PhotoThumb(file, 800, Modifier.fillMaxWidth().aspectRatio(9f / 16f), ContentScale.Fit) },
            confirmButton = {
                TextButton(onClick = {
                    file.delete()
                    files = CustomBackgrounds.list(context)
                    removing = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Keep") } },
        )
    }
}

/** A small render of one of her designs, dimmed when hidden. */
@Composable
private fun BuiltInPreview(bg: BuiltInBackground, hidden: Boolean, modifier: Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, bg.id) {
        value = withContext(Dispatchers.Default) { WallpaperController.draw(bg, 270, 480).asImageBitmap() }
    }
    val shape = RoundedCornerShape(8.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier
                .aspectRatio(9f / 16f)
                .clip(shape)
                .then(if (hidden) Modifier else Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape))
                .alpha(if (hidden) 0.35f else 1f),
        ) {
            bitmap?.let { Image(it, contentDescription = bg.headline, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Text(if (hidden) "Hidden" else "On", style = MaterialTheme.typography.labelSmall)
    }
}

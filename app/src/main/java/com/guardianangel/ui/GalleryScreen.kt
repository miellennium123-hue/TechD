package com.guardianangel.ui

import android.app.Activity
import android.view.WindowManager
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.guardianangel.core.Guardian
import com.guardianangel.core.ProofFiles
import androidx.compose.runtime.collectAsState
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Proof photos, private to this app. Screenshots are blocked while this screen is open. */
@Composable
fun GalleryScreen() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    var files by remember { mutableStateOf(ProofFiles.list(context)) }
    var viewing by remember { mutableStateOf<File?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var confirmSaveAll by remember { mutableStateOf(false) }
    val saveToPhone = rememberSaveToPhone()
    val state by Guardian.state.flow.collectAsState()
    val peeks = remember(state.peeks) { state.peeks.associateBy { it.file } }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Muted(
            "Photos and her peeks at your screen, stored only inside this app. Not in your gallery, not backed up, " +
                "unless you tap Save to phone. Session videos are in Guided sessions > Her videos.",
        )
        if (files.isEmpty()) {
            Text("No photos yet.")
        } else {
            OutlinedButton(onClick = { confirmSaveAll = true }) { Text("Save all to phone") }
            OutlinedButton(onClick = { confirmDeleteAll = true }) { Text("Delete all photos") }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(files, key = { it.name }) { file ->
                    Box(Modifier.aspectRatio(1f).clickable { viewing = file }) {
                        PhotoThumb(file, 300, Modifier.fillMaxSize())
                        if (file.name in peeks) {
                            Text(
                                "Peek",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                                    .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    viewing?.let { file ->
        Dialog(onDismissRequest = { viewing = null }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Her comment first, so a tall screenshot can't push it off the screen (round 64).
                peeks[file.name]?.let { peek ->
                    val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(peek.at))
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("She peeked at ${peek.app}, $time", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text("\"${peek.line}\"", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                if (ProofFiles.isVideo(file)) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setMediaController(MediaController(ctx).also { it.setAnchorView(this) })
                                setVideoPath(file.path)
                                setOnPreparedListener { player -> player.isLooping = true }
                                start()
                            }
                        },
                        onRelease = { it.stopPlayback() },
                        modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
                    )
                } else {
                    // Takes only the room left, so her comment and Delete always stay on screen.
                    PhotoThumb(file, 1600, Modifier.fillMaxWidth().weight(1f, fill = false), ContentScale.Fit)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { saveToPhone(listOf(file)) }) { Text("Save to phone") }
                    TextButton(onClick = {
                        file.delete()
                        Guardian.forgetPeeks(setOf(file.name))
                        files = ProofFiles.list(context)
                        viewing = null
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }

    if (confirmSaveAll) {
        SaveAllDialog(files.size, "photos", onSave = { saveToPhone(files) }, onDismiss = { confirmSaveAll = false })
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete all photos?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    ProofFiles.deleteAll(context)
                    Guardian.forgetPeeks(null)
                    files = emptyList()
                    confirmDeleteAll = false
                }) { Text("Delete all") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun PhotoThumb(file: File, maxDim: Int, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Crop) {
    val bitmap by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { ProofFiles.thumbnail(file, maxDim)?.asImageBitmap() }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it, contentDescription = "Proof photo", modifier = Modifier.fillMaxSize(), contentScale = scale) }
        if (ProofFiles.isVideo(file)) Text("▶", style = MaterialTheme.typography.headlineMedium)
    }
}

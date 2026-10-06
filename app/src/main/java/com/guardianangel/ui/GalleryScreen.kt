package com.guardianangel.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.window.Dialog
import com.guardianangel.core.ProofFiles
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

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Muted("Stored only inside this app. Not in your gallery, not backed up.")
        if (files.isEmpty()) {
            Text("No photos yet.")
        } else {
            OutlinedButton(onClick = { confirmDeleteAll = true }) { Text("Delete all photos") }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(files, key = { it.name }) { file ->
                    PhotoThumb(file, 300, Modifier.aspectRatio(1f).clickable { viewing = file })
                }
            }
        }
    }

    viewing?.let { file ->
        Dialog(onDismissRequest = { viewing = null }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PhotoThumb(file, 1600, Modifier.fillMaxWidth(), ContentScale.Fit)
                TextButton(onClick = {
                    file.delete()
                    files = ProofFiles.list(context)
                    viewing = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete all photos?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    ProofFiles.deleteAll(context)
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
        value = withContext(Dispatchers.IO) { ProofFiles.load(file, maxDim)?.asImageBitmap() }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it, contentDescription = "Proof photo", modifier = Modifier.fillMaxSize(), contentScale = scale) }
    }
}

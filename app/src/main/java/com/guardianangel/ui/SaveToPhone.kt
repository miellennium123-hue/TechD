package com.guardianangel.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.guardianangel.core.MediaSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Save to phone (round 81): returns a function that copies files into your phone's gallery, asking
 * for the storage permission first on Android 9 and older. Says how it went in a short message.
 */
@Composable
fun rememberSaveToPhone(): (List<File>) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var waiting by remember { mutableStateOf<List<File>>(emptyList()) }

    fun save(files: List<File>) {
        scope.launch {
            val saved = withContext(Dispatchers.IO) { MediaSaver.saveAll(context.applicationContext, files) }
            val message = when {
                saved == 0 -> "Couldn't save to your phone."
                saved < files.size -> "Saved $saved of ${files.size} to ${MediaSaver.where(files)}."
                saved == 1 -> "Saved to ${MediaSaver.where(files)}."
                else -> "Saved $saved to ${MediaSaver.where(files)}."
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            save(waiting)
        } else {
            Toast.makeText(context, "Saving to your gallery needs the storage permission.", Toast.LENGTH_SHORT).show()
        }
        waiting = emptyList()
    }

    return { files ->
        val allowed = !MediaSaver.needsPermission() ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        when {
            files.isEmpty() -> Unit
            allowed -> save(files)
            else -> {
                waiting = files
                permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }
}

/** Asks before copying everything out of her private storage. */
@Composable
fun SaveAllDialog(count: Int, what: String, onSave: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save all $count $what to your phone?") },
        text = {
            Text(
                "Copies go to your phone's gallery. Other apps and backups (like Google Photos) can see them, " +
                    "and deleting them here doesn't delete the copies.",
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onSave()
                onDismiss()
            }) { Text("Save all") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

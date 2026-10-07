package com.guardianangel.ui

import android.app.Activity
import android.view.WindowManager
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.guardianangel.core.ClipInfo
import com.guardianangel.core.Clips
import com.guardianangel.core.Guardian
import com.guardianangel.core.SessionClips
import java.text.DateFormat
import java.util.Date

/**
 * Her videos (round 77): every ruin, edge and CBT clip she filmed during sessions, newest first,
 * apart from your proof photos. Private to this app; screenshots are blocked while it's open.
 */
@Composable
fun ClipsScreen() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    var clips by remember { mutableStateOf(SessionClips.infos(context)) }
    var viewing by remember { mutableStateOf<ClipInfo?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var confirmSaveAll by remember { mutableStateOf(false) }
    val saveToPhone = rememberSaveToPhone()
    val state by Guardian.state.flow.collectAsState()
    val captions = state.clipCaptions
    // Round 79: her ruin reel, playing one ruin after another.
    var reel by remember { mutableStateOf<List<ClipInfo>?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Muted(
            "Ruins, edges and CBT she filmed during your sessions, with sound, and your face at the edge. Stored only inside this app: not in your " +
                "gallery, not backed up, unless you tap Save to phone. She keeps the newest ${Clips.KEEP}, and sometimes makes you watch one.",
        )
        if (clips.isEmpty()) {
            Text("No videos yet. She films your next ruin, and your edges and CBT if you let her.")
        } else {
            val ruins = Clips.reel(clips)
            if (ruins.isNotEmpty()) {
                Button(onClick = { reel = ruins }) { Text("Play her ruin reel (${ruins.size})") }
            }
            OutlinedButton(onClick = { confirmSaveAll = true }) { Text("Save all to phone") }
            OutlinedButton(onClick = { confirmDeleteAll = true }) { Text("Delete all videos") }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(clips, key = { it.name }) { clip ->
                    Box(Modifier.aspectRatio(1f).clickable { viewing = clip }) {
                        PhotoThumb(SessionClips.file(context, clip.name), 300, Modifier.fillMaxSize())
                        Text(
                            captions[clip.name] ?: clip.kind.label,
                            maxLines = 2,
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

    viewing?.let { clip ->
        Dialog(onDismissRequest = { viewing = null }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(clip.at))
                Text("${clip.kind.label}, $time", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                captions[clip.name]?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface) }
                if (clip.kind.video) {
                    ClipPlayer(
                        SessionClips.file(context, clip.name),
                        Modifier.fillMaxWidth().aspectRatio(3f / 4f),
                        controls = true,
                        loop = true,
                    )
                } else {
                    PhotoThumb(SessionClips.file(context, clip.name), 1600, Modifier.fillMaxWidth().aspectRatio(3f / 4f), ContentScale.Fit)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { saveToPhone(listOf(SessionClips.file(context, clip.name))) }) { Text("Save to phone") }
                    TextButton(onClick = {
                        SessionClips.file(context, clip.name).delete()
                        clips = SessionClips.infos(context)
                        viewing = null
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }

    reel?.let { list -> RuinReel(list, captions) { reel = null } }

    if (confirmSaveAll) {
        SaveAllDialog(clips.size, "videos", onSave = { saveToPhone(SessionClips.list(context)) }, onDismiss = { confirmSaveAll = false })
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete all videos?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    SessionClips.deleteAll(context)
                    clips = emptyList()
                    confirmDeleteAll = false
                }) { Text("Delete all") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") } },
        )
    }
}

/** Round 79: every ruin clip back to back, with her caption on each, until the last one ends. */
@Composable
private fun RuinReel(clips: List<ClipInfo>, captions: Map<String, String>, onDone: () -> Unit) {
    val context = LocalContext.current
    var at by remember { mutableIntStateOf(0) }
    Dialog(onDismissRequest = onDone) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val clip = clips[at]
            Text(
                "Ruin ${at + 1} of ${clips.size}" + (captions[clip.name]?.let { ": $it" } ?: ""),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            key(clip.name) {
                ClipPlayer(SessionClips.file(context, clip.name), Modifier.fillMaxWidth().aspectRatio(3f / 4f), onEnd = {
                    if (at + 1 < clips.size) at++ else onDone()
                })
            }
            TextButton(onClick = onDone) { Text("Close") }
        }
    }
}

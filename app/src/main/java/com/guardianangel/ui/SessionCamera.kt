package com.guardianangel.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.guardianangel.core.ClipKind
import com.guardianangel.core.Permissions
import com.guardianangel.core.SessionClips
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Film one step: [id] changes for every new clip, [maxSeconds] is the longest it runs. */
data class ClipRequest(val id: Int, val kind: ClipKind, val maxSeconds: Int)

/**
 * The camera during a session (round 77: on for every session, so you see yourself). Front by
 * default, [back] for the back one. It stays on the whole session; while [record] is set it films that
 * step into Her videos, with sound if the microphone is allowed, and reports the file (null if it
 * failed). Nothing else is ever saved. No more checks of what it sees.
 */
@Composable
fun SessionCamera(
    record: ClipRequest?,
    onClip: (ClipKind, File?) -> Unit,
    modifier: Modifier = Modifier,
    back: Boolean = false,
    snapshot: Int = 0,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val clip by rememberUpdatedState(onClip)
    val previewView = remember { PreviewView(context) }
    var capture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }

    DisposableEffect(lifecycleOwner, back) {
        val main = ContextCompat.getMainExecutor(context)
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        future.addListener({
            if (disposed) return@addListener
            runCatching {
                val p = future.get()
                provider = p
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                // The camera you picked; the other one only if this phone doesn't have it.
                val wanted = if (back) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                val other = if (back) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                val selector = if (runCatching { p.hasCamera(wanted) }.getOrDefault(false)) wanted else other
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.SD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
                    .setTargetVideoEncodingBitRate(VIDEO_BITRATE)
                    .build()
                val videoCapture = VideoCapture.withOutput(recorder)
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, selector, preview, videoCapture)
                capture = videoCapture
            }
        }, main)
        onDispose {
            disposed = true
            capture = null
            provider?.unbindAll()
        }
    }

    DisposableEffect(record?.id, capture) {
        val request = record
        val output = capture
        if (request == null || output == null) return@DisposableEffect onDispose { }
        val handler = Handler(Looper.getMainLooper())
        val file = SessionClips.newFile(context, request.kind)
        val recording = runCatching { start(context, output, file, request.kind) { kind, ok -> clip(kind, ok) } }.getOrNull()
        if (recording == null) clip(request.kind, null)
        handler.postDelayed({ recording?.stop() }, request.maxSeconds * 1_000L)
        onDispose {
            handler.removeCallbacksAndMessages(null)
            recording?.stop()
        }
    }

    // Round 79: her edge face. Each new [snapshot] number grabs what you see right now as a photo.
    LaunchedEffect(snapshot) {
        if (snapshot <= 0) return@LaunchedEffect
        val frame = previewView.bitmap ?: return@LaunchedEffect
        val file = withContext(Dispatchers.IO) {
            runCatching {
                val out = SessionClips.newFile(context, ClipKind.FACE)
                out.outputStream().use { frame.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                SessionClips.prune(context)
                out
            }.getOrNull()
        }
        clip(ClipKind.FACE, file)
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

@SuppressLint("MissingPermission")
private fun start(
    context: android.content.Context,
    capture: VideoCapture<Recorder>,
    file: File,
    kind: ClipKind,
    done: (ClipKind, File?) -> Unit,
): Recording {
    val pending = capture.output.prepareRecording(context, FileOutputOptions.Builder(file).build())
    if (Permissions.microphone(context)) pending.withAudioEnabled()
    return pending.start(ContextCompat.getMainExecutor(context)) { event ->
        if (event is VideoRecordEvent.Finalize) {
            val ok = file.exists() && file.length() > 0
            if (ok) SessionClips.prune(context) else file.delete()
            done(kind, if (ok) file else null)
        }
    }
}

/** About 1.5 Mbit/s: a 3 minute edge is roughly 35 MB. */
private const val VIDEO_BITRATE = 1_500_000

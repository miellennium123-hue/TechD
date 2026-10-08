package com.guardianangel.ui

import android.annotation.SuppressLint
import androidx.camera.core.Camera
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
import androidx.compose.runtime.SideEffect
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
import com.guardianangel.core.ClipRequest
import com.guardianangel.core.ClipTrim
import com.guardianangel.core.Clips
import com.guardianangel.core.Permissions
import com.guardianangel.core.SessionClips
import java.io.File

/** A recording in progress: its latest request (for the mark and caption) and when it was stopped. */
private class Take(request: ClipRequest) {
    @Volatile var request: ClipRequest = request
    @Volatile var stoppedAt: Long = 0
}

/**
 * The camera during a session (round 77: on for every session, so you see yourself). Front by
 * default, [back] for the back one. It stays on the whole session; while [record] is set it films into
 * Her videos, with sound if the microphone is allowed, and reports the file (null if it failed). A new
 * [ClipRequest.id] starts a new clip; null stops. Round 95: no more edge face photos, and a clip with a
 * mark is cut to start 10 seconds before it (Clips.nextTake decides the takes). Nothing else is ever saved. No checks of what it sees.
 */
@Composable
fun SessionCamera(
    record: ClipRequest?,
    onClip: (ClipRequest, File?) -> Unit,
    modifier: Modifier = Modifier,
    back: Boolean = false,
    torch: Boolean = false,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val clip by rememberUpdatedState(onClip)
    val previewView = remember { PreviewView(context) }
    var capture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val live = remember { arrayOfNulls<Take>(1) }

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
                camera = p.bindToLifecycle(lifecycleOwner, selector, preview, videoCapture)
                capture = videoCapture
            }
        }, main)
        onDispose {
            disposed = true
            capture = null
            camera = null
            provider?.unbindAll()
        }
    }

    // Keeps the take's mark and caption up to date while it films.
    SideEffect {
        val take = live[0]
        if (take != null && record != null && record.id == take.request.id) take.request = record
    }

    DisposableEffect(record?.id, capture) {
        val request = record
        val output = capture
        if (request == null || output == null) return@DisposableEffect onDispose { }
        val take = Take(request)
        live[0] = take
        val file = SessionClips.newFile(context, request.kind)
        val recording = runCatching { start(context, output, file, take) { req, f -> clip(req, f) } }.getOrNull()
        if (recording == null) clip(request, null)
        onDispose {
            take.stoppedAt = System.currentTimeMillis()
            if (live[0] === take) live[0] = null
            recording?.stop()
        }
    }

    // Round 84: the torch, for the back camera in low light.
    LaunchedEffect(torch, camera) {
        val c = camera ?: return@LaunchedEffect
        if (c.cameraInfo.hasFlashUnit()) runCatching { c.cameraControl.enableTorch(torch) }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

@SuppressLint("MissingPermission")
private fun start(
    context: android.content.Context,
    capture: VideoCapture<Recorder>,
    file: File,
    take: Take,
    done: (ClipRequest, File?) -> Unit,
): Recording {
    val main = ContextCompat.getMainExecutor(context)
    val pending = capture.output.prepareRecording(context, FileOutputOptions.Builder(file).build())
    if (Permissions.microphone(context)) pending.withAudioEnabled()
    return pending.start(main) { event ->
        if (event is VideoRecordEvent.Finalize) {
            val request = take.request
            if (!file.exists() || file.length() == 0L) {
                file.delete()
                done(request, null)
            } else {
                // Round 95: cut it to start 10 seconds before your edge tap, off the main thread.
                Thread {
                    val stoppedAt = take.stoppedAt.takeIf { it > 0 } ?: System.currentTimeMillis()
                    val cut = Clips.trimStartMs(ClipTrim.durationMs(file), stoppedAt, request.markAt)
                    if (cut > 0) ClipTrim.cutStart(file, cut)
                    SessionClips.prune(context)
                    main.execute { done(request, file.takeIf { it.exists() }) }
                }.start()
            }
        }
    }
}

/** About 1.5 Mbit/s: a 3 minute edge is roughly 35 MB. */
private const val VIDEO_BITRATE = 1_500_000

package com.guardianangel.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.guardianangel.core.ExplicitDetector
import com.guardianangel.core.ExplicitScore
import com.guardianangel.core.ProofFiles
import java.io.File
import java.util.concurrent.Executors

enum class CameraMode { WATCH, RECORD }

/**
 * The front camera during a session. WATCH runs the on-device detector about every 1.5 seconds and
 * reports whether she can see you (null when the detector can't run). RECORD films [recordSeconds]
 * of silent video into the private proof folder and reports the file (null if it failed).
 * Frames are never saved, except the clip.
 */
@Composable
fun SessionCamera(
    mode: CameraMode,
    recordSeconds: Int,
    onSeen: (Boolean?) -> Unit,
    onClip: (File?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val seen by rememberUpdatedState(onSeen)
    val clip by rememberUpdatedState(onClip)
    val previewView = remember { PreviewView(context) }

    DisposableEffect(mode, lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val main = ContextCompat.getMainExecutor(context)
        val handler = Handler(Looper.getMainLooper())
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var recording: Recording? = null
        var disposed = false
        future.addListener({
            if (disposed) return@addListener
            runCatching {
                val p = future.get()
                provider = p
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val selector = if (p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                p.unbindAll()
                when (mode) {
                    CameraMode.WATCH -> {
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                            .build()
                        var last = 0L
                        analysis.setAnalyzer(executor) { image ->
                            val now = System.currentTimeMillis()
                            if (now - last >= 1_500) {
                                last = now
                                val result = runCatching {
                                    val bitmap = upright(image.toBitmap(), image.imageInfo.rotationDegrees)
                                    ExplicitDetector.output(context, bitmap)?.let { out ->
                                        (ExplicitScore.best(out, ExplicitScore.MALE_GENITALIA)?.score ?: 0f) >= ExplicitScore.THRESHOLD
                                    }
                                }.getOrNull()
                                main.execute { if (!disposed) seen(result) }
                            }
                            image.close()
                        }
                        p.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                    }
                    CameraMode.RECORD -> {
                        val recorder = Recorder.Builder()
                            .setQualitySelector(QualitySelector.from(Quality.SD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
                            .build()
                        val capture = VideoCapture.withOutput(recorder)
                        p.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                        val dir = File(context.filesDir, ProofFiles.DIR).apply { mkdirs() }
                        val file = File(dir, "proof_${System.currentTimeMillis()}_ruin.mp4")
                        recording = capture.output
                            .prepareRecording(context, FileOutputOptions.Builder(file).build())
                            .start(main) { event ->
                                if (event is VideoRecordEvent.Finalize) {
                                    val ok = file.exists() && file.length() > 0
                                    if (!ok) file.delete()
                                    clip(if (ok) file else null)
                                }
                            }
                        handler.postDelayed({ recording?.stop() }, recordSeconds * 1_000L)
                    }
                }
            }.onFailure { if (mode == CameraMode.RECORD) clip(null) else seen(null) }
        }, main)
        onDispose {
            disposed = true
            handler.removeCallbacksAndMessages(null)
            recording?.stop()
            provider?.unbindAll()
            executor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

private fun upright(bitmap: Bitmap, degrees: Int): Bitmap =
    if (degrees == 0) {
        bitmap
    } else {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
    }

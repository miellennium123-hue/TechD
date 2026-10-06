package com.guardianangel.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.guardianangel.core.Guardian
import com.guardianangel.core.Permissions
import com.guardianangel.ui.theme.GuardianTheme
import java.io.File

/** In-app camera only (no gallery uploads), so proof can't be faked with an old photo. */
class ProofActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        val id = intent.getLongExtra(EXTRA_ID, -1)
        setContent { GuardianTheme { ProofScreen(id) { finish() } } }
    }

    companion object {
        private const val EXTRA_ID = "proof_id"

        fun intent(context: Context, id: Long): Intent {
            val intent = Intent(context, ProofActivity::class.java).putExtra(EXTRA_ID, id)
            if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return intent
        }
    }
}

@Composable
private fun ProofScreen(id: Long, onDone: () -> Unit) {
    val context = LocalContext.current
    val state by Guardian.state.flow.collectAsState()
    val request = state.proofs.firstOrNull { it.id == id }
    var hasCamera by remember { mutableStateOf(Permissions.camera(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }
    var captured by remember { mutableStateOf<File?>(null) }
    var reply by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { if (!hasCamera) permission.launch(Manifest.permission.CAMERA) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeDrawingPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val shown = reply
            val photo = captured
            when {
                shown != null -> {
                    AngelImage(Modifier.weight(1f))
                    SpeechBubble(shown)
                    Button(onClick = onDone) { Text("Done") }
                }
                request == null -> {
                    Text("This request is no longer open.")
                    Button(onClick = onDone) { Text("Close") }
                }
                !hasCamera -> {
                    Text("Photo proof needs the camera.")
                    Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
                }
                photo == null -> {
                    Text(request.reason.prompt, style = MaterialTheme.typography.titleMedium)
                    CameraCapture { captured = it }
                }
                else -> {
                    Text("Send this to her?", style = MaterialTheme.typography.titleMedium)
                    PhotoThumb(photo, 1600, Modifier.weight(1f).fillMaxWidth(), ContentScale.Fit)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = {
                            photo.delete()
                            captured = null
                        }) { Text("Retake") }
                        Button(onClick = { reply = Guardian.completeProof(id, photo) }) { Text("Send to her") }
                    }
                }
            }
            if (shown == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        captured?.delete()
                        onDone()
                    }) { Text("Not now") }
                    QuitButton {
                        captured?.delete()
                        Guardian.quitForNow()
                        onDone()
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.CameraCapture(onCaptured: (File) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var lens by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }

    DisposableEffect(lens, lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            runCatching {
                val p = future.get()
                provider = p
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                val selector = CameraSelector.Builder().requireLensFacing(lens).build()
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
                error = null
            }.onFailure { error = "Couldn't open that camera." }
        }, ContextCompat.getMainExecutor(context))
        onDispose { provider?.unbindAll() }
    }

    Box(Modifier.weight(1f).fillMaxWidth()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = {
            lens = if (lens == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        }) { Text("Flip") }
        Button(
            enabled = !busy,
            onClick = {
                busy = true
                val file = File(context.cacheDir, "pending_proof_${System.currentTimeMillis()}.jpg")
                imageCapture.takePicture(
                    ImageCapture.OutputFileOptions.Builder(file).build(),
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            busy = false
                            onCaptured(file)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            busy = false
                            error = "Couldn't take the photo. Try again."
                        }
                    },
                )
            },
        ) { Text("Take photo") }
    }
}

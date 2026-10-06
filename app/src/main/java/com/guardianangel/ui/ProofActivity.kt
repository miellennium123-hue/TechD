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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.style.TextAlign
import com.guardianangel.core.Line
import com.guardianangel.core.PhotoVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val scope = rememberCoroutineScope()
    val state by Guardian.state.flow.collectAsState()
    val request = state.proofs.firstOrNull { it.id == id }
    var hasCamera by remember { mutableStateOf(Permissions.camera(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }
    var captured by remember { mutableStateOf<File?>(null) }
    var reply by remember { mutableStateOf<String?>(null) }
    var rejection by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { if (!hasCamera) permission.launch(Manifest.permission.CAMERA) }

    fun send(photo: File, verified: Boolean) {
        reply = Guardian.completeProof(id, photo, verified)
        captured = null
    }

    fun check(photo: File, explicit: Boolean) {
        checking = true
        scope.launch {
            val issue = withContext(Dispatchers.Default) { PhotoVerifier.verify(context, photo, explicit) }
            checking = false
            if (issue == null) {
                send(photo, verified = true)
            } else {
                Guardian.recordFailedCheck(id)
                rejection = "${Guardian.say(Line.PROOF_REJECTED)}\n${issue.message}"
                photo.delete()
                captured = null
            }
        }
    }

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
                    ProofHeader(request.subject, request.explicit)
                    rejection?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    CameraCapture {
                        rejection = null
                        captured = it
                    }
                }
                else -> {
                    ProofHeader(request.subject, request.explicit)
                    PhotoThumb(photo, 1600, Modifier.weight(1f).fillMaxWidth(), ContentScale.Fit)
                    if (checking) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                            Text("She's checking it...")
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = {
                                photo.delete()
                                captured = null
                            }) { Text("Retake") }
                            Button(onClick = { check(photo, request.explicit) }) { Text("Send to her") }
                        }
                        if (request.failedChecks >= MAX_FAILED_CHECKS) {
                            TextButton(onClick = { send(photo, verified = false) }) {
                                Text("Send anyway (no merit, she'll be suspicious)")
                            }
                        }
                    }
                }
            }
            if (shown == null && !checking) {
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

/** After this many rejected photos, a false detection can't trap you: "Send anyway" appears. */
private const val MAX_FAILED_CHECKS = 3

@Composable
private fun ProofHeader(subject: String, explicit: Boolean) {
    Text("Photograph: $subject", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    if (explicit) Muted("She'll check this one is explicit. The check runs on your phone; nothing is uploaded.")
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

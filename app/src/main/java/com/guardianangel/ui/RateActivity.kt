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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.guardianangel.core.Guardian
import com.guardianangel.core.Line
import com.guardianangel.core.Permissions
import com.guardianangel.core.PhotoSignals
import com.guardianangel.core.PhotoVerifier
import com.guardianangel.core.Rating
import com.guardianangel.data.RatingRecord
import com.guardianangel.ui.theme.GuardianTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** Rate me: your measurements, then a photo through the in-app camera, then her verdict. */
class RateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        // A photo left behind if the app was closed mid-rating: delete it.
        val hourAgo = System.currentTimeMillis() - 60 * 60 * 1000L
        cacheDir.listFiles { f -> f.name.startsWith("pending_proof_") && f.lastModified() < hourAgo }?.forEach { it.delete() }
        setContent { GuardianTheme { RateScreen { finish() } } }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, RateActivity::class.java)
    }
}

private enum class RateStep { MEASURE, PHOTO, RESULT }

@Composable
private fun RateScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config by Guardian.config.flow.collectAsState()
    val state by Guardian.state.flow.collectAsState()
    val inches = config.rating.inches
    val last = remember { state.ratings.lastOrNull() }

    var step by remember { mutableStateOf(RateStep.MEASURE) }
    var lengthText by remember { mutableStateOf(last?.let { format(Rating.fromCm(it.lengthCm, inches)) }.orEmpty()) }
    var girthText by remember { mutableStateOf(last?.let { format(Rating.fromCm(it.girthCm, inches)) }.orEmpty()) }
    var hasCamera by remember { mutableStateOf(Permissions.camera(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }
    var captured by remember { mutableStateOf<File?>(null) }
    var checking by remember { mutableStateOf(false) }
    var rejection by remember { mutableStateOf<String?>(null) }
    var failedPhotos by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<Pair<RatingRecord, String>?>(null) }

    val lengthCm = Rating.parse(lengthText)?.let { Rating.toCm(it, inches) }
    val girthCm = Rating.parse(girthText)?.let { Rating.toCm(it, inches) }

    fun discard() {
        captured?.delete()
        captured = null
    }

    fun rateNow(signals: PhotoSignals?) {
        discard()
        result = Guardian.rate(lengthCm ?: return, girthCm ?: return, signals)
        step = RateStep.RESULT
    }

    fun check(photo: File) {
        checking = true
        scope.launch {
            val (issue, signals) = withContext(Dispatchers.Default) { PhotoVerifier.analyze(context, photo) }
            checking = false
            if (issue == null) {
                rateNow(signals)
            } else {
                failedPhotos++
                rejection = "${Guardian.say(Line.PROOF_REJECTED)}\n${issue.message}"
                discard()
            }
        }
    }

    LaunchedEffect(step) { if (step == RateStep.PHOTO && !hasCamera) permission.launch(Manifest.permission.CAMERA) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeDrawingPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val photo = captured
            val rated = result
            when {
                !config.rating.on || !config.enabled -> {
                    Text("Rate me is off. Turn it on in Settings, with her switched on.", Modifier.weight(1f))
                    Button(onClick = onDone) { Text("Close") }
                }
                step == RateStep.RESULT && rated != null -> Verdict(rated.first, rated.second, inches, config.rating.taste.label, Modifier.weight(1f), onDone)
                step == RateStep.MEASURE -> Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Rate me", style = MaterialTheme.typography.headlineSmall)
                    Muted(
                        "First your measurements, then a photo. She scores your size against published measurements " +
                            "of real men, and the photo on how well you show her. The photo is checked on your phone " +
                            "and deleted straight after. Only the numbers are kept.",
                    )
                    ChoiceChips(listOf(false, true), inches, { if (it) "Inches" else "cm" }) { toInches ->
                        if (toInches != inches) {
                            lengthCm?.let { lengthText = format(Rating.fromCm(it, toInches)) }
                            girthCm?.let { girthText = format(Rating.fromCm(it, toInches)) }
                            Guardian.updateConfig { c -> c.copy(rating = c.rating.copy(inches = toInches)) }
                        }
                    }
                    val unit = if (inches) "in" else "cm"
                    OutlinedTextField(
                        lengthText,
                        { lengthText = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("Erect length ($unit)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                    Muted("Ruler on top, pressed gently against the body at the base, measured to the tip.")
                    OutlinedTextField(
                        girthText,
                        { girthText = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("Erect girth ($unit)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                    Muted("Around the middle of the shaft, with a soft tape or a piece of string.")
                    val ok = Rating.isValid(lengthCm) && Rating.isValid(girthCm)
                    if (!ok && (lengthText.isNotBlank() || girthText.isNotBlank())) {
                        Muted("Enter both, between ${format(Rating.fromCm(Rating.MIN_CM, inches))} and ${format(Rating.fromCm(Rating.MAX_CM, inches))} $unit.")
                    }
                    Button(enabled = ok, onClick = { step = RateStep.PHOTO }, modifier = Modifier.fillMaxWidth()) { Text("Next: show her") }
                }
                !hasCamera -> {
                    Text("She needs the camera to see you.", Modifier.weight(1f))
                    Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
                }
                photo == null -> {
                    Text("Show her. Fill the frame, centred, in good light.", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    rejection?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    CameraCapture {
                        rejection = null
                        captured = it
                    }
                    if (failedPhotos >= Rating.MAX_FAILED_PHOTOS) {
                        TextButton(onClick = { rateNow(null) }) { Text("Rate me on my numbers (presentation 0)") }
                    }
                    TextButton(onClick = { step = RateStep.MEASURE }) { Text("Back to measurements") }
                }
                else -> {
                    PhotoThumb(photo, 1600, Modifier.weight(1f).fillMaxWidth(), ContentScale.Fit)
                    if (checking) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                            Text("She's looking...")
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { discard() }) { Text("Retake") }
                            Button(onClick = { check(photo) }) { Text("Show her") }
                        }
                    }
                }
            }
            if (!checking) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (step != RateStep.RESULT) {
                        TextButton(onClick = {
                            discard()
                            onDone()
                        }) { Text("Not now") }
                    }
                    QuitButton {
                        discard()
                        Guardian.quitForNow()
                        onDone()
                    }
                }
            }
        }
    }
}

@Composable
private fun Verdict(record: RatingRecord, line: String, inches: Boolean, taste: String, modifier: Modifier, onDone: () -> Unit) {
    val unit = if (inches) "in" else "cm"
    Column(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AngelImage(Modifier.size(160.dp))
        SpeechBubble(line)
        Text("${record.score}/10", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
        SectionCard("Her scorecard", Modifier.fillMaxWidth()) {
            Text("Length: ${format(Rating.fromCm(record.lengthCm, inches))} $unit, ${Rating.ordinal(record.lengthPercentile)} percentile")
            Text("Girth: ${format(Rating.fromCm(record.girthCm, inches))} $unit, ${Rating.ordinal(record.girthPercentile)} percentile")
            Text(if (record.presentation >= 0) "Presentation: ${record.presentation}/100" else "Presentation: she couldn't see it (0)")
            Muted(
                "$taste. Percentiles compare you with men measured in Veale et al. 2015 (BJU International). " +
                    "Size is 80% of her score, presentation 20%. The photo is already deleted.",
            )
        }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}

private fun format(value: Double): String = String.format(Locale.US, "%.1f", value)


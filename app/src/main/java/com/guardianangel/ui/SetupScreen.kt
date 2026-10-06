package com.guardianangel.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.guardianangel.core.Permissions

@Composable
fun SetupScreen() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    val accessibility = remember(refresh) { Permissions.accessibility(context) }
    val notifications = remember(refresh) { Permissions.notifications(context) }
    val exactAlarms = remember(refresh) { Permissions.exactAlarms(context) }
    val camera = remember(refresh) { Permissions.camera(context) }

    fun open(intent: Intent) = runCatching { context.startActivity(intent) }
    val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Muted("You grant each of these knowingly, and you can revoke any of them in Android Settings.")

        PermissionCard(
            "Accessibility",
            "Lets her see which app is open so she can lock apps. She only reads the app's name, never what's on screen.",
            accessibility,
        ) { open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        if (!accessibility && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            SectionCard("Switch greyed out?") {
                Muted(
                    "Android blocks this for sideloaded apps until you allow it: open App info, tap the three-dot menu, " +
                        "then \"Allow restricted settings\". Then come back and turn Accessibility on.",
                )
                OutlinedButton(onClick = { open(appDetails) }) { Text("Open App info") }
            }
        }

        PermissionCard("Notifications", "For check-ins and her messages.", notifications) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                open(appDetails)
            }
        }

        PermissionCard("Exact alarms", "Keeps check-ins on time, at least every 2 hours.", exactAlarms) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
            }
        }

        PermissionCard("Camera", "For photo proof. Photos stay private inside this app.", camera) {
            requestPermission.launch(Manifest.permission.CAMERA)
        }
    }
}

@Composable
private fun PermissionCard(title: String, why: String, granted: Boolean, onGrant: () -> Unit) {
    SectionCard(title) {
        Muted(why)
        if (granted) {
            Text("Granted", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
        } else {
            Button(onClick = onGrant) { Text("Grant") }
        }
    }
}

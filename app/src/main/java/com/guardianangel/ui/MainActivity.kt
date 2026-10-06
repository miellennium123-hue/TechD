package com.guardianangel.ui

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.guardianangel.core.Guardian
import com.guardianangel.ui.theme.GuardianTheme

enum class Screen(val title: String) {
    HOME("Guardian Angel"),
    SETTINGS("Settings"),
    CHASTITY("Chastity"),
    GALLERY("Photo proof"),
    ALLOWED("Always-allowed apps"),
    PROMPTS("What she can ask for"),
    TASK("Her task"),
    TASKS("Rules & Tasks"),
    SETUP("Permissions"),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        setContent { GuardianTheme { MainContent() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainContent() {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    val config by Guardian.config.flow.collectAsState()
    val state by Guardian.state.flow.collectAsState()
    val back = {
        screen = if (screen == Screen.ALLOWED || screen == Screen.PROMPTS || screen == Screen.TASKS) Screen.SETTINGS else Screen.HOME
    }

    BackHandler(enabled = screen != Screen.HOME) { back() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screen.title) },
                navigationIcon = {
                    if (screen != Screen.HOME) {
                        IconButton(onClick = back) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = { QuitButton(Modifier.padding(end = 8.dp)) { Guardian.quitForNow() } },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (screen) {
                Screen.HOME -> HomeScreen(config, state) { screen = it }
                Screen.SETTINGS -> SettingsScreen(config, { screen = Screen.ALLOWED }, { screen = Screen.PROMPTS }, { screen = Screen.TASKS })
                Screen.CHASTITY -> ChastityScreen(config, state)
                Screen.GALLERY -> GalleryScreen()
                Screen.ALLOWED -> AllowedAppsScreen(config)
                Screen.PROMPTS -> PromptsScreen(config)
                Screen.SETUP -> SetupScreen()
                Screen.TASK -> TaskScreen(state)
                Screen.TASKS -> TasksListScreen(config)
            }
        }
    }
}

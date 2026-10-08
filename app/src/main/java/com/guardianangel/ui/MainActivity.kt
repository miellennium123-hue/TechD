package com.guardianangel.ui

import android.content.Context
import android.content.Intent
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.guardianangel.core.Guardian
import com.guardianangel.core.Line
import com.guardianangel.ui.theme.GuardianTheme

enum class Screen(val title: String) {
    HOME("Guardian Angel"),
    SETTINGS("Settings"),
    CHASTITY("Chastity"),
    GALLERY("Photo proof"),
    ALLOWED("Always-allowed apps"),
    PORN_APPS("Apps she checks"),
    ADULT_APPS("Adult apps"),
    PROMPTS("What she can ask for"),
    TASK("Her task"),
    TASKS("Rules & Tasks"),
    QUESTIONS("Her questions"),
    SITES("Your sites"),
    LINES("Her lines"),
    LINE("Her lines"),
    SESSIONS("Guided sessions"),
    CLIPS("Her videos"),
    BACKGROUNDS("Backgrounds"),
    KINKS("Kink menu"),
    REPORTS("Daily reports"),
    RECORD("Her record"),
    RELEASE("Release calendar"),
    SETUP("Permissions"),
}

class MainActivity : ComponentActivity() {
    /** A screen asked for by a notification tap. Consumed once by [MainContent]. */
    private val requested = mutableStateOf<Screen?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        // Only on a fresh start: after a rotation, keep whatever screen you navigated to.
        if (savedInstanceState == null) requested.value = screenFrom(intent)
        setContent {
            GuardianTheme {
                MainContent(requested)
                LoosenHost()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Guardian.checkWatch() // Lock guard: her watch switched off during a lock
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        screenFrom(intent)?.let { requested.value = it }
    }

    companion object {
        private const val EXTRA_SCREEN = "screen"

        fun intent(context: Context, screen: Screen): Intent =
            Intent(context, MainActivity::class.java).putExtra(EXTRA_SCREEN, screen.name)

        private fun screenFrom(intent: Intent?): Screen? =
            intent?.getStringExtra(EXTRA_SCREEN)?.let { name -> Screen.entries.firstOrNull { it.name == name } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainContent(requested: MutableState<Screen?>) {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var line by rememberSaveable { mutableStateOf(Line.GREETING) }
    LaunchedEffect(requested.value) {
        requested.value?.let {
            screen = it
            requested.value = null
        }
    }
    val config by Guardian.config.flow.collectAsState()
    val state by Guardian.state.flow.collectAsState()
    val back = {
        screen = when (screen) {
            Screen.LINE -> Screen.LINES
            Screen.KINKS, Screen.CLIPS -> Screen.SESSIONS
            Screen.ALLOWED, Screen.PORN_APPS, Screen.ADULT_APPS, Screen.PROMPTS, Screen.TASKS, Screen.QUESTIONS, Screen.SITES, Screen.LINES, Screen.BACKGROUNDS -> Screen.SETTINGS
            else -> Screen.HOME
        }
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
                Screen.SETTINGS -> SettingsScreen(config, { screen = Screen.ALLOWED }, { screen = Screen.PROMPTS }, { screen = Screen.TASKS }, { screen = Screen.QUESTIONS }, { screen = Screen.SITES }, { screen = Screen.LINES }, { screen = Screen.BACKGROUNDS }, { screen = Screen.PORN_APPS }, { screen = Screen.ADULT_APPS })
                Screen.CHASTITY -> ChastityScreen(config, state)
                Screen.GALLERY -> GalleryScreen()
                Screen.ALLOWED -> AllowedAppsScreen(config)
                Screen.PORN_APPS -> PornAppsScreen(config)
                Screen.ADULT_APPS -> AdultAppsScreen(config)
                Screen.PROMPTS -> PromptsScreen(config)
                Screen.SETUP -> SetupScreen()
                Screen.TASK -> TaskScreen(state)
                Screen.TASKS -> TasksListScreen(config)
                Screen.QUESTIONS -> QuestionsScreen(config)
                Screen.SITES -> SitesScreen(config)
                Screen.LINES -> LinesScreen(config) {
                    line = it
                    screen = Screen.LINE
                }
                Screen.LINE -> LineScreen(config, line)
                Screen.SESSIONS -> SessionsScreen(config, state, { screen = Screen.KINKS }, { screen = Screen.CLIPS })
                Screen.CLIPS -> ClipsScreen()
                Screen.KINKS -> KinksScreen(config)
                Screen.BACKGROUNDS -> BackgroundsScreen(config)
                Screen.REPORTS -> ReportsScreen(config, state)
                Screen.RECORD -> RecordScreen(state)
                Screen.RELEASE -> ReleaseScreen(config, state)
            }
        }
    }
}

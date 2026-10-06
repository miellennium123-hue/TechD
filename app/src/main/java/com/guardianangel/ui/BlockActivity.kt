package com.guardianangel.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.guardianangel.core.AskResult
import com.guardianangel.core.Decision
import com.guardianangel.core.Guardian
import com.guardianangel.core.InstalledApps
import com.guardianangel.core.Line
import com.guardianangel.core.RestrictionKind
import com.guardianangel.core.Rules
import com.guardianangel.ui.theme.GuardianTheme

/** Her block screen, launched over a guarded app by the accessibility service. */
class BlockActivity : ComponentActivity() {
    private var targetPackage by mutableStateOf("")
    private var resumes by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        targetPackage = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        onBackPressedDispatcher.addCallback(this) { goHome() }
        setContent {
            GuardianTheme {
                BlockScreen(targetPackage, resumes, onOpen = ::openTarget, onHome = ::goHome)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_PACKAGE)?.let { targetPackage = it }
    }

    override fun onResume() {
        super.onResume()
        resumes++
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun openTarget() {
        packageManager.getLaunchIntentForPackage(targetPackage)?.let {
            startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }

    companion object {
        private const val EXTRA_PACKAGE = "package"

        fun intent(context: Context, pkg: String): Intent =
            Intent(context, BlockActivity::class.java)
                .putExtra(EXTRA_PACKAGE, pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

@Composable
private fun BlockScreen(pkg: String, resumes: Int, onOpen: () -> Unit, onHome: () -> Unit) {
    val context = LocalContext.current
    val config by Guardian.config.flow.collectAsState()
    val state by Guardian.state.flow.collectAsState()
    val now = rememberNow()
    val decision = remember(pkg, config, state, now / 2_000, resumes) { Guardian.decide(pkg) }
    val appLabel = remember(pkg) { InstalledApps.label(context, pkg) }
    var line by remember(pkg) { mutableStateOf("") }

    LaunchedEffect(decision is Decision.Allow) {
        if (decision is Decision.Allow) onOpen()
    }
    LaunchedEffect(pkg) {
        val block = decision as? Decision.Block
        line = Guardian.say(
            when {
                block == null -> Line.GRANT
                block.kind == RestrictionKind.BEDTIME -> Line.BEDTIME
                block.kind == RestrictionKind.SUMMONS -> Line.IGNORED
                else -> Line.BLOCKED
            },
        )
    }

    val block = decision as? Decision.Block ?: return

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AngelImage(Modifier.size(160.dp))
            if (line.isNotBlank()) SpeechBubble(line)
            Text(
                "$appLabel is ${
                    when (block.kind) {
                        RestrictionKind.LOCKOUT -> "locked"
                        RestrictionKind.PERMISSION -> "guarded. Ask her first."
                        RestrictionKind.BEDTIME -> "off limits at bedtime"
                        RestrictionKind.PUNISHMENT -> "locked as punishment"
                        RestrictionKind.RULE -> "locked by her rule"
                        RestrictionKind.SUMMONS -> "locked. She wants you, and you kept her waiting."
                    }
                }",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            when (block.kind) {
                RestrictionKind.PUNISHMENT, RestrictionKind.RULE, RestrictionKind.LOCKOUT ->
                    Text("${formatDuration(block.until - now)} left", fontWeight = FontWeight.Bold)
                RestrictionKind.BEDTIME -> Text("Until ${formatMinuteOfDay(config.bedtime.endMinute)}", fontWeight = FontWeight.Bold)
                else -> Unit
            }

            // Round 53: her timed block has one way in, merit. No waiting, no photos, no asking.
            if (block.canBuy) {
                when {
                    !config.meritOn -> Muted("Merit is off, so there's no way in. Her block ends on its own.")
                    else -> {
                        Muted("No asking, no photos. The only way in: ${Rules.BUY_MERIT} merit for ${Rules.BUY_MINUTES} minutes. You have ${state.merit}.")
                        OutlinedButton(
                            enabled = Rules.canBuyTime(config, state),
                            onClick = {
                                Guardian.buyTime(pkg)?.let {
                                    line = it
                                    onOpen()
                                }
                            },
                        ) { Text("Spend ${Rules.BUY_MERIT} merit for ${Rules.BUY_MINUTES} minutes") }
                    }
                }
            }

            if (block.askAllowed) {
                val cooldown = state.askCooldownUntil[pkg] ?: 0L
                Button(
                    enabled = cooldown <= now,
                    onClick = {
                        val result = Guardian.ask(pkg)
                        line = result.line
                        when (result) {
                            is AskResult.Granted -> onOpen()
                            is AskResult.ProofDemanded -> context.startActivity(ProofActivity.intent(context, result.request.id))
                            is AskResult.Denied -> Unit
                        }
                    },
                ) { Text(if (cooldown > now) "Ask again in ${formatDuration(cooldown - now)}" else "Ask her") }
            }

            if (block.kind == RestrictionKind.SUMMONS) {
                Button(onClick = { context.startActivity(ShowUpActivity.intent(context)) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Answer her")
                }
            }
            if (block.kind == RestrictionKind.RULE) state.task?.let { Text(it.text, textAlign = TextAlign.Center) }
            when (block.kind) {
                RestrictionKind.BEDTIME -> Muted("No way in until bedtime ends.")
                RestrictionKind.PUNISHMENT, RestrictionKind.RULE -> Muted("Blocked until the timer ends.")
                else -> Unit
            }

            OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) { Text("Go home") }
            QuitButton(Modifier.fillMaxWidth()) { Guardian.quitForNow() }
            Muted("Phone and emergency calls are never blocked.")
        }
    }
}

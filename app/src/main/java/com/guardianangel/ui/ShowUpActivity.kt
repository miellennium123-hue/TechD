package com.guardianangel.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.guardianangel.core.AnswerResult
import com.guardianangel.core.Guardian
import com.guardianangel.core.Rules
import com.guardianangel.data.QuestionKind
import com.guardianangel.ui.theme.GuardianTheme

/** She shows up full screen and asks a question. Opened from her "she wants you" notification. */
class ShowUpActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        setContent { GuardianTheme { ShowUpScreen(onDone = ::finish) } }
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, ShowUpActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

@Composable
private fun ShowUpScreen(onDone: () -> Unit) {
    val state by Guardian.state.flow.collectAsState()
    val summons = state.summons
    val now = rememberNow()
    var line by remember { mutableStateOf<String?>(null) }
    var typed by remember { mutableStateOf("") }
    val choices = remember(summons?.id) {
        summons?.question?.let { q -> (q.wrong + q.answer).filter { it.isNotBlank() }.distinct().shuffled() }.orEmpty()
    }

    fun answer(given: String) {
        val result = Guardian.answer(given)
        line = result.line
        typed = ""
        if (result is AnswerResult.Wrong) {
            val tries = if (result.triesLeft == 1) "Last try." else "${result.triesLeft} tries left."
            line = "${result.line}\n$tries"
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AngelImage(Modifier.size(180.dp))
            SpeechBubble(line ?: state.lastLine.ifBlank { "Pet. I want you." })
            if (summons == null) {
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
            } else {
                val q = summons.question
                Text(q.text, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                when (q.kind) {
                    QuestionKind.CHOICE -> choices.forEach { option ->
                        OutlinedButton(onClick = { answer(option) }, modifier = Modifier.fillMaxWidth()) { Text(option) }
                    }
                    QuestionKind.PHRASE -> {
                        Text("Type exactly:", style = MaterialTheme.typography.bodyMedium)
                        Text(q.answer, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        OutlinedTextField(
                            value = typed,
                            onValueChange = { typed = it },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        )
                        Button(onClick = { answer(typed) }, enabled = typed.isNotBlank()) { Text("Answer her") }
                    }
                }
                val triesLeft = Rules.MAX_WRONG_ANSWERS - summons.wrongAnswers
                Muted(
                    if (triesLeft == 1) "Last try. Another wrong answer counts as a failure."
                    else "$triesLeft tries. Getting it wrong ${Rules.MAX_WRONG_ANSWERS} times counts as a failure.",
                )
                if (now >= summons.lockAt) {
                    Text("Everything is locked until you answer.", color = MaterialTheme.colorScheme.error)
                } else {
                    Muted("Answer within ${formatDuration(summons.lockAt - now)} or everything locks. You can leave and come back.")
                }
                OutlinedButton(onClick = onDone) { Text("Later") }
            }
            QuitButton(Modifier.fillMaxWidth()) {
                Guardian.quitForNow()
                onDone()
            }
            Muted("Phone and emergency calls are never blocked.")
        }
    }
}

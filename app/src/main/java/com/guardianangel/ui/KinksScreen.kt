package com.guardianangel.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.guardianangel.core.Guardian
import com.guardianangel.data.CbtLevel
import com.guardianangel.data.GuardianConfig
import com.guardianangel.data.Kink

/** The kink menu: which kinds of commands she may use in a session. */
@Composable
fun KinksScreen(config: GuardianConfig) {
    val s = config.session

    fun toggle(kink: Kink, on: Boolean) {
        Guardian.updateConfig { c ->
            c.copy(session = c.session.copy(kinks = if (on) c.session.kinks + kink else c.session.kinks - kink))
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Muted(
            "Tick what she may use. Stroking (or holding still during a lock) is always there. " +
                "During a chastity lock only cage-safe ones run until the ending. Her words for each are in Her lines.",
        )
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Kink.entries, key = { it.name }) { kink ->
                val on = kink in s.kinks
                Card(Modifier.fillMaxWidth().clickable { toggle(kink, !on) }) {
                    Column(Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = on, onCheckedChange = { toggle(kink, it) })
                            Text(kink.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Muted(
                                when {
                                    !kink.uncaged -> "Locked only"
                                    kink.cageSafe -> "Cage-safe"
                                    else -> "Not in a cage"
                                },
                            )
                        }
                        Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Muted(kink.note)
                            if (kink == Kink.CBT && on) {
                                ChoiceChips(CbtLevel.entries, s.cbt, { it.label }) { level ->
                                    Guardian.updateConfig { c -> c.copy(session = c.session.copy(cbt = level)) }
                                }
                                Muted("Soft: 3 to 5 gentle, counted. Hard: 6 to 12 firm. Stop if it ever hurts sharply; \"Too much\" skips with no penalty.")
                            }
                            if (kink == Kink.SOUNDING && on) {
                                Muted("Only with a sterile sound and sterile lube; you tick that before each session. No beat, no rushing, and \"Too much\" skips any time.")
                            }
                        }
                    }
                }
            }
        }
    }
}

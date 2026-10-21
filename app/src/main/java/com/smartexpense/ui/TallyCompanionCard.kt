package com.smartexpense.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartexpense.R

/** Explicit file handoff to the user's custom Codex plugin; never uploads records. */
@Composable internal fun TallyCompanionCard(onPrepare: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    GroupedCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StudioSecondaryAction("Review with Tally Companion", icon = R.drawable.ic_insights,
                expanded = expanded, onClick = { expanded = !expanded })
            AnimatedVisibility(expanded,
                enter = expandVertically(tween(220)) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(180)) + fadeOut(tween(120))) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Your custom spending-review plugin", style = MaterialTheme.typography.titleSmall)
                    Text("1. Prepare a Transactions CSV for the selected month, then tap Export report to save it.",
                        style = MaterialTheme.typography.bodyMedium)
                    Text("2. In Codex, select Tally Toolkit or Tally Companion and attach the saved CSV. Ask it to review your personal spending and categories.",
                        style = MaterialTheme.typography.bodyMedium)
                    ReferenceInfo("Choose what you share. CSV reports can include names, descriptions and notes. Nothing is sent from Tally automatically. Never share your encrypted backup or its password for a spending review.")
                    EditorialPrimaryButton("Prepare CSV for Companion", onClick = onPrepare)
                    Text("This prepares the export options. Your balances and transactions stay unchanged.",
                        style = MaterialTheme.typography.bodySmall, color = Tokens.muted)
                }
            }
        }
    }
}

package com.smartexpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartexpense.data.Preferences
import com.smartexpense.domain.ThemeMode
import kotlinx.coroutines.launch

@Composable internal fun AppearanceScreen() {
    val context = LocalContext.current
    val preferences = remember(context) { Preferences(context.applicationContext) }
    val selected by preferences.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.System)
    val scope = rememberCoroutineScope()
    Page("Appearance") {
        item { SectionLabel("THEME") }
        item { GroupedCard(Modifier.selectableGroup()) {
            ThemeMode.entries.forEachIndexed { index, mode ->
                if (index > 0) GroupedDivider(16.dp)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .selectable(selected = selected == mode, role = Role.RadioButton) { scope.launch { preferences.setThemeMode(mode) } }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                        if (mode == ThemeMode.System) Text("Matches your phone's dark theme setting", style = MaterialTheme.typography.bodySmall, color = Tokens.muted)
                    }
                    ReferenceSelectionMark(selected == mode, check = true)
                }
            }
        } }
        item { ReferenceInfo("Shared receipt PDFs and reports always use a white page.") }
    }
}

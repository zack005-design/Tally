package com.smartexpense.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartexpense.data.AppIcon
import com.smartexpense.data.AppIcons
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
        item { SectionLabel("APP ICON") }
        item { AppIconPicker() }
        item { ReferenceInfo("Your home screen can take a few seconds to show the new icon. If it disappears from the home screen, add Tally again from the app list.") }
    }
}

@Composable private fun AppIconPicker() {
    val context = LocalContext.current
    var current by remember { mutableStateOf(AppIcons.current(context)) }
    GroupedCard(Modifier.selectableGroup()) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppIcon.entries.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { icon ->
                        val selected = icon == current
                        Column(
                            Modifier.weight(1f)
                                .selectable(selected = selected, role = Role.RadioButton) { if (!selected) { AppIcons.select(context, icon); current = icon } }
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(Modifier.size(56.dp).border(2.dp, if (selected) Tokens.accent else androidx.compose.ui.graphics.Color.Transparent, CircleShape).padding(4.dp)) {
                                AppIconPreview(icon, 48.dp)
                            }
                            Text(icon.label, style = MaterialTheme.typography.labelSmall, color = if (selected) Tokens.accent else Tokens.muted, textAlign = TextAlign.Center, maxLines = 2)
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Draws a launcher icon as a phone shows it: the centre 72 of 108 units, in a circle. */
@Composable internal fun AppIconPreview(icon: AppIcon, size: Dp) {
    Box(Modifier.size(size).clip(CircleShape).semantics { contentDescription = "${icon.label} icon" }, contentAlignment = Alignment.Center) {
        Image(painterResource(icon.background), null, Modifier.requiredSize(size * 1.5f))
        Image(painterResource(icon.foreground), null, Modifier.requiredSize(size * 1.5f))
    }
}

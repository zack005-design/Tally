package com.smartexpense.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.smartexpense.R

private val tabDestinations = listOf(
    Triple("home", "Home", R.drawable.ic_tab_home),
    Triple("activity", "Activity", R.drawable.ic_tab_activity),
    Triple("add", "Add", R.drawable.ic_tab_add),
    Triple("split", "Split", R.drawable.ic_tab_split),
    Triple("insights", "Insights", R.drawable.ic_tab_insights)
)

@Composable
fun EditorialBottomNavigation(route: String?, onNavigate: (String) -> Unit) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
    // Measure actual labels so both display size and accessibility text size can adapt.
    val labelWidth = with(density) {
        tabDestinations.maxOf { measurer.measure(it.second, labelStyle).size.width }.toDp()
    }
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            shadowElevation = 8.dp
        ) {
            BoxWithConstraints(Modifier.padding(6.dp)) {
                val minimumTabWidth = (labelWidth + 16.dp).coerceAtLeast(48.dp)
                val columns = (maxWidth / minimumTabWidth).toInt().coerceIn(1, tabDestinations.size)
                val tabWidth = maxWidth / columns
                Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    tabDestinations.chunked(columns).forEach {tabs ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            tabs.forEach {(destination, label, icon) ->
                                val selected = route == destination
                                val isAdd = destination == "add"
                                val tint by animateColorAsState(
                                    if (selected) Tokens.text else Tokens.muted,
                                    tween(180), label = "tab tint"
                                )
                                val highlight by animateColorAsState(
                                    if (selected) Tokens.secondarySurface else Color.Transparent,
                                    tween(180), label = "tab highlight"
                                )
                                val addFill by animateColorAsState(
                                    if (selected) Tokens.accent else Tokens.secondarySurface,
                                    tween(180), label = "add button fill"
                                )
                                val addTint by animateColorAsState(
                                    if (selected) MaterialTheme.colorScheme.onPrimary else Tokens.text,
                                    tween(180), label = "add button tint"
                                )
                                Column(
                                    Modifier.width(tabWidth).padding(horizontal = 2.dp)
                                        .clip(RoundedCornerShape(26.dp))
                                        .background(highlight, RoundedCornerShape(26.dp))
                                        .selectable(selected, role = Role.Tab, onClick = {onNavigate(destination)})
                                        .heightIn(min = 64.dp).padding(horizontal = 4.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Box(
                                        Modifier.width(if (isAdd) 42.dp else 32.dp).height(32.dp)
                                            .background(if (isAdd) addFill else Color.Transparent, RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(painterResource(icon), contentDescription = null,
                                            modifier = Modifier.size(if (isAdd) 20.dp else 23.dp),
                                            tint = if (isAdd) addTint else tint)
                                    }
                                    Text(label, style = labelStyle, color = tint, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

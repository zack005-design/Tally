package com.smartexpense.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.smartexpense.R

private val lightTabs = listOf(
    Triple("home", "Overview", R.drawable.ic_tab_home),
    Triple("insights", "Spending", R.drawable.ic_tab_insights),
    Triple("split", "Friends", R.drawable.ic_tab_split)
)

/** Floating grouped navigation and a separate compose action, with an expanded large-text layout. */
@Composable fun EditorialBottomNavigation(route: String?, onNavigate: (String) -> Unit) {
    val addInteraction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val addAction = lightHapticAction { onNavigate("add") }
    val density = LocalDensity.current
    val style = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val labelWidth = with(density) { lightTabs.maxOf { measurer.measure(it.second, style).size.width }.toDp() }
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        BoxWithConstraints(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
            val expanded = maxWidth - 80.dp < (labelWidth + 20.dp) * lightTabs.size
            val stackedTabs = maxWidth - 12.dp < (labelWidth + 10.dp) * lightTabs.size
            val tab: @Composable (Triple<String, String, Int>, Modifier) -> Unit = { (destination, label, icon), modifier ->
                val selected = route == destination
                val selectAction = lightHapticAction { onNavigate(destination) }
                val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val tint by animateColorAsState(if (selected) Tokens.accent else Tokens.muted, tween(180), label = "Tab tint")
                val fill by animateColorAsState(if (selected) Tokens.secondarySurface else Color.Transparent, tween(220), label = "Tab selection")
                val target = modifier.softPress(interaction).clip(Tokens.card).background(fill)
                    .selectable(selected, interactionSource = interaction, indication = androidx.compose.foundation.LocalIndication.current, role = Role.Tab, onClick = { if (!selected) selectAction() })
                    .heightIn(min = 54.dp)
                if (stackedTabs) Row(target.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(painterResource(icon), null, Modifier.size(22.dp), tint = tint)
                    Text(label, Modifier.weight(1f), style = style, color = tint)
                } else Column(target.padding(horizontal = 5.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)) {
                    Icon(painterResource(icon), null, Modifier.size(22.dp), tint = tint)
                    Text(label, style = style, color = tint, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            val tabs: @Composable (Modifier) -> Unit = { modifier ->
                Surface(modifier, shape = if(stackedTabs) Tokens.card else CircleShape, color = Tokens.paper.copy(alpha = .94f),
                    border = BorderStroke(.5.dp, Tokens.border), shadowElevation = 1.dp) {
                    if(stackedTabs) Column(Modifier.selectableGroup().padding(6.dp)) {
                        lightTabs.forEach { tab(it, Modifier.fillMaxWidth()) }
                    } else Row(Modifier.selectableGroup().padding(6.dp).height(IntrinsicSize.Min)) {
                        lightTabs.forEach { tab(it, Modifier.weight(1f).fillMaxHeight()) }
                    }
                }
            }
            if (expanded) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tabs(Modifier.fillMaxWidth())
                Button(addAction, Modifier.align(Alignment.End).heightIn(min = 48.dp).softPress(addInteraction).semantics { contentDescription = "Add expense" }, shape = CircleShape, interactionSource = addInteraction) {
                    Icon(painterResource(R.drawable.ic_compose), null, Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add expense")
                }
            } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                tabs(Modifier.weight(1f))
                Surface(onClick = addAction, interactionSource = addInteraction, shape = CircleShape, color = Tokens.accent,
                    contentColor = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(60.dp).softPress(addInteraction), shadowElevation = 3.dp) {
                    Box(contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_compose), "Add expense", Modifier.size(26.dp)) }
                }
            }
        }
    }
}

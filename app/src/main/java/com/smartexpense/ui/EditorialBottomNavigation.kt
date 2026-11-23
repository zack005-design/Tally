package com.smartexpense.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
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

/** Main tabs in the redesign: Summary, Activity, Spending and Split. Search sits beside them. */
internal val lightTabs = listOf(
    Triple("home", "Summary", R.drawable.ic_tab_summary),
    Triple("activity", "Activity", R.drawable.ic_tab_activity),
    Triple("insights", "Spending", R.drawable.ic_tab_insights),
    Triple("split", "Split", R.drawable.ic_people)
)

/** The glass bar: translucent paper over content, with a hairline edge. */
@Composable private fun glassColor() = Tokens.paper.copy(alpha = if (LocalDarkTheme.current) .92f else .88f)

@Composable private fun SearchButton(onNavigate: (String) -> Unit, size: androidx.compose.ui.unit.Dp) {
    val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Surface(onClick = lightHapticAction { onNavigate("search") }, interactionSource = interaction, shape = CircleShape, color = glassColor(),
        contentColor = Tokens.text, border = BorderStroke(.5.dp, Tokens.border), shadowElevation = 6.dp,
        modifier = Modifier.size(size).softPress(interaction)) {
        Box(contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_search), "Search", Modifier.size(22.dp)) }
    }
}

/** Floating glass tab bar with a separate search button, and a stacked layout for very large text. */
@Composable fun EditorialBottomNavigation(route: String?, onNavigate: (String) -> Unit) {
    val density = LocalDensity.current
    val style = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val labelWidth = with(density) { lightTabs.maxOf { measurer.measure(it.second, style).size.width }.toDp() }
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        BoxWithConstraints(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
            // Each tab needs its label plus 12dp; the bar adds 8dp of padding and search needs 56dp plus a 10dp gap.
            val stackedTabs = maxWidth - 8.dp < (labelWidth + 12.dp) * lightTabs.size
            val tab: @Composable (Triple<String, String, Int>, Modifier) -> Unit = { (destination, label, icon), modifier ->
                val selected = route == destination
                val selectAction = lightHapticAction { onNavigate(destination) }
                val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val tint by animateColorAsState(if (selected) Tokens.accent else Tokens.text, tween(180), label = "Tab tint")
                val fill by animateColorAsState(if (selected) Tokens.tonal else Color.Transparent, tween(220), label = "Tab selection")
                val target = modifier.softPress(interaction).clip(CircleShape).background(fill)
                    .selectable(selected, interactionSource = interaction, indication = androidx.compose.foundation.LocalIndication.current, role = Role.Tab, onClick = { if (!selected) selectAction() })
                    .heightIn(min = 52.dp)
                if (stackedTabs) Row(target.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(painterResource(icon), null, Modifier.size(22.dp), tint = tint)
                    Text(label, Modifier.weight(1f), style = style, color = tint)
                } else Column(target.padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
                    Icon(painterResource(icon), null, Modifier.size(22.dp), tint = tint)
                    Text(label, style = style, color = tint, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            val tabs: @Composable (Modifier) -> Unit = { modifier ->
                Surface(modifier, shape = if (stackedTabs) Tokens.card else CircleShape, color = glassColor(),
                    border = BorderStroke(.5.dp, Tokens.border), shadowElevation = 6.dp) {
                    if (stackedTabs) Column(Modifier.selectableGroup().padding(4.dp)) {
                        lightTabs.forEach { tab(it, Modifier.fillMaxWidth()) }
                    } else Row(Modifier.selectableGroup().padding(4.dp).height(IntrinsicSize.Min)) {
                        lightTabs.forEach { tab(it, Modifier.weight(1f).fillMaxHeight()) }
                    }
                }
            }
            if (stackedTabs) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tabs(Modifier.fillMaxWidth())
                Box(Modifier.align(Alignment.End)) { SearchButton(onNavigate, 56.dp) }
            } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                tabs(Modifier.weight(1f))
                SearchButton(onNavigate, 60.dp)
            }
        }
    }
}


/** Side navigation for short windows such as landscape phones, so screen content keeps the full height. */
@Composable fun EditorialNavigationRail(route: String?, onNavigate: (String) -> Unit) {
    Surface(Modifier.fillMaxHeight().width(96.dp), color = glassColor(), border = BorderStroke(.5.dp, Tokens.border)) {
        Column(Modifier.fillMaxHeight().navigationBarsPadding().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(vertical = 12.dp, horizontal = 8.dp).selectableGroup(),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
            SearchButton(onNavigate, 52.dp)
            lightTabs.forEach { (destination, label, icon) ->
                val selected = route == destination
                val selectAction = lightHapticAction { onNavigate(destination) }
                val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val tint by animateColorAsState(if (selected) Tokens.accent else Tokens.text, tween(180), label = "Rail tint")
                val fill by animateColorAsState(if (selected) Tokens.tonal else Color.Transparent, tween(220), label = "Rail selection")
                Column(Modifier.fillMaxWidth().softPress(interaction).clip(Tokens.card).background(fill)
                    .selectable(selected, interactionSource = interaction, indication = androidx.compose.foundation.LocalIndication.current, role = Role.Tab, onClick = { if (!selected) selectAction() })
                    .heightIn(min = 56.dp).padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)) {
                    Icon(painterResource(icon), null, Modifier.size(22.dp), tint = tint)
                    Text(label, style = MaterialTheme.typography.labelSmall, color = tint, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
                }
            }
        }
    }
}

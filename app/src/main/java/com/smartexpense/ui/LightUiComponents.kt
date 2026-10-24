package com.smartexpense.ui

import androidx.compose.runtime.getValue

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.smartexpense.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription

internal enum class ReferenceTone { Blue, Lavender, Peach, Pink, Gray }

@Composable internal fun ReferenceIcon(icon: Int, tone: ReferenceTone = ReferenceTone.Blue, size: androidx.compose.ui.unit.Dp = 36.dp) {
    val (background, foreground) = when (tone) {
        ReferenceTone.Blue -> Color(0xFFEAF1FF) to Tokens.accent
        ReferenceTone.Lavender -> Color(0xFFF0EAFF) to Color(0xFF7052C4)
        ReferenceTone.Peach -> Color(0xFFFFEEE3) to Color(0xFFC35A26)
        ReferenceTone.Pink -> Color(0xFFFFEBF1) to Color(0xFFB53061)
        ReferenceTone.Gray -> Color(0xFFEEF0F5) to Tokens.muted
    }
    Surface(shape = if(size>=64.dp) CircleShape else RoundedCornerShape(size*.28f), color = background, modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) { Icon(painterResource(icon), null, Modifier.size(size * .55f), tint = foreground) }
    }
}

@Composable internal fun GroupedCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = Tokens.card, color = Tokens.paper, shadowElevation = 1.dp, border = BorderStroke(.5.dp, Tokens.border.copy(alpha = .5f))) { Column(content = content) }
}

@Composable internal fun GroupedDivider(inset: androidx.compose.ui.unit.Dp = 64.dp) {
    HorizontalDivider(Modifier.padding(start = inset), color = Tokens.border.copy(alpha = .65f))
}

/** A button row; decorative icons are excluded and visible text supplies the accessible label. */
@Composable internal fun GroupedRow(label: String, icon: Int, supporting: String? = null, value: String? = null, tone: ReferenceTone = ReferenceTone.Blue, showChevron: Boolean = true, leading: (@Composable () -> Unit)? = null, roomy: Boolean = false, bareIcon: Boolean = false, color: Color = Tokens.paper, onClick: () -> Unit) {
    val rowAction = lightHapticAction(onClick)
    val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.bodyLarge
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    // Reserve the leading icon, gaps and chevron before deciding whether values fit.
    val available = with(density) { (maxWidth - 28.dp - 36.dp - 24.dp - if(showChevron) 26.dp else 0.dp).toPx() }
    val labelWidth = measurer.measure(label, style).size.width
    val valueWidth = value?.let { measurer.measure(it, style).size.width } ?: 0
    val large = density.fontScale > 1.3f || value != null && labelWidth + valueWidth > available
    Surface(onClick = rowAction, interactionSource = interaction, color = color, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).softPress(interaction)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = if(roomy) 16.dp else 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if(leading!=null) leading() else if(bareIcon) Icon(painterResource(icon),null,Modifier.size(30.dp),tint=Tokens.accent) else ReferenceIcon(icon, tone)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight=if(supporting!=null) FontWeight.Medium else FontWeight.Normal)
                supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Tokens.muted) }
                if (large) value?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            }
            if (!large) value?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            if (showChevron) Icon(painterResource(R.drawable.ic_chevron_down), null, Modifier.size(14.dp).rotate(-90f), tint = Tokens.muted)
        }
    }
    }
}

/** Whole rupees omit .00 in presentation; entered and stored amounts remain exact paise. */
internal fun referenceMoney(paise: Long): String = editorialMoney(paise).let { if (paise % 100L == 0L) it.removeSuffix(".00") else it }

@Composable internal fun ReferenceHero(amount: String, caption: String, icon: Int? = null, tone: ReferenceTone = ReferenceTone.Blue) {
    Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        icon?.let { ReferenceIcon(it, tone, 76.dp) }
        Text(amount, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
        if (caption.isNotBlank()) Text(caption, style = MaterialTheme.typography.bodyMedium, color = Tokens.muted, textAlign = TextAlign.Center)
    }
}

@Composable internal fun ReferenceInfo(text: String, warning: Boolean = false) {
    Surface(Modifier.fillMaxWidth(), shape = Tokens.field, color = if (warning) Color(0xFFFFF3DD) else Tokens.secondarySurface) {
        Row(Modifier.padding(14.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.Top) {
            Icon(painterResource(R.drawable.ic_info),null,Modifier.size(20.dp),tint=Tokens.muted)
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = if (warning) Color(0xFF865315) else Tokens.muted)
        }
    }
}

@Composable internal fun ReferenceAction(label: String, icon: Int, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Surface(onClick = lightHapticAction(onClick), interactionSource = interaction, enabled = enabled, color = Tokens.secondarySurface, contentColor = Tokens.accent, shape = Tokens.field, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).softPress(interaction)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), null, Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable internal fun ReferenceValueRow(label: String, value: String, color: Color = Tokens.text) {
    val stacked = LocalDensity.current.fontScale > 1.3f || value.length > 24
    if (stacked) Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Tokens.muted)
        Text(value, style = MaterialTheme.typography.titleSmall, color = color)
    } else Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Tokens.muted)
        Text(value, style = MaterialTheme.typography.titleSmall, color = color)
    }
}

@Composable internal fun ReferenceToggleRow(label: String, checked: Boolean, onChanged: ((Boolean) -> Unit)?, enabled: Boolean = true, supporting: String? = null) {
    val toggleFeedback = lightHapticAction {}
    val modifier = if (onChanged != null) Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = { value -> toggleFeedback(); onChanged(value) })
        else Modifier.semantics(mergeDescendants = true) { stateDescription = if (checked) "On" else "Off" }
    Row(modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Tokens.muted) }
        }
        val thumbOffset by androidx.compose.animation.core.animateDpAsState(if(checked) 20.dp else 0.dp,label="Switch position")
        val track by animateColorAsState(if(checked) Tokens.accent else Tokens.border, tween(180), label="Switch color")
        Box(Modifier.size(51.dp,31.dp).background(if(enabled) track else track.copy(alpha=.45f),CircleShape).padding(2.dp),contentAlignment=Alignment.CenterStart) {
            Surface(Modifier.offset(x=thumbOffset).size(27.dp),shape=CircleShape,color=Color.White,shadowElevation=1.dp) {}
        }
    }
}

@Composable internal fun SectionLabel(text: String) {
    Text(text, Modifier.padding(start = 12.dp, top = 8.dp).semantics { heading() }, style = MaterialTheme.typography.labelMedium, color = Tokens.muted)
}

@Composable internal fun TallyWordmark() {
    Surface(shape = CircleShape, color = Tokens.secondarySurface) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(7.dp), color = Tokens.accent, modifier = Modifier.size(27.dp)) {
                Icon(painterResource(R.drawable.ic_tally_mark), null, Modifier.fillMaxSize().padding(2.dp), tint = Color.White)
            }
            Text("tally", style = MaterialTheme.typography.titleSmall, color = Tokens.accent)
        }
    }
}

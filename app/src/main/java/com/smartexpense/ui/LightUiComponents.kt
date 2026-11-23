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

/** Solid colour icon square with a white glyph, as in iOS Settings. */
@Composable internal fun ReferenceIcon(icon: Int, tone: ReferenceTone = ReferenceTone.Blue, size: androidx.compose.ui.unit.Dp = 32.dp) {
    val background = when (tone) {
        ReferenceTone.Blue -> CategoryColors.brand
        ReferenceTone.Lavender -> CategoryColors.groceries
        ReferenceTone.Peach -> CategoryColors.food
        ReferenceTone.Pink -> CategoryColors.shopping
        ReferenceTone.Gray -> CategoryColors.gray
    }
    SolidIcon(icon, background, size)
}

@Composable internal fun SolidIcon(icon: Int, background: Color, size: androidx.compose.ui.unit.Dp = 32.dp) {
    Surface(shape = RoundedCornerShape(size * .26f), color = background, modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) { Icon(painterResource(icon), null, Modifier.size(size * .58f), tint = Color.White) }
    }
}

@Composable internal fun GroupedCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = Tokens.card, color = Tokens.paper) { Column(content = content) }
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
    val available = with(density) { (maxWidth - 28.dp - 32.dp - 24.dp - if(showChevron) 26.dp else 0.dp).toPx() }
    val labelWidth = measurer.measure(label, style).size.width
    val valueWidth = value?.let { measurer.measure(it, style).size.width } ?: 0
    val large = density.fontScale > 1.3f || value != null && labelWidth + valueWidth > available
    Surface(onClick = rowAction, interactionSource = interaction, color = color, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).softPress(interaction)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = if(roomy) 16.dp else 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if(leading!=null) leading() else if(bareIcon) Icon(painterResource(icon),null,Modifier.size(30.dp),tint=Tokens.accent) else ReferenceIcon(icon, tone)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight=if(supporting!=null) FontWeight.Medium else FontWeight.Normal)
                supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Tokens.muted) }
                if (large) value?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            }
            if (!large) value?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = Tokens.muted) }
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
    Surface(Modifier.fillMaxWidth(), shape = Tokens.field, color = if (warning) Tints.warningSoft else Tokens.secondarySurface) {
        Row(Modifier.padding(14.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.Top) {
            Icon(painterResource(R.drawable.ic_info),null,Modifier.size(20.dp),tint=Tokens.muted)
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = if (warning) Tints.warning else Tokens.muted)
        }
    }
}

@Composable internal fun ReferenceAction(label: String, icon: Int, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Surface(onClick = lightHapticAction(onClick), interactionSource = interaction, enabled = enabled, color = Tokens.tonal, contentColor = Tokens.accent, shape = Tokens.pill, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).softPress(interaction)) {
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
        // Switches use the system green from the redesign rather than the app tint.
        val track by animateColorAsState(if(checked) tint(Color(0xFF34C759), Color(0xFF30D158)) else MaterialTheme.colorScheme.surfaceContainerHighest, tween(180), label="Switch color")
        Box(Modifier.size(51.dp,31.dp).background(if(enabled) track else track.copy(alpha=.45f),CircleShape).padding(2.dp),contentAlignment=Alignment.CenterStart) {
            Surface(Modifier.offset {androidx.compose.ui.unit.IntOffset(thumbOffset.roundToPx(),0)}.size(27.dp),shape=CircleShape,color=Color.White,shadowElevation=1.dp) {}
        }
    }
}

/** Grouped-list header in sentence case, as iOS inset lists use. */
@Composable internal fun SectionLabel(text: String) {
    Text(sentenceCaseLabel(text), Modifier.padding(start = 16.dp, top = 8.dp).semantics { heading() }, style = MaterialTheme.typography.bodySmall, color = Tokens.muted)
}

private val labelAcronyms = mapOf("gpay" to "GPay", "upi" to "UPI", "pdf" to "PDF", "csv" to "CSV")

/** Older all-caps labels read as sentence case; acronyms keep their capitals. */
internal fun sentenceCaseLabel(text: String): String {
    if (text != text.uppercase() || text.none { it.isLetter() }) return text
    return text.lowercase().split(" ").joinToString(" ") { word -> labelAcronyms[word] ?: word }.replaceFirstChar { it.uppercase() }
}

@Composable internal fun TallyWordmark() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_tally_mark), null, Modifier.size(30.dp), tint = Tokens.accent)
        Text("Tally", style = MaterialTheme.typography.titleLarge, color = Tokens.text)
    }
}

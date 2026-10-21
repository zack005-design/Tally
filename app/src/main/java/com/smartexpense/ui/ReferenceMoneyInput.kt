package com.smartexpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartexpense.R

/** Inserts separators only in the rendered value; parsing still receives the original exact text. */
internal object GroupedMoneyTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (!Regex("[+-]?\\d*(\\.\\d*)?").matches(raw)) return TransformedText(text, OffsetMapping.Identity)
        val end = raw.indexOf('.').takeIf { it >= 0 } ?: raw.length
        val sign = if (raw.startsWith('-') || raw.startsWith('+')) 1 else 0
        val formatted = StringBuilder()
        val original = IntArray(raw.length + 1)
        val transformed = mutableListOf<Int>()
        raw.forEachIndexed { index, char ->
            if (index > sign && index < end && (end - index) % 3 == 0) {
                formatted.append(','); transformed.add(index)
            }
            original[index] = formatted.length
            formatted.append(char); transformed.add(index)
        }
        original[raw.length] = formatted.length
        transformed.add(raw.length)
        return TransformedText(AnnotatedString(formatted.toString()), object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = original[offset.coerceIn(0, raw.length)]
            override fun transformedToOriginal(offset: Int) = transformed[offset.coerceIn(0, formatted.length)]
        })
    }
}

/** Currency is part of the rendered text so the symbol stays next to the amount. */
internal object RupeeMoneyTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val grouped = GroupedMoneyTransformation.filter(text)
        val value = if (text.isEmpty()) "0" else grouped.text.text
        return TransformedText(AnnotatedString("₹$value"), object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = if (text.isEmpty()) 2 else grouped.offsetMapping.originalToTransformed(offset) + 1
            override fun transformedToOriginal(offset: Int) = if (text.isEmpty()) 0 else grouped.offsetMapping.transformedToOriginal((offset - 1).coerceAtLeast(0))
        })
    }
}

@Composable internal fun StartingBalanceInput(name: String, caption: String, value: String, onChange: (String) -> Unit, icon: Int, tone: ReferenceTone) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val large = LocalDensity.current.fontScale > 1.3f
    val input: @Composable (Modifier) -> Unit = { modifier ->
        BasicTextField(value, { onChange(it.replace(",", "").removePrefix("₹")) },
            modifier = modifier.heightIn(min = 48.dp).semantics { contentDescription = "$name balance (₹)" },
            textStyle = MaterialTheme.typography.headlineSmall.copy(fontSize = 23.sp, fontWeight = FontWeight.Normal, textAlign = TextAlign.End),
            cursorBrush = SolidColor(Tokens.accent), singleLine = true, visualTransformation = RupeeMoneyTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); keyboard?.hide() }),
            decorationBox = { inner -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    inner()
                }
                Icon(painterResource(R.drawable.ic_compose), null, Modifier.padding(start = 5.dp).size(18.dp), tint = Tokens.accent)
            } }
        )
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ReferenceIcon(icon, tone, 50.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(caption, style = MaterialTheme.typography.bodySmall, color = Tokens.muted)
            }
            if (!large) input(Modifier.width(118.dp))
        }
        if (large) input(Modifier.fillMaxWidth())
    }
}

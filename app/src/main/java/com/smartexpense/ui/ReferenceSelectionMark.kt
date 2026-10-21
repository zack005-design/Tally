package com.smartexpense.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/** Decorative selection mark. The containing selectable row owns interaction and semantics. */
@Composable internal fun ReferenceSelectionMark(selected:Boolean,check:Boolean=false) {
    val accent=Tokens.accent
    val border=Tokens.border
    Canvas(Modifier.size(24.dp).clearAndSetSemantics {}) {
        val radius=size.minDimension/2-1.dp.toPx()
        drawCircle(if(selected) accent else border,radius,style=if(selected && check) androidx.compose.ui.graphics.drawscope.Fill else Stroke(1.5.dp.toPx()))
        if(selected && check) {
            val path=Path().apply {moveTo(size.width*.26f,size.height*.51f);lineTo(size.width*.44f,size.height*.68f);lineTo(size.width*.76f,size.height*.32f)}
            drawPath(path,Color.White,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))
        } else if(selected) drawCircle(accent,radius*.53f)
    }
}

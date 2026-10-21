package com.smartexpense.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.smartexpense.R

internal fun referenceSymbol(label:String,fallback:Int=R.drawable.ic_food):Int {
    val text=label.lowercase()
    return when {
        "coffee" in text || "tea" in text -> R.drawable.ic_coffee_outline
        "pizza" in text -> R.drawable.ic_pizza_outline
        "drink" in text || "juice" in text -> R.drawable.ic_drink_outline
        "laptop" in text || "computer" in text -> R.drawable.ic_laptop_outline
        "trip" in text || "holiday" in text || "flight" in text -> R.drawable.ic_plane_outline
        "internet" in text || "wifi" in text -> R.drawable.ic_wifi_outline
        "gym" in text || "fitness" in text -> R.drawable.ic_gym_outline
        "rent" in text || "home" in text -> R.drawable.ic_home
        "emergency" in text -> R.drawable.ic_shield_outline
        "metro" in text || "train" in text || "travel" in text -> R.drawable.ic_transit
        else -> fallback
    }
}

/** Rounded continuous progress without Material's decorative endpoint dot. */
@Composable internal fun ReferenceProgress(progress:Float,modifier:Modifier=Modifier,overLimit:Boolean=false) {
    val value=progress.coerceIn(0f,1f)
    val track=Tokens.border.copy(alpha=.65f)
    val fill=if(overLimit) listOf(Tokens.negative,Tokens.negative) else listOf(Color(0xFF4594FF),Color(0xFF79B0FF))
    Canvas(modifier.height(10.dp).semantics {progressBarRangeInfo=ProgressBarRangeInfo(value,0f..1f)}) {
        val radius=CornerRadius(size.height/2)
        drawRoundRect(track,cornerRadius=radius)
        if(value>0) drawRoundRect(Brush.horizontalGradient(fill),size=Size(size.width*value,size.height),cornerRadius=radius)
    }
}

package com.smartexpense.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView

/** Draw-only feedback keeps touch targets and text layout stable while pressed. */
@Composable
internal fun Modifier.softPress(source: MutableInteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) .975f else 1f,
        spring(dampingRatio = .8f, stiffness = 650f),
        label = "Control press"
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Uses the system touch-feedback preference; never forces vibration. */
@Composable
internal fun lightHapticAction(onClick: () -> Unit): () -> Unit {
    val view = LocalView.current
    return {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        onClick()
    }
}

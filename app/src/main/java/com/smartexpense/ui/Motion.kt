package com.smartexpense.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** iOS-like spring: quick, settles without a visible bounce. */
internal fun <T> iosSpring(stiffness: Float = Spring.StiffnessMediumLow) = spring<T>(dampingRatio = .86f, stiffness = stiffness)

/**
 * Draw-only feedback keeps touch targets and text layout stable while pressed.
 * Like UIKit, a pressed control shrinks slightly and dims, then springs back on release.
 */
@Composable
internal fun Modifier.softPress(source: MutableInteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .96f else 1f, spring(dampingRatio = .6f, stiffness = if (pressed) 1400f else 500f), label = "Control press")
    val fade by animateFloatAsState(if (pressed) .72f else 1f, spring(stiffness = if (pressed) 2000f else 400f), label = "Control dim")
    return graphicsLayer { scaleX = scale; scaleY = scale; alpha = fade }
}

/**
 * iOS-style touch highlight used app-wide instead of the Material ripple: a soft gray wash appears at once
 * under the finger and fades out on release.
 */
internal object IosHighlight : androidx.compose.foundation.IndicationNodeFactory {
    override fun create(interactionSource: androidx.compose.foundation.interaction.InteractionSource): androidx.compose.ui.node.DelegatableNode = Node(interactionSource)
    override fun equals(other: Any?) = other === this
    override fun hashCode() = 7

    private class Node(private val source: androidx.compose.foundation.interaction.InteractionSource) :
        androidx.compose.ui.Modifier.Node(), androidx.compose.ui.node.DrawModifierNode {
        private val wash = androidx.compose.animation.core.Animatable(0f)
        override fun onAttach() {
            coroutineScope.launch {
                var presses = 0
                source.interactions.collect { interaction ->
                    when (interaction) {
                        is androidx.compose.foundation.interaction.PressInteraction.Press -> presses++
                        is androidx.compose.foundation.interaction.PressInteraction.Release, is androidx.compose.foundation.interaction.PressInteraction.Cancel -> presses = (presses - 1).coerceAtLeast(0)
                    }
                    launch {
                        if (presses > 0) wash.snapTo(1f)
                        else wash.animateTo(0f, androidx.compose.animation.core.tween(280))
                    }
                }
            }
        }
        override fun androidx.compose.ui.graphics.drawscope.ContentDrawScope.draw() {
            drawContent()
            if (wash.value > 0f) drawRect(androidx.compose.ui.graphics.Color(0xFF787880), alpha = .16f * wash.value)
        }
    }
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

/**
 * Status messages as an iOS-style banner pinned to the top of the page, so they never sit over
 * Save or the action buttons. Swipe up to dismiss early; TalkBack still announces it.
 */
@Composable
internal fun BoxScope.IosToastHost(state: SnackbarHostState) {
    val current = state.currentSnackbarData
    var shown by remember { mutableStateOf<SnackbarData?>(null) }
    if (current != null) shown = current
    // SnackbarHost normally times messages out; this host has to do it itself.
    LaunchedEffect(current) {
        val data = current ?: return@LaunchedEffect
        if (data.visuals.duration == androidx.compose.material3.SnackbarDuration.Indefinite) return@LaunchedEffect
        kotlinx.coroutines.delay(if (data.visuals.duration == androidx.compose.material3.SnackbarDuration.Long) 5000L else 2800L)
        data.dismiss()
    }
    AnimatedVisibility(
        current != null, Modifier.align(Alignment.TopCenter).padding(horizontal = 16.dp, vertical = 8.dp),
        enter = slideInVertically(iosSpring<IntOffset>(Spring.StiffnessMedium)) { -it * 2 } + fadeIn() + scaleIn(iosSpring(), initialScale = .9f),
        exit = slideOutVertically(iosSpring<IntOffset>(Spring.StiffnessMedium)) { -it * 2 } + fadeOut() + scaleOut(targetScale = .9f)
    ) {
        val data = shown ?: return@AnimatedVisibility
        Surface(
            shape = Tokens.pill, color = Tokens.paper, contentColor = Tokens.text, shadowElevation = 10.dp, tonalElevation = 0.dp,
            modifier = Modifier.widthIn(max = 420.dp).semantics { liveRegion = LiveRegionMode.Polite }
                .pointerInput(data) { detectVerticalDragGestures { _, drag -> if (drag < -6f) data.dismiss() } }
        ) {
            Text(data.visuals.message, Modifier.padding(horizontal = 20.dp, vertical = 12.dp), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    }
}

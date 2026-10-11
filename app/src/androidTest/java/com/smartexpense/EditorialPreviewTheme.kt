package com.smartexpense

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.activity.compose.LocalActivity
import com.smartexpense.ui.*

/** Match the production Activity's light system bars and safe drawing insets in isolated fixtures. */
@Composable fun EditorialPreviewTheme(content:@Composable ()->Unit) {
    val activity=LocalActivity.current as? ComponentActivity
    SideEffect {activity?.enableEdgeToEdge(statusBarStyle=SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT),navigationBarStyle=SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT))}
    TrackerTheme {Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),color=Tokens.background) {content()}}
}

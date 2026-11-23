package com.smartexpense

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.activity.compose.LocalActivity
import androidx.test.platform.app.InstrumentationRegistry
import com.smartexpense.ui.*

/** Run instrumented tests with -e tallyDark true to render fixtures in the dark theme. */
val previewDark: Boolean get() = InstrumentationRegistry.getArguments().getString("tallyDark") == "true"

/** Match the production Activity's system bars and safe drawing insets in isolated fixtures. */
@Composable fun EditorialPreviewTheme(content:@Composable ()->Unit) {
    val activity=LocalActivity.current as? ComponentActivity
    val dark=previewDark
    val bars=if(dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT)
    SideEffect {activity?.enableEdgeToEdge(statusBarStyle=bars,navigationBarStyle=bars)}
    TrackerTheme(darkTheme=dark) {Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),color=Tokens.background) {content()}}
}

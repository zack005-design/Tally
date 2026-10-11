package com.smartexpense
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import com.smartexpense.ui.*
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
@AndroidEntryPoint class MainActivity:ComponentActivity() {
    private val model:AppViewModel by viewModels()
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState);enableEdgeToEdge(statusBarStyle=SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT),navigationBarStyle=SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT));setContent { TrackerTheme { TrackerApp(model) } }
        lifecycleScope.launch {repeatOnLifecycle(Lifecycle.State.STARTED) {
            var seen:java.time.LocalDate?=null
            while(true) {
                val current=java.time.LocalDate.now()
                if(seen!=current && !model.state.value.loading) {model.refreshWallet();seen=current}
                delay(30000)
            }
        }}
    }
}


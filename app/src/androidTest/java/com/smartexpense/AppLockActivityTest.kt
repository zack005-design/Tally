package com.smartexpense

import android.content.Context
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class AppLockActivityTest {
    private fun waitLocked() {
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline=android.os.SystemClock.uptimeMillis()+10000
        while(android.os.SystemClock.uptimeMillis()<deadline) {
            if(contains(automation.rootInActiveWindow,"Tally is locked")) return
            android.os.SystemClock.sleep(50)
        }
        throw AssertionError("Locked cold-start surface did not become accessible")
    }
    private fun contains(node:android.view.accessibility.AccessibilityNodeInfo?,text:String):Boolean {
        if(node==null) return false
        if(node.text?.toString()?.contains(text)==true) return true
        return (0 until node.childCount).any {contains(node.getChild(it),text)}
    }
    @Test fun coldStartAndBackgroundRequireAuthenticationAndProtectWindow() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val prefs=context.getSharedPreferences("device-auth-lock",Context.MODE_PRIVATE)
        val present=prefs.contains("enabled");val old=prefs.getBoolean("enabled",false)
        assertTrue(prefs.edit().putBoolean("enabled",true).commit())
        try {
            ActivityScenario.launch(MainActivity::class.java).use {scenario->
                val instrumentation=InstrumentationRegistry.getInstrumentation()
                instrumentation.waitForIdleSync()
                scenario.onActivity {assertTrue(it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0)}
                waitLocked()
                scenario.moveToState(Lifecycle.State.CREATED);scenario.moveToState(Lifecycle.State.RESUMED)
                instrumentation.waitForIdleSync()
                waitLocked()
            }
        } finally {val editor=prefs.edit();if(present) editor.putBoolean("enabled",old) else editor.remove("enabled");assertTrue(editor.commit())}
    }
}

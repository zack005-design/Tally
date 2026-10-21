package com.smartexpense

import android.app.KeyguardManager
import android.content.Context
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.*
import org.junit.Test

/** Temporary credential fixture on an unsecured emulator only, removed in finally. */
class AppLockSystemAuthTest {
    private val automation get()=InstrumentationRegistry.getInstrumentation().uiAutomation
    private fun shell(command:String)=ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use {it.readBytes().toString(Charsets.UTF_8)}
    private fun find(node:AccessibilityNodeInfo?,predicate:(AccessibilityNodeInfo)->Boolean):AccessibilityNodeInfo? {
        if(node==null) return null
        if(predicate(node)) return node
        for(i in 0 until node.childCount) find(node.getChild(i),predicate)?.let {return it}
        return null
    }
    private fun waitNode(predicate:(AccessibilityNodeInfo)->Boolean):AccessibilityNodeInfo {
        val end=SystemClock.uptimeMillis()+10000
        while(SystemClock.uptimeMillis()<end) {find(automation.rootInActiveWindow,predicate)?.let {return it};SystemClock.sleep(100)}
        fun describe(node:AccessibilityNodeInfo?,depth:Int=0) {
            if(node==null || depth>15) return
            if(node.packageName?.toString() in listOf("com.android.settings","com.android.systemui")) android.util.Log.i("AuthFixture","${node.viewIdResourceName} ${node.className} focus=${node.isFocused} editable=${node.isEditable} text=${if(node.isPassword) "<password ${node.text?.length}>" else node.text} error=${node.error}")
            for(i in 0 until node.childCount) describe(node.getChild(i),depth+1)
        }
        describe(automation.rootInActiveWindow)
        throw AssertionError("Expected system authentication UI was not visible")
    }
    private fun waitText(text:String)=waitNode {it.text?.toString()?.contains(text)==true}
    private fun clickText(text:String) {
        var node=waitText(text)
        while(!node.isClickable) node=node.parent?:throw AssertionError("No clickable parent for $text")
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }
    @Test fun deviceCredentialSuccessCancelAndBackgroundUseRealSystemResult() {
        assumeTrue(android.os.Build.VERSION.SDK_INT>=30)
        val context=ApplicationProvider.getApplicationContext<Context>()
        assumeTrue(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk"))
        val keyguard=context.getSystemService(KeyguardManager::class.java);assumeFalse(keyguard.isDeviceSecure)
        val prefs=context.getSharedPreferences("device-auth-lock",Context.MODE_PRIVATE)
        val present=prefs.contains("enabled");val old=prefs.getBoolean("enabled",false)
        val pin="410503";var created=false
        try {
            val response=shell("locksettings set-pin $pin");created=keyguard.isDeviceSecure
            assertTrue("Temporary emulator credential was not created: $response",created)
            assertTrue(prefs.edit().putBoolean("enabled",true).commit())
            ActivityScenario.launch(MainActivity::class.java).use {scenario->
                waitText("Tally is locked");clickText("Use device PIN / pattern / password")
                automation.waitForIdle(500,5000)
                val input=waitNode {it.isEditable && it.isPassword}
                assertTrue(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply {putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,pin)}))
                automation.waitForIdle(500,5000)
                val readyInput=waitNode {it.isEditable && it.isPassword}
                assertTrue("System credential field did not accept its IME submit action",readyInput.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id))
                waitNode {it.packageName?.toString()==context.packageName && (it.text?.toString()?.contains("Welcome to Tally")==true || it.text?.toString()?.contains("Today")==true)}
                scenario.onActivity {assertTrue(it.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE!=0)}
                scenario.moveToState(Lifecycle.State.CREATED);scenario.moveToState(Lifecycle.State.RESUMED)
                waitText("Tally is locked");clickText("Use device PIN / pattern / password")
                waitNode {it.isEditable && it.isPassword};shell("input keyevent 4")
                val settle=SystemClock.uptimeMillis()+1500
                while(SystemClock.uptimeMillis()<settle && find(automation.rootInActiveWindow) {it.isEditable && it.isPassword}!=null) SystemClock.sleep(50)
                if(find(automation.rootInActiveWindow) {it.isEditable && it.isPassword}!=null) shell("input keyevent 4")
                waitText("Tally is locked")
                assertNull(find(automation.rootInActiveWindow) {it.text?.toString()=="Today"})
            }
        } finally {
            val edit=prefs.edit();if(present) edit.putBoolean("enabled",old) else edit.remove("enabled");assertTrue(edit.commit())
            if(created) {val cleared=shell("locksettings clear --old $pin");assertFalse("Temporary emulator credential cleanup failed: $cleared",keyguard.isDeviceSecure)}
        }
    }
}


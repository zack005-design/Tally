package com.smartexpense.data

import android.app.Activity
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import com.smartexpense.domain.AppLockGate

data class DeviceLockView(val enabled:Boolean,val unlocked:Boolean,val busy:Boolean=false,val message:String?=null,val credentialAvailable:Boolean=false)

/** The Android authentication result is the sole source of an unlock grant. */
class DeviceLockController(private val activity:ComponentActivity) {
    private val preferences=activity.getSharedPreferences("device-auth-lock",android.content.Context.MODE_PRIVATE)
    private val keyguard=activity.getSystemService(KeyguardManager::class.java)
    private val gate=AppLockGate(preferences.getBoolean("enabled",false))
    var view by mutableStateOf(DeviceLockView(gate.enabled,gate.canShowLedger,credentialAvailable=keyguard.isDeviceSecure))
        private set
    private var token:Long?=null
    private var requestedSetting:Boolean?=null
    private var cancellation:CancellationSignal?=null
    private var credentialActivity=false
    private val credential=activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {result->
        credentialActivity=false;finish(result.resultCode==Activity.RESULT_OK,if(result.resultCode==Activity.RESULT_OK) null else "Tally stays locked until you unlock it.")
    }
    init {protectWindow()}
    private fun protectWindow() {
        if(gate.enabled) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    private fun update(busy:Boolean=false,message:String?=null) {protectWindow();view=DeviceLockView(gate.enabled,gate.canShowLedger,busy,message,keyguard.isDeviceSecure)}
    fun requestSetting(enabled:Boolean) {if(!view.busy) authenticate(true,enabled)}
    fun unlock(credentialOnly:Boolean=false) {if(!view.busy) authenticate(credentialOnly,null)}
    private fun authenticate(credentialOnly:Boolean,setting:Boolean?) {
        if(!keyguard.isDeviceSecure) {update(message="Set a device PIN, pattern or password in Android settings first.");return}
        token=gate.beginAuthentication();requestedSetting=setting;update(busy=true)
        try {
            // Android 11+: one modern system sheet over Tally's own lock screen that offers fingerprint, face and the
            // PIN pad together, instead of the old full-screen "Confirm your PIN" page.
            if(Build.VERSION.SDK_INT>=30) modernPrompt(credentialOnly)
            else if(Build.VERSION.SDK_INT>=28 && !credentialOnly) biometric() else openCredential()
        } catch(e:Exception) {finish(false,"Authentication is unavailable. Try your device credential.")}
    }
    @androidx.annotation.RequiresApi(30)
    private fun modernPrompt(credentialOnly:Boolean) {
        val authToken=requireNotNull(token)
        val kinds=android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL or
            (if(credentialOnly) 0 else android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG)
        val prompt=BiometricPrompt.Builder(activity).setTitle("Unlock Tally")
            .setSubtitle(if(credentialOnly) "Enter your phone's PIN, pattern or password" else "Use your fingerprint, face or screen lock")
            .setAllowedAuthenticators(kinds).build()
        val signal=CancellationSignal();cancellation=signal
        prompt.authenticate(signal,activity.mainExecutor,object:BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result:BiometricPrompt.AuthenticationResult) {finish(true,null,authToken)}
            override fun onAuthenticationError(code:Int,message:CharSequence) {
                finish(false,if(code==BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED || code==BiometricPrompt.BIOMETRIC_ERROR_CANCELED) "Tally stays locked until you unlock it." else message.toString(),authToken)
            }
        })
    }
    @androidx.annotation.RequiresApi(28)
    private fun biometric() {
        val authToken=requireNotNull(token)
        val builder=BiometricPrompt.Builder(activity).setTitle("Unlock Tally").setSubtitle("Use your fingerprint or screen lock")
        if(Build.VERSION.SDK_INT>=30) builder.setAllowedAuthenticators(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG)
        builder.setNegativeButton("Use PIN",activity.mainExecutor) {_,_->if(token==authToken) openCredential()}
        val signal=CancellationSignal();cancellation=signal
        builder.build().authenticate(signal,activity.mainExecutor,object:BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result:BiometricPrompt.AuthenticationResult) {finish(true,null,authToken)}
            override fun onAuthenticationError(code:Int,message:CharSequence) {
                // API28's negative button hands off to the separately registered credential result.
                if(!credentialActivity) finish(false,"Tally stays locked. Try again, or use your PIN, pattern or password.",authToken)
            }
        })
    }
    @Suppress("DEPRECATION")
    private fun openCredential() {
        val intent=keyguard.createConfirmDeviceCredentialIntent("Unlock Tally","Enter your phone's PIN, pattern or password")
        if(intent==null) {finish(false,"No device credential is configured.");return}
        credentialActivity=true;credential.launch(intent)
    }
    // KTX edit(commit=true) discards commit's Boolean; lock changes must detect failed writes.
    @android.annotation.SuppressLint("UseKtx")
    private fun finish(success:Boolean,message:String?,expected:Long?=token) {
        val current=token?:return
        if(expected!=current) return
        token=null;cancellation=null
        val accepted=gate.finishAuthentication(current,success)
        val setting=requestedSetting;requestedSetting=null
        if(accepted && setting!=null) {
            if(!preferences.edit().putBoolean("enabled",setting).commit()) {update(message="Could not save the device lock setting. Try again.");return}
            gate.configure(setting)
        }
        update(message=if(accepted) null else message)
    }
    fun background() {
        gate.background(credentialActivity)
        if(!credentialActivity) {token=null;requestedSetting=null;val signal=cancellation;cancellation=null;signal?.cancel()}
        update(busy=credentialActivity)
    }
    fun destroy() {token=null;cancellation?.cancel();cancellation=null}
}

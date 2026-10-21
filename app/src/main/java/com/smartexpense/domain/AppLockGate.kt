package com.smartexpense.domain

/** Authentication grants are process-local and invalidated when the app leaves the foreground. */
class AppLockGate(enabled:Boolean) {
    var enabled:Boolean=enabled
        private set
    private var authenticated=false
    private var sequence=0L
    private var pending:Long?=null
    val canShowLedger:Boolean get()=!enabled || authenticated
    fun beginAuthentication():Long {sequence++;pending=sequence;return sequence}
    fun finishAuthentication(token:Long,success:Boolean):Boolean {
        if(pending!=token) return false
        pending=null;authenticated=success;return success
    }
    fun background(credentialActivity:Boolean) {
        authenticated=false
        if(!credentialActivity) pending=null
    }
    fun configure(enabled:Boolean) {this.enabled=enabled}
}

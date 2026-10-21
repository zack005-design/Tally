package com.smartexpense.domain
import org.junit.Assert.*
import org.junit.Test

class AppLockGateTest {
    @Test fun canceledAuthenticationAndStaleSuccessCannotExposeLedger() {
        val gate=AppLockGate(true)
        assertFalse(gate.canShowLedger)
        val first=gate.beginAuthentication();gate.finishAuthentication(first,false)
        assertFalse(gate.canShowLedger)
        val second=gate.beginAuthentication();gate.background(false)
        gate.finishAuthentication(second,true);assertFalse(gate.canShowLedger)
        val third=gate.beginAuthentication();gate.finishAuthentication(third,true)
        assertTrue(gate.canShowLedger)
        gate.background(false);assertFalse(gate.canShowLedger)
    }
    @Test fun credentialActivityReturnMustStillConfirmSuccess() {
        val gate=AppLockGate(true);val token=gate.beginAuthentication()
        gate.background(true);assertFalse(gate.canShowLedger)
        gate.finishAuthentication(token,true);assertTrue(gate.canShowLedger)
    }
}


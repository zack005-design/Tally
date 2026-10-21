package com.smartexpense.domain

import org.junit.Assert.*
import org.junit.Test

class FirstBalanceSetupTest {
    @Test fun freshInstallStartsWithBalances() {
        assertEquals("balance-setup", launchDestination(Snapshot()))
    }
    @Test fun configuredWalletOpensTodayEvenWithoutOldWelcomeFlag() {
        assertEquals("home", launchDestination(Snapshot(walletSources=WalletSources("w","g","2026-10"))))
    }
    @Test fun existingUsersKeepTheirAccountsAndGoToToday() {
        assertEquals("home", launchDestination(Snapshot(localSettings=LocalSettings(welcomeDone=true))))
    }
}

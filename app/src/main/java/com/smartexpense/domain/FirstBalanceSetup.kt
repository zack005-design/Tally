package com.smartexpense.domain

/** Existing installations retain their accounts; new installations set balances first. */
fun launchDestination(snapshot:Snapshot):String =
    if(snapshot.walletSources==null && !snapshot.localSettings.welcomeDone) "balance-setup" else "home"

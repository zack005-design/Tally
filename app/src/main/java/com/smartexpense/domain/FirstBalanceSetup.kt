package com.smartexpense.domain

/** Existing installations retain their accounts; new installations start with onboarding. */
fun launchDestination(snapshot:Snapshot):String =
    if(snapshot.walletSources==null && !snapshot.localSettings.welcomeDone) "onboarding" else "home"

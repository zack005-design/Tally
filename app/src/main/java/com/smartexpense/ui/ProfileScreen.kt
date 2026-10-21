package com.smartexpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.smartexpense.R
import com.smartexpense.domain.*

@Composable fun ProfileScreen(settings:LocalSettings,busy:Boolean,onSave:(String,String,()->Unit)->Unit,onWelcome:()->Unit,onWallet:()->Unit={}) {
    var name by rememberSaveable {mutableStateOf(settings.name)}
    var avatar by rememberSaveable {mutableStateOf(settings.avatar)}
    var saved by rememberSaveable {mutableStateOf(false)}
    Page("Your profile") {
        item {Column(Modifier.fillMaxWidth().padding(vertical=16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            when(avatar) {
                "initials"->InitialAvatar(name.ifBlank {"You"},Modifier.size(76.dp))
                "leaf"->Surface(shape=androidx.compose.foundation.shape.CircleShape,color=Tokens.secondarySurface) {Box(Modifier.size(76.dp),contentAlignment=Alignment.Center) {Text("❧",style=MaterialTheme.typography.displaySmall,color=Tokens.accent)}}
                else->ReferenceIcon(if(avatar=="wallet") R.drawable.ic_wallet_outline else R.drawable.ic_star_outline,size=76.dp)
            }
        }}
        item {GroupedCard {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Field("Your name",name,{name=it;saved=false})
            Choice("Profile avatar",avatar,listOf("initials" to "Initials","wallet" to "Wallet","leaf" to "Leaf","star" to "Star")) {avatar=it;saved=false}
        }}}
        item {ReferenceInfo("No email or password needed. Your profile is included in local backups.")}
        item {Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            EditorialPrimaryButton("Save profile",name.trim().isNotEmpty() && name.trim().length<=100 && !busy) {onSave(name,avatar) {saved=true}}
            if(saved) Text("Profile saved",style=MaterialTheme.typography.bodySmall)
        }}
        item {GroupedCard {
            GroupedRow("See welcome screens again",R.drawable.ic_document_outline,onClick=onWelcome)
            GroupedDivider()
            GroupedRow("Set up Wallet & GPay",R.drawable.ic_wallet_outline,onClick=onWallet)
        }}
    }
}

@Composable fun WelcomeScreen(busy:Boolean,onFinish:(Boolean)->Unit) {
    var step by rememberSaveable {mutableStateOf(0)}
    val title=listOf("Make room for every day","Know what you have","Share meals, settle clearly")
    val body=listOf("Record spending and daily notes. Your month-end review keeps available money, spending and cash left in view.","Enter your Wallet and GPay balances yourself. Monthly GPay resets are optional and follow dates you choose.","Enter what everyone paid and their shares. Record repayments when they happen; friends' debts are separate from cash.")
    Page("Welcome to Tally","Your money, kept on your device") {
        item {Panel {
            Text("${step+1} / 3",color=Tokens.muted,style=MaterialTheme.typography.labelLarge)
            Text(title[step],style=MaterialTheme.typography.headlineLarge)
            Text(body[step],style=MaterialTheme.typography.bodyLarge)
            Text("Fully local · no bank connection · no syncing · no subscription",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
            EditorialPrimaryButton(if(step==2) "Create my profile" else "Next",!busy) {if(step==2) onFinish(true) else step++}
            if(step>0) OutlinedButton({step--},Modifier.fillMaxWidth(),enabled=!busy) {Text("Back")}
            TextButton({onFinish(false)},enabled=!busy) {Text("Skip welcome")}
        }}
    }
}


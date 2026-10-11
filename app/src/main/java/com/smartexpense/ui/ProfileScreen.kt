package com.smartexpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.smartexpense.domain.*

@Composable fun ProfileScreen(settings:LocalSettings,busy:Boolean,onSave:(String,String,()->Unit)->Unit,onWelcome:()->Unit) {
    var name by rememberSaveable {mutableStateOf(settings.name)}
    var avatar by rememberSaveable {mutableStateOf(settings.avatar)}
    var saved by rememberSaveable {mutableStateOf(false)}
    Page("Your profile","Personal to you, stored on this device") {
        item {Panel {
            Text(when(avatar) {"wallet"->"▣";"leaf"->"❧";"star"->"☆";else->name.trim().take(2).uppercase().ifBlank {"You"}},style=MaterialTheme.typography.displaySmall)
            Field("Your name",name,{name=it;saved=false})
            Choice("Profile avatar",avatar,listOf("initials" to "Initials","wallet" to "Wallet","leaf" to "Leaf","star" to "Star")) {avatar=it;saved=false}
            Text("No email or password needed. Your profile is included in local backups.",color=Tokens.muted)
            EditorialPrimaryButton("Save profile",name.trim().isNotEmpty() && name.trim().length<=100 && !busy) {onSave(name,avatar) {saved=true}}
            if(saved) Text("Profile saved",style=MaterialTheme.typography.bodySmall)
        }}
        item {OutlinedButton(onWelcome,Modifier.fillMaxWidth()) {Text("See welcome screens again")}}
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

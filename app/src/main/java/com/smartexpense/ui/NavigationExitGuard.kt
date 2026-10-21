package com.smartexpense.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** Lets the current editor resolve unsaved work before navigation executes. */
class NavigationExitGuard {
    private var owner:Any?=null
    private var request:((()->Unit)->Unit)?=null
    fun install(owner:Any,request:(()->Unit)->Unit) {this.owner=owner;this.request=request}
    fun clear(owner:Any) {if(this.owner===owner) {this.owner=null;request=null}}
    fun navigate(action:()->Unit) {request?.invoke(action)?:action()}
}
val LocalNavigationExitGuard=staticCompositionLocalOf<NavigationExitGuard?> {null}

package com.smartexpense.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.smartexpense.R

/**
 * Home-screen icons the user can choose in Appearance. Each one is an activity-alias in the
 * manifest that opens MainActivity; exactly one alias is enabled at a time.
 */
enum class AppIcon(val key: String, val label: String, val alias: String, val background: Int, val foreground: Int) {
    Tally("tally", "Tally", "com.smartexpense.icon.Tally", R.drawable.ic_launcher_bg_tally, R.drawable.ic_launcher_fg_tally),
    LetterT("lettert", "Lowercase t", "com.smartexpense.icon.LetterT", R.drawable.ic_launcher_bg_lettert, R.drawable.ic_launcher_fg_lettert),
    Wallet("wallet", "Wallet", "com.smartexpense.icon.Wallet", R.drawable.ic_launcher_bg_wallet, R.drawable.ic_launcher_fg_wallet),
    Rupee("rupee", "Rupee", "com.smartexpense.icon.Rupee", R.drawable.ic_launcher_bg_rupee, R.drawable.ic_launcher_fg_rupee),
    Coins("coins", "Coins", "com.smartexpense.icon.Coins", R.drawable.ic_launcher_bg_coins, R.drawable.ic_launcher_fg_coins),
    Ledger("ledger", "Ledger", "com.smartexpense.icon.Ledger", R.drawable.ic_launcher_bg_ledger, R.drawable.ic_launcher_fg_ledger),
    Split("split", "Split", "com.smartexpense.icon.Split", R.drawable.ic_launcher_bg_split, R.drawable.ic_launcher_fg_split),
    Ask("ask", "Ask", "com.smartexpense.icon.Ask", R.drawable.ic_launcher_bg_ask, R.drawable.ic_launcher_fg_ask),
    Receipt("receipt", "Receipt", "com.smartexpense.icon.Receipt", R.drawable.ic_launcher_bg_receipt, R.drawable.ic_launcher_fg_receipt),
    Classic("classic", "Classic", "com.smartexpense.icon.Classic", R.drawable.ic_launcher_bg_classic, R.drawable.ic_launcher_fg_classic);

    companion object { val Default = Tally }
}

object AppIcons {
    private fun component(context: Context, icon: AppIcon) = ComponentName(context.packageName, icon.alias)

    private fun isEnabled(context: Context, icon: AppIcon): Boolean =
        when (context.packageManager.getComponentEnabledSetting(component(context, icon))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            else -> icon == AppIcon.Default
        }

    fun current(context: Context): AppIcon = AppIcon.entries.firstOrNull { isEnabled(context, it) } ?: AppIcon.Default

    /** Enables the chosen launcher entry first, then disables the rest, so the app never has none. */
    fun select(context: Context, icon: AppIcon) {
        val packages = context.packageManager
        packages.setComponentEnabledSetting(component(context, icon), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        AppIcon.entries.filter { it != icon }.forEach {
            packages.setComponentEnabledSetting(component(context, it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
}

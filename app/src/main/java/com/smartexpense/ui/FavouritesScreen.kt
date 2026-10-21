package com.smartexpense.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.smartexpense.R
import com.smartexpense.domain.*
import com.smartexpense.widget.QuickExpenseWidget
import java.util.UUID
import androidx.compose.ui.res.painterResource

@Composable fun FavouritesScreen(s:Snapshot,busy:Boolean,onUse:(FavouriteExpense)->Unit,onSave:(FavouriteExpense,()->Unit)->Unit,onDelete:(String)->Unit) {
    val context=LocalContext.current
    var editing by rememberSaveable {mutableStateOf<String?>(null)}
    var description by rememberSaveable {mutableStateOf("")}
    var amount by rememberSaveable {mutableStateOf("")}
    var account by rememberSaveable {mutableStateOf("")}
    var category by rememberSaveable {mutableStateOf("")}
    var remove by rememberSaveable {mutableStateOf<String?>(null)}
    var widgetMessage by rememberSaveable {mutableStateOf<String?>(null)}
    val widgetFavourites=s.favourites.filter {value->
        s.accounts.any {it.id==value.accountId && !it.archived} && (value.categoryId==null || s.categories.any {it.id==value.categoryId && !it.archived})
    }.take(3)
    fun edit(value:FavouriteExpense?) {
        editing=value?.id?:UUID.randomUUID().toString();description=value?.description.orEmpty()
        amount=value?.amount?.let(Money::input).orEmpty();account=value?.accountId.orEmpty();category=value?.categoryId.orEmpty()
    }
    Page("Favourites & widget") {
        item {Text("Favourites",style=MaterialTheme.typography.titleMedium)}
        if(s.favourites.isEmpty()) item {EmptyLedger("Keep your regular expenses handy","Save up to six favourites, such as lunch or your commute.")}
        if(s.favourites.isNotEmpty()) item {GroupedCard {s.favourites.forEachIndexed {index,value->
            val active=s.accounts.any {it.id==value.accountId && !it.archived} && (value.categoryId==null || s.categories.any {it.id==value.categoryId && !it.archived})
            val large=LocalDensity.current.fontScale>1.3f || referenceMoney(value.amount).length>14
            val valueCategory=s.categories.find {it.id==value.categoryId}
            val icon=referenceSymbol(value.description,QuickExpenseWidget.shortcutIcon(valueCategory?.name.orEmpty(),valueCategory?.icon.orEmpty()))
            Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                ReferenceIcon(icon,if(icon==R.drawable.ic_food) ReferenceTone.Peach else ReferenceTone.Lavender)
                Column(Modifier.weight(1f)) {
                    TextButton({edit(value)},enabled=!busy,contentPadding=PaddingValues(0.dp)) {Text(value.description,color=Tokens.text,style=MaterialTheme.typography.titleSmall)}
                    Text(if(active) s.accounts.find {it.id==value.accountId}!!.name else "Account or category unavailable",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
                    if(large) Text(referenceMoney(value.amount),style=MaterialTheme.typography.bodyMedium)
                }
                if(!large) Text(referenceMoney(value.amount),style=MaterialTheme.typography.titleSmall)
                FilledTonalButton({onUse(value)},enabled=!busy) {Text("Use")}
            }
            if(index<s.favourites.lastIndex) GroupedDivider()
        }}}
        item {ReferenceAction("Add favourite · ${s.favourites.size}/6",R.drawable.ic_add,!busy && s.favourites.size<6) {edit(null)}}
        item {GroupedCard {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {Text("Home-screen widget",style=MaterialTheme.typography.titleMedium);Text("PREVIEW",color=Tokens.muted,style=MaterialTheme.typography.labelSmall)}
            GroupedCard {Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("tally",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                    Icon(painterResource(R.drawable.ic_add),null,Modifier.size(22.dp),tint=Tokens.accent)
                }
                Row(Modifier.heightIn(min=48.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_compose),null,Modifier.size(22.dp),tint=Tokens.accent)
                    Text("Add expense",style=MaterialTheme.typography.bodyMedium,color=Tokens.accent)
                }
                widgetFavourites.forEach {value->
                    val valueCategory=s.categories.find {it.id==value.categoryId}
                    Row(Modifier.heightIn(min=48.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                        Icon(painterResource(QuickExpenseWidget.shortcutIcon(valueCategory?.name.orEmpty(),valueCategory?.icon.orEmpty())),null,Modifier.size(22.dp),tint=Tokens.accent)
                        Text(value.description,style=MaterialTheme.typography.bodyMedium)
                    }
                }
            }}
            OutlinedButton({
                val manager=context.getSystemService(AppWidgetManager::class.java)
                widgetMessage=try {
                    if(manager.isRequestPinAppWidgetSupported && manager.requestPinAppWidget(ComponentName(context,QuickExpenseWidget::class.java),null,null)) "Finish adding the widget in your launcher's prompt."
                    else "Your launcher does not support an in-app pin request. Long-press the home screen, choose Widgets, then Tally."
                } catch(_:IllegalStateException) {"Return to the foreground and try again, or add Tally through your launcher's Widgets menu."}
            },Modifier.fillMaxWidth()) {Text("Add home-screen widget")}
            widgetMessage?.let {Text(it,color=Tokens.muted)}
        }}}
        item {ReferenceInfo("The widget shows up to three available favourites by name and icon. Opens a draft. Save to record. No amounts, balances or history appear. App lock still applies. You can also long-press Tally's app icon and choose Add expense.")}
    }
    val id=editing
    if(id!=null) ManagementEditorSurface("Favourite expense",{if(!busy) editing=null}) {
        Field("Description",description,{description=it})
        Field("Amount (₹)",amount,{amount=it},true)
        Choice("Account",account,s.accounts.filterNot {it.archived}.map {it.id to it.name}) {account=it}
        Choice("Category",category,listOf("" to "Uncategorised")+s.categories.filterNot {it.archived}.map {it.id to it.name}) {category=it}
        val value=runCatching {
            require(s.accounts.any {it.id==account && !it.archived}) {"Choose an active account"}
            require(category.isEmpty() || s.categories.any {it.id==category && !it.archived}) {"Choose an active category"}
            FavouriteExpense(id,description.trim(),Money.parse(amount),account,category.ifEmpty {null})
        }
        value.exceptionOrNull()?.let {Text(it.message?:"Check the template details",color=Tokens.muted)}
        EditorialPrimaryButton("Save favourite",!busy && value.isSuccess) {onSave(value.getOrThrow()) {editing=null}}
        if(s.favourites.any {it.id==id}) TextButton({editing=null;remove=id},enabled=!busy) {Text("Remove favourite")}
    }
    remove?.let {key->EditorialConfirmSheet("Remove favourite?","This removes the template. Saved expenses stay in your ledger.","Remove",{remove=null},enabled=!busy) {onDelete(key);remove=null}}
}

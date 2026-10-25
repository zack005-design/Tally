package com.smartexpense.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.smartexpense.MainActivity
import com.smartexpense.R
import com.smartexpense.data.FinanceRepository
import com.smartexpense.data.WidgetFavourite
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetRepositoryEntryPoint { fun repository():FinanceRepository }

/** Names and category icons only. The secure app resolves a shortcut ID into a draft. */
class QuickExpenseWidget:AppWidgetProvider() {
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray) {
        if(ids.isEmpty()) return
        fallback(context,manager,ids)
        val pending=goAsync()
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            try {
                val completed=withTimeoutOrNull(7500) {
                    val repository=EntryPointAccessors.fromApplication(context.applicationContext,WidgetRepositoryEntryPoint::class.java).repository()
                    render(context,manager,ids,repository.widgetFavourites())
                    true
                }
                if(completed==null) {
                    Log.w("QuickExpenseWidget","Widget update timed out; showing generic shortcut")
                    fallback(context,manager,ids)
                }
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) {
                Log.w("QuickExpenseWidget","Widget update failed; showing generic shortcut",e)
                fallback(context,manager,ids)
            } finally {pending.finish()}
        }
    }
    companion object {
        const val ACTION_FAVOURITE="com.smartexpense.QUICK_FAVOURITE"
        const val EXTRA_FAVOURITE_ID="favourite_id"
        fun launchIntent(context:Context)=Intent(context,MainActivity::class.java).setAction("com.smartexpense.QUICK_ADD")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        fun favouriteIntent(context:Context,id:String)=Intent(context,MainActivity::class.java).setAction(ACTION_FAVOURITE)
            .setData(Uri.Builder().scheme("tally").authority("favourite").appendPath(id).build())
            .putExtra(EXTRA_FAVOURITE_ID,id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        /** Shares icon mapping with the in-app preview; amounts never enter RemoteViews. */
        fun shortcutIcon(categoryName:String,categoryIcon:String):Int = when {
            categoryIcon=="food" || categoryName.lowercase() in listOf("food","food & drinks") -> R.drawable.ic_food
            categoryIcon=="transport" || categoryName.lowercase() in listOf("travel","transport") -> R.drawable.ic_transit
            categoryIcon=="shopping" || categoryName.lowercase() in listOf("shopping","groceries") -> R.drawable.ic_basket
            categoryName.equals("Bills",true) -> R.drawable.ic_document_outline
            else -> R.drawable.ic_activity
        }
        suspend fun refresh(context:Context,repository:FinanceRepository) = withContext(Dispatchers.IO) {
            val manager=AppWidgetManager.getInstance(context)
            val ids=manager.getAppWidgetIds(ComponentName(context,QuickExpenseWidget::class.java))
            if(ids.isEmpty()) return@withContext
            val shortcuts=try {withTimeoutOrNull(5000) {repository.widgetFavourites()}?:run {
                Log.w("QuickExpenseWidget","Widget refresh timed out; showing generic shortcut")
                emptyList()
            }}
                catch(e:CancellationException) {throw e}
                catch(e:Exception) {Log.w("QuickExpenseWidget","Widget refresh failed; showing generic shortcut",e);emptyList()}
            render(context,manager,ids,shortcuts)
        }
        private fun fallback(context:Context,manager:AppWidgetManager,ids:IntArray) {
            try {render(context,manager,ids,emptyList())}
            catch(e:CancellationException) {throw e}
            catch(e:Exception) {Log.w("QuickExpenseWidget","Could not render generic widget shortcut",e)}
        }
        private fun render(context:Context,manager:AppWidgetManager,ids:IntArray,shortcuts:List<WidgetFavourite>) {
            val flags=PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val open=PendingIntent.getActivity(context,406,launchIntent(context),flags)
            val rows=intArrayOf(R.id.widget_favourite_1,R.id.widget_favourite_2,R.id.widget_favourite_3)
            ids.forEach {id->
                val views=RemoteViews(context.packageName,R.layout.quick_expense_widget)
                views.setOnClickPendingIntent(R.id.quick_expense_open,open)
                views.setOnClickPendingIntent(R.id.widget_add_expense,open)
                rows.indices.forEach {index->
                    val shortcut=shortcuts.getOrNull(index)
                    views.setViewVisibility(rows[index],if(shortcut==null) View.GONE else View.VISIBLE)
                    if(shortcut!=null) {
                        views.setTextViewText(rows[index],shortcut.name)
                        views.setTextViewCompoundDrawablesRelative(rows[index],shortcutIcon(shortcut.categoryName,shortcut.categoryIcon),0,0,0)
                        views.setOnClickPendingIntent(rows[index],PendingIntent.getActivity(context,407,favouriteIntent(context,shortcut.id),flags))
                        views.setContentDescription(rows[index],"Open favourite draft: "+shortcut.name)
                    }
                }
                manager.updateAppWidget(id,views)
            }
        }
    }
}

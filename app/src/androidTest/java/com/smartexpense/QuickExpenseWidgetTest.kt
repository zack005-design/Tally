package com.smartexpense

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.smartexpense.widget.QuickExpenseWidget
import androidx.room.Room
import com.smartexpense.data.*
import com.smartexpense.domain.FavouriteExpense
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class QuickExpenseWidgetTest {
    @Test fun launcherIntentIsExplicitAndContainsNoFinancialData() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val intent=QuickExpenseWidget.launchIntent(context)
        assertEquals(MainActivity::class.java.name,intent.component!!.className)
        assertEquals(context.packageName,intent.component!!.packageName)
        assertEquals("com.smartexpense.QUICK_ADD",intent.action)
        assertTrue(intent.extras==null || intent.extras!!.isEmpty)
    }
    @Test fun favouriteIntentContainsOnlyStableIdAndNoFinancialPayload() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val first=QuickExpenseWidget.favouriteIntent(context,"coffee")
        val second=QuickExpenseWidget.favouriteIntent(context,"metro")
        assertEquals(MainActivity::class.java.name,first.component!!.className)
        assertEquals(QuickExpenseWidget.ACTION_FAVOURITE,first.action)
        assertEquals(setOf(QuickExpenseWidget.EXTRA_FAVOURITE_ID),first.extras!!.keySet())
        assertEquals("coffee",first.getStringExtra(QuickExpenseWidget.EXTRA_FAVOURITE_ID))
        assertNotEquals(first.data,second.data)
        assertFalse(first.hasExtra("amount"))
        assertFalse(first.hasExtra("description"))
        assertFalse(first.hasExtra("accountId"))
        assertFalse(first.hasExtra("categoryId"))
    }
    @Test fun widgetQueryReturnsOnlyThreeAvailableNameAndIconShortcuts() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val database=Room.inMemoryDatabaseBuilder(context,TrackerDatabase::class.java).build()
        try {
            val dao=database.dao()
            dao.insertAccount(AccountEntity("active","Everyday","Cash"))
            dao.insertAccount(AccountEntity("archived","Old account","Cash",true))
            dao.insertCategory(CategoryEntity("food","Food",0xFFFFDDC8,"food",false))
            dao.insertCategory(CategoryEntity("old","Old category",0xFFFFDDC8,"food",true))
            dao.savePlan(PlanCodec.favourite(FavouriteExpense("0","Unavailable account",100,"archived","food")))
            dao.savePlan(PlanCodec.favourite(FavouriteExpense("1","Unavailable category",100,"active","old")))
            (2..5).forEach {dao.savePlan(PlanCodec.favourite(FavouriteExpense(it.toString(),"Coffee $it",8000,"active","food")))}
            val shortcuts=FinanceRepository(database).widgetFavourites()
            assertEquals(listOf("2","3","4"),shortcuts.map {it.id})
            assertEquals(listOf("Coffee 2","Coffee 3","Coffee 4"),shortcuts.map {it.name})
            assertTrue(shortcuts.all {it.categoryName=="Food" && it.categoryIcon=="food"})
        } finally {database.close()}
    }
}

package com.smartexpense

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.smartexpense.data.*
import com.smartexpense.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ArchivedFavouriteCategoryTest {
    @Test fun favouriteDraftMustReplaceArchivedCategoryBeforeSaving()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),TrackerDatabase::class.java).build()
        try {
            val repo=FinanceRepository(db);repo.initialize();val account=repo.createAccount("Cash","Cash",10000)
            val category=repo.snapshot().categories.first()
            val favourite=FavouriteExpense("coffee","Coffee",100,account,category.id)
            repo.saveFavourite(favourite)
            repo.saveCategory(category.name,category.icon,category.color,category.id,true)
            try {repo.save(favourite.draft(System.currentTimeMillis()));fail("Archived category accepted without review")} catch(_:IllegalArgumentException) {}
            assertEquals(10000L,repo.snapshot().accounts.single().balance)
            repo.save(favourite.copy(categoryId=null).draft(System.currentTimeMillis()))
            assertEquals(9900L,repo.snapshot().accounts.single().balance)
        } finally {db.close()}
    }
}

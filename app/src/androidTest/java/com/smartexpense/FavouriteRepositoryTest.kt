package com.smartexpense

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.smartexpense.data.*
import com.smartexpense.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FavouriteRepositoryTest {
    @Test fun sixFavouriteLimitBackupRoundTripAndArchivedReferences()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),TrackerDatabase::class.java).build()
        try {
            val repo=FinanceRepository(db);repo.initialize();val account=repo.createAccount("Cash","Cash",10000)
            repeat(6) {repo.saveFavourite(FavouriteExpense("f$it","Tea $it",100,account,null))}
            try {repo.saveFavourite(FavouriteExpense("seven","Tea",100,account,null));fail("Seventh accepted")} catch(_:IllegalArgumentException) {}
            repo.saveFavourite(FavouriteExpense("f0","Coffee",200,account,null))
            val saved=repo.snapshot().favourites
            val service=BackupService(db);service.restore(service.export("test".toCharArray()),"test".toCharArray())
            assertEquals(saved,repo.snapshot().favourites)
            assertEquals(10000L,repo.snapshot().accounts.single().balance)
            repo.updateAccount(repo.snapshot().accounts.single().copy(archived=true))
            try {repo.save(repo.snapshot().favourites.first().draft(1000));fail("Archived draft saved")} catch(_:IllegalArgumentException) {}
            assertEquals(10000L,repo.snapshot().accounts.single().balance)
        } finally {db.close()}
    }
}

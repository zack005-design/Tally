package com.smartexpense.data
import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
@Module @InstallIn(SingletonComponent::class)
object AppModule {
    @Provides fun clock():java.time.Clock=java.time.Clock.systemUTC()
    @Provides fun reminderGateway(value:AndroidReminderGateway):ReminderGateway=value
    val migration1To2=object:androidx.room.migration.Migration(1,2) {
        override fun migrate(db:androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS walletSources (id INTEGER NOT NULL PRIMARY KEY, walletId TEXT NOT NULL, gpayId TEXT NOT NULL, lastResetMonth TEXT NOT NULL, FOREIGN KEY(walletId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(gpayId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_walletSources_walletId ON walletSources(walletId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_walletSources_gpayId ON walletSources(gpayId)")
        }
    }
    val migration2To3=object:androidx.room.migration.Migration(2,3) {
        override fun migrate(db:androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS gpaySchedule (id INTEGER NOT NULL PRIMARY KEY, enabled INTEGER NOT NULL, target INTEGER NOT NULL, day INTEGER NOT NULL, startMonth TEXT NOT NULL, nextMonth TEXT NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS resetTargets (transactionId TEXT NOT NULL PRIMARY KEY, target INTEGER NOT NULL, FOREIGN KEY(transactionId) REFERENCES transactions(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("INSERT INTO resetTargets SELECT id,500000 FROM transactions WHERE id LIKE 'gpay-reset:%'")
            db.execSQL("CREATE TABLE IF NOT EXISTS diary (date TEXT NOT NULL PRIMARY KEY, text TEXT NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS localSettings (id INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL, avatar TEXT NOT NULL, welcomeDone INTEGER NOT NULL, lastReviewMonth TEXT NOT NULL)")
            db.execSQL("INSERT INTO localSettings VALUES (1,'','initials',1,'')")
        }
    }
    val migration3To4=object:androidx.room.migration.Migration(3,4) {
        override fun migrate(db:androidx.sqlite.db.SupportSQLiteDatabase) {db.execSQL("CREATE TABLE IF NOT EXISTS localPlans (`key` TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, json TEXT NOT NULL)")}
    }
    val migration4To5=object:androidx.room.migration.Migration(4,5) {
        override fun migrate(db:androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS receipts (transactionId TEXT NOT NULL PRIMARY KEY, mime TEXT NOT NULL, text TEXT NOT NULL, hash TEXT NOT NULL, FOREIGN KEY(transactionId) REFERENCES transactions(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE TABLE IF NOT EXISTS receiptChunks (transactionId TEXT NOT NULL, position INTEGER NOT NULL, bytes BLOB NOT NULL, PRIMARY KEY(transactionId,position), FOREIGN KEY(transactionId) REFERENCES receipts(transactionId) ON UPDATE NO ACTION ON DELETE CASCADE)")
        }
    }
    @Provides @Singleton fun database(@ApplicationContext context:Context):TrackerDatabase = Room.databaseBuilder(context,TrackerDatabase::class.java,"smart-expense.db").addMigrations(migration1To2,migration2To3,migration3To4,migration4To5).build()
}

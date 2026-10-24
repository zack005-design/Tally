package com.smartexpense

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.smartexpense.data.*
import com.smartexpense.domain.BackupEnvelope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class InstallationDataSafetyTest {
    // Build historical fixtures from the committed schemas, then let the real Room
    // database open/validate them. Avoid the test helper's incompatible serializer runtime.
    private fun historicalDatabase(context:Context,name:String,version:Int):android.database.sqlite.SQLiteDatabase {
        val schema=InstrumentationRegistry.getInstrumentation().context.assets
            .open("${TrackerDatabase::class.java.canonicalName}/$version.json")
            .bufferedReader().use {org.json.JSONObject(it.readText()).getJSONObject("database")}
        val path=context.getDatabasePath(name)
        check(path.parentFile!!.isDirectory || path.parentFile!!.mkdirs())
        return android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(path,null).apply {
            try {
                val entities=schema.getJSONArray("entities")
                for(i in 0 until entities.length()) {
                    val table=entities.getJSONObject(i)
                    fun sql(value:String)=value.replace("\${TABLE_NAME}",table.getString("tableName"))
                    execSQL(sql(table.getString("createSql")))
                    val indices=table.optJSONArray("indices")?:org.json.JSONArray()
                    for(j in 0 until indices.length()) execSQL(sql(indices.getJSONObject(j).getString("createSql")))
                }
                val queries=schema.getJSONArray("setupQueries")
                for(i in 0 until queries.length()) execSQL(queries.getString(i))
                this.version=version
                execSQL("PRAGMA foreign_keys=ON")
            } catch(error:Throwable) {close();throw error}
        }
    }

    @Test fun everySupportedSchemaPreservesPopulatedLedgerOnUpgrade() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        for(version in 1..4) {
            val name="migration-safety-$version"
            try {
                historicalDatabase(context,name,version).use {
                    with(it) {
                    execSQL("INSERT INTO accounts VALUES ('cash','Existing cash','Cash',0)")
                    execSQL("INSERT INTO people VALUES ('self','Me','')")
                    execSQL("INSERT INTO people VALUES ('friend','Existing friend','')")
                    execSQL("INSERT INTO transactions VALUES ('opening','ADJUSTMENT','Existing balance',12345,0,NULL,NULL,1,'',0,NULL)")
                    execSQL("INSERT INTO entries VALUES ('entry','opening','cash',12345)")
                    execSQL("INSERT INTO transactions VALUES ('bill','EXPENSE','Existing shared bill',1000,500,NULL,NULL,2,'',0,NULL)")
                    execSQL("INSERT INTO entries VALUES ('bill-entry','bill','cash',-1000)")
                    execSQL("INSERT INTO shares VALUES ('bill','self',500),('bill','friend',500)")
                    execSQL("INSERT INTO contributions VALUES ('bill','self',1000)")
                    execSQL("INSERT INTO obligations VALUES ('debt','bill','friend','self',500)")
                    }
                }
                val upgraded=Room.databaseBuilder(context,TrackerDatabase::class.java,name)
                    .addMigrations(AppModule.migration1To2,AppModule.migration2To3,AppModule.migration3To4,AppModule.migration4To5).build()
                try {with(upgraded.openHelper.writableDatabase) {
                    query("SELECT SUM(amount) FROM entries WHERE accountId='cash'").use {assertTrue(it.moveToFirst());assertEquals(11345L,it.getLong(0))}
                    query("SELECT description FROM transactions WHERE id='opening'").use {assertTrue(it.moveToFirst());assertEquals("Existing balance",it.getString(0))}
                    query("SELECT amount FROM obligations WHERE fromPerson='friend' AND toPerson='self'").use {assertTrue(it.moveToFirst());assertEquals(500L,it.getLong(0))}
                    query("SELECT name FROM people WHERE id='friend'").use {assertTrue(it.moveToFirst());assertEquals("Existing friend",it.getString(0))}
                    query("PRAGMA foreign_key_check").use {assertFalse(it.moveToFirst())}
                }} finally {upgraded.close()}
            } finally {context.deleteDatabase(name)}
        }
    }

    @Test fun wrongPasswordTamperingAndMalformedBackupsPreserveCurrentRecords()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),TrackerDatabase::class.java).build()
        try {
            val repository=FinanceRepository(db);repository.initialize();repository.createAccount("Existing cash","Cash",12345)
            val service=BackupService(db)
            val bytes=service.export("fixture password".toCharArray())
            val damaged=bytes.copyOf().apply {this[lastIndex]=(last().toInt() xor 1).toByte()}
            val before=service.exportFixture()
            for((input,password) in listOf(bytes to "wrong password",damaged to "fixture password","{\"version\":999}".toByteArray() to "")) {
                assertTrue(runCatching {service.restore(input,password.toCharArray())}.isFailure)
                assertArrayEquals(before,service.exportFixture())
                assertEquals(12345L,repository.snapshot().accounts.single().balance)
            }
            service.restore(bytes,"fixture password".toCharArray())
            assertArrayEquals(before,service.exportFixture())
            // Legacy plaintext imports remain supported even though new exports require encryption.
            service.restore(BackupEnvelope.decode(bytes,"fixture password".toCharArray()),charArrayOf())
            assertArrayEquals(before,service.exportFixture())
        } finally {db.close()}
    }

    @Test fun insertionFailureRollsBackDeletesDuringRestore()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),TrackerDatabase::class.java).build()
        try {
            val repository=FinanceRepository(db);repository.initialize();repository.createAccount("Backup cash","Cash",10000)
            val service=BackupService(db);val older=service.export("fixture password".toCharArray())
            repository.createAccount("New cash","Cash",2345)
            val before=service.exportFixture()
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_restore BEFORE INSERT ON accounts BEGIN SELECT RAISE(ABORT,'simulated restore failure'); END")
            val failure=runCatching {service.restore(older,"fixture password".toCharArray())}.exceptionOrNull()
            assertNotNull(failure)
            assertTrue("Restore must reach the simulated insert failure",failure!!.message.orEmpty().contains("simulated restore failure"))
            assertArrayEquals(before,service.exportFixture())
            assertEquals(2,repository.snapshot().accounts.size)
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_restore")
            service.restore(older,"fixture password".toCharArray())
            assertEquals(10000L,repository.snapshot().accounts.single().balance)
        } finally {db.close()}
    }
}

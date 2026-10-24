package com.smartexpense

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.smartexpense.data.*
import com.smartexpense.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.util.Collections
import java.util.concurrent.Executor

class ReminderEfficiencyTest {
    private class Gateway : ReminderGateway {
        var allowed = true
        var next: Instant? = null
        var shown = 0
        override fun allowed() = allowed
        override fun reconcile(next: Instant?, hasDue: Boolean) { this.next = next }
        override fun show(): Boolean { shown++; return true }
    }

    @Test fun disabledAndDeniedRemindersNeverReadTheLedger() = runBlocking {
        val queries = Collections.synchronizedList(mutableListOf<String>())
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), TrackerDatabase::class.java)
            .setQueryCallback({ sql, _ -> queries.add(sql.lowercase()) }, Executor { it.run() }).build()
        try {
            val repo = FinanceRepository(db)
            repo.initialize()
            val gateway = Gateway()
            val coordinator = ReminderCoordinator(repo, gateway, Clock.systemUTC())
            queries.clear()
            coordinator.syncNotifications()
            val disabledReads = synchronized(queries) { queries.toList() }
            assertTrue("Disabled reminders queried the ledger: $disabledReads", disabledReads.none { it.contains("from transactions") || it.contains("from accounts") })
            repo.saveReminderSettings(ReminderSettings(enabled = true))
            gateway.allowed = false
            queries.clear()
            coordinator.syncNotifications()
            val deniedReads = synchronized(queries) { queries.toList() }
            assertTrue("Denied reminders queried the ledger: $deniedReads", deniedReads.none { it.contains("from transactions") || it.contains("from accounts") })
            assertNull(gateway.next)
            assertEquals(0, gateway.shown)
        } finally { db.close() }
    }

    @Test fun enabledReminderUsesSchedulingInputsWithoutRebuildingReports() = runBlocking {
        val queries = Collections.synchronizedList(mutableListOf<String>())
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), TrackerDatabase::class.java)
            .setQueryCallback({ sql, _ -> queries.add(sql.lowercase()) }, Executor { it.run() }).build()
        try {
            val repo = FinanceRepository(db)
            repo.initialize()
            val account = repo.createAccount("Fixture cash", "Cash", 10000)
            val day = LocalDate.of(2026, 10, 24)
            repo.saveTemplate(RecurringTemplate("fixture", Kind.EXPENSE, 100, "Tea", account, day.toString(), "Daily"))
            repo.saveReminderSettings(ReminderSettings(enabled = true))
            val expected = day.atTime(20, 0).atZone(ZoneId.systemDefault()).toInstant()
            val gateway = Gateway()
            val coordinator = ReminderCoordinator(repo, gateway, Clock.fixed(expected.plusSeconds(60), ZoneOffset.UTC))
            queries.clear()
            coordinator.syncNotifications(expected.toEpochMilli())
            assertEquals(1, gateway.shown)
            assertNotNull(gateway.next)
            val schedulingReads = synchronized(queries) { queries.toList() }
            assertTrue("Reminder rebuilt reports or debts: $schedulingReads", schedulingReads.none {
                it.contains("from entries") || it.contains("from shares") || it.contains("from obligations") || it.contains("strftime(")
            })
            assertTrue(repo.snapshot().records.none { it.kind == Kind.EXPENSE })
        } finally { db.close() }
    }

    @Test fun schedulingInputsKeepReversedOccurrenceIdsAndExcludeArchivedAccounts() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), TrackerDatabase::class.java).build()
        try {
            val repo = FinanceRepository(db)
            repo.initialize()
            val account = repo.createAccount("Fixture cash", "Cash", 10000)
            val day = LocalDate.of(2026, 10, 24)
            val template = RecurringTemplate("fixture", Kind.EXPENSE, 100, "Tea", account, day.toString(), "Daily")
            repo.saveTemplate(template)
            val occurrence = EntryDraft(id = template.entryId(day), amount = 100, description = "Tea", accountId = account)
            repo.save(occurrence)
            repo.reverse(occurrence.id)
            val inputs = repo.reminderInputs()
            assertEquals(listOf(template), inputs.templates)
            assertEquals(setOf(occurrence.id), inputs.recorded)
            db.dao().updateAccount(AccountEntity(account, "Fixture cash", "Cash", archived = true))
            assertTrue(repo.reminderInputs().templates.isEmpty())
        } finally { db.close() }
    }
}

package com.smartexpense
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartexpense.data.*
import com.smartexpense.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class) class CorrectionRepositoryTest {
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    @Test fun editingPastExpenseCreatesRevisionAndReconstructsBalance()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,TrackerDatabase::class.java).build()
        try {val repo=FinanceRepository(db);repo.initialize();val account=repo.createAccount("Bank","Bank",100000);val original=EntryDraft(amount=10000,description="Lunch",accountId=account);repo.save(original)
            repo.save(repo.loadDraft(original.id).copy(amount=20000,contributions=mapOf("self" to 20000L),splitRule=SplitRule.Equal),original.id)
            assertEquals(80000L,repo.snapshot().accounts.single().balance);assertTrue(repo.snapshot().records.single {it.id==original.id}.reversed);assertEquals(1,db.dao().revisions().size)
        } finally {db.close()}
    }
    @Test fun settledBillCorrectionRollsBackUntilRepaymentReversed()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,TrackerDatabase::class.java).build()
        try {val repo=FinanceRepository(db);repo.initialize();val account=repo.createAccount("Bank","Bank",100000);val friend=repo.createPerson("Friend");val original=EntryDraft(amount=10000,description="Shared",accountId=account,participants=listOf("self",friend),contributions=mapOf("self" to 10000L,friend to 0L));repo.save(original);repo.recordRepayment(friend,"self",1000,account,"")
            val count=repo.snapshot().records.size;var rejected=false;try {repo.save(repo.loadDraft(original.id),original.id)} catch(_:IllegalArgumentException) {rejected=true};assertTrue(rejected);assertEquals(count,repo.snapshot().records.size);assertFalse(repo.snapshot().records.single {it.id==original.id}.reversed)
        } finally {db.close()}
    }
    @Test fun netPaymentAllocatesOriginalPathWithoutIntermediateCash()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,TrackerDatabase::class.java).build()
        try {val repo=FinanceRepository(db);repo.initialize();val account=repo.createAccount("Bank","Bank",100000);val rahul=repo.createPerson("Rahul");val kevin=repo.createPerson("Kevin")
            repo.save(EntryDraft(amount=20000,description="First",accountId=account,participants=listOf("self",rahul),contributions=mapOf("self" to 20000L,rahul to 0L)))
            repo.save(EntryDraft(amount=20000,description="Second",accountId=account,participants=listOf("self",kevin),contributions=mapOf("self" to 0L,kevin to 20000L)))
            val payment=repo.recordRepayment(rahul,kevin,10000,null,"Net settlement");assertEquals(80000L,repo.snapshot().accounts.single().balance);assertTrue(repo.snapshot().debts.all {it.outstanding==0L});assertEquals(2,repo.detail(payment).allocations.size)
            val backup=BackupService(db);val bytes=backup.export(charArrayOf());backup.restore(bytes,charArrayOf());assertTrue(repo.snapshot().debts.all {it.outstanding==0L})
            repo.reverse(payment);assertEquals(20000L,repo.snapshot().debts.sumOf {it.outstanding})
        } finally {db.close()}
    }
    @Test fun reopeningFileDatabasePreservesLedger()=runBlocking {
        val name="verification-${java.util.UUID.randomUUID()}.db";var db=Room.databaseBuilder(context,TrackerDatabase::class.java,name).build()
        try {val repo=FinanceRepository(db);repo.initialize();val account=repo.createAccount("Cash","Cash",100000);repo.save(EntryDraft(kind=Kind.INCOME,amount=5000,description="Income",accountId=account));db.close();db=Room.databaseBuilder(context,TrackerDatabase::class.java,name).build();assertEquals(105000L,FinanceRepository(db).snapshot().accounts.single().balance)} finally {db.close();context.deleteDatabase(name)}
    }
    @Test fun duplicateIdWithDifferentMoneyIsRejected()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,TrackerDatabase::class.java).build()
        try {val repo=FinanceRepository(db);repo.initialize();val account=repo.createAccount("Cash","Cash",10000);val original=EntryDraft(amount=1000,description="Lunch",accountId=account);repo.save(original);var rejected=false;try {repo.save(original.copy(amount=2000,contributions=mapOf("self" to 2000L)))} catch(_:IllegalArgumentException) {rejected=true};assertTrue(rejected);assertEquals(9000L,repo.snapshot().accounts.single().balance)} finally {db.close()}
    }
    @Test fun mutualOffsetIsExplicitReversibleAndMovesNoCash()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,TrackerDatabase::class.java).build()
        try {val repo=FinanceRepository(db);repo.initialize();val account=repo.createAccount("Bank","Bank",100000);val friend=repo.createPerson("Friend")
            repo.save(EntryDraft(amount=10000,description="First",accountId=account,participants=listOf("self",friend),contributions=mapOf("self" to 10000L,friend to 0L)))
            repo.save(EntryDraft(amount=4000,description="Second",accountId=account,participants=listOf("self",friend),contributions=mapOf("self" to 0L,friend to 4000L)))
            val balance=repo.snapshot().accounts.single().balance;repo.recordOffset(friend,"self",2000)
            assertEquals(balance,repo.snapshot().accounts.single().balance);assertEquals(3000L,repo.snapshot().debts.sumOf {it.outstanding})
            val offset=repo.snapshot().records.first {it.kind==Kind.OFFSET};repo.reverse(offset.id);assertEquals(7000L,repo.snapshot().debts.sumOf {it.outstanding})
            val backup=BackupService(db);backup.restore(backup.export(charArrayOf()),charArrayOf());assertEquals(balance,repo.snapshot().accounts.single().balance)
        } finally {db.close()}
    }
    @Test fun groupOffsetOnlyAppliesToSelectedGroup()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,TrackerDatabase::class.java).build()
        try {val repo=FinanceRepository(db);repo.initialize();val account=repo.createAccount("Bank","Bank",100000);val friend=repo.createPerson("Friend");val groupA=repo.saveGroup("A",listOf("self",friend));val groupB=repo.saveGroup("B",listOf("self",friend))
            listOf(groupA,groupB).forEach {group->repo.save(EntryDraft(amount=10000,description="I paid",accountId=account,groupId=group,participants=listOf("self",friend),contributions=mapOf("self" to 10000L,friend to 0L)));repo.save(EntryDraft(amount=10000,description="Friend paid",accountId=account,groupId=group,participants=listOf("self",friend),contributions=mapOf("self" to 0L,friend to 10000L)))}
            repo.recordOffset(friend,"self",5000,groupB);val debts=repo.snapshot().debts;assertEquals(10000L,debts.filter {it.groupId==groupA}.sumOf {it.outstanding});assertEquals(0L,debts.filter {it.groupId==groupB}.sumOf {it.outstanding})
        } finally {db.close()}
    }
}

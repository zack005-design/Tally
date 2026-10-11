package com.smartexpense.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackerDao {
    @Query("SELECT * FROM localPlans ORDER BY key") suspend fun plans():List<PlanEntity>
    @Query("SELECT COUNT(*) FROM localPlans") fun planChanges():Flow<Int>
    @Upsert suspend fun savePlan(value:PlanEntity)
    @Query("DELETE FROM localPlans WHERE `key`=:key") suspend fun deletePlan(key:String)
    @Query("SELECT * FROM gpaySchedule WHERE id=1") suspend fun gpaySchedule():GpayScheduleEntity?
    @Query("SELECT COUNT(*) FROM gpaySchedule") fun scheduleChanges():Flow<Int>
    @Upsert suspend fun saveGpaySchedule(value:GpayScheduleEntity)
    @Query("SELECT * FROM resetTargets") suspend fun resetTargets():List<ResetTargetEntity>
    @Upsert suspend fun saveResetTarget(value:ResetTargetEntity)
    @Query("SELECT * FROM diary ORDER BY date") suspend fun diary():List<DiaryEntity>
    @Query("SELECT COUNT(*) FROM diary") fun diaryChanges():Flow<Int>
    @Upsert suspend fun saveDiary(value:DiaryEntity)
    @Query("DELETE FROM diary WHERE date=:date") suspend fun deleteDiary(date:String)
    @Query("SELECT * FROM localSettings WHERE id=1") suspend fun localSettings():LocalSettingsEntity?
    @Query("SELECT COUNT(*) FROM localSettings") fun settingsChanges():Flow<Int>
    @Upsert suspend fun saveLocalSettings(value:LocalSettingsEntity)
    @Query("SELECT * FROM walletSources WHERE id=1") suspend fun walletSources():WalletSourcesEntity?
    @Query("SELECT COUNT(*) FROM walletSources") fun walletSourceChanges():Flow<Int>
    @Upsert suspend fun saveWalletSources(value:WalletSourcesEntity)
    @Query("SELECT COALESCE(SUM(e.amount),0) FROM entries e JOIN transactions t ON t.id=e.transactionId WHERE e.accountId=:accountId AND t.date=:at AND t.reversed=0 AND t.id LIKE 'gpay-reset-correction:%'") suspend fun resetCorrections(accountId:String,at:Long):Long
    @Query("SELECT COALESCE(SUM(e.amount),0) FROM entries e JOIN transactions t ON t.id=e.transactionId WHERE e.accountId=:accountId AND t.reversed=0 AND t.date<:until") suspend fun balanceBefore(accountId:String,until:Long):Long
    @Query("SELECT a.*, COALESCE((SELECT SUM(e.amount) FROM entries e JOIN transactions t ON t.id=e.transactionId WHERE e.accountId=a.id AND t.reversed=0),0) AS balance FROM accounts a ORDER BY a.name") suspend fun balances():List<AccountBalanceRow>
    @Query("SELECT o.*, o.amount - COALESCE((SELECT SUM(a.amount) FROM allocations a JOIN settlements s ON s.id=a.settlementId JOIN transactions st ON st.id=s.transactionId WHERE a.obligationId=o.id AND st.reversed=0),0) AS outstanding, t.date, t.groupId FROM obligations o JOIN transactions t ON t.id=o.transactionId WHERE t.reversed=0 ORDER BY t.date,o.id") suspend fun debts():List<DebtRow>
    @Query("SELECT * FROM transactions ORDER BY date DESC,id") suspend fun transactions():List<TransactionEntity>
    @Query("SELECT * FROM transactions ORDER BY date DESC,id LIMIT :limit") suspend fun recentTransactions(limit:Int):List<TransactionEntity>
    @Query("SELECT COUNT(*) FROM transactions") suspend fun transactionCount():Int
    @Query("SELECT * FROM entries WHERE transactionId IN (:ids)") suspend fun entriesForRecords(ids:List<String>):List<AccountEntryEntity>
    @Query("SELECT * FROM shares WHERE transactionId IN (:ids)") suspend fun sharesForRecords(ids:List<String>):List<ShareEntity>
    @Query("SELECT * FROM settlements WHERE transactionId IN (:ids)") suspend fun settlementsForRecords(ids:List<String>):List<SettlementEntity>
    @Query("SELECT COALESCE(SUM(CASE kind WHEN 'EXPENSE' THEN personalAmount WHEN 'REFUND' THEN -personalAmount ELSE 0 END),0) AS spending, COALESCE(SUM(CASE WHEN kind='INCOME' THEN amount ELSE 0 END),0) AS income FROM transactions WHERE reversed=0 AND date>=:from AND date<:until") suspend fun reportTotals(from:Long,until:Long):ReportTotalsRow
    @Query("SELECT categoryId, SUM(CASE kind WHEN 'EXPENSE' THEN personalAmount ELSE -personalAmount END) AS amount FROM transactions WHERE reversed=0 AND kind IN ('EXPENSE','REFUND') AND date>=:from AND date<:until GROUP BY categoryId") suspend fun reportCategories(from:Long,until:Long):List<CategoryTotalRow>
    @Query("SELECT strftime('%Y-%m-%d', date/1000, 'unixepoch', 'localtime') AS day, SUM(CASE kind WHEN 'EXPENSE' THEN personalAmount WHEN 'REFUND' THEN -personalAmount ELSE 0 END) AS amount FROM transactions WHERE reversed=0 AND date>=:from AND date<:until GROUP BY day") suspend fun reportDays(from:Long,until:Long):List<DailyTotalRow>
    @Query("SELECT groupId, SUM(amount) AS amount FROM transactions WHERE reversed=0 AND kind='EXPENSE' AND groupId IS NOT NULL GROUP BY groupId") suspend fun groupSpending():List<GroupTotalRow>
    @Query("SELECT * FROM transactions WHERE id=:id") suspend fun transaction(id:String):TransactionEntity?
    @Query("SELECT * FROM transactions WHERE id=:id") fun observeTransaction(id:String):Flow<TransactionEntity?>
    @Query("SELECT * FROM entries WHERE transactionId=:id") suspend fun entriesFor(id:String):List<AccountEntryEntity>
    @Query("SELECT * FROM shares WHERE transactionId=:id") suspend fun sharesFor(id:String):List<ShareEntity>
    @Query("SELECT * FROM contributions WHERE transactionId=:id") suspend fun contributionsFor(id:String):List<ContributionEntity>
    @Query("SELECT * FROM accounts") suspend fun accounts():List<AccountEntity>
    @Query("SELECT * FROM people ORDER BY name,id") suspend fun people():List<PersonEntity>
    @Query("SELECT * FROM groups ORDER BY name") suspend fun groups():List<GroupEntity>
    @Query("SELECT * FROM members") suspend fun members():List<GroupMemberEntity>
    @Query("SELECT * FROM categories ORDER BY name") suspend fun categories():List<CategoryEntity>
    @Query("SELECT * FROM budgets") suspend fun budgets():List<BudgetEntity>
    @Query("SELECT * FROM entries") suspend fun entries():List<AccountEntryEntity>
    @Query("SELECT * FROM shares") suspend fun shares():List<ShareEntity>
    @Query("SELECT * FROM contributions") suspend fun contributions():List<ContributionEntity>
    @Query("SELECT * FROM obligations") suspend fun obligations():List<ObligationEntity>
    @Query("SELECT * FROM settlements") suspend fun settlements():List<SettlementEntity>
    @Query("SELECT * FROM allocations") suspend fun allocations():List<AllocationEntity>
    @Query("SELECT * FROM revisions") suspend fun revisions():List<RevisionEntity>
    @Query("SELECT COUNT(*) FROM transactions") fun transactionChanges():Flow<Int>
    @Query("SELECT COUNT(*) FROM accounts") fun accountChanges():Flow<Int>
    @Query("SELECT COUNT(*) FROM people") fun personChanges():Flow<Int>
    @Query("SELECT COUNT(*) FROM categories") fun categoryChanges():Flow<Int>
    @Query("SELECT COUNT(*) FROM budgets") fun budgetChanges():Flow<Int>
    @Query("SELECT COUNT(*) FROM groups") fun groupChanges():Flow<Int>
    @Insert suspend fun insertAccount(value:AccountEntity)
    @Insert suspend fun insertPerson(value:PersonEntity)
    @Insert suspend fun insertGroup(value:GroupEntity)
    @Insert suspend fun insertMembers(value:List<GroupMemberEntity>)
    @Insert suspend fun insertCategory(value:CategoryEntity)
    @Insert suspend fun insertTransaction(value:TransactionEntity)
    @Insert suspend fun insertEntries(value:List<AccountEntryEntity>)
    @Insert suspend fun insertShares(value:List<ShareEntity>)
    @Insert suspend fun insertContributions(value:List<ContributionEntity>)
    @Insert suspend fun insertObligations(value:List<ObligationEntity>)
    @Insert suspend fun insertSettlement(value:SettlementEntity)
    @Insert suspend fun insertAllocations(value:List<AllocationEntity>)
    @Insert suspend fun insertRevision(value:RevisionEntity)
    @Upsert suspend fun saveBudget(value:BudgetEntity)
    @Update suspend fun updateAccount(value:AccountEntity)
    @Update suspend fun updatePerson(value:PersonEntity)
    @Update suspend fun updateCategory(value:CategoryEntity)
    @Update suspend fun updateGroup(value:GroupEntity)
    @Query("DELETE FROM members WHERE groupId=:id") suspend fun clearMembers(id:String)
    @Query("UPDATE transactions SET reversed=1 WHERE id=:id") suspend fun markReversed(id:String)
}

@Database(entities=[PlanEntity::class,GpayScheduleEntity::class,ResetTargetEntity::class,DiaryEntity::class,LocalSettingsEntity::class,WalletSourcesEntity::class,AccountEntity::class,PersonEntity::class,GroupEntity::class,GroupMemberEntity::class,CategoryEntity::class,TransactionEntity::class,AccountEntryEntity::class,ShareEntity::class,ContributionEntity::class,ObligationEntity::class,SettlementEntity::class,AllocationEntity::class,BudgetEntity::class,RevisionEntity::class],version=4,exportSchema=true)
abstract class TrackerDatabase:RoomDatabase() { abstract fun dao():TrackerDao }

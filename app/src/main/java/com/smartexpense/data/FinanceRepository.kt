package com.smartexpense.data

import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import com.smartexpense.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

class LocalDataFailure(cause:Throwable):Exception("Could not save local data. Your previous records are unchanged.",cause)

/** Widget-safe shortcut metadata; never includes an amount, account, or history. */
data class WidgetFavourite(val id:String,val name:String,val categoryName:String,val categoryIcon:String)

@Singleton
class FinanceRepository @Inject constructor(private val db:TrackerDatabase) {
    private val dao=db.dao()
    private fun id()=UUID.randomUUID().toString()
    private suspend fun <T> storage(block:suspend ()->T):T = try { block() } catch(e:CancellationException) { throw e } catch(e:SQLiteException) { throw LocalDataFailure(e) }
    val changes:Flow<Unit> = combine(listOf(dao.transactionChanges(),dao.accountChanges(),dao.personChanges(),dao.categoryChanges(),dao.budgetChanges(),dao.groupChanges(),dao.walletSourceChanges(),dao.scheduleChanges(),dao.diaryChanges(),dao.settingsChanges(),dao.planChanges())) { Unit }
    val snapshots:Flow<Snapshot> = changes.map {snapshot()}

    private class HistoryPreviewRollback(val preview:HistoryLedgerPreview):RuntimeException()

    /** Runs the real ledger write path, then rolls back every write before returning. */
    suspend fun previewChatHistory(history:ChatHistory,selectedSourceIds:Set<String>,accountMapping:Map<String,String>,categoryMapping:Map<String,String>):HistoryLedgerPreview = storage {
        try {
            db.withTransaction {
                val reviewDay=java.time.LocalDate.now()
                val before=snapshot()
                val drafts=ChatHistoryImport.preview(history,before,accountMapping,categoryMapping=categoryMapping).selected(selectedSourceIds)
                require(drafts.isNotEmpty()) {"Select at least one supported record"}
                val token=historyReviewToken(drafts)
                val skipped=drafts.count {dao.transaction(it.id)!=null}
                drafts.forEach {kotlinx.coroutines.currentCoroutineContext().ensureActive();save(it)}
                require(java.time.LocalDate.now()==reviewDay) {"The day changed while preparing this batch. Preview again."}
                val after=snapshot()
                throw HistoryPreviewRollback(HistoryLedgerPreview(token,before.accounts.associate {it.id to it.balance},after.accounts.associate {it.id to it.balance},drafts.size-skipped,skipped))
            }
        } catch(rollback:HistoryPreviewRollback) {rollback.preview}
    }

    suspend fun importChatHistory(history:ChatHistory,selectedSourceIds:Set<String>,accountMapping:Map<String,String>,categoryMapping:Map<String,String>,expectedReviewToken:String):HistoryImportResult = storage {db.withTransaction {
        val reviewDay=java.time.LocalDate.now()
        val drafts=ChatHistoryImport.preview(history,snapshot(),accountMapping,categoryMapping=categoryMapping).selected(selectedSourceIds)
        require(drafts.isNotEmpty()) {"Select at least one supported record"}
        require(historyReviewToken(drafts)==expectedReviewToken) {"Your accounts or history changed. Preview the import again before confirming."}
        val skipped=drafts.count {dao.transaction(it.id)!=null}
        drafts.forEach {kotlinx.coroutines.currentCoroutineContext().ensureActive();save(it)}
        require(java.time.LocalDate.now()==reviewDay) {"The day changed while importing. Nothing was saved; preview again."}
        HistoryImportResult(drafts.size-skipped,skipped,history.entries.size-drafts.size)
    }}

    private suspend fun historyReviewToken(drafts:List<EntryDraft>):String = ChatHistoryImport.reviewToken(
        listOf(drafts.toString(),dao.transactions().toString(),dao.balances().toString(),dao.categories().toString(),dao.gpaySchedule().toString(),dao.walletSources().toString(),dao.resetTargets().toString())
    )
    suspend fun widgetFavourites():List<WidgetFavourite> = storage {db.withTransaction {
        val activeAccounts=dao.accounts().filterNot {it.archived}.map {it.id}.toSet()
        val activeCategories=dao.categories().filterNot {it.archived}.associateBy {it.id}
        dao.plans().asSequence().filter {it.kind=="favourite"}.mapNotNull {runCatching {PlanCodec.favourite(it)}.getOrNull()}
            .filter {it.accountId in activeAccounts && (it.categoryId==null || it.categoryId in activeCategories)}
            .take(3).map {value->val category=activeCategories[value.categoryId];WidgetFavourite(value.id,value.description,category?.name.orEmpty(),category?.icon.orEmpty())}.toList()
    }}
    suspend fun reminderSettings():ReminderSettings=storage {dao.plans().find {it.key=="settings:reminder"}?.let(PlanCodec::reminder)?:ReminderSettings()}
    suspend fun saveReminderSettings(value:ReminderSettings)=storage {db.withTransaction {
        val previous=reminderSettings();dao.savePlan(PlanCodec.reminder(value.copy(lastNotifiedDate=previous.lastNotifiedDate)))
    }}
    suspend fun markReminderDelivered(date:String)=storage {db.withTransaction {dao.savePlan(PlanCodec.reminder(reminderSettings().copy(lastNotifiedDate=date)))}}
    suspend fun savedReceiptId(bytes:ByteArray):String?=storage {db.withTransaction {
        var current=dao.receipts().find {it.hash==ReceiptStorage.hash(bytes)}?.transactionId?:return@withTransaction null
        val revisions=dao.revisions().filter {it.replacementId!=null}.associateBy {it.originalId}
        val visited=mutableSetOf<String>()
        while(visited.add(current)) current=revisions[current]?.replacementId?:return@withTransaction current
        error("Invalid receipt revision history")
    }}
    private suspend fun receiptOwner(id:String):String? {
        var current=id
        val ancestors=dao.revisions().filter {it.replacementId!=null}.associateBy {it.replacementId}
        val visited=mutableSetOf<String>()
        while(visited.add(current)) {
            if(dao.receipt(current)!=null) return current
            current=ancestors[current]?.originalId?:return null
        }
        error("Invalid receipt revision history")
    }
    suspend fun receipt(id:String):ScannedBill?=storage {db.withTransaction {
        receiptOwner(id)?.let {owner->dao.receipt(owner)?.let {ScannedBill(ReceiptStorage.join(dao.receiptChunks(owner)),it.mime,it.text)}}
    }}
    fun receiptChanges(id:String):Flow<ScannedBill?> = combine(dao.receiptChanges(id),dao.transactionChanges(),dao.revisionChanges()) {_,_,_->receipt(id)}
    suspend fun removeReceipt(id:String)=storage {db.withTransaction {receiptOwner(id)?.let {dao.deleteReceipt(it)}}}
    suspend fun saveWithBill(draft:EntryDraft,bill:ScannedBill):String=storage {db.withTransaction {
        require(draft.kind==Kind.EXPENSE) {"Bill attachments belong to expense entries"}
        ReceiptStorage.validate(bill)
        val hash=ReceiptStorage.hash(bill.bytes)
        require(dao.receipts().none {it.hash==hash && it.transactionId!=draft.id}) {"This bill is already attached to a saved transaction"}
        dao.receipt(draft.id)?.let {require(it.hash==hash && it.text==bill.text && it.mime==bill.mime);return@withTransaction save(draft)}
        require(dao.receiptChunks().sumOf {it.bytes.size.toLong()}+bill.bytes.size<=ReceiptStorage.TOTAL_BYTES) {"Receipt storage is full. Export a backup and remove old attachments to add another."}
        val transaction=save(draft)
        dao.insertReceipt(ReceiptEntity(transaction,bill.mime,bill.text,hash))
        dao.insertReceiptChunks((bill.bytes.indices step ReceiptStorage.CHUNK_BYTES).mapIndexed {index,start->ReceiptChunkEntity(transaction,index,bill.bytes.copyOfRange(start,minOf(start+ReceiptStorage.CHUNK_BYTES,bill.bytes.size)))})
        transaction
    }}
    suspend fun saveCategoryRule(value:CategoryRule)=storage {
        require(value.id.isNotBlank() && value.merchant.trim().isNotBlank() && value.merchant.length<=100 && dao.categories().any {it.id==value.categoryId && !it.archived})
        require(dao.plans().filter {it.kind=="category"}.map(PlanCodec::category).none {it.id!=value.id && it.merchant.equals(value.merchant.trim(),true)}) {"Another merchant rule already uses that name"}
        dao.savePlan(PlanCodec.category(value.copy(merchant=value.merchant.trim())))
    }

    suspend fun initialize() = storage { db.withTransaction {
        if(dao.localSettings()==null) dao.saveLocalSettings(LocalSettingsEntity(welcomeDone=dao.accounts().isNotEmpty(),lastReviewMonth=java.time.YearMonth.now().toString()))
        if(dao.people().none { it.id=="self" }) dao.insertPerson(PersonEntity("self","You"))
        if(dao.categories().isEmpty()) listOf("Food","Groceries","Transport","Shopping","Education","Entertainment","Bills","Subscriptions","Health","Travel","Other").forEachIndexed { i,name -> dao.insertCategory(CategoryEntity("category-$i",name)) }
    } }
    suspend fun configureWallet(walletOpening:Long,walletId:String?=null,gpayId:String?=null,today:java.time.LocalDate=java.time.LocalDate.now(),gpayOpening:Long=0) = storage {db.withTransaction {
        require(dao.walletSources()==null) {"Wallet and GPay are already set up"}
        require(walletOpening>=0 && gpayOpening>=0) {"Opening balances cannot be negative"}
        val wallet=walletId?:createAccount("Wallet","Cash",walletOpening)
        val gpay=gpayId?:createAccount("GPay","Bank",gpayOpening)
        require(wallet!=gpay && listOf(wallet,gpay).all {key->dao.accounts().any {it.id==key && !it.archived}}) {"Choose two different active accounts"}
        dao.saveWalletSources(WalletSourcesEntity(walletId=wallet,gpayId=gpay,lastResetMonth=java.time.YearMonth.from(today).toString()))
        dao.saveLocalSettings((dao.localSettings()?:LocalSettingsEntity()).copy(welcomeDone=true))
    }}
    suspend fun refreshMonthlyGpay(today:java.time.LocalDate=java.time.LocalDate.now()) = storage {db.withTransaction {
        val source=dao.walletSources()?:return@withTransaction
        val setting=dao.gpaySchedule()?:return@withTransaction
        if(!setting.enabled) return@withTransaction
        val schedule=setting.toDomain()
        val current=java.time.YearMonth.from(today);var month=java.time.YearMonth.parse(setting.nextMonth)
        if(schedule.dueDate(month)>today) return@withTransaction
        require(dao.accounts().any {it.id==source.gpayId && !it.archived}) {"Reactivate your GPay account to apply its monthly reset"}
        while(month<=current && schedule.dueDate(month)<=today) {
            val boundary=schedule.dueDate(month).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val tx="gpay-reset:${source.gpayId}:$month"
            if(dao.transaction(tx)==null) {
                val adjustment=Math.subtractExact(setting.target,dao.balanceBefore(source.gpayId,boundary))
                dao.insertTransaction(TransactionEntity(tx,Kind.ADJUSTMENT.name,"GPay monthly reset · $month",adjustment,0,null,null,boundary,"Set to ${Money.format(setting.target)} · no carryover"))
                dao.insertEntries(listOf(AccountEntryEntity(id(),tx,source.gpayId,adjustment)))
                dao.saveResetTarget(ResetTargetEntity(tx,setting.target))
            }
            month=month.plusMonths(1)
        }
        dao.saveGpaySchedule(setting.copy(nextMonth=month.toString()))
        dao.saveWalletSources(source.copy(lastResetMonth=month.minusMonths(1).toString()));validateBalances()
    }}
    suspend fun saveGpaySchedule(enabled:Boolean,target:Long,day:Int,startMonth:String,today:java.time.LocalDate=java.time.LocalDate.now()) = storage {db.withTransaction {
        require(dao.walletSources()!=null) {"Set up Wallet & GPay first"}
        val next=firstResetMonth(day,startMonth,today)
        GpaySchedule(enabled,target,day,startMonth,next)
        val previous=dao.gpaySchedule()
        // Saving an unchanged active schedule must not skip an overdue occurrence.
        if(previous?.enabled==true && enabled && previous.target==target && previous.day==day && previous.startMonth==startMonth) return@withTransaction
        dao.saveGpaySchedule(GpayScheduleEntity(enabled=enabled,target=target,day=day,startMonth=startMonth,nextMonth=next))
    }}
    private fun GpayScheduleEntity.toDomain()=GpaySchedule(enabled,target,day,startMonth,nextMonth)
    suspend fun saveDiary(date:String,text:String)=storage {
        java.time.LocalDate.parse(date);require(text.length<=10000) {"Keep the daily note under 10,000 characters"}
        if(text.isBlank()) dao.deleteDiary(date) else dao.saveDiary(DiaryEntity(date,text))
    }
    suspend fun saveGroupDefault(value:GroupDefault)=storage {db.withTransaction {
        val members=dao.members().filter {it.groupId==value.groupId}.map {it.personId}
        require(dao.groups().any {it.id==value.groupId});value.validate((listOf("self")+members).distinct())
        dao.savePlan(PlanCodec.group(value))
    }}
    suspend fun saveTemplate(value:RecurringTemplate)=storage {
        require(dao.accounts().any {it.id==value.accountId && !it.archived}) {"Choose an active account"}
        dao.savePlan(PlanCodec.template(value))
    }
    suspend fun saveGoal(name:String,target:Long,goalId:String?=null)=storage {db.withTransaction {
        val existing=dao.plans().find {it.key=="goal:$goalId"}?.let(PlanCodec::goal)
        val goal=SavingsGoal(goalId?:id(),name.trim(),target,existing?.contributions.orEmpty())
        require(goal.allocated<=target) {"Target cannot be below your allocations"};dao.savePlan(PlanCodec.goal(goal))
    }}
    suspend fun allocateGoal(goalId:String,amount:Long)=storage {db.withTransaction {
        val goal=PlanCodec.goal(dao.plans().single {it.key=="goal:$goalId"})
        require(amount>0 && amount<=Math.subtractExact(goal.target,goal.allocated)) {"Enter an allocation within the remaining goal amount"}
        val next=goal.copy(contributions=goal.contributions+GoalContribution(id(),java.time.LocalDate.now().toString(),amount))
        val row=PlanCodec.goal(next);require(row.json.length<=10000) {"This goal has reached its local history limit"};dao.savePlan(row)
    }}
    suspend fun deletePlan(key:String)=storage {dao.deletePlan(key)}
    suspend fun saveFavourite(value:FavouriteExpense)=storage {db.withTransaction {
        val favourites=dao.plans().filter {it.kind=="favourite"}
        require(favourites.any {it.key=="favourite:${value.id}"} || favourites.size<6) {"Keep up to six favourites. Remove one to add another."}
        require(dao.accounts().any {it.id==value.accountId && !it.archived}) {"Choose an active account"}
        require(value.categoryId==null || dao.categories().any {it.id==value.categoryId && !it.archived}) {"Choose an active category"}
        dao.savePlan(PlanCodec.favourite(value))
    }}
    suspend fun saveProfile(name:String,avatar:String)=storage {db.withTransaction {
        require(name.trim().isNotEmpty() && name.trim().length<=100 && avatar in listOf("initials","wallet","leaf","star")) {"Enter your name and choose an avatar"}
        dao.saveLocalSettings((dao.localSettings()?:LocalSettingsEntity()).copy(name=name.trim(),avatar=avatar))
    }}
    suspend fun finishWelcome()=storage {db.withTransaction {dao.saveLocalSettings((dao.localSettings()?:LocalSettingsEntity()).copy(welcomeDone=true))}}
    suspend fun acknowledgeReview(month:String)=storage {db.withTransaction {
        java.time.YearMonth.parse(month)
        dao.saveLocalSettings((dao.localSettings()?:LocalSettingsEntity()).copy(lastReviewMonth=month))
    }}
    suspend fun monthlyReview(month:String):MonthlyReview=storage {db.withTransaction {
        val shares=dao.shares().groupBy {it.transactionId}
        val records=dao.transactions().map {Record(it.id,Kind.valueOf(it.kind),it.description,it.amount,it.personalAmount,it.categoryId,it.groupId,it.date,it.notes,it.reversed,personIds=shares[it.id].orEmpty().map {share->share.personId})}
        com.smartexpense.domain.monthlyReview(records,dao.entries().map {CashMovement(it.transactionId,it.accountId,it.amount)},java.time.YearMonth.parse(month))
    }}
    suspend fun exportReport(month:String):Pair<Snapshot,MonthlyReview> = storage {db.withTransaction {snapshot(Int.MAX_VALUE,month) to monthlyReview(month)}}
    suspend fun snapshot(historyLimit:Int=Int.MAX_VALUE,selectedMonth:String=java.time.YearMonth.now().toString()):Snapshot = storage { db.withTransaction {
        val transactions=dao.recentTransactions(historyLimit);val ids=transactions.map {it.id}
        val plans=dao.plans();val members=dao.members();val entries=ids.chunked(900).flatMap {dao.entriesForRecords(it)}.groupBy {it.transactionId};val shares=ids.chunked(900).flatMap {dao.sharesForRecords(it)}.groupBy {it.transactionId};val settlements=ids.chunked(900).flatMap {dao.settlementsForRecords(it)}.groupBy {it.transactionId}
        val today=java.time.LocalDate.now();val month=java.time.YearMonth.from(today);val previous=month.minusMonths(1);val selected=java.time.YearMonth.parse(selectedMonth)
        Snapshot(dao.balances().map { Account(it.id,it.name,it.type,it.balance,it.archived) },dao.people().map { Person(it.id,it.name,it.aliases.split('|').filter(String::isNotBlank)) },dao.groups().map { g -> Group(g.id,g.name,members.filter { it.groupId==g.id }.map { it.personId }) },dao.categories().map { Category(it.id,it.name,it.color,it.icon,it.archived) },dao.budgets().map { Budget(it.id,it.month,it.categoryId,it.amount) },transactions.map { Record(it.id,Kind.valueOf(it.kind),it.description,it.amount,it.personalAmount,it.categoryId,it.groupId,it.date,it.notes,it.reversed,entries[it.id].orEmpty().map {it.accountId},(shares[it.id].orEmpty().map {it.personId}+settlements[it.id].orEmpty().flatMap {listOf(it.fromPerson,it.toPerson)}).distinct()) },dao.debts().map { Debt(it.id,it.transactionId,it.fromPerson,it.toPerson,it.amount,it.outstanding,it.date,it.groupId) },DashboardMetrics(report(today,today).spending,report(today.with(java.time.DayOfWeek.MONDAY),today).spending,report(month.atDay(1),today),report(previous.atDay(1),previous.atDay(minOf(today.dayOfMonth,previous.lengthOfMonth()))).spending),report(selected.atDay(1),selected.atEndOfMonth()),dao.groupSpending().associate {it.groupId to it.amount},dao.transactionCount(),selectedMonth,report(selected.minusMonths(1).atDay(1),selected.minusMonths(1).atEndOfMonth()).spending,walletSources=dao.walletSources()?.let {WalletSources(it.walletId,it.gpayId,it.lastResetMonth,dao.transaction("gpay-reset:${it.gpayId}:${it.lastResetMonth}")?.date,dao.gpaySchedule()?.toDomain()?:GpaySchedule())},diary=dao.diary().associate {it.date to it.text},localSettings=dao.localSettings()?.let {LocalSettings(it.name,it.avatar,it.welcomeDone,it.lastReviewMonth)}?:LocalSettings(),groupDefaults=plans.filter {it.kind=="group"}.map(PlanCodec::group),templates=plans.filter {it.kind=="template"}.map(PlanCodec::template),goals=plans.filter {it.kind=="goal"}.map(PlanCodec::goal),categoryRules=plans.filter {it.kind=="category"}.map(PlanCodec::category),favourites=plans.filter {it.kind=="favourite"}.map(PlanCodec::favourite))
    } }
    suspend fun report(start:java.time.LocalDate,end:java.time.LocalDate):PeriodReport = storage {
        val zone=java.time.ZoneId.systemDefault();val from=start.atStartOfDay(zone).toInstant().toEpochMilli();val until=end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();val totals=dao.reportTotals(from,until)
        PeriodReport(totals.spending,totals.income,dao.reportCategories(from,until).associate {it.categoryId to it.amount},dao.reportDays(from,until).associate {java.time.LocalDate.parse(it.day) to it.amount})
    }
    suspend fun createAccount(name:String,type:String,opening:Long):String = storage { db.withTransaction {
        require(name.isNotBlank() && type in listOf("Cash","Bank","Wallet","Other")) { "Enter an account name and type" }
        val key=id(); dao.insertAccount(AccountEntity(key,name.trim(),type))
        if(opening!=0L) {
            val tx=id(); dao.insertTransaction(TransactionEntity(tx,Kind.ADJUSTMENT.name,"Opening balance",opening,0,null,null,System.currentTimeMillis(),""))
            dao.insertEntries(listOf(AccountEntryEntity(id(),tx,key,opening)))
        }; validateBalances();key
    } }
    suspend fun updateAccount(account:Account) = storage { db.withTransaction {
        require(account.name.isNotBlank())
        val sources=dao.walletSources();require(!account.archived || account.id !in listOf(sources?.walletId,sources?.gpayId)) {"Wallet and GPay must stay active"}
        dao.updateAccount(AccountEntity(account.id,account.name.trim(),account.type,account.archived));validateBalances()
    } }
    suspend fun reconcile(accountId:String,target:Long,expectedBalance:Long?=null,reason:String="") = storage { db.withTransaction {
        refreshMonthlyGpay()
        val account=dao.balances().single { it.id==accountId }
        require(!account.archived) {"Choose an active account"}
        val balance=account.balance
        require(expectedBalance==null || balance==expectedBalance) {"The recorded balance changed. Review it again before adjusting."}
        require(reason.length<=500) {"Keep the reason under 500 characters"}
        val change=Math.subtractExact(target,balance); require(change!=0L) { "Balance is already correct" }
        val tx=id(); dao.insertTransaction(TransactionEntity(tx,Kind.ADJUSTMENT.name,"Balance reconciliation",change,0,null,null,System.currentTimeMillis(),"Target ${Money.format(target)}${if(reason.isBlank()) "" else " · ${reason.trim()}"}")); dao.insertEntries(listOf(AccountEntryEntity(id(),tx,accountId,change)));validateBalances()
    } }
    /** Read-only preview uses the same later-reset boundary as balance validation. */
    suspend fun previewWalletFunds(accountId:String,amount:Long,date:Long):Long = storage {db.withTransaction {
        val sources=requireNotNull(dao.walletSources()) {"Set up Wallet and GPay first"}
        require(amount>0 && accountId in listOf(sources.walletId,sources.gpayId)) {"Choose Wallet or GPay and enter a positive amount"}
        val account=dao.balances().single {it.id==accountId}
        require(!account.archived) {"Choose an active account"}
        val absorbed=accountId==sources.gpayId && dao.transactions().any {it.id.startsWith("gpay-reset:${sources.gpayId}:") && !it.reversed && date<it.date}
        if(absorbed) account.balance else Math.addExact(account.balance,amount)
    }}
    suspend fun addWalletFunds(accountId:String,amount:Long,date:Long=System.currentTimeMillis(),note:String="") = storage {db.withTransaction {
        refreshMonthlyGpay()
        val sources=dao.walletSources()
        require(amount>0 && sources!=null && accountId in listOf(sources.walletId,sources.gpayId)) {"Choose Wallet or GPay and enter a positive amount"}
        require(note.length<=500) {"Keep the note under 500 characters"}
        require(java.time.Instant.ofEpochMilli(date).atZone(java.time.ZoneId.systemDefault()).year in 1900..2100) {"Choose a date between 1900 and 2100"}
        val name=if(accountId==sources.walletId) "Wallet" else "GPay"
        val tx=id();dao.insertTransaction(TransactionEntity(tx,Kind.ADJUSTMENT.name,"$name cash top-up",amount,0,null,null,date,"Money added to $name"+if(note.isBlank()) "" else " · ${note.trim()}"))
        dao.insertEntries(listOf(AccountEntryEntity(id(),tx,accountId,amount)));validateBalances()
    }}
    suspend fun createPerson(name:String,aliases:String=""):String = storage { require(name.isNotBlank()); val key=id(); dao.insertPerson(PersonEntity(key,name.trim(),aliases)); key }
    suspend fun updatePerson(person:Person) = storage { require(person.id!="self" && person.name.isNotBlank()); dao.updatePerson(PersonEntity(person.id,person.name.trim(),person.aliases.joinToString("|"))) }
    suspend fun saveGroup(name:String,members:List<String>,groupId:String?=null):String = storage { db.withTransaction {
        require(name.isNotBlank() && members.size>=2 && members.distinct().size==members.size) { "Choose a name and at least two group members" }
        val key=groupId?:id(); if(groupId==null) dao.insertGroup(GroupEntity(key,name.trim())) else { dao.updateGroup(GroupEntity(key,name.trim())); if(dao.members().filter {it.groupId==key}.map {it.personId}.toSet()!=members.toSet()) dao.deletePlan("group:$key");dao.clearMembers(key) }
        dao.insertMembers(members.map { GroupMemberEntity(key,it) }); key
    } }
    suspend fun saveCategory(name:String,icon:String,color:Long,categoryId:String?=null,archived:Boolean=false) = storage {
        require(name.isNotBlank()); val value=CategoryEntity(categoryId?:id(),name.trim(),color,icon.take(8),archived)
        if(categoryId==null) dao.insertCategory(value) else dao.updateCategory(value)
    }
    suspend fun saveBudget(month:String,categoryId:String?,amount:Long) = storage { java.time.YearMonth.parse(month); require(amount>0); dao.saveBudget(BudgetEntity("$month:${categoryId?:"all"}",month,categoryId,amount)) }
    suspend fun save(draft:EntryDraft,replaces:String?=null):String = storage { db.withTransaction {
        refreshMonthlyGpay()
        // A stable draft id is the idempotency key; retries never duplicate account entries.
        dao.transaction(draft.id)?.let {existing->
            require(!existing.reversed && existing.kind==draft.kind.name && existing.amount==draft.amount && existing.description==draft.description.trim() && existing.categoryId==draft.categoryId && existing.groupId==draft.groupId && existing.date==draft.date && existing.notes==draft.notes && existing.refundOf==draft.refundOf) {"This draft ID was already used for different transaction details"}
            if(draft.kind==Kind.EXPENSE) {require(dao.sharesFor(draft.id).associate {it.personId to it.amount}==Finance.split(draft.amount,draft.participants,draft.splitRule) && dao.contributionsFor(draft.id).associate {it.personId to it.amount}==draft.contributions) {"This draft was already saved with different shares"}}
            val entries=dao.entriesFor(draft.id)
            val expected=when(draft.kind) {
                Kind.EXPENSE->expensePayments(draft).mapValues {Math.negateExact(it.value)}
                Kind.TRANSFER->mapOf(draft.accountId to -draft.amount,requireNotNull(draft.destinationAccountId) to draft.amount)
                else->mapOf(draft.accountId to draft.amount)
            }
            require(entries.associate {it.accountId to it.amount}==expected) {"This draft was already saved with different payment sources"}
            return@withTransaction draft.id
        }
        require(draft.amount>0 && draft.description.isNotBlank()) { "Enter a positive amount and description" }
        require(draft.kind !in listOf(Kind.REPAYMENT,Kind.ADJUSTMENT,Kind.OFFSET)) { "Use the repayment or reconciliation workflow" }
        require(draft.kind==Kind.REFUND || draft.refundOf==null) {"Only refunds may link to an original expense"}
        require(dao.accounts().any { it.id==draft.accountId && !it.archived }) { "Choose an active payment account" }
        require(draft.kind!=Kind.EXPENSE || draft.categoryId==null || dao.categories().any {it.id==draft.categoryId && !it.archived}) {"Choose an active category or explicitly choose Uncategorised before saving."}
        if(replaces!=null) reverseInternal(replaces)
        val shares=if(draft.kind==Kind.EXPENSE) Finance.split(draft.amount,draft.participants,draft.splitRule) else emptyMap()
        val net=if(draft.kind==Kind.EXPENSE) Finance.net(shares,draft.contributions) else emptyMap()
        if(draft.kind==Kind.EXPENSE) require(expensePayments(draft).keys.all {key->dao.accounts().any {it.id==key && !it.archived}}) {"Choose active payment sources"}
        val personal=when(draft.kind) { Kind.EXPENSE -> shares["self"]?:0; Kind.TRANSFER -> 0; else -> draft.amount }
        if(draft.kind==Kind.TRANSFER) require(draft.destinationAccountId!=draft.accountId && dao.accounts().any { it.id==draft.destinationAccountId && !it.archived }) { "Choose a different destination account" }
        if(draft.kind==Kind.REFUND) {
            val original=dao.transaction(requireNotNull(draft.refundOf) { "Choose the expense being refunded" })
            require(original!=null && original.kind==Kind.EXPENSE.name && !original.reversed) { "Choose an active expense" }
            require(dao.sharesFor(original.id).map {it.personId}==listOf("self")) { "Correct a shared bill using its original split; individual refunds are supported here" }
            val prior=Finance.total(dao.transactions().filter {it.kind==Kind.REFUND.name && it.refundOf==original.id && !it.reversed }.map { it.amount })
            require(draft.amount<=Math.subtractExact(original.personalAmount,prior)) { "Refund exceeds remaining expense" }
            require(draft.categoryId==original.categoryId) { "Refund must use the original category" }
        }
        dao.insertTransaction(TransactionEntity(draft.id,draft.kind.name,draft.description.trim(),draft.amount,personal,draft.categoryId,draft.groupId,draft.date,draft.notes,refundOf=draft.refundOf))
        val movements=when(draft.kind) {
            Kind.EXPENSE -> expensePayments(draft).map {Movement(it.key,Math.negateExact(it.value))}
            Kind.TRANSFER -> listOf(Movement(draft.accountId,-draft.amount),Movement(draft.destinationAccountId!!,draft.amount))
            else -> listOf(Movement(draft.accountId,draft.amount))
        }
        dao.insertEntries(movements.filter { it.amount!=0L }.map { AccountEntryEntity(id(),draft.id,it.accountId,it.amount) })
        if(draft.kind==Kind.EXPENSE) {
            dao.insertShares(shares.map { ShareEntity(draft.id,it.key,it.value) }); dao.insertContributions(draft.contributions.map { ContributionEntity(draft.id,it.key,it.value) })
            // Commit a deterministic original bill allocation. Later group netting is only a suggestion.
            dao.insertObligations(Finance.settlements(net).map { ObligationEntity(id(),draft.id,it.from,it.to,it.amount) })
        }
        if(replaces!=null) dao.insertRevision(RevisionEntity(id(),replaces,draft.id,System.currentTimeMillis(),"Corrected transaction"))
        validateBalances()
        draft.id
    } }
    suspend fun loadDraft(txId:String):EntryDraft = storage { db.withTransaction {
        val tx=requireNotNull(dao.transaction(txId)); require(!tx.reversed && tx.kind !in listOf(Kind.REPAYMENT.name,Kind.ADJUSTMENT.name,Kind.OFFSET.name)) { "This record cannot be edited through expense entry" }
        val entries=dao.entriesFor(txId); val shares=dao.sharesFor(txId); val contributions=dao.contributionsFor(txId)
        val payments=if(tx.kind==Kind.EXPENSE.name) entries.filter {it.amount<0}.associate {it.accountId to Math.negateExact(it.amount)} else emptyMap()
        EntryDraft(kind=Kind.valueOf(tx.kind),amount=tx.amount,description=tx.description,accountId=entries.firstOrNull { it.amount<0 }?.accountId?:entries.firstOrNull()?.accountId?:dao.accounts().firstOrNull()?.id.orEmpty(),destinationAccountId=if(tx.kind==Kind.TRANSFER.name) entries.firstOrNull { it.amount>0 }?.accountId else null,categoryId=tx.categoryId,groupId=tx.groupId,date=tx.date,notes=tx.notes,participants=shares.map { it.personId }.ifEmpty { listOf("self") },contributions=contributions.associate { it.personId to it.amount }.ifEmpty { mapOf("self" to tx.amount) },splitRule=if(shares.isEmpty()) SplitRule.Equal else SplitRule.Fixed(shares.associate { it.personId to it.amount }),refundOf=tx.refundOf,selfPayments=payments.takeIf {it.size>1}.orEmpty())
    } }
    suspend fun detail(txId:String):RecordDetail = storage { db.withTransaction {
        val t=requireNotNull(dao.transaction(txId));val record=Record(t.id,Kind.valueOf(t.kind),t.description,t.amount,t.personalAmount,t.categoryId,t.groupId,t.date,t.notes,t.reversed);val settlements=dao.settlementsForRecords(listOf(txId))
        RecordDetail(record,dao.entriesFor(txId).map {Movement(it.accountId,it.amount)},dao.sharesFor(txId).associate {it.personId to it.amount},dao.contributionsFor(txId).associate {it.personId to it.amount},settlements.map {SuggestedSettlement(it.fromPerson,it.toPerson,it.amount)},dao.allocations().filter {allocation->settlements.any {it.id==allocation.settlementId}}.associate {it.obligationId to it.amount})
    } }
    fun details(txId:String):Flow<RecordDetail> = dao.observeTransaction(txId).map {detail(txId)}
    suspend fun reverse(txId:String) = storage { db.withTransaction { reverseInternal(txId); dao.insertRevision(RevisionEntity(id(),txId,null,System.currentTimeMillis(),"User reversed transaction")) } }
    private suspend fun validateBalances() {
        // A corrected/backdated entry before a reset cannot leak the previous month's cash into this month.
        dao.walletSources()?.let {source->dao.transactions().filter {it.id.startsWith("gpay-reset:${source.gpayId}:") && !it.reversed}.sortedBy {it.date}.forEach {reset->
            val target=dao.resetTargets().find {it.transactionId==reset.id}?.target?:MonthlyGpay.allocation
            val adjustment=Math.subtractExact(target,dao.balanceBefore(source.gpayId,reset.date))
            val recorded=Math.addExact(reset.amount,dao.resetCorrections(source.gpayId,reset.date))
            val difference=Math.subtractExact(adjustment,recorded)
            if(difference!=0L) {
                val tx="gpay-reset-correction:${id()}";val now=System.currentTimeMillis()
                dao.insertTransaction(TransactionEntity(tx,Kind.ADJUSTMENT.name,"GPay reset recalculation",difference,0,null,null,reset.date,"Recalculated after a historical ledger change. Original reset: ${reset.id}. Recorded at ${java.time.Instant.ofEpochMilli(now)}"))
                dao.insertEntries(listOf(AccountEntryEntity(id(),tx,source.gpayId,difference)))
                dao.insertRevision(RevisionEntity(id(),reset.id,tx,now,"GPay reset recalculation"))
            }
        }}
        val balances=dao.balances();Finance.total(balances.filterNot {it.archived}.map {it.balance})
    }
    private suspend fun reverseInternal(txId:String) {
        val tx=requireNotNull(dao.transaction(txId)); require(!tx.reversed) { "Already reversed" }
        require(!tx.id.startsWith("gpay-reset")) {"Monthly resets cannot be reversed; use a balance correction"}
        val debts=dao.debts().filter { it.transactionId==txId }
        require(debts.all { it.amount==it.outstanding }) { "Reverse linked repayments before correcting this bill" }
        require(dao.transactions().none {it.kind==Kind.REFUND.name && it.refundOf==txId && !it.reversed }) { "Reverse linked refunds before correcting this expense" }
        dao.markReversed(txId)
        if(tx.kind==Kind.OFFSET.name) {
            val pair=dao.revisions().single {it.reason=="Mutual offset" && (it.originalId==txId || it.replacementId==txId)}
            dao.markReversed(if(pair.originalId==txId) requireNotNull(pair.replacementId) else pair.originalId)
        }
        validateBalances()
    }
    suspend fun recordRepayment(from:String,to:String,amount:Long,accountId:String?,note:String,selectedDebt:String?=null,date:Long=System.currentTimeMillis(),requestId:String=id(),groupId:String?=null):String = storage { db.withTransaction {
        if(dao.transaction(requestId)!=null) return@withTransaction requestId
        require(from!=to)
        val available=dao.debts().map {Debt(it.id,it.transactionId,it.fromPerson,it.toPerson,it.amount,it.outstanding,it.date,it.groupId)}.filter {it.outstanding>0 && (selectedDebt==null||it.id==selectedDebt) && (groupId==null || it.groupId==groupId)}
        val allocation=DebtRouting.allocate(amount,from,to,available)
        if(from=="self" || to=="self") require(dao.accounts().any { it.id==accountId && !it.archived }) { "Choose an active payment account" }
        val tx=requestId; dao.insertTransaction(TransactionEntity(tx,Kind.REPAYMENT.name,"Repayment",amount,0,null,groupId,date,note))
        val settlement=id(); dao.insertSettlement(SettlementEntity(settlement,tx,from,to,amount)); dao.insertAllocations(allocation.map { AllocationEntity(settlement,it.key,it.value) })
        if(from=="self" || to=="self") dao.insertEntries(listOf(AccountEntryEntity(id(),tx,accountId!!,if(to=="self") amount else -amount)))
        validateBalances()
        tx
    } }
    suspend fun recordOffset(first:String,second:String,amount:Long,groupId:String?=null) = storage {db.withTransaction {
        require(first!=second && amount>0)
        val debts=dao.debts().filter {it.outstanding>0 && (groupId==null || it.groupId==groupId)}
        val forward=Finance.allocate(amount,debts.filter {it.fromPerson==first && it.toPerson==second}.map {it.id to it.outstanding})
        val backward=Finance.allocate(amount,debts.filter {it.fromPerson==second && it.toPerson==first}.map {it.id to it.outstanding})
        val time=System.currentTimeMillis();val firstTx=id();val secondTx=id()
        listOf(Triple(firstTx,first,second) to forward,Triple(secondTx,second,first) to backward).forEach {(direction,allocation)->
            dao.insertTransaction(TransactionEntity(direction.first,Kind.OFFSET.name,"Mutual debt offset",amount,0,null,groupId,time,"Explicitly offset equal opposite obligations; no cash changed hands"))
            val settlement=id();dao.insertSettlement(SettlementEntity(settlement,direction.first,direction.second,direction.third,amount));dao.insertAllocations(allocation.map {AllocationEntity(settlement,it.key,it.value)})
        }
        dao.insertRevision(RevisionEntity(id(),firstTx,secondTx,time,"Mutual offset"))
    }}
}




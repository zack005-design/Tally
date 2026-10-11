package com.smartexpense.data
import androidx.room.withTransaction
import com.smartexpense.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class BackupService @Inject constructor(private val db:TrackerDatabase) {
    private val dao=db.dao()
    private fun row(vararg values:Any?):JSONArray=JSONArray().apply {values.forEach {put(it?:JSONObject.NULL)}}
    private fun <T> table(items:List<T>,encode:(T)->JSONArray)=JSONArray().apply {items.forEach {put(encode(it))}}
    suspend fun export(password:CharArray):ByteArray=withContext(Dispatchers.IO) {
        val json=db.withTransaction {JSONObject().apply {
            put("version",4);put("currency","INR")
            put("localPlans",table(dao.plans()) {row(it.key,it.kind,it.json)})
            put("gpaySchedule",table(listOfNotNull(dao.gpaySchedule())) {row(it.id,it.enabled,it.target,it.day,it.startMonth,it.nextMonth)})
            put("resetTargets",table(dao.resetTargets()) {row(it.transactionId,it.target)})
            put("diary",table(dao.diary()) {row(it.date,it.text)})
            put("localSettings",table(listOfNotNull(dao.localSettings())) {row(it.id,it.name,it.avatar,it.welcomeDone,it.lastReviewMonth)})
            put("walletSources",table(listOfNotNull(dao.walletSources())) {row(it.id,it.walletId,it.gpayId,it.lastResetMonth)})
            put("accounts",table(dao.accounts()) {row(it.id,it.name,it.type,it.archived)})
            put("people",table(dao.people()) {row(it.id,it.name,it.aliases)})
            put("groups",table(dao.groups()) {row(it.id,it.name)})
            put("members",table(dao.members()) {row(it.groupId,it.personId)})
            put("categories",table(dao.categories()) {row(it.id,it.name,it.color,it.icon,it.archived)})
            put("transactions",table(dao.transactions()) {row(it.id,it.kind,it.description,it.amount,it.personalAmount,it.categoryId,it.groupId,it.date,it.notes,it.reversed,it.refundOf)})
            put("entries",table(dao.entries()) {row(it.id,it.transactionId,it.accountId,it.amount)})
            put("shares",table(dao.shares()) {row(it.transactionId,it.personId,it.amount)})
            put("contributions",table(dao.contributions()) {row(it.transactionId,it.personId,it.amount)})
            put("obligations",table(dao.obligations()) {row(it.id,it.transactionId,it.fromPerson,it.toPerson,it.amount)})
            put("settlements",table(dao.settlements()) {row(it.id,it.transactionId,it.fromPerson,it.toPerson,it.amount)})
            put("allocations",table(dao.allocations()) {row(it.settlementId,it.obligationId,it.amount)})
            put("budgets",table(dao.budgets()) {row(it.id,it.month,it.categoryId,it.amount)})
            put("revisions",table(dao.revisions()) {row(it.id,it.originalId,it.replacementId,it.date,it.reason)})
        }.toString()}
        BackupEnvelope.encode(json.toByteArray(Charsets.UTF_8),password)
    }
    suspend fun inspect(data:ByteArray,password:CharArray):String=withContext(Dispatchers.IO) {val bundle=parse(data,password);bundle.validate();"${bundle.accounts.size} accounts, ${bundle.transactions.size} transactions, ${bundle.people.size-1} friends"}
    suspend fun restore(data:ByteArray,password:CharArray)=withContext(Dispatchers.IO) {
        val bundle=parse(data,password);bundle.validate()
        db.withTransaction {
            // Fixed table names, dependency order; any insert/constraint failure rolls back deletion.
            listOf("localPlans","resetTargets","gpaySchedule","diary","localSettings","walletSources","allocations","settlements","obligations","entries","shares","contributions","revisions","transactions","members","budgets","groups","categories","accounts","people").forEach {db.openHelper.writableDatabase.execSQL("DELETE FROM `$it`")}
            bundle.accounts.forEach {dao.insertAccount(it)};bundle.people.forEach {dao.insertPerson(it)};bundle.groups.forEach {dao.insertGroup(it)};dao.insertMembers(bundle.members);bundle.categories.forEach {dao.insertCategory(it)}
            bundle.transactions.forEach {dao.insertTransaction(it)};dao.insertEntries(bundle.entries);dao.insertShares(bundle.shares);dao.insertContributions(bundle.contributions);dao.insertObligations(bundle.obligations);bundle.settlements.forEach {dao.insertSettlement(it)};dao.insertAllocations(bundle.allocations);bundle.budgets.forEach {dao.saveBudget(it)};bundle.revisions.forEach {dao.insertRevision(it)}
            bundle.walletSources.forEach {dao.saveWalletSources(it)}
            bundle.schedules.forEach {dao.saveGpaySchedule(it)}
            bundle.plans.forEach {dao.savePlan(it)}
            val targets=bundle.resetTargets.associateBy {it.transactionId}
            bundle.transactions.filter {it.id.startsWith("gpay-reset:")}.forEach {dao.saveResetTarget(targets[it.id]?:ResetTargetEntity(it.id,MonthlyGpay.allocation))}
            bundle.diary.forEach {dao.saveDiary(it)}
            if(bundle.settings.isEmpty()) dao.saveLocalSettings(LocalSettingsEntity(welcomeDone=true,lastReviewMonth=java.time.YearMonth.now().toString()))
            else bundle.settings.forEach {dao.saveLocalSettings(it)}
        }
    }
    private fun parse(data:ByteArray,password:CharArray):BackupBundle {
        val json=JSONObject(BackupEnvelope.decode(data,password).toString(Charsets.UTF_8))
        require(json.getInt("version") in 1..4 && json.getString("currency")=="INR") {"Unsupported backup version or currency"}
        fun <T> rows(name:String,decode:(JSONArray)->T):List<T> {val array=json.getJSONArray(name);require(array.length()<=100000) {"Too many records"};return (0 until array.length()).map {decode(array.getJSONArray(it))}}
        return BackupBundle(
            rows("accounts") {AccountEntity(it.s(0),it.s(1),it.s(2),it.b(3))},rows("people") {PersonEntity(it.s(0),it.s(1),it.s(2))},rows("groups") {GroupEntity(it.s(0),it.s(1))},rows("members") {GroupMemberEntity(it.s(0),it.s(1))},rows("categories") {CategoryEntity(it.s(0),it.s(1),it.n(2),it.s(3),it.b(4))},
            rows("transactions") {TransactionEntity(it.s(0),it.s(1),it.s(2),it.n(3),it.n(4),it.optional(5),it.optional(6),it.n(7),it.s(8),it.b(9),it.optional(10))},rows("entries") {AccountEntryEntity(it.s(0),it.s(1),it.s(2),it.n(3))},rows("shares") {ShareEntity(it.s(0),it.s(1),it.n(2))},rows("contributions") {ContributionEntity(it.s(0),it.s(1),it.n(2))},rows("obligations") {ObligationEntity(it.s(0),it.s(1),it.s(2),it.s(3),it.n(4))},rows("settlements") {SettlementEntity(it.s(0),it.s(1),it.s(2),it.s(3),it.n(4))},rows("allocations") {AllocationEntity(it.s(0),it.s(1),it.n(2))},rows("budgets") {BudgetEntity(it.s(0),it.s(1),it.optional(2),it.n(3))},rows("revisions") {RevisionEntity(it.s(0),it.s(1),it.optional(2),it.n(3),it.s(4))},if(json.getInt("version")>=2) rows("walletSources") {WalletSourcesEntity(it.n(0).toInt(),it.s(1),it.s(2),it.s(3))} else emptyList(),            if(json.getInt("version")>=3) rows("gpaySchedule") {GpayScheduleEntity(it.n(0).toInt(),it.b(1),it.n(2),it.n(3).toInt(),it.s(4),it.s(5))} else emptyList(),
            if(json.getInt("version")>=3) rows("resetTargets") {ResetTargetEntity(it.s(0),it.n(1))} else emptyList(),
            if(json.getInt("version")>=3) rows("diary") {DiaryEntity(it.s(0),it.s(1))} else emptyList(),
            if(json.getInt("version")>=3) rows("localSettings") {LocalSettingsEntity(it.n(0).toInt(),it.s(1),it.s(2),it.b(3),it.s(4))} else emptyList(),if(json.getInt("version")>=4) rows("localPlans") {PlanEntity(it.s(0),it.s(1),it.s(2))} else emptyList())
    }
    private fun JSONArray.s(i:Int):String {val value=get(i);require(value is String && value.length<=10000) {"Invalid backup text"};return value}
    private fun JSONArray.n(i:Int):Long {val value=get(i);require(value is Int || value is Long) {"Money must be stored in integer paise"};return (value as Number).toLong()}
    private fun JSONArray.b(i:Int):Boolean {val value=get(i);require(value is Boolean);return value}
    private fun JSONArray.optional(i:Int):String?=if(isNull(i)) null else s(i)
}

private data class BackupBundle(val accounts:List<AccountEntity>,val people:List<PersonEntity>,val groups:List<GroupEntity>,val members:List<GroupMemberEntity>,val categories:List<CategoryEntity>,val transactions:List<TransactionEntity>,val entries:List<AccountEntryEntity>,val shares:List<ShareEntity>,val contributions:List<ContributionEntity>,val obligations:List<ObligationEntity>,val settlements:List<SettlementEntity>,val allocations:List<AllocationEntity>,val budgets:List<BudgetEntity>,val revisions:List<RevisionEntity>,val walletSources:List<WalletSourcesEntity>,val schedules:List<GpayScheduleEntity>,val resetTargets:List<ResetTargetEntity>,val diary:List<DiaryEntity>,val settings:List<LocalSettingsEntity>,val plans:List<PlanEntity>) {
    fun validate() {
        require(plans.map {it.key}.distinct().size==plans.size && plans.size<=10000) {"Invalid planning records"}
        val planGroups=groups.map {group->Group(group.id,group.name,members.filter {it.groupId==group.id}.map {it.personId})}
        plans.forEach {PlanCodec.validate(it,planGroups,accounts.map {it.id}.toSet(),categories.map {it.id}.toSet())}
        require(schedules.size<=1 && schedules.all {it.id==1 && it.day in 1..41 && it.target>=0 && walletSources.isNotEmpty()}) {"Invalid GPay reset settings"}
        schedules.forEach {GpaySchedule(it.enabled,it.target,it.day,it.startMonth,it.nextMonth);require(it.nextMonth>=it.startMonth)}
        require(settings.size<=1 && settings.all {it.id==1 && it.name.length<=100 && it.avatar in listOf("initials","wallet","leaf","star")}) {"Invalid local profile"}
        settings.filter {it.lastReviewMonth.isNotEmpty()}.forEach {java.time.YearMonth.parse(it.lastReviewMonth)}
        require(diary.map {it.date}.distinct().size==diary.size && diary.all {it.text.length<=10000}) {"Invalid diary"}
        diary.forEach {java.time.LocalDate.parse(it.date)}
        require(resetTargets.map {it.transactionId}.distinct().size==resetTargets.size && resetTargets.all {it.target>=0 && transactions.any {tx->tx.id==it.transactionId && tx.id.startsWith("gpay-reset:") && tx.kind==Kind.ADJUSTMENT.name}}) {"Invalid reset history"}
        fun unique(ids:List<String>) {require(ids.distinct().size==ids.size && ids.all {it.isNotBlank()}) {"Duplicate or empty backup IDs"}}
        listOf(accounts.map {it.id},people.map {it.id},groups.map {it.id},categories.map {it.id},transactions.map {it.id},entries.map {it.id},obligations.map {it.id},settlements.map {it.id},budgets.map {it.id},revisions.map {it.id}).forEach(::unique)
        unique(members.map {"${it.groupId}:${it.personId}"});unique(shares.map {"${it.transactionId}:${it.personId}"});unique(contributions.map {"${it.transactionId}:${it.personId}"});unique(allocations.map {"${it.settlementId}:${it.obligationId}"})
        require(walletSources.size<=1 && walletSources.all {it.id==1 && it.walletId!=it.gpayId && accounts.any {a->a.id==it.walletId && !a.archived} && accounts.any {a->a.id==it.gpayId && !a.archived}}) {"Invalid wallet sources"}
        walletSources.forEach {java.time.YearMonth.parse(it.lastResetMonth)}
        require(people.count {it.id=="self"}==1) {"Backup must have exactly one user"}
        val tx=transactions.associateBy {it.id};val personIds=people.map {it.id}.toSet();val accountIds=accounts.map {it.id}.toSet();val categoryIds=categories.map {it.id}.toSet();val groupIds=groups.map {it.id}.toSet()
        require(accounts.all {it.type in listOf("Cash","Bank","Wallet","Other") && it.name.isNotBlank()})
        require(members.all {it.groupId in groupIds && it.personId in personIds})
        require(entries.all {it.transactionId in tx && it.accountId in accountIds})
        require((shares.map {it.transactionId to it.personId}+contributions.map {it.transactionId to it.personId}).all {it.first in tx && it.second in personIds})
        transactions.forEach {t ->
            val kind=Kind.valueOf(t.kind);require(t.categoryId==null || t.categoryId in categoryIds);require(t.groupId==null || t.groupId in groupIds)
            if(kind!=Kind.ADJUSTMENT) require(t.amount>0);require(t.personalAmount>=0)
            require(kind==Kind.REFUND || t.refundOf==null) {"Non-refund has an invalid expense link"}
            val movements=entries.filter {it.transactionId==t.id};val allocated=shares.filter {it.transactionId==t.id};val paid=contributions.filter {it.transactionId==t.id}
            when(kind) {
                Kind.EXPENSE -> {
                    require(allocated.isNotEmpty() && allocated.all {it.amount>=0} && Finance.total(allocated.map {it.amount})==t.amount)
                    val net=Finance.net(allocated.associate {it.personId to it.amount},paid.associate {it.personId to it.amount})
                    require(t.personalAmount==(allocated.find {it.personId=="self"}?.amount?:0))
                    val selfPaid=paid.find {it.personId=="self"}?.amount?:0
                    require(movements.map {it.accountId}.distinct().size==movements.size && movements.all {it.amount<0} && Finance.total(movements.map {it.amount})==Math.negateExact(selfPaid)) {"Expense account movements do not match its payer contribution"}
                    val original=obligations.filter {it.transactionId==t.id};val reconstructed=net.keys.associateWith {id->Finance.total(original.filter {it.toPerson==id}.map {it.amount})-Finance.total(original.filter {it.fromPerson==id}.map {it.amount})}
                    require(original.all {it.fromPerson in net && it.toPerson in net}) {"Obligation has participants outside its bill"}
                    require(original.all {(net[it.fromPerson]?:0)<0 && (net[it.toPerson]?:0)>0}) {"Expense has fabricated debt cycles"}
                    require(net==reconstructed) {"Expense obligations do not reconcile"}
                }
                Kind.TRANSFER -> require(movements.size==2 && movements.map {it.accountId}.distinct().size==2 && movements.map {it.amount}.sorted()==listOf(-t.amount,t.amount) && t.personalAmount==0L)
                Kind.INCOME -> require(movements.size==1 && movements.single().amount==t.amount && t.personalAmount==t.amount)
                Kind.REFUND -> {
                    val original=requireNotNull(tx[t.refundOf]) {"Refund has no original expense"}
                    require(original.kind==Kind.EXPENSE.name && t.categoryId==original.categoryId && shares.filter {it.transactionId==original.id}.map {it.personId}==listOf("self")) {"Refund must refer to a personal expense in the same category"}
                    require(t.reversed || !original.reversed) {"Active refund refers to a reversed expense"}
                    require(movements.size==1 && movements.single().amount==t.amount && t.personalAmount==t.amount)
                }
                Kind.REPAYMENT -> require(t.personalAmount==0L)
                Kind.OFFSET -> require(t.personalAmount==0L && movements.isEmpty())
                Kind.ADJUSTMENT -> require(movements.size==1 && movements.single().amount==t.amount && t.personalAmount==0L)
            }
            if(kind!=Kind.EXPENSE) require(allocated.isEmpty() && paid.isEmpty() && obligations.none {it.transactionId==t.id}) {"Non-expense has fabricated expense data"}
        }
        transactions.filter {it.kind==Kind.EXPENSE.name}.forEach {original->require(Finance.total(transactions.filter {it.kind==Kind.REFUND.name && it.refundOf==original.id && !it.reversed}.map {it.amount})<=original.personalAmount) {"Refunds exceed original expense"}}
        require(obligations.all {it.transactionId in tx && it.fromPerson in personIds && it.toPerson in personIds && it.fromPerson!=it.toPerson && it.amount>0})
        val obligationMap=obligations.associateBy {it.id};val settlementMap=settlements.associateBy {it.id}
        require(allocations.all {it.settlementId in settlementMap && it.obligationId in obligationMap && it.amount>0})
        settlements.forEach {settlement ->
            val transaction=requireNotNull(tx[settlement.transactionId]);require(transaction.kind in listOf(Kind.REPAYMENT.name,Kind.OFFSET.name) && transaction.amount==settlement.amount)
            require(settlement.fromPerson in personIds && settlement.toPerson in personIds && settlement.fromPerson!=settlement.toPerson)
            val applied=allocations.filter {it.settlementId==settlement.id}
            val paths=applied.map {a->val o=obligationMap.getValue(a.obligationId);Debt(o.id,o.transactionId,o.fromPerson,o.toPerson,o.amount,a.amount,tx.getValue(o.transactionId).date)}
            require(DebtRouting.allocate(settlement.amount,settlement.fromPerson,settlement.toPerson,paths)==applied.associate {it.obligationId to it.amount}) {"Settlement allocations do not form valid debt paths"}
            val movements=entries.filter {it.transactionId==transaction.id}
            if(transaction.kind==Kind.REPAYMENT.name && (settlement.fromPerson=="self" || settlement.toPerson=="self")) require(movements.size==1 && movements.single().amount==(if(settlement.toPerson=="self") settlement.amount else -settlement.amount)) else require(movements.isEmpty())
        }
        require(transactions.filter {it.kind in listOf(Kind.REPAYMENT.name,Kind.OFFSET.name)}.all {t->settlements.count {it.transactionId==t.id}==1})
        transactions.filter {it.kind==Kind.OFFSET.name}.forEach {offset->
            val pair=revisions.single {it.reason=="Mutual offset" && (it.originalId==offset.id || it.replacementId==offset.id)}
            val other=tx.getValue(if(pair.originalId==offset.id) requireNotNull(pair.replacementId) else pair.originalId)
            require(other.kind==Kind.OFFSET.name && other.amount==offset.amount && other.reversed==offset.reversed)
            val a=settlements.single {it.transactionId==offset.id};val b=settlements.single {it.transactionId==other.id};require(a.fromPerson==b.toPerson && a.toPerson==b.fromPerson)
        }
        obligations.forEach {o ->val paid=Finance.total(allocations.filter {it.obligationId==o.id && tx[settlementMap[it.settlementId]?.transactionId]?.reversed==false}.map {it.amount});require(paid<=o.amount);if(tx[o.transactionId]?.reversed==true) require(paid==0L)}
        require(budgets.all {it.amount>0 && (it.categoryId==null||it.categoryId in categoryIds)});budgets.forEach {java.time.YearMonth.parse(it.month)}
        require(revisions.all {it.originalId in tx && (it.replacementId==null || it.replacementId in tx)})
        accounts.forEach {account->Finance.total(entries.filter {it.accountId==account.id && tx[it.transactionId]?.reversed==false}.map {it.amount})}
    }
}

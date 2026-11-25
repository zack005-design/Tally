package com.smartexpense.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartexpense.data.*
import com.smartexpense.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppUiState(val snapshot:Snapshot=Snapshot(),val loading:Boolean=true,val busy:Boolean=false,val message:String?=null)
data class BackupUiState(val password:String="",val visible:Boolean=false,val restoreSummary:String?=null)
@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppViewModel @Inject constructor(val repository:FinanceRepository,val backup:BackupService,val billScanner:BillScanner,private val saved:SavedStateHandle,private val commandParser:LocalCommandParser?=null):ViewModel() {
    private val mutable=MutableStateFlow(AppUiState())
    val state=mutable.asStateFlow()
    private val route=MutableStateFlow("home")
    private val month=MutableStateFlow(saved.get<String>("reportMonth")?:java.time.YearMonth.now().toString())
    val reportMonth=month.asStateFlow()
    private val reviewMonthMutable=MutableStateFlow(saved.get<String>("reviewMonth")?:java.time.YearMonth.now().minusMonths(1).toString())
    val reviewMonth=reviewMonthMutable.asStateFlow()
    private val reviewMutable=MutableStateFlow<MonthlyReview?>(null)
    val monthlyReview=reviewMutable.asStateFlow()
    fun selectReviewMonth(value:String) {
        java.time.YearMonth.parse(value);reviewMonthMutable.value=value;saved["reviewMonth"]=value;reviewMutable.value=null;loadReview()
    }
    private fun loadReview() {
        val selected=reviewMonthMutable.value
        viewModelScope.launch {
            try {val result=repository.monthlyReview(selected);if(reviewMonthMutable.value==selected) reviewMutable.value=result}
            catch(e:CancellationException) {throw e}
            catch(e:Exception) {mutable.update {it.copy(message=e.message?:"Could not load monthly review")}}
        }
    }
    private fun refreshOpenReview() {
        if(route.value=="monthly-review") loadReview()
    }
    fun acknowledgeReview(onDone:()->Unit)=action("Monthly review saved") {
        repository.acknowledgeReview(java.time.YearMonth.now().toString());onDone()
    }
    fun observeRoute(value:String) {route.value=value}
    fun selectMonth(value:String) {if(runCatching {java.time.YearMonth.parse(value)}.isSuccess) {month.value=value;saved["reportMonth"]=value}}
    private val form=MutableStateFlow(EntryUiState(
        id=saved["draftId"]?:java.util.UUID.randomUUID().toString(),kind=saved.get<String>("kind")?.let(Kind::valueOf)?:Kind.EXPENSE,
        amount=saved["amount"]?:"",description=saved["description"]?:"",accountId=saved["account"]?:"",destinationId=saved["destination"]?:"",text=saved["text"]?:"",
        categoryId=saved["category"],groupId=saved["group"],shared=saved["shared"]?:false,participants=saved.get<ArrayList<String>>("participants")?:listOf("self"),
        paid=saved.get<HashMap<String,String>>("paid")?:emptyMap(),method=saved["method"]?:"Equal",values=saved.get<HashMap<String,String>>("values")?:emptyMap(),items=saved["items"]?:"",billItemNames=saved.get<ArrayList<String>>("billItemNames")?:emptyList(),
        date=saved["date"]?:java.time.LocalDateTime.now().withNano(0).toString(),notes=saved["notes"]?:"",parserNotice=saved["notice"]?:"",replaces=saved["replaces"],refundOf=saved["refundOf"],secondAccountId=saved["secondAccount"]?:"",secondPayment=saved["secondPayment"]?:"",itemCharges=saved["itemCharges"]?:"",itemDiscount=saved["itemDiscount"]?:"",originalMovements=saved.get<HashMap<String,Long>>("originalMovements")?:emptyMap(),originalDate=saved["originalDate"]
    ))
    val entry=form.asStateFlow()
    private val launchMutable=MutableStateFlow(saved.get<String>("externalLaunch"))
    val launchRequest=launchMutable.asStateFlow()
    fun requestLaunch(destination:String) {require(destination in listOf("backup","quick-add","budgets"));saved["externalFavourite"]=null;saved["externalLaunch"]=destination;launchMutable.value=destination}
    val requestedFavouriteId:String? get()=saved["externalFavourite"]
    fun requestFavourite(id:String) {
        if(id.isBlank() || id.length>200) {requestLaunch("quick-add");return}
        saved["externalFavourite"]=id;saved["externalLaunch"]="favourite";launchMutable.value="favourite"
    }
    fun consumeLaunch() {saved["externalFavourite"]=null;saved["externalLaunch"]=null;launchMutable.value=null}
    private var pendingBill:ScannedBill?=null
    val billExpected=MutableStateFlow(saved.get<Boolean>("billExpected")?:false)
    fun discardDraftBill() {pendingBill=null;billExpected.value=false;saved["billExpected"]=false}
    fun prepareBill(bill:ScannedBill,suggestion:BillSuggestion,category:String?) {
        newEntry();pendingBill=bill;billExpected.value=true;saved["billExpected"]=true
        change(entry.value.copy(amount=suggestion.total?.let(Money::input).orEmpty(),description=suggestion.merchant.ifBlank {"Printed bill"},categoryId=category,
            date=suggestion.date?.let {java.time.LocalDate.parse(it).atTime(12,0).toString()}?:entry.value.date,
            items=suggestion.items.joinToString("\n") {"${Money.input(it.amount)}=self"},
            billItemNames=suggestion.items.map {it.name},
            notes=("Imported printed bill · reviewed locally\n"+suggestion.items.joinToString("\n") {"${it.name}: ${Money.input(it.amount)}"}).take(10000),
            parserNotice=suggestion.warnings.joinToString(" ")))
    }
    private val parsedRepayment=MutableStateFlow<ParsedDraft?>(null)
    val repaymentDraft=parsedRepayment.asStateFlow()
    private var pendingExportPassword:CharArray?=null
    private val backupMutable=MutableStateFlow(BackupUiState())
    val backupUi=backupMutable.asStateFlow()
    private var pendingRestoreBytes:ByteArray?=null
    private var pendingRestorePassword:CharArray?=null
    fun setBackupPassword(value:String) {backupMutable.update {it.copy(password=value)}}
    fun toggleBackupPassword() {backupMutable.update {it.copy(visible=!it.visible)}}
    fun prepareRestore(bytes:ByteArray,summary:String,password:String) {
        clearRestore();pendingRestoreBytes=bytes.copyOf();pendingRestorePassword=password.toCharArray()
        backupMutable.update {it.copy(restoreSummary=summary)}
    }
    fun clearRestore() {pendingRestoreBytes?.fill(0);pendingRestoreBytes=null;pendingRestorePassword?.fill('\u0000');pendingRestorePassword=null;backupMutable.update {it.copy(restoreSummary=null)}}
    suspend fun restorePrepared() {
        val bytes=requireNotNull(pendingRestoreBytes) {"Choose a backup to restore"}.copyOf()
        val password=requireNotNull(pendingRestorePassword).copyOf()
        try {backup.restore(bytes,password);repository.refreshMonthlyGpay();clearRestore()} finally {bytes.fill(0);password.fill('\u0000')}
    }
    fun prepareExport(password:String) {clearExport();pendingExportPassword=password.toCharArray()}
    fun exportPassword():CharArray=requireNotNull(pendingExportPassword) {"Export was interrupted. Choose Export again to confirm encryption settings."}.copyOf()
    fun clearExport() {pendingExportPassword?.fill('\u0000');pendingExportPassword=null}
    override fun onCleared() {clearExport();clearRestore();backupMutable.value=BackupUiState()}
    init {
        viewModelScope.launch {
            try { repository.initialize();
                try {repository.refreshMonthlyGpay()} catch(e:CancellationException) {throw e} catch(e:Exception) {mutable.update {it.copy(message=e.message?:"Could not apply monthly reset")}}
                combine(route,month) {screen,selected->
                val limit=when {screen=="diary" || screen.startsWith("diary/")->Int.MAX_VALUE;screen in listOf("home","activity","calendar","add","accounts","people","groups","split","recurring","balance-check","insights")->Int.MAX_VALUE;else->5}
                // Route entry refreshes time-dependent reports and runs monthly-review loading.
                Triple(screen,limit,selected)
            }.distinctUntilChanged().flatMapLatest {(_,limit,selected)->repository.observeSnapshots(limit,selected)}
                .collect { snapshot -> mutable.update { it.copy(snapshot=snapshot,loading=false) };refreshOpenReview() } }
            catch(e:CancellationException) { throw e } catch(e:Exception) { mutable.update { it.copy(loading=false,message=e.message?:"Could not load data") } }
        }
    }
    fun change(value:EntryUiState) {
        form.value=value; saved["draftId"]=value.id;saved["kind"]=value.kind.name;saved["amount"]=value.amount;saved["description"]=value.description;saved["account"]=value.accountId;saved["destination"]=value.destinationId;saved["text"]=value.text
        saved["category"]=value.categoryId;saved["group"]=value.groupId;saved["shared"]=value.shared;saved["participants"]=ArrayList(value.participants);saved["paid"]=HashMap(value.paid);saved["method"]=value.method;saved["values"]=HashMap(value.values);saved["items"]=value.items;saved["billItemNames"]=ArrayList(value.billItemNames);saved["date"]=value.date;saved["notes"]=value.notes;saved["notice"]=value.parserNotice;saved["replaces"]=value.replaces;saved["refundOf"]=value.refundOf;saved["secondAccount"]=value.secondAccountId;saved["secondPayment"]=value.secondPayment;saved["itemCharges"]=value.itemCharges;saved["itemDiscount"]=value.itemDiscount;saved["originalMovements"]=HashMap(value.originalMovements);saved["originalDate"]=value.originalDate
    }
    fun clearRepaymentDraft() {parsedRepayment.value=null}
    /** Remaining drafts from a several-expenses command, reviewed and saved one at a time. */
    private val queuedDrafts=ArrayDeque<Pair<String,ParsedDraft>>()
    private var queuedTotal=0
    /** True while the entry form holds the next draft of a several-expenses command. */
    var continuingQueue=false
        private set
    fun newEntry(kind:Kind=Kind.EXPENSE,shared:Boolean=false) {queuedDrafts.clear();continuingQueue=false;discardDraftBill();change(EntryUiState(kind=kind,shared=shared,accountId=state.value.snapshot.walletSources?.walletId.orEmpty())) }
    fun repeatGroup(group:Group) {newEntry(Kind.EXPENSE,true);change(withGroupDefault(entry.value,group))}
    fun repeatExpense(id:String,onReady:()->Unit) = action("Expense copy ready to review") {
        val detail=repository.detail(id)
        require(!detail.record.reversed && detail.record.kind==Kind.EXPENSE && detail.shares.keys==setOf("self")) {"Choose an active personal expense"}
        val draft=repository.loadDraft(id)
        discardDraftBill()
        change(EntryUiState(amount=Money.input(draft.amount),description=draft.description,accountId=draft.accountId,categoryId=draft.categoryId,
            secondAccountId=draft.selfPayments.keys.firstOrNull {it!=draft.accountId}.orEmpty(),
            secondPayment=draft.selfPayments.entries.firstOrNull {it.key!=draft.accountId}?.value?.let(Money::input).orEmpty(),
            parserNotice="Copy for today. Review the amount and payment account before saving."))
        onReady()
    }
    fun favouriteEntry(value:FavouriteExpense) {
        discardDraftBill()
        change(EntryUiState(amount=Money.input(value.amount),description=value.description,accountId=value.accountId,categoryId=value.categoryId,
            parserNotice="Favourite draft. Review the amount, account and category before saving."))
    }
    fun withGroupDefault(value:EntryUiState,group:Group):EntryUiState {
        val entry=value.withGroup(group)
        val default=state.value.snapshot.groupDefaults.find {it.groupId==group.id}?:return entry
        return if(runCatching {default.validate(entry.participants)}.isSuccess) entry.copy(method=default.method,values=default.values) else entry
    }
    fun recurringEntry(template:RecurringTemplate,date:java.time.LocalDate) {
        discardDraftBill()
        change(EntryUiState(id=template.entryId(date),kind=template.kind,amount=Money.input(template.amount),description=template.description,accountId=template.accountId,date=date.atTime(12,0).toString(),notes="Recurring template · ${template.frequency}"))
    }
    fun refreshWallet() {
        if(state.value.loading) return
        viewModelScope.launch {
            try {repository.refreshMonthlyGpay();refreshOpenReview()}
            catch(e:CancellationException) {throw e}
            catch(e:Exception) {mutable.update {it.copy(message=e.message?:"Could not refresh GPay balance")}}
        }
    }
    fun dismissMessage() { mutable.update { it.copy(message=null) } }
    fun action(success:String,block:suspend ()->Unit) {
        if(mutable.value.busy) return
        mutable.update { it.copy(busy=true) }
        viewModelScope.launch {
            try { block(); mutable.update { it.copy(busy=false,message=success) } }
            catch(e:CancellationException) { mutable.update { it.copy(busy=false) }; throw e }
            catch(e:Exception) { mutable.update { it.copy(busy=false,message=e.message?:"Operation failed") } }
        }
    }
    fun saveEntry(onSaved:(String)->Unit) = action("Transaction saved") {
        val id=if(billExpected.value) {require(form.value.replaces==null) {"Discard this imported attachment before correcting an existing transaction."};val bill=requireNotNull(pendingBill) {"Bill review was interrupted. Import the bill again, or discard its attachment explicitly."};repository.saveWithBill(form.value.draft(),bill)}
        else repository.save(form.value.draft(),form.value.replaces)
        val rest=queuedDrafts.toList();val total=queuedTotal
        newEntry()
        rest.firstOrNull()?.let {(segment,next)->
            queuedDrafts.addAll(rest.drop(1));queuedTotal=total;continuingQueue=true
            applyParsedText(queuedNotice(next,total-rest.size+1,total),form.value.copy(text=segment))
        }
        onSaved(id)
    }
    fun edit(id:String,duplicate:Boolean=false,onReady:()->Unit) = action(if(duplicate) "Copy ready to review" else "Correction ready to review") {
        val d=repository.loadDraft(id)
        val original=if(duplicate) emptyMap() else repository.detail(id).entries.associate {it.accountId to it.amount}
        discardDraftBill()
        change(EntryUiState(kind=d.kind,amount=Money.input(d.amount),description=d.description,accountId=d.accountId,destinationId=d.destinationAccountId.orEmpty(),categoryId=d.categoryId,groupId=d.groupId,shared=d.participants!=listOf("self"),participants=d.participants,paid=d.contributions.mapValues { Money.input(it.value) },method=if(d.participants!=listOf("self")) "Fixed" else "Equal",values=(d.splitRule as? SplitRule.Fixed)?.amounts?.mapValues { Money.input(it.value) }.orEmpty(),date=java.time.Instant.ofEpochMilli(d.date).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().toString(),notes=d.notes,replaces=if(duplicate) null else id,refundOf=d.refundOf,secondAccountId=d.selfPayments.keys.firstOrNull {it!=d.accountId}.orEmpty(),originalMovements=original,originalDate=if(duplicate) null else d.date,secondPayment=d.selfPayments.entries.firstOrNull {it.key!=d.accountId}?.value?.let(Money::input).orEmpty()));onReady()
    }
    private var parseRequest = 0L
    /** Personal category model trained on this phone from saved entries; rebuilt when the records change. */
    private var personalCategories: Pair<Int,PersonalCategoryModel?>? = null
    private suspend fun refreshPersonalCategories() {
        val snapshot=state.value.snapshot
        val key=java.util.Objects.hash(snapshot.records.size,snapshot.records.firstOrNull()?.id,snapshot.transactionCount,snapshot.categories.map {it.id to it.archived})
        if(personalCategories?.first==key) return
        val active=snapshot.categories.filterNot {it.archived}.map {it.id}.toSet()
        personalCategories=key to kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { PersonalCategoryModel.train(snapshot.records,active) }
    }
    fun parseText() {
        val request = ++parseRequest
        val requested = form.value
        val people = state.value.snapshot.people.filter { it.id!="self" }.map { PersonRef(it.id,it.name,it.aliases) }
        viewModelScope.launch {
        // Spending questions are answered from local records instead of becoming a draft.
        if (SpendingQuestions.isQuestion(requested.text)) {
            val answer = try { val full = repository.snapshot(); SpendingQuestions.answer(requested.text, full.records, full.categories, java.time.LocalDate.now()) }
                catch (error: CancellationException) { throw error } catch (error: Exception) { null }
            if (request != parseRequest || form.value != requested) return@launch
            if (answer != null) { change(requested.copy(parserNotice = answer.text + " Nothing was saved.")); return@launch }
        }
        queuedDrafts.clear();continuingQueue=false
        try { refreshPersonalCategories() } catch (error: CancellationException) { throw error } catch (error: Exception) { personalCategories=null }
        suspend fun interpret(text:String)=commandParser?.parse(text, people) ?: kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { CommandExpenseTextParser(null).parse(text, people) }
        // Several complete personal expenses on one line become drafts reviewed and saved one by one.
        val segments=MultiExpense.segments(requested.text)
        if (segments.isNotEmpty()) {
            val drafts = try { segments.map { segment -> segment to interpret(segment) } } catch (error: CancellationException) { throw error } catch (error: Exception) { emptyList() }
            if (request != parseRequest || form.value != requested) return@launch
            if (drafts.size == segments.size && drafts.all { MultiExpense.isComplete(it.second) }) {
                queuedDrafts.addAll(drafts.drop(1));queuedTotal=drafts.size
                applyParsedText(queuedNotice(drafts.first().second,1,drafts.size),requested.copy(text=drafts.first().first))
                return@launch
            }
        }
        val parsed = try { interpret(requested.text) }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { if (request == parseRequest && form.value == requested) mutable.update { it.copy(message="Could not interpret this entry. Please enter the fields manually.") }; return@launch }
        if (request != parseRequest || form.value != requested) return@launch
        applyParsedText(parsed, requested)
        }
    }
    private fun queuedNotice(draft:ParsedDraft,position:Int,total:Int)=draft.copy(warnings=listOf("Expense $position of $total from your list. Save it to continue with the next one; nothing is saved automatically.")+draft.warnings)
    private fun applyParsedText(parsed: ParsedDraft, current: EntryUiState) {
        val snapshot=state.value.snapshot
        val source=resolveParsedAccount(parsed.fromAccount,snapshot.accounts,snapshot.walletSources)
        val destination=resolveParsedAccount(parsed.toAccount,snapshot.accounts,snapshot.walletSources)
        // The user's own saved entries take priority for category and fill a missing account; explicit account words win.
        val personal=if(parsed.kind==Kind.EXPENSE && parsed.contributions.keys.all { it=="self" } && parsed.splitRule==null)
            personalSuggestion(parsed.description,snapshot.records,snapshot.accounts.filterNot { it.archived }.map { it.id }.toSet(),snapshot.categories.map { it.id }.toSet())
            else PersonalSuggestion()
        val learned=if(personal.categoryId==null && parsed.kind==Kind.EXPENSE && parsed.contributions.keys.all { it=="self" } && parsed.splitRule==null)
            personalCategories?.second?.suggest(parsed.description)?.categoryId?.takeIf { id -> snapshot.categories.any { it.id==id && !it.archived } } else null
        val personalAccount=if(source==null) personal.accountId else null
        val accountSource=source?:personalAccount
        val missing=parsed.missing-(if(accountSource!=null) setOf("account") else emptySet())-(if(destination!=null) setOf("destination account") else emptySet())
        val history=listOfNotNull(personal.categoryId?.let { "category" },personalAccount?.let { "account" })
        val warnings=(if(personal.categoryId!=null || learned!=null) parsed.warnings.filterNot { it.startsWith("Category suggested on device") } else parsed.warnings)+
            listOfNotNull(learned?.let { "Category picked by your personal model, trained on this phone from your entries. Change it if it does not fit." })+
            (if(history.isEmpty()) emptyList() else listOf("Filled ${history.joinToString(" and ")} from your past entries. Change if needed."))+
            listOfNotNull(SmartInsights.possibleDuplicate(parsed.description,parsed.amount,parsed.date?:java.time.LocalDate.now(),snapshot.records)?.let { "You already saved ${it.description} for ${Money.format(it.amount)} that day. Check this is not a duplicate." })
        if(parsed.kind==Kind.REPAYMENT) {parsedRepayment.value=parsed;change(current.copy(parserNotice=(parsed.warnings+"Repayment draft ready. Review the person, direction, amount and account.").joinToString("\n")));return}
        change(current.copy(kind=parsed.kind?:current.kind,amount=parsed.amount?.let(Money::input).orEmpty(),description=parsed.description,notes=parsed.notes?.let { (current.notes+"\n"+it).trim() }?:current.notes,categoryId=personal.categoryId?:learned?:state.value.snapshot.categories.find { it.name==parsed.category }?.id,shared=parsed.contributions.keys.any { it!="self" } || "participants" in parsed.missing,participants=parsed.contributions.keys.toList().ifEmpty { listOf("self") },paid=parsed.contributions.mapValues { Money.input(it.value) },method=if("split method" in parsed.missing) "" else "Equal",accountId=accountSource?:current.accountId,secondAccountId="",secondPayment="",destinationId=destination.orEmpty(),date=parsed.date?.atTime(12,0)?.toString()?:current.date,parserNotice=(warnings+"Review all fields. Missing: ${missing.joinToString()}${if(parsed.date!=null) "; date identified, review time (12:00 draft default)" else ""}").joinToString("\n")))
    }
}


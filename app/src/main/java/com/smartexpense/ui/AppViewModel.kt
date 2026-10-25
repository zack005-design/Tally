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
    private val pendingReviewMutable=MutableStateFlow<String?>(null)
    val pendingReview=pendingReviewMutable.asStateFlow()
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
    private fun checkReview(snapshot:Snapshot) {
        val current=java.time.YearMonth.now().toString()
        if(snapshot.localSettings.lastReviewMonth!=current && snapshot.transactionCount>0) pendingReviewMutable.value=java.time.YearMonth.now().minusMonths(1).toString()
        if(route.value=="monthly-review") loadReview()
    }
    fun acknowledgeReview(onDone:()->Unit)=action("Monthly review saved") {
        repository.acknowledgeReview(java.time.YearMonth.now().toString());pendingReviewMutable.value=null;onDone()
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
    fun requestLaunch(destination:String) {require(destination in listOf("backup","quick-add"));saved["externalFavourite"]=null;saved["externalLaunch"]=destination;launchMutable.value=destination}
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
                combine(repository.changes,route,month) {_,screen,selected->
                val limit=when {screen=="diary" || screen.startsWith("diary/")->Int.MAX_VALUE;screen in listOf("home","activity","calendar","add","accounts","people","groups","split","recurring","balance-check","insights")->Int.MAX_VALUE;else->5}
                repository.snapshot(limit,selected)
            }.collect { snapshot -> mutable.update { it.copy(snapshot=snapshot,loading=false) };checkReview(snapshot) } }
            catch(e:CancellationException) { throw e } catch(e:Exception) { mutable.update { it.copy(loading=false,message=e.message?:"Could not load data") } }
        }
    }
    fun change(value:EntryUiState) {
        form.value=value; saved["draftId"]=value.id;saved["kind"]=value.kind.name;saved["amount"]=value.amount;saved["description"]=value.description;saved["account"]=value.accountId;saved["destination"]=value.destinationId;saved["text"]=value.text
        saved["category"]=value.categoryId;saved["group"]=value.groupId;saved["shared"]=value.shared;saved["participants"]=ArrayList(value.participants);saved["paid"]=HashMap(value.paid);saved["method"]=value.method;saved["values"]=HashMap(value.values);saved["items"]=value.items;saved["billItemNames"]=ArrayList(value.billItemNames);saved["date"]=value.date;saved["notes"]=value.notes;saved["notice"]=value.parserNotice;saved["replaces"]=value.replaces;saved["refundOf"]=value.refundOf;saved["secondAccount"]=value.secondAccountId;saved["secondPayment"]=value.secondPayment;saved["itemCharges"]=value.itemCharges;saved["itemDiscount"]=value.itemDiscount;saved["originalMovements"]=HashMap(value.originalMovements);saved["originalDate"]=value.originalDate
    }
    fun clearRepaymentDraft() {parsedRepayment.value=null}
    fun newEntry(kind:Kind=Kind.EXPENSE,shared:Boolean=false) {discardDraftBill();change(EntryUiState(kind=kind,shared=shared,accountId=state.value.snapshot.walletSources?.walletId.orEmpty())) }
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
            try {repository.refreshMonthlyGpay();checkReview(repository.snapshot(5))}
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
        newEntry();onSaved(id)
    }
    fun edit(id:String,duplicate:Boolean=false,onReady:()->Unit) = action(if(duplicate) "Copy ready to review" else "Correction ready to review") {
        val d=repository.loadDraft(id)
        val original=if(duplicate) emptyMap() else repository.detail(id).entries.associate {it.accountId to it.amount}
        discardDraftBill()
        change(EntryUiState(kind=d.kind,amount=Money.input(d.amount),description=d.description,accountId=d.accountId,destinationId=d.destinationAccountId.orEmpty(),categoryId=d.categoryId,groupId=d.groupId,shared=d.participants!=listOf("self"),participants=d.participants,paid=d.contributions.mapValues { Money.input(it.value) },method=if(d.participants!=listOf("self")) "Fixed" else "Equal",values=(d.splitRule as? SplitRule.Fixed)?.amounts?.mapValues { Money.input(it.value) }.orEmpty(),date=java.time.Instant.ofEpochMilli(d.date).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().toString(),notes=d.notes,replaces=if(duplicate) null else id,refundOf=d.refundOf,secondAccountId=d.selfPayments.keys.firstOrNull {it!=d.accountId}.orEmpty(),originalMovements=original,originalDate=if(duplicate) null else d.date,secondPayment=d.selfPayments.entries.firstOrNull {it.key!=d.accountId}?.value?.let(Money::input).orEmpty()));onReady()
    }
    private var parseRequest = 0L
    fun parseText() {
        val request = ++parseRequest
        val requested = form.value
        val people = state.value.snapshot.people.filter { it.id!="self" }.map { PersonRef(it.id,it.name,it.aliases) }
        viewModelScope.launch {
        val parsed = try { commandParser?.parse(requested.text, people) ?: kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { CommandExpenseTextParser(null).parse(requested.text, people) } }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { if (request == parseRequest && form.value == requested) mutable.update { it.copy(message="Could not interpret this entry. Please enter the fields manually.") }; return@launch }
        if (request != parseRequest || form.value != requested) return@launch
        applyParsedText(parsed, requested)
        }
    }
    private fun applyParsedText(parsed: ParsedDraft, current: EntryUiState) {
        val snapshot=state.value.snapshot
        val source=resolveParsedAccount(parsed.fromAccount,snapshot.accounts,snapshot.walletSources)
        val destination=resolveParsedAccount(parsed.toAccount,snapshot.accounts,snapshot.walletSources)
        val missing=parsed.missing-(if(source!=null) setOf("account") else emptySet())-(if(destination!=null) setOf("destination account") else emptySet())
        if(parsed.kind==Kind.REPAYMENT) {parsedRepayment.value=parsed;change(current.copy(parserNotice=(parsed.warnings+"Repayment draft ready. Review the person, direction, amount and account.").joinToString("\n")));return}
        change(current.copy(kind=parsed.kind?:current.kind,amount=parsed.amount?.let(Money::input).orEmpty(),description=parsed.description,categoryId=state.value.snapshot.categories.find { it.name==parsed.category }?.id,shared=parsed.contributions.keys.any { it!="self" } || "participants" in parsed.missing,participants=parsed.contributions.keys.toList().ifEmpty { listOf("self") },paid=parsed.contributions.mapValues { Money.input(it.value) },method=if("split method" in parsed.missing) "" else "Equal",accountId=source?:current.accountId,secondAccountId="",secondPayment="",destinationId=destination.orEmpty(),date=parsed.date?.atTime(12,0)?.toString()?:current.date,parserNotice=(parsed.warnings+"Review all fields. Missing: ${missing.joinToString()}${if(parsed.date!=null) "; date identified, review time (12:00 draft default)" else ""}").joinToString("\n")))
    }
}


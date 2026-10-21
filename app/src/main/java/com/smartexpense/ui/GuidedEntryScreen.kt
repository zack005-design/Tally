package com.smartexpense.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import com.smartexpense.R
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import com.smartexpense.domain.*

/** Everyday entry keeps actual payments separate from how a bill is shared. */
@Composable fun EntryScreen(s:EntryUiState,accounts:List<Account>,people:List<Person>,categories:List<Category>,groups:List<Group>,onChange:(EntryUiState)->Unit,onParse:()->Unit,onSave:()->Unit,records:List<Record> = emptyList(),onFriends:()->Unit={},onBack:()->Unit={},walletSources:WalletSources?=null,hasBill:Boolean=false,onDiscardBill:()->Unit={},onExchange:()->Unit={},onImportBill:()->Unit={}) {
    if(s.shared && s.kind==Kind.EXPENSE && !hasBill) {
        SharedBillScreen(s,accounts,people,categories,groups,onChange,onSave,onBack,onFriends,walletSources,onImportBill)
        return
    }
    var advanced by rememberSaveable(s.id) {mutableStateOf(hasBill || s.method in listOf("Itemized","Weighted") || (s.shared && "self" !in s.participants))}
    var step by rememberSaveable(s.id) {mutableIntStateOf(0)}
    var extras by rememberSaveable(s.id) {mutableStateOf(false)}
    var friendsExpanded by rememberSaveable(s.id) {mutableStateOf(false)}
    var advancedMeal by rememberSaveable(s.id) {mutableStateOf(false)}
    var choosingMethod by rememberSaveable(s.id) {mutableStateOf(false)}
    if(choosingMethod) {
        BackHandler {choosingMethod=false}
        Page("Split method",onBack={choosingMethod=false}) {
            item {EntryStageHeading("Choose a split method","Decide how to split the bill among ${s.participants.size} people.")}
            item {GroupedCard {
                listOf(Triple("Equal","Equal split","Everyone pays the same amount."),Triple("Fixed","Exact amounts","Set a specific amount for each person."),Triple("Percentage","Percentages","Split by percentage."),Triple("Weighted","Shares","Split by custom shares, e.g. 2:1:1."),Triple("Itemized","By item","Assign people to specific items.")).forEachIndexed {index,method->
                    Row(Modifier.fillMaxWidth().selectable(s.method==method.first,role=Role.RadioButton,onClick={onChange(s.copy(method=method.first))}).padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                        ReferenceSelectionMark(s.method==method.first)
                        Column(Modifier.weight(1f)) {Text(method.second,style=MaterialTheme.typography.titleSmall);Text(method.third,color=Tokens.muted,style=MaterialTheme.typography.bodySmall)}
                    }
                    if(index<4) GroupedDivider()
                }
            }}
            if(s.method in listOf("Fixed","Percentage","Weighted")) item {Panel {s.participants.forEach {id->
                val name=if(id=="self") "You" else people.find {it.id==id}?.let {personLabel(it,people)}?:"Friend"
                Field("$name ${if(s.method=="Percentage") "percent" else if(s.method=="Weighted") "weight" else "share (₹)"}",s.values[id].orEmpty(),{onChange(s.copy(values=s.values+(id to it)))},true)
            }}}
            if(s.method!="Itemized") item {SettlementPreview(s,people)}
            item {EditorialPrimaryButton(if(s.method=="Itemized") "Add and assign items" else "Continue") {choosingMethod=false;if(s.method=="Itemized") advanced=true}}
        }
        return
    }
    val namedBill=hasBill && s.billItemNames.isNotEmpty() && s.kind==Kind.EXPENSE
    if(namedBill && !advancedMeal) {
        MealBillScreen(s,accounts,people,onChange,onSave,onBack,onFriends,{advancedMeal=true},walletSources)
        return
    }
    if(advanced || s.kind!=Kind.EXPENSE) {
        AdvancedEntryScreen(s,accounts,people,categories,groups,onChange,onParse,onSave,records,onFriends,if(namedBill && advancedMeal) ({advancedMeal=false}) else if(s.kind==Kind.EXPENSE) ({step=0;advanced=false}) else onBack,walletSources,hasBill,onDiscardBill,onExchange)
        return
    }
    fun back() {if(step>0) step-- else onBack()}
    BackHandler(step>0) {back()}
    val total=runCatching {Money.parse(s.amount)}.getOrNull()
    val billValid=s.description.isNotBlank() && total!=null && total>0
    val payments=automaticBill(s).getOrNull()
    val paymentsValid=billValid && (!s.shared || (s.participants.size>1 && payments?.net!=null))
    val name:(String)->String={id->if(id=="self") "You" else people.find {it.id==id}?.let {personLabel(it,people)}?:"Friend"}
    Scaffold(modifier=Modifier.imePadding(),containerColor=Tokens.background,contentWindowInsets=WindowInsets(0,0,0,0),bottomBar={
        Column(Modifier.padding(horizontal=Tokens.pageInset,vertical=10.dp)) {
            when(step) {
                0->EditorialPrimaryButton("Continue to payments",billValid) {step=1;if(s.method.isBlank()) onChange(s.copy(method="Equal"))}
                1->EditorialPrimaryButton("Review bill",paymentsValid) {step=2}
                else->EditorialPrimaryButton("Save transaction",s.canReview(accounts,categories),onSave)
            }
        }
    }) {padding->Box(Modifier.fillMaxSize().padding(padding)) {
        key(step) {Page(if(s.replaces!=null) "Correct expense" else "Add expense","Step ${step+1} of 3 · ${listOf("Bill","Who paid","Review")[step]}",onBack={back()}) {
            item {StudioSteps(step)}
            when(step) {
                0->{
                    item {EntryStageHeading(if(s.shared) "Create a shared bill" else "What was the bill?","Add the basic details to get started.")}
                    item {OutlinedButton(onImportBill,Modifier.fillMaxWidth().heightIn(min=48.dp),shape=Tokens.field) {Text("Use a bill photo / PDF")}}
                    item {Panel {
                        Field("Description",s.description,{onChange(s.copy(description=it))})
                        Field("Total bill (₹)",s.amount,{onChange(s.copy(amount=it))},true)
                    }}
                    item {StudioCard {
                        Text("Who is this expense for?",style=MaterialTheme.typography.titleSmall)
                        FilterChip(!s.shared,{onChange(s.copy(shared=false))},label={Text("Just me")},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp))
                        FilterChip(s.shared,{onChange(s.copy(shared=true))},label={Text("Me and friends")},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp))
                        Text(if(s.shared) "Enter what each person actually paid next. Shares are equal by default." else "The bill amount is what you paid.",color=Tokens.muted)
                    }}
                    item {TextButton({advanced=true},Modifier.heightIn(min=48.dp)) {Text("Describe a transaction or use advanced entry")}}
                }
                1->{
                    item {total?.let {AmountRow("Total bill",it)}}
                    if(s.shared) {
                        item {EntryStageHeading("Enter who paid","Add how much each person actually paid for this bill, including 0.")}
                        if(groups.isNotEmpty()) item {Choice("Use a saved group",s.groupId,listOf("none" to "Choose friends individually")+groups.map {it.id to it.name}) {id->onChange(if(id=="none") s.copy(groupId=null) else s.withGroup(groups.single {it.id==id}))}}
                        s.participants.forEach {id->item(key=id) {
                            StudioAmountField("${name(id)} paid (₹)",s.paid[id].orEmpty(),person=name(id)) {onChange(s.copy(paid=s.paid+(id to it)))}
                            if(id!="self") TextButton({onChange(s.copy(participants=s.participants-id))}) {Text("Remove ${name(id)}")}
                        }}
                        item {StudioSecondaryAction(if(friendsExpanded) "Hide friends" else "Add friends",icon=com.smartexpense.R.drawable.ic_people,expanded=friendsExpanded) {friendsExpanded=!friendsExpanded}}
                        if(friendsExpanded || s.participants.size<2) item {StudioCard {
                            Text("Choose friends",style=MaterialTheme.typography.titleSmall)
                            people.filter {it.id!="self" && it.id !in s.participants}.forEach {person->
                                TextButton({onChange(s.copy(participants=(s.participants+person.id).distinct(),paid=s.paid+(person.id to "")))},Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Add ${personLabel(person,people)}")}
                            }
                            TextButton(onFriends,Modifier.heightIn(min=48.dp)) {Text("Create or manage friends")}
                        }}
                        item {Column {
                            Text(if(s.method=="Equal") "Equally · each person has the same share" else "${s.method} shares",color=Tokens.muted)
                            StudioSecondaryAction("Change split method") {choosingMethod=true}
                        }}
                        item {SettlementPreview(s,people)}
                    } else {
                        item {Panel {total?.let {AmountRow("You paid",it)};Text("This is a personal expense. No split is needed.",color=Tokens.muted)}}
                    }
                }
                else->{
                    item {EntryStageHeading("Review and save","Check the final split and settlements before saving.")}
                    item {GroupedCard {GroupedRow(s.description,R.drawable.ic_food,supporting=if(s.shared) "${s.participants.size} people" else "Personal expense",value=total?.let(::referenceMoney),tone=ReferenceTone.Peach,showChevron=false,onClick={})}}
                    item {StudioCard {
                        val review=s.reviewAmounts().getOrNull()
                        total?.let {AmountRow("Total bill",it)}
                        review?.let {AmountRow("You paid",it.payment);AmountRow("Your share",it.share)}
                    }}
                    if(s.shared) item {SettlementPreview(s,people)}
                    item {StudioCard {PaymentSourcePicker(s,accounts,onChange,walletSources)}}
                    item {TextButton({extras=!extras},Modifier.heightIn(min=48.dp)) {Text(if(extras) "Hide optional details" else "Category, date and notes (optional)")}}
                    val categoryUnavailable=s.categoryId!=null && categories.none {it.id==s.categoryId && !it.archived}
                    if(categoryUnavailable) item {Text("This category is no longer available. Choose an active category or Uncategorised before saving.",color=Tokens.negative)}
                    if(extras || categoryUnavailable) item {Panel {
                        Choice("Category",s.categoryId?:"",listOf("" to "Uncategorised")+categories.filterNot {it.archived}.map {it.id to it.name}) {onChange(s.copy(categoryId=it.ifEmpty {null}))}
                        EditorialDateRow(s.date) {onChange(s.copy(date=it))}
                        Field("Notes (optional)",s.notes,{onChange(s.copy(notes=it))})
                    }}
                    if(!s.canReview(accounts,categories) && !categoryUnavailable) item {Text(s.preview().exceptionOrNull()?.message?:s.accountImpact().exceptionOrNull()?.message?:"Choose a payment account before saving.",color=Tokens.negative)}
                    if(accounts.none {!it.archived}) item {Text("Create an account from Today → Settings → Money & planning → Accounts.",color=Tokens.muted)}
                }
            }
        }}
    }}
}

@Composable fun SettlementPreview(s:EntryUiState,people:List<Person>) {
    val result=automaticBill(s)
    val bill=result.getOrNull()
    fun name(id:String)=if(id=="self") "You" else people.find {it.id==id}?.let {personLabel(it,people)}?:"Friend"
    Column(Modifier.fillMaxWidth().semantics {liveRegion=LiveRegionMode.Polite},verticalArrangement=Arrangement.spacedBy(12.dp)) {
        when {
            bill==null->Text(result.exceptionOrNull()?.message?:"Check the bill amounts",color=Tokens.negative)
            bill.missingPayments.isNotEmpty()->Text("Enter a payment for ${bill.missingPayments.joinToString {name(it)}}. Zero is allowed.",color=Tokens.muted)
            bill.remaining!=0L->Text(if(bill.remaining>0) "Payments are ${Money.format(bill.remaining)} short of the bill." else "Payments exceed the bill by ${Money.format(Math.negateExact(bill.remaining))}.",color=Tokens.negative)
            else->{
                if(bill.settlements.isEmpty()) Text("All settled · nobody owes anything")
                bill.settlements.forEach {SettlementCard("${if(it.from=="self") "You owe" else "${name(it.from)} owes"} ${name(it.to)}",it.amount)}
                StudioCard {bill.shares.forEach {(id,share)->AmountRow("${name(id)} · share",share)}}
            }
        }
    }
}

@Composable private fun StudioAmountField(label:String,value:String,person:String?=null,onChange:(String)->Unit) {
    GroupedCard {
        Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        if(person!=null) InitialAvatar(person,Modifier.size(38.dp))
        OutlinedTextField(value,onChange,label={Text(label,style=MaterialTheme.typography.labelMedium)},prefix={Text("₹ ")},textStyle=MaterialTheme.typography.titleLarge,
            modifier=Modifier.weight(1f),shape=Tokens.field,singleLine=true,
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal,imeAction=ImeAction.Next),
            colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=Tokens.paper,focusedContainerColor=Tokens.paper,unfocusedBorderColor=Tokens.border,focusedBorderColor=Tokens.accent))
        }
    }
}

@Composable private fun EntryStageHeading(title:String,subtitle:String) {
    Column(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(title,style=MaterialTheme.typography.headlineSmall,textAlign=TextAlign.Center)
        Text(subtitle,color=Tokens.muted,textAlign=TextAlign.Center)
    }
}

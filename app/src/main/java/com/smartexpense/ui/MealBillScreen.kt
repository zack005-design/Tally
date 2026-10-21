package com.smartexpense.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.smartexpense.domain.*

/** Guided two-person receipt allocation. Shares never overwrite actual contributions. */
@Composable fun MealBillScreen(s:EntryUiState,accounts:List<Account>,people:List<Person>,onChange:(EntryUiState)->Unit,onSave:()->Unit,onBack:()->Unit,onFriends:()->Unit,onAdvanced:()->Unit,walletSources:WalletSources?=null) {
    var step by rememberSaveable(s.id) {mutableIntStateOf(0)}
    var description by rememberSaveable(s.id) {mutableStateOf("")}
    var selectionError by rememberSaveable(s.id) {mutableStateOf<String?>(null)}
    var extras by rememberSaveable(s.id) {mutableStateOf(false)}
    fun back() {if(step>0) step-- else onBack()}
    BackHandler(step>0) {back()}
    val friend=s.participants.filter {it!="self"}.singleOrNull()?.takeIf {id->s.shared && people.any {it.id==id}}
    var descriptionPerson by rememberSaveable(s.id,friend) {mutableStateOf("self")}
    fun name(id:String)=if(id=="self") "You" else people.find {it.id==id}?.let {personLabel(it,people)}?:"Friend"
    val rowsResult=runCatching {namedMealItems(s.billItemNames,s.items)}
    val rows=rowsResult.getOrDefault(emptyList())
    val calculation=automaticBill(s)
    val bill=calculation.getOrNull()
    val amountsValid=runCatching {
        val total=Money.parse(s.amount)
        require(total>0 && s.description.isNotBlank()) {"Check the bill total and description."}
        require(friend!=null && s.participants.size==2 && s.method=="Itemized") {"Choose the friend who ate with you."}
        rowsResult.getOrThrow()
        require(rows.all {it.people.all {id->id in s.participants}}) {"Choose who ate each item."}
        val sum=Math.subtractExact(Math.addExact(Finance.total(rows.map {it.amount}),Money.parse(s.itemCharges.ifBlank {"0"})),Money.parse(s.itemDiscount.ifBlank {"0"}))
        require(sum==total) {"Items, charges and discount must match the bill total. Check the original bill."}
        Finance.split(total,s.participants,foodSplitRule(s.items,s.participants,Money.parse(s.itemCharges.ifBlank {"0"}),Money.parse(s.itemDiscount.ifBlank {"0"})))
    }
    val ready=amountsValid.isSuccess
    Scaffold(modifier=Modifier.imePadding(),containerColor=Tokens.background,contentWindowInsets=WindowInsets(0,0,0,0),bottomBar={
        Column(Modifier.padding(horizontal=Tokens.pageInset,vertical=10.dp)) {
            when(step) {
                0->EditorialPrimaryButton("Continue to payments",ready) {step=1}
                1->EditorialPrimaryButton("Review bill",ready && bill?.net!=null) {step=2}
                else->EditorialPrimaryButton("Save transaction",ready && s.canReview(accounts),onSave)
            }
        }
    }) {padding->Box(Modifier.fillMaxSize().padding(padding)) {
        key(step) {Page("Split your bill","Step ${step+1} of 3 · ${listOf("Your food","Who paid","Review")[step]}",onBack={back()}) {
            item {StudioSteps(step)}
            when(step) {
                0->{
                    item {Text("Add and assign items",style=MaterialTheme.typography.headlineSmall);Text("Check the recognized rows against your bill and assign who shared each item.",color=Tokens.muted)}
                    item {Choice("Who ate with you?",friend,people.filter {it.id!="self"}.map {it.id to personLabel(it,people)},icon=com.smartexpense.R.drawable.ic_people) {id->
                        runCatching {s.withMealFriend(id)}.fold(onSuccess={selectionError=null;onChange(it)},onFailure={selectionError=it.message})
                    }}
                    item {StudioSecondaryAction("Add a friend",icon=com.smartexpense.R.drawable.ic_add,onClick=onFriends)}
                    item {StudioCard {
                        if(friend!=null) EditorialSegmentedControl(descriptionPerson,listOf("self" to "My items",friend to "${name(friend)}'s items")) {descriptionPerson=it;description="";selectionError=null}
                        Text(if(descriptionPerson=="self") "Assign items to you" else "Assign items to ${name(descriptionPerson)}",style=MaterialTheme.typography.titleSmall)
                        Field(if(descriptionPerson=="self") "What did you eat?" else "What did ${name(descriptionPerson)} eat?",description,{description=it;selectionError=null})
                        Text("Use item names, e.g. ‘I ate paneer biryani’. You can also select rows below.",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
                        OutlinedButton({
                            runCatching {s.withMealDescription(description,descriptionPerson)}
                                .fold(onSuccess={selectionError=null;onChange(it)},onFailure={selectionError=it.message})
                        },enabled=friend!=null && description.isNotBlank(),modifier=Modifier.fillMaxWidth(),shape=Tokens.field) {Text(if(descriptionPerson=="self") "Apply my items" else "Apply ${name(descriptionPerson)}'s items")}
                        Text(when {friend==null->"Choose one friend first.";descriptionPerson=="self"->"Remaining items go to ${name(friend)}. Shared selections stay shared.";else->"Updates these items only. Your other items and shared selections stay as shown."},color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
                        selectionError?.let {Text(it,color=Tokens.negative,modifier=Modifier.semantics {liveRegion=LiveRegionMode.Polite})}
                    }}
                    if(rowsResult.isFailure) item {Text(rowsResult.exceptionOrNull()?.message?:"Check the bill rows.",color=Tokens.negative)}
                    rows.forEachIndexed {index,row->item(key="food-$index") {Panel {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            ReferenceIcon(com.smartexpense.R.drawable.ic_food,ReferenceTone.Peach)
                            Text(row.name,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                            Text(referenceMoney(row.amount),style=MaterialTheme.typography.titleMedium)
                        }
                        if(friend!=null) {
                            val selected=when(row.people.toSet()) {setOf("self")->"mine";setOf(friend)->"friend";setOf("self",friend)->"shared";else->""}
                            MealOwnerChoices(row.name,selected,name(friend)) {owner->
                                val mine=rows.indices.filter {rows[it].people.toSet()==setOf("self")}.toMutableSet().apply {remove(index);if(owner=="mine") add(index)}
                                val shared=rows.indices.filter {rows[it].people.toSet()==setOf("self",friend)}.toMutableSet().apply {remove(index);if(owner=="shared") add(index)}
                                onChange(s.copy(items=assignMealItems(s.items,mine,shared,friend)));selectionError=null
                            }
                        }
                    }}}
                    item {StudioCard {
                        SectionHeading("Check the bill total")
                        Field("Bill total (₹)",s.amount,{onChange(s.copy(amount=it))},true)
                        Field("Description",s.description,{onChange(s.copy(description=it))})
                        Field("Shared taxes / charges (₹)",s.itemCharges,{onChange(s.copy(itemCharges=it))},true)
                        Field("Bill discount (₹)",s.itemDiscount,{onChange(s.copy(itemDiscount=it))},true)
                        Text("Shared rows split equally. Charges and discounts follow item shares. Use advanced entry for unequal quantities or more people.",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
                        amountsValid.getOrNull()?.forEach {(id,share)->AmountRow("${name(id)} · food share",share)}
                        if(friend!=null && amountsValid.isFailure) Text(amountsValid.exceptionOrNull()?.message.orEmpty(),color=Tokens.negative)
                    }}
                    item {TextButton(onAdvanced,Modifier.heightIn(min=48.dp)) {Text("Personal expense or advanced item editing")}}
                }
                1->{
                    item {StudioHeading("What did you each pay?");Text("Enter the actual amounts, including 0. Item shares stay separate.",color=Tokens.muted)}
                    item {runCatching {Money.parse(s.amount)}.getOrNull()?.let {AmountRow("Bill total",it)}}
                    s.participants.forEach {id->item(key=id) {StudioCard {Field("${name(id)} paid (₹)",s.paid[id].orEmpty(),{onChange(s.copy(paid=s.paid+(id to it)))},true)}}}
                    item {SettlementPreview(s,people)}
                }
                else->{
                    item {StudioHeading(s.description);Text("Based on what each person ate.",color=Tokens.muted)}
                    item {SettlementPreview(s,people)}
                    item {StudioCard {
                        bill?.contributions?.forEach {(id,amount)->AmountRow("${name(id)} paid",amount)}
                        runCatching {Money.parse(s.amount)}.getOrNull()?.let {AmountRow("Total bill",it)}
                    }}
                    item {StudioCard {PaymentSourcePicker(s,accounts,onChange,walletSources)}}
                    item {TextButton({extras=!extras}) {Text(if(extras) "Hide item assignments" else "Review item assignments")}}
                    if(extras) rows.forEachIndexed {index,row->item(key="review-$index") {StudioCard {
                        Text(row.name,style=MaterialTheme.typography.titleMedium)
                        AmountRow(row.people.joinToString(" + ") {name(it)},row.amount)
                    }}}
                    if(!ready) item {Text(amountsValid.exceptionOrNull()?.message.orEmpty(),color=Tokens.negative)}
                }
            }
        }}
    }}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun MealOwnerChoices(item:String,selected:String,friend:String,onSelect:(String)->Unit) {
    val large=androidx.compose.ui.platform.LocalDensity.current.fontScale>1.3f
    val choices=listOf(Triple("mine","Mine","Assign $item to you"),Triple("friend",friend,"Assign $item to $friend"),Triple("shared","Shared","Share $item equally"))
    val content:@Composable (Triple<String,String,String>,Modifier)->Unit={choice,modifier->
        FilterChip(selected==choice.first,{onSelect(choice.first)},label={Text(choice.second)},modifier=modifier.heightIn(min=48.dp).semantics {contentDescription=choice.third})
    }
    if(large) Column(Modifier.fillMaxWidth()) {choices.forEach {content(it,Modifier.fillMaxWidth())}}
    else androidx.compose.foundation.layout.FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {choices.forEach {content(it,Modifier)}}
}

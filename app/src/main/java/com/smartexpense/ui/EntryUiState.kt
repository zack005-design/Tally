package com.smartexpense.ui

import com.smartexpense.domain.*
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

data class EntryUiState(
    val id:String=UUID.randomUUID().toString(), val kind:Kind=Kind.EXPENSE, val amount:String="",val description:String="",
    val accountId:String="",val destinationId:String="",val categoryId:String?=null,val groupId:String?=null,
    val shared:Boolean=false,val participants:List<String> = listOf("self"),val paid:Map<String,String> = emptyMap(),
    val method:String="Equal",val values:Map<String,String> = emptyMap(),val items:String="",
    val date:String=LocalDateTime.now().withNano(0).toString(),val notes:String="",val text:String="",val parserNotice:String="",
    val replaces:String?=null,val refundOf:String?=null,
    val secondAccountId:String="",val secondPayment:String="",val itemCharges:String="",val itemDiscount:String="",
    val originalMovements:Map<String,Long> = emptyMap(),val originalDate:Long?=null
) {
    fun draft():EntryDraft {
        val money=Money.parse(amount); require(money>0) { "Enter a positive amount" }
        val splitting=shared && kind==Kind.EXPENSE
        require(!splitting || participants!=listOf("self")) {"Choose the other participants for this shared expense"}
        val rule=if(!splitting) SplitRule.Equal else when(method) {
            "Equal" -> SplitRule.Equal
            "Fixed" -> SplitRule.Fixed(participants.associateWith { Money.parse(values[it].orEmpty()) })
            "Weighted" -> SplitRule.Weighted(participants.associateWith { values[it]?.toLongOrNull()?:throw IllegalArgumentException("Enter whole-number weights") })
            "Percentage" -> SplitRule.Percentage(participants.associateWith { Money.parse(values[it].orEmpty()) })
            "Itemized" -> foodSplitRule(items,participants,itemCharges.ifBlank {"0"}.let(Money::parse),itemDiscount.ifBlank {"0"}.let(Money::parse))
            else -> throw IllegalArgumentException("Choose a split method")
        }
        return EntryDraft(id,kind,money,description,accountId,destinationId.ifBlank { null },categoryId,groupId,
            LocalDateTime.parse(date).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),notes,
            if(splitting) participants else listOf("self"),if(splitting) participants.associateWith { Money.parse(paid[it].orEmpty()) } else mapOf("self" to money),rule,if(kind==Kind.REFUND) refundOf else null,
            if(kind==Kind.EXPENSE) sourcePayments(if(splitting) paid["self"]?.let(Money::parse)?:0 else money) else emptyMap())
    }
    fun preview():Result<String> = runCatching {
        val d=draft()
        if(d.kind==Kind.EXPENSE) {
            val shares=Finance.split(d.amount,d.participants,d.splitRule); Finance.net(shares,d.contributions)
            "Personal spending: ${Money.format(shares["self"]?:0)}"
        } else when(d.kind) { Kind.TRANSFER -> "Transfer: ${Money.format(d.amount)} · no spending or income"; Kind.REFUND -> "Refund: ${Money.format(d.amount)} · reduces spending"; else -> "Income: ${Money.format(d.amount)}" }
    }
    fun accountImpact():Result<List<Movement>> = runCatching {
        val d=draft()
        when(d.kind) {
            Kind.EXPENSE->expensePayments(d).map {Movement(it.key,Math.negateExact(it.value))}
            Kind.TRANSFER->listOf(Movement(d.accountId,-d.amount),Movement(requireNotNull(d.destinationAccountId) {"Choose a destination account"},d.amount))
            Kind.INCOME,Kind.REFUND->listOf(Movement(d.accountId,d.amount))
            else->emptyList()
        }
    }
}

data class EntryReviewAmounts(val total:Long,val payment:Long,val share:Long,val net:Long)
fun EntryUiState.sourcePayments(payment:Long):Map<String,Long> {
    if(secondAccountId.isBlank()) return emptyMap()
    require(secondAccountId!=accountId) {"Choose two different payment sources"}
    val second=Money.parse(secondPayment)
    require(second>=0 && second<=payment) {"Second source payment cannot exceed your payment"}
    return mapOf(accountId to Math.subtractExact(payment,second),secondAccountId to second)
}
fun EntryUiState.withGroup(group:Group):EntryUiState = copy(shared=true,kind=Kind.EXPENSE,groupId=group.id,
    participants=(listOf("self")+group.members).distinct(),paid=(listOf("self")+group.members).distinct().associateWith {""},method="Equal",values=emptyMap(),items="",itemCharges="",itemDiscount="")
fun EntryUiState.reviewAmounts():Result<EntryReviewAmounts> = runCatching {
    val draft=draft()
    val shares=Finance.split(draft.amount,draft.participants,draft.splitRule)
    val net=Finance.net(shares,draft.contributions)
    EntryReviewAmounts(draft.amount,draft.contributions["self"]?:0,shares["self"]?:0,net["self"]?:0)
}

fun EntryUiState.canReview(accounts:List<Account>):Boolean =
    preview().isSuccess && accountImpact().isSuccess && description.isNotBlank() &&
        (secondAccountId.isBlank() || accounts.any {it.id==secondAccountId && !it.archived}) &&
        accounts.any {it.id==accountId && !it.archived} &&
        (kind!=Kind.TRANSFER || destinationId!=accountId && accounts.any {it.id==destinationId && !it.archived}) &&
        (kind!=Kind.REFUND || refundOf!=null)

fun EntryUiState.reuse(record:Record):EntryUiState = copy(
    kind=record.kind,amount=Money.input(record.amount),description=record.description,categoryId=record.categoryId,
    accountId="",destinationId="",shared=record.kind==Kind.EXPENSE && record.personIds.any {it!="self"},
    participants=if(record.kind==Kind.EXPENSE && record.personIds.any {it!="self"}) record.personIds else listOf("self"),paid=emptyMap(),
    method=if(record.kind==Kind.EXPENSE && record.personIds.any {it!="self"}) "" else "Equal",
    values=emptyMap(),items="",groupId=record.groupId,refundOf=null,secondAccountId="",secondPayment="",itemCharges="",itemDiscount="",originalMovements=emptyMap(),originalDate=null,notes=record.notes,parserNotice="Review the suggested transaction and choose an account. For a shared bill, select its split method and enter contributions.")


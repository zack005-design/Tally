package com.smartexpense.data

import com.smartexpense.domain.*
import java.util.UUID

data class SharedRefundContext(val original:Record,val remaining:Map<String,Long>)

/** Called within the repository's Room transaction and storage error boundary. */
internal class SharedRefundStore(private val dao:TrackerDao) {
    suspend fun context(originalId:String):SharedRefundContext {
        val original=requireNotNull(dao.transaction(originalId)) {"Original bill is unavailable"}
        require(original.kind==Kind.EXPENSE.name && !original.reversed) {"Choose an active shared expense"}
        val shares=dao.sharesFor(originalId).sortedBy {it.personId}.associate {it.personId to it.amount}
        require(shares.size>1) {"Use the personal refund form for this expense"}
        val refunds=dao.transactions().filter {it.kind==Kind.REFUND.name && it.refundOf==originalId && !it.reversed}
            .map {refund->dao.sharesFor(refund.id).associate {it.personId to it.amount}}
        return SharedRefundContext(Record(original.id,Kind.EXPENSE,original.description,original.amount,original.personalAmount,original.categoryId,original.groupId,original.date,original.notes,false,personIds=shares.keys.toList()),SharedRefund.remaining(shares,refunds))
    }
    suspend fun save(draft:SharedRefundDraft):String {
        val context=context(draft.originalId)
        val original=context.original
        require(draft.id.isNotBlank() && draft.description.isNotBlank() && draft.date>=original.date) {"Add a description and record the refund on or after the original bill"}
        val cash=draft.received["self"]?:0L
        val expected=if(cash>0) mapOf(draft.accountId to cash) else emptyMap()
        dao.transaction(draft.id)?.let {saved->
            require(!saved.reversed && saved.kind==Kind.REFUND.name && saved.refundOf==draft.originalId && saved.amount==draft.amount && saved.description==draft.description.trim() && saved.date==draft.date && saved.notes==draft.notes && saved.categoryId==original.categoryId && saved.groupId==original.groupId && dao.sharesFor(draft.id).associate {it.personId to it.amount}==draft.shares && dao.contributionsFor(draft.id).associate {it.personId to it.amount}==draft.received && dao.entriesFor(draft.id).associate {it.accountId to it.amount}==expected) {"This refund ID was already used for different details"}
            return draft.id
        }
        val positions=SharedRefund.positions(draft.amount,context.remaining,draft.shares,draft.received)
        if(cash>0) require(dao.accounts().any {it.id==draft.accountId && !it.archived}) {"Choose an active account for your received cash"}
        fun id()=UUID.randomUUID().toString()
        dao.insertTransaction(TransactionEntity(draft.id,Kind.REFUND.name,draft.description.trim(),draft.amount,draft.shares["self"]?:0L,original.categoryId,original.groupId,draft.date,draft.notes,refundOf=original.id))
        dao.insertShares(draft.shares.map {ShareEntity(draft.id,it.key,it.value)})
        dao.insertContributions(draft.received.map {ContributionEntity(draft.id,it.key,it.value)})
        dao.insertEntries(expected.map {AccountEntryEntity(id(),draft.id,it.key,it.value)})
        dao.insertObligations(Finance.settlements(positions).map {ObligationEntity(id(),draft.id,it.from,it.to,it.amount)})
        return draft.id
    }
}

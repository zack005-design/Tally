package com.smartexpense.domain

import java.util.UUID

data class SharedRefundDraft(
    val id:String=UUID.randomUUID().toString(),val originalId:String,val amount:Long,
    val description:String,val accountId:String,val shares:Map<String,Long>,val received:Map<String,Long>,
    val date:Long=System.currentTimeMillis(),val notes:String=""
)

/** A refund's spending benefit and actual cash recipients are separate exact-paise totals. */
object SharedRefund {
    fun remaining(original:Map<String,Long>,refunds:List<Map<String,Long>>):Map<String,Long> {
        require(original.isNotEmpty() && original.values.all {it>=0}) {"Invalid original shares"}
        val result=original.toMutableMap()
        refunds.forEach {refund->
            require(refund.keys==original.keys && refund.values.all {it>=0}) {"Refund participants do not match the original bill"}
            refund.forEach {(person,amount)->
                require(amount<=result.getValue(person)) {"Refund exceeds a participant's remaining share"}
                result[person]=Math.subtractExact(result.getValue(person),amount)
            }
        }
        return result
    }
    fun proportional(amount:Long,remaining:Map<String,Long>):Map<String,Long> {
        require(amount>0 && amount<=Finance.total(remaining.values)) {"Enter a refund within the remaining bill amount"}
        return Finance.split(amount,remaining.keys.sorted(),SplitRule.Weighted(remaining))
    }
    fun positions(amount:Long,remaining:Map<String,Long>,shares:Map<String,Long>,received:Map<String,Long>):Map<String,Long> {
        require(amount>0 && shares.keys==remaining.keys && received.keys==remaining.keys) {"Include every original participant"}
        require(shares.values.all {it>=0} && received.values.all {it>=0} && Finance.total(shares.values)==amount && Finance.total(received.values)==amount) {"Refund shares and cash received must each equal the refund total"}
        require(shares.all {(person,value)->value<=remaining.getValue(person)}) {"Refund exceeds a participant's remaining share"}
        return Finance.net(shares,received).mapValues {Math.negateExact(it.value)}
    }
}

package com.smartexpense.domain

import java.util.UUID

data class Account(val id: String, val name: String, val type: String, val balance: Long, val archived: Boolean)
data class Person(val id: String, val name: String, val aliases: List<String> = emptyList())
data class Group(val id: String, val name: String, val members: List<String>)
data class Category(val id: String, val name: String, val color: Long, val icon: String, val archived: Boolean)
data class Budget(val id: String, val month: String, val categoryId: String?, val amount: Long)
data class Record(val id: String, val kind: Kind, val description: String, val amount: Long, val personalAmount: Long, val categoryId: String?, val groupId: String?, val date: Long, val notes: String, val reversed: Boolean, val accountIds:List<String> = emptyList(),val personIds:List<String> = emptyList())
data class RecordDetail(val record:Record,val entries:List<Movement>,val shares:Map<String,Long>,val contributions:Map<String,Long>,val settlements:List<SuggestedSettlement>,val allocations:Map<String,Long>)
data class Debt(val id: String, val transactionId: String, val from: String, val to: String, val original: Long, val outstanding: Long, val date: Long,val groupId:String?=null)
data class DashboardMetrics(val today:Long=0,val week:Long=0,val month:PeriodReport=PeriodReport(0,0,emptyMap(),emptyMap()),val previousToDate:Long=0)
data class Snapshot(val accounts: List<Account> = emptyList(), val people: List<Person> = emptyList(), val groups: List<Group> = emptyList(), val categories: List<Category> = emptyList(), val budgets: List<Budget> = emptyList(), val records: List<Record> = emptyList(), val debts: List<Debt> = emptyList(),val dashboard:DashboardMetrics=DashboardMetrics(),val selectedReport:PeriodReport=PeriodReport(0,0,emptyMap(),emptyMap()),val groupSpending:Map<String,Long> = emptyMap(),val transactionCount:Int=0,val reportMonth:String="",val previousPeriodSpending:Long=0,val walletSources:WalletSources?=null,val diary:Map<String,String> = emptyMap(),val localSettings:LocalSettings=LocalSettings(),val groupDefaults:List<GroupDefault> = emptyList(),val templates:List<RecurringTemplate> = emptyList(),val goals:List<SavingsGoal> = emptyList(),val categoryRules:List<CategoryRule> = emptyList())
data class EntryDraft(
    val id: String = UUID.randomUUID().toString(), val kind: Kind = Kind.EXPENSE,
    val amount: Long, val description: String, val accountId: String,
    val destinationAccountId: String? = null, val categoryId: String? = null, val groupId: String? = null,
    val date: Long = System.currentTimeMillis(), val notes: String = "",
    val participants: List<String> = listOf("self"), val contributions: Map<String, Long> = mapOf("self" to amount),
    val splitRule: SplitRule = SplitRule.Equal, val refundOf: String? = null,
    val selfPayments: Map<String,Long> = emptyMap()
)

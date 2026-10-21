package com.smartexpense.domain

import org.junit.Assert.*
import org.junit.Test

class TextParserTest {
    private val parser = RuleBasedTextParser()
    @Test fun approvedDinnerDescriptionKeepsTotalSeparateFromPayers() {
        val d=parser.parse("Dinner with friends ₹890. I paid ₹500, Rahul paid ₹290, Kevin paid ₹100 and Arjun paid nothing. Split equally.",listOf(PersonRef("r","Rahul"),PersonRef("k","Kevin"),PersonRef("a","Arjun")))
        assertEquals(89000L,d.amount)
        assertEquals(mapOf("self" to 50000L,"r" to 29000L,"k" to 10000L,"a" to 0L),d.contributions)
        assertEquals(SplitRule.Equal,d.splitRule)
    }
    @Test fun lunchDraftRequiresAccount() { val d=parser.parse("Spent 250 on lunch.", emptyList()); assertEquals(25000L,d.amount); assertEquals(Kind.EXPENSE,d.kind); assertEquals("Food",d.category); assertTrue("account" in d.missing) }
    @Test fun mealExtractsAllPayers() { val d=parser.parse("Food for 890. I paid 500, Rahul paid 290, Kevin paid 100, Arjun paid 0. Split equally.",listOf(PersonRef("r","Rahul"),PersonRef("k","Kevin"),PersonRef("a","Arjun"))); assertEquals(89000L,d.amount); assertEquals(mapOf("self" to 50000L,"r" to 29000L,"k" to 10000L,"a" to 0L),d.contributions); assertEquals(SplitRule.Equal,d.splitRule) }
    @Test fun duplicateNamesRemainAmbiguous() { val d=parser.parse("Rahul returned 200",listOf(PersonRef("r1","Rahul"),PersonRef("r2","Rahul"))); assertNull(d.personId); assertTrue(d.warnings.isNotEmpty()) }
    @Test fun transferRequiresTwoAccounts() { val d=parser.parse("Transferred 1000 from bank to cash",emptyList()); assertEquals(Kind.TRANSFER,d.kind); assertEquals("bank",d.fromAccount); assertEquals("cash",d.toAccount) }
    @Test fun noAmountDoesNotInventMoney() { assertNull(parser.parse("Bought groceries",emptyList()).amount) }
    @Test fun unspecifiedPeopleAreMissing() { val d=parser.parse("I paid 900 for dinner for three people",emptyList()); assertTrue("participants" in d.missing); assertNull(d.splitRule) }
    @Test fun unknownPayerIsUnresolved() { assertTrue("participants" in parser.parse("Bill was 500, Sam paid 500, split equally",emptyList()).missing) }
    @Test fun compactCoffeeExampleCreatesAnEditableExpenseSuggestion() {
        val parsed=parser.parse("Coffee 120 gpay",emptyList())
        assertEquals(Kind.EXPENSE,parsed.kind)
        assertEquals(12000L,parsed.amount)
        assertEquals("Coffee",parsed.description)
        assertEquals("gpay",parsed.fromAccount)
        assertEquals("Food",parsed.category)
    }
    @Test fun accountHintsNeverSelectAnArchivedOrAmbiguousAccount() {
        val wallet=Account("w","Pocket","Cash",0,false);val gpay=Account("g","UPI","Bank",0,false)
        val sources=WalletSources("w","g","2026-10")
        assertEquals("g",resolveParsedAccount("gpay",listOf(wallet,gpay),sources))
        assertNull(resolveParsedAccount("gpay",listOf(wallet,gpay.copy(archived=true)),sources))
        assertNull(resolveParsedAccount("Cash",listOf(wallet.copy(name="Cash"),wallet.copy(id="x",name="Cash")),sources))
        assertNull(resolveParsedAccount("unknown",listOf(wallet,gpay),sources))
    }
}


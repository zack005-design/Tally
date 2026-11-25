package com.smartexpense.ui

import org.junit.Assert.*
import org.junit.Test

class KeypadAmountTest {
    private fun type(keys: String) = keys.fold("") { value, key -> keypadAmount(value, key.toString()) }

    @Test fun buildsExactAmounts() {
        assertEquals("120", type("120"))
        assertEquals("120.5", type("120.5"))
        assertEquals("0.75", type(".75"))
        assertEquals("5", type("05"))
    }
    @Test fun ignoresExtraDecimalsAndDigits() {
        assertEquals("1.23", type("1.234"))
        assertEquals("1.23", type("1.2.3"))
        assertEquals("123456789", type("1234567890"))
        assertEquals("123456789.5", type("123456789.5"))
    }
    @Test fun deletesWithoutFailingWhenEmpty() {
        assertEquals("12", keypadAmount("123", "⌫"))
        assertEquals("", keypadAmount("", "⌫"))
    }
    @Test fun showsIndianGroupingWhileTyping() {
        assertEquals("₹0", keypadDisplay(""))
        assertEquals("₹999", keypadDisplay("999"))
        assertEquals("₹1,000", keypadDisplay("1000"))
        assertEquals("₹2,14,600.5", keypadDisplay("214600.5"))
        assertEquals("₹12,34,56,789", keypadDisplay("123456789"))
        assertEquals("₹5.", keypadDisplay("5."))
    }
}

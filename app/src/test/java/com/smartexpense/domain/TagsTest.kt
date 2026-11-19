package com.smartexpense.domain
import org.junit.Assert.*
import org.junit.Test

class TagsTest {
    @Test fun parsingTrimsHashesSpacesAndCaseInsensitiveDuplicates() {
        assertEquals(listOf("Goa trip", "work"), Tags.parse(" #Goa   trip, work ,goa TRIP,, "))
        assertEquals(emptyList<String>(), Tags.parse(" , ,#"))
    }
    @Test fun limitsAreEnforced() {
        assertThrows(IllegalArgumentException::class.java) { Tags.parse("x".repeat(Tags.MAX_LENGTH + 1)) }
        assertEquals(Tags.MAX_LENGTH, Tags.parse("x".repeat(Tags.MAX_LENGTH)).single().length)
        assertThrows(IllegalArgumentException::class.java) { Tags.parse((1..Tags.MAX_TAGS + 1).joinToString(",") { "t$it" }) }
        assertThrows(IllegalArgumentException::class.java) { Tags.normalize(listOf("bad\u0007tag")) }
    }
    @Test fun storedFormIsCheckedStrictly() {
        assertTrue(Tags.isNormalized(listOf("Goa trip", "work")))
        assertFalse(Tags.isNormalized(emptyList()))
        assertFalse(Tags.isNormalized(listOf(" padded")))
        assertFalse(Tags.isNormalized(listOf("a,b")))
        assertFalse(Tags.isNormalized(listOf("Work", "work")))
    }
    @Test fun matchingAndListingIgnoreCase() {
        val records = listOf(
            Record("a", Kind.EXPENSE, "A", 1, 1, null, null, 0, "", false, tags = listOf("Work", "Goa trip")),
            Record("b", Kind.EXPENSE, "B", 1, 1, null, null, 0, "", false, tags = listOf("work")),
        )
        assertTrue(Tags.has(records[1], "WORK"))
        assertEquals(listOf("Goa trip", "Work"), Tags.all(records))
    }
}

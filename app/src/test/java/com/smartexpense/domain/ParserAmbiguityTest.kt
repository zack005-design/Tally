package com.smartexpense.domain
import org.junit.Assert.*
import org.junit.Test
import java.time.*
class ParserAmbiguityTest {
    @Test fun twoUnexplainedAmountsRequireReview() {val d=RuleBasedTextParser().parse("Spent 250 and 300 on lunch",emptyList());assertNull(d.amount);assertTrue("amount" in d.missing)}
    @Test fun yesterdayIsExtractedWithoutInventingTime() {val d=RuleBasedTextParser(Clock.fixed(Instant.parse("2026-10-10T06:00:00Z"),ZoneId.of("Asia/Kolkata"))).parse("Spent 250 on lunch yesterday",emptyList());assertEquals(LocalDate.of(2026,10,9),d.date)}
}

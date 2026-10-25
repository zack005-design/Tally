package com.smartexpense.domain

import org.junit.Assert.*
import org.junit.Test

class SharedRefundTest {
    private val original=linkedMapOf("self" to 30000L,"a" to 30000L,"b" to 30000L)
    @Test fun proportionalRefundSeparatesCashAndPersonalBenefit() {
        val shares=SharedRefund.proportional(30000,original)
        assertEquals(mapOf("self" to 10000L,"a" to 10000L,"b" to 10000L),shares)
        val net=SharedRefund.positions(30000,original,shares,mapOf("self" to 30000L,"a" to 0L,"b" to 0L))
        assertEquals(mapOf("self" to -20000L,"a" to 10000L,"b" to 10000L),net)
    }
    @Test fun repeatedRefundsRespectRemainingSharesAndDeterministicPaise() {
        val first=SharedRefund.proportional(1,mapOf("b" to 1L,"a" to 1L))
        assertEquals(mapOf("a" to 1L,"b" to 0L),first)
        val remaining=SharedRefund.remaining(mapOf("a" to 1L,"b" to 1L),listOf(first))
        assertEquals(mapOf("a" to 0L,"b" to 1L),remaining)
        assertEquals(mapOf("a" to 0L,"b" to 1L),SharedRefund.proportional(1,remaining))
    }
    @Test fun rejectsOverRefundAndMismatchedRecipients() {
        assertThrows(IllegalArgumentException::class.java) {SharedRefund.positions(30000,original,mapOf("self" to 30001L,"a" to 0L,"b" to 0L),original)}
        assertThrows(IllegalArgumentException::class.java) {SharedRefund.positions(30000,original,SharedRefund.proportional(30000,original),mapOf("self" to 30000L))}
        assertThrows(IllegalArgumentException::class.java) {SharedRefund.remaining(original,listOf(mapOf("self" to 30001L,"a" to 0L,"b" to 0L)))}
    }
    @Test fun supportsLargeTotalsWithoutProductOverflow() {
        val remaining=mapOf("a" to Long.MAX_VALUE-1,"self" to 1L)
        assertEquals(remaining,SharedRefund.proportional(Long.MAX_VALUE,remaining))
    }
}

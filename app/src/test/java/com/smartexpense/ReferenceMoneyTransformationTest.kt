package com.smartexpense

import androidx.compose.ui.text.AnnotatedString
import com.smartexpense.ui.RupeeMoneyTransformation
import org.junit.Assert.*
import org.junit.Test

class ReferenceMoneyTransformationTest {
    @Test fun formattingNeverChangesTheEditableAmountAndCursorRoundTrips() {
        val values = mapOf("" to "₹0", "0" to "₹0", "1200" to "₹1,200", "5000.50" to "₹5,000.50", "-123456.78" to "₹-123,456.78", "1." to "₹1.")
        values.forEach { (raw, expected) ->
            val result = RupeeMoneyTransformation.filter(AnnotatedString(raw))
            assertEquals(expected, result.text.text)
            (0..raw.length).forEach { original ->
                val visual = result.offsetMapping.originalToTransformed(original)
                assertTrue(visual in 0..expected.length)
                assertEquals(original, result.offsetMapping.transformedToOriginal(visual))
            }
            val offsets = (0..expected.length).map(result.offsetMapping::transformedToOriginal)
            assertTrue(offsets.all { it in 0..raw.length })
            assertEquals(offsets.sorted(), offsets)
        }
    }
}

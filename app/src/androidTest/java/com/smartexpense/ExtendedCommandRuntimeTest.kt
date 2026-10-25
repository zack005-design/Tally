package com.smartexpense

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.smartexpense.data.LocalCommandParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class ExtendedCommandRuntimeTest {
    @Test fun packagedModelParsesNewCapabilitiesOffMainThread() = runBlocking {
        assumeTrue(BuildConfig.LOCAL_AI_ENABLED)
        val parser = LocalCommandParser(InstrumentationRegistry.getInstrumentation().targetContext)
        val inputs = listOf("Paid 49 for coffee using gpy" to 4900L, "maine chai ke liye cash se 30 diye" to 3000L)
        inputs.forEach { (text, amount) -> assertEquals(amount, parser.parse(text, emptyList()).amount) }
        repeat(5) { parser.parse(inputs[1].first, emptyList()) }
        val times = List(30) {
            val start = System.nanoTime()
            assertEquals(3000L, parser.parse(inputs[1].first, emptyList()).amount)
            (System.nanoTime() - start) / 1_000_000.0
        }.sorted()
        Log.i("Tally1M", "warm packaged Hinglish p50=${times[15]}ms p95=${times[28]}ms")
        listOf("kal lunch 100 gpay", "coffee 45 wallett gpy", "chai 25 aur taxi 100 cash").forEach {
            assertNull(parser.parse(it, emptyList()).amount)
        }
    }
}

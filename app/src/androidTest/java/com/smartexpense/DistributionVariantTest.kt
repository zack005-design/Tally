package com.smartexpense

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class DistributionVariantTest {
    @Test fun modelAssetsMatchDistributionFlag() {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets.list("")!!.toSet()
        assertEquals(BuildConfig.LOCAL_AI_ENABLED, "tally-command.bin" in assets)
        assertEquals(BuildConfig.LOCAL_AI_ENABLED, "tally-command.json" in assets)
    }
}

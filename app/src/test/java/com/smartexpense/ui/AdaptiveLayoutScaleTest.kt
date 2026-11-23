package com.smartexpense.ui
import org.junit.Assert.*
import org.junit.Test
class AdaptiveLayoutScaleTest {
    @Test fun regularAndWidePhonesKeepTheirNativeDensity() {
        listOf(360,393,412,600,840).forEach {assertEquals(1f,adaptiveLayoutScale(it),0f)}
    }
    @Test fun compactPhonesShrinkProportionally() {
        assertEquals(320f/360f,adaptiveLayoutScale(320),.0001f)
        assertEquals(.82f,adaptiveLayoutScale(280),.0001f)
    }
    @Test fun everyNarrowWindowStillGetsTheMinimumVerifiedLayoutWidth() {
        (1 until 360).forEach {width->
            val layoutWidth=width/adaptiveLayoutScale(width)
            assertTrue("width $width composes at $layoutWidth dp",layoutWidth>=MINIMUM_LAYOUT_WIDTH_DP-.01f)
        }
    }
    @Test fun unknownWidthIsLeftUnscaled() {
        assertEquals(1f,adaptiveLayoutScale(0),0f)
    }
}

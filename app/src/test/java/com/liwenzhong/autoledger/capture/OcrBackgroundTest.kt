package com.liwenzhong.autoledger.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrBackgroundTest {
    @Test fun darkScreensAreInvertedButLightOrMixedScreensAreNot() {
        assertTrue(shouldInvertOcrBackground(0xFF161616.toInt(), 0xFF161616.toInt()))
        assertFalse(shouldInvertOcrBackground(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()))
        assertFalse(shouldInvertOcrBackground(0xFF161616.toInt(), 0xFFFFFFFF.toInt()))
    }
}

package com.liwenzhong.autoledger.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityCaptureGateTest {
    @Test
    fun capturesOnlyEnabledOrArmedWeChatWindows() {
        assertTrue(
            AccessibilityCaptureGate.shouldCapture(
                eventPackageName = "com.tencent.mm",
                windowPackageName = "com.tencent.mm",
                armed = true,
                autoCaptureEnabled = false,
            ),
        )
        assertTrue(
            AccessibilityCaptureGate.shouldCapture(
                "com.tencent.mm",
                "com.tencent.mm",
                armed = false,
                autoCaptureEnabled = true,
            ),
        )
        assertFalse(
            AccessibilityCaptureGate.shouldCapture(
                "com.tencent.mm",
                "com.tencent.mm",
                armed = false,
                autoCaptureEnabled = false,
            ),
        )
        assertFalse(
            AccessibilityCaptureGate.shouldCapture(
                "com.example.other",
                "com.tencent.mm",
                armed = true,
                autoCaptureEnabled = true,
            ),
        )
        assertFalse(
            AccessibilityCaptureGate.shouldCapture(
                "com.tencent.mm",
                "com.example.other",
                armed = true,
                autoCaptureEnabled = true,
            ),
        )
    }

    @Test
    fun manualSampleCaptureStopsAfterTheFirstResult() {
        val first = AccessibilitySample(1L, "ocr", listOf("微信支付"))
        val second = AccessibilitySample(2L, "ocr", listOf("不应覆盖"))

        AccessibilitySampleStore.clear()
        AccessibilitySampleStore.arm()
        AccessibilitySampleStore.capture(first)
        AccessibilitySampleStore.capture(second)

        assertEquals(first, AccessibilitySampleStore.latest)
        assertFalse(AccessibilitySampleStore.armed)
        AccessibilitySampleStore.clear()
    }
}

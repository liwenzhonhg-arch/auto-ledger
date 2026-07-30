package com.liwenzhong.autoledger.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityCaptureGateTest {
    @Test
    fun capturesOnlyArmedWeChatWindows() {
        assertTrue(
            AccessibilityCaptureGate.shouldCapture(
                eventPackageName = "com.tencent.mm",
                windowPackageName = "com.tencent.mm",
                armed = true,
            ),
        )
        assertFalse(AccessibilityCaptureGate.shouldCapture("com.tencent.mm", "com.tencent.mm", armed = false))
        assertFalse(AccessibilityCaptureGate.shouldCapture("com.example.other", "com.tencent.mm", armed = true))
        assertFalse(AccessibilityCaptureGate.shouldCapture("com.tencent.mm", "com.example.other", armed = true))
    }
}

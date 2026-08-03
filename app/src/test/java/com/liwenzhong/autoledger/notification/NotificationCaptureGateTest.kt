package com.liwenzhong.autoledger.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationCaptureGateTest {
    @Test
    fun acceptsWeChatInBackgroundAndRejectsOtherPackages() {
        assertTrue(NotificationCaptureGate.shouldCapture("com.tencent.mm"))
        assertFalse(NotificationCaptureGate.shouldCapture("com.example.other"))
    }
}

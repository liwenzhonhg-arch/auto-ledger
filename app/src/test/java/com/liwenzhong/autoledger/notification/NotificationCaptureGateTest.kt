package com.liwenzhong.autoledger.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationCaptureGateTest {
    @Test
    fun capturesArmedWeChatNotificationsIncludingGroupSummaries() {
        assertTrue(NotificationCaptureGate.shouldCapture("com.tencent.mm", armed = true))
        assertFalse(NotificationCaptureGate.shouldCapture("com.tencent.mm", armed = false))
        assertFalse(NotificationCaptureGate.shouldCapture("com.example.other", armed = true))
    }
}

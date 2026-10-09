package com.liwenzhong.autoledger.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationReconnectTest {
    @Test
    fun requestsReconnectOnlyWhenPermissionRemainsAndServiceIsDisconnected() {
        assertFalse(shouldRequestNotificationReconnect(false, false))
        assertFalse(shouldRequestNotificationReconnect(false, true))
        assertFalse(shouldRequestNotificationReconnect(true, true))
        assertTrue(shouldRequestNotificationReconnect(true, false))
    }
}

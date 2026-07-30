package com.liwenzhong.autoledger.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NotificationSampleStoreTest {
    @Test
    fun capturesOneArmedSyntheticSampleInMemory() {
        val sample = NotificationSample(
            postedAtMillis = 1L,
            title = "微信支付",
            text = "付款成功：向测试商户支付 ￥12.34",
            bigText = null,
            subText = "模拟数据",
            textLines = emptyList(),
        )

        NotificationSampleStore.clear()
        NotificationSampleStore.arm()
        NotificationSampleStore.capture(sample)

        assertEquals(sample, NotificationSampleStore.latest)
        assertFalse(NotificationSampleStore.armed)
        NotificationSampleStore.clear()
    }
}

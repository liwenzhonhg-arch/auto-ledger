package com.liwenzhong.autoledger.parser

import com.liwenzhong.autoledger.notification.NotificationSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeChatNotificationParserTest {
    @Test
    fun parsesSyntheticPaymentAndKeepsUnknownRefundFieldsPending() {
        val payment = WeChatNotificationParser.parse(
            NotificationSample(
                postedAtMillis = 60_000L,
                title = "微信支付",
                text = "付款成功：向测试商户支付 ￥12.34",
                bigText = null,
                subText = "模拟数据",
                textLines = emptyList(),
            ),
        )!!

        assertEquals(1_234L, payment.amountCents)
        assertEquals("测试商户", payment.counterparty)
        assertEquals(TransactionDirection.EXPENSE, payment.direction)
        assertEquals("未分类", payment.category)
        assertEquals(1_500L, WeChatNotificationParser.parseYuanInput("15.00"))
        assertNull(WeChatNotificationParser.parseYuanInput("15.001"))

        val refund = WeChatNotificationParser.parse(
            NotificationSample(
                postedAtMillis = 120_000L,
                title = "微信支付",
                text = "退款到账通知",
                bigText = null,
                subText = null,
                textLines = emptyList(),
            ),
        )!!

        assertNull(refund.amountCents)
        assertEquals("未确定", refund.counterparty)
        assertEquals(TransactionDirection.INCOME, refund.direction)
        assertNull(
            WeChatNotificationParser.parse(
                NotificationSample(180_000L, "好友", "晚上好", null, null, emptyList()),
            ),
        )
    }
}

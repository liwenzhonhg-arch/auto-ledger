package com.liwenzhong.autoledger.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeChatScreenParserTest {
    @Test
    fun parsesSingleOcrAmountAndRejectsAmbiguousBillLists() {
        val payment = WeChatScreenParser.parse(
            lines = listOf("微信支付", "支付成功", "xY20.80", "交易详情"),
            capturedAtMillis = 123_000L,
        )!!

        assertEquals(2_080L, payment.amountCents)
        assertEquals(TransactionDirection.EXPENSE, payment.direction)
        assertEquals("未确定", payment.counterparty)

        val latestBill = WeChatScreenParser.parse(
            lines = listOf(
                "23:25",
                "微信支付",
                "王爵售货柜",
                "通过零钱扣款",
                "x3.00",
                "按时支付，记入微信支付分记录",
                "账单详情",
                "我的账单 支付服务 摇优惠",
            ),
            capturedAtMillis = 123_000L,
        )!!

        assertEquals(300L, latestBill.amountCents)
        assertEquals(
            latestBill.fingerprint,
            WeChatScreenParser.parse(
                lines = listOf(
                    "23:26",
                    "返回 微信支付",
                    "王爵售货柜",
                    "通过零钱扣款",
                    "x3.00",
                    "按时支付，记入微信支付分记录",
                    "账单详情",
                    "不同的页面底部文字",
                ),
                capturedAtMillis = 124_000L,
            )!!.fingerprint,
        )

        assertNull(
            WeChatScreenParser.parse(
                lines = listOf(
                    "微信支付",
                    "xY20.80",
                    "交易详情",
                    "美团",
                    "使用零钱支付",
                    "未识别金额",
                    "账单详情",
                    "我的账单 支付服务 摇优惠",
                ),
                capturedAtMillis = 123_000L,
            ),
        )
        assertNull(
            WeChatScreenParser.parse(
                lines = listOf("微信聊天列表", "微信支付", "已扣费 ¥3.00"),
                capturedAtMillis = 123_000L,
            ),
        )
        assertNull(WeChatScreenParser.parse(listOf("普通聊天", "xY20.80"), 123_000L))
    }
}

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

        assertNull(
            WeChatScreenParser.parse(
                lines = listOf(
                    "微信支付",
                    "xY20.80",
                    "交易详情",
                    "美团",
                    "使用零钱支付",
                    "未识别金额",
                    "交易详情",
                    "我的账单 支付服务 摇优惠",
                ),
                capturedAtMillis = 123_000L,
            ),
        )
        assertNull(WeChatScreenParser.parse(listOf("普通聊天", "xY20.80"), 123_000L))
    }
}

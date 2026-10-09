package com.liwenzhong.autoledger.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeChatScreenParserTest {
    @Test
    fun parsesObservedSignedExpenseDetailWithSpacedOcrLabels() {
        val lines = listOf("全部账单", "测试商户", "−12.34", "当前 状态", "支付 成功",
            "支付 时间", "2026年1月2日 12:30:00", "支付方式", "零钱", "交易单号", "000000000000")
        val candidate = WeChatScreenParser.parse(lines, 100L)!!
        assertEquals(1234L, candidate.amountCents)
        assertEquals(TransactionDirection.EXPENSE, candidate.direction)
        assertEquals(1234L, WeChatScreenParser.parse(lines.map {
            if (it == "支付方式") "文付方式" else it
        }, 100L)?.amountCents)
        assertNull(WeChatScreenParser.parse(lines.filterNot { it == "支付方式" }, 100L))
        assertNull(WeChatScreenParser.parse(lines.map { if (it == "−12.34") "−0.00" else it }, 100L))
        assertNull(WeChatScreenParser.parse(lines + "¥99.00", 100L))
        assertNull(WeChatScreenParser.parse(listOf("支付成功", "-12.34", "普通聊天"), 100L))
        assertNull(WeChatScreenParser.parse(listOf("-12.34") + lines, 100L))
    }

    @Test
    fun amountBeforeLateWeChatMarkerDoesNotBreakSubsequentParsing() {
        val reordered = listOf("¥20.80", "支付成功", "其他文字", "返回", "更多", "微信支付")
        assertEquals(2_080L, WeChatScreenParser.parse(reordered, 100L)?.amountCents)
        assertEquals(2_080L, WeChatScreenParser.parse(listOf("微信支付", "支付成功", "¥20.80"), 101L)?.amountCents)
    }

    @Test
    fun explainsRejectionsWithoutReturningRawText() {
        assertEquals(ScreenRejection.NOT_TRANSACTION,
            WeChatScreenParser.inspect(listOf("微信支付", "付款码"), 1L).rejection)
        assertEquals(ScreenRejection.AMOUNT_UNCLEAR,
            WeChatScreenParser.inspect(listOf("支付成功"), 1L).rejection)
        assertEquals(ScreenRejection.MULTIPLE_BILLS,
            WeChatScreenParser.inspect(listOf("微信支付", "交易详情", "账单详情", "¥12.34"), 1L).rejection)
    }
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

package com.liwenzhong.autoledger.parser

import java.security.MessageDigest

object WeChatScreenParser {
    private val markerPattern = Regex("微信支付|支付成功|付款成功|退款到账|交易详情")
    private val incomePattern = Regex("退款|到账|收款成功")
    private val amountPattern = Regex("(?:[￥¥]|[xX][yY])\\s*(\\d+)(?:[.,](\\d{1,2}))?")

    fun parse(lines: List<String>, capturedAtMillis: Long): TransactionCandidate? {
        val text = lines.joinToString(" ")
        if (!markerPattern.containsMatchIn(text)) return null
        if (text.contains("我的账单") || lines.count { it.contains("交易详情") } > 1) return null

        val amounts = amountPattern.findAll(text)
            .mapNotNull(::amountCents)
            .distinct()
            .toList()
        if (amounts.size != 1) return null

        val direction = if (incomePattern.containsMatchIn(text)) {
            TransactionDirection.INCOME
        } else {
            TransactionDirection.EXPENSE
        }
        return TransactionCandidate(
            fingerprint = fingerprint(lines, direction, amounts.single()),
            occurredAtMillis = capturedAtMillis,
            amountCents = amounts.single(),
            direction = direction,
            counterparty = "未确定",
            source = "微信界面 OCR",
        )
    }

    private fun amountCents(match: MatchResult): Long? {
        val yuan = match.groupValues[1].toLongOrNull() ?: return null
        val fraction = match.groupValues[2].padEnd(2, '0').take(2).toLongOrNull() ?: 0L
        if (yuan > (MAX_TRANSACTION_AMOUNT_CENTS - fraction) / 100L) return null
        return yuan * 100L + fraction
    }

    private fun fingerprint(
        lines: List<String>,
        direction: TransactionDirection,
        amountCents: Long,
    ): String {
        val firstWeChatLine = lines.indexOfFirst { it.contains("微信支付") }.coerceAtLeast(0)
        val stableText = lines.drop(firstWeChatLine)
            .joinToString(" ")
            .lowercase()
            .replace(amountPattern, " amount=$amountCents ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return MessageDigest.getInstance("SHA-256")
            .digest("wechat-screen|$direction|$stableText".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

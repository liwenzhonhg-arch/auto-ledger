package com.liwenzhong.autoledger.parser

import java.security.MessageDigest

internal const val WECHAT_SCREEN_SOURCE = "微信界面 OCR"

object WeChatScreenParser {
    private val markerPattern = Regex("微信支付|支付成功|付款成功|退款到账|收款成功|交易详情")
    private val completedTransactionPattern = Regex("支付成功|付款成功|退款到账|收款成功")
    private val incomePattern = Regex("退款|到账|收款成功")
    private val amountPattern = Regex("(?:[￥¥]|[xX](?:[yY])?)\\s*(\\d+)(?:[.,](\\d{1,2}))?")

    fun parse(lines: List<String>, capturedAtMillis: Long): TransactionCandidate? {
        val text = lines.joinToString(" ")
        if (!markerPattern.containsMatchIn(text)) return null
        val visibleBillCount = lines.count {
            it.contains("交易详情") || it.contains("账单详情")
        }
        if (visibleBillCount > 1 ||
            (visibleBillCount == 0 && !completedTransactionPattern.containsMatchIn(text))
        ) return null

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
            source = WECHAT_SCREEN_SOURCE,
        )
    }

    private fun amountCents(match: MatchResult): Long? {
        val yuan = match.groupValues[1].toLongOrNull() ?: return null
        val fraction = match.groupValues[2].padEnd(2, '0').take(2).toLongOrNull() ?: 0L
        if (yuan > (MAX_TRANSACTION_AMOUNT_CENTS - fraction) / 100L) return null
        return yuan * 100L + fraction
    }

    // This identifies visible content, not a unique payment. The repository scopes repeated observations.
    private fun fingerprint(
        lines: List<String>,
        direction: TransactionDirection,
        amountCents: Long,
    ): String {
        val firstWeChatLine = lines.indexOfFirst { it.contains("微信支付") }.coerceAtLeast(0)
        val amountLine = lines.indexOfFirst(amountPattern::containsMatchIn)
        val stableLines = if (amountLine >= 0) {
            lines.subList(
                (amountLine - 2).coerceAtLeast(firstWeChatLine),
                (amountLine + 3).coerceAtMost(lines.size),
            )
        } else {
            lines.drop(firstWeChatLine)
        }
        val stableText = stableLines
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

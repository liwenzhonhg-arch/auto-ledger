package com.liwenzhong.autoledger.parser

import java.security.MessageDigest

internal const val WECHAT_SCREEN_SOURCE = "微信界面 OCR"

enum class ScreenRejection { NOT_TRANSACTION, MULTIPLE_BILLS, AMOUNT_UNCLEAR }
data class ScreenParseResult(val candidate: TransactionCandidate? = null, val rejection: ScreenRejection? = null)

object WeChatScreenParser {
    private val markerPattern = Regex("微信支付|支付成功|付款成功|退款到账|收款成功|交易详情")
    private val completedTransactionPattern = Regex("支付成功|付款成功|退款到账|收款成功")
    private val incomePattern = Regex("退款|到账|收款成功")
    private val amountPattern = Regex("(?:[￥¥]|[xX](?:[yY])?)\\s*(\\d+)(?:[.,](\\d{1,2}))?")
    private val signedExpensePattern = Regex("^[-−－]\\s*(\\d+)[.,](\\d{2})$")

    fun parse(lines: List<String>, capturedAtMillis: Long): TransactionCandidate? {
        return inspect(lines, capturedAtMillis).candidate
    }

    fun inspect(lines: List<String>, capturedAtMillis: Long): ScreenParseResult {
        val text = lines.joinToString(" ")
        val compactLines = lines.map { it.replace(Regex("\\s+"), "") }
        val markerText = compactLines.joinToString("")
        if (!markerPattern.containsMatchIn(markerText)) return ScreenParseResult(rejection = ScreenRejection.NOT_TRANSACTION)
        val visibleBillCount = compactLines.count {
            it.contains("交易详情") || it.contains("账单详情")
        }
        if (visibleBillCount > 1) return ScreenParseResult(rejection = ScreenRejection.MULTIPLE_BILLS)
        if (visibleBillCount == 0 && !completedTransactionPattern.containsMatchIn(markerText)) {
            return ScreenParseResult(rejection = ScreenRejection.NOT_TRANSACTION)
        }

        // Observed single payment-detail layout: a standalone negative amount above the status fields.
        // Do not treat arbitrary signed numbers in chats, dates or order IDs as money.
        val statusIndex = compactLines.indexOfFirst { it.startsWith("当前状态") }
        val signedHeader = if (statusIndex > 0 && markerText.contains("当前状态支付成功") &&
            markerText.contains("支付时间") && Regex("[支文]付方式").containsMatchIn(markerText)) {
            lines.take(statusIndex).mapNotNull { signedExpensePattern.matchEntire(it.trim()) }
        } else emptyList()
        if (signedHeader.size > 1) return ScreenParseResult(rejection = ScreenRejection.AMOUNT_UNCLEAR)
        val amounts = (amountPattern.findAll(text)
            .mapNotNull(::amountCents)
            .toList() + signedHeader.mapNotNull(::amountCents))
            .distinct()
        if (amounts.size != 1 || amounts.single() <= 0) return ScreenParseResult(rejection = ScreenRejection.AMOUNT_UNCLEAR)

        val direction = if (signedHeader.isNotEmpty()) TransactionDirection.EXPENSE else if (incomePattern.containsMatchIn(markerText)) {
            TransactionDirection.INCOME
        } else {
            TransactionDirection.EXPENSE
        }
        return ScreenParseResult(candidate = TransactionCandidate(
            fingerprint = fingerprint(lines.map { line ->
                if (signedHeader.isNotEmpty() && signedExpensePattern.matches(line.trim()))
                    line.trim().replaceFirst(Regex("^[-−－]"), "¥") else line
            }, direction, amounts.single()),
            occurredAtMillis = capturedAtMillis,
            amountCents = amounts.single(),
            direction = direction,
            counterparty = "未确定",
            source = WECHAT_SCREEN_SOURCE,
        ))
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
                (amountLine - 2).coerceAtLeast(0).coerceAtLeast(firstWeChatLine.coerceAtMost(amountLine)),
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

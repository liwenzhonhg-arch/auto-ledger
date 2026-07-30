package com.liwenzhong.autoledger.parser

import com.liwenzhong.autoledger.notification.NotificationSample
import java.security.MessageDigest

const val MAX_TRANSACTION_AMOUNT_CENTS = 99_999_999_999L

enum class TransactionDirection {
    EXPENSE,
    INCOME,
}

data class TransactionCandidate(
    val fingerprint: String,
    val occurredAtMillis: Long,
    val amountCents: Long?,
    val direction: TransactionDirection,
    val counterparty: String,
    val source: String = "微信",
    val category: String = "未分类",
)

object WeChatNotificationParser {
    private val transactionPattern = Regex("微信支付|付款|收款|退款|转账|红包|到账|扣款")
    private val incomePattern = Regex("收款|退款|到账|红包")
    private val amountPattern = Regex("[￥¥]\\s*(\\d+)(?:\\.(\\d{1,2}))?")
    private val amountInputPattern = Regex("[￥¥]?\\s*(\\d+)(?:\\.(\\d{1,2}))?")
    private val counterpartyPattern = Regex("向\\s*([^￥¥\\r\\n]{1,40}?)\\s*支付")

    fun parse(sample: NotificationSample): TransactionCandidate? {
        val text = (
            listOfNotNull(sample.title, sample.text, sample.bigText, sample.subText) +
                sample.textLines
            ).joinToString(" ")
        if (!transactionPattern.containsMatchIn(text)) return null

        return TransactionCandidate(
            fingerprint = fingerprint(text, sample.postedAtMillis),
            occurredAtMillis = sample.postedAtMillis,
            amountCents = amountPattern.find(text)?.let(::amountCents),
            direction = if (incomePattern.containsMatchIn(text)) {
                TransactionDirection.INCOME
            } else {
                TransactionDirection.EXPENSE
            },
            counterparty = counterpartyPattern.find(text)
                ?.groupValues
                ?.get(1)
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: "未确定",
        )
    }

    fun parseYuanInput(input: String): Long? =
        amountInputPattern.matchEntire(input.trim())?.let(::amountCents)

    private fun amountCents(match: MatchResult): Long? {
        val yuan = match.groupValues[1].toLongOrNull() ?: return null
        val fraction = match.groupValues[2].padEnd(2, '0').take(2).toLongOrNull() ?: 0L
        if (yuan > (MAX_TRANSACTION_AMOUNT_CENTS - fraction) / 100L) return null
        return yuan * 100L + fraction
    }

    private fun fingerprint(text: String, postedAtMillis: Long): String {
        val normalized = "${postedAtMillis / 60_000}|${text.lowercase().replace(Regex("\\s+"), " ").trim()}"
        return MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

package com.liwenzhong.autoledger.repository

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.liwenzhong.autoledger.notification.NotificationSample
import com.liwenzhong.autoledger.parser.MAX_TRANSACTION_AMOUNT_CENTS
import com.liwenzhong.autoledger.parser.TransactionCandidate
import com.liwenzhong.autoledger.parser.WeChatNotificationParser

object InMemoryLedgerStore {
    var pending by mutableStateOf<List<TransactionCandidate>>(emptyList())
        private set
    var confirmed by mutableStateOf<List<TransactionCandidate>>(emptyList())
        private set

    fun ingest(sample: NotificationSample): IngestResult {
        val candidate = WeChatNotificationParser.parse(sample) ?: return IngestResult.IGNORED
        if ((pending + confirmed).any { it.fingerprint == candidate.fingerprint }) {
            return IngestResult.DUPLICATE
        }
        pending = listOf(candidate) + pending
        return IngestResult.ADDED
    }

    fun confirm(
        fingerprint: String,
        amountCents: Long?,
        counterparty: String,
        category: String,
    ): Boolean {
        if (amountCents == null || amountCents !in 1L..MAX_TRANSACTION_AMOUNT_CENTS) return false
        val candidate = pending.firstOrNull { it.fingerprint == fingerprint } ?: return false
        val confirmedCandidate = candidate.copy(
            amountCents = amountCents,
            counterparty = counterparty.trim().ifEmpty { "未确定" },
            category = category.trim().ifEmpty { "未分类" },
        )
        pending = pending.filterNot { it.fingerprint == fingerprint }
        confirmed = listOf(confirmedCandidate) + confirmed
        return true
    }

    fun clear() {
        pending = emptyList()
        confirmed = emptyList()
    }
}

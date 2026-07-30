package com.liwenzhong.autoledger.repository

import com.liwenzhong.autoledger.notification.NotificationSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryLedgerStoreTest {
    @Test
    fun deduplicatesThenConfirmsAnEditedCandidate() {
        val sample = NotificationSample(
            postedAtMillis = 60_000L,
            title = "微信支付",
            text = "付款成功：向测试商户支付 ￥12.34",
            bigText = null,
            subText = "模拟数据",
            textLines = emptyList(),
        )

        InMemoryLedgerStore.clear()
        assertEquals(IngestResult.ADDED, InMemoryLedgerStore.ingest(sample))
        assertEquals(IngestResult.DUPLICATE, InMemoryLedgerStore.ingest(sample))
        assertEquals(1, InMemoryLedgerStore.pending.size)

        val fingerprint = InMemoryLedgerStore.pending.single().fingerprint
        assertTrue(
            InMemoryLedgerStore.confirm(
                fingerprint = fingerprint,
                amountCents = 1_500L,
                counterparty = "修改后的商户",
                category = "餐饮",
            ),
        )

        assertTrue(InMemoryLedgerStore.pending.isEmpty())
        assertEquals(1_500L, InMemoryLedgerStore.confirmed.single().amountCents)
        assertEquals("修改后的商户", InMemoryLedgerStore.confirmed.single().counterparty)
        assertEquals("餐饮", InMemoryLedgerStore.confirmed.single().category)
        InMemoryLedgerStore.clear()
    }
}

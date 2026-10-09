package com.liwenzhong.autoledger.repository

import com.liwenzhong.autoledger.parser.WeChatScreenParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ScreenObservationDeduplicationTest {
    @Test
    fun mergesCaptureBurstButKeepsLaterIdenticalPaymentsIncludingAcrossRestart() {
        val first = observation(59_900L)
        val stored = screenObservationForInsert(first, null)
        val nextFrame = screenObservationForInsert(observation(60_100L), stored)
        assertEquals(stored.fingerprint, nextFrame.fingerprint)
        assertEquals(stored.fingerprint, screenObservationForInsert(observation(149_899L), stored).fingerprint)

        val second = screenObservationForInsert(observation(149_900L), stored)
        assertNotEquals(stored.fingerprint, second.fingerprint)
        assertEquals(second.fingerprint, screenObservationForInsert(observation(150_100L), second).fingerprint)
        assertNotEquals(second.fingerprint, screenObservationForInsert(observation(86_400_000L), second).fingerprint)
    }

    @Test
    fun handlesLegacyRowsOutOfOrderWritesAndLargeClockChanges() {
        val legacy = observation(100_000L).copy(status = "CONFIRMED", amountCents = 900L)
        assertEquals(legacy.fingerprint, screenObservationForInsert(observation(101_000L), legacy).fingerprint)
        assertNotEquals(legacy.fingerprint, screenObservationForInsert(observation(190_000L), legacy).fingerprint)
        assertEquals(legacy.fingerprint, screenObservationForInsert(observation(99_000L), legacy).fingerprint)
        assertNotEquals(legacy.fingerprint, screenObservationForInsert(observation(10_000L), legacy).fingerprint)

        val other = legacy.copy(fingerprint = "other-content")
        assertNotEquals(other.fingerprint, screenObservationForInsert(observation(101_000L), other).fingerprint)
    }

    private fun observation(at: Long): TransactionEntity {
        val candidate = WeChatScreenParser.parse(
            listOf("微信支付", "支付成功", "测试商户", "¥3.00", "交易详情"),
            capturedAtMillis = at,
        )!!
        return TransactionEntity(
            occurredAtMillis = at,
            amountCents = candidate.amountCents,
            direction = candidate.direction.name,
            counterparty = candidate.counterparty,
            source = candidate.source,
            category = candidate.category,
            classificationSource = "UNCLASSIFIED",
            fingerprint = candidate.fingerprint,
            status = "PENDING",
        )
    }
}

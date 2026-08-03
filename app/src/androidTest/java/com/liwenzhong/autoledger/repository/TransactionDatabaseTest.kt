package com.liwenzhong.autoledger.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionDatabaseTest {
    private lateinit var database: AutoLedgerDatabase
    private lateinit var dao: TransactionDao

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AutoLedgerDatabase::class.java,
        ).build()
        dao = database.transactionDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun uniqueFingerprintSurvivesConfirmation() = runBlocking {
        val candidate = TransactionEntity(
            fingerprint = "test-fingerprint",
            occurredAtMillis = 60_000L,
            amountCents = 1_234L,
            direction = "EXPENSE",
            counterparty = "测试商户",
            source = "微信",
            category = "未分类",
            classificationSource = "UNCLASSIFIED",
            status = "PENDING",
        )

        assertNotEquals(-1L, dao.insert(candidate))
        assertEquals(-1L, dao.insert(candidate))
        assertEquals(
            1,
            dao.confirm(
                fingerprint = candidate.fingerprint,
                amountCents = 1_500L,
                counterparty = "修改后的商户",
                category = "餐饮",
            ),
        )

        val stored = dao.findByFingerprint(candidate.fingerprint)!!
        assertEquals(1_500L, stored.amountCents)
        assertEquals("餐饮", stored.category)
        assertEquals("MANUAL", stored.classificationSource)
        assertEquals("CONFIRMED", stored.status)
    }

    @Test
    fun deletesOnlyFixedSyntheticMerchant() = runBlocking {
        val template = TransactionEntity(
            occurredAtMillis = 60_000L,
            amountCents = 1_234L,
            direction = "EXPENSE",
            counterparty = "测试商户",
            source = "微信",
            category = "未分类",
            classificationSource = "UNCLASSIFIED",
            fingerprint = "synthetic",
            status = "PENDING",
        )
        dao.insert(template)
        dao.insert(
            template.copy(
                counterparty = "未确定",
                fingerprint = "real",
            ),
        )

        assertEquals(1, dao.deleteSyntheticTransactions())
        assertEquals(null, dao.findByFingerprint("synthetic"))
        assertEquals("未确定", dao.findByFingerprint("real")?.counterparty)
    }
}

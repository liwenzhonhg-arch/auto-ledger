package com.liwenzhong.autoledger.ui

import com.liwenzhong.autoledger.parser.TransactionDirection
import com.liwenzhong.autoledger.parser.TransactionCandidate
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionDisplayTest {
    @Test
    fun doesNotClaimCaptureIsRunningFromSavedSwitchOrPermissionAlone() {
        assertEquals("未授权", accessibilityCaptureLabel(false, true, false))
        assertEquals("未授权", accessibilityCaptureLabel(false, true, true))
        assertEquals("未开启", accessibilityCaptureLabel(true, false, true))
        assertEquals("未连接", accessibilityCaptureLabel(true, true, false))
        assertEquals("运行中", accessibilityCaptureLabel(true, true, true))
        assertEquals("未授权", notificationConnectionLabel(false, true))
        assertEquals("未连接", notificationConnectionLabel(true, false))
        assertEquals("已连接", notificationConnectionLabel(true, true))
    }

    @Test
    fun labelsOnlyUnclassifiedExpensesAsExpense() {
        assertEquals(
            "支出",
            transactionCategoryLabel("未分类", TransactionDirection.EXPENSE),
        )
        assertEquals(
            "未分类",
            transactionCategoryLabel("未分类", TransactionDirection.INCOME),
        )
        assertEquals(
            "餐饮",
            transactionCategoryLabel("餐饮", TransactionDirection.EXPENSE),
        )
    }

    @Test
    fun aggregatesExpenseCategoriesAndKeepsManualAmountValid() {
        val transactions = listOf(
            candidate("a", 300L, TransactionDirection.EXPENSE, "未分类"),
            candidate("b", 500L, TransactionDirection.EXPENSE, "餐饮"),
            candidate("c", 200L, TransactionDirection.EXPENSE, "餐饮"),
            candidate("d", 900L, TransactionDirection.INCOME, "收入"),
        )

        assertEquals(
            listOf(CategoryExpenseTotal("餐饮", 700L), CategoryExpenseTotal("支出", 300L)),
            expenseTotalsByCategory(transactions),
        )
        assertEquals("12.34", listOf("1", "2", ".", "3", "4", "5").fold("0", ::updateManualAmount))
        assertEquals("12.3", updateManualAmount("12.34", "⌫"))
    }

    private fun candidate(
        fingerprint: String,
        amountCents: Long,
        direction: TransactionDirection,
        category: String,
    ) = TransactionCandidate(
        fingerprint = fingerprint,
        occurredAtMillis = 1L,
        amountCents = amountCents,
        direction = direction,
        counterparty = "测试商户",
        category = category,
    )
}

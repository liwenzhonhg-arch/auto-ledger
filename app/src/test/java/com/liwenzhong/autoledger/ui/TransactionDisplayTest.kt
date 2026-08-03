package com.liwenzhong.autoledger.ui

import com.liwenzhong.autoledger.parser.TransactionDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionDisplayTest {
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
}

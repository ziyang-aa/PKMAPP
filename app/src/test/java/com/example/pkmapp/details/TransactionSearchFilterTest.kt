package com.example.pkmapp.details

import com.example.pkmapp.data.Transaction
import com.example.pkmapp.data.TransactionType
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionSearchFilterTest {

    private fun millisOf(year: Int, monthZeroBased: Int, day: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(year, monthZeroBased, day, 12, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun sampleTransactions(): List<Transaction> = listOf(
        Transaction("t1", "default", TransactionType.EXPENSE, 3500L, "餐饮", "手冲咖啡", millisOf(2026, 10, 20)),
        Transaction("t2", "default", TransactionType.EXPENSE, 250000L, "居住", "十月房租", millisOf(2026, 9, 1)),
        Transaction("t3", "default", TransactionType.INCOME, 850000L, "工资", "十一月工资", millisOf(2026, 10, 10)),
        Transaction("t4", "default", TransactionType.EXPENSE, 12800L, "餐饮", "聚餐火锅", millisOf(2026, 10, 15))
    )

    @Test
    fun searchesAcrossMonthsByNoteCategoryAndAmount() {
        val all = sampleTransactions()

        val byNote = TransactionSearchFilter.filter(all, TransactionSearchFilter.Query(keyword = "房租"))
        assertEquals(listOf("t2"), byNote.items.map { it.id })
        assertEquals(250000L, byNote.expenseCents)
        assertEquals(0L, byNote.incomeCents)

        val byCategory = TransactionSearchFilter.filter(all, TransactionSearchFilter.Query(keyword = "餐饮"))
        assertEquals(listOf("t1", "t4"), byCategory.items.map { it.id })
        assertEquals(16300L, byCategory.expenseCents)

        val byAmount = TransactionSearchFilter.filter(all, TransactionSearchFilter.Query(keyword = "8500"))
        assertEquals(listOf("t3"), byAmount.items.map { it.id })
        assertEquals(850000L, byAmount.incomeCents)
    }

    @Test
    fun appliesCurrentMonthScopeTypeCategoryAndAmountBounds() {
        val all = sampleTransactions()
        val result = TransactionSearchFilter.filter(
            all,
            TransactionSearchFilter.Query(
                scope = TransactionSearchFilter.Scope.CURRENT_MONTH,
                type = TransactionType.EXPENSE,
                category = "餐饮",
                minCents = 4000L,
                maxCents = 20000L,
                monthYear = 2026,
                monthZeroBased = 10
            )
        )
        assertEquals(listOf("t4"), result.items.map { it.id })
        assertEquals(12800L, result.expenseCents)
        assertEquals(-12800L, result.netCents)
    }

    @Test
    fun parsesOptionalYuanSafely() {
        assertNull(TransactionSearchFilter.parseOptionalYuanToCents(""))
        assertNull(TransactionSearchFilter.parseOptionalYuanToCents("-5"))
        assertEquals(1250L, TransactionSearchFilter.parseOptionalYuanToCents("¥12.5"))
        assertEquals(100000L, TransactionSearchFilter.parseOptionalYuanToCents("1,000"))
    }
}

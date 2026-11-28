package com.example.pkmapp.recurring

import com.example.pkmapp.data.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringTransactionEngineTest {

    private fun monthlyRule(
        day: Int = 31,
        start: String = "2026-01-01",
        lastGen: String = "",
        enabled: Boolean = true
    ) = RecurringRule(
        id = "rent1",
        ledgerId = "l1",
        title = "房租",
        type = TransactionType.EXPENSE,
        category = "居住",
        amountCents = 250000L,
        frequency = RecurringFrequency.MONTHLY,
        dayOfMonth = day,
        startDateKey = start,
        lastGeneratedDateKey = lastGen,
        autoPost = true,
        enabled = enabled
    )

    @Test
    fun monthlyClampsDay31AcrossShortMonthsAndLeapYear() {
        val nonLeap = RecurringTransactionEngine.dueOccurrences(
            monthlyRule(day = 31, start = "2026-01-01"),
            todayDateKey = "2026-04-30"
        )
        assertEquals(listOf("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30"), nonLeap)

        val leap = RecurringTransactionEngine.dueOccurrences(
            monthlyRule(day = 31, start = "2028-01-01"),
            todayDateKey = "2028-03-01"
        )
        assertEquals(listOf("2028-01-31", "2028-02-29"), leap)
    }

    @Test
    fun respectsLastGeneratedCursorAndDisabledFlag() {
        val afterCursor = RecurringTransactionEngine.dueOccurrences(
            monthlyRule(day = 15, start = "2026-01-01", lastGen = "2026-02-15"),
            todayDateKey = "2026-04-14"
        )
        assertEquals(listOf("2026-03-15"), afterCursor)

        val disabled = RecurringTransactionEngine.dueOccurrences(
            monthlyRule(day = 15, start = "2026-01-01", enabled = false),
            todayDateKey = "2026-04-30"
        )
        assertTrue(disabled.isEmpty())
    }

    @Test
    fun supportsWeeklyYearlyAndCatchupCap() {
        val weekly = RecurringRule(
            id = "w1",
            ledgerId = "l1",
            title = "健身课",
            type = TransactionType.EXPENSE,
            category = "运动",
            amountCents = 10000L,
            frequency = RecurringFrequency.WEEKLY,
            dayOfWeek = 1, // Monday
            startDateKey = "2026-11-01"
        )
        assertEquals(
            listOf("2026-11-02", "2026-11-09", "2026-11-16", "2026-11-23"),
            RecurringTransactionEngine.dueOccurrences(weekly, "2026-11-27")
        )

        val yearlyLeapDay = RecurringRule(
            id = "y1",
            ledgerId = "l1",
            title = "年费会员",
            type = TransactionType.EXPENSE,
            category = "其他",
            amountCents = 12000L,
            frequency = RecurringFrequency.YEARLY,
            monthOfYear = 2,
            dayOfMonth = 29,
            startDateKey = "2024-01-01"
        )
        assertEquals(
            listOf("2024-02-29", "2025-02-28", "2026-02-28"),
            RecurringTransactionEngine.dueOccurrences(yearlyLeapDay, "2026-11-27")
        )

        // Catch-up cap prevents flooding when startDate is years ago
        val capped = RecurringTransactionEngine.dueOccurrences(
            monthlyRule(day = 1, start = "2020-01-01"),
            todayDateKey = "2026-11-27"
        )
        assertEquals(12, capped.size)
        assertEquals("2026-11-01", capped.last())
    }

    @Test
    fun computesDeterministicIdNextDueAndMonthlyEquivalent() {
        assertEquals(
            "rec_rent1_20261201",
            RecurringTransactionEngine.deterministicTransactionId("rent1", "2026-12-01")
        )
        val rule = monthlyRule(day = 1, start = "2026-11-01", lastGen = "2026-11-01")
        assertEquals("2026-12-01", RecurringTransactionEngine.nextDueDateKey(rule, "2026-11-27"))
        assertEquals(4, RecurringTransactionEngine.daysUntil("2026-11-27", "2026-12-01"))

        val yearlyIncome = RecurringRule(
            id = "bonus",
            ledgerId = "l1",
            title = "年度分红",
            type = TransactionType.INCOME,
            category = "理财",
            amountCents = 120000L,
            frequency = RecurringFrequency.YEARLY,
            monthOfYear = 12,
            dayOfMonth = 1,
            startDateKey = "2026-01-01"
        )
        assertEquals(250000L, RecurringTransactionEngine.monthlyEquivalentCents(listOf(rule, yearlyIncome), TransactionType.EXPENSE))
        assertEquals(10000L, RecurringTransactionEngine.monthlyEquivalentCents(listOf(rule, yearlyIncome), TransactionType.INCOME))
    }
}

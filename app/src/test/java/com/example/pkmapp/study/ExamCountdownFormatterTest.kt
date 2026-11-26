package com.example.pkmapp.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ExamCountdownFormatterTest {

    private fun localMillis(year: Int, month: Int, day: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, day, 12, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    @Test
    fun evaluate_computesRemainingTodayAndOverdueDays() {
        val now = localMillis(2026, 11, 26)
        assertNull(ExamCountdownFormatter.evaluate("408 考研", "", now))
        assertNull(ExamCountdownFormatter.evaluate("408 考研", "invalid", now))

        val future = ExamCountdownFormatter.evaluate("408 考研", "2026-12-20", now)
        assertNotNull(future)
        assertEquals(24L, future!!.daysRemaining)
        assertTrue(future.focusHeadline.contains("24 天"))
        assertTrue(future.homeCompactBadge.contains("24天"))

        val today = ExamCountdownFormatter.evaluate("期末周", "2026-11-26", now)
        assertEquals(0L, today!!.daysRemaining)
        assertTrue(today.focusHeadline.contains("今天"))

        val past = ExamCountdownFormatter.evaluate("蓝桥杯", "2026-11-20", now)
        assertEquals(-6L, past!!.daysRemaining)
        assertTrue(past.focusHeadline.contains("已过 6 天"))
    }
}

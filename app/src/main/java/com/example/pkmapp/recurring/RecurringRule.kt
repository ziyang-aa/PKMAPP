package com.example.pkmapp.recurring

import com.example.pkmapp.data.TransactionType

enum class RecurringFrequency(val label: String) {
    MONTHLY("每月"),
    WEEKLY("每周"),
    YEARLY("每年");

    companion object {
        @JvmStatic
        fun parse(raw: String?): RecurringFrequency =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: MONTHLY
    }
}

data class RecurringRule(
    val id: String,
    val ledgerId: String,
    val title: String,
    val type: TransactionType,
    val category: String,
    val amountCents: Long,
    val frequency: RecurringFrequency,
    val dayOfMonth: Int = 1,
    val dayOfWeek: Int = 1,
    val monthOfYear: Int = 1,
    val startDateKey: String,
    val lastGeneratedDateKey: String = "",
    val autoPost: Boolean = true,
    val enabled: Boolean = true,
    val note: String = "",
    val sortOrder: Int = 0
) {
    fun scheduleLabel(): String = when (frequency) {
        RecurringFrequency.MONTHLY -> "每月 ${dayOfMonth.coerceIn(1, 31)} 日"
        RecurringFrequency.WEEKLY -> "每${WEEKDAY_LABELS[(dayOfWeek - 1).coerceIn(0, 6)]}"
        RecurringFrequency.YEARLY -> "每年 ${monthOfYear.coerceIn(1, 12)}月${dayOfMonth.coerceIn(1, 31)}日"
    }

    companion object {
        private val WEEKDAY_LABELS = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    }
}

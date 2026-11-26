package com.example.pkmapp.data

import kotlin.math.roundToInt

/** Pure monthly budget evaluator shared by Details UI, widgets, and unit tests. */
object MonthlyBudgetEvaluator {

    enum class Level {
        UNSET,
        NORMAL,
        WARNING,
        EXCEEDED
    }

    data class Status(
        val budgetCents: Long,
        val expenseCents: Long,
        val remainingCents: Long,
        val usagePercent: Int,
        val progressPermille: Int,
        val level: Level
    ) {
        val isSet: Boolean get() = budgetCents > 0L
        val overBudgetCents: Long get() = if (remainingCents < 0L) -remainingCents else 0L
    }

    @JvmStatic
    fun evaluate(budgetCents: Long, expenseCents: Long): Status {
        val safeBudget = budgetCents.coerceAtLeast(0L)
        val safeExpense = expenseCents.coerceAtLeast(0L)
        if (safeBudget == 0L) {
            return Status(
                budgetCents = 0L,
                expenseCents = safeExpense,
                remainingCents = 0L,
                usagePercent = 0,
                progressPermille = 0,
                level = Level.UNSET
            )
        }
        val remaining = safeBudget - safeExpense
        val ratio = safeExpense.toDouble() / safeBudget.toDouble()
        val usagePercent = (ratio * 100.0).roundToInt().coerceAtLeast(0)
        val progressPermille = (ratio.coerceIn(0.0, 1.0) * 1000.0).roundToInt()
        val level = when {
            safeExpense > safeBudget -> Level.EXCEEDED
            safeExpense * 100L >= safeBudget * 80L -> Level.WARNING
            else -> Level.NORMAL
        }
        return Status(
            budgetCents = safeBudget,
            expenseCents = safeExpense,
            remainingCents = remaining,
            usagePercent = usagePercent,
            progressPermille = progressPermille,
            level = level
        )
    }
}

package com.example.pkmapp.details

import com.example.pkmapp.data.Transaction
import com.example.pkmapp.data.TransactionType
import com.example.pkmapp.record.MoneyParser
import java.util.Calendar
import java.util.Locale

/**
 * Pure multi-criteria search & summary engine for Life Cabin transactions.
 */
object TransactionSearchFilter {

    enum class Scope {
        ALL_TIME,
        CURRENT_MONTH
    }

    data class Query(
        val keyword: String = "",
        val scope: Scope = Scope.ALL_TIME,
        val type: TransactionType? = null,
        val category: String? = null,
        val minCents: Long? = null,
        val maxCents: Long? = null,
        val monthYear: Int = 0,
        val monthZeroBased: Int = 0
    ) {
        val hasActiveFilters: Boolean
            get() = keyword.isNotBlank() ||
                type != null ||
                !category.isNullOrBlank() ||
                minCents != null ||
                maxCents != null
    }

    data class SearchResult(
        val items: List<Transaction>,
        val expenseCents: Long,
        val incomeCents: Long
    ) {
        val netCents: Long
            get() = incomeCents - expenseCents
    }

    @JvmStatic
    fun filter(transactions: List<Transaction>, query: Query): SearchResult {
        val normalizedKeyword = query.keyword.trim().lowercase(Locale.ROOT)
        val normalizedCategory = query.category?.trim()?.takeIf { it.isNotEmpty() }
        val minBound = query.minCents
        val maxBound = query.maxCents
        val cal = Calendar.getInstance()

        val matched = ArrayList<Transaction>()
        var expenseTotal = 0L
        var incomeTotal = 0L

        for (tx in transactions) {
            if (query.scope == Scope.CURRENT_MONTH && query.monthYear > 0) {
                cal.timeInMillis = tx.occurredAtMillis
                if (cal.get(Calendar.YEAR) != query.monthYear ||
                    cal.get(Calendar.MONTH) != query.monthZeroBased
                ) {
                    continue
                }
            }
            if (query.type != null && tx.type != query.type) {
                continue
            }
            if (normalizedCategory != null && tx.category != normalizedCategory) {
                continue
            }
            if (minBound != null && tx.amountInCents < minBound) {
                continue
            }
            if (maxBound != null && tx.amountInCents > maxBound) {
                continue
            }
            if (normalizedKeyword.isNotEmpty() && !matchesKeyword(tx, normalizedKeyword)) {
                continue
            }
            matched.add(tx)
            if (tx.type == TransactionType.INCOME) {
                incomeTotal += tx.amountInCents
            } else {
                expenseTotal += tx.amountInCents
            }
        }

        matched.sortWith(
            compareByDescending<Transaction> { it.occurredAtMillis }
                .thenByDescending { it.id }
        )

        return SearchResult(
            items = matched,
            expenseCents = expenseTotal,
            incomeCents = incomeTotal
        )
    }

    @JvmStatic
    fun parseOptionalYuanToCents(raw: String?): Long? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return runCatching {
            val cleaned = trimmed.replace("¥", "").replace("￥", "").replace(",", "")
            val parsed = cleaned.toBigDecimalOrNull() ?: return null
            if (parsed.signum() < 0) return null
            parsed.movePointRight(2).toLong()
        }.getOrNull()
    }

    private fun matchesKeyword(tx: Transaction, lowerKeyword: String): Boolean {
        if (tx.note.lowercase(Locale.ROOT).contains(lowerKeyword)) return true
        if (tx.category.lowercase(Locale.ROOT).contains(lowerKeyword)) return true
        val formatted = MoneyParser.formatCents(tx.amountInCents).lowercase(Locale.ROOT)
        if (formatted.contains(lowerKeyword)) return true
        val plain = formatted.removePrefix("¥").removePrefix("￥").replace(",", "")
        return plain.contains(lowerKeyword)
    }
}

package com.example.pkmapp.recurring

import android.content.Context
import com.example.pkmapp.core.database.AccountScope
import com.example.pkmapp.core.database.AppDatabase
import com.example.pkmapp.data.InMemoryLedgerRepository
import com.example.pkmapp.data.TransactionType
import com.example.pkmapp.widget.ForestWidgets
import java.util.Calendar
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Deterministic, idempotent recurring bill scheduler & auto-posting engine.
 */
object RecurringTransactionEngine {

    private const val MAX_CATCHUP_CYCLES = 12

    data class PendingConfirmation(
        val rule: RecurringRule,
        val dueDateKey: String
    )

    data class RunSummary(
        val autoPostedTitles: List<String>,
        val autoPostedAmountCents: Long,
        val pendingConfirmations: List<PendingConfirmation>
    ) {
        val postedCount: Int get() = autoPostedTitles.size
    }

    @JvmStatic
    fun deterministicTransactionId(ruleId: String, dateKey: String): String =
        "rec_${ruleId}_${dateKey.replace("-", "")}"

    @JvmStatic
    fun todayDateKey(): String {
        val cal = Calendar.getInstance()
        return formatDateKey(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    /**
     * Pure calculation of all due occurrence date keys (YYYY-MM-DD) up to [todayDateKey],
     * strictly after [RecurringRule.lastGeneratedDateKey] and on/after [RecurringRule.startDateKey].
     */
    @JvmStatic
    fun dueOccurrences(
        rule: RecurringRule,
        todayDateKey: String,
        maxCycles: Int = MAX_CATCHUP_CYCLES
    ): List<String> {
        if (!rule.enabled || maxCycles <= 0) return emptyList()
        val today = parseDateKey(todayDateKey) ?: return emptyList()
        val start = parseDateKey(rule.startDateKey) ?: today
        if (compareYmd(start, today) > 0) return emptyList()

        val lastGen = parseDateKey(rule.lastGeneratedDateKey)
        val floor = if (lastGen != null && compareYmd(lastGen, start) >= 0) {
            addDays(lastGen, 1)
        } else {
            start
        }
        if (compareYmd(floor, today) > 0) return emptyList()

        var cursor = firstOccurrenceOnOrAfter(rule, floor)
        val allDue = ArrayList<String>()
        var guard = 0
        while (compareYmd(cursor, today) <= 0 && guard < 120) {
            allDue.add(formatDateKey(cursor.year, cursor.month, cursor.day))
            cursor = nextOccurrenceAfter(rule, cursor)
            guard++
        }
        return if (allDue.size > maxCycles) {
            allDue.subList(allDue.size - maxCycles, allDue.size)
        } else {
            allDue
        }
    }

    @JvmStatic
    fun nextDueDateKey(rule: RecurringRule, todayDateKey: String): String {
        val today = parseDateKey(todayDateKey) ?: return todayDateKey
        val start = parseDateKey(rule.startDateKey) ?: today
        val lastGen = parseDateKey(rule.lastGeneratedDateKey)
        var floor = if (compareYmd(start, today) > 0) start else today
        if (lastGen != null && compareYmd(lastGen, floor) >= 0) {
            floor = addDays(lastGen, 1)
        }
        val next = firstOccurrenceOnOrAfter(rule, floor)
        return formatDateKey(next.year, next.month, next.day)
    }

    @JvmStatic
    fun daysUntil(fromDateKey: String, toDateKey: String): Int {
        val a = parseDateKey(fromDateKey) ?: return 0
        val b = parseDateKey(toDateKey) ?: return 0
        val calA = ymdToNoonMillis(a)
        val calB = ymdToNoonMillis(b)
        return ((calB - calA) / 86_400_000L).toInt()
    }

    @JvmStatic
    fun monthlyEquivalentCents(rules: List<RecurringRule>, type: TransactionType): Long {
        var total = 0L
        for (rule in rules) {
            if (!rule.enabled || rule.type != type) continue
            total += when (rule.frequency) {
                RecurringFrequency.MONTHLY -> rule.amountCents
                RecurringFrequency.WEEKLY -> (rule.amountCents * 52.0 / 12.0).roundToLong()
                RecurringFrequency.YEARLY -> (rule.amountCents / 12.0).roundToLong()
            }
        }
        return total
    }

    /**
     * Executes idempotent catch-up for all enabled rules under the active account.
     */
    @JvmStatic
    @JvmOverloads
    fun processDueRules(
        context: Context,
        todayKey: String = todayDateKey()
    ): RunSummary {
        val appContext = context.applicationContext
        val store = RecurringRuleStore(appContext)
        val rules = store.getAll()
        if (rules.isEmpty()) {
            return RunSummary(emptyList(), 0L, emptyList())
        }

        val db = AppDatabase.getInstance(appContext)
        val ownerId = AccountScope.currentOwnerId(appContext)
        // Include soft-deleted rows so a user-deleted generated bill is never resurrected!
        val knownTxIds = db.ledgerDao().getAllTransactionRowsIncludingDeleted(ownerId)
            .map { it.id }
            .toHashSet()
        val repo = InMemoryLedgerRepository.getInstance()

        val postedTitles = ArrayList<String>()
        var postedTotal = 0L
        val pending = ArrayList<PendingConfirmation>()

        for (rule in rules) {
            if (!rule.enabled) continue
            val dueKeys = dueOccurrences(rule, todayKey)
            if (dueKeys.isEmpty()) continue

            if (rule.autoPost) {
                var latestProcessed = rule.lastGeneratedDateKey
                for (dateKey in dueKeys) {
                    val txId = deterministicTransactionId(rule.id, dateKey)
                    if (!knownTxIds.contains(txId)) {
                        val inserted = repo.addTransactionWithId(
                            txId,
                            rule.ledgerId,
                            rule.type,
                            rule.amountCents,
                            rule.category,
                            buildTransactionNote(rule),
                            dateKeyToNoonMillis(dateKey)
                        )
                        if (inserted) {
                            knownTxIds.add(txId)
                            postedTitles.add(rule.title)
                            postedTotal += rule.amountCents
                        }
                    }
                    if (dateKey > latestProcessed) {
                        latestProcessed = dateKey
                    }
                }
                if (latestProcessed > rule.lastGeneratedDateKey) {
                    store.advanceLastGenerated(rule.id, latestProcessed)
                }
            } else {
                // Reminder-only rules expose their due dates for 1-tap confirmation
                for (dateKey in dueKeys) {
                    val txId = deterministicTransactionId(rule.id, dateKey)
                    if (!knownTxIds.contains(txId)) {
                        pending.add(PendingConfirmation(rule, dateKey))
                    }
                }
            }
        }

        if (postedTitles.isNotEmpty()) {
            ForestWidgets.refreshAll(appContext)
        }
        return RunSummary(postedTitles, postedTotal, pending)
    }

    @JvmStatic
    fun confirmPendingItem(context: Context, rule: RecurringRule, dueDateKey: String): Boolean {
        val appContext = context.applicationContext
        val txId = deterministicTransactionId(rule.id, dueDateKey)
        val inserted = InMemoryLedgerRepository.getInstance().addTransactionWithId(
            txId,
            rule.ledgerId,
            rule.type,
            rule.amountCents,
            rule.category,
            buildTransactionNote(rule),
            dateKeyToNoonMillis(dueDateKey)
        )
        RecurringRuleStore(appContext).advanceLastGenerated(rule.id, dueDateKey)
        if (inserted) {
            ForestWidgets.refreshAll(appContext)
        }
        return inserted
    }

    @JvmStatic
    fun skipPendingItem(context: Context, rule: RecurringRule, dueDateKey: String) {
        RecurringRuleStore(context.applicationContext).advanceLastGenerated(rule.id, dueDateKey)
    }

    @JvmStatic
    fun pendingForLedger(
        context: Context,
        ledgerId: String,
        todayKey: String = todayDateKey()
    ): List<PendingConfirmation> {
        val appContext = context.applicationContext
        val rules = RecurringRuleStore(appContext).getForLedger(ledgerId)
        if (rules.isEmpty()) return emptyList()
        val ownerId = AccountScope.currentOwnerId(appContext)
        val knownTxIds = AppDatabase.getInstance(appContext).ledgerDao()
            .getAllTransactionRowsIncludingDeleted(ownerId)
            .map { it.id }
            .toHashSet()
        val out = ArrayList<PendingConfirmation>()
        for (rule in rules) {
            if (!rule.enabled || rule.autoPost) continue
            for (dateKey in dueOccurrences(rule, todayKey)) {
                val txId = deterministicTransactionId(rule.id, dateKey)
                if (!knownTxIds.contains(txId)) {
                    out.add(PendingConfirmation(rule, dateKey))
                }
            }
        }
        return out
    }

    private fun buildTransactionNote(rule: RecurringRule): String =
        if (rule.note.isBlank()) "[周期] ${rule.title}" else "[周期] ${rule.title} · ${rule.note}"

    // --- Pure YMD Calendar Math ---

    private data class Ymd(val year: Int, val month: Int, val day: Int)

    private fun parseDateKey(raw: String?): Ymd? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.trim().split("-")
        if (parts.size != 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val d = parts[2].toIntOrNull() ?: return null
        if (y < 1970 || m !in 1..12 || d !in 1..31) return null
        return Ymd(y, m, min(d, daysInMonth(y, m)))
    }

    private fun formatDateKey(year: Int, month: Int, day: Int): String =
        String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day)

    private fun compareYmd(a: Ymd, b: Ymd): Int {
        if (a.year != b.year) return a.year - b.year
        if (a.month != b.month) return a.month - b.month
        return a.day - b.day
    }

    private fun isLeapYear(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

    private fun daysInMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (isLeapYear(year)) 29 else 28
        else -> 30
    }

    private fun addDays(ymd: Ymd, deltaDays: Int): Ymd {
        val cal = Calendar.getInstance()
        cal.set(ymd.year, ymd.month - 1, ymd.day, 12, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_MONTH, deltaDays)
        return Ymd(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    private fun isoDayOfWeek(ymd: Ymd): Int {
        val cal = Calendar.getInstance()
        cal.set(ymd.year, ymd.month - 1, ymd.day, 12, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val dow = cal.get(Calendar.DAY_OF_WEEK) // Sun=1, Mon=2 .. Sat=7
        return if (dow == Calendar.SUNDAY) 7 else dow - 1
    }

    private fun firstOccurrenceOnOrAfter(rule: RecurringRule, floor: Ymd): Ymd = when (rule.frequency) {
        RecurringFrequency.MONTHLY -> {
            val clamped = min(rule.dayOfMonth.coerceIn(1, 31), daysInMonth(floor.year, floor.month))
            val candidate = Ymd(floor.year, floor.month, clamped)
            if (compareYmd(candidate, floor) >= 0) {
                candidate
            } else {
                val nextMonth = if (floor.month == 12) 1 else floor.month + 1
                val nextYear = if (floor.month == 12) floor.year + 1 else floor.year
                Ymd(nextYear, nextMonth, min(rule.dayOfMonth.coerceIn(1, 31), daysInMonth(nextYear, nextMonth)))
            }
        }
        RecurringFrequency.WEEKLY -> {
            val targetDow = rule.dayOfWeek.coerceIn(1, 7)
            val currentDow = isoDayOfWeek(floor)
            val diff = (targetDow - currentDow + 7) % 7
            addDays(floor, diff)
        }
        RecurringFrequency.YEARLY -> {
            val m = rule.monthOfYear.coerceIn(1, 12)
            val candidate = Ymd(floor.year, m, min(rule.dayOfMonth.coerceIn(1, 31), daysInMonth(floor.year, m)))
            if (compareYmd(candidate, floor) >= 0) {
                candidate
            } else {
                val ny = floor.year + 1
                Ymd(ny, m, min(rule.dayOfMonth.coerceIn(1, 31), daysInMonth(ny, m)))
            }
        }
    }

    private fun nextOccurrenceAfter(rule: RecurringRule, current: Ymd): Ymd = when (rule.frequency) {
        RecurringFrequency.MONTHLY -> {
            val nextMonth = if (current.month == 12) 1 else current.month + 1
            val nextYear = if (current.month == 12) current.year + 1 else current.year
            Ymd(nextYear, nextMonth, min(rule.dayOfMonth.coerceIn(1, 31), daysInMonth(nextYear, nextMonth)))
        }
        RecurringFrequency.WEEKLY -> addDays(current, 7)
        RecurringFrequency.YEARLY -> {
            val ny = current.year + 1
            val m = rule.monthOfYear.coerceIn(1, 12)
            Ymd(ny, m, min(rule.dayOfMonth.coerceIn(1, 31), daysInMonth(ny, m)))
        }
    }

    private fun ymdToNoonMillis(ymd: Ymd): Long {
        val cal = Calendar.getInstance()
        cal.set(ymd.year, ymd.month - 1, ymd.day, 12, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun dateKeyToNoonMillis(dateKey: String): Long {
        val ymd = parseDateKey(dateKey) ?: return System.currentTimeMillis()
        return ymdToNoonMillis(ymd)
    }
}

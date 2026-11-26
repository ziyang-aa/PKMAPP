package com.example.pkmapp.data

import com.example.pkmapp.backup.BackupCodec
import com.example.pkmapp.backup.BackupSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthlyBudgetEvaluatorTest {

    @Test
    fun evaluate_handlesUnsetNormalWarningAndExceeded() {
        val unset = MonthlyBudgetEvaluator.evaluate(0L, 120_00L)
        assertEquals(MonthlyBudgetEvaluator.Level.UNSET, unset.level)
        assertFalse(unset.isSet)

        val normal = MonthlyBudgetEvaluator.evaluate(2000_00L, 1000_00L)
        assertEquals(MonthlyBudgetEvaluator.Level.NORMAL, normal.level)
        assertEquals(50, normal.usagePercent)
        assertEquals(1000_00L, normal.remainingCents)

        val warningBoundary = MonthlyBudgetEvaluator.evaluate(1000_00L, 800_00L)
        assertEquals(MonthlyBudgetEvaluator.Level.WARNING, warningBoundary.level)
        assertEquals(80, warningBoundary.usagePercent)
        assertEquals(200_00L, warningBoundary.remainingCents)

        val exact100 = MonthlyBudgetEvaluator.evaluate(1000_00L, 1000_00L)
        assertEquals(MonthlyBudgetEvaluator.Level.WARNING, exact100.level)
        assertEquals(0L, exact100.remainingCents)

        val exceeded = MonthlyBudgetEvaluator.evaluate(1000_00L, 1250_00L)
        assertEquals(MonthlyBudgetEvaluator.Level.EXCEEDED, exceeded.level)
        assertEquals(125, exceeded.usagePercent)
        assertEquals(-250_00L, exceeded.remainingCents)
        assertEquals(250_00L, exceeded.overBudgetCents)
        assertTrue(exceeded.isSet)
    }

    @Test
    fun codecs_roundTripBudgetAndRemainBackwardCompatible() {
        val ledgerWithBudget = Ledger("l-1", "生活账本", 2500_00L)
        val encoded = LedgerCodec.encode(listOf(ledgerWithBudget), emptyList(), "l-1")
        val decoded = LedgerCodec.decode(encoded)
        assertEquals(2500_00L, decoded!!.ledgers[0].monthlyBudgetCents)

        // Legacy backup without monthlyBudgetCents still decodes cleanly
        val legacyBackup = """
            {
              "format": "pkmapp-backup",
              "version": 1,
              "exportedAtMillis": 1700000000000,
              "currentLedgerId": "l-1",
              "ledgers": [{"id": "l-1", "name": "旧账本"}],
              "transactions": [],
              "savingsGoals": [],
              "assets": [{"ledgerId": "l-1", "initialAssetsInCents": 0, "liabilitiesInCents": 0}],
              "borrowingRecords": []
            }
        """.trimIndent()
        val legacySnap = BackupCodec.decode(legacyBackup)
        assertEquals(0L, legacySnap.ledgers[0].monthlyBudgetCents)

        val modernSnap = BackupSnapshot(
            1700000000000L,
            listOf(ledgerWithBudget),
            emptyList(),
            "l-1",
            emptyList(),
            listOf(BackupSnapshot.AssetValues("l-1", 0L, 0L)),
            emptyList()
        )
        val reDecoded = BackupCodec.decode(BackupCodec.encode(modernSnap))
        assertEquals(2500_00L, reDecoded.ledgers[0].monthlyBudgetCents)
    }
}

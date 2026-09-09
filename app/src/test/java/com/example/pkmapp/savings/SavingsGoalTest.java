package com.example.pkmapp.savings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SavingsGoalTest {
    @Test
    public void depositKeepsAmountAboveTargetAndMarksGoalComplete() {
        SavingsGoal goal = new SavingsGoal("goal-1", "旅行基金", 10_000L, 0L, 1L);

        SavingsGoal updated = goal.deposit(15_000L);

        assertEquals(15_000L, updated.getSavedCents());
        assertEquals(0L, updated.getRemainingCents());
        assertEquals(100, updated.getProgressPercent());
        assertTrue(updated.isComplete());
    }

    @Test
    public void updateDetailsKeepsAlreadySavedAmount() {
        SavingsGoal goal = new SavingsGoal("goal-1", "旅行基金", 10_000L, 3_500L, 1L);

        SavingsGoal updated = goal.updateDetails("应急备用金", 20_000L);

        assertEquals("应急备用金", updated.getName());
        assertEquals(20_000L, updated.getTargetCents());
        assertEquals(3_500L, updated.getSavedCents());
    }
}

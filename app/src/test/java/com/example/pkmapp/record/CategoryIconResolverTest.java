package com.example.pkmapp.record;

import static org.junit.Assert.assertEquals;

import com.example.pkmapp.data.TransactionType;
import org.junit.Test;

public final class CategoryIconResolverTest {

    @Test
    public void mapsSavingsGoalDepositsToTheSeedlingIcon() {
        assertEquals("savings_seedling_stage_1", CategoryIconResolver.resourceName(
                TransactionType.INCOME, "攒钱 · 旅行基金"));
    }
    @Test
    public void mapsExpenseAndIncomeLabelsToNormalizedAssets() {
        assertEquals("norm_type_expense_dining",
                CategoryIconResolver.resourceName(TransactionType.EXPENSE, "餐饮"));
        assertEquals("norm_type_income_salary",
                CategoryIconResolver.resourceName(TransactionType.INCOME, "工资"));
        assertEquals("norm_type_expense_loan",
                CategoryIconResolver.resourceName(TransactionType.INCOME, "借入"));
        assertEquals("norm_type_expense_shopping",
                CategoryIconResolver.resourceName(TransactionType.EXPENSE, "网购"));
    }
}

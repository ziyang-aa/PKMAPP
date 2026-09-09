package com.example.pkmapp.record;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.pkmapp.borrowing.BorrowingDirection;
import com.example.pkmapp.data.TransactionType;

import org.junit.Test;

public final class RecordBorrowingLinkTest {
    @Test
    public void mapsExpenseLendAndIncomeBorrowCategories() {
        assertEquals(BorrowingDirection.LEND,
                BorrowingCategoryLink.directionFor(TransactionType.EXPENSE, "借出"));
        assertEquals(BorrowingDirection.BORROW,
                BorrowingCategoryLink.directionFor(TransactionType.INCOME, "借入"));
    }

    @Test
    public void regularCategoriesDoNotOpenBorrowingComposer() {
        assertNull(BorrowingCategoryLink.directionFor(TransactionType.EXPENSE, "餐饮"));
        assertNull(BorrowingCategoryLink.directionFor(TransactionType.INCOME, "工资"));
    }
}

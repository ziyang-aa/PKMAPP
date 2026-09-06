package com.example.pkmapp.details;

import static org.junit.Assert.assertEquals;

import com.example.pkmapp.data.Transaction;
import com.example.pkmapp.data.TransactionType;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

public final class DetailsListItemTest {
    @Test
    public void fromTransactions_groupsTransactionsByRelativeDate() {
        long now = noonUtc(2026, Calendar.SEPTEMBER, 6);
        List<Transaction> transactions = Arrays.asList(
                transaction("today", 6), transaction("yesterday", 5), transaction("older", 4));

        List<DetailsListItem> items = DetailsListItem.fromTransactions(transactions, now);

        assertEquals(DetailsListItem.Kind.DATE_HEADER, items.get(0).getKind());
        assertEquals("今天", items.get(0).getHeaderLabel());
        assertEquals(DetailsListItem.Kind.TRANSACTION, items.get(1).getKind());
        assertEquals("昨天", items.get(2).getHeaderLabel());
        assertEquals("9月4日", items.get(4).getHeaderLabel());
    }

    private static Transaction transaction(String id, int day) {
        return new Transaction(id, "ledger", TransactionType.EXPENSE, 100L, "餐饮", "",
                noonUtc(2026, Calendar.SEPTEMBER, day));
    }

    private static long noonUtc(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.clear();
        calendar.set(year, month, day, 12, 0, 0);
        return calendar.getTimeInMillis();
    }
}

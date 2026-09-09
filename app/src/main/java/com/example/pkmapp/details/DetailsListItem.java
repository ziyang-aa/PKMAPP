package com.example.pkmapp.details;

import com.example.pkmapp.data.Transaction;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class DetailsListItem {
    public enum Kind {
        DATE_HEADER,
        TRANSACTION
    }

    private final Kind kind;
    private final String headerLabel;
    private final Transaction transaction;

    private DetailsListItem(Kind kind, String headerLabel, Transaction transaction) {
        this.kind = kind;
        this.headerLabel = headerLabel;
        this.transaction = transaction;
    }

    public static List<DetailsListItem> fromTransactions(List<Transaction> transactions,
            long nowMillis) {
        if (transactions == null) {
            throw new IllegalArgumentException("记录列表不能为空");
        }
        if (nowMillis <= 0L) {
            throw new IllegalArgumentException("当前日期不能为空");
        }
        List<DetailsListItem> items = new ArrayList<>();
        String previousHeader = null;
        for (Transaction transaction : transactions) {
            String header = headerFor(transaction.getOccurredAtMillis(), nowMillis);
            if (!header.equals(previousHeader)) {
                items.add(new DetailsListItem(Kind.DATE_HEADER, header, null));
                previousHeader = header;
            }
            items.add(new DetailsListItem(Kind.TRANSACTION, null, transaction));
        }
        return Collections.unmodifiableList(items);
    }

    public Kind getKind() {
        return kind;
    }

    public String getHeaderLabel() {
        return headerLabel;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    private static String headerFor(long occurredAtMillis, long nowMillis) {
        Calendar today = Calendar.getInstance();
        today.setTimeInMillis(nowMillis);
        Calendar occurred = Calendar.getInstance();
        occurred.setTimeInMillis(occurredAtMillis);
        String dateLabel = new SimpleDateFormat("M月d日", Locale.CHINA).format(occurredAtMillis);
        if (isSameDay(occurred, today)) {
            return dateLabel + " · 今天";
        }
        today.add(Calendar.DAY_OF_YEAR, -1);
        if (isSameDay(occurred, today)) {
            return dateLabel + " · 昨天";
        }
        return dateLabel;
    }

    private static boolean isSameDay(Calendar first, Calendar second) {
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR)
                && first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR);
    }
}

package com.example.pkmapp.borrowing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public final class BorrowingRecordStoreTest {
    @Test
    public void jsonRoundTripKeepsRequiredAndOptionalFields() {
        BorrowingRecord original = new BorrowingRecord(
                "record-1", "life", BorrowingDirection.LEND, 12_340L,
                1_725_000_000_000L, "小林", "周末先垫付", "transaction-9");

        List<BorrowingRecord> decoded = BorrowingRecordCodec.decode(
                BorrowingRecordCodec.encode(Arrays.asList(original)));

        assertEquals(1, decoded.size());
        BorrowingRecord restored = decoded.get(0);
        assertEquals(original.getId(), restored.getId());
        assertEquals(original.getLedgerId(), restored.getLedgerId());
        assertEquals(original.getDirection(), restored.getDirection());
        assertEquals(original.getAmountInCents(), restored.getAmountInCents());
        assertEquals(original.getOccurredAtMillis(), restored.getOccurredAtMillis());
        assertEquals(original.getPerson(), restored.getPerson());
        assertEquals(original.getNote(), restored.getNote());
        assertEquals(original.getSourceTransactionId(), restored.getSourceTransactionId());
    }

    @Test
    public void malformedEntriesAreSkippedAndMissingOptionalValuesStayEmpty() {
        String json = "[{\"id\":\"ok\",\"ledgerId\":\"life\","
                + "\"direction\":\"BORROW\",\"amountInCents\":500,"
                + "\"occurredAtMillis\":1725000000000,\"person\":\"阿木\"},"
                + "{\"id\":\"bad\",\"direction\":\"UNKNOWN\"}]";

        List<BorrowingRecord> decoded = BorrowingRecordCodec.decode(json);

        assertEquals(1, decoded.size());
        assertEquals(BorrowingDirection.BORROW, decoded.get(0).getDirection());
        assertEquals("", decoded.get(0).getNote());
        assertNull(decoded.get(0).getSourceTransactionId());
    }
}

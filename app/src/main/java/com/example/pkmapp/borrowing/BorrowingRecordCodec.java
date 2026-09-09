package com.example.pkmapp.borrowing;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Encodes borrowing records without coupling the domain model to Android storage. */
public final class BorrowingRecordCodec {
    private BorrowingRecordCodec() {
    }

    public static String encode(List<BorrowingRecord> records) {
        JSONArray array = new JSONArray();
        if (records == null) {
            return array.toString();
        }
        for (BorrowingRecord record : records) {
            if (record == null) {
                continue;
            }
            JSONObject item = new JSONObject();
            try {
                item.put("id", record.getId());
                item.put("ledgerId", record.getLedgerId());
                item.put("direction", record.getDirection().name());
                item.put("amountInCents", record.getAmountInCents());
                item.put("occurredAtMillis", record.getOccurredAtMillis());
                item.put("person", record.getPerson());
                item.put("note", record.getNote());
                if (record.getSourceTransactionId() != null) {
                    item.put("sourceTransactionId", record.getSourceTransactionId());
                }
                array.put(item);
            } catch (JSONException exception) {
                throw new IllegalStateException("保存借钱记录失败", exception);
            }
        }
        return array.toString();
    }

    public static List<BorrowingRecord> decode(String encoded) {
        if (encoded == null || encoded.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            JSONArray array = new JSONArray(encoded);
            List<BorrowingRecord> records = new ArrayList<>();
            for (int index = 0; index < array.length(); index++) {
                try {
                    records.add(decodeItem(array.getJSONObject(index)));
                } catch (JSONException | IllegalArgumentException exception) {
                    // A damaged item should not make all locally saved records disappear.
                }
            }
            return Collections.unmodifiableList(records);
        } catch (JSONException exception) {
            return Collections.emptyList();
        }
    }

    private static BorrowingRecord decodeItem(JSONObject item) throws JSONException {
        String source = item.has("sourceTransactionId")
                ? item.optString("sourceTransactionId", null) : null;
        return new BorrowingRecord(
                item.getString("id"),
                item.getString("ledgerId"),
                BorrowingDirection.valueOf(item.getString("direction")),
                item.getLong("amountInCents"),
                item.getLong("occurredAtMillis"),
                item.getString("person"),
                item.optString("note", ""),
                source);
    }
}

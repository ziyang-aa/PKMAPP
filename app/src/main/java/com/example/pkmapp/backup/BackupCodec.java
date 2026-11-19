package com.example.pkmapp.backup;

import com.example.pkmapp.borrowing.BorrowingDirection;
import com.example.pkmapp.borrowing.BorrowingRecord;
import com.example.pkmapp.data.Ledger;
import com.example.pkmapp.data.Transaction;
import com.example.pkmapp.data.TransactionType;
import com.example.pkmapp.savings.SavingsGoal;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Strict JSON codec for user-created .pkmbackup.json files. */
public final class BackupCodec {
    public static final String FORMAT = "pkmapp-backup";
    public static final int CURRENT_VERSION = 1;

    private BackupCodec() {
    }

    public static String encode(BackupSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("备份数据不能为空");
        }
        try {
            JSONObject root = new JSONObject();
            root.put("format", FORMAT);
            root.put("version", CURRENT_VERSION);
            root.put("exportedAtMillis", snapshot.getExportedAtMillis());
            root.put("currentLedgerId", snapshot.getCurrentLedgerId());

            JSONArray ledgers = new JSONArray();
            for (Ledger ledger : snapshot.getLedgers()) {
                JSONObject item = new JSONObject();
                item.put("id", ledger.getId());
                item.put("name", ledger.getName());
                ledgers.put(item);
            }
            root.put("ledgers", ledgers);

            JSONArray transactions = new JSONArray();
            for (Transaction transaction : snapshot.getTransactions()) {
                JSONObject item = new JSONObject();
                item.put("id", transaction.getId());
                item.put("ledgerId", transaction.getLedgerId());
                item.put("type", transaction.getType().name());
                item.put("amountInCents", transaction.getAmountInCents());
                item.put("category", transaction.getCategory());
                item.put("note", transaction.getNote());
                item.put("occurredAtMillis", transaction.getOccurredAtMillis());
                transactions.put(item);
            }
            root.put("transactions", transactions);

            JSONArray goals = new JSONArray();
            for (SavingsGoal goal : snapshot.getSavingsGoals()) {
                JSONObject item = new JSONObject();
                item.put("id", goal.getId());
                item.put("name", goal.getName());
                item.put("targetCents", goal.getTargetCents());
                item.put("savedCents", goal.getSavedCents());
                item.put("createdAtMillis", goal.getCreatedAtMillis());
                goals.put(item);
            }
            root.put("savingsGoals", goals);

            JSONArray assets = new JSONArray();
            for (BackupSnapshot.AssetValues values : snapshot.getAssetValues()) {
                JSONObject item = new JSONObject();
                item.put("ledgerId", values.getLedgerId());
                item.put("initialAssetsInCents", values.getInitialAssetsInCents());
                item.put("liabilitiesInCents", values.getLiabilitiesInCents());
                assets.put(item);
            }
            root.put("assets", assets);

            JSONArray borrowingRecords = new JSONArray();
            for (BorrowingRecord record : snapshot.getBorrowingRecords()) {
                JSONObject item = new JSONObject();
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
                borrowingRecords.put(item);
            }
            root.put("borrowingRecords", borrowingRecords);
            return root.toString(2);
        } catch (JSONException exception) {
            throw new IllegalStateException("生成备份文件失败", exception);
        }
    }

    public static BackupSnapshot decode(String encoded) {
        if (encoded == null || encoded.trim().isEmpty()) {
            throw new IllegalArgumentException("备份文件为空");
        }
        try {
            JSONObject root = new JSONObject(encoded);
            if (!FORMAT.equals(requiredString(root, "format"))) {
                throw new IllegalArgumentException("不是芽叶记备份文件");
            }
            if (requiredLong(root, "version") != CURRENT_VERSION) {
                throw new IllegalArgumentException("备份文件版本不受支持");
            }

            List<Ledger> ledgers = new ArrayList<>();
            JSONArray ledgerArray = requiredArray(root, "ledgers");
            for (int index = 0; index < ledgerArray.length(); index++) {
                JSONObject item = ledgerArray.getJSONObject(index);
                ledgers.add(new Ledger(requiredString(item, "id"), requiredString(item, "name")));
            }

            List<Transaction> transactions = new ArrayList<>();
            JSONArray transactionArray = requiredArray(root, "transactions");
            for (int index = 0; index < transactionArray.length(); index++) {
                JSONObject item = transactionArray.getJSONObject(index);
                transactions.add(new Transaction(
                        requiredString(item, "id"),
                        requiredString(item, "ledgerId"),
                        TransactionType.valueOf(requiredString(item, "type")),
                        requiredLong(item, "amountInCents"),
                        requiredString(item, "category"),
                        item.optString("note", ""),
                        requiredLong(item, "occurredAtMillis")));
            }

            List<SavingsGoal> goals = new ArrayList<>();
            JSONArray goalArray = requiredArray(root, "savingsGoals");
            for (int index = 0; index < goalArray.length(); index++) {
                JSONObject item = goalArray.getJSONObject(index);
                goals.add(new SavingsGoal(
                        requiredString(item, "id"),
                        requiredString(item, "name"),
                        requiredLong(item, "targetCents"),
                        requiredLong(item, "savedCents"),
                        requiredLong(item, "createdAtMillis")));
            }

            List<BackupSnapshot.AssetValues> assets = new ArrayList<>();
            JSONArray assetArray = requiredArray(root, "assets");
            for (int index = 0; index < assetArray.length(); index++) {
                JSONObject item = assetArray.getJSONObject(index);
                assets.add(new BackupSnapshot.AssetValues(
                        requiredString(item, "ledgerId"),
                        requiredLong(item, "initialAssetsInCents"),
                        requiredLong(item, "liabilitiesInCents")));
            }

            List<BorrowingRecord> borrowingRecords = new ArrayList<>();
            JSONArray borrowingArray = requiredArray(root, "borrowingRecords");
            for (int index = 0; index < borrowingArray.length(); index++) {
                JSONObject item = borrowingArray.getJSONObject(index);
                String source = item.has("sourceTransactionId")
                        ? item.optString("sourceTransactionId", null) : null;
                borrowingRecords.add(new BorrowingRecord(
                        requiredString(item, "id"),
                        requiredString(item, "ledgerId"),
                        BorrowingDirection.valueOf(requiredString(item, "direction")),
                        requiredLong(item, "amountInCents"),
                        requiredLong(item, "occurredAtMillis"),
                        requiredString(item, "person"),
                        item.optString("note", ""),
                        source));
            }

            return new BackupSnapshot(
                    requiredLong(root, "exportedAtMillis"),
                    ledgers,
                    transactions,
                    requiredString(root, "currentLedgerId"),
                    goals,
                    assets,
                    borrowingRecords);
        } catch (JSONException exception) {
            throw new IllegalArgumentException("备份文件结构无效", exception);
        }
    }

    private static JSONArray requiredArray(JSONObject object, String key) throws JSONException {
        return object.getJSONArray(key);
    }

    private static String requiredString(JSONObject object, String key) throws JSONException {
        String value = object.getString(key);
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException("备份字段不能为空：" + key);
        }
        return value;
    }

    private static long requiredLong(JSONObject object, String key) throws JSONException {
        Object value = object.get(key);
        if (!(value instanceof Integer) && !(value instanceof Long)) {
            throw new IllegalArgumentException("备份字段必须是整数：" + key);
        }
        return ((Number) value).longValue();
    }
}

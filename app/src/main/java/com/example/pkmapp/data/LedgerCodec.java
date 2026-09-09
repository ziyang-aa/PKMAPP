package com.example.pkmapp.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Encodes the local ledger state without coupling the domain model to Android storage. */
public final class LedgerCodec {
    private LedgerCodec() {
    }

    public static String encode(List<Ledger> ledgers, List<Transaction> transactions,
            String currentLedgerId) {
        JSONObject root = new JSONObject();
        JSONArray ledgerArray = new JSONArray();
        JSONArray transactionArray = new JSONArray();
        try {
            root.put("currentLedgerId", currentLedgerId);
            if (ledgers != null) {
                for (Ledger ledger : ledgers) {
                    if (ledger == null) {
                        continue;
                    }
                    JSONObject item = new JSONObject();
                    item.put("id", ledger.getId());
                    item.put("name", ledger.getName());
                    ledgerArray.put(item);
                }
            }
            if (transactions != null) {
                for (Transaction transaction : transactions) {
                    if (transaction == null) {
                        continue;
                    }
                    JSONObject item = new JSONObject();
                    item.put("id", transaction.getId());
                    item.put("ledgerId", transaction.getLedgerId());
                    item.put("type", transaction.getType().name());
                    item.put("amountInCents", transaction.getAmountInCents());
                    item.put("category", transaction.getCategory());
                    item.put("note", transaction.getNote());
                    item.put("occurredAtMillis", transaction.getOccurredAtMillis());
                    transactionArray.put(item);
                }
            }
            root.put("ledgers", ledgerArray);
            root.put("transactions", transactionArray);
            return root.toString();
        } catch (JSONException exception) {
            throw new IllegalStateException("保存账本数据失败", exception);
        }
    }

    public static State decode(String encoded) {
        if (encoded == null || encoded.trim().isEmpty()) {
            return null;
        }
        try {
            JSONObject root = new JSONObject(encoded);
            List<Ledger> ledgers = new ArrayList<>();
            JSONArray ledgerArray = root.optJSONArray("ledgers");
            if (ledgerArray != null) {
                for (int index = 0; index < ledgerArray.length(); index++) {
                    try {
                        JSONObject item = ledgerArray.getJSONObject(index);
                        ledgers.add(new Ledger(item.getString("id"), item.getString("name")));
                    } catch (JSONException | IllegalArgumentException exception) {
                        // A damaged item should not make all locally saved ledgers disappear.
                    }
                }
            }

            List<Transaction> transactions = new ArrayList<>();
            JSONArray transactionArray = root.optJSONArray("transactions");
            if (transactionArray != null) {
                for (int index = 0; index < transactionArray.length(); index++) {
                    try {
                        JSONObject item = transactionArray.getJSONObject(index);
                        transactions.add(new Transaction(
                                item.getString("id"),
                                item.getString("ledgerId"),
                                TransactionType.valueOf(item.getString("type")),
                                item.getLong("amountInCents"),
                                item.getString("category"),
                                item.optString("note", ""),
                                item.getLong("occurredAtMillis")));
                    } catch (JSONException | IllegalArgumentException exception) {
                        // A damaged item should not make all locally saved transactions disappear.
                    }
                }
            }
            return new State(ledgers, transactions, root.optString("currentLedgerId", ""));
        } catch (JSONException exception) {
            return null;
        }
    }

    public static final class State {
        private final List<Ledger> ledgers;
        private final List<Transaction> transactions;
        private final String currentLedgerId;

        private State(List<Ledger> ledgers, List<Transaction> transactions, String currentLedgerId) {
            this.ledgers = Collections.unmodifiableList(new ArrayList<>(ledgers));
            this.transactions = Collections.unmodifiableList(new ArrayList<>(transactions));
            this.currentLedgerId = currentLedgerId;
        }

        public List<Ledger> getLedgers() {
            return ledgers;
        }

        public List<Transaction> getTransactions() {
            return transactions;
        }

        public String getCurrentLedgerId() {
            return currentLedgerId;
        }
    }
}

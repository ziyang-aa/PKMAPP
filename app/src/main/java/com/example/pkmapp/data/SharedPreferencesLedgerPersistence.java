package com.example.pkmapp.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;

/** Stores all ledgers and transactions in the app's private local preferences. */
final class SharedPreferencesLedgerPersistence implements LedgerPersistence {
    private static final String PREFERENCES = "ledger_data";
    private static final String STATE_KEY = "state";

    private final SharedPreferences preferences;

    SharedPreferencesLedgerPersistence(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(
                PREFERENCES, Context.MODE_PRIVATE);
    }

    @Override
    public synchronized LedgerCodec.State load() {
        String encoded = preferences.getString(STATE_KEY, null);
        return LedgerCodec.decode(encoded);
    }

    @Override
    public synchronized void save(List<Ledger> ledgers, List<Transaction> transactions,
            String currentLedgerId) {
        preferences.edit().putString(STATE_KEY,
                LedgerCodec.encode(ledgers, transactions, currentLedgerId)).apply();
    }
}

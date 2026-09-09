package com.example.pkmapp.data;

import java.util.List;

interface LedgerPersistence {
    LedgerCodec.State load();

    void save(List<Ledger> ledgers, List<Transaction> transactions, String currentLedgerId);
}

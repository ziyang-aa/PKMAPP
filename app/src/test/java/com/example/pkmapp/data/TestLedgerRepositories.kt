package com.example.pkmapp.data

internal fun newTestLedgerRepository(): InMemoryLedgerRepository =
    InMemoryLedgerRepository.createForTest()

package com.example.pkmapp.data

import android.content.Context
import com.example.pkmapp.core.database.AccountScope
import com.example.pkmapp.core.database.AppDatabase
import com.example.pkmapp.core.database.TransactionEntity

internal class RoomLedgerPersistence(
    private val database: AppDatabase,
    private val ownerIdProvider: () -> String
) : LedgerPersistence {

    constructor(context: Context) : this(
        AppDatabase.getInstance(context.applicationContext),
        { AccountScope.currentOwnerId(context.applicationContext) }
    )

    @Synchronized
    override fun load(): LedgerCodec.State? {
        val ownerId = ownerIdProvider()
        val dao = database.ledgerDao()
        val ledgerRows = dao.getOrderedLedgers(ownerId)
        if (ledgerRows.isEmpty()) {
            return null
        }
        val ledgers = ledgerRows.map { Ledger(it.id, it.name) }
        val currentId = ledgerRows.firstOrNull { it.isCurrent }?.id ?: ledgerRows.first().id
        val transactions = dao.getAllTransactions(ownerId).mapNotNull { row ->
            try {
                Transaction(
                    row.id,
                    row.ledgerId,
                    TransactionType.valueOf(row.type),
                    row.amountCents,
                    row.category,
                    row.note,
                    row.occurredAt
                )
            } catch (_: IllegalArgumentException) {
                null
            }
        }
        return LedgerCodec.decode(LedgerCodec.encode(ledgers, transactions, currentId))
    }

    @Synchronized
    override fun save(
        ledgers: List<Ledger>,
        transactions: List<Transaction>,
        currentLedgerId: String
    ) {
        val ownerId = ownerIdProvider()
        val ledgerPairs = ledgers.map { it.id to it.name }
        val txEntities = transactions.mapIndexed { index, tx ->
            TransactionEntity(
                id = tx.id,
                ledgerId = tx.ledgerId,
                type = tx.type.name,
                category = tx.category,
                amountCents = tx.amountInCents,
                occurredAt = tx.occurredAtMillis,
                note = tx.note,
                createdAt = tx.occurredAtMillis,
                sortOrder = index,
                ownerId = ownerId
            )
        }
        database.ledgerDao().saveLedgerAndTransactionState(
            ledgerPairs = ledgerPairs,
            transactions = txEntities,
            currentLedgerId = currentLedgerId,
            ownerId = ownerId
        )
    }
}

package com.example.koistock.data.remote

import com.example.koistock.data.model.Transaction
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

interface TransactionRepo {
    suspend fun append(transaction: Transaction)
}

class TransactionRepository(
    private val db: FirebaseFirestore,
) : TransactionRepo {
    private val collection get() = db.collection("transactions")

    override suspend fun append(transaction: Transaction) {
        collection.document(transaction.id).set(transaction.toMap()).await()
    }
}

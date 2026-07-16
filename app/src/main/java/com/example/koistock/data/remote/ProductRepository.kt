package com.example.koistock.data.remote

import com.example.koistock.data.model.Product
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

interface ProductRepo {
    suspend fun getBySku(sku: String): Product?
    fun observeAll(): Flow<List<Product>>
    suspend fun upsert(product: Product)
}

class ProductRepository(
    private val db: FirebaseFirestore,
) : ProductRepo {
    private val collection get() = db.collection("products")

    override suspend fun getBySku(sku: String): Product? {
        val snapshot = collection.document(sku).get().await()
        val data = snapshot.data ?: return null
        return Product.fromMap(snapshot.id, data)
    }

    override fun observeAll(): Flow<List<Product>> = callbackFlow {
        val registration = collection.addSnapshotListener { snapshot, _ ->
            val products = snapshot?.documents.orEmpty()
                .mapNotNull { doc -> doc.data?.let { Product.fromMap(doc.id, it) } }
            trySend(products)
        }
        awaitClose { registration.remove() }
    }

    override suspend fun upsert(product: Product) {
        collection.document(product.sku).set(product.toMap()).await()
    }
}

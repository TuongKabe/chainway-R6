package com.example.koistock.data.remote

import com.example.koistock.data.model.FirestoreValueCodec
import com.example.koistock.data.model.TagMapping
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

interface TagRepo {
    suspend fun getByEpc(epc: String): TagMapping?
    suspend fun upsert(tag: TagMapping)
    suspend fun listBySku(sku: String): List<TagMapping>
    suspend fun voidTag(epc: String)
}

class TagRepository(
    private val db: FirebaseFirestore,
) : TagRepo {
    private val collection get() = db.collection("tags")

    override suspend fun getByEpc(epc: String): TagMapping? {
        val snapshot = collection.document(epc).get().await()
        val data = snapshot.data ?: return null
        return TagMapping.fromMap(snapshot.id, data)
    }

    override suspend fun upsert(tag: TagMapping) {
        collection.document(tag.epc).set(tag.toMap()).await()
    }

    override suspend fun listBySku(sku: String): List<TagMapping> {
        return collection
            .whereEqualTo("sku", sku)
            .get()
            .await()
            .documents
            .mapNotNull { doc -> doc.data?.let { TagMapping.fromMap(doc.id, it) } }
    }

    override suspend fun voidTag(epc: String) {
        collection.document(epc).update(
            mapOf(
                "status" to "void",
                "locationCode" to null,
                "updatedAt" to FirestoreValueCodec.serverTimestamp(),
            ),
        ).await()
    }
}

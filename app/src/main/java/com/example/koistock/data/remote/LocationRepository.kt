package com.example.koistock.data.remote

import com.example.koistock.data.model.LocationNode
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

interface LocationRepo {
    fun observeAll(): Flow<List<LocationNode>>
    suspend fun upsert(location: LocationNode)
}

class LocationRepository(
    private val db: FirebaseFirestore,
) : LocationRepo {
    private val collection get() = db.collection("locations")

    override fun observeAll(): Flow<List<LocationNode>> = callbackFlow {
        val registration = collection.addSnapshotListener { snapshot, _ ->
            val locations = snapshot?.documents.orEmpty()
                .mapNotNull { doc -> doc.data?.let { LocationNode.fromMap(doc.id, it) } }
            trySend(locations)
        }
        awaitClose { registration.remove() }
    }

    override suspend fun upsert(location: LocationNode) {
        collection.document(location.code).set(location.toMap()).await()
    }
}

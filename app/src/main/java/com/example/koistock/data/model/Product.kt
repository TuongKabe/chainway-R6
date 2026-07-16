package com.example.koistock.data.model

enum class TrackingMode { SERIALIZED, BULK }

data class Product(
    val sku: String,
    val name: String,
    val unit: String,
    val trackingMode: TrackingMode,
    val quantity: Long = 0,
    val locationCode: String = "",
    val imageUrl: String? = null,
    val updatedAt: Long = 0,
    val origin: String = "app",
    val syncRev: Long = 0,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "unit" to unit,
        "trackingMode" to trackingMode.name,
        "quantity" to quantity,
        "locationCode" to locationCode,
        "imageUrl" to imageUrl,
        "updatedAt" to FirestoreValueCodec.serverTimestamp(),
        "origin" to origin,
        "syncRev" to syncRev,
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): Product {
            return Product(
                sku = id,
                name = map["name"] as? String ?: "",
                unit = map["unit"] as? String ?: "",
                trackingMode = TrackingMode.valueOf(map["trackingMode"] as? String ?: TrackingMode.BULK.name),
                quantity = (map["quantity"] as? Number)?.toLong() ?: 0L,
                locationCode = map["locationCode"] as? String ?: "",
                imageUrl = map["imageUrl"] as? String,
                updatedAt = FirestoreValueCodec.readEpochMillis(map["updatedAt"]),
                origin = map["origin"] as? String ?: "app",
                syncRev = (map["syncRev"] as? Number)?.toLong() ?: 0L,
            )
        }
    }
}

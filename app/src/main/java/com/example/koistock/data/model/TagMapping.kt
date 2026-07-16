package com.example.koistock.data.model

data class TagMapping(
    val epc: String,
    val sku: String,
    val unitSerial: String? = null,
    val status: String = "active",
    val locationCode: String? = null,
    val updatedAt: Long = 0,
    val origin: String = "app",
    val syncRev: Long = 0,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "sku" to sku,
        "unitSerial" to unitSerial,
        "status" to status,
        "locationCode" to locationCode,
        "updatedAt" to FirestoreValueCodec.serverTimestamp(),
        "origin" to origin,
        "syncRev" to syncRev,
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): TagMapping {
            return TagMapping(
                epc = id,
                sku = map["sku"] as? String ?: "",
                unitSerial = map["unitSerial"] as? String,
                status = map["status"] as? String ?: "active",
                locationCode = map["locationCode"] as? String,
                updatedAt = FirestoreValueCodec.readEpochMillis(map["updatedAt"]),
                origin = map["origin"] as? String ?: "app",
                syncRev = (map["syncRev"] as? Number)?.toLong() ?: 0L,
            )
        }
    }
}

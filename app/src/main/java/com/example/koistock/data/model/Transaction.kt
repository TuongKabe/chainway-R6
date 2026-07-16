package com.example.koistock.data.model

enum class TxType { IN, OUT, COUNT, ADJUST, MOVE }

data class Transaction(
    val id: String,
    val type: TxType,
    val sku: String,
    val epc: String? = null,
    val delta: Long = 0,
    val locationCode: String? = null,
    val deviceId: String,
    val at: Long = 0,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "type" to type.name,
        "sku" to sku,
        "epc" to epc,
        "delta" to delta,
        "locationCode" to locationCode,
        "deviceId" to deviceId,
        "updatedAt" to FirestoreValueCodec.serverTimestamp(),
        "at" to at,
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): Transaction {
            return Transaction(
                id = id,
                type = TxType.valueOf(map["type"] as? String ?: TxType.COUNT.name),
                sku = map["sku"] as? String ?: "",
                epc = map["epc"] as? String,
                delta = (map["delta"] as? Number)?.toLong() ?: 0L,
                locationCode = map["locationCode"] as? String,
                deviceId = map["deviceId"] as? String ?: "",
                at = (map["at"] as? Number)?.toLong()
                    ?: FirestoreValueCodec.readEpochMillis(map["updatedAt"]),
            )
        }
    }
}

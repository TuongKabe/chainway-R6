package com.example.koistock.data.model

enum class LocationType { ZONE, SHELF }

data class LocationNode(
    val code: String,
    val name: String,
    val type: LocationType,
    val parent: String? = null,
    val updatedAt: Long = 0,
    val origin: String = "app",
    val syncRev: Long = 0,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "type" to type.name,
        "parent" to parent,
        "updatedAt" to FirestoreValueCodec.serverTimestamp(),
        "origin" to origin,
        "syncRev" to syncRev,
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): LocationNode {
            return LocationNode(
                code = id,
                name = map["name"] as? String ?: "",
                type = LocationType.valueOf(map["type"] as? String ?: LocationType.SHELF.name),
                parent = map["parent"] as? String,
                updatedAt = FirestoreValueCodec.readEpochMillis(map["updatedAt"]),
                origin = map["origin"] as? String ?: "app",
                syncRev = (map["syncRev"] as? Number)?.toLong() ?: 0L,
            )
        }
    }
}

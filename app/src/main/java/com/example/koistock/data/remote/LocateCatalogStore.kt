package com.example.koistock.data.remote

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import kotlinx.coroutines.flow.first

data class LocateCatalogSnapshot(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val items: List<LocatableProduct> = emptyList(),
    val tagSignatureBySku: Map<String, String> = emptyMap(),
    val metadataVerifiedAt: Long = 0L,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

interface LocateCatalogCache {
    suspend fun read(): LocateCatalogSnapshot?
    suspend fun write(snapshot: LocateCatalogSnapshot)
}

class LocateCatalogStore(
    private val dataStore: DataStore<Preferences>,
    private val gson: Gson = Gson(),
) : LocateCatalogCache {
    override suspend fun read(): LocateCatalogSnapshot? {
        val raw = dataStore.data.first()[snapshotKey] ?: return null
        return runCatching { gson.fromJson(raw, LocateCatalogSnapshot::class.java) }
            .getOrNull()
            ?.takeIf { it.schemaVersion == LocateCatalogSnapshot.CURRENT_SCHEMA_VERSION }
    }

    override suspend fun write(snapshot: LocateCatalogSnapshot) {
        dataStore.edit { it[snapshotKey] = gson.toJson(snapshot) }
    }

    private companion object {
        val snapshotKey = stringPreferencesKey("locate_catalog_snapshot_v1")
    }
}

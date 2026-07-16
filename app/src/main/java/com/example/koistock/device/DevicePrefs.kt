package com.example.koistock.device

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

class DevicePrefs(
    private val dataStore: DataStore<Preferences>,
) {
    val lastMac: Flow<String?> = dataStore.data.map { it[LAST_MAC] }

    suspend fun saveMac(mac: String) {
        dataStore.edit { prefs -> prefs[LAST_MAC] = mac }
    }

    suspend fun deviceId(): String {
        val current = dataStore.data.first()[DEVICE_ID]
        if (current != null) {
            return current
        }

        val generated = UUID.randomUUID().toString()
        dataStore.edit { prefs -> prefs[DEVICE_ID] = generated }
        return generated
    }

    private companion object {
        val LAST_MAC = stringPreferencesKey("last_mac")
        val DEVICE_ID = stringPreferencesKey("device_id")
    }
}

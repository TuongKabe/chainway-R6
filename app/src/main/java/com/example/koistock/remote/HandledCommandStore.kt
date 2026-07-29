package com.example.koistock.remote

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.handledStore by preferencesDataStore(name = "handled_commands")

class HandledCommandStore(private val context: Context) {
    companion object {
        private val HANDLED_IDS_KEY = stringPreferencesKey("handled_ids")
        private const val MAX_IDS = 100
    }

    suspend fun isHandled(commandId: String): Boolean {
        val ids = getHandledIds()
        return ids.contains(commandId)
    }

    suspend fun markHandled(commandId: String) {
        context.handledStore.edit { prefs ->
            val current = (prefs[HANDLED_IDS_KEY] ?: "").split(",").filter { it.isNotEmpty() }
            val updated = (current + commandId).takeLast(MAX_IDS)
            prefs[HANDLED_IDS_KEY] = updated.joinToString(",")
        }
    }

    private suspend fun getHandledIds(): List<String> {
        return context.handledStore.data.map { prefs ->
            (prefs[HANDLED_IDS_KEY] ?: "").split(",").filter { it.isNotEmpty() }
        }.first()
    }

    fun getHandledIdsFlow(): Flow<List<String>> {
        return context.handledStore.data.map { prefs ->
            (prefs[HANDLED_IDS_KEY] ?: "").split(",").filter { it.isNotEmpty() }
        }
    }
}

package com.example.koistock.device

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Lưu/đọc [ScanProfile] cho từng [ScanFunction] trong DataStore. */
class ScanProfileStore(
    private val dataStore: DataStore<Preferences>,
) {
    fun profile(function: ScanFunction): Flow<ScanProfile> = dataStore.data.map { prefs ->
        val def = ScanProfile.default(function)
        ScanProfile(
            triggerMode = prefs[modeKey(function)]?.let { runCatching { TriggerMode.valueOf(it) }.getOrNull() } ?: def.triggerMode,
            power = prefs[powerKey(function)] ?: def.power,
            session = prefs[sessionKey(function)] ?: def.session,
            q = prefs[qKey(function)] ?: def.q,
            tagFocus = prefs[tagFocusKey(function)] ?: def.tagFocus,
            fastId = prefs[fastIdKey(function)] ?: def.fastId,
            millerM = prefs[millerKey(function)] ?: def.millerM,
        ).sanitized()
    }

    suspend fun save(function: ScanFunction, profile: ScanProfile) {
        val p = profile.sanitized()
        dataStore.edit { prefs ->
            prefs[modeKey(function)] = p.triggerMode.name
            prefs[powerKey(function)] = p.power
            prefs[sessionKey(function)] = p.session
            prefs[qKey(function)] = p.q
            prefs[tagFocusKey(function)] = p.tagFocus
            prefs[fastIdKey(function)] = p.fastId
            prefs[millerKey(function)] = p.millerM
        }
    }

    suspend fun reset(function: ScanFunction) = save(function, ScanProfile.default(function))

    private fun modeKey(f: ScanFunction) = stringPreferencesKey("scan_${f.key}_mode")
    private fun powerKey(f: ScanFunction) = intPreferencesKey("scan_${f.key}_power")
    private fun sessionKey(f: ScanFunction) = intPreferencesKey("scan_${f.key}_session")
    private fun qKey(f: ScanFunction) = intPreferencesKey("scan_${f.key}_q")
    private fun tagFocusKey(f: ScanFunction) = booleanPreferencesKey("scan_${f.key}_tagfocus")
    private fun fastIdKey(f: ScanFunction) = booleanPreferencesKey("scan_${f.key}_fastid")
    private fun millerKey(f: ScanFunction) = intPreferencesKey("scan_${f.key}_miller")
}

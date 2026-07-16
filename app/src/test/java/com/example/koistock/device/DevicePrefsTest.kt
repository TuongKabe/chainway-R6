package com.example.koistock.device

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DevicePrefsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun saveMac_persistsValue() = runTest {
        val prefs = createPrefs()

        prefs.saveMac("AA:BB:CC")

        assertEquals("AA:BB:CC", prefs.lastMac.first())
    }

    @Test
    fun deviceId_isStableAcrossReads() = runTest {
        val prefs = createPrefs()

        val first = prefs.deviceId()
        val second = prefs.deviceId()

        assertEquals(first, second)
    }

    @Test
    fun lastMac_defaultsToNull() = runTest {
        val prefs = createPrefs()

        assertNull(prefs.lastMac.first())
    }

    private fun createPrefs(): DevicePrefs {
        return DevicePrefs(
            PreferenceDataStoreFactory.create {
                tmp.newFile("device.preferences_pb")
            },
        )
    }
}

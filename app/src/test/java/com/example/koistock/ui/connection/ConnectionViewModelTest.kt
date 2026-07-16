package com.example.koistock.ui.connection

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.koistock.device.ConnectionState
import com.example.koistock.device.DevicePrefs
import com.example.koistock.device.FakeRfidReader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun connect_success_savesMac_andState() = runTest {
        val reader = FakeRfidReader()
        reader.setBattery(87)
        val prefs = prefs()
        val vm = ConnectionViewModel(reader, prefs, this.backgroundScope)

        vm.connect("AA:BB:CC:DD:EE:FF")
        advanceUntilIdle()

        assertEquals(
            ConnectionState.Connected("AA:BB:CC:DD:EE:FF"),
            vm.state.value,
        )
        assertEquals(87, vm.batteryPercent.value)
        assertEquals("AA:BB:CC:DD:EE:FF", prefs.lastMac.first { it != null })
    }

    @Test
    fun scan_populatesDevices() = runTest {
        val vm = ConnectionViewModel(FakeRfidReader(), prefs(), this.backgroundScope)

        vm.scan()
        advanceUntilIdle()

        assertEquals(1, vm.devices.value.size)
        assertEquals("AA:BB:CC:DD:EE:FF", vm.devices.value.first().mac)
    }

    @Test
    fun autoReconnect_noSavedMac_returnsFalse() = runTest {
        val vm = ConnectionViewModel(FakeRfidReader(), prefs(), this.backgroundScope)

        assertFalse(vm.tryAutoReconnect())
    }

    @Test
    fun autoReconnect_withSavedMac_connects() = runTest {
        val prefs = prefs()
        prefs.saveMac("AA:BB:CC:DD:EE:FF")
        val reader = FakeRfidReader().apply { setBattery(61) }
        val vm = ConnectionViewModel(reader, prefs, this.backgroundScope)

        assertTrue(vm.tryAutoReconnect())
        assertEquals(
            ConnectionState.Connected("AA:BB:CC:DD:EE:FF"),
            vm.state.value,
        )
        assertEquals(61, vm.batteryPercent.value)
    }

    @Test
    fun disconnect_clearsBattery() = runTest {
        val reader = FakeRfidReader().apply { setBattery(73) }
        val vm = ConnectionViewModel(reader, prefs(), this.backgroundScope)

        vm.connect("AA:BB:CC:DD:EE:FF")
        advanceUntilIdle()
        vm.disconnect()

        assertEquals(null, vm.batteryPercent.value)
    }

    private fun prefs(): DevicePrefs {
        return DevicePrefs(
            PreferenceDataStoreFactory.create {
                tmp.newFile("connection.preferences_pb")
            },
        )
    }
}

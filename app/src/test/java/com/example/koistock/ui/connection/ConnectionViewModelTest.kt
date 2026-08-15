package com.example.koistock.ui.connection

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.koistock.device.BleDeviceInfo
import com.example.koistock.device.ConnectionState
import com.example.koistock.device.DevicePrefs
import com.example.koistock.device.FakeRfidReader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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
        val reader = FakeRfidReader()
        val vm = ConnectionViewModel(reader, prefs(), this.backgroundScope)

        vm.scan()
        advanceUntilIdle()

        assertEquals(1, reader.deviceScanCount)
        assertEquals(1, vm.devices.value.size)
        assertEquals("AA:BB:CC:DD:EE:FF", vm.devices.value.first().mac)
    }

    @Test
    fun scan_dedupesSameNameKeepingStrongerRssi() = runTest {
        val reader = FakeRfidReader().apply {
            scanDevices.clear()
            scanDevices.add(BleDeviceInfo("UR-C88E", "AA:AA:AA:AA:AA:01", -70))
            scanDevices.add(BleDeviceInfo("UR-C88E", "BB:BB:BB:BB:BB:02", -40))
        }
        val vm = ConnectionViewModel(reader, prefs(), this.backgroundScope)

        vm.scan()
        advanceUntilIdle()

        assertEquals(1, vm.devices.value.size)
        assertEquals("BB:BB:BB:BB:BB:02", vm.devices.value.first().mac)
        assertEquals(-40, vm.devices.value.first().rssi)
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
    fun scan_restartsWithoutConcurrentReaderScans() = runTest {
        val reader = FakeRfidReader()
        val vm = ConnectionViewModel(reader, prefs(), this.backgroundScope)

        vm.scan()
        vm.scan()
        runCurrent()

        assertEquals(2, reader.deviceScanCount)
        assertEquals(1, reader.maxActiveDeviceScans)
    }

    @Test
    fun stopScan_cancelsActiveReaderScan() = runTest {
        val reader = FakeRfidReader()
        val vm = ConnectionViewModel(reader, prefs(), this.backgroundScope)
        vm.scan()
        runCurrent()

        vm.stopScan()
        runCurrent()

        assertEquals(0, reader.activeDeviceScans)
    }

    @Test
    fun scan_immediatelyAfterStop_waitsForPreviousReaderScanToClose() = runTest {
        val reader = FakeRfidReader()
        val vm = ConnectionViewModel(reader, prefs(), this.backgroundScope)
        vm.scan()
        runCurrent()

        vm.stopScan()
        vm.scan()
        runCurrent()

        assertEquals(1, reader.maxActiveDeviceScans)
        assertEquals(1, reader.activeDeviceScans)
    }

    @Test
    fun scan_whenAlreadyConnected_doesNotStartDeviceScan() = runTest {
        val reader = FakeRfidReader()
        val vm = ConnectionViewModel(reader, prefs(), this.backgroundScope)
        vm.connect("AA:BB:CC:DD:EE:FF")
        advanceUntilIdle()

        vm.scan()
        runCurrent()

        assertEquals(0, reader.deviceScanCount)
    }

    @Test
    fun autoReconnect_whenAlreadyConnected_doesNotConnectAgain() = runTest {
        val prefs = prefs()
        prefs.saveMac("AA:BB:CC:DD:EE:FF")
        val reader = FakeRfidReader()
        val vm = ConnectionViewModel(reader, prefs, this.backgroundScope)
        vm.connect("AA:BB:CC:DD:EE:FF")
        advanceUntilIdle()

        assertTrue(vm.tryAutoReconnect())
        assertEquals(1, reader.connectCount)
    }

    @Test
    fun autoReconnect_timesOutWhenReaderNeverCompletes() = runTest {
        val prefs = prefs()
        prefs.saveMac("AA:BB:CC:DD:EE:FF")
        val reader = FakeRfidReader().apply { connectDelayMs = Long.MAX_VALUE }
        val vm = ConnectionViewModel(reader, prefs, this.backgroundScope)

        assertFalse(vm.tryAutoReconnect())
        assertEquals(1, reader.connectCount)
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

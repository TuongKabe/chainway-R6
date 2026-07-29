package com.example.koistock.ui.connection

import com.example.koistock.device.BleDeviceInfo
import com.example.koistock.device.ConnectionState
import com.example.koistock.device.DevicePrefs
import com.example.koistock.device.RfidReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class ConnectionViewModel(
    private val reader: RfidReader,
    private val prefs: DevicePrefs,
    private val scope: CoroutineScope,
) {
    val state: StateFlow<ConnectionState> = reader.connectionState

    private val mutableDevices = MutableStateFlow<List<BleDeviceInfo>>(emptyList())
    val devices: StateFlow<List<BleDeviceInfo>> = mutableDevices.asStateFlow()

    private val mutableBatteryPercent = MutableStateFlow<Int?>(null)
    val batteryPercent: StateFlow<Int?> = mutableBatteryPercent.asStateFlow()

    private val mutablePower = MutableStateFlow<Int?>(null)
    val power: StateFlow<Int?> = mutablePower.asStateFlow()
    private var scanJob: Job? = null

    fun scan() {
        if (state.value is ConnectionState.Connected) return
        val previousScan = scanJob
        previousScan?.cancel()
        mutableDevices.value = emptyList()
        scanJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            previousScan?.join()
            reader.startDeviceScan().collect { device ->
                mutableDevices.update { current ->
                    current
                        .indexOfFirst {
                            it.mac == device.mac ||
                                it.name.equals(device.name, ignoreCase = true)
                        }
                        .let { existingIndex ->
                            when {
                                existingIndex < 0 -> current + device
                                device.rssi > current[existingIndex].rssi -> current.toMutableList().apply {
                                    this[existingIndex] = device
                                }
                                else -> current
                            }
                        }
                }
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
    }

    fun connect(mac: String) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (reader.connect(mac)) {
                mutableBatteryPercent.value = reader.batteryPercent()
                mutablePower.value = reader.getPower()
                prefs.saveMac(mac)
            }
        }
    }

    fun setPower(value: Int) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            reader.setPower(value)
            mutablePower.value = reader.getPower()
        }
    }

    fun refreshPower() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            mutablePower.value = reader.getPower()
        }
    }

    fun disconnect() {
        stopScan()
        reader.disconnect()
        mutableBatteryPercent.value = null
    }

    suspend fun tryAutoReconnect(): Boolean {
        if (state.value is ConnectionState.Connected) return true
        val mac = prefs.lastMac.first() ?: return false
        val connected = withTimeoutOrNull(AUTO_RECONNECT_TIMEOUT_MS) {
            reader.connect(mac)
        } ?: false
        if (connected) {
            mutableBatteryPercent.value = reader.batteryPercent()
            mutablePower.value = reader.getPower()
            prefs.saveMac(mac)
        }
        return connected
    }

    fun refreshBattery() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            mutableBatteryPercent.value = reader.batteryPercent()
        }
    }

    private companion object {
        const val AUTO_RECONNECT_TIMEOUT_MS = 8_000L
    }
}

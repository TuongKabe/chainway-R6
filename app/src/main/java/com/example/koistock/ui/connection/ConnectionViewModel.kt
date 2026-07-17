package com.example.koistock.ui.connection

import com.example.koistock.device.BleDeviceInfo
import com.example.koistock.device.ConnectionState
import com.example.koistock.device.DevicePrefs
import com.example.koistock.device.RfidReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    fun scan() {
        mutableDevices.value = emptyList()
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
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
        reader.disconnect()
        mutableBatteryPercent.value = null
    }

    suspend fun tryAutoReconnect(): Boolean {
        val mac = prefs.lastMac.first() ?: return false
        val connected = reader.connect(mac)
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
}

package com.example.koistock.device

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class ScannedTag(val epc: String, val rssi: Int)

data class BleDeviceInfo(val name: String, val mac: String, val rssi: Int)

interface RfidReader {
    val connectionState: StateFlow<ConnectionState>
    val inventory: SharedFlow<ScannedTag>
    val locateSignal: SharedFlow<Int>
    val triggerEvents: SharedFlow<Boolean>
    val rawKeyEvents: SharedFlow<String>

    fun startDeviceScan(): Flow<BleDeviceInfo>
    suspend fun connect(mac: String): Boolean
    fun disconnect()

    suspend fun scanSingle(): ScannedTag?
    fun startInventory()
    fun stopInventory()
    suspend fun writeEpc(oldEpc: String, newEpc: String): Boolean

    fun startLocate(targetEpc: String)
    fun stopLocate()

    suspend fun batteryPercent(): Int
    fun beep()
    fun release()
}

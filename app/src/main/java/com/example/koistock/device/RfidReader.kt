package com.example.koistock.device

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class ScannedTag(val epc: String, val rssi: Int, val rawRssi: String? = null)

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

    /**
     * Quét đơn tin cậy: chạy inventory liên tục trong [durationMs] rồi trả về tag có RSSI mạnh nhất
     * (gần đầu đọc nhất). Chính xác hơn hẳn scanSingle vốn chỉ đọc 1 vòng nên dễ trượt.
     */
    suspend fun scanBurst(durationMs: Long): ScannedTag?

    fun startInventory()
    fun stopInventory()
    suspend fun writeEpc(oldEpc: String, newEpc: String): Boolean

    /** Công suất phát 1..30 dBm. Cao hơn = xa/nhạy hơn nhưng dễ đọc nhầm tag lân cận. */
    suspend fun setPower(power: Int): Boolean
    suspend fun getPower(): Int

    /** Áp toàn bộ thông số Chainway theo [ScanProfile] (công suất, session, Q, TagFocus, FastID, Miller). */
    suspend fun applyScanConfig(profile: ScanProfile)

    fun startLocate(targetEpc: String)
    fun stopLocate()

    /** Bật/tắt tiếng beep tự động của phần cứng khi đọc trúng thẻ (để chỉ dùng beep phần mềm khi cần). */
    suspend fun setReadBeep(enabled: Boolean)

    suspend fun batteryPercent(): Int
    fun beep()
    fun release()
}

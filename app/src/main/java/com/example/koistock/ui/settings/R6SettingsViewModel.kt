package com.example.koistock.ui.settings

import com.example.koistock.device.ConfigApplyResult
import com.example.koistock.device.ConnectionState
import com.example.koistock.device.R6ConfigSnapshot
import com.example.koistock.device.R6DeviceConfig
import com.example.koistock.device.R6Region
import com.example.koistock.device.Readback
import com.example.koistock.device.RfidReader
import com.example.koistock.device.ScanFunction
import com.example.koistock.device.ScanProfile
import com.example.koistock.device.ScanProfileStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class VerificationStatus { UNVERIFIED, VERIFIED, MISMATCH, FAILED }

data class R6SettingsState(
    val connected: Boolean = false,
    val desiredConfig: R6DeviceConfig = R6DeviceConfig(),
    val snapshot: R6ConfigSnapshot? = null,
    val regionStatus: VerificationStatus = VerificationStatus.UNVERIFIED,
    val applyResult: ConfigApplyResult? = null,
    val loading: Boolean = false,
    val message: String? = null,
)

class R6SettingsViewModel(
    private val reader: RfidReader,
    private val store: ScanProfileStore,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutableState = MutableStateFlow(R6SettingsState())
    val state: StateFlow<R6SettingsState> = mutableState.asStateFlow()

    init {
        scope.launch {
            reader.connectionState.collectLatest { connection ->
                mutableState.update { it.copy(connected = connection is ConnectionState.Connected) }
                if (connection is ConnectionState.Connected) refresh()
            }
        }
        scope.launch {
            store.deviceConfig().collectLatest { config ->
                mutableState.update { it.copy(desiredConfig = config) }
            }
        }
    }

    fun refresh() = scope.launch(dispatcher) {
        if (reader.connectionState.value !is ConnectionState.Connected) {
            mutableState.update { it.copy(message = "R6 chưa kết nối") }
            return@launch
        }
        mutableState.update { it.copy(loading = true) }
        val snapshot = reader.readConfigSnapshot()
        val desired = store.deviceConfig().first().region
        val status = regionStatus(desired, snapshot.region)
        mutableState.update {
            it.copy(snapshot = snapshot, regionStatus = status, loading = false, message = status.message)
        }
    }

    fun saveRegion(region: R6Region) = scope.launch(dispatcher) {
        store.saveDeviceConfig(R6DeviceConfig(region))
        if (reader.connectionState.value !is ConnectionState.Connected) {
            mutableState.update {
                it.copy(regionStatus = VerificationStatus.UNVERIFIED, message = "Đã lưu, chưa áp dụng lên R6")
            }
            return@launch
        }
        mutableState.update { it.copy(loading = true) }
        val command = reader.setRegion(region)
        val snapshot = reader.readConfigSnapshot()
        val status = if (command.success) regionStatus(region, snapshot.region) else VerificationStatus.FAILED
        mutableState.update {
            it.copy(snapshot = snapshot, regionStatus = status, loading = false, message = command.message ?: status.message)
        }
    }

    fun saveProfile(function: ScanFunction, profile: ScanProfile) = scope.launch(dispatcher) {
        store.save(function, profile)
        if (reader.connectionState.value !is ConnectionState.Connected) {
            mutableState.update { it.copy(message = "Đã lưu, chưa áp dụng lên R6") }
            return@launch
        }
        val region = store.deviceConfig().first().region
        val result = reader.applyScanConfig(profile, region)
        mutableState.update {
            it.copy(
                snapshot = result.snapshot,
                applyResult = result,
                message = if (result.isBlocking) "Cấu hình R6 sai lệch" else "Đã lưu và xác nhận trên R6",
            )
        }
    }

    fun resetProfile(function: ScanFunction) = scope.launch(dispatcher) {
        store.reset(function)
    }

    private fun regionStatus(expected: R6Region, actual: Readback<R6Region>): VerificationStatus = when (actual) {
        is Readback.Value -> if (actual.value == expected) VerificationStatus.VERIFIED else VerificationStatus.MISMATCH
        is Readback.Failed -> VerificationStatus.FAILED
        Readback.Unsupported -> VerificationStatus.FAILED
    }

    private val VerificationStatus.message: String get() = when (this) {
        VerificationStatus.VERIFIED -> "Đã xác nhận trên R6"
        VerificationStatus.MISMATCH -> "Vùng tần số R6 đang sai lệch"
        VerificationStatus.FAILED -> "Không đọc được vùng tần số từ R6"
        VerificationStatus.UNVERIFIED -> "Chưa áp dụng lên R6"
    }
}

package com.example.koistock.device

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

class FakeRfidReader : RfidReader {
    private val state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = state.asStateFlow()

    private val inventoryFlow = MutableSharedFlow<ScannedTag>(extraBufferCapacity = 64)
    override val inventory: SharedFlow<ScannedTag> = inventoryFlow.asSharedFlow()

    private val locateFlow = MutableSharedFlow<Int>(extraBufferCapacity = 64)
    override val locateSignal: SharedFlow<Int> = locateFlow.asSharedFlow()

    private val triggerFlow = MutableSharedFlow<Boolean>(extraBufferCapacity = 16)
    override val triggerEvents: SharedFlow<Boolean> = triggerFlow.asSharedFlow()

    private val rawKeyFlow = MutableSharedFlow<String>(extraBufferCapacity = 16)
    override val rawKeyEvents: SharedFlow<String> = rawKeyFlow.asSharedFlow()

    private var connectResult = true
    private var battery = 100
    var scannedSingle: ScannedTag? = null
    var singleScanCount = 0
    var lastWrittenEpc: Pair<String, String>? = null
    var locateTarget: String? = null
    var inventoryRunning = false
    var inventoryStartCount = 0
    var inventoryStopCount = 0
    var beepCount = 0

    val scanDevices = mutableListOf(BleDeviceInfo("R6-TEST", "AA:BB:CC:DD:EE:FF", -50))

    override fun startDeviceScan(): Flow<BleDeviceInfo> = flow {
        state.value = ConnectionState.Scanning
        scanDevices.forEach { emit(it) }
        if (state.value == ConnectionState.Scanning) {
            state.value = ConnectionState.Disconnected
        }
    }

    override suspend fun connect(mac: String): Boolean {
        state.value = ConnectionState.Connecting(mac)
        state.value = if (connectResult) {
            ConnectionState.Connected(mac)
        } else {
            ConnectionState.Disconnected
        }
        return connectResult
    }

    override fun disconnect() {
        state.value = ConnectionState.Disconnected
    }

    override suspend fun scanSingle(): ScannedTag? {
        singleScanCount += 1
        return scannedSingle
    }

    var burstScanCount = 0
    var lastPower = 30
    var readProfileApplied = false

    override suspend fun scanBurst(durationMs: Long): ScannedTag? {
        burstScanCount += 1
        return scannedSingle
    }

    override suspend fun setPower(power: Int): Boolean {
        lastPower = power
        return true
    }

    override suspend fun getPower(): Int = lastPower

    var lastAppliedProfile: ScanProfile? = null
    var desiredRegion = R6Region.VIETNAM
    var nextApplyResult: ConfigApplyResult? = null
    var nextSnapshot: R6ConfigSnapshot? = null

    override suspend fun readConfigSnapshot(): R6ConfigSnapshot =
        nextSnapshot ?: snapshot(lastAppliedProfile ?: ScanProfile())

    override suspend fun setRegion(region: R6Region): ConfigCommandResult {
        desiredRegion = region
        return ConfigCommandResult(ConfigField.REGION, true)
    }

    override suspend fun applyScanConfig(
        profile: ScanProfile,
        expectedRegion: R6Region,
    ): ConfigApplyResult {
        lastAppliedProfile = profile
        lastPower = profile.power
        return nextApplyResult ?: ConfigApplyResult(
            requested = profile,
            requestedRegion = expectedRegion,
            commands = emptyList(),
            snapshot = nextSnapshot ?: snapshot(profile, expectedRegion),
        )
    }

    private fun snapshot(
        profile: ScanProfile,
        region: R6Region = desiredRegion,
    ) = R6ConfigSnapshot(
        power = Readback.Value(profile.power),
        region = Readback.Value(region),
        session = Readback.Value(profile.session),
        q = Readback.Value(profile.q),
        millerM = Readback.Value(profile.millerM),
        tagFocus = Readback.Value(profile.tagFocus),
        fastId = Readback.Value(profile.fastId),
    )

    override fun startInventory() {
        inventoryRunning = true
        inventoryStartCount += 1
    }

    override fun stopInventory() {
        inventoryRunning = false
        inventoryStopCount += 1
    }

    override suspend fun writeEpc(oldEpc: String, newEpc: String): Boolean {
        lastWrittenEpc = oldEpc to newEpc
        return true
    }

    override fun startLocate(targetEpc: String) {
        locateTarget = targetEpc
    }

    override fun stopLocate() {
        locateTarget = null
    }

    var readBeepEnabled = true

    override suspend fun setReadBeep(enabled: Boolean) {
        readBeepEnabled = enabled
    }

    override suspend fun batteryPercent(): Int = battery

    override fun beep() {
        beepCount += 1
    }

    override fun release() = Unit

    suspend fun emitTag(epc: String, rssi: Int = -55) {
        inventoryFlow.emit(ScannedTag(epc, rssi))
    }

    suspend fun emitLocate(value: Int) {
        locateFlow.emit(value)
    }

    suspend fun emitTrigger(pressed: Boolean) {
        triggerFlow.emit(pressed)
    }

    suspend fun emitRawKeyEvent(message: String) {
        rawKeyFlow.emit(message)
    }

    fun setConnectResult(value: Boolean) {
        connectResult = value
    }

    fun setBattery(value: Int) {
        battery = value
    }
}

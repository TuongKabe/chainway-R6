package com.example.koistock.device

import android.annotation.SuppressLint
import android.content.Context
import com.rscja.deviceapi.RFIDWithUHFBLE
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.interfaces.ConnectionStatus
import com.rscja.deviceapi.interfaces.ConnectionStatusCallback
import com.rscja.deviceapi.interfaces.IUHFInventoryCallback
import com.rscja.deviceapi.interfaces.IUHFLocationCallback
import com.rscja.deviceapi.interfaces.KeyEventCallback
import com.rscja.deviceapi.interfaces.ScanBTCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class ChainwayRfidReader(
    context: Context,
) : RfidReader {
    private val appContext = context.applicationContext
    private val sdk = RFIDWithUHFBLE.getInstance()

    private val mutableConnectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = mutableConnectionState.asStateFlow()

    private val inventoryFlow = MutableSharedFlow<ScannedTag>(extraBufferCapacity = 64)
    override val inventory: SharedFlow<ScannedTag> = inventoryFlow.asSharedFlow()

    private val locateFlow = MutableSharedFlow<Int>(extraBufferCapacity = 64)
    override val locateSignal: SharedFlow<Int> = locateFlow.asSharedFlow()

    private val triggerFlow = MutableSharedFlow<Boolean>(extraBufferCapacity = 16)
    override val triggerEvents: SharedFlow<Boolean> = triggerFlow.asSharedFlow()

    private val rawKeyFlow = MutableSharedFlow<String>(extraBufferCapacity = 32)
    override val rawKeyEvents: SharedFlow<String> = rawKeyFlow.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val triggerTracker = TriggerPressTracker(scope) { pressed ->
        triggerFlow.tryEmit(pressed)
    }

    private var currentMac: String? = null

    private val inventoryCallback = IUHFInventoryCallback { info ->
        info?.toScannedTag()?.let { inventoryFlow.tryEmit(it) }
    }

    private val locateCallback = IUHFLocationCallback { value, _ ->
        locateFlow.tryEmit(value.coerceIn(0, 100))
    }

    private val connectionCallback = ConnectionStatusCallback<Any> { status, _ ->
        mutableConnectionState.value = when (status) {
            ConnectionStatus.CONNECTED -> ConnectionState.Connected(currentMac ?: "")
            ConnectionStatus.CONNECTING -> ConnectionState.Connecting(currentMac ?: "")
            ConnectionStatus.DISCONNECTED -> ConnectionState.Disconnected
            else -> ConnectionState.Disconnected
        }
    }

    private val keyEventCallback = object : KeyEventCallback {
        override fun onKeyDown(keyCode: Int) {
            rawKeyFlow.tryEmit("SDK DOWN keyCode=$keyCode")
            triggerTracker.onKeyDown(keyCode)
        }

        override fun onKeyUp(keyCode: Int) {
            rawKeyFlow.tryEmit("SDK UP keyCode=$keyCode")
            triggerTracker.onKeyUp(keyCode)
        }
    }

    init {
        sdk.init(appContext)
        sdk.setConnectionStatusCallback(connectionCallback)
        sdk.setInventoryCallback(inventoryCallback)
        sdk.setKeyEventCallback(keyEventCallback)
    }

    @SuppressLint("MissingPermission")
    override fun startDeviceScan(): Flow<BleDeviceInfo> = callbackFlow {
        mutableConnectionState.value = ConnectionState.Scanning
        runCatching {
            sdk.startScanBTDevices(
                ScanBTCallback { device, rssi, _ ->
                    val name = device?.name?.trim().orEmpty()
                    val mac = device?.address?.trim().orEmpty()
                    if (!looksLikeChainwayReader(name, mac)) return@ScanBTCallback
                    trySend(BleDeviceInfo(name = name, mac = mac, rssi = rssi))
                },
            )
        }
        awaitClose {
            runCatching { sdk.stopScanBTDevices() }
            if (mutableConnectionState.value == ConnectionState.Scanning) {
                mutableConnectionState.value = ConnectionState.Disconnected
            }
        }
    }

    override suspend fun connect(mac: String): Boolean = suspendCancellableCoroutine { cont ->
        currentMac = mac
        mutableConnectionState.value = ConnectionState.Connecting(mac)
        val started = runCatching {
            sdk.connect(
                mac,
                ConnectionStatusCallback<Any> { status, _ ->
                when (status) {
                    ConnectionStatus.CONNECTED -> {
                        mutableConnectionState.value = ConnectionState.Connected(mac)
                        if (cont.isActive) cont.resume(true)
                    }

                    ConnectionStatus.DISCONNECTED -> {
                        mutableConnectionState.value = ConnectionState.Disconnected
                        if (cont.isActive) cont.resume(false)
                    }

                    ConnectionStatus.CONNECTING -> {
                        mutableConnectionState.value = ConnectionState.Connecting(mac)
                    }

                    else -> Unit
                }
                },
            )
        }
        if (started.isFailure) {
            mutableConnectionState.value = ConnectionState.Disconnected
            if (cont.isActive) cont.resume(false)
        }
    }

    override fun disconnect() {
        runCatching { sdk.disconnect() }
        mutableConnectionState.value = ConnectionState.Disconnected
    }

    override suspend fun scanSingle(): ScannedTag? =
        runCatching { sdk.inventorySingleTag()?.toScannedTag() }.getOrNull()

    override fun startInventory() {
        runCatching {
            sdk.setInventoryCallback(inventoryCallback)
            sdk.startInventoryTag()
        }
    }

    override fun stopInventory() {
        runCatching { sdk.stopInventory() }
    }

    override suspend fun writeEpc(oldEpc: String, newEpc: String): Boolean =
        runCatching { sdk.writeDataToEpc(oldEpc, newEpc) }.getOrDefault(false)

    override fun startLocate(targetEpc: String) {
        runCatching { sdk.startLocation(appContext, targetEpc, 5, 0, locateCallback) }
    }

    override fun stopLocate() {
        runCatching { sdk.stopLocation() }
        locateFlow.tryEmit(0)
    }

    override suspend fun batteryPercent(): Int =
        runCatching { sdk.battery.coerceIn(0, 100) }.getOrDefault(0)

    override fun beep() {
        runCatching { sdk.triggerBeep(1) }
    }

    override fun release() {
        triggerTracker.release()
        scope.coroutineContext.cancel()
        stopInventory()
        stopLocate()
        disconnect()
        runCatching { sdk.free() }
    }

    private fun UHFTAGInfo.toScannedTag(): ScannedTag? {
        val epcValue = epc?.takeIf { it.isNotBlank() } ?: return null
        val parsedRssi = rssi?.toIntOrNull() ?: -60
        return ScannedTag(epcValue, parsedRssi)
    }

    private fun looksLikeChainwayReader(
        name: String,
        mac: String,
    ): Boolean {
        if (name.isBlank() || mac.isBlank()) return false
        val normalized = name.uppercase()
        return normalized == "UR-C88E" ||
            normalized.startsWith("UR-C88E") ||
            normalized.contains("C88E") ||
            normalized.contains("CHAINWAY")
    }
}

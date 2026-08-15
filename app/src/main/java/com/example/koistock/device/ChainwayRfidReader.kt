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
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class ChainwayRfidReader(
    context: Context,
) : RfidReader {
    private val appContext = context.applicationContext
    private val configGate = VerifiedConfigGate()

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

    private val sdkDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RFIDWithUHFBLE.getInstance().also { reader ->
            reader.init(appContext)
            reader.setConnectionStatusCallback(connectionCallback)
            reader.setInventoryCallback(inventoryCallback)
            reader.setKeyEventCallback(keyEventCallback)
        }
    }
    private val sdk: RFIDWithUHFBLE get() = sdkDelegate.value

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
                if (!cont.isActive) {
                    if (status == ConnectionStatus.CONNECTED) {
                        runCatching { sdk.disconnect() }
                    }
                    return@ConnectionStatusCallback
                }
                when (status) {
                    ConnectionStatus.CONNECTED -> {
                        configGate.allowPending()
                        mutableConnectionState.value = ConnectionState.Connected(mac)
                        runCatching { sdk.setSupportRssi(true) }
                        // Áp cấu hình mặc định ngay khi kết nối; mỗi màn sẽ áp profile riêng khi mở.
                        scope.launch { applyScanConfig(ScanProfile()) }
                        if (cont.isActive) cont.resume(true)
                    }

                    ConnectionStatus.DISCONNECTED -> {
                        configGate.close()
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
        cont.invokeOnCancellation {
            runCatching { sdk.disconnect() }
            mutableConnectionState.value = ConnectionState.Disconnected
        }
    }

    override fun disconnect() {
        configGate.close()
        runCatching { sdk.disconnect() }
        mutableConnectionState.value = ConnectionState.Disconnected
    }

    override suspend fun scanSingle(): ScannedTag? =
        if (!configGate.canScan) null
        else runCatching { sdk.inventorySingleTag()?.toScannedTag() }.getOrNull()

    override suspend fun scanBurst(durationMs: Long): ScannedTag? {
        if (!configGate.canScan) return null
        val bestRssiByEpc = HashMap<String, Int>()
        runCatching {
            sdk.setInventoryCallback(inventoryCallback)
            sdk.startInventoryTag()
            withTimeoutOrNull(durationMs) {
                inventoryFlow.collect { tag ->
                    val prev = bestRssiByEpc[tag.epc]
                    if (prev == null || tag.rssi > prev) bestRssiByEpc[tag.epc] = tag.rssi
                }
            }
        }
        runCatching { sdk.stopInventory() }
        return bestRssiByEpc.maxByOrNull { it.value }?.let { ScannedTag(it.key, it.value) }
    }

    override suspend fun setPower(power: Int): Boolean =
        runCatching { sdk.setPower(power.coerceIn(1, 30)) }.getOrDefault(false)

    override suspend fun getPower(): Int =
        runCatching { sdk.power }.getOrDefault(0)

    override suspend fun readConfigSnapshot(): R6ConfigSnapshot {
        val gen2 = runCatching { sdk.gen2 }
        return R6ConfigSnapshot(
            power = readback { sdk.power }.validating { it in 1..30 },
            region = readback { sdk.frequencyMode }.mapping { code ->
                R6Region.fromSdkCode(code) ?: error("Mã vùng không hỗ trợ: 0x${code.toString(16)}")
            },
            session = gen2.readback { it.querySession },
            q = gen2.readback { it.startQ },
            millerM = gen2.readback { it.queryM },
        )
    }

    override suspend fun setRegion(region: R6Region): ConfigCommandResult =
        command(ConfigField.REGION) { sdk.setFrequencyMode(region.sdkCode) }

    override suspend fun applyScanConfig(
        profile: ScanProfile,
        expectedRegion: R6Region,
    ): ConfigApplyResult {
        val p = profile.sanitized()
        val commands = buildList {
            add(command(ConfigField.READ_MODE) { sdk.setEPCMode() })
            add(command(ConfigField.POWER) { sdk.setPower(p.power) })
            val gen2 = runCatching { sdk.gen2 }.getOrNull()
                ?: com.rscja.deviceapi.entity.Gen2Entity()
            gen2.querySession = p.session
            gen2.queryTarget = 0
            gen2.startQ = p.q
            gen2.queryM = p.millerM
            add(command(ConfigField.SESSION) { sdk.setGen2(gen2) })
            add(command(ConfigField.TAG_FOCUS) { sdk.setTagFocus(p.tagFocus) })
            add(command(ConfigField.FAST_ID) {
                sdk.setFastID(p.readMode == ScanReadMode.EPC_AND_TID || p.fastId)
            })
        }
        return ConfigApplyResult(p, expectedRegion, commands, readConfigSnapshot()).also(configGate::record)
    }

    private inline fun command(field: ConfigField, block: () -> Boolean): ConfigCommandResult =
        runCatching { block() }.fold(
            onSuccess = { ConfigCommandResult(field, it, if (it) null else "SDK trả về false") },
            onFailure = { ConfigCommandResult(field, false, it.message ?: it.javaClass.simpleName) },
        )

    private inline fun <T> readback(block: () -> T): Readback<T> =
        runCatching(block).fold(
            onSuccess = { Readback.Value(it) },
            onFailure = { Readback.Failed(it.message ?: it.javaClass.simpleName) },
        )

    private inline fun <T, R> Result<T?>.readback(transform: (T) -> R): Readback<R> = fold(
        onSuccess = { value ->
            if (value == null) Readback.Failed("R6 không trả về Gen2")
            else runCatching { transform(value) }.fold(
                onSuccess = { Readback.Value(it) },
                onFailure = { Readback.Failed(it.message ?: it.javaClass.simpleName) },
            )
        },
        onFailure = { Readback.Failed(it.message ?: it.javaClass.simpleName) },
    )

    private inline fun <T> Readback<T>.validating(predicate: (T) -> Boolean): Readback<T> = when (this) {
        is Readback.Value -> if (predicate(value)) this else Readback.Failed("Giá trị R6 không hợp lệ: $value")
        else -> this
    }

    private inline fun <T, R> Readback<T>.mapping(transform: (T) -> R): Readback<R> = when (this) {
        is Readback.Value -> runCatching { transform(value) }.fold(
            onSuccess = { Readback.Value(it) },
            onFailure = { Readback.Failed(it.message ?: it.javaClass.simpleName) },
        )
        is Readback.Failed -> this
        Readback.Unsupported -> Readback.Unsupported
    }

    override fun startInventory() {
        if (!configGate.canScan) return
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
        if (!configGate.canScan) return
        runCatching { sdk.startLocation(appContext, targetEpc, 5, 0, locateCallback) }
    }

    override fun stopLocate() {
        runCatching { sdk.stopLocation() }
        locateFlow.tryEmit(0)
    }

    override suspend fun batteryPercent(): Int =
        runCatching { sdk.battery.coerceIn(0, 100) }.getOrDefault(0)

    override fun beep() {
        // triggerBeep là lệnh BLE chặn luồng; chạy nền để không nghẽn Main
        // (nếu gọi trong vòng lặp beep của định vị sẽ làm treo collector/cò).
        scope.launch { runCatching { sdk.triggerBeep(1) } }
    }

    override fun release() {
        triggerTracker.release()
        scope.coroutineContext.cancel()
        if (!sdkDelegate.isInitialized()) return
        stopInventory()
        stopLocate()
        disconnect()
        runCatching { sdk.free() }
    }

    private fun UHFTAGInfo.toScannedTag(): ScannedTag? {
        val epcValue = epc?.takeIf { it.isNotBlank() } ?: return null
        val raw = rssi
        return ScannedTag(epcValue, RssiParser.parse(raw), raw)
    }

    override suspend fun setReadBeep(enabled: Boolean) {
        runCatching { sdk.setBeep(enabled) }
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

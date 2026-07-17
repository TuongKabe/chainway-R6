package com.example.koistock.ui.locate

import com.example.koistock.device.Beeper
import com.example.koistock.device.RfidReader
import com.example.koistock.device.ScanProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object BeepCadence {
    /**
     * Nhịp beep theo cường độ tín hiệu, lấy theo công thức của demo hãng Chainway:
     * càng gần (signal cao) nhịp càng ngắn. Có sàn tối thiểu để không thành tiếng rè liền mạch.
     */
    fun intervalMs(signal0to100: Int): Long {
        val s = signal0to100.coerceIn(0, 100)
        val t = when {
            s > 85 -> 5
            s > 66 -> 100 - s
            s > 33 -> (100 - s) * 2
            else -> (100 - s) * 3
        }
        return t.toLong().coerceAtLeast(MIN_INTERVAL_MS)
    }

    private const val MIN_INTERVAL_MS = 45L
}

/**
 * Định vị bằng cơ chế inventory (giống "Quét theo khu" đang chạy tốt trên R6):
 * bật inventory, lọc EPC mục tiêu, đổi RSSI thành cường độ tín hiệu 0..100.
 * RSSI càng cao (gần 0 dBm) nghĩa là tag càng gần.
 */
class LocateViewModel(
    private val reader: RfidReader,
    private val scope: CoroutineScope,
    private val profile: ScanProfile = ScanProfile(),
    /** Phát tiếng qua loa điện thoại; buzzer R6 bị tắt khi dò nên không dùng được. */
    private val beeper: Beeper = Beeper.NoOp,
) {
    private val mutableSignal = MutableStateFlow(0)
    val signal: StateFlow<Int> = mutableSignal.asStateFlow()
    private val mutableIntervalMs = MutableStateFlow(BeepCadence.intervalMs(0))
    val intervalMs: StateFlow<Long> = mutableIntervalMs.asStateFlow()
    private val mutableIsLocating = MutableStateFlow(false)
    val isLocating: StateFlow<Boolean> = mutableIsLocating.asStateFlow()
    private val mutableTargetEpc = MutableStateFlow<String?>(null)
    val targetEpc: StateFlow<String?> = mutableTargetEpc.asStateFlow()

    /** Tổng số lần đọc được BẤT KỲ thẻ nào khi đang dò (để chẩn đoán reader có đọc không). */
    private val mutableRawReads = MutableStateFlow(0)
    val rawReads: StateFlow<Int> = mutableRawReads.asStateFlow()

    /** EPC thẻ vừa đọc gần nhất (bất kỳ), giúp so với EPC mục tiêu khi không khớp. */
    private val mutableLastSeenEpc = MutableStateFlow<String?>(null)
    val lastSeenEpc: StateFlow<String?> = mutableLastSeenEpc.asStateFlow()

    /** RSSI thô (chuỗi) của thẻ mục tiêu vừa đọc — để chẩn đoán định dạng RSSI của R6. */
    private val mutableLastRawRssi = MutableStateFlow<String?>(null)
    val lastRawRssi: StateFlow<String?> = mutableLastRawRssi.asStateFlow()

    private var armedEpc: String? = null
    private var locateJob: Job? = null
    private var beepJob: Job? = null
    private var triggerJob: Job? = null

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { reader.applyScanConfig(profile) }
        // Định vị cần tín hiệu liên tục nên luôn dùng toggle: bóp lần 1 dò, bóp lần 2 dừng.
        triggerJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            reader.triggerEvents.collect { pressed ->
                if (!pressed) return@collect
                if (mutableIsLocating.value) stop() else armedEpc?.let { start(it) }
            }
        }
    }

    /** Đặt tag mục tiêu để cò có thể bắt đầu dò. */
    fun setTarget(epc: String?) {
        armedEpc = epc
    }

    fun start(targetEpc: String) {
        // Đang dò đúng tag này rồi thì bỏ qua (tránh cò nảy làm reset bộ đếm/collector).
        if (mutableIsLocating.value && mutableTargetEpc.value == targetEpc) return
        armedEpc = targetEpc
        mutableTargetEpc.value = targetEpc
        mutableIsLocating.value = true
        mutableSignal.value = 0
        mutableIntervalMs.value = BeepCadence.intervalMs(0)
        mutableRawReads.value = 0
        mutableLastSeenEpc.value = null
        mutableLastRawRssi.value = null
        val normalizedTarget = normalizeEpc(targetEpc)
        locateJob?.cancel()
        beepJob?.cancel()
        // Tắt beep tự động của phần cứng: chỉ beep phần mềm khi khớp đúng tag mục tiêu.
        scope.launch { reader.setReadBeep(false) }
        reader.startInventory()
        locateJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            reader.inventory.collect { scanned ->
                if (!mutableIsLocating.value) return@collect
                // Đếm mọi thẻ đọc được để biết reader có hoạt động không.
                mutableRawReads.value += 1
                mutableLastSeenEpc.value = scanned.epc
                // So khớp EPC mục tiêu đã chuẩn hoá (bỏ khoảng trắng, không phân biệt hoa/thường).
                if (normalizeEpc(scanned.epc) != normalizedTarget) return@collect
                mutableLastRawRssi.value = scanned.rawRssi
                val value = rssiToSignal(scanned.rssi)
                mutableSignal.value = value
                mutableIntervalMs.value = BeepCadence.intervalMs(value)
                restartBeepLoop(value)
            }
        }
    }

    fun stop() {
        // Đặt cờ trước để collector/vòng beep đang chạy tự thoát ngay, tránh race bật lại.
        mutableIsLocating.value = false
        locateJob?.cancel()
        locateJob = null
        beepJob?.cancel()
        beepJob = null
        reader.stopInventory()
        scope.launch { reader.setReadBeep(true) }
        mutableSignal.value = 0
        mutableIntervalMs.value = BeepCadence.intervalMs(0)
    }

    fun clear() {
        triggerJob?.cancel()
        stop()
    }

    private fun restartBeepLoop(signal: Int) {
        beepJob?.cancel()
        if (signal <= 0 || !mutableIsLocating.value) return
        beepJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            // Vòng beep tự thoát khi hết dò, không phụ thuộc riêng vào cancel.
            while (mutableIsLocating.value) {
                beeper.beep()
                delay(mutableIntervalMs.value)
            }
        }
    }

    companion object {
        // Dải RSSI đo thực tế trên R6 (FCC/America, 26dBm): -75 khi xa nhất, -29 khi sát thẻ.
        // Map đúng dải này để tín hiệu trải đều hết thang 0..100.
        private const val FAR_DBM = -75
        private const val NEAR_DBM = -29

        /** Chuẩn hoá EPC để so khớp: bỏ khoảng trắng, viết hoa. */
        fun normalizeEpc(epc: String): String = epc.trim().replace(" ", "").uppercase()

        /** Đổi RSSI (dBm) sang tín hiệu 0..100. -75dBm→0 (xa nhất), -29dBm→100 (sát thẻ). */
        fun rssiToSignal(rssi: Int): Int {
            val clamped = rssi.coerceIn(FAR_DBM, NEAR_DBM)
            return ((clamped - FAR_DBM) * 100 / (NEAR_DBM - FAR_DBM)).coerceIn(0, 100)
        }
    }
}

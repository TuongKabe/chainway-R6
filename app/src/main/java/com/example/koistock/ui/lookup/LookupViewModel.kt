package com.example.koistock.ui.lookup

import com.example.koistock.data.model.Product
import com.example.koistock.data.model.TagMapping
import com.example.koistock.data.remote.ProductRepo
import com.example.koistock.data.remote.TagRepo
import com.example.koistock.device.RfidReader
import com.example.koistock.device.ScanProfile
import com.example.koistock.device.TriggerMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LookupResult {
    data object Idle : LookupResult
    data class Found(val product: Product, val tag: TagMapping) : LookupResult
    data class UnknownTag(val epc: String) : LookupResult
    data object NotFound : LookupResult
    data class Error(val message: String) : LookupResult
}

class LookupViewModel(
    private val reader: RfidReader,
    private val tagRepo: TagRepo,
    private val productRepo: ProductRepo,
    private val scope: CoroutineScope,
    private val profile: ScanProfile = ScanProfile(),
) {
    private val mutableResult = MutableStateFlow<LookupResult>(LookupResult.Idle)
    val result: StateFlow<LookupResult> = mutableResult.asStateFlow()

    private val burstDurationMs = if (profile.triggerMode == TriggerMode.CONTINUOUS) 300L else 600L

    private var triggerJob: Job? = null
    private var holdJob: Job? = null
    private var holdActive = false

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { reader.applyScanConfig(profile) }
        triggerJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            reader.triggerEvents.collect { pressed ->
                if (!pressed) return@collect
                when (profile.triggerMode) {
                    // Bóp 1 lần: mỗi lần bóp đọc 1 lần.
                    TriggerMode.SINGLE -> scanOnce()
                    // Liên tục: bóp lần 1 bắt đầu, bóp lần 2 kết thúc.
                    TriggerMode.CONTINUOUS -> if (holdActive) stopHold() else startHold()
                }
            }
        }
    }

    private fun startHold() {
        holdActive = true
        holdJob?.cancel()
        holdJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            while (true) {
                lookupOnce()
            }
        }
    }

    private fun stopHold() {
        holdActive = false
        holdJob?.cancel()
    }

    fun scanOnce() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { lookupOnce() }
    }

    private suspend fun lookupOnce() {
        try {
            val scanned = reader.scanBurst(burstDurationMs)
            if (scanned == null) {
                mutableResult.value = LookupResult.NotFound
                return
            }

            val mapping = tagRepo.getByEpc(scanned.epc)
            if (mapping == null) {
                mutableResult.value = LookupResult.UnknownTag(scanned.epc)
                return
            }

            val product = productRepo.getBySku(mapping.sku)
            mutableResult.value = if (product != null) {
                reader.beep()
                LookupResult.Found(product, mapping)
            } else {
                LookupResult.Error("Tag đã map SKU ${mapping.sku} nhưng chưa lấy được dữ liệu sản phẩm từ backend.")
            }
        } catch (t: Throwable) {
            mutableResult.value = LookupResult.Error(t.message ?: "Tra cứu thất bại. Kiểm tra mạng hoặc backend.")
        }
    }

    fun clear() {
        triggerJob?.cancel()
        holdJob?.cancel()
    }
}

package com.example.koistock.ui.count

import com.example.koistock.data.model.Transaction
import com.example.koistock.data.model.TxType
import com.example.koistock.data.remote.ProductRepo
import com.example.koistock.data.remote.TagRepo
import com.example.koistock.data.remote.TransactionRepo
import com.example.koistock.device.RfidReader
import com.example.koistock.domain.CountReconciler
import com.example.koistock.domain.CountRow
import com.example.koistock.domain.CsvExporter
import com.example.koistock.domain.ExpectedItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class CountViewModel(
    private val reader: RfidReader,
    private val tagRepo: TagRepo,
    private val productRepo: ProductRepo,
    private val txRepo: TransactionRepo,
    private val deviceId: String,
    private val now: () -> Long,
    private val scope: CoroutineScope,
) {
    private val mutableZone = MutableStateFlow<String?>(null)
    val zone: StateFlow<String?> = mutableZone.asStateFlow()

    private val mutableCountedBySku = MutableStateFlow<Map<String, Int>>(emptyMap())
    val countedBySku: StateFlow<Map<String, Int>> = mutableCountedBySku.asStateFlow()

    private val mutableRows = MutableStateFlow<List<CountRow>>(emptyList())
    val rows: StateFlow<List<CountRow>> = mutableRows.asStateFlow()

    private val seenEpcs = mutableSetOf<String>()
    private var scanJob: Job? = null
    private var triggerJob: Job? = null
    private var scanRunning = false

    init {
        triggerJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            reader.triggerEvents.collect { pressed ->
                if (pressed) {
                    if (scanRunning) {
                        stopScan()
                    } else {
                        startScan()
                    }
                }
            }
        }
    }

    fun setZone(code: String) {
        mutableZone.value = code
    }

    fun startScan() {
        seenEpcs.clear()
        mutableCountedBySku.value = emptyMap()
        reader.startInventory()
        scanRunning = true
        scanJob?.cancel()
        scanJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            reader.inventory.collect { scanned ->
                if (!seenEpcs.add(scanned.epc)) return@collect
                val tag = tagRepo.getByEpc(scanned.epc) ?: return@collect
                val product = productRepo.getBySku(tag.sku) ?: return@collect
                mutableCountedBySku.update { current ->
                    current + (product.sku to ((current[product.sku] ?: 0) + 1))
                }
            }
        }
    }

    fun stopScan() {
        reader.stopInventory()
        scanJob?.cancel()
        scanRunning = false
    }

    suspend fun reconcile(expected: List<ExpectedItem>) {
        mutableRows.value = CountReconciler.reconcile(mutableZone.value.orEmpty(), countedBySku.value, expected)
    }

    suspend fun saveCount() {
        val location = mutableZone.value ?: return
        val at = now()
        countedBySku.value.forEach { (sku, qty) ->
            txRepo.append(
                Transaction(
                    id = UUID.randomUUID().toString(),
                    type = TxType.COUNT,
                    sku = sku,
                    delta = qty.toLong(),
                    locationCode = location,
                    deviceId = deviceId,
                    at = at,
                ),
            )
        }
    }

    fun csv(): String = CsvExporter.toCsv(rows.value, now())

    fun clear() {
        triggerJob?.cancel()
        scanJob?.cancel()
    }
}

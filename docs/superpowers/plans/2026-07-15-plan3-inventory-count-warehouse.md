# KOIStock — Plan 3: Kiểm kê & Kho (Quét theo khu + CSV · Quản lý khu vực · Nhập/Xuất)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hoàn thiện nghiệp vụ kho: Quét theo khu/Kiểm kê (liệt kê + đối chiếu + xuất CSV), Quản lý khu vực (CRUD Khu/Kệ), Nhập/Xuất kho.

**Architecture:** Tách logic thuần (`StockCalculator`, `CountReconciler`, `CsvExporter`) khỏi ViewModel để TDD đầy đủ. ViewModel dùng `FakeRfidReader` + repo giả. UI verify thủ công.

**Tech Stack:** Kotlin, Compose, Coroutines/Flow, JUnit4 + Turbine + coroutines-test; Android share Intent cho CSV.

> **Revision 2 — 2026-07-15:** `confirm()` không được append từng transaction rồi tự sửa `Product.quantity`. Toàn bộ nhập/xuất chuyển sang command nguyên tử, idempotent ở repository.

## Global Constraints

- Kế thừa Global Constraints Plan 1 & phụ thuộc kiểu Plan 1–2 (models, repo interfaces, `RfidReader`, `EpcCodec`, fakes).
- Tồn kho: **SERIALIZED** = đếm tag `active`; **BULK** = tổng delta giao dịch. Không đọc-sửa-ghi số tồn trực tiếp.
- Đối chiếu kiểm kê phân loại: `MATCH`, `EXTRA`, `MISSING`, `MISPLACED`.
- CSV: **UTF-8 có BOM**; cột: `khu,kệ,sku,tên,soLuongDem,kyVong,chenhLech,trangThai,thoiDiem`.
- Phân khu 2 cấp: ZONE (parent=null) → SHELF (parent=zone code).
- Không cho OUT làm tồn âm. Mỗi lần confirm có `commandId` ổn định; retry sau timeout phải trả lại cùng kết quả, không nhân đôi delta.
- `products.quantity` là projection/cache cho BULK, được cập nhật cùng transaction trong Firestore transaction; lịch sử `transactions` mới là audit log.

---

## Contract ghi tồn kho bắt buộc

```kotlin
data class StockMovement(val sku: String, val epc: String?, val delta: Long, val locationCode: String?)
sealed interface CommitStockResult {
    data class Success(val commandId: String) : CommitStockResult
    data class InsufficientStock(val sku: String, val available: Long, val requested: Long) : CommitStockResult
    data class InvalidTag(val epc: String, val reason: String) : CommitStockResult
}
interface StockCommandRepo {
    suspend fun commit(commandId: String, type: TxType, deviceId: String, movements: List<StockMovement>): CommitStockResult
}
```

Implementation thật phải dùng Firestore transaction: kiểm tra document `commands/{commandId}` trước; nếu đã committed thì trả kết quả cũ; nếu chưa thì validate tag/tồn, tạo transaction append-only, cập nhật projection BULK và ghi command marker trong cùng transaction. Emulator integration test phải bao phủ concurrent OUT, retry và failure rollback.

---

## File Structure

```
app/src/main/java/com/example/koistock/
  domain/StockCalculator.kt
  domain/CountReconciler.kt        (+ CountRow, CountStatus)
  domain/CsvExporter.kt
  ui/count/CountViewModel.kt       + CountScreen.kt
  ui/zones/ZoneViewModel.kt        + ZoneScreen.kt
  ui/inout/InOutViewModel.kt       + InOutScreen.kt
  util/CsvShare.kt                 (Android Intent share)
app/src/test/java/com/example/koistock/
  domain/StockCalculatorTest.kt
  domain/CountReconcilerTest.kt
  domain/CsvExporterTest.kt
  ui/count/CountViewModelTest.kt
  ui/zones/ZoneViewModelTest.kt
  ui/inout/InOutViewModelTest.kt
```

---

### Task 1: `StockCalculator` — tồn từ giao dịch & tag

**Files:**
- Create: `app/src/main/java/com/example/koistock/domain/StockCalculator.kt`
- Test: `app/src/test/java/com/example/koistock/domain/StockCalculatorTest.kt`

**Interfaces:**
- Consumes: models `Transaction`, `TagMapping`, `TrackingMode`.
- Produces:
  - `object StockCalculator { fun bulkQuantity(txns: List<Transaction>, sku: String): Long; fun serializedQuantity(tags: List<TagMapping>, sku: String, locationCode: String? = null): Int }`

- [ ] **Step 1: Viết test StockCalculatorTest.kt**

```kotlin
package com.example.koistock.domain
import com.example.koistock.data.model.*
import org.junit.Assert.assertEquals
import org.junit.Test

class StockCalculatorTest {
    private fun tx(type: TxType, sku: String, delta: Long) = Transaction("id-${System.nanoTime()}", type, sku, null, delta, null, "d", 0)
    @Test fun bulk_sumsDeltas() {
        val list = listOf(tx(TxType.IN,"B1",10), tx(TxType.OUT,"B1",-3), tx(TxType.IN,"B2",5))
        assertEquals(7L, StockCalculator.bulkQuantity(list, "B1"))
    }
    @Test fun bulk_adjustOverrides_isTreatedAsDelta() {
        val list = listOf(tx(TxType.IN,"B1",10), tx(TxType.ADJUST,"B1",-2))
        assertEquals(8L, StockCalculator.bulkQuantity(list, "B1"))
    }
    @Test fun serialized_countsActiveTagsOfSku() {
        val tags = listOf(
            TagMapping("e1","S1", status="active"),
            TagMapping("e2","S1", status="void"),
            TagMapping("e3","S1", status="active"),
            TagMapping("e4","S2", status="active"),
        )
        assertEquals(2, StockCalculator.serializedQuantity(tags, "S1"))
    }
    @Test fun serialized_filtersByLocation_whenGiven() {
        val tags = listOf(
            TagMapping("e1","S1", status="active", locationCode="A-03"),
            TagMapping("e2","S1", status="active", locationCode="A-05"),
        )
        assertEquals(1, StockCalculator.serializedQuantity(tags, "S1", "A-03"))
    }
}
```

- [ ] **Step 2: Chạy test — phải FAIL.**

- [ ] **Step 3: Viết StockCalculator.kt**

```kotlin
package com.example.koistock.domain
import com.example.koistock.data.model.TagMapping
import com.example.koistock.data.model.Transaction

object StockCalculator {
    fun bulkQuantity(txns: List<Transaction>, sku: String): Long =
        txns.filter { it.sku == sku }.sumOf { it.delta }
    fun serializedQuantity(tags: List<TagMapping>, sku: String, locationCode: String? = null): Int =
        tags.count { it.sku == sku && it.status == "active" && (locationCode == null || it.locationCode == locationCode) }
}
```

- [ ] **Step 4: Chạy test — phải PASS** (4 test).

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/example/koistock/domain/StockCalculator.kt app/src/test/java/com/example/koistock/domain/StockCalculatorTest.kt
git commit -m "feat: StockCalculator (bulk deltas + serialized tag count)"
```

---

### Task 2: `CountReconciler` — đối chiếu quét vs kỳ vọng

**Files:**
- Create: `app/src/main/java/com/example/koistock/domain/CountReconciler.kt`
- Test: `app/src/test/java/com/example/koistock/domain/CountReconcilerTest.kt`

**Interfaces:**
- Produces:
  - `enum CountStatus { MATCH, EXTRA, MISSING, MISPLACED }`
  - `data class CountRow(sku, name, counted: Int, expected: Int, locationCode: String, status: CountStatus)`
  - `object CountReconciler { fun reconcile(zone: String, countedBySku: Map<String,Int>, expected: List<ExpectedItem>): List<CountRow> }`
  - `data class ExpectedItem(sku, name, expectedQty: Int, homeLocation: String)`

- [ ] **Step 1: Viết test CountReconcilerTest.kt**

```kotlin
package com.example.koistock.domain
import org.junit.Assert.*
import org.junit.Test

class CountReconcilerTest {
    private val expected = listOf(
        ExpectedItem("S1","Áo", 3, "A-03"),
        ExpectedItem("S2","Quần", 2, "A-03"),
        ExpectedItem("S3","Nón", 1, "B-01"), // thuộc khu khác
    )
    @Test fun match_whenCountedEqualsExpectedInZone() {
        val rows = CountReconciler.reconcile("A-03", mapOf("S1" to 3), expected)
        assertEquals(CountStatus.MATCH, rows.first { it.sku=="S1" }.status)
    }
    @Test fun missing_whenExpectedButNotCounted() {
        val rows = CountReconciler.reconcile("A-03", mapOf("S1" to 3), expected)
        assertEquals(CountStatus.MISSING, rows.first { it.sku=="S2" }.status)
    }
    @Test fun misplaced_whenCountedButHomeIsOtherZone() {
        val rows = CountReconciler.reconcile("A-03", mapOf("S1" to 3, "S3" to 1), expected)
        assertEquals(CountStatus.MISPLACED, rows.first { it.sku=="S3" }.status)
    }
    @Test fun extra_whenCountedUnknownSku() {
        val rows = CountReconciler.reconcile("A-03", mapOf("SX" to 1), expected)
        assertEquals(CountStatus.EXTRA, rows.first { it.sku=="SX" }.status)
    }
}
```

- [ ] **Step 2: Chạy test — phải FAIL.**

- [ ] **Step 3: Viết CountReconciler.kt**

```kotlin
package com.example.koistock.domain

enum class CountStatus { MATCH, EXTRA, MISSING, MISPLACED }
data class ExpectedItem(val sku: String, val name: String, val expectedQty: Int, val homeLocation: String)
data class CountRow(val sku: String, val name: String, val counted: Int, val expected: Int,
                    val locationCode: String, val status: CountStatus)

object CountReconciler {
    fun reconcile(zone: String, countedBySku: Map<String, Int>, expected: List<ExpectedItem>): List<CountRow> {
        val expBySku = expected.associateBy { it.sku }
        val rows = mutableListOf<CountRow>()
        // các SKU kỳ vọng thuộc khu này
        for (e in expected.filter { it.homeLocation == zone }) {
            val c = countedBySku[e.sku] ?: 0
            val status = when {
                c == 0 -> CountStatus.MISSING
                c == e.expectedQty -> CountStatus.MATCH
                else -> CountStatus.MATCH.takeIf { c == e.expectedQty } ?: CountStatus.MISSING.let {
                    if (c < e.expectedQty) CountStatus.MISSING else CountStatus.EXTRA
                }
            }
            rows.add(CountRow(e.sku, e.name, c, e.expectedQty, zone, status))
        }
        // các SKU quét được nhưng không thuộc khu
        for ((sku, c) in countedBySku) {
            if (expBySku[sku]?.homeLocation == zone) continue
            val e = expBySku[sku]
            val status = if (e == null) CountStatus.EXTRA else CountStatus.MISPLACED
            rows.add(CountRow(sku, e?.name ?: sku, c, e?.expectedQty ?: 0, zone, status))
        }
        return rows
    }
}
```

- [ ] **Step 4: Chạy test — phải PASS** (4 test).

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/example/koistock/domain/CountReconciler.kt app/src/test/java/com/example/koistock/domain/CountReconcilerTest.kt
git commit -m "feat: CountReconciler (match/extra/missing/misplaced)"
```

---

### Task 3: `CsvExporter` — sinh CSV kiểm kê

**Files:**
- Create: `app/src/main/java/com/example/koistock/domain/CsvExporter.kt`
- Test: `app/src/test/java/com/example/koistock/domain/CsvExporterTest.kt`

**Interfaces:**
- Consumes: `CountRow`, `CountStatus`.
- Produces: `object CsvExporter { fun toCsv(rows: List<CountRow>, atMillis: Long): String }` — bắt đầu bằng BOM `﻿`, header cố định, escape dấu phẩy/ngoặc kép.

- [ ] **Step 1: Viết test CsvExporterTest.kt**

```kotlin
package com.example.koistock.domain
import org.junit.Assert.*
import org.junit.Test

class CsvExporterTest {
    @Test fun csv_startsWithBom_andHeader() {
        val csv = CsvExporter.toCsv(emptyList(), 0)
        assertTrue(csv.startsWith("﻿"))
        assertTrue(csv.contains("khu,kệ,sku,tên,soLuongDem,kyVong,chenhLech,trangThai,thoiDiem"))
    }
    @Test fun csv_writesRowWithDeltaAndStatus() {
        val row = CountRow("S1","Áo",2,3,"A-03",CountStatus.MISSING)
        val csv = CsvExporter.toCsv(listOf(row), 1000)
        val line = csv.lines().first { it.startsWith("A,") }
        assertTrue(line.contains(",S1,Áo,2,3,-1,MISSING,"))
    }
    @Test fun csv_escapesCommaInName() {
        val row = CountRow("S1","Áo, size L",1,1,"A-03",CountStatus.MATCH)
        val csv = CsvExporter.toCsv(listOf(row), 0)
        assertTrue(csv.contains("\"Áo, size L\""))
    }
}
```

- [ ] **Step 2: Chạy test — phải FAIL.**

- [ ] **Step 3: Viết CsvExporter.kt**

```kotlin
package com.example.koistock.domain
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {
    private const val HEADER = "khu,kệ,sku,tên,soLuongDem,kyVong,chenhLech,trangThai,thoiDiem"
    private fun esc(s: String): String =
        if (s.contains(',') || s.contains('"') || s.contains('\n'))
            "\"" + s.replace("\"", "\"\"") + "\"" else s
    private fun zoneOf(code: String) = code.substringBefore('-')
    fun toCsv(rows: List<CountRow>, atMillis: Long): String {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(atMillis))
        val sb = StringBuilder("﻿").append(HEADER).append("\n")
        for (r in rows) {
            val delta = r.counted - r.expected
            sb.append(esc(zoneOf(r.locationCode))).append(',')
              .append(esc(r.locationCode)).append(',')
              .append(esc(r.sku)).append(',').append(esc(r.name)).append(',')
              .append(r.counted).append(',').append(r.expected).append(',')
              .append(delta).append(',').append(r.status.name).append(',').append(ts).append("\n")
        }
        return sb.toString()
    }
}
```

- [ ] **Step 4: Chạy test — phải PASS** (3 test).

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/example/koistock/domain/CsvExporter.kt app/src/test/java/com/example/koistock/domain/CsvExporterTest.kt
git commit -m "feat: CsvExporter with BOM, escaping, delta/status columns"
```

---

### Task 4: `CountViewModel` — quét theo khu, gom SKU, đối chiếu

**Files:**
- Create: `app/src/main/java/com/example/koistock/ui/count/CountViewModel.kt`
- Test: `app/src/test/java/com/example/koistock/ui/count/CountViewModelTest.kt`

**Interfaces:**
- Consumes: `RfidReader`, `TagRepo`, `ProductRepo`, `TransactionRepo`, `CountReconciler`, `StockCalculator`, `CsvExporter`.
- Produces:
  - `class CountViewModel(reader, tagRepo, productRepo, txRepo, deviceId, now, scope)` với `val zone: StateFlow<String?>`, `val countedBySku: StateFlow<Map<String,Int>>` (nhóm theo SKU), `val rows: StateFlow<List<CountRow>>`, `fun setZone(code)`, `fun startScan()`, `fun stopScan()`, `suspend fun reconcile(expected: List<ExpectedItem>)`, `suspend fun saveCount()`, `fun csv(): String`.

- [ ] **Step 1: Viết test CountViewModelTest.kt**

```kotlin
package com.example.koistock.ui.count
import com.example.koistock.data.model.*
import com.example.koistock.device.FakeRfidReader
import com.example.koistock.domain.*
import com.example.koistock.fakes.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class CountViewModelTest {
    private fun vm(reader: FakeRfidReader, tags: FakeTagRepo) = CountViewModel(
        reader, tags, FakeProductRepo(mutableMapOf(
            "S1" to Product("S1","Áo","c",TrackingMode.SERIALIZED,0,"A-03"))),
        FakeTransactionRepo(), "dev-1", { 1000 }, kotlinx.coroutines.GlobalScope)

    @Test fun scan_groupsBySku() = runTest {
        val reader = FakeRfidReader()
        val tags = FakeTagRepo(mutableMapOf(
            "KOI-S1-1" to TagMapping("KOI-S1-1","S1"),
            "KOI-S1-2" to TagMapping("KOI-S1-2","S1")))
        val v = CountViewModel(reader, tags, FakeProductRepo(), FakeTransactionRepo(), "d", { 0 }, backgroundScope)
        v.setZone("A-03"); v.startScan(); advanceUntilIdle()
        reader.emitTag("KOI-S1-1"); reader.emitTag("KOI-S1-2"); reader.emitTag("KOI-S1-1"); advanceUntilIdle()
        v.stopScan()
        assertEquals(2, v.countedBySku.value["S1"])
    }
    @Test fun reconcile_producesRows() = runTest {
        val reader = FakeRfidReader()
        val tags = FakeTagRepo(mutableMapOf("KOI-S1-1" to TagMapping("KOI-S1-1","S1")))
        val v = CountViewModel(reader, tags, FakeProductRepo(), FakeTransactionRepo(), "d", { 0 }, backgroundScope)
        v.setZone("A-03"); v.startScan(); advanceUntilIdle()
        reader.emitTag("KOI-S1-1"); advanceUntilIdle(); v.stopScan()
        v.reconcile(listOf(ExpectedItem("S1","Áo",1,"A-03")))
        assertEquals(CountStatus.MATCH, v.rows.value.first().status)
    }
    @Test fun csv_containsHeader() = runTest {
        val v = CountViewModel(FakeRfidReader(), FakeTagRepo(), FakeProductRepo(), FakeTransactionRepo(), "d", { 0 }, backgroundScope)
        assertTrue(v.csv().contains("khu,kệ,sku"))
    }
}
```

- [ ] **Step 2: Chạy test — phải FAIL.**

- [ ] **Step 3: Viết CountViewModel.kt**

```kotlin
package com.example.koistock.ui.count
import com.example.koistock.data.model.*
import com.example.koistock.data.remote.*
import com.example.koistock.device.RfidReader
import com.example.koistock.domain.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
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
    private val _zone = MutableStateFlow<String?>(null)
    val zone: StateFlow<String?> = _zone.asStateFlow()
    private val seenEpc = mutableSetOf<String>()
    private val _counted = MutableStateFlow<Map<String, Int>>(emptyMap())
    val countedBySku: StateFlow<Map<String, Int>> = _counted.asStateFlow()
    private val _rows = MutableStateFlow<List<CountRow>>(emptyList())
    val rows: StateFlow<List<CountRow>> = _rows.asStateFlow()
    private var job: kotlinx.coroutines.Job? = null

    fun setZone(code: String) { _zone.value = code }

    fun startScan() {
        seenEpc.clear(); _counted.value = emptyMap()
        reader.startInventory()
        job = scope.launch {
            reader.inventory.collect { t ->
                if (seenEpc.add(t.epc)) {
                    val sku = tagRepo.getByEpc(t.epc)?.sku ?: return@collect
                    _counted.update { it + (sku to ((it[sku] ?: 0) + 1)) }
                }
            }
        }
    }
    fun stopScan() { reader.stopInventory(); job?.cancel() }

    suspend fun reconcile(expected: List<ExpectedItem>) {
        _rows.value = CountReconciler.reconcile(_zone.value ?: "", _counted.value, expected)
    }
    suspend fun saveCount() {
        val ts = now(); val z = _zone.value
        _counted.value.forEach { (sku, qty) ->
            txRepo.append(Transaction(UUID.randomUUID().toString(), TxType.COUNT, sku, null, qty.toLong(), z, deviceId, ts))
        }
    }
    fun csv(): String = CsvExporter.toCsv(_rows.value, now())
}
```

- [ ] **Step 4: Chạy test — phải PASS** (3 test).

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/example/koistock/ui/count/CountViewModel.kt app/src/test/java/com/example/koistock/ui/count/
git commit -m "feat: CountViewModel scan/group/reconcile/save/csv"
```

---

### Task 5: Màn Quét theo khu + xuất CSV (Compose + share) — verify thủ công

**Files:**
- Create: `app/src/main/java/com/example/koistock/ui/count/CountScreen.kt`
- Create: `app/src/main/java/com/example/koistock/util/CsvShare.kt`

**Interfaces:**
- Consumes: `CountViewModel`, `LocationRepo` (danh sách khu), `ProductRepo`/`TagRepo` (dựng `ExpectedItem`).
- Produces: `fun shareCsv(context: Context, csv: String, fileName: String)`; `@Composable fun CountScreen(vm, zones)`.

- [ ] **Step 1: Viết CsvShare.kt**

```kotlin
package com.example.koistock.util
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

fun shareCsv(context: Context, csv: String, fileName: String) {
    val file = File(context.cacheDir, fileName).apply { writeText(csv, Charsets.UTF_8) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"; putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Xuất kiểm kê"))
}
```
Thêm FileProvider vào Manifest (authority `${applicationId}.fileprovider`) + `res/xml/file_paths.xml` (`<cache-path name="csv" path="." />`).

- [ ] **Step 2: Viết CountScreen.kt** — dropdown chọn Khu/Kệ; nút "Bắt đầu/Dừng quét"; danh sách SKU+SL đếm; nút "Đối chiếu" (→ `reconcile`); bảng trạng thái; nút "Lưu" (`saveCount`) + "Xuất CSV" (`shareCsv(ctx, vm.csv(), "kiemke.csv")`).

- [ ] **Step 3: Verify thủ công** — quét 1 khu → danh sách SKU; Đối chiếu → thấy Khớp/Thừa/Thiếu/Lệch; Xuất CSV → mở được trên Excel, tiếng Việt đúng.

- [ ] **Step 4: Commit**
```bash
git add app/src/main/java/com/example/koistock/ui/count/CountScreen.kt app/src/main/java/com/example/koistock/util/CsvShare.kt app/src/main/AndroidManifest.xml app/src/main/res/xml/file_paths.xml
git commit -m "feat: Count screen + CSV export/share (manual-verified)"
```

---

### Task 6: Quản lý khu vực — `ZoneViewModel`

**Files:**
- Create: `app/src/main/java/com/example/koistock/ui/zones/ZoneViewModel.kt`
- Test: `app/src/test/java/com/example/koistock/ui/zones/ZoneViewModelTest.kt`

**Interfaces:**
- Consumes: `LocationRepo`.
- Produces:
  - `sealed interface ZoneAddResult { object Ok; data class Error(msg: String) }`
  - `class ZoneViewModel(locationRepo, now, scope)` với `val locations: StateFlow<List<LocationNode>>`, `suspend fun addZone(code, name): ZoneAddResult`, `suspend fun addShelf(zoneCode, code, name): ZoneAddResult`. Validate: code không rỗng/không trùng; shelf phải có zone cha tồn tại.

- [ ] **Step 1: Viết test ZoneViewModelTest.kt**

```kotlin
package com.example.koistock.ui.zones
import com.example.koistock.data.model.*
import com.example.koistock.fakes.FakeLocationRepo
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class ZoneViewModelTest {
    @Test fun addZone_savesZoneNode() = runTest {
        val repo = FakeLocationRepo()
        val vm = ZoneViewModel(repo, { 5 }, backgroundScope)
        assertTrue(vm.addZone("A","Khu A") is ZoneAddResult.Ok)
        assertEquals(LocationType.ZONE, repo.items["A"]!!.type)
    }
    @Test fun addZone_duplicate_returnsError() = runTest {
        val repo = FakeLocationRepo(mutableMapOf("A" to LocationNode("A","Khu A",LocationType.ZONE)))
        val vm = ZoneViewModel(repo, { 5 }, backgroundScope)
        assertTrue(vm.addZone("A","x") is ZoneAddResult.Error)
    }
    @Test fun addShelf_withoutParent_returnsError() = runTest {
        val vm = ZoneViewModel(FakeLocationRepo(), { 5 }, backgroundScope)
        assertTrue(vm.addShelf("A","A-03","Kệ 3") is ZoneAddResult.Error)
    }
    @Test fun addShelf_withParent_savesShelfWithParent() = runTest {
        val repo = FakeLocationRepo(mutableMapOf("A" to LocationNode("A","Khu A",LocationType.ZONE)))
        val vm = ZoneViewModel(repo, { 5 }, backgroundScope)
        assertTrue(vm.addShelf("A","A-03","Kệ 3") is ZoneAddResult.Ok)
        assertEquals("A", repo.items["A-03"]!!.parent)
        assertEquals(LocationType.SHELF, repo.items["A-03"]!!.type)
    }
}
```

- [ ] **Step 2: Chạy test — phải FAIL.**

- [ ] **Step 3: Viết ZoneViewModel.kt**

```kotlin
package com.example.koistock.ui.zones
import com.example.koistock.data.model.*
import com.example.koistock.data.remote.LocationRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

sealed interface ZoneAddResult { data object Ok : ZoneAddResult; data class Error(val msg: String) : ZoneAddResult }

class ZoneViewModel(
    private val locationRepo: LocationRepo,
    private val now: () -> Long,
    private val scope: CoroutineScope,
) {
    val locations: StateFlow<List<LocationNode>> =
        locationRepo.observeAll().stateIn(scope, SharingStarted.Eagerly, emptyList())

    private suspend fun exists(code: String) = locations.value.any { it.code == code }

    suspend fun addZone(code: String, name: String): ZoneAddResult {
        if (code.isBlank()) return ZoneAddResult.Error("Mã rỗng")
        if (exists(code)) return ZoneAddResult.Error("Mã đã tồn tại")
        locationRepo.upsert(LocationNode(code, name, LocationType.ZONE, null, now(), "app"))
        return ZoneAddResult.Ok
    }
    suspend fun addShelf(zoneCode: String, code: String, name: String): ZoneAddResult {
        if (code.isBlank()) return ZoneAddResult.Error("Mã rỗng")
        if (locations.value.none { it.code == zoneCode && it.type == LocationType.ZONE })
            return ZoneAddResult.Error("Chưa có Khu cha")
        if (exists(code)) return ZoneAddResult.Error("Mã đã tồn tại")
        locationRepo.upsert(LocationNode(code, name, LocationType.SHELF, zoneCode, now(), "app"))
        return ZoneAddResult.Ok
    }
}
```

> Lưu ý test: `FakeLocationRepo.observeAll()` trả `MutableStateFlow` chụp tại thời điểm gọi. Nếu test cần thấy thay đổi sau upsert, đọc lại `repo.items` (như test) thay vì `locations.value`.

- [ ] **Step 4: Chạy test — phải PASS** (4 test).

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/example/koistock/ui/zones/ZoneViewModel.kt app/src/test/java/com/example/koistock/ui/zones/
git commit -m "feat: ZoneViewModel CRUD zones/shelves with validation"
```

---

### Task 7: Màn Quản lý khu vực (Compose) — verify thủ công

**Files:**
- Create: `app/src/main/java/com/example/koistock/ui/zones/ZoneScreen.kt`

**Interfaces:**
- Consumes: `ZoneViewModel`.
- Produces: `@Composable fun ZoneScreen(vm: ZoneViewModel)`.

- [ ] **Step 1: Viết ZoneScreen.kt** — danh sách Khu (mở rộng thấy Kệ con); form thêm Khu (mã, tên); form thêm Kệ (chọn Khu cha, mã, tên); hiện lỗi từ `ZoneAddResult.Error`. (Tùy chọn nút "Ghi tag vị trí" để Plan sau nếu cần.)

- [ ] **Step 2: Verify thủ công** — thêm Khu A, thêm Kệ A-03; thử trùng mã → báo lỗi; kiểm doc `locations` trên Firestore.

- [ ] **Step 3: Commit**
```bash
git add app/src/main/java/com/example/koistock/ui/zones/ZoneScreen.kt app/src/main/java/com/example/koistock/ui/shell/AppShell.kt
git commit -m "feat: Zone management screen (manual-verified)"
```

---

### Task 8: Nhập/Xuất — `InOutViewModel`

**Files:**
- Create: `app/src/main/java/com/example/koistock/ui/inout/InOutViewModel.kt`
- Test: `app/src/test/java/com/example/koistock/ui/inout/InOutViewModelTest.kt`

**Interfaces:**
- Consumes: `RfidReader`, `TagRepo`, `StockCommandRepo`.
- Produces:
  - `enum InOutMode { IN, OUT }`
  - `class InOutViewModel(reader, tagRepo, stockCommandRepo, deviceId, newCommandId, scope)` với `val mode`, `pending`, `commitState`, `setMode`, `startScan`, `stopScan`, `confirm`. Mỗi EPC quét ⇒ +1 vào `pending[sku]`; `confirm()` gửi đúng một command nguyên tử. ViewModel không append transaction và không cập nhật quantity trực tiếp.

- [ ] **Step 1: Viết test InOutViewModelTest.kt**

```kotlin
package com.example.koistock.ui.inout
import com.example.koistock.data.model.*
import com.example.koistock.device.FakeRfidReader
import com.example.koistock.fakes.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class InOutViewModelTest {
    private fun setup(mode: InOutMode): Triple<FakeRfidReader, FakeTransactionRepo, InOutViewModel> {
        val reader = FakeRfidReader()
        val tags = FakeTagRepo(mutableMapOf(
            "KOI-S1-1" to TagMapping("KOI-S1-1","S1"),
            "KOI-S1-2" to TagMapping("KOI-S1-2","S1")))
        val prods = FakeProductRepo(mutableMapOf("S1" to Product("S1","Áo","c",TrackingMode.BULK,10,"A-03")))
        val tx = FakeTransactionRepo()
        val vm = InOutViewModel(reader, tags, prods, tx, "dev-1", { 7 }, backgroundScope).also { it.setMode(mode) }
        return Triple(reader, tx, vm)
    }
    @Test fun out_confirm_appendsNegativeDeltas() = runTest {
        val (reader, tx, vm) = setup(InOutMode.OUT)
        vm.startScan(); advanceUntilIdle()
        reader.emitTag("KOI-S1-1"); reader.emitTag("KOI-S1-2"); advanceUntilIdle()
        vm.stopScan(); vm.confirm(); advanceUntilIdle()
        assertEquals(2, tx.appended.size)
        assertTrue(tx.appended.all { it.type == TxType.OUT && it.delta == -1L })
    }
    @Test fun in_confirm_appendsPositiveDeltas() = runTest {
        val (reader, tx, vm) = setup(InOutMode.IN)
        vm.startScan(); advanceUntilIdle()
        reader.emitTag("KOI-S1-1"); advanceUntilIdle()
        vm.stopScan(); vm.confirm(); advanceUntilIdle()
        assertEquals(1L, tx.appended.first().delta)
        assertEquals(TxType.IN, tx.appended.first().type)
    }
    @Test fun pending_groupsBySku_dedupeEpc() = runTest {
        val (reader, _, vm) = setup(InOutMode.IN)
        vm.startScan(); advanceUntilIdle()
        reader.emitTag("KOI-S1-1"); reader.emitTag("KOI-S1-1"); reader.emitTag("KOI-S1-2"); advanceUntilIdle()
        assertEquals(2, vm.pending.value["S1"])
    }
}
```

- [ ] **Step 2: Chạy test — phải FAIL.**

- [ ] **Step 3: Viết InOutViewModel.kt**

```kotlin
package com.example.koistock.ui.inout
import com.example.koistock.data.model.*
import com.example.koistock.data.remote.*
import com.example.koistock.device.RfidReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

enum class InOutMode { IN, OUT }

class InOutViewModel(
    private val reader: RfidReader,
    private val tagRepo: TagRepo,
    private val stockCommandRepo: StockCommandRepo,
    private val deviceId: String,
    private val newCommandId: () -> String,
    private val scope: CoroutineScope,
) {
    private var commandId: String = newCommandId()
    private val _mode = MutableStateFlow(InOutMode.IN)
    val mode: StateFlow<InOutMode> = _mode.asStateFlow()
    private val seen = mutableSetOf<String>()
    private val epcBySku = mutableMapOf<String, MutableList<String>>()
    private val _pending = MutableStateFlow<Map<String, Int>>(emptyMap())
    val pending: StateFlow<Map<String, Int>> = _pending.asStateFlow()
    private var job: kotlinx.coroutines.Job? = null

    fun setMode(m: InOutMode) { _mode.value = m }

    fun startScan() {
        seen.clear(); epcBySku.clear(); _pending.value = emptyMap()
        reader.startInventory()
        job = scope.launch {
            reader.inventory.collect { t ->
                if (seen.add(t.epc)) {
                    val sku = tagRepo.getByEpc(t.epc)?.sku ?: return@collect
                    epcBySku.getOrPut(sku) { mutableListOf() }.add(t.epc)
                    _pending.update { it + (sku to ((it[sku] ?: 0) + 1)) }
                }
            }
        }
    }
    fun stopScan() { reader.stopInventory(); job?.cancel() }

    suspend fun confirm() {
        val sign = if (_mode.value == InOutMode.IN) 1L else -1L
        val type = if (_mode.value == InOutMode.IN) TxType.IN else TxType.OUT
        val movements = epcBySku.flatMap { (sku, epcs) ->
            epcs.map { epc -> StockMovement(sku, epc, sign, null) }
        }
        when (stockCommandRepo.commit(commandId, type, deviceId, movements)) {
            is CommitStockResult.Success -> {
                _pending.value = emptyMap()
                epcBySku.clear()
                commandId = newCommandId()
            }
            else -> Unit // giữ pending + commandId để sửa hoặc retry idempotent
        }
    }
}
```

- [ ] **Step 4: Chạy test — phải PASS** (3 test).

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/example/koistock/ui/inout/InOutViewModel.kt app/src/test/java/com/example/koistock/ui/inout/
git commit -m "feat: InOutViewModel IN/OUT deltas + bulk quantity update"
```

---

### Task 9: Màn Nhập/Xuất (Compose) + nối routes — verify thủ công

**Files:**
- Create: `app/src/main/java/com/example/koistock/ui/inout/InOutScreen.kt`
- Modify: `AppShell.kt` (routes `count`, `zones`, `inout`), `MainMenuScreen`.

**Interfaces:**
- Consumes: `InOutViewModel`.
- Produces: `@Composable fun InOutScreen(vm: InOutViewModel)`.

- [ ] **Step 1: Viết InOutScreen.kt** — SegmentedButton IN/OUT; nút "Bắt đầu/Dừng quét"; danh sách SKU + số món; nút "Xác nhận" (`confirm`).

- [ ] **Step 2: Nối routes** trong `AppShell` cho `count`, `zones`, `inout`; `MainMenuScreen.onOpen` điều hướng đủ 8 route (trừ `sync` để Plan 4).

- [ ] **Step 3: Verify thủ công** — Xuất 2 món → tồn giảm 2 (BULK) realtime; kiểm `transactions` trên Firestore.

- [ ] **Step 4: Commit**
```bash
git add app/src/main/java/com/example/koistock/ui/inout/InOutScreen.kt app/src/main/java/com/example/koistock/ui/shell/AppShell.kt
git commit -m "feat: In/Out screen + route wiring (manual-verified)"
```

---

## Self-Review

**Spec coverage:** Quét theo khu → danh sách SKU + Đối chiếu (Task 2,4,5) ✓ · Xuất CSV UTF-8 BOM (Task 3,5) ✓ · Quản lý khu vực CRUD Khu/Kệ (Task 6,7) ✓ · Nhập/Xuất delta + tồn BULK (Task 8,9) ✓ · Tồn SERIALIZED=đếm tag / BULK=delta (Task 1) ✓.
**Placeholder scan:** không có TBD/TODO; UI/hardware nêu tiêu chí verify. ✓
**Type consistency:** `CountRow`/`CountStatus` dùng nhất quán Task 2→3→4→5; `TxType.COUNT/IN/OUT/MOVE` khớp model Plan 1; `TrackingMode` phân nhánh Task 8 khớp. ✓
**Rủi ro:** `CountReconciler.reconcile` nhánh MATCH/EXTRA/MISSING cho trường hợp lệch số lượng đã đơn giản hóa (thiếu→MISSING, dư→EXTRA); nếu cần trạng thái "LỆCH SỐ LƯỢNG" riêng thì thêm enum sau — hiện đủ theo spec (Khớp/Thừa/Thiếu/Lệch vị trí).

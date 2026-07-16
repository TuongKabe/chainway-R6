# KOIStock — Plan 2: Nghiệp vụ RFID lõi (Tra cứu · Tìm · Gán tag · Đặt vị trí)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Xây 4 màn nghiệp vụ đầu tiên trên nền Plan 1: Tra cứu hàng, Tìm sản phẩm (định vị Geiger), Gán tag cho hàng mới, Đặt vị trí theo khu.

**Architecture:** Mỗi màn = 1 ViewModel (logic, test bằng `FakeRfidReader` + repository giả) + 1 Composable (UI, verify thủ công). Mã EPC xử lý qua `EpcCodec` thuần logic (TDD đầy đủ).

**Tech Stack:** Kotlin, Compose, Coroutines/Flow, JUnit4 + Turbine + coroutines-test.

> **Revision 2 — 2026-07-15:** Các workflow ghi dữ liệu phải có command id, rollback/recovery rõ ràng và không được để tag vật lý lệch mapping Firestore.

## Global Constraints

- Kế thừa toàn bộ Global Constraints của Plan 1 (Kotlin/Compose, minSdk 24, SDK bọc sau `RfidReader`, Firestore nguồn sự thật, hybrid `trackingMode`, Khu→Kệ, không đăng nhập).
- EPC có cấu trúc: `KOI-<sku>-<serial>`; mặt nạ lọc theo SKU: `KOI-<sku>-` (prefix).
- Tất cả repository/`RfidReader` được **inject** vào ViewModel (không tạo bên trong) để test.
- Phụ thuộc kiểu từ Plan 1: `RfidReader`, `ScannedTag`, `ConnectionState`, models `Product/TagMapping/Transaction/LocationNode/TrackingMode/TxType/LocationType`, các repository.
- Test đơn vị: `./gradlew testDebugUnitTest`.
- Gán EPC là một workflow có trạng thái (`SCANNED → WRITTEN → MAPPED`), không giả định write tag và Firestore có thể atomic với nhau.
- Khi đổi EPC: dùng EPC cũ làm selection mask, verify đọc lại EPC mới, tạo mapping mới và đánh dấu mapping cũ `void`; không để hai mapping `active` cho cùng một đơn vị.
- Mỗi lệnh gán/đặt vị trí có `commandId` để retry không tạo bản ghi trùng.

---

## Acceptance bổ sung trước khi hoàn thành Plan 2

- [ ] Test `AssignTag`: write thất bại → không ghi mapping; write thành công nhưng Firestore lỗi → hiện trạng thái cần phục hồi và retry cùng `commandId`; retry không tạo mapping kép.
- [ ] Test EPC collision: từ chối nếu `finalEpc` đang active cho SKU/serial khác.
- [ ] Test serialized invariant: một EPC active thuộc tối đa một SKU; một `unitSerial` active có tối đa một EPC.
- [ ] `Putaway` ghi thay đổi vị trí và MOVE transaction trong cùng repository command; không cập nhật từng tag rời rạc từ ViewModel.
- [ ] Hardware verify bắt buộc tag write khi có nhiều tag gần đầu đọc: selection mask chỉ cho phép đúng `oldEpc` bị thay đổi.

---

## File Structure

```
app/src/main/java/com/example/koistock/
  domain/EpcCodec.kt
  ui/lookup/LookupViewModel.kt         + LookupScreen.kt
  ui/locate/LocateViewModel.kt         + LocateScreen.kt   (+ BeepCadence trong LocateViewModel.kt)
  ui/assign/AssignTagViewModel.kt      + AssignTagScreen.kt
  ui/putaway/PutawayViewModel.kt       + PutawayScreen.kt
app/src/test/java/com/example/koistock/
  domain/EpcCodecTest.kt
  ui/lookup/LookupViewModelTest.kt
  ui/locate/LocateViewModelTest.kt
  ui/assign/AssignTagViewModelTest.kt
  ui/putaway/PutawayViewModelTest.kt
  fakes/FakeRepositories.kt            # ProductRepo/TagRepo/... giả cho test
```

> **Ghi chú test:** repository trong Plan 1 là lớp cụ thể bọc Firestore. Để test ViewModel, ở Task 1 tạo **interface** cho từng repo và cho lớp Firestore hiện thực nó; các fake hiện thực cùng interface. (Refactor nhỏ, không đổi hành vi.)

---

### Task 1: Tách interface repository + fakes + EpcCodec

**Files:**
- Modify: `app/src/main/java/com/example/koistock/data/remote/ProductRepository.kt` (thêm interface `ProductRepo`)
- Modify: `TagRepository.kt`, `LocationRepository.kt`, `TransactionRepository.kt` (thêm interface tương ứng)
- Create: `app/src/main/java/com/example/koistock/domain/EpcCodec.kt`
- Create: `app/src/test/java/com/example/koistock/fakes/FakeRepositories.kt`
- Test: `app/src/test/java/com/example/koistock/domain/EpcCodecTest.kt`

**Interfaces:**
- Produces:
  - `interface ProductRepo { suspend fun getBySku(sku): Product?; fun observeAll(): Flow<List<Product>>; suspend fun upsert(p: Product) }` (và `ProductRepository : ProductRepo`)
  - `interface TagRepo { suspend fun getByEpc(epc): TagMapping?; suspend fun upsert(t: TagMapping); suspend fun listBySku(sku): List<TagMapping> }`
  - `interface LocationRepo { fun observeAll(): Flow<List<LocationNode>>; suspend fun upsert(l: LocationNode) }`
  - `interface TransactionRepo { suspend fun append(t: Transaction) }`
  - `object EpcCodec { fun encode(sku, serial): String; fun skuOf(epc): String?; fun maskForSku(sku): String; fun isStructured(epc): Boolean }`
  - Fakes: `FakeProductRepo`, `FakeTagRepo`, `FakeLocationRepo`, `FakeTransactionRepo` (lưu trong `MutableMap`/`MutableList`, expose để assert).

- [ ] **Step 1: Thêm interface cho mỗi repo + cho lớp cụ thể implement**

Ví dụ `TagRepository.kt`:
```kotlin
interface TagRepo {
    suspend fun getByEpc(epc: String): TagMapping?
    suspend fun upsert(t: TagMapping)
    suspend fun listBySku(sku: String): List<TagMapping>
}
class TagRepository(private val db: FirebaseFirestore) : TagRepo {
    private val col get() = db.collection("tags")
    override suspend fun getByEpc(epc: String) =
        col.document(epc).get().await().let { if (it.exists()) TagMapping.fromMap(epc, it.data!!) else null }
    override suspend fun upsert(t: TagMapping) { col.document(t.epc).set(t.toMap()).await() }
    override suspend fun listBySku(sku: String): List<TagMapping> =
        col.whereEqualTo("sku", sku).get().await().documents.mapNotNull { d -> d.data?.let { TagMapping.fromMap(d.id, it) } }
}
```
Làm tương tự: `ProductRepo`/`ProductRepository`, `LocationRepo`/`LocationRepository`, `TransactionRepo`/`TransactionRepository`.

- [ ] **Step 2: Viết EpcCodec.kt**

```kotlin
package com.example.koistock.domain

object EpcCodec {
    private const val PREFIX = "KOI"
    private const val SEP = "-"
    fun encode(sku: String, serial: String): String = "$PREFIX$SEP$sku$SEP$serial"
    fun maskForSku(sku: String): String = "$PREFIX$SEP$sku$SEP"
    fun isStructured(epc: String): Boolean = epc.startsWith("$PREFIX$SEP") && epc.count { it == '-' } >= 2
    fun skuOf(epc: String): String? {
        if (!isStructured(epc)) return null
        val parts = epc.split(SEP)
        // KOI - <sku có thể chứa gì đó không phải '-'> - <serial>
        return if (parts.size >= 3) parts.subList(1, parts.size - 1).joinToString(SEP) else null
    }
}
```

- [ ] **Step 3: Viết EpcCodecTest.kt**

```kotlin
package com.example.koistock.domain
import org.junit.Assert.*
import org.junit.Test
class EpcCodecTest {
    @Test fun encode_thenSkuOf_roundTrips() {
        val epc = EpcCodec.encode("SKU1", "0001")
        assertEquals("KOI-SKU1-0001", epc)
        assertEquals("SKU1", EpcCodec.skuOf(epc))
    }
    @Test fun mask_isPrefixForSku() {
        assertEquals("KOI-SKU1-", EpcCodec.maskForSku("SKU1"))
        assertTrue(EpcCodec.encode("SKU1", "9").startsWith(EpcCodec.maskForSku("SKU1")))
    }
    @Test fun rawEpc_isNotStructured() {
        assertFalse(EpcCodec.isStructured("E20000123456"))
        assertNull(EpcCodec.skuOf("E20000123456"))
    }
}
```

- [ ] **Step 4: Viết FakeRepositories.kt**

```kotlin
package com.example.koistock.fakes
import com.example.koistock.data.model.*
import com.example.koistock.data.remote.*
import kotlinx.coroutines.flow.*

class FakeProductRepo(val items: MutableMap<String, Product> = mutableMapOf()) : ProductRepo {
    override suspend fun getBySku(sku: String) = items[sku]
    override fun observeAll(): Flow<List<Product>> = MutableStateFlow(items.values.toList())
    override suspend fun upsert(p: Product) { items[p.sku] = p }
}
class FakeTagRepo(val items: MutableMap<String, TagMapping> = mutableMapOf()) : TagRepo {
    override suspend fun getByEpc(epc: String) = items[epc]
    override suspend fun upsert(t: TagMapping) { items[t.epc] = t }
    override suspend fun listBySku(sku: String) = items.values.filter { it.sku == sku }
}
class FakeLocationRepo(val items: MutableMap<String, LocationNode> = mutableMapOf()) : LocationRepo {
    override fun observeAll(): Flow<List<LocationNode>> = MutableStateFlow(items.values.toList())
    override suspend fun upsert(l: LocationNode) { items[l.code] = l }
}
class FakeTransactionRepo(val appended: MutableList<Transaction> = mutableListOf()) : TransactionRepo {
    override suspend fun append(t: Transaction) { appended.add(t) }
}
```

- [ ] **Step 5: Chạy test — phải PASS**

Run: `./gradlew testDebugUnitTest --tests "com.example.koistock.domain.EpcCodecTest"`
Expected: PASS (3 test). Đồng thời `./gradlew :app:compileDebugKotlin` xanh (interface refactor không lỗi).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/koistock/data/remote/ app/src/main/java/com/example/koistock/domain/EpcCodec.kt app/src/test/java/com/example/koistock/domain/ app/src/test/java/com/example/koistock/fakes/
git commit -m "refactor: repo interfaces + fakes; feat: EpcCodec"
```

---

### Task 2: Tra cứu — `LookupViewModel`

**Files:**
- Create: `app/src/main/java/com/example/koistock/ui/lookup/LookupViewModel.kt`
- Test: `app/src/test/java/com/example/koistock/ui/lookup/LookupViewModelTest.kt`

**Interfaces:**
- Consumes: `RfidReader`, `TagRepo`, `ProductRepo`, `EpcCodec`.
- Produces:
  - `sealed interface LookupResult { object Idle; data class Found(product: Product, tag: TagMapping); data class UnknownTag(epc: String); object NotFound }`
  - `class LookupViewModel(reader, tagRepo, productRepo, scope)` với `val result: StateFlow<LookupResult>`, `fun scanOnce()`.

- [ ] **Step 1: Viết test LookupViewModelTest.kt**

```kotlin
package com.example.koistock.ui.lookup
import com.example.koistock.data.model.*
import com.example.koistock.device.FakeRfidReader
import com.example.koistock.device.ScannedTag
import com.example.koistock.fakes.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class LookupViewModelTest {
    @Test fun scan_knownTag_returnsFound() = runTest {
        val reader = FakeRfidReader().apply { scannedSingle = ScannedTag("KOI-SKU1-1", -40) }
        val tags = FakeTagRepo(mutableMapOf("KOI-SKU1-1" to TagMapping("KOI-SKU1-1","SKU1")))
        val prods = FakeProductRepo(mutableMapOf("SKU1" to Product("SKU1","Cá KOI","con",TrackingMode.SERIALIZED,3,"A-03")))
        val vm = LookupViewModel(reader, tags, prods, backgroundScope)
        vm.scanOnce(); advanceUntilIdle()
        val r = vm.result.value
        assertTrue(r is LookupResult.Found)
        assertEquals("Cá KOI", (r as LookupResult.Found).product.name)
    }
    @Test fun scan_unmappedTag_returnsUnknown() = runTest {
        val reader = FakeRfidReader().apply { scannedSingle = ScannedTag("E2000999", -40) }
        val vm = LookupViewModel(reader, FakeTagRepo(), FakeProductRepo(), backgroundScope)
        vm.scanOnce(); advanceUntilIdle()
        assertTrue(vm.result.value is LookupResult.UnknownTag)
    }
    @Test fun scan_noTag_returnsNotFound() = runTest {
        val reader = FakeRfidReader().apply { scannedSingle = null }
        val vm = LookupViewModel(reader, FakeTagRepo(), FakeProductRepo(), backgroundScope)
        vm.scanOnce(); advanceUntilIdle()
        assertEquals(LookupResult.NotFound, vm.result.value)
    }
}
```

- [ ] **Step 2: Chạy test — phải FAIL** (`./gradlew testDebugUnitTest --tests "*LookupViewModelTest"`, lỗi Unresolved reference).

- [ ] **Step 3: Viết LookupViewModel.kt**

```kotlin
package com.example.koistock.ui.lookup
import com.example.koistock.data.model.*
import com.example.koistock.data.remote.*
import com.example.koistock.device.RfidReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface LookupResult {
    data object Idle : LookupResult
    data class Found(val product: Product, val tag: TagMapping) : LookupResult
    data class UnknownTag(val epc: String) : LookupResult
    data object NotFound : LookupResult
}

class LookupViewModel(
    private val reader: RfidReader,
    private val tagRepo: TagRepo,
    private val productRepo: ProductRepo,
    private val scope: CoroutineScope,
) {
    private val _result = MutableStateFlow<LookupResult>(LookupResult.Idle)
    val result: StateFlow<LookupResult> = _result.asStateFlow()

    fun scanOnce() = scope.launch {
        val tag = reader.scanSingle()
        if (tag == null) { _result.value = LookupResult.NotFound; return@launch }
        val mapping = tagRepo.getByEpc(tag.epc)
        if (mapping == null) { _result.value = LookupResult.UnknownTag(tag.epc); return@launch }
        val product = productRepo.getBySku(mapping.sku)
        _result.value = if (product != null) LookupResult.Found(product, mapping) else LookupResult.UnknownTag(tag.epc)
        reader.beep()
    }
}
```

- [ ] **Step 4: Chạy test — phải PASS** (3 test).

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/example/koistock/ui/lookup/LookupViewModel.kt app/src/test/java/com/example/koistock/ui/lookup/
git commit -m "feat: LookupViewModel resolves tag to product"
```

---

### Task 3: Màn Tra cứu (Compose) — verify thủ công

**Files:**
- Create: `app/src/main/java/com/example/koistock/ui/lookup/LookupScreen.kt`
- Modify: `AppShell.kt` (route `lookup`)

**Interfaces:**
- Consumes: `LookupViewModel`, `LookupResult`.
- Produces: `@Composable fun LookupScreen(vm: LookupViewModel, onAssign: (String) -> Unit)`.

- [ ] **Step 1: Viết LookupScreen.kt**

```kotlin
package com.example.koistock.ui.lookup
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LookupScreen(vm: LookupViewModel, onAssign: (String) -> Unit) {
    val r by vm.result.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Tra cứu hàng hóa", style = MaterialTheme.typography.headlineSmall)
        Button(onClick = { vm.scanOnce() }, modifier = Modifier.fillMaxWidth()) { Text("Quét (hoặc bấm cò R6)") }
        when (val s = r) {
            is LookupResult.Found -> {
                Text(s.product.name, style = MaterialTheme.typography.titleLarge)
                Text("SKU: ${s.product.sku} · ĐVT: ${s.product.unit}")
                Text("Tồn: ${s.product.quantity} · Vị trí: ${s.product.locationCode}")
            }
            is LookupResult.UnknownTag -> {
                Text("Tag lạ: ${s.epc}")
                Button(onClick = { onAssign(s.epc) }) { Text("Gán tag này") }
            }
            LookupResult.NotFound -> Text("Không đọc được tag.")
            LookupResult.Idle -> Text("Chưa quét.")
        }
    }
}
```

- [ ] **Step 2: Nối route trong AppShell** — thêm `composable("lookup") { LookupScreen(lookupVm) { epc -> nav.navigate("assign?epc=$epc") } }` và mở từ `MainMenuScreen`.

- [ ] **Step 3: Verify thủ công** — quét tag đã map → hiện tên/tồn/vị trí; tag lạ → nút "Gán tag này".

- [ ] **Step 4: Commit**
```bash
git add app/src/main/java/com/example/koistock/ui/lookup/LookupScreen.kt app/src/main/java/com/example/koistock/ui/shell/AppShell.kt
git commit -m "feat: Lookup screen (manual-verified)"
```

---

### Task 4: Tìm sản phẩm — `LocateViewModel` (nhịp bíp từ tín hiệu)

**Files:**
- Create: `app/src/main/java/com/example/koistock/ui/locate/LocateViewModel.kt`
- Test: `app/src/test/java/com/example/koistock/ui/locate/LocateViewModelTest.kt`

**Interfaces:**
- Consumes: `RfidReader` (`locateSignal`, `startLocate`, `stopLocate`), `EpcCodec`.
- Produces:
  - `object BeepCadence { fun intervalMs(signal0to100: Int): Long }` — map 0→bíp thưa (vd 1200ms), 100→tít liên tục (vd 60ms).
  - `class LocateViewModel(reader, scope)` với `val signal: StateFlow<Int>`, `val intervalMs: StateFlow<Long>`, `fun start(targetEpc: String)`, `fun startForSku(sku: String)`, `fun stop()`.

- [ ] **Step 1: Viết test LocateViewModelTest.kt**

```kotlin
package com.example.koistock.ui.locate
import com.example.koistock.device.FakeRfidReader
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class LocateViewModelTest {
    @Test fun cadence_isMonotonic_closerMeansShorter() {
        val far = BeepCadence.intervalMs(0)
        val mid = BeepCadence.intervalMs(50)
        val near = BeepCadence.intervalMs(100)
        assertTrue(far > mid && mid > near)
        assertTrue(near <= 100)   // sát tag: gần như liên tục
        assertTrue(far >= 1000)   // xa: bíp thưa
    }
    @Test fun signal_updatesFromReader() = runTest {
        val reader = FakeRfidReader()
        val vm = LocateViewModel(reader, backgroundScope)
        vm.start("KOI-SKU1-1"); advanceUntilIdle()
        reader.emitLocate(80); advanceUntilIdle()
        assertEquals(80, vm.signal.value)
        assertEquals(BeepCadence.intervalMs(80), vm.intervalMs.value)
    }
    @Test fun startForSku_usesMaskTarget() = runTest {
        val reader = FakeRfidReader()
        val vm = LocateViewModel(reader, backgroundScope)
        vm.startForSku("SKU1"); advanceUntilIdle()
        assertEquals("KOI-SKU1-", reader.locateTarget)
    }
}
```

- [ ] **Step 2: Chạy test — phải FAIL.**

- [ ] **Step 3: Viết LocateViewModel.kt**

```kotlin
package com.example.koistock.ui.locate
import com.example.koistock.device.RfidReader
import com.example.koistock.domain.EpcCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

object BeepCadence {
    // 0 -> 1200ms (thưa), 100 -> 60ms (liên tục); nội suy tuyến tính
    fun intervalMs(signal0to100: Int): Long {
        val s = signal0to100.coerceIn(0, 100)
        return (1200L - (1200L - 60L) * s / 100L)
    }
}

class LocateViewModel(
    private val reader: RfidReader,
    private val scope: CoroutineScope,
) {
    private val _signal = MutableStateFlow(0)
    val signal: StateFlow<Int> = _signal.asStateFlow()
    val intervalMs: StateFlow<Long> = _signal.map { BeepCadence.intervalMs(it) }
        .stateIn(scope, SharingStarted.Eagerly, BeepCadence.intervalMs(0))

    fun start(targetEpc: String) {
        reader.startLocate(targetEpc)
        scope.launch { reader.locateSignal.collect { _signal.value = it } }
    }
    fun startForSku(sku: String) = start(EpcCodec.maskForSku(sku))
    fun stop() { reader.stopLocate(); _signal.value = 0 }
}
```

- [ ] **Step 4: Chạy test — phải PASS** (3 test).

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/example/koistock/ui/locate/LocateViewModel.kt app/src/test/java/com/example/koistock/ui/locate/
git commit -m "feat: LocateViewModel with proximity beep cadence"
```

---

### Task 5: Màn Tìm sản phẩm (Geiger UI + phát bíp) — verify thủ công

**Files:**
- Create: `app/src/main/java/com/example/koistock/ui/locate/LocateScreen.kt`

**Interfaces:**
- Consumes: `LocateViewModel`.
- Produces: `@Composable fun LocateScreen(vm: LocateViewModel, sku: String)`.

- [ ] **Step 1: Viết LocateScreen.kt** (thanh cường độ + vòng lặp bíp theo `intervalMs`)

```kotlin
package com.example.koistock.ui.locate
import android.media.ToneGenerator
import android.media.AudioManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun LocateScreen(vm: LocateViewModel, sku: String) {
    val signal by vm.signal.collectAsState()
    val interval by vm.intervalMs.collectAsState()
    DisposableEffect(sku) { vm.startForSku(sku); onDispose { vm.stop() } }
    LaunchedEffect(Unit) {
        val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        try { while (true) { tone.startTone(ToneGenerator.TONE_PROP_BEEP, 40); delay(interval) } }
        finally { tone.release() }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Tìm: $sku", style = MaterialTheme.typography.headlineSmall)
        LinearProgressIndicator(progress = { signal / 100f }, modifier = Modifier.fillMaxWidth().height(24.dp))
        Text("Tín hiệu: $signal/100 — lại gần thì bíp nhanh dần")
    }
}
```

- [ ] **Step 2: Verify thủ công trên R6** — chọn SKU có tag → tiến/lùi → nghe bíp nhanh dần khi lại gần; thanh cường độ tăng.

- [ ] **Step 3: Commit**
```bash
git add app/src/main/java/com/example/koistock/ui/locate/LocateScreen.kt
git commit -m "feat: Locate screen with Geiger-style beep (manual-verified)"
```

---

### Task 6: Gán tag — `AssignTagViewModel`

**Files:**
- Create: `app/src/main/java/com/example/koistock/ui/assign/AssignTagViewModel.kt`
- Test: `app/src/test/java/com/example/koistock/ui/assign/AssignTagViewModelTest.kt`

**Interfaces:**
- Consumes: `RfidReader` (`scanSingle`, `writeEpc`), `TagRepo`, `ProductRepo`, `EpcCodec`.
- Produces:
  - `class AssignTagViewModel(reader, tagRepo, productRepo, deviceId, now, scope)` với `val scannedEpc: StateFlow<String?>`, `val done: StateFlow<Boolean>`, `fun scanBlank()`, `fun assign(sku: String, encodeStructured: Boolean)`. `now: () -> Long` inject cho test.

- [ ] **Step 1: Viết test AssignTagViewModelTest.kt**

```kotlin
package com.example.koistock.ui.assign
import com.example.koistock.data.model.*
import com.example.koistock.device.FakeRfidReader
import com.example.koistock.device.ScannedTag
import com.example.koistock.fakes.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class AssignTagViewModelTest {
    @Test fun scanBlank_capturesEpc() = runTest {
        val reader = FakeRfidReader().apply { scannedSingle = ScannedTag("E2000ABC", -30) }
        val vm = AssignTagViewModel(reader, FakeTagRepo(), FakeProductRepo(), "dev-1", { 100 }, backgroundScope)
        vm.scanBlank(); advanceUntilIdle()
        assertEquals("E2000ABC", vm.scannedEpc.value)
    }
    @Test fun assign_raw_writesTagMappingWithScannedEpc() = runTest {
        val reader = FakeRfidReader().apply { scannedSingle = ScannedTag("E2000ABC", -30) }
        val tags = FakeTagRepo()
        val vm = AssignTagViewModel(reader, tags, FakeProductRepo(), "dev-1", { 100 }, backgroundScope)
        vm.scanBlank(); advanceUntilIdle()
        vm.assign("SKU1", encodeStructured = false); advanceUntilIdle()
        val saved = tags.items["E2000ABC"]!!
        assertEquals("SKU1", saved.sku)
        assertEquals(100, saved.updatedAt)
        assertTrue(vm.done.value)
    }
    @Test fun assign_structured_writesEncodedEpcToTagAndMapping() = runTest {
        val reader = FakeRfidReader().apply { scannedSingle = ScannedTag("E2000ABC", -30) }
        val tags = FakeTagRepo()
        val vm = AssignTagViewModel(reader, tags, FakeProductRepo(), "dev-1", { 100 }, backgroundScope)
        vm.scanBlank(); advanceUntilIdle()
        vm.assign("SKU1", encodeStructured = true); advanceUntilIdle()
        // EPC mới có cấu trúc, đã ghi vào tag qua reader.writeEpc
        assertEquals("E2000ABC", reader.lastWrittenEpc?.first)
        assertTrue(reader.lastWrittenEpc?.second?.startsWith("KOI-SKU1-") == true)
        assertTrue(tags.items.keys.any { it.startsWith("KOI-SKU1-") })
    }
}
```

- [ ] **Step 2: Chạy test — phải FAIL.**

- [ ] **Step 3: Viết AssignTagViewModel.kt**

```kotlin
package com.example.koistock.ui.assign
import com.example.koistock.data.model.TagMapping
import com.example.koistock.data.remote.*
import com.example.koistock.device.RfidReader
import com.example.koistock.domain.EpcCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AssignTagViewModel(
    private val reader: RfidReader,
    private val tagRepo: TagRepo,
    private val productRepo: ProductRepo,
    private val deviceId: String,
    private val now: () -> Long,
    private val scope: CoroutineScope,
) {
    private val _epc = MutableStateFlow<String?>(null)
    val scannedEpc: StateFlow<String?> = _epc.asStateFlow()
    private val _done = MutableStateFlow(false)
    val done: StateFlow<Boolean> = _done.asStateFlow()

    fun scanBlank() = scope.launch { _epc.value = reader.scanSingle()?.epc }

    fun assign(sku: String, encodeStructured: Boolean) = scope.launch {
        val current = _epc.value ?: return@launch
        val ts = now()
        val finalEpc = if (encodeStructured) {
            val serial = (tagRepo.listBySku(sku).size + 1).toString().padStart(4, '0')
            val newEpc = EpcCodec.encode(sku, serial)
            reader.writeEpc(current, newEpc)
            newEpc
        } else current
        tagRepo.upsert(TagMapping(epc = finalEpc, sku = sku, status = "active", updatedAt = ts, origin = "app"))
        reader.beep()
        _done.value = true
    }
}
```

- [ ] **Step 4: Chạy test — phải PASS** (3 test).

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/example/koistock/ui/assign/AssignTagViewModel.kt app/src/test/java/com/example/koistock/ui/assign/
git commit -m "feat: AssignTagViewModel binds EPC to SKU (raw/structured)"
```

---

### Task 7: Màn Gán tag (Compose) — verify thủ công

**Files:**
- Create: `app/src/main/java/com/example/koistock/ui/assign/AssignTagScreen.kt`

**Interfaces:**
- Consumes: `AssignTagViewModel`, `ProductRepo` (chọn SKU).
- Produces: `@Composable fun AssignTagScreen(vm: AssignTagViewModel, products: List<Product>, prefillEpc: String?)`.

- [ ] **Step 1: Viết AssignTagScreen.kt** (nút "Quét tag trắng" → hiện EPC → chọn SKU từ danh sách/tìm → switch "Ghi mã có cấu trúc" → nút "Gán").

```kotlin
package com.example.koistock.ui.assign
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.koistock.data.model.Product

@Composable
fun AssignTagScreen(vm: AssignTagViewModel, products: List<Product>, prefillEpc: String?) {
    val epc by vm.scannedEpc.collectAsState()
    val done by vm.done.collectAsState()
    var structured by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Gán tag cho hàng", style = MaterialTheme.typography.headlineSmall)
        Button(onClick = { vm.scanBlank() }, Modifier.fillMaxWidth()) { Text("Quét tag trắng (cò R6)") }
        Text("EPC: ${epc ?: prefillEpc ?: "—"}")
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Switch(checked = structured, onCheckedChange = { structured = it }); Spacer(Modifier.width(8.dp)); Text("Ghi mã KOI có cấu trúc")
        }
        LazyColumn(Modifier.weight(1f)) {
            items(products) { p ->
                ListItem(headlineContent = { Text(p.name) }, supportingContent = { Text(p.sku) },
                    trailingContent = { RadioButton(selected = picked == p.sku, onClick = { picked = p.sku }) })
            }
        }
        Button(onClick = { picked?.let { vm.assign(it, structured) } }, enabled = picked != null && epc != null,
            modifier = Modifier.fillMaxWidth()) { Text(if (done) "Đã gán ✓" else "Gán") }
    }
}
```

- [ ] **Step 2: Verify thủ công** — quét tag trắng → chọn SKU → Gán → doc `tags` xuất hiện trên Firestore.

- [ ] **Step 3: Commit**
```bash
git add app/src/main/java/com/example/koistock/ui/assign/AssignTagScreen.kt
git commit -m "feat: Assign tag screen (manual-verified)"
```

---

### Task 8: Đặt vị trí — `PutawayViewModel`

**Files:**
- Create: `app/src/main/java/com/example/koistock/ui/putaway/PutawayViewModel.kt`
- Test: `app/src/test/java/com/example/koistock/ui/putaway/PutawayViewModelTest.kt`

**Interfaces:**
- Consumes: `RfidReader` (`inventory`, `startInventory`, `stopInventory`, `scanSingle`), `TagRepo`, `ProductRepo`, `TransactionRepo`, `EpcCodec`.
- Produces:
  - `class PutawayViewModel(reader, tagRepo, productRepo, txRepo, deviceId, now, scope)` với `val locationCode: StateFlow<String?>`, `val scanned: StateFlow<Set<String>>` (EPC), `fun setLocationByTag(locationCode: String)`, `fun startCollect()`, `fun stopCollect()`, `suspend fun applyAssign(): Int` (số món gán; SERIALIZED cập nhật `tags.locationCode`, BULK append `MOVE`). Trả về số EPC xử lý.

- [ ] **Step 1: Viết test PutawayViewModelTest.kt**

```kotlin
package com.example.koistock.ui.putaway
import com.example.koistock.data.model.*
import com.example.koistock.device.FakeRfidReader
import com.example.koistock.fakes.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class PutawayViewModelTest {
    private fun product(sku: String, mode: TrackingMode) = Product(sku,"n","c",mode,0,"A-01")

    @Test fun serialized_applyAssign_updatesTagLocation() = runTest {
        val reader = FakeRfidReader()
        val tags = FakeTagRepo(mutableMapOf("KOI-S1-1" to TagMapping("KOI-S1-1","S1", locationCode="A-01")))
        val prods = FakeProductRepo(mutableMapOf("S1" to product("S1", TrackingMode.SERIALIZED)))
        val tx = FakeTransactionRepo()
        val vm = PutawayViewModel(reader, tags, prods, tx, "dev-1", { 5 }, backgroundScope)
        vm.setLocationByTag("A-03")
        vm.startCollect(); advanceUntilIdle()
        reader.emitTag("KOI-S1-1"); advanceUntilIdle()
        vm.stopCollect()
        val n = vm.applyAssign()
        assertEquals(1, n)
        assertEquals("A-03", tags.items["KOI-S1-1"]!!.locationCode)
        assertTrue(tx.appended.isEmpty()) // serialized không cần MOVE tx
    }

    @Test fun bulk_applyAssign_appendsMoveTransaction() = runTest {
        val reader = FakeRfidReader()
        val tags = FakeTagRepo(mutableMapOf("KOI-B1-1" to TagMapping("KOI-B1-1","B1", locationCode="A-01")))
        val prods = FakeProductRepo(mutableMapOf("B1" to product("B1", TrackingMode.BULK)))
        val tx = FakeTransactionRepo()
        val vm = PutawayViewModel(reader, tags, prods, tx, "dev-1", { 5 }, backgroundScope)
        vm.setLocationByTag("A-03")
        vm.startCollect(); advanceUntilIdle()
        reader.emitTag("KOI-B1-1"); advanceUntilIdle()
        vm.stopCollect()
        vm.applyAssign()
        assertEquals(1, tx.appended.size)
        assertEquals(TxType.MOVE, tx.appended.first().type)
        assertEquals("A-03", tx.appended.first().locationCode)
    }

    @Test fun collect_dedupesByEpc() = runTest {
        val reader = FakeRfidReader()
        val vm = PutawayViewModel(reader, FakeTagRepo(), FakeProductRepo(), FakeTransactionRepo(), "d", { 0 }, backgroundScope)
        vm.startCollect(); advanceUntilIdle()
        reader.emitTag("E1"); reader.emitTag("E1"); reader.emitTag("E2"); advanceUntilIdle()
        assertEquals(setOf("E1","E2"), vm.scanned.value)
    }
}
```

- [ ] **Step 2: Chạy test — phải FAIL.**

- [ ] **Step 3: Viết PutawayViewModel.kt**

```kotlin
package com.example.koistock.ui.putaway
import com.example.koistock.data.model.*
import com.example.koistock.data.remote.*
import com.example.koistock.device.RfidReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class PutawayViewModel(
    private val reader: RfidReader,
    private val tagRepo: TagRepo,
    private val productRepo: ProductRepo,
    private val txRepo: TransactionRepo,
    private val deviceId: String,
    private val now: () -> Long,
    private val scope: CoroutineScope,
) {
    private val _loc = MutableStateFlow<String?>(null)
    val locationCode: StateFlow<String?> = _loc.asStateFlow()
    private val _scanned = MutableStateFlow<Set<String>>(emptySet())
    val scanned: StateFlow<Set<String>> = _scanned.asStateFlow()
    private var job: kotlinx.coroutines.Job? = null

    fun setLocationByTag(locationCode: String) { _loc.value = locationCode }

    fun startCollect() {
        _scanned.value = emptySet()
        reader.startInventory()
        job = scope.launch { reader.inventory.collect { t -> _scanned.update { it + t.epc } } }
    }
    fun stopCollect() { reader.stopInventory(); job?.cancel() }

    suspend fun applyAssign(): Int {
        val loc = _loc.value ?: return 0
        val ts = now()
        var count = 0
        for (epc in _scanned.value) {
            val tag = tagRepo.getByEpc(epc) ?: continue
            val product = productRepo.getBySku(tag.sku) ?: continue
            when (product.trackingMode) {
                TrackingMode.SERIALIZED ->
                    tagRepo.upsert(tag.copy(locationCode = loc, updatedAt = ts, origin = "app"))
                TrackingMode.BULK ->
                    txRepo.append(Transaction(UUID.randomUUID().toString(), TxType.MOVE, tag.sku, epc, 0, loc, deviceId, ts))
            }
            count++
        }
        return count
    }
}
```

- [ ] **Step 4: Chạy test — phải PASS** (3 test).

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/example/koistock/ui/putaway/PutawayViewModel.kt app/src/test/java/com/example/koistock/ui/putaway/
git commit -m "feat: PutawayViewModel assigns location by tag (serialized/bulk)"
```

---

### Task 9: Màn Đặt vị trí (Compose) + nối routes — verify thủ công

**Files:**
- Create: `app/src/main/java/com/example/koistock/ui/putaway/PutawayScreen.kt`
- Modify: `AppShell.kt` (routes `locate`, `assign`, `putaway`), `MainMenuScreen` mở đúng route.

**Interfaces:**
- Consumes: `PutawayViewModel`.
- Produces: `@Composable fun PutawayScreen(vm: PutawayViewModel)`.

- [ ] **Step 1: Viết PutawayScreen.kt** — nút "Quét tag vị trí" (→ dùng `Epc... / scanSingle` để lấy locationCode, hoặc chọn từ danh sách), hiện khu đang chọn; nút "Bắt đầu quét hàng"/"Dừng"; danh sách EPC đã gom; nút "Gán N món".

```kotlin
package com.example.koistock.ui.putaway
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun PutawayScreen(vm: PutawayViewModel) {
    val loc by vm.locationCode.collectAsState()
    val scanned by vm.scanned.collectAsState()
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Đặt vị trí", style = MaterialTheme.typography.headlineSmall)
        Text("Khu/Kệ: ${loc ?: "— quét tag vị trí —"}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.startCollect() }) { Text("Bắt đầu quét") }
            OutlinedButton(onClick = { vm.stopCollect() }) { Text("Dừng") }
        }
        Text("Đã quét: ${scanned.size} món")
        Button(enabled = loc != null && scanned.isNotEmpty(),
            onClick = { scope.launch { result = vm.applyAssign() } },
            modifier = Modifier.fillMaxWidth()) { Text(result?.let { "Đã gán $it món ✓" } ?: "Gán vào ${loc ?: ""}") }
    }
}
```

- [ ] **Step 2: Nối routes trong AppShell** cho `locate`, `assign`, `putaway`; `MainMenuScreen.onOpen` điều hướng.

- [ ] **Step 3: Verify thủ công** — quét tag vị trí → khu hiện lên → quét vài hàng → Gán → kiểm `tags.locationCode`/`MOVE` trên Firestore.

- [ ] **Step 4: Commit**
```bash
git add app/src/main/java/com/example/koistock/ui/putaway/PutawayScreen.kt app/src/main/java/com/example/koistock/ui/shell/AppShell.kt
git commit -m "feat: Putaway screen + route wiring (manual-verified)"
```

---

## Self-Review

**Spec coverage:** Tra cứu (Task 2,3) ✓ · Tìm sản phẩm/Geiger + mask theo SKU (Task 4,5) ✓ · Gán tag raw/structured + writeEpc (Task 6,7) ✓ · Đặt vị trí serialized/bulk bằng tag vị trí (Task 8,9) ✓ · Tag lạ gợi ý gán (Task 2,3) ✓.
**Placeholder scan:** không có TBD/TODO; UI/hardware ghi rõ tiêu chí verify. ✓
**Type consistency:** `EpcCodec.maskForSku` dùng ở Locate (Task 4) & mọi nơi; `TagRepo.listBySku` thêm ở Task 1 dùng ở Task 5,6; `TrackingMode` phân nhánh ở Task 8 khớp model Plan 1. ✓
**Phụ thuộc:** cần Plan 1 hoàn tất (models, repos, RfidReader, FakeRfidReader, AppShell).

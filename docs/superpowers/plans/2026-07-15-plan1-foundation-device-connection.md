# KOIStock — Plan 1: Nền tảng, Kết nối R6 & Tầng dữ liệu

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dựng nền app Android KOIStock: tích hợp SDK Chainway sau một interface trừu tượng, kết nối/ghép nối đầu đọc R6 qua BLE (auto-reconnect), và tầng dữ liệu Firestore (models + repositories) — đủ để các plan sau xây nghiệp vụ lên trên.

**Architecture:** Kotlin + Jetpack Compose (MVVM). SDK Chainway `RFIDWithUHFBLE` bị bọc sau interface `RfidReader` để test bằng `FakeRfidReader`. Firestore là nguồn sự thật; truy cập qua các repository. UI có thanh trạng thái kết nối R6 + màn ghép nối.

**Tech Stack:** Kotlin, Jetpack Compose, Coroutines/Flow, Chainway `DeviceAPI_ver20251103_release.aar` (`RFIDWithUHFBLE`), Firebase (Firestore + Anonymous Auth), DataStore Preferences, JUnit4 + Turbine + kotlinx-coroutines-test.

> **Revision 2 — 2026-07-15:** Bản này thay thế các đoạn code cũ nếu có mâu thuẫn. Trước khi mở rộng UI phải khóa bốn nền móng: timestamp mapper, Firestore Security Rules, giao dịch kho nguyên tử và hardware spike Chainway R6.

## Global Constraints

- Ngôn ngữ: **Kotlin**; UI **Jetpack Compose** (đã bật `buildFeatures.compose = true`).
- `minSdk = 24`, `targetSdk = 36`, `applicationId = "com.example.koistock"`, `namespace = "com.example.koistock"`.
- Tính năng **HID điền ô Sheet** (Plan 5) cần **API 28+** → phải feature-gate; KHÔNG nâng `minSdk` toàn app.
- Đầu đọc: **Chainway R6** — UHF RFID qua **BLE**, SDK class `RFIDWithUHFBLE`.
- Mọi truy cập SDK Chainway phải đi qua interface `RfidReader` (không gọi thẳng class Chainway trong ViewModel/UI).
- Nguồn sự thật = **Firestore**; collections: `products`, `tags`, `transactions`, `locations`, `syncMeta`.
- Kiểu gắn tag **hybrid**: mỗi product có `trackingMode ∈ {SERIALIZED, BULK}`. Phân khu 2 cấp **Khu(ZONE) → Kệ(SHELF)**.
- Máy dùng chung, **không đăng nhập người dùng** → Firebase **Anonymous Auth**; giao dịch gắn `deviceId`.
- Trường đồng bộ chung ở mọi bản ghi: `updatedAt` (Firestore `Timestamp`, do server cấp), `origin ∈ {app, sheet}`, `syncRev` (Long). Domain không cast `Timestamp` thành `Number`; mapper chuyển đổi rõ `Timestamp ↔ Instant/epochMillis`.
- `transactions` là **append-only**, có đủ `updatedAt/origin/syncRev`, và không cho client sửa/xóa sau khi tạo.
- Mọi thay đổi tồn kho phải đi qua một operation idempotent, nguyên tử; cấm ViewModel tự đọc–sửa–ghi `quantity`.
- Phải có `firestore.rules` + emulator rule tests trước khi nối UI thật. Anonymous Auth không đồng nghĩa với quyền đọc/ghi toàn DB.
- Test đơn vị chạy bằng `./gradlew testDebugUnitTest`. Bước phần cứng (BLE thật) verify thủ công trên R6.
- Spec nguồn: `docs/superpowers/specs/2026-07-15-koistock-warehouse-design.md`.

---

## Release gates bắt buộc của Plan 1

- [ ] **Gate A — Timestamp codec:** tạo `FirestoreValueCodec` đọc được `Timestamp`, ghi `FieldValue.serverTimestamp()`, có unit test cho Timestamp/null/legacy epoch millis. `updatedAt=0` chỉ dùng cho object chưa lưu, không được dùng làm fallback im lặng cho dữ liệu sai kiểu.
- [ ] **Gate B — Security Rules:** thêm `firestore.rules`, `firebase.json` và emulator tests. Anonymous client chỉ được đọc dữ liệu nghiệp vụ cần thiết; `transactions` chỉ create, không update/delete; validate enum, kiểu, field bắt buộc và giới hạn delta.
- [ ] **Gate C — Stable device identity:** sinh UUID một lần trong DataStore làm `deviceId`; không dùng anonymous UID làm định danh thiết bị vì UID đổi sau reinstall/clear data.
- [ ] **Gate D — Chainway spike trên R6 thật:** xác nhận bằng javadoc + phần cứng: callback connect, scan cancellation, EPC mask khi write, locate target, trigger press/release, battery, reconnect và release. Ghi kết quả/chữ ký SDK đã xác nhận vào `docs/hardware/chainway-r6-sdk-spike.md`.
- [ ] **Gate E — Atomic command contract:** định nghĩa `StockCommandRepository.commit(commandId, movements)` để Plan 3 dùng Firestore transaction/write batch và retry an toàn.

---

## File Structure

```
app/
  libs/DeviceAPI_ver20251103_release.aar          # copy từ "Chainway RFID SDK android/"
  src/main/java/com/example/koistock/
    device/
      RfidReader.kt              # interface trừu tượng + kiểu dữ liệu quét
      ChainwayRfidReader.kt      # hiện thực bằng RFIDWithUHFBLE
      ConnectionState.kt         # sealed state kết nối
    data/
      model/
        Product.kt               # + TrackingMode
        TagMapping.kt
        Transaction.kt           # + TxType
        LocationNode.kt          # + LocationType
      remote/
        FirestoreProvider.kt     # khởi tạo Firestore + anonymous auth
        ProductRepository.kt
        TagRepository.kt
        LocationRepository.kt
        TransactionRepository.kt
    device/DevicePrefs.kt        # DataStore lưu MAC lần cuối
    ui/
      connection/
        ConnectionViewModel.kt   # state machine kết nối, auto-reconnect
        PairingScreen.kt         # UI quét/chọn/nối R6
      shell/
        AppShell.kt              # scaffold + thanh trạng thái + navigation
        MainMenuScreen.kt        # lưới 9 nút chức năng (chỉ điều hướng ở plan này)
    MainActivity.kt              # host Compose

  src/test/java/com/example/koistock/
    device/FakeRfidReader.kt
    device/FakeRfidReaderTest.kt
    data/model/ModelMappingTest.kt
    ui/connection/ConnectionViewModelTest.kt
    device/DevicePrefsTest.kt
```

---

### Task 1: Cấu hình Gradle, SDK Chainway, Firebase, quyền

**Files:**
- Create: `app/libs/DeviceAPI_ver20251103_release.aar` (copy)
- Modify: `app/build.gradle.kts`
- Modify: `build.gradle.kts` (root) — thêm plugin Google Services
- Modify: `gradle/libs.versions.toml`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/google-services.json` (tải từ Firebase Console)

**Interfaces:**
- Consumes: —
- Produces: build cấu hình sẵn Chainway AAR + Firebase + thư viện test (Turbine, coroutines-test, DataStore).

- [ ] **Step 1: Copy AAR vào app/libs**

Copy file `Chainway RFID SDK android/DeviceAPI_ver20251103_release.aar` → `app/libs/DeviceAPI_ver20251103_release.aar`.

- [ ] **Step 2: Thêm phiên bản thư viện vào version catalog**

Trong `gradle/libs.versions.toml`, mục `[versions]` thêm:
```toml
firebaseBom = "33.7.0"
googleServices = "4.4.2"
datastore = "1.1.1"
coroutines = "1.9.0"
turbine = "1.2.0"
lifecycleViewmodelCompose = "2.9.4"
navigationCompose = "2.9.4"
```
Mục `[libraries]` thêm:
```toml
firebase-bom = { group = "com.google.firebase", name = "firebase-bom", version.ref = "firebaseBom" }
firebase-firestore = { group = "com.google.firebase", name = "firebase-firestore-ktx" }
firebase-auth = { group = "com.google.firebase", name = "firebase-auth-ktx" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
kotlinx-coroutines-play-services = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-play-services", version.ref = "coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "androidx-lifecycle-viewmodel-compose", version.ref = "lifecycleViewmodelCompose" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
```
Mục `[plugins]` thêm:
```toml
google-services = { id = "com.google.gms.google-services", version.ref = "googleServices" }
```

- [ ] **Step 3: Khai báo plugin ở root build**

Trong `build.gradle.kts` (root), khối `plugins { ... }` thêm:
```kotlin
alias(libs.plugins.google.services) apply false
```

- [ ] **Step 4: Cấu hình app/build.gradle.kts**

Thêm `apply` plugin google-services (đầu khối `plugins`):
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}
```
Trong `dependencies { ... }` thêm:
```kotlin
implementation(files("libs/DeviceAPI_ver20251103_release.aar"))
implementation(platform(libs.firebase.bom))
implementation(libs.firebase.firestore)
implementation(libs.firebase.auth)
implementation(libs.kotlinx.coroutines.play.services)
implementation(libs.androidx.datastore.preferences)
implementation(libs.androidx.lifecycle.viewmodel.compose)
implementation(libs.androidx.navigation.compose)
testImplementation(libs.kotlinx.coroutines.test)
testImplementation(libs.turbine)
```

- [ ] **Step 5: Thêm quyền BLE vào Manifest**

Trong `AndroidManifest.xml`, trước thẻ `<application>`:
```xml
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" android:usesPermissionFlags="neverForLocation" tools:targetApi="s" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" tools:targetApi="s" />
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-feature android:name="android.hardware.bluetooth_le" android:required="true" />
```

- [ ] **Step 6: Đặt google-services.json**

Tải `google-services.json` từ Firebase Console (project mới, thêm app package `com.example.koistock`) → đặt vào `app/google-services.json`. Bật **Firestore** và **Anonymous Auth** trong Console.

- [ ] **Step 7: Sync & build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (AAR + Firebase resolve được).

- [ ] **Step 8: Commit**

```bash
git add app/build.gradle.kts build.gradle.kts gradle/libs.versions.toml app/src/main/AndroidManifest.xml app/libs/ app/google-services.json
git commit -m "chore: add Chainway AAR, Firebase, BLE permissions and test libs"
```

---

### Task 2: Interface `RfidReader` + `FakeRfidReader`

**Files:**
- Create: `app/src/main/java/com/example/koistock/device/RfidReader.kt`
- Create: `app/src/main/java/com/example/koistock/device/ConnectionState.kt`
- Create: `app/src/test/java/com/example/koistock/device/FakeRfidReader.kt`
- Test: `app/src/test/java/com/example/koistock/device/FakeRfidReaderTest.kt`

**Interfaces:**
- Consumes: —
- Produces:
  - `data class ScannedTag(val epc: String, val rssi: Int)`
  - `data class BleDeviceInfo(val name: String, val mac: String, val rssi: Int)`
  - `sealed interface ConnectionState { object Disconnected; object Scanning; data class Connecting(val mac: String); data class Connected(val mac: String) }`
  - interface `RfidReader` với: `val connectionState: StateFlow<ConnectionState>`, `val inventory: SharedFlow<ScannedTag>`, `val locateSignal: SharedFlow<Int>`, `val triggerEvents: SharedFlow<Boolean>`, `fun startDeviceScan(): Flow<BleDeviceInfo>`, `suspend fun connect(mac: String): Boolean`, `fun disconnect()`, `suspend fun scanSingle(): ScannedTag?`, `fun startInventory()`, `fun stopInventory()`, `suspend fun writeEpc(oldEpc: String, newEpc: String): Boolean`, `fun startLocate(targetEpc: String)`, `fun stopLocate()`, `suspend fun batteryPercent(): Int`, `fun beep()`, `fun release()`
  - `FakeRfidReader` (test) hiện thực `RfidReader` với hàm điều khiển: `emitTag(epc, rssi)`, `emitLocate(value)`, `emitTrigger(pressed)`, `setConnectResult(Boolean)`, `setBattery(Int)`.

- [ ] **Step 1: Viết ConnectionState.kt**

```kotlin
package com.example.koistock.device

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Scanning : ConnectionState
    data class Connecting(val mac: String) : ConnectionState
    data class Connected(val mac: String) : ConnectionState
}
```

- [ ] **Step 2: Viết RfidReader.kt (interface + kiểu dữ liệu)**

```kotlin
package com.example.koistock.device

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class ScannedTag(val epc: String, val rssi: Int)
data class BleDeviceInfo(val name: String, val mac: String, val rssi: Int)

interface RfidReader {
    val connectionState: StateFlow<ConnectionState>
    val inventory: SharedFlow<ScannedTag>
    val locateSignal: SharedFlow<Int>   // 0..100, càng cao càng gần
    val triggerEvents: SharedFlow<Boolean> // true=nhấn cò, false=nhả

    fun startDeviceScan(): Flow<BleDeviceInfo>
    suspend fun connect(mac: String): Boolean
    fun disconnect()

    suspend fun scanSingle(): ScannedTag?
    fun startInventory()
    fun stopInventory()
    suspend fun writeEpc(oldEpc: String, newEpc: String): Boolean

    fun startLocate(targetEpc: String)
    fun stopLocate()

    suspend fun batteryPercent(): Int
    fun beep()
    fun release()
}
```

- [ ] **Step 3: Viết FakeRfidReader.kt (trong src/test)**

```kotlin
package com.example.koistock.device

import kotlinx.coroutines.flow.*

class FakeRfidReader : RfidReader {
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _state

    private val _inventory = MutableSharedFlow<ScannedTag>(extraBufferCapacity = 64)
    override val inventory: SharedFlow<ScannedTag> = _inventory

    private val _locate = MutableSharedFlow<Int>(extraBufferCapacity = 64)
    override val locateSignal: SharedFlow<Int> = _locate

    private val _trigger = MutableSharedFlow<Boolean>(extraBufferCapacity = 8)
    override val triggerEvents: SharedFlow<Boolean> = _trigger

    var connectResult = true
    var battery = 100
    var lastWrittenEpc: Pair<String, String>? = null
    var locateTarget: String? = null
    var inventoryRunning = false

    val scanDevices = mutableListOf(BleDeviceInfo("R6-TEST", "AA:BB:CC:DD:EE:FF", -50))

    override fun startDeviceScan(): Flow<BleDeviceInfo> = scanDevices.asFlow()

    override suspend fun connect(mac: String): Boolean {
        _state.value = ConnectionState.Connecting(mac)
        _state.value = if (connectResult) ConnectionState.Connected(mac) else ConnectionState.Disconnected
        return connectResult
    }
    override fun disconnect() { _state.value = ConnectionState.Disconnected }
    override suspend fun scanSingle(): ScannedTag? = scannedSingle
    var scannedSingle: ScannedTag? = null
    override fun startInventory() { inventoryRunning = true }
    override fun stopInventory() { inventoryRunning = false }
    override suspend fun writeEpc(oldEpc: String, newEpc: String): Boolean {
        lastWrittenEpc = oldEpc to newEpc; return true
    }
    override fun startLocate(targetEpc: String) { locateTarget = targetEpc }
    override fun stopLocate() { locateTarget = null }
    override suspend fun batteryPercent(): Int = battery
    override fun beep() {}
    override fun release() {}

    // Điều khiển từ test
    suspend fun emitTag(epc: String, rssi: Int = -55) = _inventory.emit(ScannedTag(epc, rssi))
    suspend fun emitLocate(value: Int) = _locate.emit(value)
    suspend fun emitTrigger(pressed: Boolean) = _trigger.emit(pressed)
    fun setState(s: ConnectionState) { _state.value = s }
}
```

- [ ] **Step 4: Viết test FakeRfidReaderTest.kt**

```kotlin
package com.example.koistock.device

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class FakeRfidReaderTest {
    @Test fun connect_success_movesToConnected() = runTest {
        val r = FakeRfidReader()
        assertTrue(r.connect("AA:BB:CC:DD:EE:FF"))
        assertEquals(ConnectionState.Connected("AA:BB:CC:DD:EE:FF"), r.connectionState.value)
    }

    @Test fun connect_failure_staysDisconnected() = runTest {
        val r = FakeRfidReader().apply { connectResult = false }
        assertFalse(r.connect("X"))
        assertEquals(ConnectionState.Disconnected, r.connectionState.value)
    }

    @Test fun emitTag_isReceivedOnInventoryFlow() = runTest {
        val r = FakeRfidReader()
        r.inventory.test {
            r.emitTag("E200001", -40)
            assertEquals(ScannedTag("E200001", -40), awaitItem())
        }
    }
}
```

- [ ] **Step 5: Chạy test — phải PASS**

Run: `./gradlew testDebugUnitTest --tests "com.example.koistock.device.FakeRfidReaderTest"`
Expected: PASS (3 test).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/koistock/device/ app/src/test/java/com/example/koistock/device/FakeRfidReader*.kt
git commit -m "feat: RfidReader abstraction + FakeRfidReader for tests"
```

---

### Task 3: Domain models + mapping Firestore

**Files:**
- Create: `app/src/main/java/com/example/koistock/data/model/Product.kt`
- Create: `app/src/main/java/com/example/koistock/data/model/TagMapping.kt`
- Create: `app/src/main/java/com/example/koistock/data/model/Transaction.kt`
- Create: `app/src/main/java/com/example/koistock/data/model/LocationNode.kt`
- Test: `app/src/test/java/com/example/koistock/data/model/ModelMappingTest.kt`

**Interfaces:**
- Consumes: —
- Produces:
  - `enum TrackingMode { SERIALIZED, BULK }`
  - `enum LocationType { ZONE, SHELF }`
  - `enum TxType { IN, OUT, COUNT, ADJUST, MOVE }`
  - `data class Product(sku, name, unit, trackingMode, quantity, locationCode, imageUrl?, updatedAt, origin, syncRev)`
  - `data class TagMapping(epc, sku, unitSerial?, status, locationCode, updatedAt, origin, syncRev)`
  - `data class Transaction(id, type, sku, epc?, delta, locationCode?, deviceId, at)`
  - `data class LocationNode(code, name, type, parent, updatedAt, origin, syncRev)`
  - mỗi model có `fun toMap(): Map<String, Any?>` và `companion object { fun fromMap(id: String, m: Map<String, Any?>): T }`.

- [ ] **Step 1: Viết Product.kt**

```kotlin
package com.example.koistock.data.model

enum class TrackingMode { SERIALIZED, BULK }

data class Product(
    val sku: String,
    val name: String,
    val unit: String,
    val trackingMode: TrackingMode,
    val quantity: Long,
    val locationCode: String,
    val imageUrl: String? = null,
    val updatedAt: Long = 0,
    val origin: String = "app",
    val syncRev: Long = 0,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "name" to name, "unit" to unit, "trackingMode" to trackingMode.name,
        "quantity" to quantity, "locationCode" to locationCode, "imageUrl" to imageUrl,
        "updatedAt" to updatedAt, "origin" to origin, "syncRev" to syncRev,
    )
    companion object {
        fun fromMap(sku: String, m: Map<String, Any?>) = Product(
            sku = sku,
            name = m["name"] as? String ?: "",
            unit = m["unit"] as? String ?: "",
            trackingMode = runCatching { TrackingMode.valueOf(m["trackingMode"] as String) }.getOrDefault(TrackingMode.BULK),
            quantity = (m["quantity"] as? Number)?.toLong() ?: 0,
            locationCode = m["locationCode"] as? String ?: "",
            imageUrl = m["imageUrl"] as? String,
            updatedAt = (m["updatedAt"] as? Number)?.toLong() ?: 0,
            origin = m["origin"] as? String ?: "app",
            syncRev = (m["syncRev"] as? Number)?.toLong() ?: 0,
        )
    }
}
```

- [ ] **Step 2: Viết TagMapping.kt**

```kotlin
package com.example.koistock.data.model

data class TagMapping(
    val epc: String,
    val sku: String,
    val unitSerial: String? = null,
    val status: String = "active",  // active | void
    val locationCode: String = "",
    val updatedAt: Long = 0,
    val origin: String = "app",
    val syncRev: Long = 0,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "sku" to sku, "unitSerial" to unitSerial, "status" to status,
        "locationCode" to locationCode, "updatedAt" to updatedAt,
        "origin" to origin, "syncRev" to syncRev,
    )
    companion object {
        fun fromMap(epc: String, m: Map<String, Any?>) = TagMapping(
            epc = epc,
            sku = m["sku"] as? String ?: "",
            unitSerial = m["unitSerial"] as? String,
            status = m["status"] as? String ?: "active",
            locationCode = m["locationCode"] as? String ?: "",
            updatedAt = (m["updatedAt"] as? Number)?.toLong() ?: 0,
            origin = m["origin"] as? String ?: "app",
            syncRev = (m["syncRev"] as? Number)?.toLong() ?: 0,
        )
    }
}
```

- [ ] **Step 3: Viết Transaction.kt**

```kotlin
package com.example.koistock.data.model

enum class TxType { IN, OUT, COUNT, ADJUST, MOVE }

data class Transaction(
    val id: String,
    val type: TxType,
    val sku: String,
    val epc: String? = null,
    val delta: Long,
    val locationCode: String? = null,
    val deviceId: String,
    val at: Long = 0,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "type" to type.name, "sku" to sku, "epc" to epc, "delta" to delta,
        "locationCode" to locationCode, "deviceId" to deviceId, "at" to at,
    )
    companion object {
        fun fromMap(id: String, m: Map<String, Any?>) = Transaction(
            id = id,
            type = runCatching { TxType.valueOf(m["type"] as String) }.getOrDefault(TxType.ADJUST),
            sku = m["sku"] as? String ?: "",
            epc = m["epc"] as? String,
            delta = (m["delta"] as? Number)?.toLong() ?: 0,
            locationCode = m["locationCode"] as? String,
            deviceId = m["deviceId"] as? String ?: "",
            at = (m["at"] as? Number)?.toLong() ?: 0,
        )
    }
}
```

- [ ] **Step 4: Viết LocationNode.kt**

```kotlin
package com.example.koistock.data.model

enum class LocationType { ZONE, SHELF }

data class LocationNode(
    val code: String,
    val name: String,
    val type: LocationType,
    val parent: String? = null,
    val updatedAt: Long = 0,
    val origin: String = "app",
    val syncRev: Long = 0,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "name" to name, "type" to type.name, "parent" to parent,
        "updatedAt" to updatedAt, "origin" to origin, "syncRev" to syncRev,
    )
    companion object {
        fun fromMap(code: String, m: Map<String, Any?>) = LocationNode(
            code = code,
            name = m["name"] as? String ?: "",
            type = runCatching { LocationType.valueOf(m["type"] as String) }.getOrDefault(LocationType.SHELF),
            parent = m["parent"] as? String,
            updatedAt = (m["updatedAt"] as? Number)?.toLong() ?: 0,
            origin = m["origin"] as? String ?: "app",
            syncRev = (m["syncRev"] as? Number)?.toLong() ?: 0,
        )
    }
}
```

- [ ] **Step 5: Viết ModelMappingTest.kt**

```kotlin
package com.example.koistock.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelMappingTest {
    @Test fun product_roundTrip_preservesFields() {
        val p = Product("SKU1", "Cá KOI", "con", TrackingMode.SERIALIZED, 5, "A-03",
            updatedAt = 111, origin = "app", syncRev = 2)
        assertEquals(p, Product.fromMap("SKU1", p.toMap()))
    }
    @Test fun product_fromMap_defaultsTrackingModeBulk_whenMissing() {
        val p = Product.fromMap("SKU2", mapOf("name" to "X"))
        assertEquals(TrackingMode.BULK, p.trackingMode)
    }
    @Test fun tag_roundTrip_preservesFields() {
        val t = TagMapping("E2001", "SKU1", "SN-1", "active", "A-03", 9, "app", 1)
        assertEquals(t, TagMapping.fromMap("E2001", t.toMap()))
    }
    @Test fun transaction_roundTrip_preservesFields() {
        val tx = Transaction("t1", TxType.OUT, "SKU1", "E2001", -1, "A-03", "dev-1", 7)
        assertEquals(tx, Transaction.fromMap("t1", tx.toMap()))
    }
    @Test fun location_roundTrip_preservesFields() {
        val l = LocationNode("A-03", "Kệ 3", LocationType.SHELF, "A", 3, "app", 1)
        assertEquals(l, LocationNode.fromMap("A-03", l.toMap()))
    }
}
```

- [ ] **Step 6: Chạy test — phải PASS**

Run: `./gradlew testDebugUnitTest --tests "com.example.koistock.data.model.ModelMappingTest"`
Expected: PASS (5 test).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/koistock/data/model/ app/src/test/java/com/example/koistock/data/model/
git commit -m "feat: domain models with Firestore map round-trip"
```

---

### Task 4: `ChainwayRfidReader` — hiện thực SDK (verify thủ công)

**Files:**
- Create: `app/src/main/java/com/example/koistock/device/ChainwayRfidReader.kt`

**Interfaces:**
- Consumes: `RfidReader`, `ScannedTag`, `BleDeviceInfo`, `ConnectionState` (Task 2). SDK: `com.rscja.deviceapi.RFIDWithUHFBLE`, `com.rscja.deviceapi.interfaces.{ConnectionStatusCallback, IUHFInventoryCallback, IUHFLocationCallback, KeyEventCallback}`, `com.rscja.deviceapi.entity.UHFTAGInfo`, `com.rscja.deviceapi.enums.ConnectionStatus`.
- Produces: `class ChainwayRfidReader(context: Context) : RfidReader` — singleton dùng thật trong app.

> Không có unit test cho task này (phụ thuộc phần cứng BLE). Verify thủ công ở Step cuối. Tra cứu chữ ký hàm chính xác trong javadoc: `Chainway RFID SDK android/doc/com/rscja/deviceapi/RFIDWithUHFBLE.html`.

- [ ] **Step 1: Viết ChainwayRfidReader.kt**

```kotlin
package com.example.koistock.device

import android.content.Context
import com.rscja.deviceapi.RFIDWithUHFBLE
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.enums.ConnectionStatus
import com.rscja.deviceapi.interfaces.ConnectionStatusCallback
import com.rscja.deviceapi.interfaces.IUHFInventoryCallback
import com.rscja.deviceapi.interfaces.IUHFLocationCallback
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class ChainwayRfidReader(private val context: Context) : RfidReader {
    private val uhf: RFIDWithUHFBLE = RFIDWithUHFBLE.getInstance()
    init { runCatching { uhf.init(context) } }

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _inventory = MutableSharedFlow<ScannedTag>(extraBufferCapacity = 256)
    override val inventory: SharedFlow<ScannedTag> = _inventory

    private val _locate = MutableSharedFlow<Int>(extraBufferCapacity = 64)
    override val locateSignal: SharedFlow<Int> = _locate

    private val _trigger = MutableSharedFlow<Boolean>(extraBufferCapacity = 8)
    override val triggerEvents: SharedFlow<Boolean> = _trigger

    override fun startDeviceScan(): Flow<BleDeviceInfo> = callbackFlow {
        uhf.startScanBTDevices { device, _, rssi ->
            trySend(BleDeviceInfo(device?.name ?: "R6", device?.address ?: "", rssi))
        }
        awaitClose { uhf.stopScanBTDevices() }
    }

    override suspend fun connect(mac: String): Boolean = suspendCancellableCoroutine { cont ->
        _state.value = ConnectionState.Connecting(mac)
        val ok = uhf.connect(mac, ConnectionStatusCallback<Any> { status, _ ->
            _state.value = when (status) {
                ConnectionStatus.CONNECTED -> ConnectionState.Connected(mac)
                ConnectionStatus.DISCONNECTED -> ConnectionState.Disconnected
                else -> _state.value
            }
        })
        if (!ok) { _state.value = ConnectionState.Disconnected }
        // key trigger callback
        uhf.setKeyEventCallback { keyDown -> _trigger.tryEmit(keyDown) }
        if (cont.isActive) cont.resume(ok)
    }

    override fun disconnect() { runCatching { uhf.disconnect() }; _state.value = ConnectionState.Disconnected }

    override suspend fun scanSingle(): ScannedTag? {
        val info: UHFTAGInfo? = uhf.inventorySingleTag()
        return info?.let { ScannedTag(it.epc, it.rssi.toIntOrNull() ?: 0) }
    }

    override fun startInventory() {
        uhf.setInventoryCallback(IUHFInventoryCallback { info ->
            _inventory.tryEmit(ScannedTag(info.epc, info.rssi.toIntOrNull() ?: 0))
        })
        uhf.startInventoryTag()
    }
    override fun stopInventory() { runCatching { uhf.stopInventory() } }

    override suspend fun writeEpc(oldEpc: String, newEpc: String): Boolean =
        runCatching { uhf.writeDataToEpc("00000000", newEpc) }.getOrDefault(false)

    override fun startLocate(targetEpc: String) {
        uhf.startLocation(context, IUHFLocationCallback { value, _ -> _locate.tryEmit(value) })
    }
    override fun stopLocate() { runCatching { uhf.stopLocation() } }

    override suspend fun batteryPercent(): Int = runCatching { uhf.getBattery() }.getOrDefault(0)
    override fun beep() { runCatching { uhf.triggerBeep(1) } }
    override fun release() { runCatching { uhf.disconnect() }; runCatching { uhf.free() } }
}
```

> Lưu ý: chữ ký lambda `startScanBTDevices`, `IUHFInventoryCallback`, `IUHFLocationCallback`, `setKeyEventCallback`, `getBattery`, `stopInventory`, `triggerBeep` phải khớp javadoc SDK — chỉnh lại đúng tên/tham số khi biên dịch báo lỗi.

- [ ] **Step 2: Biên dịch**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (nếu lỗi chữ ký SDK → sửa theo javadoc rồi build lại).

- [ ] **Step 3: Verify thủ công trên R6 (ghi lại kết quả)**

Tạm gọi từ `MainActivity` (hoặc chờ Task 7 UI). Kiểm: bật R6 → `startDeviceScan()` thấy R6 trong danh sách → `connect(mac)` chuyển `Connected` → `scanSingle()` đọc được 1 tag. Ghi chú kết quả vào commit message.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/koistock/device/ChainwayRfidReader.kt
git commit -m "feat: ChainwayRfidReader wrapping RFIDWithUHFBLE (manual-verified on R6)"
```

---

### Task 5: `DevicePrefs` — lưu MAC lần cuối (DataStore)

**Files:**
- Create: `app/src/main/java/com/example/koistock/device/DevicePrefs.kt`
- Test: `app/src/test/java/com/example/koistock/device/DevicePrefsTest.kt`

**Interfaces:**
- Consumes: —
- Produces: `class DevicePrefs(private val store: DataStore<Preferences>)` với `val lastMac: Flow<String?>` và `suspend fun saveMac(mac: String)`, `suspend fun clear()`.

- [ ] **Step 1: Viết DevicePrefs.kt**

```kotlin
package com.example.koistock.device

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DevicePrefs(private val store: DataStore<Preferences>) {
    private val keyMac = stringPreferencesKey("last_mac")
    val lastMac: Flow<String?> = store.data.map { it[keyMac] }
    suspend fun saveMac(mac: String) { store.edit { it[keyMac] = mac } }
    suspend fun clear() { store.edit { it.remove(keyMac) } }
}
```

- [ ] **Step 2: Viết DevicePrefsTest.kt (DataStore in-memory qua tmp file)**

```kotlin
package com.example.koistock.device

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DevicePrefsTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun store(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { tmp.newFile("prefs.preferences_pb") }

    @Test fun saveMac_thenRead_returnsMac() = runTest {
        val prefs = DevicePrefs(store())
        prefs.saveMac("AA:BB:CC:DD:EE:FF")
        assertEquals("AA:BB:CC:DD:EE:FF", prefs.lastMac.first())
    }
    @Test fun default_isNull() = runTest {
        assertNull(DevicePrefs(store()).lastMac.first())
    }
}
```

- [ ] **Step 3: Chạy test — phải PASS**

Run: `./gradlew testDebugUnitTest --tests "com.example.koistock.device.DevicePrefsTest"`
Expected: PASS (2 test).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/koistock/device/DevicePrefs.kt app/src/test/java/com/example/koistock/device/DevicePrefsTest.kt
git commit -m "feat: DevicePrefs persists last connected R6 MAC"
```

---

### Task 6: `ConnectionViewModel` — state machine + auto-reconnect

**Files:**
- Create: `app/src/main/java/com/example/koistock/ui/connection/ConnectionViewModel.kt`
- Test: `app/src/test/java/com/example/koistock/ui/connection/ConnectionViewModelTest.kt`

**Interfaces:**
- Consumes: `RfidReader`, `ConnectionState`, `BleDeviceInfo` (Task 2); `DevicePrefs` (Task 5).
- Produces: `class ConnectionViewModel(private val reader: RfidReader, private val prefs: DevicePrefs, private val scope: CoroutineScope)` với: `val state: StateFlow<ConnectionState>`, `val devices: StateFlow<List<BleDeviceInfo>>`, `fun scan()`, `fun connect(mac: String)`, `fun disconnect()`, `suspend fun tryAutoReconnect(): Boolean`. Khi connect thành công → `prefs.saveMac(mac)`.

- [ ] **Step 1: Viết test trước — ConnectionViewModelTest.kt**

```kotlin
package com.example.koistock.ui.connection

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.example.koistock.device.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ConnectionViewModelTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun prefs() = DevicePrefs(
        PreferenceDataStoreFactory.create { tmp.newFile("p.preferences_pb") }
    )

    @Test fun connect_success_savesMac_andState() = runTest {
        val reader = FakeRfidReader()
        val p = prefs()
        val vm = ConnectionViewModel(reader, p, backgroundScope)
        vm.connect("AA:BB:CC:DD:EE:FF")
        advanceUntilIdle()
        assertEquals(ConnectionState.Connected("AA:BB:CC:DD:EE:FF"), vm.state.value)
        assertEquals("AA:BB:CC:DD:EE:FF", p.lastMac.first())
    }

    @Test fun scan_populatesDevices() = runTest {
        val reader = FakeRfidReader()
        val vm = ConnectionViewModel(reader, prefs(), backgroundScope)
        vm.scan()
        advanceUntilIdle()
        assertEquals(1, vm.devices.value.size)
        assertEquals("AA:BB:CC:DD:EE:FF", vm.devices.value.first().mac)
    }

    @Test fun autoReconnect_noSavedMac_returnsFalse() = runTest {
        val vm = ConnectionViewModel(FakeRfidReader(), prefs(), backgroundScope)
        assertFalse(vm.tryAutoReconnect())
    }

    @Test fun autoReconnect_withSavedMac_connects() = runTest {
        val reader = FakeRfidReader()
        val p = prefs().also { it.saveMac("AA:BB:CC:DD:EE:FF") }
        val vm = ConnectionViewModel(reader, p, backgroundScope)
        assertTrue(vm.tryAutoReconnect())
        assertEquals(ConnectionState.Connected("AA:BB:CC:DD:EE:FF"), vm.state.value)
    }
}
```

- [ ] **Step 2: Chạy test — phải FAIL (chưa có lớp)**

Run: `./gradlew testDebugUnitTest --tests "com.example.koistock.ui.connection.ConnectionViewModelTest"`
Expected: FAIL biên dịch ("Unresolved reference: ConnectionViewModel").

- [ ] **Step 3: Viết ConnectionViewModel.kt**

```kotlin
package com.example.koistock.ui.connection

import com.example.koistock.device.BleDeviceInfo
import com.example.koistock.device.ConnectionState
import com.example.koistock.device.DevicePrefs
import com.example.koistock.device.RfidReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ConnectionViewModel(
    private val reader: RfidReader,
    private val prefs: DevicePrefs,
    private val scope: CoroutineScope,
) {
    val state: StateFlow<ConnectionState> = reader.connectionState

    private val _devices = MutableStateFlow<List<BleDeviceInfo>>(emptyList())
    val devices: StateFlow<List<BleDeviceInfo>> = _devices.asStateFlow()

    fun scan() {
        _devices.value = emptyList()
        scope.launch {
            reader.startDeviceScan().collect { d ->
                _devices.update { list -> if (list.any { it.mac == d.mac }) list else list + d }
            }
        }
    }

    fun connect(mac: String) = scope.launch {
        if (reader.connect(mac)) prefs.saveMac(mac)
    }

    fun disconnect() = reader.disconnect()

    suspend fun tryAutoReconnect(): Boolean {
        val mac = prefs.lastMac.first() ?: return false
        return reader.connect(mac).also { if (it) prefs.saveMac(mac) }
    }
}
```

- [ ] **Step 4: Chạy test — phải PASS**

Run: `./gradlew testDebugUnitTest --tests "com.example.koistock.ui.connection.ConnectionViewModelTest"`
Expected: PASS (4 test).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/koistock/ui/connection/ConnectionViewModel.kt app/src/test/java/com/example/koistock/ui/connection/ConnectionViewModelTest.kt
git commit -m "feat: ConnectionViewModel with scan, connect, auto-reconnect"
```

---

### Task 7: Màn Ghép nối (Compose) + quyền BLE (verify thủ công)

**Files:**
- Create: `app/src/main/java/com/example/koistock/ui/connection/PairingScreen.kt`

**Interfaces:**
- Consumes: `ConnectionViewModel` (Task 6), `ConnectionState`, `BleDeviceInfo`.
- Produces: `@Composable fun PairingScreen(vm: ConnectionViewModel, onConnected: () -> Unit)`.

> UI phần cứng — verify thủ công.

- [ ] **Step 1: Viết PairingScreen.kt**

```kotlin
package com.example.koistock.ui.connection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.koistock.device.ConnectionState

@Composable
fun PairingScreen(vm: ConnectionViewModel, onConnected: () -> Unit) {
    val state by vm.state.collectAsState()
    val devices by vm.devices.collectAsState()

    LaunchedEffect(state) { if (state is ConnectionState.Connected) onConnected() }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Ghép nối đầu đọc R6", style = MaterialTheme.typography.headlineSmall)
        Text("Trạng thái: " + when (state) {
            is ConnectionState.Connected -> "Đã nối"
            is ConnectionState.Connecting -> "Đang nối…"
            ConnectionState.Scanning -> "Đang tìm…"
            ConnectionState.Disconnected -> "Chưa nối"
        })
        Button(onClick = { vm.scan() }, modifier = Modifier.fillMaxWidth()) { Text("Tìm thiết bị") }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(devices) { d ->
                ListItem(
                    headlineContent = { Text(d.name) },
                    supportingContent = { Text("${d.mac} · RSSI ${d.rssi}") },
                    trailingContent = { Button(onClick = { vm.connect(d.mac) }) { Text("Nối") } },
                )
            }
        }
    }
}
```

- [ ] **Step 2: Xin quyền BLE runtime**

Trong `MainActivity`, trước khi hiện `PairingScreen`, request runtime các quyền theo phiên bản: API 31+ → `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`; API ≤30 → `ACCESS_FINE_LOCATION`. Dùng `rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions())`.

```kotlin
val perms = if (android.os.Build.VERSION.SDK_INT >= 31)
    arrayOf(android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT)
else arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
```

- [ ] **Step 3: Verify thủ công trên R6**

Chạy app → cấp quyền → "Tìm thiết bị" thấy R6 → "Nối" → trạng thái "Đã nối" → `onConnected()` được gọi.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/koistock/ui/connection/PairingScreen.kt app/src/main/java/com/example/koistock/MainActivity.kt
git commit -m "feat: R6 pairing screen with BLE runtime permissions (manual-verified)"
```

---

### Task 8: Firestore provider + repositories + Anonymous Auth

**Files:**
- Create: `app/src/main/java/com/example/koistock/data/remote/FirestoreProvider.kt`
- Create: `app/src/main/java/com/example/koistock/data/remote/ProductRepository.kt`
- Create: `app/src/main/java/com/example/koistock/data/remote/TagRepository.kt`
- Create: `app/src/main/java/com/example/koistock/data/remote/LocationRepository.kt`
- Create: `app/src/main/java/com/example/koistock/data/remote/TransactionRepository.kt`

**Interfaces:**
- Consumes: models (Task 3).
- Produces:
  - `object FirestoreProvider { fun db(): FirebaseFirestore; suspend fun ensureSignedIn() }`
  - `class ProductRepository(db)` : `suspend fun getBySku(sku): Product?`, `fun observeAll(): Flow<List<Product>>`, `suspend fun upsert(p: Product)`
  - `class TagRepository(db)` : `suspend fun getByEpc(epc): TagMapping?`, `suspend fun upsert(t: TagMapping)`
  - `class LocationRepository(db)` : `fun observeAll(): Flow<List<LocationNode>>`, `suspend fun upsert(l: LocationNode)`
  - `class TransactionRepository(db)` : `suspend fun append(t: Transaction)`

> Repositories bọc Firestore SDK (khó unit-test thuần) → verify bằng test tích hợp emulator hoặc thủ công. Ở plan này viết code + verify thủ công 1 vòng ghi/đọc.

- [ ] **Step 1: Viết FirestoreProvider.kt**

```kotlin
package com.example.koistock.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object FirestoreProvider {
    fun db(): FirebaseFirestore = FirebaseFirestore.getInstance()
    suspend fun ensureSignedIn() {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) auth.signInAnonymously().await()
    }
}
```

- [ ] **Step 2: Viết ProductRepository.kt**

```kotlin
package com.example.koistock.data.remote

import com.example.koistock.data.model.Product
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ProductRepository(private val db: FirebaseFirestore) {
    private val col get() = db.collection("products")
    suspend fun getBySku(sku: String): Product? =
        col.document(sku).get().await().let { if (it.exists()) Product.fromMap(sku, it.data!!) else null }
    fun observeAll(): Flow<List<Product>> = callbackFlow {
        val reg = col.addSnapshotListener { snap, _ ->
            trySend(snap?.documents?.mapNotNull { d -> d.data?.let { Product.fromMap(d.id, it) } } ?: emptyList())
        }
        awaitClose { reg.remove() }
    }
    suspend fun upsert(p: Product) { col.document(p.sku).set(p.toMap()).await() }
}
```

- [ ] **Step 3: Viết TagRepository.kt, LocationRepository.kt, TransactionRepository.kt**

```kotlin
// TagRepository.kt
package com.example.koistock.data.remote
import com.example.koistock.data.model.TagMapping
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
class TagRepository(private val db: FirebaseFirestore) {
    private val col get() = db.collection("tags")
    suspend fun getByEpc(epc: String): TagMapping? =
        col.document(epc).get().await().let { if (it.exists()) TagMapping.fromMap(epc, it.data!!) else null }
    suspend fun upsert(t: TagMapping) { col.document(t.epc).set(t.toMap()).await() }
}
```
```kotlin
// LocationRepository.kt
package com.example.koistock.data.remote
import com.example.koistock.data.model.LocationNode
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
class LocationRepository(private val db: FirebaseFirestore) {
    private val col get() = db.collection("locations")
    fun observeAll(): Flow<List<LocationNode>> = callbackFlow {
        val reg = col.addSnapshotListener { snap, _ ->
            trySend(snap?.documents?.mapNotNull { d -> d.data?.let { LocationNode.fromMap(d.id, it) } } ?: emptyList())
        }
        awaitClose { reg.remove() }
    }
    suspend fun upsert(l: LocationNode) { col.document(l.code).set(l.toMap()).await() }
}
```
```kotlin
// TransactionRepository.kt
package com.example.koistock.data.remote
import com.example.koistock.data.model.Transaction
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
class TransactionRepository(private val db: FirebaseFirestore) {
    private val col get() = db.collection("transactions")
    suspend fun append(t: Transaction) { col.document(t.id).set(t.toMap()).await() }
}
```

- [ ] **Step 4: Biên dịch**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Verify thủ công 1 vòng ghi/đọc**

Trong `MainActivity` gọi `FirestoreProvider.ensureSignedIn()`, sau đó `ProductRepository(db).upsert(Product("SKU-TEST","Test","cái",TrackingMode.BULK,0,"A-01"))` rồi `getBySku("SKU-TEST")` — xác nhận đọc lại đúng và doc hiện trên Firebase Console.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/koistock/data/remote/
git commit -m "feat: Firestore provider + repositories + anonymous auth"
```

---

### Task 9: App shell — thanh trạng thái + navigation + lưới chức năng

**Files:**
- Create: `app/src/main/java/com/example/koistock/ui/shell/AppShell.kt`
- Create: `app/src/main/java/com/example/koistock/ui/shell/MainMenuScreen.kt`
- Modify: `app/src/main/java/com/example/koistock/MainActivity.kt`

**Interfaces:**
- Consumes: `ConnectionViewModel` (Task 6), `PairingScreen` (Task 7), `ConnectionState`.
- Produces: `@Composable fun AppShell(vm: ConnectionViewModel)` — Scaffold có topBar hiện trạng thái R6 + % pin; NavHost giữa `pairing`, `menu`; `MainMenuScreen` là lưới 9 nút (chỉ điều hướng placeholder route cho các plan sau).

- [ ] **Step 1: Viết MainMenuScreen.kt**

```kotlin
package com.example.koistock.ui.shell

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MainMenuScreen(onOpen: (String) -> Unit) {
    val items = listOf(
        "Tra cứu" to "lookup", "Tìm sản phẩm" to "locate",
        "Quét theo khu" to "count", "Nhập/Xuất" to "inout",
        "Gán tag" to "assign", "Đặt vị trí" to "putaway",
        "Quản lý khu vực" to "zones", "Đồng bộ kho" to "sync",
    )
    LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items) { (label, route) ->
            Card(onClick = { onOpen(route) }, modifier = Modifier.fillMaxWidth().height(96.dp)) {
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
```

- [ ] **Step 2: Viết AppShell.kt (Scaffold + NavHost + status bar)**

```kotlin
package com.example.koistock.ui.shell

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.koistock.device.ConnectionState
import com.example.koistock.ui.connection.ConnectionViewModel
import com.example.koistock.ui.connection.PairingScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(vm: ConnectionViewModel) {
    val nav = rememberNavController()
    val state by vm.state.collectAsState()
    Scaffold(topBar = {
        TopAppBar(title = { Text("KOIStock") }, actions = {
            val label = if (state is ConnectionState.Connected) "● R6" else "○ R6"
            Text(label, modifier = Modifier.padding(end = 12.dp))
        })
    }) { pad ->
        NavHost(nav, startDestination = "pairing", modifier = Modifier.padding(pad)) {
            composable("pairing") { PairingScreen(vm) { nav.navigate("menu") } }
            composable("menu") { MainMenuScreen(onOpen = { /* các plan sau */ }) }
        }
    }
}
```

- [ ] **Step 3: Cập nhật MainActivity.kt để host AppShell**

Khởi tạo `ChainwayRfidReader(applicationContext)`, `DevicePrefs`, `ConnectionViewModel`; gọi `FirestoreProvider.ensureSignedIn()`; `setContent { KOIStockTheme { AppShell(vm) } }`. Trong `onDestroy` gọi `reader.release()`.

- [ ] **Step 4: Verify thủ công**

Chạy app → màn Ghép nối → nối R6 → chuyển sang lưới 9 nút → topBar hiện "● R6". Bấm nút → chưa mở gì (đúng, để plan sau).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/koistock/ui/shell/ app/src/main/java/com/example/koistock/MainActivity.kt
git commit -m "feat: app shell with R6 status bar and main menu navigation"
```

---

## Self-Review

**Spec coverage (Plan 1 phạm vi nền tảng):**
- SDK bọc sau interface `RfidReader` → Task 2, 4 ✓
- Kết nối/ghép nối R6 BLE + auto-reconnect + nhớ MAC + trạng thái → Task 2,5,6,7,9 ✓
- Quyền BLE (API 31+ & ≤30) → Task 1,7 ✓
- Mô hình dữ liệu products/tags/transactions/locations + trackingMode + Khu→Kệ → Task 3 ✓
- Firestore nguồn sự thật + Anonymous Auth + repositories → Task 8 ✓
- Thanh trạng thái + % pin + điều hướng 9 chức năng → Task 9 ✓ (nút bấm mở màn: để Plan 2–5)
- Nghiệp vụ (tra cứu, tìm, kiểm kê, nhập/xuất, gán tag, đặt vị trí, quản lý khu, đồng bộ, HID) → **ngoài phạm vi Plan 1**, ở Plan 2–5.

**Placeholder scan:** không có "TBD/TODO"; các bước phần cứng/UI ghi rõ verify thủ công với tiêu chí cụ thể (không phải placeholder). ✓

**Type consistency:** `RfidReader` dùng nhất quán ở Task 2/4/6; `ConnectionState` 4 nhánh dùng đồng bộ ở Task 2/6/7/9; models `toMap/fromMap` khớp Task 3 ↔ repositories Task 8. ✓

**Rủi ro cần lưu ý khi thực thi:** chữ ký callback SDK Chainway (Task 4) có thể lệch tên/tham số so với javadoc phiên bản này — Step 2 của Task 4 dặn sửa theo javadoc khi biên dịch lỗi.

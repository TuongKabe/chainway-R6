# Chainway R6 Verified Configuration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Đọc và xác nhận cấu hình thực tế của Chainway R6, quản lý region Việt Nam dùng chung, và hiển thị/áp dụng thông số riêng của từng chức năng trong Setting.

**Architecture:** Tách cấu hình mong muốn (`ScanProfile`, `R6DeviceConfig`) khỏi snapshot đọc từ phần cứng (`R6ConfigSnapshot`). `RfidReader` trả về `ConfigApplyResult` có kết quả từng lệnh và danh sách sai lệch; một ViewModel Setting điều phối lưu, áp dụng và đọc lại. Các ViewModel quét chỉ bắt đầu inventory sau khi cấu hình quan trọng được xác nhận.

**Tech Stack:** Kotlin, Android, Jetpack Compose Material 3, Coroutines/Flow, Preferences DataStore, Chainway `c72_sdk`, JUnit 4.

## Global Constraints

- Region là cấu hình chung của R6 và không được profile chức năng ghi đè.
- Cài mới mặc định Việt Nam: SDK code `0x35`, dải `918–923 MHz`.
- Không tự đặt mọi chức năng về 30 dBm.
- Phải kiểm tra giá trị Boolean/exception của từng lệnh SDK và đọc lại một lần sau khi ghi.
- Công suất, region, Session và Q sai lệch phải chặn bắt đầu quét.
- Getter không được SDK/firmware hỗ trợ phải mang trạng thái `UNSUPPORTED`, không suy đoán là đã xác nhận.
- Không thay đổi SKU, EPC, dữ liệu kho hoặc lịch sử giao dịch.

## File Structure

- Create `app/src/main/java/com/example/koistock/device/R6Configuration.kt`: region, snapshot, trạng thái trường và kết quả áp dụng thuần Kotlin.
- Modify `app/src/main/java/com/example/koistock/device/ScanProfile.kt`: khai báo rõ read mode và ánh xạ Miller hiện có.
- Modify `app/src/main/java/com/example/koistock/device/ScanProfileStore.kt`: lưu region chung và trường profile mới tương thích dữ liệu cũ.
- Modify `app/src/main/java/com/example/koistock/device/RfidReader.kt`: API đọc snapshot, đặt region và áp dụng có kết quả.
- Modify `app/src/main/java/com/example/koistock/device/ChainwayRfidReader.kt`: adapter SDK, kiểm tra từng lệnh và readback.
- Modify `app/src/test/java/com/example/koistock/device/FakeRfidReader.kt`: fake điều khiển được snapshot/kết quả.
- Create `app/src/main/java/com/example/koistock/ui/settings/R6SettingsViewModel.kt`: state machine cho đọc/lưu/xác nhận.
- Modify `app/src/main/java/com/example/koistock/ui/settings/SettingsScreen.kt`: card Thiết bị R6.
- Modify `app/src/main/java/com/example/koistock/ui/settings/ScanConfigScreen.kt`: giá trị yêu cầu/thực tế và trạng thái lưu.
- Modify `app/src/main/java/com/example/koistock/ui/shell/AppShell.kt`: wiring reader/store/ViewModel.
- Modify sáu ViewModel quét: xử lý `ConfigApplyResult` trước khi cho quét.
- Create/modify unit tests tương ứng dưới `app/src/test/java/com/example/koistock/`.

---

### Task 1: Mô hình cấu hình và DataStore tương thích dữ liệu cũ

**Files:**
- Create: `app/src/main/java/com/example/koistock/device/R6Configuration.kt`
- Modify: `app/src/main/java/com/example/koistock/device/ScanProfile.kt`
- Modify: `app/src/main/java/com/example/koistock/device/ScanProfileStore.kt`
- Create: `app/src/test/java/com/example/koistock/device/ScanProfileStoreTest.kt`
- Create: `app/src/test/java/com/example/koistock/device/R6ConfigurationTest.kt`

**Interfaces:**
- Produces: `R6Region`, `R6DeviceConfig`, `Readback<T>`, `R6ConfigSnapshot`, `ConfigField`, `ConfigCommandResult`, `ConfigApplyResult.isBlocking`, `ScanReadMode`.
- Produces: `ScanProfileStore.deviceConfig(): Flow<R6DeviceConfig>` and `saveDeviceConfig(config)`.

- [ ] **Step 1: Viết test thất bại cho region, trạng thái readback và sai lệch quan trọng**

```kotlin
@Test fun vietnam_region_uses_chainway_code_0x35() {
    assertEquals(0x35, R6Region.VIETNAM.sdkCode)
    assertEquals("918–923 MHz", R6Region.VIETNAM.bandLabel)
}

@Test fun power_mismatch_is_blocking() {
    val result = ConfigApplyResult(
        requested = ScanProfile(power = 30),
        commands = listOf(ConfigCommandResult(ConfigField.POWER, true)),
        snapshot = R6ConfigSnapshot(power = Readback.Value(26)),
    )
    assertTrue(result.isBlocking)
    assertTrue(result.mismatches.any { it.field == ConfigField.POWER })
}

@Test fun unsupported_optional_field_does_not_block() {
    val result = verifiedResult(tagFocus = Readback.Unsupported)
    assertFalse(result.isBlocking)
}
```

- [ ] **Step 2: Chạy test để xác nhận RED**

Run:
`$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "com.example.koistock.device.R6ConfigurationTest"`

Expected: FAIL vì các type cấu hình R6 chưa tồn tại.

- [ ] **Step 3: Tạo model thuần Kotlin với so sánh trường quan trọng**

```kotlin
enum class R6Region(val sdkCode: Int, val label: String, val bandLabel: String) {
    VIETNAM(0x35, "Việt Nam", "918–923 MHz"),
    USA(0x08, "USA", "902–928 MHz"),
    EUROPE(0x04, "Europe", "865–868 MHz");

    companion object { fun fromSdkCode(code: Int) = entries.firstOrNull { it.sdkCode == code } }
}

data class R6DeviceConfig(val region: R6Region = R6Region.VIETNAM)
sealed interface Readback<out T> {
    data class Value<T>(val value: T) : Readback<T>
    data object Unsupported : Readback<Nothing>
    data class Failed(val message: String) : Readback<Nothing>
}
enum class ConfigField { POWER, REGION, SESSION, Q, MILLER, TAG_FOCUS, FAST_ID, READ_MODE }
data class ConfigCommandResult(val field: ConfigField, val success: Boolean, val message: String? = null)
data class R6ConfigSnapshot(
    val power: Readback<Int>,
    val region: Readback<R6Region>,
    val session: Readback<Int>,
    val q: Readback<Int>,
    val millerM: Readback<Int>,
    val tagFocus: Readback<Boolean> = Readback.Unsupported,
    val fastId: Readback<Boolean> = Readback.Unsupported,
)
data class ConfigMismatch(val field: ConfigField, val requested: String, val actual: String)
data class ConfigApplyResult(
    val requested: ScanProfile,
    val requestedRegion: R6Region,
    val commands: List<ConfigCommandResult>,
    val snapshot: R6ConfigSnapshot,
) {
    val mismatches: List<ConfigMismatch> get() = compareRequestedToSnapshot(this)
    val isBlocking: Boolean get() = commands.any { !it.success && it.field in criticalFields } ||
        mismatches.any { it.field in criticalFields }
}
private val criticalFields = setOf(ConfigField.POWER, ConfigField.REGION, ConfigField.SESSION, ConfigField.Q)
```

- [ ] **Step 4: Viết test DataStore cho mặc định Việt Nam và profile cũ**

```kotlin
@Test fun missing_region_defaults_to_vietnam() = runTest {
    assertEquals(R6Region.VIETNAM, store.deviceConfig().first().region)
}

@Test fun old_profile_without_read_mode_keeps_epc_default() = runTest {
    seedOldPowerOnly(ScanFunction.LOCATE, 26)
    assertEquals(ScanReadMode.EPC, store.profile(ScanFunction.LOCATE).first().readMode)
}
```

- [ ] **Step 5: Bổ sung enum và persistence tối thiểu**

```kotlin
enum class ScanReadMode { EPC, EPC_AND_TID }

data class ScanProfile(
    // giữ nguyên trường hiện có
    val readMode: ScanReadMode = ScanReadMode.EPC,
)

fun deviceConfig(): Flow<R6DeviceConfig> = dataStore.data.map { prefs ->
    R6DeviceConfig(
        region = prefs[regionKey]?.let { saved -> R6Region.entries.firstOrNull { it.name == saved } }
            ?: R6Region.VIETNAM,
    )
}

suspend fun saveDeviceConfig(config: R6DeviceConfig) {
    dataStore.edit { it[regionKey] = config.region.name }
}
```

- [ ] **Step 6: Chạy test Task 1 và commit**

Run: `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "com.example.koistock.device.R6ConfigurationTest" --tests "com.example.koistock.device.ScanProfileStoreTest"`

Expected: PASS.

Commit:
```powershell
git add app/src/main/java/com/example/koistock/device/R6Configuration.kt app/src/main/java/com/example/koistock/device/ScanProfile.kt app/src/main/java/com/example/koistock/device/ScanProfileStore.kt app/src/test/java/com/example/koistock/device/R6ConfigurationTest.kt app/src/test/java/com/example/koistock/device/ScanProfileStoreTest.kt
git commit -m "feat: model verified R6 configuration"
```

### Task 2: Adapter Chainway kiểm tra lệnh và đọc lại

**Files:**
- Modify: `app/src/main/java/com/example/koistock/device/RfidReader.kt`
- Modify: `app/src/main/java/com/example/koistock/device/ChainwayRfidReader.kt`
- Modify: `app/src/test/java/com/example/koistock/device/FakeRfidReader.kt`
- Create: `app/src/test/java/com/example/koistock/device/ConfigResultFactoryTest.kt`

**Interfaces:**
- Consumes: model Task 1.
- Produces: `suspend fun readConfigSnapshot(): R6ConfigSnapshot`, `suspend fun setRegion(region: R6Region): ConfigCommandResult`, `suspend fun applyScanConfig(profile, expectedRegion): ConfigApplyResult`.

- [ ] **Step 1: Viết test thất bại cho tổng hợp lệnh SDK và readback**

```kotlin
@Test fun failed_set_power_is_preserved_in_result() {
    val result = buildApplyResult(
        requested = ScanProfile(power = 30),
        region = R6Region.VIETNAM,
        commands = listOf(ConfigCommandResult(ConfigField.POWER, false, "SDK returned false")),
        snapshot = matchingSnapshot(power = 26),
    )
    assertTrue(result.isBlocking)
    assertEquals("SDK returned false", result.commands.single().message)
}
```

- [ ] **Step 2: Chạy test để xác nhận RED**

Run: `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "com.example.koistock.device.ConfigResultFactoryTest"`

Expected: FAIL vì API mới chưa tồn tại.

- [ ] **Step 3: Đổi contract `RfidReader` và fake**

```kotlin
suspend fun readConfigSnapshot(): R6ConfigSnapshot
suspend fun setRegion(region: R6Region): ConfigCommandResult
suspend fun applyScanConfig(profile: ScanProfile, expectedRegion: R6Region): ConfigApplyResult
```

Fake phải có `nextSnapshot`, `nextApplyResult`, `appliedProfiles`, và không truy cập SDK thật.

- [ ] **Step 4: Implement readback trong `ChainwayRfidReader`**

```kotlin
override suspend fun readConfigSnapshot(): R6ConfigSnapshot = withContext(ioDispatcher) {
    val gen2 = runCatching { sdk.gen2 }
    R6ConfigSnapshot(
        power = readback("power") { sdk.power }.requirePositive(),
        region = readback("region") { sdk.frequencyMode }
            .mapValue { R6Region.fromSdkCode(it) ?: error("Unknown region code $it") },
        session = gen2.toReadback { it?.querySession ?: error("Gen2 unavailable") },
        q = gen2.toReadback { it?.startQ ?: error("Gen2 unavailable") },
        millerM = gen2.toReadback { it?.queryM ?: error("Gen2 unavailable") },
        tagFocus = Readback.Unsupported,
        fastId = Readback.Unsupported,
    )
}
```

Tên property SDK phải được xác nhận bằng compile với AAR hiện tại; nếu Kotlin property không resolve thì gọi getter Java tương ứng `getPower()`, `getFrequencyMode()`, `getGen2()`.

- [ ] **Step 5: Implement apply theo từng lệnh, không bọc chung một `runCatching`**

```kotlin
override suspend fun applyScanConfig(profile: ScanProfile, expectedRegion: R6Region): ConfigApplyResult {
    val p = profile.sanitized()
    val commands = buildList {
        add(command(ConfigField.READ_MODE) { if (p.readMode == ScanReadMode.EPC) sdk.setEPCMode() else sdk.setFastID(true) })
        add(command(ConfigField.POWER) { sdk.setPower(p.power) })
        val gen2 = sdk.gen2 ?: Gen2Entity()
        gen2.querySession = p.session
        gen2.queryTarget = 0
        gen2.startQ = p.q
        gen2.queryM = p.millerM
        add(command(ConfigField.SESSION) { sdk.setGen2(gen2) })
        add(command(ConfigField.TAG_FOCUS) { sdk.setTagFocus(p.tagFocus) })
        add(command(ConfigField.FAST_ID) { sdk.setFastID(p.readMode == ScanReadMode.EPC_AND_TID) })
    }
    return ConfigApplyResult(p, expectedRegion, commands, readConfigSnapshot())
}
```

Không gọi `setFrequencyMode` trong hàm này. `setRegion()` là đường ghi region duy nhất.

- [ ] **Step 6: Chạy compile/test và commit**

Run: `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:compileDebugKotlin testDebugUnitTest --tests "com.example.koistock.device.ConfigResultFactoryTest"`

Expected: BUILD SUCCESSFUL.

Commit:
```powershell
git add app/src/main/java/com/example/koistock/device/RfidReader.kt app/src/main/java/com/example/koistock/device/ChainwayRfidReader.kt app/src/test/java/com/example/koistock/device/FakeRfidReader.kt app/src/test/java/com/example/koistock/device/ConfigResultFactoryTest.kt
git commit -m "feat: verify Chainway R6 configuration"
```

### Task 3: ViewModel Setting cho region và xác nhận profile

**Files:**
- Create: `app/src/main/java/com/example/koistock/ui/settings/R6SettingsViewModel.kt`
- Create: `app/src/test/java/com/example/koistock/ui/settings/R6SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `RfidReader`, `ScanProfileStore`, model Task 1.
- Produces: `R6SettingsState`, `refresh()`, `saveRegion(region)`, `saveProfile(function, profile)`, `resetProfile(function)`.

- [ ] **Step 1: Viết test thất bại cho ba luồng lưu**

```kotlin
@Test fun save_region_when_connected_sets_reads_back_then_marks_verified() = runTest {
    fake.connection(ConnectionState.Connected("R6"))
    fake.nextSnapshot = snapshot(region = R6Region.VIETNAM)
    vm.saveRegion(R6Region.VIETNAM)
    advanceUntilIdle()
    assertEquals(VerificationStatus.VERIFIED, vm.state.value.regionStatus)
}

@Test fun save_profile_when_disconnected_persists_without_applying() = runTest {
    vm.saveProfile(ScanFunction.LOCATE, ScanProfile(power = 30))
    advanceUntilIdle()
    assertEquals("Đã lưu, chưa áp dụng lên R6", vm.state.value.message)
    assertTrue(fake.appliedProfiles.isEmpty())
}

@Test fun mismatch_exposes_requested_and_actual_values() = runTest {
    fake.nextApplyResult = mismatchResult(ConfigField.POWER, "30 dBm", "26 dBm")
    vm.saveProfile(ScanFunction.LOCATE, ScanProfile(power = 30))
    advanceUntilIdle()
    assertTrue(vm.state.value.applyResult!!.isBlocking)
}
```

- [ ] **Step 2: Chạy test để xác nhận RED**

Run: `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "com.example.koistock.ui.settings.R6SettingsViewModelTest"`

Expected: FAIL vì ViewModel chưa tồn tại.

- [ ] **Step 3: Implement state machine với một operation tại một thời điểm**

```kotlin
data class R6SettingsState(
    val connected: Boolean = false,
    val desiredDeviceConfig: R6DeviceConfig = R6DeviceConfig(),
    val snapshot: R6ConfigSnapshot? = null,
    val applyResult: ConfigApplyResult? = null,
    val loading: Boolean = false,
    val message: String? = null,
)

fun saveProfile(function: ScanFunction, profile: ScanProfile) = scope.launch {
    store.save(function, profile)
    if (reader.connectionState.value !is ConnectionState.Connected) {
        updateMessage("Đã lưu, chưa áp dụng lên R6")
        return@launch
    }
    val region = store.deviceConfig().first().region
    val result = reader.applyScanConfig(profile, region)
    mutableState.update { it.copy(applyResult = result, snapshot = result.snapshot, message = result.userMessage()) }
}
```

- [ ] **Step 4: Chạy test ViewModel và commit**

Run: `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "com.example.koistock.ui.settings.R6SettingsViewModelTest"`

Expected: PASS.

Commit:
```powershell
git add app/src/main/java/com/example/koistock/ui/settings/R6SettingsViewModel.kt app/src/test/java/com/example/koistock/ui/settings/R6SettingsViewModelTest.kt
git commit -m "feat: manage verified R6 settings state"
```

### Task 4: Giao diện Setting chung và theo chức năng

**Files:**
- Modify: `app/src/main/java/com/example/koistock/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/example/koistock/ui/settings/ScanConfigScreen.kt`
- Modify: `app/src/main/java/com/example/koistock/ui/shell/AppShell.kt`
- Create: `app/src/test/java/com/example/koistock/ui/settings/R6SettingsFormattingTest.kt`

**Interfaces:**
- Consumes: `R6SettingsState` và actions Task 3.
- Produces: card `Thiết bị R6`, region selector chung, requested/actual rows và cảnh báo tầm đọc.

- [ ] **Step 1: Viết test formatter thất bại cho giá trị thực tế và sai lệch**

```kotlin
@Test fun mismatch_text_names_requested_and_actual_power() {
    val text = ConfigDisplay.mismatch(ConfigField.POWER, "30 dBm", "26 dBm")
    assertEquals("Yêu cầu 30 dBm · R6 thực tế 26 dBm", text)
}

@Test fun unsupported_readback_is_not_presented_as_verified() {
    assertEquals("R6 không hỗ trợ đọc lại", ConfigDisplay.readback(Readback.Unsupported))
}
```

- [ ] **Step 2: Chạy test để xác nhận RED, rồi tạo formatter thuần Kotlin**

Run: `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "com.example.koistock.ui.settings.R6SettingsFormattingTest"`

Expected trước implementation: FAIL. Expected sau implementation: PASS.

- [ ] **Step 3: Bổ sung card `Thiết bị R6`**

Card phải render đúng các state:

```kotlin
R6DeviceCard(
    connected = state.connected,
    selectedRegion = state.desiredDeviceConfig.region,
    actualRegion = state.snapshot?.region,
    loading = state.loading,
    onRegionSelected = vm::saveRegion,
    onRefresh = vm::refresh,
)
```

Các lựa chọn hiển thị `Việt Nam · 918–923 MHz`; nút lưu disable khi mất kết nối nhưng lựa chọn mong muốn vẫn lưu qua action riêng và hiện `Chưa áp dụng lên máy`.

- [ ] **Step 4: Bổ sung read mode và requested/actual vào `ScanConfigScreen`**

```kotlin
ChipRow(
    options = listOf(ScanReadMode.EPC to "Chỉ EPC", ScanReadMode.EPC_AND_TID to "EPC + TID"),
    selected = draft.readMode,
    onSelect = { draft = draft.copy(readMode = it) },
)
```

Hiển thị snapshot bên dưới Power, Session, Q, Miller; TagFocus/FastID dùng nhãn `Không hỗ trợ đọc lại` nếu SDK thiếu getter. Thêm nội dung cố định: `Khoảng cách trên 26 m là kết quả thử nghiệm tối ưu với thẻ phù hợp; 30 dBm không bảo đảm mọi thẻ đạt khoảng cách này.`

- [ ] **Step 5: Wire một `R6SettingsViewModel` dùng chung trong `AppShell`**

Tạo ViewModel bằng `remember(reader, scanProfileStore)`, collect state, truyền actions cho hai màn hình. Không tạo ViewModel mới mỗi lần đổi profile.

- [ ] **Step 6: Compile, chạy test formatter và commit**

Run: `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:compileDebugKotlin testDebugUnitTest --tests "com.example.koistock.ui.settings.R6SettingsFormattingTest"`

Expected: BUILD SUCCESSFUL.

Commit:
```powershell
git add app/src/main/java/com/example/koistock/ui/settings/SettingsScreen.kt app/src/main/java/com/example/koistock/ui/settings/ScanConfigScreen.kt app/src/main/java/com/example/koistock/ui/shell/AppShell.kt app/src/test/java/com/example/koistock/ui/settings/R6SettingsFormattingTest.kt
git commit -m "feat: show requested and actual R6 settings"
```

### Task 5: Chặn quét khi cấu hình quan trọng không được xác nhận

**Files:**
- Modify: `app/src/main/java/com/example/koistock/ui/lookup/LookupViewModel.kt`
- Modify: `app/src/main/java/com/example/koistock/ui/locate/LocateViewModel.kt`
- Modify: `app/src/main/java/com/example/koistock/ui/count/CountViewModel.kt`
- Modify: `app/src/main/java/com/example/koistock/ui/inout/InOutViewModel.kt`
- Modify: `app/src/main/java/com/example/koistock/ui/putaway/PutawayViewModel.kt`
- Modify: `app/src/main/java/com/example/koistock/ui/assign/AssignTagViewModel.kt`
- Modify: tests hiện có của sáu ViewModel trong các package tương ứng.

**Interfaces:**
- Consumes: `applyScanConfig(profile, expectedRegion): ConfigApplyResult`.
- Produces: cùng hành vi quét hiện có khi verified; state lỗi và không gọi inventory khi blocking.

- [ ] **Step 1: Bổ sung test RED cho một ViewModel quét đơn và một ViewModel inventory**

```kotlin
@Test fun lookup_does_not_scan_when_config_is_blocking() = runTest {
    fake.nextApplyResult = blockingPowerMismatch()
    vm.onTrigger()
    advanceUntilIdle()
    assertEquals(0, fake.scanSingleCalls)
    assertTrue(vm.state.value.error!!.contains("30 dBm"))
}

@Test fun count_does_not_start_inventory_when_region_is_wrong() = runTest {
    fake.nextApplyResult = blockingRegionMismatch()
    vm.start()
    advanceUntilIdle()
    assertEquals(0, fake.startInventoryCalls)
}
```

- [ ] **Step 2: Chạy hai test để xác nhận RED**

Run: `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "com.example.koistock.ui.lookup.LookupViewModelTest" --tests "com.example.koistock.ui.count.CountViewModelTest"`

Expected: FAIL vì apply hiện chạy fire-and-forget.

- [ ] **Step 3: Thay fire-and-forget bằng cổng xác nhận cấu hình**

Mỗi ViewModel nhận thêm `expectedRegion: R6Region`. Trước scan/inventory:

```kotlin
private suspend fun ensureConfig(): Boolean {
    val result = reader.applyScanConfig(profile, expectedRegion)
    if (!result.isBlocking) return true
    mutableState.update { it.copy(error = result.blockingMessage()) }
    return false
}

fun start() = scope.launch {
    if (!ensureConfig()) return@launch
    reader.startInventory()
}
```

Không áp dụng cấu hình trong `init` bằng coroutine rời vì người dùng có thể bóp cò trước khi coroutine hoàn tất.

- [ ] **Step 4: Lặp test tương ứng cho Locate, InOut, Putaway và Assign**

Mỗi test đặt blocking result, gọi hành động bắt đầu thực tế của ViewModel, và assert fake không nhận `startInventory`, `startLocate`, `scanBurst`, hoặc `writeEpc`.

- [ ] **Step 5: Chạy toàn bộ test sáu ViewModel và commit**

Run: `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "com.example.koistock.ui.lookup.LookupViewModelTest" --tests "com.example.koistock.ui.locate.LocateViewModelTest" --tests "com.example.koistock.ui.count.CountViewModelTest" --tests "com.example.koistock.ui.inout.InOutViewModelTest" --tests "com.example.koistock.ui.putaway.PutawayViewModelTest" --tests "com.example.koistock.ui.assign.AssignTagViewModelTest"`

Expected: PASS.

Commit:
```powershell
git add app/src/main/java/com/example/koistock/ui app/src/test/java/com/example/koistock/ui
git commit -m "fix: block scans when R6 configuration is invalid"
```

### Task 6: Rà soát tích hợp, tài liệu và kiểm chứng cuối

**Files:**
- Modify: `docs/hardware/chainway-r6-scan-tuning.md`
- Modify only if required by compilation/tests: files touched in Tasks 1–5.

**Interfaces:**
- Consumes: toàn bộ feature.
- Produces: build debug sạch, toàn bộ unit test xanh, hướng dẫn kiểm tra trực tiếp trên R6.

- [ ] **Step 1: Cập nhật tài liệu vận hành**

Ghi rõ:

```markdown
1. Kết nối R6.
2. Mở Cài đặt > Thiết bị R6.
3. Chọn Việt Nam 918–923 MHz và Lưu & áp dụng.
4. Xác nhận trạng thái Đã xác nhận.
5. Mở profile chức năng, lưu và kiểm tra cột R6 thực tế.
6. Nếu app báo sai lệch, không tiếp tục quét cho đến khi cấu hình được xác nhận.
```

- [ ] **Step 2: Chạy toàn bộ build và unit test**

Run: `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat --no-daemon --console=plain :app:compileDebugKotlin testDebugUnitTest`

Expected: `BUILD SUCCESSFUL` và không có failed tests.

- [ ] **Step 3: Kiểm tra whitespace và phạm vi diff**

Run: `git diff --check`

Expected: không có output.

Run: `git status --short`

Expected: chỉ có các file thuộc feature và các thay đổi người dùng đã có từ trước; không stage hoặc sửa thay đổi ngoài phạm vi.

- [ ] **Step 4: Kiểm tra thủ công với thiết bị R6 thật**

Expected:

- Region đọc lại là `0x35 / Việt Nam / 918–923 MHz`.
- Power, Session và Q khớp profile vừa lưu.
- Chuyển chức năng không thay region.
- Rút kết nối rồi lưu profile hiển thị `Đã lưu, chưa áp dụng lên R6`.
- Giả lập/quan sát lệnh thất bại hiển thị đúng trường và không bắt đầu quét.

- [ ] **Step 5: Commit tài liệu và sửa tích hợp cuối**

```powershell
git add docs/hardware/chainway-r6-scan-tuning.md
git commit -m "docs: document verified R6 setup"
```

Không commit thay đổi ngoài phạm vi nếu chúng đã tồn tại trong worktree trước feature.

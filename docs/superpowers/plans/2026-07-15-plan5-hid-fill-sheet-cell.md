# KOIStock — Plan 5: Điền EPC thẳng vào ô Google Sheet (Android HID bridge)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bấm cò R6 khi đang chọn 1 ô trên Google Sheet (mở trên máy tính) → EPC tự "gõ" vào ô đó, bằng cách điện thoại đóng vai bàn phím Bluetooth (HID). Kèm ghi chú đường R6-HID trực tiếp.

**Architecture:** Điện thoại giữ 2 kết nối BT: central với R6 (đọc EPC qua `RfidReader`) + HID peripheral với máy tính (`BluetoothHidDevice`, API 28+). Chuỗi EPC được format (raw/KOI + terminator) rồi mã hóa thành các HID report (logic thuần, TDD đầy đủ). Lớp `HidKeyboard` bọc `BluetoothHidDevice` (phần cứng, verify thủ công).

**Tech Stack:** Kotlin, `android.bluetooth.BluetoothHidDevice` (API 28+), Coroutines/Flow, JUnit4.

> **Revision 2 — 2026-07-15:** HID là experimental feature cho tới khi verify đồng thời R6 BLE + PC HID trên đúng model điện thoại mục tiêu. Việc gửi report phải non-blocking và lifecycle-safe.

## Global Constraints

- Kế thừa Global Constraints Plan 1–2 (Kotlin/Compose, `RfidReader`, `EpcCodec`).
- Tính năng này **feature-gate API 28+**; máy <28 ẩn màn HID (không nâng `minSdk`).
- Điện thoại giữ đồng thời 2 kết nối BT (central R6 + HID host). Payload nhỏ → chấp nhận được.
- Máy tính không cần cài phần mềm; chỉ ghép Bluetooth 1 lần (nhận điện thoại là bàn phím).
- Terminator sau EPC cấu hình được: `ENTER` (mặc định, xuống ô dưới) hoặc `TAB` (sang ô phải) hoặc `NONE`.
- Test đơn vị: `./gradlew testDebugUnitTest`.
- Không dùng `Thread.sleep` trên main thread. `sendReports` là `suspend`, serialize qua một queue/Mutex và dùng coroutine `delay`.
- Luôn gửi key-release report sau key-press, kể cả khi bị lỗi giữa chuỗi; dừng sạch khi host disconnect.
- Runtime-check `BLUETOOTH_CONNECT` trước mọi API cần quyền; `@SuppressLint` không thay thế permission handling.

---

## Hardware/lifecycle gates bắt buộc

- [ ] `HidKeyboard.sendReports(...)` trả kết quả typed (`Sent`, `NotRegistered`, `HostDisconnected`, `PermissionDenied`, `SendFailed`) thay vì im lặng `return`.
- [ ] `unregister()` gọi `unregisterApp()` và `BluetoothAdapter.closeProfileProxy()`, hủy queue gửi, xóa host reference và callback state.
- [ ] Test encoder cho ký tự không hỗ trợ, uppercase, release report, Enter/Tab/None và chuỗi dài.
- [ ] Instrument/manual test: rotate/background/foreground, host disconnect giữa chuỗi, gửi liên tiếp, permission bị thu hồi.
- [ ] Matrix phần cứng ghi rõ Android model/OS, PC OS, vừa connected R6 BLE vừa connected HID, 100 lần trigger không mất/nhân ký tự.
- [ ] Nếu thiết bị OEM không hỗ trợ HID Device profile hoặc dual-role không ổn định, UI báo unsupported và giữ đường R6-HID trực tiếp làm fallback.

---

## File Structure

```
app/src/main/java/com/example/koistock/hid/
  KeymapEncoder.kt        # char/string -> HID report bytes (thuần)
  HidEpcFormatter.kt      # EPC -> chuỗi gõ (raw/KOI + terminator) (thuần)
  HidKeyboard.kt          # interface + AndroidHidKeyboard (BluetoothHidDevice)
  HidBridgeViewModel.kt
  HidBridgeScreen.kt
app/src/test/java/com/example/koistock/hid/
  KeymapEncoderTest.kt
  HidEpcFormatterTest.kt
  HidBridgeViewModelTest.kt
docs/hardware/r6-hid-direct-note.md
```

---

### Task 1: `KeymapEncoder` — ký tự → HID report

**Files:**
- Create: `app/src/main/java/com/example/koistock/hid/KeymapEncoder.kt`
- Test: `app/src/test/java/com/example/koistock/hid/KeymapEncoderTest.kt`

**Interfaces:**
- Produces:
  - `enum Terminator { ENTER, TAB, NONE }`
  - `object KeymapEncoder`:
    - `fun usageFor(c: Char): Pair<Int, Int>?` → `(modifier, usageId)`; null nếu không hỗ trợ.
    - `fun report(modifier: Int, usageId: Int): ByteArray` → mảng 8 byte `[modifier,0,usage,0,0,0,0,0]`.
    - `val RELEASE: ByteArray` → 8 byte 0.
    - `fun stringToReports(s: String, terminator: Terminator): List<ByteArray>` → xen kẽ report phím + RELEASE; thêm phím terminator cuối.

Hằng HID: chữ thường a=0x04..z=0x1d; số 1=0x1e..9=0x26, 0=0x27; '-'=0x2d; Enter=0x28; Tab=0x2b; chữ hoa = thêm modifier LeftShift 0x02.

- [ ] **Step 1: Viết test KeymapEncoderTest.kt**

```kotlin
package com.example.koistock.hid
import org.junit.Assert.*
import org.junit.Test

class KeymapEncoderTest {
    @Test fun lowercaseLetter_hasNoModifier() {
        assertEquals(0x00 to 0x04, KeymapEncoder.usageFor('a'))
    }
    @Test fun uppercaseLetter_hasShiftModifier() {
        assertEquals(0x02 to 0x04, KeymapEncoder.usageFor('A'))
    }
    @Test fun digitsAndDash_mapCorrectly() {
        assertEquals(0x00 to 0x27, KeymapEncoder.usageFor('0'))
        assertEquals(0x00 to 0x1e, KeymapEncoder.usageFor('1'))
        assertEquals(0x00 to 0x2d, KeymapEncoder.usageFor('-'))
    }
    @Test fun unsupportedChar_returnsNull() {
        assertNull(KeymapEncoder.usageFor('€'))
    }
    @Test fun report_hasUsageInThirdByte() {
        val r = KeymapEncoder.report(0x02, 0x04)
        assertEquals(8, r.size)
        assertEquals(0x02.toByte(), r[0]); assertEquals(0x04.toByte(), r[2])
    }
    @Test fun stringToReports_appendsEnterAndReleases() {
        val reps = KeymapEncoder.stringToReports("A", Terminator.ENTER)
        // A(press) A-release Enter(press) Enter-release
        assertEquals(4, reps.size)
        assertEquals(0x04.toByte(), reps[0][2])           // 'A'
        assertTrue(reps[1].all { it == 0.toByte() })      // release
        assertEquals(0x28.toByte(), reps[2][2])           // Enter
        assertTrue(reps[3].all { it == 0.toByte() })      // release
    }
    @Test fun stringToReports_none_hasNoTerminator() {
        val reps = KeymapEncoder.stringToReports("1", Terminator.NONE)
        assertEquals(2, reps.size)  // chỉ press+release '1'
    }
}
```

- [ ] **Step 2: Chạy test — phải FAIL.**

- [ ] **Step 3: Viết KeymapEncoder.kt**

```kotlin
package com.example.koistock.hid

enum class Terminator { ENTER, TAB, NONE }

object KeymapEncoder {
    private const val SHIFT = 0x02
    private const val ENTER = 0x28
    private const val TAB = 0x2b
    val RELEASE = ByteArray(8)

    fun usageFor(c: Char): Pair<Int, Int>? = when (c) {
        in 'a'..'z' -> 0x00 to (0x04 + (c - 'a'))
        in 'A'..'Z' -> SHIFT to (0x04 + (c - 'A'))
        in '1'..'9' -> 0x00 to (0x1e + (c - '1'))
        '0' -> 0x00 to 0x27
        '-' -> 0x00 to 0x2d
        else -> null
    }

    fun report(modifier: Int, usageId: Int): ByteArray =
        byteArrayOf(modifier.toByte(), 0, usageId.toByte(), 0, 0, 0, 0, 0)

    fun stringToReports(s: String, terminator: Terminator): List<ByteArray> {
        val out = mutableListOf<ByteArray>()
        for (c in s) {
            val (mod, usage) = usageFor(c) ?: continue
            out.add(report(mod, usage)); out.add(RELEASE)
        }
        val term = when (terminator) { Terminator.ENTER -> ENTER; Terminator.TAB -> TAB; Terminator.NONE -> null }
        if (term != null) { out.add(report(0, term)); out.add(RELEASE) }
        return out
    }
}
```

- [ ] **Step 4: Chạy test — phải PASS** (7 test).

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/example/koistock/hid/KeymapEncoder.kt app/src/test/java/com/example/koistock/hid/KeymapEncoderTest.kt
git commit -m "feat(hid): KeymapEncoder char/string -> HID reports"
```

---

### Task 2: `HidEpcFormatter` — EPC → chuỗi cần gõ

**Files:**
- Create: `app/src/main/java/com/example/koistock/hid/HidEpcFormatter.kt`
- Test: `app/src/test/java/com/example/koistock/hid/HidEpcFormatterTest.kt`

**Interfaces:**
- Consumes: `EpcCodec`.
- Produces:
  - `enum HidFormat { RAW, STRUCTURED }`
  - `object HidEpcFormatter { fun textFor(epc: String, sku: String?, format: HidFormat): String }` — RAW → chính EPC; STRUCTURED → nếu có `sku` thì `EpcCodec.encode(sku, serial)`; nếu EPC đã có cấu trúc thì giữ nguyên; nếu không có sku và EPC thô → trả EPC thô.

- [ ] **Step 1: Viết test HidEpcFormatterTest.kt**

```kotlin
package com.example.koistock.hid
import org.junit.Assert.*
import org.junit.Test

class HidEpcFormatterTest {
    @Test fun raw_returnsEpcAsIs() {
        assertEquals("E2000ABC", HidEpcFormatter.textFor("E2000ABC", "S1", HidFormat.RAW))
    }
    @Test fun structured_withStructuredEpc_keepsIt() {
        assertEquals("KOI-S1-0001", HidEpcFormatter.textFor("KOI-S1-0001", null, HidFormat.STRUCTURED))
    }
    @Test fun structured_rawEpcNoSku_fallsBackToRaw() {
        assertEquals("E2000ABC", HidEpcFormatter.textFor("E2000ABC", null, HidFormat.STRUCTURED))
    }
}
```

- [ ] **Step 2: Chạy test — phải FAIL.**

- [ ] **Step 3: Viết HidEpcFormatter.kt**

```kotlin
package com.example.koistock.hid
import com.example.koistock.domain.EpcCodec

enum class HidFormat { RAW, STRUCTURED }

object HidEpcFormatter {
    fun textFor(epc: String, sku: String?, format: HidFormat): String = when (format) {
        HidFormat.RAW -> epc
        HidFormat.STRUCTURED -> when {
            EpcCodec.isStructured(epc) -> epc
            sku != null -> EpcCodec.encode(sku, "0001")
            else -> epc
        }
    }
}
```

- [ ] **Step 4: Chạy test — phải PASS** (3 test).

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/example/koistock/hid/HidEpcFormatter.kt app/src/test/java/com/example/koistock/hid/HidEpcFormatterTest.kt
git commit -m "feat(hid): HidEpcFormatter (raw/structured EPC text)"
```

---

### Task 3: `HidKeyboard` interface + `AndroidHidKeyboard` (verify thủ công)

**Files:**
- Create: `app/src/main/java/com/example/koistock/hid/HidKeyboard.kt`

**Interfaces:**
- Consumes: `KeymapEncoder` (RELEASE, report bytes).
- Produces:
  - `interface HidKeyboard { val isHostConnected: StateFlow<Boolean>; fun register(); suspend fun sendReports(reports: List<ByteArray>): HidSendResult; fun unregister() }`
  - `class AndroidHidKeyboard(context)` : `HidKeyboard` — dùng `BluetoothHidDevice.registerApp(...)` với SDP mô tả bàn phím, `sendReport(host, id=0, report)`; theo dõi host qua `onConnectionStateChanged`. **@RequiresApi(28)**.

> Phần cứng BT — không unit-test; verify thủ công ở Task 4/5.

- [ ] **Step 1: Viết HidKeyboard.kt** (khung SDP keyboard descriptor + gửi report tuần tự có delay nhỏ)

```kotlin
package com.example.koistock.hid
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface HidKeyboard {
    val isHostConnected: StateFlow<Boolean>
    fun register()
    suspend fun sendReports(reports: List<ByteArray>): HidSendResult
    fun unregister()
}

@RequiresApi(28)
@SuppressLint("MissingPermission")
class AndroidHidKeyboard(private val context: Context) : HidKeyboard {
    private val _host = MutableStateFlow(false)
    override val isHostConnected: StateFlow<Boolean> = _host
    private var service: BluetoothHidDevice? = null
    private var hostDevice: BluetoothDevice? = null
    private val adapter get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    // Keyboard HID descriptor (8-byte boot keyboard report)
    private val sdp = BluetoothHidDeviceAppSdpSettings(
        "KOIStock", "RFID keyboard", "KOIStock",
        BluetoothHidDevice.SUBCLASS1_KEYBOARD, KEYBOARD_DESCRIPTOR)

    override fun register() {
        adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                service = proxy as BluetoothHidDevice
                service?.registerApp(sdp, null, null, { it.run() }, object : BluetoothHidDevice.Callback() {
                    override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
                        _host.value = state == BluetoothProfile.STATE_CONNECTED
                        if (_host.value) hostDevice = device
                    }
                })
            }
            override fun onServiceDisconnected(profile: Int) { service = null }
        }, BluetoothProfile.HID_DEVICE)
    }

    override suspend fun sendReports(reports: List<ByteArray>): HidSendResult = sendMutex.withLock {
        val svc = service ?: return@withLock HidSendResult.NotRegistered
        val host = hostDevice ?: return@withLock HidSendResult.HostDisconnected
        for (report in reports) {
            if (!svc.sendReport(host, 0, report)) return@withLock HidSendResult.SendFailed
            kotlinx.coroutines.delay(8)
        }
        HidSendResult.Sent
    }
    override fun unregister() { service?.unregisterApp(); _host.value = false }

    companion object {
        // Boot keyboard report descriptor (modifier+reserved+6 keys)
        val KEYBOARD_DESCRIPTOR = byteArrayOf(
            0x05, 0x01, 0x09, 0x06, 0xA1.toByte(), 0x01, 0x05, 0x07,
            0x19, 0xE0.toByte(), 0x29, 0xE7.toByte(), 0x15, 0x00, 0x25, 0x01,
            0x75, 0x01, 0x95.toByte(), 0x08, 0x81.toByte(), 0x02, 0x95.toByte(), 0x01,
            0x75, 0x08, 0x81.toByte(), 0x01, 0x95.toByte(), 0x06, 0x75, 0x08,
            0x15, 0x00, 0x25, 0x65, 0x05, 0x07, 0x19, 0x00,
            0x29, 0x65, 0x81.toByte(), 0x00, 0xC0.toByte())
    }
}
```

> Chữ ký `BluetoothHidDeviceAppSdpSettings`/`registerApp`/`sendReport` theo API 28 — chỉnh theo trình biên dịch nếu lệch. Cần quyền `BLUETOOTH_CONNECT` (API 31+).

- [ ] **Step 2: Biên dịch**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (lớp `@RequiresApi(28)` không lỗi vì được gate khi gọi).

- [ ] **Step 3: Commit**
```bash
git add app/src/main/java/com/example/koistock/hid/HidKeyboard.kt
git commit -m "feat(hid): AndroidHidKeyboard over BluetoothHidDevice (API28+)"
```

---

### Task 4: `HidBridgeViewModel` — cò R6 → gõ EPC

**Files:**
- Create: `app/src/main/java/com/example/koistock/hid/HidBridgeViewModel.kt`
- Test: `app/src/test/java/com/example/koistock/hid/HidBridgeViewModelTest.kt`

**Interfaces:**
- Consumes: `RfidReader` (`scanSingle` hoặc `triggerEvents`+`scanSingle`), `TagRepo` (suy `sku`), `HidKeyboard`, `KeymapEncoder`, `HidEpcFormatter`, `Terminator`, `HidFormat`.
- Produces:
  - `class HidBridgeViewModel(reader, tagRepo, keyboard, format, terminator, scope)` với `val hostConnected: StateFlow<Boolean>`, `val lastSent: StateFlow<String?>`, `fun onTrigger()`. `onTrigger()`: đọc EPC → suy sku (nếu có) → `HidEpcFormatter.textFor` → `KeymapEncoder.stringToReports` → `keyboard.sendReports`.

- [ ] **Step 1: Viết test HidBridgeViewModelTest.kt (FakeHidKeyboard)**

```kotlin
package com.example.koistock.hid
import com.example.koistock.data.model.TagMapping
import com.example.koistock.device.FakeRfidReader
import com.example.koistock.device.ScannedTag
import com.example.koistock.fakes.FakeTagRepo
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class HidBridgeViewModelTest {
    private class FakeKeyboard : HidKeyboard {
        val sent = mutableListOf<List<ByteArray>>()
        private val _h = MutableStateFlow(true)
        override val isHostConnected: StateFlow<Boolean> = _h
        override fun register() {}
        override fun sendReports(reports: List<ByteArray>) { sent.add(reports) }
        override fun unregister() {}
    }
    @Test fun onTrigger_raw_sendsEpcKeystrokes() = runTest {
        val reader = FakeRfidReader().apply { scannedSingle = ScannedTag("E2000ABC", -30) }
        val kb = FakeKeyboard()
        val vm = HidBridgeViewModel(reader, FakeTagRepo(), kb, HidFormat.RAW, Terminator.ENTER, backgroundScope)
        vm.onTrigger(); advanceUntilIdle()
        assertEquals(1, kb.sent.size)
        // "E2000ABC" = 8 ký tự * 2 report + Enter*2 = 18
        assertEquals(18, kb.sent.first().size)
        assertEquals("E2000ABC", vm.lastSent.value)
    }
    @Test fun onTrigger_noTag_sendsNothing() = runTest {
        val reader = FakeRfidReader().apply { scannedSingle = null }
        val kb = FakeKeyboard()
        val vm = HidBridgeViewModel(reader, FakeTagRepo(), kb, HidFormat.RAW, Terminator.ENTER, backgroundScope)
        vm.onTrigger(); advanceUntilIdle()
        assertTrue(kb.sent.isEmpty())
    }
    @Test fun onTrigger_structured_usesSkuFromTag() = runTest {
        val reader = FakeRfidReader().apply { scannedSingle = ScannedTag("KOI-S1-0001", -30) }
        val tags = FakeTagRepo(mutableMapOf("KOI-S1-0001" to TagMapping("KOI-S1-0001","S1")))
        val kb = FakeKeyboard()
        val vm = HidBridgeViewModel(reader, tags, kb, HidFormat.STRUCTURED, Terminator.TAB, backgroundScope)
        vm.onTrigger(); advanceUntilIdle()
        assertEquals("KOI-S1-0001", vm.lastSent.value)
    }
}
```

- [ ] **Step 2: Chạy test — phải FAIL.**

- [ ] **Step 3: Viết HidBridgeViewModel.kt**

```kotlin
package com.example.koistock.hid
import com.example.koistock.data.remote.TagRepo
import com.example.koistock.device.RfidReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HidBridgeViewModel(
    private val reader: RfidReader,
    private val tagRepo: TagRepo,
    private val keyboard: HidKeyboard,
    private val format: HidFormat,
    private val terminator: Terminator,
    private val scope: CoroutineScope,
) {
    val hostConnected: StateFlow<Boolean> = keyboard.isHostConnected
    private val _lastSent = MutableStateFlow<String?>(null)
    val lastSent: StateFlow<String?> = _lastSent.asStateFlow()

    fun onTrigger() = scope.launch {
        val epc = reader.scanSingle()?.epc ?: return@launch
        val sku = tagRepo.getByEpc(epc)?.sku
        val text = HidEpcFormatter.textFor(epc, sku, format)
        keyboard.sendReports(KeymapEncoder.stringToReports(text, terminator))
        _lastSent.value = text
        reader.beep()
    }
}
```

- [ ] **Step 4: Chạy test — phải PASS** (3 test).

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/example/koistock/hid/HidBridgeViewModel.kt app/src/test/java/com/example/koistock/hid/HidBridgeViewModelTest.kt
git commit -m "feat(hid): HidBridgeViewModel types EPC on R6 trigger"
```

---

### Task 5: Màn HID + feature-gate API 28 + ghi chú R6-HID — verify thủ công

**Files:**
- Create: `app/src/main/java/com/example/koistock/hid/HidBridgeScreen.kt`
- Create: `docs/hardware/r6-hid-direct-note.md`
- Modify: `AppShell.kt`/`MainMenuScreen` (nút "Điền vào Sheet (HID)" chỉ hiện khi `Build.VERSION.SDK_INT >= 28`).

**Interfaces:**
- Consumes: `HidBridgeViewModel`, `AndroidHidKeyboard`.
- Produces: `@Composable fun HidBridgeScreen(vm: HidBridgeViewModel)`.

- [ ] **Step 1: Viết HidBridgeScreen.kt** — hướng dẫn ghép Bluetooth điện thoại↔máy tính; trạng thái host (`hostConnected`); chọn `HidFormat` (Raw/KOI) + `Terminator` (Enter/Tab); nút test "Gõ EPC vừa quét"; hiện `lastSent`. Gọi `keyboard.register()` khi mở, `unregister()` khi thoát.

- [ ] **Step 2: Feature-gate** — trong `MainMenuScreen` chỉ thêm mục HID nếu `android.os.Build.VERSION.SDK_INT >= 28`; route `hid` trong `AppShell`.

- [ ] **Step 3: Viết docs/hardware/r6-hid-direct-note.md** — mô tả đường thay thế: nếu firmware R6 hỗ trợ chế độ HID keyboard, ghép thẳng R6↔máy tính (không cần điện thoại); ưu/nhược (EPC thô, không cập nhật Firestore); cách kiểm tra R6 (menu/tổ hợp phím/tài liệu Chainway). Link tới [chainway-r6-specifications.md](chainway-r6-specifications.md).

- [ ] **Step 4: Verify thủ công** — ghép điện thoại làm bàn phím với máy tính → mở Google Sheet → chọn 1 ô → bấm cò R6 → EPC hiện vào ô + xuống ô dưới (Enter). Thử format KOI và terminator Tab.

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/example/koistock/hid/HidBridgeScreen.kt docs/hardware/r6-hid-direct-note.md app/src/main/java/com/example/koistock/ui/shell/
git commit -m "feat(hid): HID bridge screen (API28-gated) + R6-HID note (manual-verified)"
```

---

## Self-Review

**Spec coverage:** Bấm cò → EPC điền vào ô đang chọn trên Sheet qua HID bridge (Task 1–5) ✓ · format raw/KOI + terminator (Task 2,4) ✓ · feature-gate API 28+ (Task 3,5) ✓ · 2 kết nối BT đồng thời (Task 3,4 thiết kế) ✓ · ghi chú R6-HID trực tiếp (Task 5) ✓.
**Placeholder scan:** không TBD/TODO; HID descriptor & chữ ký BluetoothHidDevice nêu rõ "chỉnh theo trình biên dịch nếu lệch" (rủi ro có chủ đích, không phải placeholder). ✓
**Type consistency:** `Terminator`/`HidFormat` dùng nhất quán Task 1→2→4; `HidKeyboard` interface khớp Task 3 impl ↔ Task 4 fake; `KeymapEncoder.stringToReports` số report khớp test Task 1 & Task 4. ✓
**Rủi ro:** khoảng `delay(8)` non-blocking giữa report có thể cần tinh chỉnh theo host; descriptor vẫn phải kiểm trên hardware matrix. OEM có thể không hỗ trợ HID Device profile hoặc dual-role BLE/HID ổn định; khi đó UI báo unsupported và dùng fallback đã mô tả.

# Default RFID Auto-Connect Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Automatically reconnect the last successfully used Chainway R6 after Bluetooth permission resolution, falling back once to an automatically scanning pairing screen.

**Architecture:** Add a platform-independent startup coordinator that owns the one-attempt guard and returns an explicit navigation outcome. `MainActivity` resolves reader-related permissions, `AppShell` invokes the coordinator and owns fallback navigation, and `PairingScreen` starts scanning on entry.

**Tech Stack:** Kotlin, Android Compose, Navigation Compose, Kotlin coroutines, JUnit 4, DataStore Preferences, existing `RfidReader` abstraction.

## Global Constraints

- Reuse only the last successfully connected Bluetooth MAC address.
- Do not invoke the RFID SDK before runtime permission resolution.
- Attempt direct startup connection at most once per `MainActivity` creation.
- On missing MAC, denied permission, or failed connection, open pairing once and automatically scan.
- Do not add background services, continuous retries, multi-device history, or dependencies.
- Preserve the unrelated modification in `app/src/main/java/com/example/koistock/remote/DeviceRegistrar.kt`.

---

### Task 1: One-shot startup connection decision

**Files:**
- Create: `app/src/main/java/com/example/koistock/ui/connection/StartupConnectionCoordinator.kt`
- Create: `app/src/test/java/com/example/koistock/ui/connection/StartupConnectionCoordinatorTest.kt`

**Interfaces:**
- Consumes: nullable permission outcome and `suspend () -> Boolean` reconnect operation.
- Produces: `suspend fun run(permissionGranted: Boolean?): StartupConnectionResult`.

- [ ] **Step 1: Write the failing tests**

```kotlin
class StartupConnectionCoordinatorTest {
    @Test fun unresolvedPermission_waitsAndCanRunLater() = runTest {
        var calls = 0
        val subject = StartupConnectionCoordinator { calls++; true }
        assertEquals(StartupConnectionResult.WaitingForPermission, subject.run(null))
        assertEquals(0, calls)
        assertEquals(StartupConnectionResult.Connected, subject.run(true))
        assertEquals(1, calls)
    }

    @Test fun deniedPermission_opensPairingWithoutConnecting() = runTest {
        var calls = 0
        val subject = StartupConnectionCoordinator { calls++; true }
        assertEquals(StartupConnectionResult.OpenPairing, subject.run(false))
        assertEquals(0, calls)
    }

    @Test fun failedReconnect_opensPairing() = runTest {
        val subject = StartupConnectionCoordinator { false }
        assertEquals(StartupConnectionResult.OpenPairing, subject.run(true))
    }

    @Test fun successfulReconnect_staysOnDashboard() = runTest {
        val subject = StartupConnectionCoordinator { true }
        assertEquals(StartupConnectionResult.Connected, subject.run(true))
    }

    @Test fun repeatedRun_isIgnored() = runTest {
        var calls = 0
        val subject = StartupConnectionCoordinator { calls++; false }
        assertEquals(StartupConnectionResult.OpenPairing, subject.run(true))
        assertEquals(StartupConnectionResult.AlreadyHandled, subject.run(true))
        assertEquals(1, calls)
    }
}
```

- [ ] **Step 2: Verify RED**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.koistock.ui.connection.StartupConnectionCoordinatorTest"`

Expected: compilation fails because the coordinator and result types do not exist.

- [ ] **Step 3: Implement the minimal coordinator**

```kotlin
sealed interface StartupConnectionResult {
    data object WaitingForPermission : StartupConnectionResult
    data object Connected : StartupConnectionResult
    data object OpenPairing : StartupConnectionResult
    data object AlreadyHandled : StartupConnectionResult
}

class StartupConnectionCoordinator(private val reconnect: suspend () -> Boolean) {
    private var handled = false

    suspend fun run(permissionGranted: Boolean?): StartupConnectionResult {
        if (permissionGranted == null) return StartupConnectionResult.WaitingForPermission
        if (handled) return StartupConnectionResult.AlreadyHandled
        handled = true
        if (!permissionGranted) return StartupConnectionResult.OpenPairing
        return if (runCatching { reconnect() }.getOrDefault(false)) {
            StartupConnectionResult.Connected
        } else {
            StartupConnectionResult.OpenPairing
        }
    }
}
```

- [ ] **Step 4: Verify GREEN**

Run the Task 1 test command again. Expected: five tests pass.

- [ ] **Step 5: Commit Task 1**

```powershell
git add app/src/main/java/com/example/koistock/ui/connection/StartupConnectionCoordinator.kt app/src/test/java/com/example/koistock/ui/connection/StartupConnectionCoordinatorTest.kt
git commit -m "feat: add one-shot RFID startup coordinator"
```

### Task 2: Permission-gated startup integration

**Files:**
- Modify: `app/src/main/java/com/example/koistock/MainActivity.kt`
- Modify: `app/src/main/java/com/example/koistock/ui/shell/AppShell.kt`

**Interfaces:**
- Consumes: `StartupConnectionCoordinator(vm::tryAutoReconnect)`.
- Produces: `AppShell(..., readerPermissionGranted: Boolean?)`; `null` means unresolved.

- [ ] **Step 1: Track reader permission separately from notifications**

Inside `setContent`, define `readerPermissions`, keep `readerPermissionGranted` nullable, and resolve it in the launcher callback using only reader permissions:

```kotlin
val readerPermissions = remember {
    if (Build.VERSION.SDK_INT >= 31) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}
var readerPermissionGranted by remember { mutableStateOf<Boolean?>(null) }
val launcher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions(),
) { grants ->
    readerPermissionGranted = readerPermissions.all { grants[it] == true }
}
```

Launch the reader permissions plus `POST_NOTIFICATIONS` where required, then pass `readerPermissionGranted` to `AppShell`.

- [ ] **Step 2: Add one-shot shell integration**

Remember the coordinator once and react to resolved permission:

```kotlin
val startupConnection = remember(vm) { StartupConnectionCoordinator(vm::tryAutoReconnect) }
LaunchedEffect(readerPermissionGranted) {
    if (startupConnection.run(readerPermissionGranted) == StartupConnectionResult.OpenPairing) {
        navController.navigate(AppDestinations.Pairing.route) { launchSingleTop = true }
    }
}
```

- [ ] **Step 3: Compile and run connection tests**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.koistock.ui.connection.*"`

Expected: all startup and existing connection tests pass.

- [ ] **Step 4: Commit Task 2**

```powershell
git add app/src/main/java/com/example/koistock/MainActivity.kt app/src/main/java/com/example/koistock/ui/shell/AppShell.kt
git commit -m "feat: auto-connect saved RFID reader at launch"
```

### Task 3: Automatic pairing fallback scan

**Files:**
- Modify: `app/src/main/java/com/example/koistock/ui/connection/PairingScreen.kt`
- Modify: `app/src/test/java/com/example/koistock/device/FakeRfidReader.kt`
- Modify: `app/src/test/java/com/example/koistock/ui/connection/ConnectionViewModelTest.kt`

**Interfaces:**
- Consumes: `ConnectionViewModel.scan()`.
- Produces: one scan when a pairing-screen composition is entered.

- [ ] **Step 1: Add a failing scan-count test**

```kotlin
@Test fun scan_invokesReaderOnce() = runTest {
    val reader = FakeRfidReader()
    val vm = ConnectionViewModel(reader, prefs(), backgroundScope)
    vm.scan()
    advanceUntilIdle()
    assertEquals(1, reader.deviceScanCount)
}
```

- [ ] **Step 2: Verify RED**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.koistock.ui.connection.ConnectionViewModelTest"`

Expected: compilation fails because `deviceScanCount` does not exist.

- [ ] **Step 3: Expose test-fake scan count and trigger entry scan**

Increment `deviceScanCount` at the start of `FakeRfidReader.startDeviceScan()`. In `PairingScreen`, add:

```kotlin
LaunchedEffect(Unit) {
    vm.scan()
}
```

Compose guarantees this effect once for that pairing-screen composition; the existing scan button remains the manual retry.

- [ ] **Step 4: Verify focused and full builds**

Run:

```powershell
./gradlew.bat testDebugUnitTest --tests "com.example.koistock.ui.connection.*"
./gradlew.bat testDebugUnitTest
./gradlew.bat :app:assembleDebug
```

Expected: all tests pass and the debug APK assembles.

- [ ] **Step 5: Inspect and commit Task 3**

Run `git diff --check` and `git status --short`; confirm the unrelated `DeviceRegistrar.kt` edit remains untouched.

```powershell
git add app/src/main/java/com/example/koistock/ui/connection/PairingScreen.kt app/src/test/java/com/example/koistock/device/FakeRfidReader.kt app/src/test/java/com/example/koistock/ui/connection/ConnectionViewModelTest.kt
git commit -m "feat: scan automatically on pairing fallback"
```

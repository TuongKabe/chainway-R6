# Web Assign Auto-Complete and SKU–EPC Uniqueness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Automatically validate and complete an active web assign session as soon as Android scans an EPC, while blocking one-SKU/one-active-EPC conflicts and linking the operator to the conflicting SKU.

**Architecture:** `AssignTagViewModel` will centralize a fail-closed preflight using `TagRepo.getByEpc` and `TagRepo.listBySku`, publish structured conflict state, suppress duplicate scans while working, and automatically call `submitScan` then `confirm`. Compose removes the manual web-submit button and renders the conflict action; `AppShell` carries the requested SKU to warehouse management, where `ProductManagementViewModel` opens it when synchronized products are available.

**Tech Stack:** Kotlin, Android Jetpack Compose Material 3, Navigation Compose, Kotlin Coroutines `StateFlow`, JUnit 4, kotlinx-coroutines-test, Retrofit.

## Global Constraints

- Only mappings with `status == "active"` block uniqueness.
- An EPC already active for the same session SKU is allowed and may complete the web session again.
- A conflicting EPC or a different active EPC for the session SKU stops before `submitScan` and `confirm`.
- The app never automatically replaces, voids, or rewrites an existing EPC.
- A repository verification failure fails closed and sends nothing to the web.
- No active web session means scanning remains local and does not auto-submit.
- Preserve the existing manual/local tag assignment and Google Sheet workflows.
- Preserve unrelated R6 configuration changes already present in the working tree.

---

## File Structure

- `app/src/main/java/com/example/koistock/data/remote/HttpRepositories.kt`: distinguishes a missing EPC from a tag-verification network/server failure.
- `app/src/main/java/com/example/koistock/ui/assign/AssignTagViewModel.kt`: owns preflight rules, automatic completion, conflict state, and duplicate-operation suppression.
- `app/src/test/java/com/example/koistock/ui/assign/AssignTagViewModelTest.kt`: verifies all allowed, blocked, failure, and duplicate paths.
- `app/src/main/java/com/example/koistock/ui/assign/AssignTagScreen.kt`: removes manual confirmation and displays the conflict-management action.
- `app/src/main/java/com/example/koistock/ui/warehouse/ProductManagementViewModel.kt`: accepts a request to open a SKU after product data becomes available.
- `app/src/test/java/com/example/koistock/ui/warehouse/ProductManagementViewModelTest.kt`: verifies deferred SKU opening.
- `app/src/main/java/com/example/koistock/ui/shell/AppShell.kt`: routes the conflict SKU from Assign to Warehouse.

### Task 1: Validate and auto-complete scanned web sessions

**Files:**
- Modify: `app/src/main/java/com/example/koistock/data/remote/HttpRepositories.kt`
- Modify: `app/src/main/java/com/example/koistock/ui/assign/AssignTagViewModel.kt`
- Modify: `app/src/test/java/com/example/koistock/ui/assign/AssignTagViewModelTest.kt`

**Interfaces:**
- Consumes: `TagRepo.getByEpc(epc)`, `TagRepo.listBySku(sku)`, `ProductRepo.getBySku(sku)`, `AssignSessionRepo.submitScan`, and `AssignSessionRepo.confirm`.
- Produces: `AssignConflictReason`, `AssignConflict`, `val conflict: StateFlow<AssignConflict?>`, and automatic processing from `scanBlank`/continuous scan.

- [ ] **Step 1: Add recording test doubles and failing automatic-flow tests**

In `AssignTagViewModelTest.kt`, add a configurable session repository and a fixed waiting session:

```kotlin
private class RecordingSessionRepo : AssignSessionRepo {
    val waiting = AssignSessionSnapshot(
        id = "session-1",
        itemCode = "SKU1",
        status = "waiting_scan",
        expiresAt = "2099-01-01T00:00:00Z",
        item = AssignSessionItem("SKU1", "Ca KOI"),
    )
    var submitCalls = 0
    var confirmCalls = 0
    var submitGate: CompletableDeferred<Unit>? = null
    override suspend fun getLatestWaiting() = waiting
    override suspend fun submitScan(sessionId: String, epc: String, serialNo: String?): AssignSessionActionResult {
        submitCalls++
        submitGate?.await()
        return AssignSessionActionResult.Success(waiting.copy(status = "scanned", scannedEpc = epc))
    }
    override suspend fun confirm(sessionId: String) =
        AssignSessionActionResult.Success(waiting.copy(status = "confirmed")).also { confirmCalls++ }
}
```

Add focused tests:

```kotlin
@Test
fun scanWithWaitingSession_autoSubmitsAndConfirms() = runTest {
    val reader = FakeRfidReader().apply { scannedSingle = ScannedTag("EPC-1", -30) }
    val sessions = RecordingSessionRepo()
    val vm = viewModel(reader, FakeTagRepo(), backgroundScope, sessions)
    vm.refreshLatestAssignSession()
    runCurrent()

    vm.scanBlank()
    advanceUntilIdle()

    assertEquals(1, sessions.submitCalls)
    assertEquals(1, sessions.confirmCalls)
    assertEquals("confirmed", vm.assignSession.value?.status)
}

@Test
fun epcOwnedByAnotherSku_blocksWebCallsAndPublishesConflict() = runTest {
    val reader = FakeRfidReader().apply { scannedSingle = ScannedTag("EPC-X", -30) }
    val tags = FakeTagRepo(mutableMapOf("EPC-X" to TagMapping("EPC-X", "SKU2", status = "active")))
    val sessions = RecordingSessionRepo()
    val vm = viewModel(reader, tags, backgroundScope, sessions, products = sampleProductsWithSku2())
    vm.refreshLatestAssignSession(); runCurrent()

    vm.scanBlank(); advanceUntilIdle()

    assertEquals(0, sessions.submitCalls)
    assertEquals("SKU2", vm.conflict.value?.ownerSku)
    assertEquals("EPC-X", vm.conflict.value?.epc)
}

@Test
fun skuWithAnotherActiveEpc_blocksWebCalls() = runTest {
    val reader = FakeRfidReader().apply { scannedSingle = ScannedTag("EPC-NEW", -30) }
    val tags = FakeTagRepo(mutableMapOf("EPC-OLD" to TagMapping("EPC-OLD", "SKU1", status = "active")))
    val sessions = RecordingSessionRepo()
    val vm = viewModel(reader, tags, backgroundScope, sessions)
    vm.refreshLatestAssignSession(); runCurrent()

    vm.scanBlank(); advanceUntilIdle()

    assertEquals(0, sessions.submitCalls)
    assertEquals("EPC-OLD", vm.conflict.value?.epc)
}

@Test
fun epcAlreadyOwnedBySessionSku_isAllowed() = runTest {
    val reader = FakeRfidReader().apply { scannedSingle = ScannedTag("EPC-1", -30) }
    val tags = FakeTagRepo(mutableMapOf("EPC-1" to TagMapping("EPC-1", "SKU1", status = "active")))
    val sessions = RecordingSessionRepo()
    val vm = viewModel(reader, tags, backgroundScope, sessions)
    vm.refreshLatestAssignSession(); runCurrent()

    vm.scanBlank(); advanceUntilIdle()

    assertEquals(1, sessions.submitCalls)
    assertEquals(1, sessions.confirmCalls)
}
```

Add the remaining boundary tests:

```kotlin
@Test
fun inactiveMappings_doNotBlockWebCompletion() = runTest {
    val reader = FakeRfidReader().apply { scannedSingle = ScannedTag("EPC-NEW", -30) }
    val tags = FakeTagRepo(mutableMapOf("EPC-OLD" to TagMapping("EPC-OLD", "SKU1", status = "void")))
    val sessions = RecordingSessionRepo()
    val vm = viewModel(reader, tags, backgroundScope, sessions)
    vm.refreshLatestAssignSession(); runCurrent()

    vm.scanBlank(); advanceUntilIdle()

    assertEquals(1, sessions.submitCalls)
}

@Test
fun verificationFailure_failsClosed() = runTest {
    val reader = FakeRfidReader().apply { scannedSingle = ScannedTag("EPC-1", -30) }
    val sessions = RecordingSessionRepo()
    val vm = viewModel(reader, ThrowingTagRepo(), backgroundScope, sessions)
    vm.refreshLatestAssignSession(); runCurrent()

    vm.scanBlank(); advanceUntilIdle()

    assertEquals(0, sessions.submitCalls)
    assertTrue(vm.result.value is AssignResult.Error)
}

@Test
fun scanWithoutWaitingSession_doesNotCallWeb() = runTest {
    val reader = FakeRfidReader().apply { scannedSingle = ScannedTag("EPC-1", -30) }
    val sessions = RecordingSessionRepo()
    val vm = viewModel(reader, FakeTagRepo(), backgroundScope, sessions)

    vm.scanBlank(); advanceUntilIdle()

    assertEquals(0, sessions.submitCalls)
}

@Test
fun repeatedScanWhileSubmitting_callsWebOnce() = runTest {
    val reader = FakeRfidReader().apply { scannedSingle = ScannedTag("EPC-1", -30) }
    val sessions = RecordingSessionRepo().apply { submitGate = CompletableDeferred() }
    val vm = viewModel(reader, FakeTagRepo(), backgroundScope, sessions)
    vm.refreshLatestAssignSession(); runCurrent()

    vm.scanBlank(); runCurrent()
    vm.scanBlank(); runCurrent()
    sessions.submitGate?.complete(Unit)
    advanceUntilIdle()

    assertEquals(1, sessions.submitCalls)
    assertEquals(1, sessions.confirmCalls)
}
```

Make `RecordingSessionRepo.submitScan` wait on `submitGate` when present, add this throwing fake, and extend the test factory:

```kotlin
private class ThrowingTagRepo : TagRepo {
    override suspend fun getByEpc(epc: String): TagMapping? = error("verification offline")
    override suspend fun listBySku(sku: String): List<TagMapping> = error("verification offline")
    override suspend fun upsert(tag: TagMapping) = Unit
    override suspend fun voidTag(epc: String) = Unit
}

private fun sampleProductsWithSku2() = FakeProductRepo(
    mutableMapOf(
        "SKU1" to Product("SKU1", "Ca KOI", "con", TrackingMode.SERIALIZED, 1, "A-03"),
        "SKU2" to Product("SKU2", "Ca Showa", "con", TrackingMode.SERIALIZED, 1, "A-03"),
    ),
)

private fun viewModel(
    reader: FakeRfidReader,
    tags: TagRepo,
    scope: CoroutineScope,
    sessions: AssignSessionRepo = FakeAssignSessionRepo(),
    products: ProductRepo = sampleProducts(),
) = AssignTagViewModel(
    reader = reader,
    tagRepo = tags,
    productRepo = products,
    gsheetWriteRepo = FakeGsheetWriteRepo(),
    assignSessionRepo = sessions,
    deviceId = "dev-1",
    now = { 100 },
    scope = scope,
)
```

- [ ] **Step 2: Run tests and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "com.example.koistock.ui.assign.AssignTagViewModelTest"
```

Expected: compilation fails because `AssignConflict` and `conflict` do not exist and valid scans do not auto-submit.

- [ ] **Step 3: Implement structured fail-closed validation and automatic completion**

Add to `AssignTagViewModel.kt`:

```kotlin
enum class AssignConflictReason { EPC_OWNED_BY_OTHER_SKU, SKU_ALREADY_HAS_OTHER_EPC }

data class AssignConflict(
    val reason: AssignConflictReason,
    val ownerSku: String,
    val ownerName: String?,
    val epc: String,
)
```

Expose `MutableStateFlow<AssignConflict?>`, then route both single and continuous scan results through one suspend function:

```kotlin
private suspend fun acceptScannedEpc(epc: String) {
    mutableScannedEpc.value = epc
    mutableDone.value = false
    val session = mutableAssignSession.value ?: return
    if (mutableWorking.value) return
    mutableWorking.value = true
    mutableConflict.value = null
    try {
        val epcMapping = tagRepo.getByEpc(epc)?.takeIf { it.status == "active" }
        val skuMappings = tagRepo.listBySku(session.itemCode).filter { it.status == "active" }
        val conflictMapping = when {
            epcMapping != null && epcMapping.sku != session.itemCode -> epcMapping
            else -> skuMappings.firstOrNull { it.epc != epc }
        }
        if (conflictMapping != null) {
            val product = runCatching { productRepo.getBySku(conflictMapping.sku) }.getOrNull()
            mutableConflict.value = AssignConflict(
                reason = if (epcMapping?.sku != null && epcMapping.sku != session.itemCode) {
                    AssignConflictReason.EPC_OWNED_BY_OTHER_SKU
                } else {
                    AssignConflictReason.SKU_ALREADY_HAS_OTHER_EPC
                },
                ownerSku = conflictMapping.sku,
                ownerName = product?.name,
                epc = conflictMapping.epc,
            )
            return
        }
        completeWebSession(session, epc)
    } catch (failure: Exception) {
        mutableResult.value = AssignResult.Error("Không thể kiểm tra liên kết EPC/SKU: ${failure.message ?: "lỗi kết nối"}")
    } finally {
        mutableWorking.value = false
    }
}
```

Extract the existing `submitScan` then `confirm` block from `pushCurrentEpcToAssignSession` into `completeWebSession(session, epc)`. Call `acceptScannedEpc` immediately after a successful read in `scanBlank` and `startHold`. Remove `pushCurrentEpcToAssignSession` after Compose no longer calls it.

In `HttpTagRepository`, return `null` only for an HTTP 404 and rethrow other HTTP/network failures; remove `getOrDefault(emptyList())` from `listBySku` so validation can fail closed:

```kotlin
override suspend fun getByEpc(epc: String): TagMapping? = try {
    api.getTag(epc).data.toTagMapping()
} catch (error: HttpException) {
    if (error.code() == 404) null else throw error
}

override suspend fun listBySku(sku: String): List<TagMapping> =
    api.getTagsByItem(sku).data.map { it.toTagMapping() }
```

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the Step 2 command again.

Expected: all `AssignTagViewModelTest` tests pass, including existing manual assignment tests.

- [ ] **Step 5: Commit Task 1**

```powershell
git add app/src/main/java/com/example/koistock/data/remote/HttpRepositories.kt app/src/main/java/com/example/koistock/ui/assign/AssignTagViewModel.kt app/src/test/java/com/example/koistock/ui/assign/AssignTagViewModelTest.kt
git commit -m "feat: auto-complete validated web tag sessions"
```

### Task 2: Remove manual confirmation and show the conflict action

**Files:**
- Modify: `app/src/main/java/com/example/koistock/ui/assign/AssignTagScreen.kt`
- Create: `app/src/test/java/com/example/koistock/ui/assign/AssignConflictPresentationTest.kt`

**Interfaces:**
- Consumes: `AssignTagViewModel.conflict`.
- Produces: `AssignTagScreen(vm, products, prefillEpc, onManageSku)` and a conflict dialog with one primary management action.

- [ ] **Step 1: Write and run the failing conflict-copy test**

Create:

```kotlin
package com.example.koistock.ui.assign

import org.junit.Assert.assertTrue
import org.junit.Test

class AssignConflictPresentationTest {
    @Test
    fun messageIncludesOwnerSkuAndEpc() {
        val conflict = AssignConflict(
            AssignConflictReason.EPC_OWNED_BY_OTHER_SKU,
            ownerSku = "SKU2",
            ownerName = "Ca Showa",
            epc = "EPC-X",
        )

        val message = conflict.displayMessage()

        assertTrue(message.contains("SKU2"))
        assertTrue(message.contains("EPC-X"))
    }
}
```

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "com.example.koistock.ui.assign.AssignConflictPresentationTest"
```

Expected: compilation fails because `displayMessage` does not exist.

- [ ] **Step 2: Update the screen contract and content**

Change the signature:

```kotlin
fun AssignTagScreen(
    vm: AssignTagViewModel,
    products: List<Product>,
    prefillEpc: String? = null,
    onManageSku: (String) -> Unit,
)
```

Collect `conflict`, delete the button that calls `pushCurrentEpcToAssignSession`, and render:

```kotlin
internal fun AssignConflict.displayMessage(): String =
    "${ownerName ?: ownerSku} ($ownerSku) đang giữ EPC $epc. Dữ liệu hiện tại được giữ nguyên."

conflict?.let { current ->
    AlertDialog(
        onDismissRequest = vm::clearConflict,
        title = { Text("Không thể gán tag") },
        text = {
            Text(current.displayMessage())
        },
        confirmButton = {
            Button(onClick = { onManageSku(current.ownerSku); vm.clearConflict() }) {
                Text("Quản lý SKU đang giữ EPC")
            }
        },
        dismissButton = { TextButton(onClick = vm::clearConflict) { Text("Đóng") } },
    )
}
```

Update the explanatory copy to state that a scan is checked and sent automatically when a web session is active.

- [ ] **Step 3: Run presentation test and compile the Compose change**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:compileDebugKotlin testDebugUnitTest --tests "com.example.koistock.ui.assign.AssignConflictPresentationTest"
```

Expected: compilation fails only at the existing `AppShell` caller because `onManageSku` is not supplied yet; the screen itself compiles.

### Task 3: Open the conflicting SKU in warehouse management

**Files:**
- Modify: `app/src/main/java/com/example/koistock/ui/warehouse/ProductManagementViewModel.kt`
- Modify: `app/src/test/java/com/example/koistock/ui/warehouse/ProductManagementViewModelTest.kt`
- Modify: `app/src/main/java/com/example/koistock/ui/shell/AppShell.kt`

**Interfaces:**
- Consumes: conflict SKU callback from `AssignTagScreen` and the existing product repository flow.
- Produces: `ProductManagementViewModel.selectProductWhenAvailable(sku: String)` and AppShell navigation to the warehouse product editor.

- [ ] **Step 1: Write the failing deferred-selection test**

Add to `ProductManagementViewModelTest.kt`:

```kotlin
@Test
fun selectProductWhenAvailable_opensEditorAfterRefresh() = runTest {
    val repo = ProductSource(emptyList())
    val vm = ProductManagementViewModel(repo, LocationSource(locations), { WarehouseSyncResult.Success }, backgroundScope)

    vm.selectProductWhenAvailable("SKU-1")
    runCurrent()
    assertNull(vm.editor.value)

    repo.flow.value = products
    runCurrent()

    assertEquals("SKU-1", vm.editor.value?.sku)
}
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "com.example.koistock.ui.warehouse.ProductManagementViewModelTest"
```

Expected: compilation fails because `selectProductWhenAvailable` does not exist.

- [ ] **Step 3: Implement deferred selection**

Add one requested SKU state and a collector in `ProductManagementViewModel`:

```kotlin
private val requestedSku = MutableStateFlow<String?>(null)

init {
    scope.launch {
        combine(products, requestedSku) { items, sku -> items.firstOrNull { it.sku == sku } }
            .filterNotNull()
            .collect { product ->
                originalProduct = product
                resetEditor()
                requestedSku.value = null
            }
    }
}

fun selectProductWhenAvailable(sku: String) {
    requestedSku.value = sku
}
```

Keep the existing immediate `selectProduct` behavior unchanged.

- [ ] **Step 4: Wire navigation in AppShell**

Add `var requestedWarehouseSku by rememberSaveable { mutableStateOf<String?>(null) }`. In the Assign route:

```kotlin
AssignTagScreen(
    vm = assignVm,
    products = products,
    onManageSku = { sku ->
        requestedWarehouseSku = sku
        navController.navigate(AppDestinations.Warehouse.route)
    },
)
```

In the Warehouse route, request selection once:

```kotlin
LaunchedEffect(requestedWarehouseSku, productManagementVm) {
    requestedWarehouseSku?.let(productManagementVm::selectProductWhenAvailable)
    requestedWarehouseSku = null
}
```

- [ ] **Step 5: Run focused tests and compile**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:compileDebugKotlin testDebugUnitTest --tests "com.example.koistock.ui.assign.AssignTagViewModelTest" --tests "com.example.koistock.ui.warehouse.ProductManagementViewModelTest"
```

Expected: Kotlin compilation succeeds and both test classes pass.

- [ ] **Step 6: Run the complete unit-test suite**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL` with no failed tests.

- [ ] **Step 7: Commit Tasks 2 and 3**

Stage only the Assign/Warehouse hunks from `AppShell.kt`, preserving unrelated R6 working-tree changes:

```powershell
git add app/src/main/java/com/example/koistock/ui/assign/AssignTagScreen.kt app/src/main/java/com/example/koistock/ui/warehouse/ProductManagementViewModel.kt app/src/test/java/com/example/koistock/ui/warehouse/ProductManagementViewModelTest.kt
git add -p app/src/main/java/com/example/koistock/ui/shell/AppShell.kt
git commit -m "feat: surface web tag conflicts in warehouse"
```

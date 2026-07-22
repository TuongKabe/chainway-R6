# Location Stock Reconciliation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make “Quét theo khu” reconcile scanned EPC presence against DB stock for the entire warehouse, a parent zone, or one shelf without comparing one representative EPC to the SKU quantity.

**Architecture:** Add a count-specific inventory gateway that reads item metadata, bins, and active EPC tags directly from the backend and a pure calculator that resolves location scope through `LocationNode.parent`. Feed its scoped DB quantities and global-stock SKU set into a presence-based reconciler; the ViewModel owns loading/error state and the Compose screen only renders it.

**Tech Stack:** Kotlin, coroutines/StateFlow, Retrofit, Jetpack Compose, JUnit 4, kotlinx-coroutines-test.

## Global Constraints

- An empty location means the entire warehouse (“Toàn khu”).
- A zone includes itself and all recursive descendants by `LocationNode.parent`; a shelf matches exactly.
- BULK DB stock is the scoped sum of `BinDto.actualQty`; SERIALIZED DB stock is the scoped count of active EPC rows.
- One scanned EPC is a presence marker for its SKU: positive scoped DB stock plus at least one scanned tag is `MATCH`, regardless of quantity difference.
- Keep scanned tag count and DB stock as separate values and labels.
- `MISPLACED` applies only in a specific scope when the SKU has stock elsewhere; entire-warehouse results never use it.
- A refresh failure keeps the previous reconciliation results and displays the error.
- Google Sheet removal is outside this plan.

---

## File Structure

- Create `app/src/main/java/com/example/koistock/domain/CountInventory.kt`: scope, stock snapshot, gateway contract, and pure scope/stock calculation.
- Create `app/src/main/java/com/example/koistock/data/remote/HttpCountInventoryRepository.kt`: strict backend loading and DTO mapping for count reconciliation.
- Modify `app/src/main/java/com/example/koistock/domain/CountReconciler.kt`: presence-based statuses and separate DB-stock/tag-count fields.
- Modify `app/src/main/java/com/example/koistock/domain/CsvExporter.kt`: unambiguous exported column names.
- Modify `app/src/main/java/com/example/koistock/ui/count/CountViewModel.kt`: load snapshots, preserve previous results on failure, expose progress/message/scope.
- Modify `app/src/main/java/com/example/koistock/ui/count/CountScreen.kt`: default whole-warehouse scope and correct result wording.
- Modify `app/src/main/java/com/example/koistock/ui/shell/AppShell.kt`: inject the count repository and remove the product-derived expected list.
- Add focused domain, repository, ViewModel, and CSV unit tests.

### Task 1: Resolve DB stock by warehouse scope

**Files:**
- Create: `app/src/main/java/com/example/koistock/domain/CountInventory.kt`
- Test: `app/src/test/java/com/example/koistock/domain/CountInventoryCalculatorTest.kt`

**Interfaces:**
- Produces: `CountScope`, `CountInventoryItem`, `CountBinStock`, `CountTagStock`, `CountInventorySnapshot`, `CountInventoryRepository.load(locationCode, locations)`, and `CountInventoryCalculator.calculate(...)`.

- [ ] **Step 1: Write failing scope and quantity tests**

```kotlin
@Test fun blankScopeIncludesAllLocations() {
    val result = CountInventoryCalculator.calculate("", locations, items, bins, tags)
    assertEquals(CountScope.EntireWarehouse, result.scope)
    assertEquals(10, result.expected.single { it.sku == "BULK-1" }.expectedQty)
}

@Test fun zoneUsesParentRelationshipNotCodePrefix() {
    val result = CountInventoryCalculator.calculate("ZONE-A", locations, items, bins, tags)
    assertEquals(setOf("ZONE-A", "SHELF-X"), (result.scope as CountScope.Location).includedCodes)
    assertEquals(4, result.expected.single { it.sku == "BULK-1" }.expectedQty)
}

@Test fun shelfIsExactAndSerializedCountsOnlyActiveTags() {
    val result = CountInventoryCalculator.calculate("SHELF-X", locations, items, bins, tags)
    assertEquals(1, result.expected.single { it.sku == "SER-1" }.expectedQty)
}
```

- [ ] **Step 2: Run the new test and verify it fails**

Run: `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "com.example.koistock.domain.CountInventoryCalculatorTest"`

Expected: FAIL because `CountInventoryCalculator` and its models do not exist.

- [ ] **Step 3: Implement the pure inventory calculator**

```kotlin
sealed interface CountScope {
    data object EntireWarehouse : CountScope
    data class Location(val code: String, val includedCodes: Set<String>) : CountScope
}

data class CountInventorySnapshot(
    val scope: CountScope,
    val expected: List<ExpectedItem>,
    val skusWithStockAnywhere: Set<String>,
)

interface CountInventoryRepository {
    suspend fun load(locationCode: String, locations: List<LocationNode>): CountInventorySnapshot
}
```

Implement recursive descendant collection using `parent`, strict `actualQty.toBigDecimal()`, BULK bin sums, active SERIALIZED tag counts, and `skusWithStockAnywhere` from positive whole-warehouse quantities. Reject a nonblank unknown location with a descriptive exception.

- [ ] **Step 4: Run the focused test and verify it passes**

Run the command from Step 2.

Expected: PASS for whole warehouse, parent-zone, exact-shelf, active-tag, and unknown-location cases.

- [ ] **Step 5: Commit the pure stock calculation**

```powershell
git add app/src/main/java/com/example/koistock/domain/CountInventory.kt app/src/test/java/com/example/koistock/domain/CountInventoryCalculatorTest.kt
git commit -m "feat: calculate count stock by location scope"
```

### Task 2: Load count inventory directly from the backend

**Files:**
- Create: `app/src/main/java/com/example/koistock/data/remote/HttpCountInventoryRepository.kt`
- Test: `app/src/test/java/com/example/koistock/data/remote/HttpCountInventoryRepositoryTest.kt`

**Interfaces:**
- Consumes: `KoiApiService.getItems()`, `getBins()`, `getTagsByItem()`, and `CountInventoryCalculator.calculate(...)`.
- Produces: `HttpCountInventoryRepository(api) : CountInventoryRepository`.

- [ ] **Step 1: Write failing repository mapping tests**

Use a fake `KoiApiService` returning one BULK item with bin quantity `10`, one SERIALIZED item with active/inactive tags, and assert `load("", locations)` returns DB quantities `10` and `1`. Add a test where `getBins()` throws and assert `load` throws instead of returning zero.

- [ ] **Step 2: Run the repository test and verify it fails**

Run: `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "com.example.koistock.data.remote.HttpCountInventoryRepositoryTest"`

Expected: FAIL because `HttpCountInventoryRepository` does not exist.

- [ ] **Step 3: Implement strict API loading**

```kotlin
class HttpCountInventoryRepository(
    private val api: KoiApiService,
) : CountInventoryRepository {
    override suspend fun load(locationCode: String, locations: List<LocationNode>): CountInventorySnapshot {
        val items = api.getItems().data.filter(ItemDto::isActive)
        val bins = api.getBins().data
        val tags = items.flatMap { api.getTagsByItem(it.itemCode).data }
        return CountInventoryCalculator.calculate(locationCode, locations, items.toCountItems(), bins.toCountBins(), tags.toCountTags())
    }
}
```

Do not wrap API calls in `getOrDefault(emptyList())`; network or parsing failures must propagate so the ViewModel can preserve the last good rows.

- [ ] **Step 4: Run repository and calculator tests**

Run: `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "com.example.koistock.data.remote.HttpCountInventoryRepositoryTest" --tests "com.example.koistock.domain.CountInventoryCalculatorTest"`

Expected: PASS.

- [ ] **Step 5: Commit the backend gateway**

```powershell
git add app/src/main/java/com/example/koistock/data/remote/HttpCountInventoryRepository.kt app/src/test/java/com/example/koistock/data/remote/HttpCountInventoryRepositoryTest.kt
git commit -m "feat: load count inventory from database api"
```

### Task 3: Reconcile EPC presence instead of quantities

**Files:**
- Modify: `app/src/main/java/com/example/koistock/domain/CountReconciler.kt`
- Modify: `app/src/main/java/com/example/koistock/domain/CsvExporter.kt`
- Modify: `app/src/test/java/com/example/koistock/domain/CountReconcilerTest.kt`
- Create: `app/src/test/java/com/example/koistock/domain/CsvExporterTest.kt`

**Interfaces:**
- Consumes: `CountScope`, scoped `ExpectedItem` rows, `skusWithStockAnywhere`, and `countedBySku`.
- Produces: `CountReconciler.reconcile(scope, countedBySku, expected, skusWithStockAnywhere)` and `CountRow(scannedTagCount, dbStockQty, unit, ...)`.

- [ ] **Step 1: Replace quantity-equality tests with presence tests**

```kotlin
@Test fun oneTagMatchesSkuWithDbStockTen() {
    val rows = CountReconciler.reconcile(
        CountScope.EntireWarehouse,
        mapOf("S1" to 1),
        listOf(ExpectedItem("S1", "Áo", 10, "", "cái")),
        setOf("S1"),
    )
    assertEquals(CountStatus.MATCH, rows.single().status)
    assertEquals(1, rows.single().scannedTagCount)
    assertEquals(10, rows.single().dbStockQty)
}

@Test fun wholeWarehouseNeverMarksMisplaced() {
    val row = CountReconciler.reconcile(
        CountScope.EntireWarehouse, mapOf("SX" to 1), emptyList(), setOf("SX"),
    ).single()
    assertEquals(CountStatus.EXTRA, row.status)
}

@Test fun specificScopeMarksStockedElsewhereAsMisplaced() {
    val row = CountReconciler.reconcile(
        CountScope.Location("A", setOf("A")), mapOf("SX" to 1), emptyList(), setOf("SX"),
    ).single()
    assertEquals(CountStatus.MISPLACED, row.status)
}

@Test fun unscannedPositiveStockIsMissing() {
    val row = CountReconciler.reconcile(
        CountScope.EntireWarehouse,
        emptyMap(),
        listOf(ExpectedItem("S1", "Áo", 10, "", "cái")),
        setOf("S1"),
    ).single()
    assertEquals(CountStatus.MISSING, row.status)
}
```

Add a CSV assertion for headers `soTagDaQuet,tonDb,donVi,trangThai` and ensure no `chenhLech` column remains.

- [ ] **Step 2: Run reconciler and CSV tests and verify they fail**

Run: `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "com.example.koistock.domain.CountReconcilerTest" --tests "com.example.koistock.domain.CsvExporterTest"`

Expected: FAIL because the old reconciler compares `counted == expected` and CSV exports a misleading difference.

- [ ] **Step 3: Implement presence statuses and explicit fields**

```kotlin
val status = if (scannedTagCount > 0) CountStatus.MATCH else CountStatus.MISSING

val outsideStatus = when {
    scope is CountScope.Location && sku in skusWithStockAnywhere -> CountStatus.MISPLACED
    else -> CountStatus.EXTRA
}
```

Rename row fields to `scannedTagCount` and `dbStockQty`, carry `unit`, and export them separately. Do not calculate or render a numeric difference between these unlike units.

- [ ] **Step 4: Run the focused domain tests**

Run the command from Step 2.

Expected: PASS.

- [ ] **Step 5: Commit presence reconciliation**

```powershell
git add app/src/main/java/com/example/koistock/domain/CountReconciler.kt app/src/main/java/com/example/koistock/domain/CsvExporter.kt app/src/test/java/com/example/koistock/domain/CountReconcilerTest.kt app/src/test/java/com/example/koistock/domain/CsvExporterTest.kt
git commit -m "fix: reconcile count by sku presence"
```

### Task 4: Wire DB reconciliation into ViewModel and Compose

**Files:**
- Modify: `app/src/main/java/com/example/koistock/ui/count/CountViewModel.kt`
- Modify: `app/src/main/java/com/example/koistock/ui/count/CountScreen.kt`
- Modify: `app/src/main/java/com/example/koistock/ui/shell/AppShell.kt`
- Modify: `app/src/test/java/com/example/koistock/ui/count/CountViewModelTest.kt`

**Interfaces:**
- Consumes: `CountInventoryRepository.load(locationCode, locations)`.
- Produces: `CountViewModel.reconcile(locations)`, `isReconciling`, `reconcileMessage`, and `scopeLabel` StateFlows.

- [ ] **Step 1: Write failing ViewModel tests**

```kotlin
@Test fun blankLocationLoadsEntireWarehouseAndMatchesOneTagToStockTen() = runTest {
    val repo = FakeCountInventoryRepository(snapshot(expectedQty = 10))
    val vm = countViewModel(repo)
    scanOneTag(vm)
    vm.reconcile(locations)
    assertEquals("Toàn khu", vm.scopeLabel.value)
    assertEquals(CountStatus.MATCH, vm.rows.value.single().status)
    assertEquals(10, vm.rows.value.single().dbStockQty)
}

@Test fun loadFailureKeepsPreviousRowsAndShowsMessage() = runTest {
    val repo = FakeCountInventoryRepository(firstSnapshot, thenFailure = IOException("offline"))
    val vm = countViewModel(repo)
    vm.reconcile(locations)
    val previous = vm.rows.value
    vm.reconcile(locations)
    assertEquals(previous, vm.rows.value)
    assertTrue(vm.reconcileMessage.value!!.contains("offline"))
}
```

- [ ] **Step 2: Run ViewModel tests and verify they fail**

Run: `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "com.example.koistock.ui.count.CountViewModelTest"`

Expected: FAIL because the ViewModel still accepts a prefiltered product-derived list.

- [ ] **Step 3: Implement ViewModel loading and failure preservation**

Inject `CountInventoryRepository`. In `reconcile(locations)`, set loading true, call `load(zone.orEmpty().trim(), locations)`, replace rows only after success, set `scopeLabel` to `Toàn khu` or the selected code, and keep old rows in `catch` while setting a Vietnamese error message. Clear loading in `finally`.

- [ ] **Step 4: Update CountScreen wording and call path**

Remove `expectedItems`. The reconcile button calls `vm.reconcile(locations)`. Render each result as:

```kotlin
Text("${row.sku} · ${row.name}")
Text("Đã quét ${row.scannedTagCount} tag · Tồn DB: ${row.dbStockQty} ${row.unit} · ${row.status}")
```

Show `Phạm vi: Toàn khu` for blank input, a progress indicator while loading, and the nonblocking error message. Keep the SKU accordion intact.

- [ ] **Step 5: Wire the repository in AppShell**

Create `val countInventoryRepo = remember { HttpCountInventoryRepository(api) }`, pass it into `CountViewModel`, delete `expectedItems = products.map { Product.quantity/locationCode }`, and remove the `expectedItems` screen argument. Stage only these exact AppShell hunks because the file contains unrelated R6 work.

- [ ] **Step 6: Run focused tests and compile**

Run: `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:compileDebugKotlin testDebugUnitTest --tests "com.example.koistock.ui.count.CountViewModelTest" --tests "com.example.koistock.domain.CountReconcilerTest" --tests "com.example.koistock.domain.CountInventoryCalculatorTest" --tests "com.example.koistock.data.remote.HttpCountInventoryRepositoryTest"`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Run the complete unit suite**

Run: `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest`

Expected: BUILD SUCCESSFUL with no regressions.

- [ ] **Step 8: Commit the app integration**

```powershell
git add app/src/main/java/com/example/koistock/ui/count/CountViewModel.kt app/src/main/java/com/example/koistock/ui/count/CountScreen.kt app/src/test/java/com/example/koistock/ui/count/CountViewModelTest.kt
# Stage only the intended AppShell patch, then inspect git diff --cached.
git commit -m "fix: use database stock for location count"
```

## Self-Review

- Spec coverage: every approved rule maps to Tasks 1–4, including whole warehouse, recursive parent scope, exact shelf, DB source, representative EPC presence, status rules, separate labels, and failure preservation.
- Placeholder scan: no TBD/TODO/future implementation placeholders; all status examples include concrete inputs and assertions.
- Type consistency: Task 1 produces `CountScope` and `CountInventorySnapshot`; Task 2 returns that contract; Task 3 consumes it; Task 4 injects the same `CountInventoryRepository` and calls the new reconciler.

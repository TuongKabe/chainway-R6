# Count SKU Accordion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the plain scanned-SKU text block in "Quét theo khu" with a compact accordion list showing scan totals and warehouse-style product details.

**Architecture:** `CountViewModel` will publish immutable `CountedSkuRow` values containing the resolved `Product` and unique scanned-tag count while preserving the existing `countedBySku` contract for reconciliation and CSV behavior. `CountScreen` will receive synchronized locations from `AppShell`, keep the single expanded SKU as local presentation state, and render each result through a focused accordion card.

**Tech Stack:** Kotlin, Android Jetpack Compose Material 3, Kotlin Coroutines `StateFlow`, JUnit 4, kotlinx-coroutines-test.

## Global Constraints

- Only one SKU may be expanded at a time.
- Collapsed rows show product name, SKU, and scanned tag quantity.
- Expanded rows show SKU, product name, warehouse quantity/unit, tracking mode, and parent zone to shelf.
- Missing location metadata uses a concise fallback and never removes the scanned SKU.
- Product details are read-only; editing remains in "Quản lý kho".
- Existing zone selection, scanning, reconciliation, saving, CSV export, EPC deduplication, and backend contracts remain unchanged.
- Preserve unrelated R6 configuration changes already present in the working tree.

---

## File Structure

- `app/src/main/java/com/example/koistock/ui/count/CountViewModel.kt`: owns scan aggregation and publishes `CountedSkuRow` results.
- `app/src/main/java/com/example/koistock/ui/count/CountScreen.kt`: owns accordion expansion state and renders summary, collapsed rows, and expanded details.
- `app/src/main/java/com/example/koistock/ui/shell/AppShell.kt`: supplies the already synchronized `locations` list to `CountScreen`.
- `app/src/test/java/com/example/koistock/ui/count/CountViewModelTest.kt`: verifies resolved product rows, unique counts, and scan reset behavior.
- `app/src/test/java/com/example/koistock/ui/count/CountAccordionStateTest.kt`: verifies the single-expanded-row interaction as a pure state transition.

### Task 1: Publish resolved scanned-SKU rows

**Files:**
- Modify: `app/src/main/java/com/example/koistock/ui/count/CountViewModel.kt`
- Modify: `app/src/test/java/com/example/koistock/ui/count/CountViewModelTest.kt`

**Interfaces:**
- Consumes: `Product`, `TagRepo.getByEpc(epc: String)`, and `ProductRepo.getBySku(sku: String)`.
- Produces: `data class CountedSkuRow(val product: Product, val scannedTagCount: Int)` and `val scannedSkuRows: StateFlow<List<CountedSkuRow>>` sorted by SKU.

- [ ] **Step 1: Write failing tests for resolved rows and reset**

Add these assertions to focused tests in `CountViewModelTest.kt`:

```kotlin
@Test
fun scan_exposesProductDetailsAndUniqueTagCount() = runTest {
    val reader = FakeRfidReader()
    val product = Product("S1", "Áo KOI", "cái", TrackingMode.SERIALIZED, 12, "A-03")
    val vm = CountViewModel(
        reader,
        FakeTagRepo(mutableMapOf(
            "KOI-S1-1" to TagMapping("KOI-S1-1", "S1"),
            "KOI-S1-2" to TagMapping("KOI-S1-2", "S1"),
        )),
        FakeProductRepo(mutableMapOf("S1" to product)),
        FakeTransactionRepo(),
        "d",
        { 0 },
        this,
    )
    vm.startScan()
    runCurrent()

    reader.emitTag("KOI-S1-1")
    reader.emitTag("KOI-S1-2")
    reader.emitTag("KOI-S1-1")
    advanceUntilIdle()

    assertEquals(listOf(CountedSkuRow(product, 2)), vm.scannedSkuRows.value)
    vm.clear()
}

@Test
fun startingNewScan_clearsResolvedRows() = runTest {
    val reader = FakeRfidReader()
    val product = Product("S1", "Áo KOI", "cái", TrackingMode.SERIALIZED, 12, "A-03")
    val vm = CountViewModel(
        reader,
        FakeTagRepo(mutableMapOf("KOI-S1-1" to TagMapping("KOI-S1-1", "S1"))),
        FakeProductRepo(mutableMapOf("S1" to product)),
        FakeTransactionRepo(),
        "d",
        { 0 },
        this,
    )
    vm.startScan()
    runCurrent()
    reader.emitTag("KOI-S1-1")
    advanceUntilIdle()
    vm.stopScan()

    vm.startScan()

    assertTrue(vm.scannedSkuRows.value.isEmpty())
    vm.clear()
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "com.example.koistock.ui.count.CountViewModelTest"
```

Expected: compilation fails because `CountedSkuRow` and `scannedSkuRows` do not exist.

- [ ] **Step 3: Add the minimal resolved-row state**

In `CountViewModel.kt`, add:

```kotlin
import com.example.koistock.data.model.Product

data class CountedSkuRow(
    val product: Product,
    val scannedTagCount: Int,
)

private val mutableScannedSkuRows = MutableStateFlow<List<CountedSkuRow>>(emptyList())
val scannedSkuRows: StateFlow<List<CountedSkuRow>> = mutableScannedSkuRows.asStateFlow()
```

Clear `mutableScannedSkuRows` in `startScan()`. After the existing `countedBySku` update resolves a product, update the matching row without duplicating products:

```kotlin
val count = mutableCountedBySku.value.getValue(product.sku)
mutableScannedSkuRows.update { rows ->
    (rows.filterNot { it.product.sku == product.sku } + CountedSkuRow(product, count))
        .sortedBy { it.product.sku }
}
```

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the Step 2 command again.

Expected: all `CountViewModelTest` tests pass.

- [ ] **Step 5: Commit Task 1**

```powershell
git add app/src/main/java/com/example/koistock/ui/count/CountViewModel.kt app/src/test/java/com/example/koistock/ui/count/CountViewModelTest.kt
git commit -m "feat: expose scanned SKU product rows"
```

### Task 2: Render the single-open SKU accordion

**Files:**
- Create: `app/src/test/java/com/example/koistock/ui/count/CountAccordionStateTest.kt`
- Modify: `app/src/main/java/com/example/koistock/ui/count/CountScreen.kt`
- Modify: `app/src/main/java/com/example/koistock/ui/shell/AppShell.kt`

**Interfaces:**
- Consumes: `CountViewModel.scannedSkuRows`, `List<LocationNode>`, `LocationType.ZONE`, and `LocationType.SHELF`.
- Produces: `fun toggleExpandedSku(currentSku: String?, tappedSku: String): String?`, `CountScreen(vm, expectedItems, locations)`, and a read-only `CountedSkuAccordionCard`.

- [ ] **Step 1: Write the failing accordion-state tests**

Create `CountAccordionStateTest.kt`:

```kotlin
package com.example.koistock.ui.count

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CountAccordionStateTest {
    @Test
    fun tappingClosedSku_opensIt() {
        assertEquals("S1", toggleExpandedSku(null, "S1"))
    }

    @Test
    fun tappingOpenSku_closesIt() {
        assertNull(toggleExpandedSku("S1", "S1"))
    }

    @Test
    fun tappingAnotherSku_replacesOpenSku() {
        assertEquals("S2", toggleExpandedSku("S1", "S2"))
    }
}
```

- [ ] **Step 2: Run the accordion-state test and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "com.example.koistock.ui.count.CountAccordionStateTest"
```

Expected: compilation fails because `toggleExpandedSku` does not exist.

- [ ] **Step 3: Add the minimal state transition**

Add a top-level function in `CountScreen.kt`:

```kotlin
internal fun toggleExpandedSku(currentSku: String?, tappedSku: String): String? =
    if (currentSku == tappedSku) null else tappedSku
```

- [ ] **Step 4: Run the accordion-state test and verify GREEN**

Run the Step 2 command again.

Expected: all three `CountAccordionStateTest` tests pass.

- [ ] **Step 5: Replace the plain scanned-SKU block with the summary and accordion**

Change `CountScreen` to accept synchronized locations:

```kotlin
fun CountScreen(
    vm: CountViewModel,
    expectedItems: List<ExpectedItem>,
    locations: List<LocationNode>,
)
```

Collect rows and retain one expanded SKU:

```kotlin
val scannedSkuRows by vm.scannedSkuRows.collectAsState()
var expandedSku by remember { mutableStateOf<String?>(null) }

LaunchedEffect(scannedSkuRows) {
    if (scannedSkuRows.none { it.product.sku == expandedSku }) expandedSku = null
}
```

Render the summary from the same row state:

```kotlin
Text(
    "Đã quét: ${scannedSkuRows.sumOf { it.scannedTagCount }} tag · ${scannedSkuRows.size} SKU",
    style = MaterialTheme.typography.titleMedium,
)
```

For each row, render a clickable card whose header contains `product.name`, `${product.sku} · Đã quét ${row.scannedTagCount} tag`, and `KeyboardArrowDown`/`KeyboardArrowUp`. When expanded, resolve `product.locationCode` to a shelf and its parent zone, then show:

```kotlin
HorizontalDivider()
DetailLine("SKU", product.sku)
DetailLine("Tên sản phẩm", product.name)
DetailLine("Tồn kho", "${product.quantity} ${product.unit}")
DetailLine("Loại quản lý", if (product.trackingMode == TrackingMode.SERIALIZED) "Theo serial" else "Hàng số lượng")
DetailLine("Vị trí", shelf?.let { "${zone?.code ?: "?"} → ${it.code} · ${it.name}" } ?: "Chưa có vị trí mặc định")
```

Keep reconciliation results below the action buttons and do not move their data into the accordion.

- [ ] **Step 6: Supply locations from `AppShell`**

Update the existing route call:

```kotlin
CountScreen(
    vm = countVm,
    expectedItems = expectedItems,
    locations = locations,
)
```

- [ ] **Step 7: Compile and run focused count tests**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:compileDebugKotlin testDebugUnitTest --tests "com.example.koistock.ui.count.CountViewModelTest" --tests "com.example.koistock.ui.count.CountAccordionStateTest"
```

Expected: Kotlin compilation succeeds and both test classes pass.

- [ ] **Step 8: Run the complete unit-test suite**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL` with no failed tests.

- [ ] **Step 9: Commit Task 2**

```powershell
git add app/src/main/java/com/example/koistock/ui/count/CountScreen.kt app/src/main/java/com/example/koistock/ui/shell/AppShell.kt app/src/test/java/com/example/koistock/ui/count/CountAccordionStateTest.kt
git commit -m "feat: add scanned SKU accordion"
```

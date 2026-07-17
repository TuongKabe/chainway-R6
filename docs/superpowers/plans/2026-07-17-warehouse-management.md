# Warehouse Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add searchable and filterable SKU management, data-backed zone/shelf management, startup/post-save synchronization, and shelf selection in Putaway.

**Architecture:** `AppShell` owns one `WarehouseSyncCoordinator` and shared product/location flows. Focused ViewModels implement pure filtering and validated saves; Compose screens consume state and reuse a shared `ShelfSelector` so existing codes are selected instead of typed.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, coroutines/StateFlow, Retrofit repositories, JUnit 4, kotlinx-coroutines-test, Gradle.

## Global Constraints

- SKU, zone code, and shelf code remain immutable while editing.
- Synchronize at app startup and after every successful save; do not add periodic sync.
- Product default location and Putaway accept synchronized shelves only.
- Do not add categories, deletion, offline persistence, or backend schema changes.
- Preserve form input on save/sync failure.

---

### Task 1: Shared synchronization coordinator

**Files:**
- Create: `app/src/main/java/com/example/koistock/data/remote/WarehouseSyncCoordinator.kt`
- Create: `app/src/test/java/com/example/koistock/data/remote/WarehouseSyncCoordinatorTest.kt`
- Modify: `app/src/main/java/com/example/koistock/ui/shell/AppShell.kt`

**Interfaces:**
- Produces: `WarehouseSyncResult` (`Success`, `SavedButSyncFailed(message)`, `LoadFailed(message)`) and `WarehouseSyncCoordinator.syncAndRefresh(): WarehouseSyncResult`.
- Consumes: `HttpSyncRepository.reconcile()`, `HttpProductRepository.refresh()`, and `HttpLocationRepository.refresh()`.

- [ ] **Step 1: Write failing coordinator tests** proving reconcile happens before both refresh calls, refresh still reports failure, and a failed reconcile produces `SavedButSyncFailed`.
- [ ] **Step 2: Run** `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "com.example.koistock.data.remote.WarehouseSyncCoordinatorTest"` and verify RED because the coordinator does not exist.
- [ ] **Step 3: Implement the coordinator** with injectable suspend functions:

```kotlin
sealed interface WarehouseSyncResult {
    data object Success : WarehouseSyncResult
    data class SavedButSyncFailed(val message: String) : WarehouseSyncResult
    data class LoadFailed(val message: String) : WarehouseSyncResult
}

class WarehouseSyncCoordinator(
    private val reconcile: suspend () -> SyncOutcome,
    private val refreshProducts: suspend () -> Unit,
    private val refreshLocations: suspend () -> Unit,
) {
    suspend fun syncAndRefresh(): WarehouseSyncResult {
        val sync = reconcile()
        val refreshFailure = runCatching {
            refreshProducts()
            refreshLocations()
        }.exceptionOrNull()
        if (refreshFailure != null) {
            return WarehouseSyncResult.LoadFailed(refreshFailure.message ?: "Không tải lại được dữ liệu kho")
        }
        return when (sync) {
            is SyncOutcome.Success -> WarehouseSyncResult.Success
            is SyncOutcome.Failure -> WarehouseSyncResult.SavedButSyncFailed(sync.message)
        }
    }
}
```

- [ ] **Step 4: Replace startup refresh in `AppShell`** with one remembered coordinator and `syncAndRefresh()`; keep manual sync wired to the same operation and snackbar reporting.
- [ ] **Step 5: Run coordinator tests and existing unit tests**; expect PASS.

### Task 2: Product management state, filtering, and validated saves

**Files:**
- Create: `app/src/main/java/com/example/koistock/ui/warehouse/ProductManagementViewModel.kt`
- Create: `app/src/test/java/com/example/koistock/ui/warehouse/ProductManagementViewModelTest.kt`
- Reuse: `app/src/test/java/com/example/koistock/fakes/FakeRepositories.kt`

**Interfaces:**
- Consumes: `ProductRepo`, `LocationRepo`, `WarehouseSyncCoordinator`, and a `CoroutineScope`.
- Produces: `ProductFilters`, `ProductEditorState`, `ProductSaveResult`, `filteredProducts`, `availableUnits`, `zones`, and `shelvesForSelectedZone` StateFlows.

- [ ] **Step 1: Write failing tests** for case-insensitive SKU/name search, combined tracking/stock/unit/zone/shelf filters, zone narrowing shelf choices, clear filters, selecting a product with prefilled immutable SKU, undo, blank-name/unit validation, stale-shelf rejection, successful upsert+sync, save failure retaining editor state, and sync failure returning partial success.
- [ ] **Step 2: Run** `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "com.example.koistock.ui.warehouse.ProductManagementViewModelTest"` and verify RED.
- [ ] **Step 3: Implement filter types**:

```kotlin
enum class StockFilter { ALL, IN_STOCK, OUT_OF_STOCK }

data class ProductFilters(
    val query: String = "",
    val trackingMode: TrackingMode? = null,
    val stock: StockFilter = StockFilter.ALL,
    val unit: String? = null,
    val zoneCode: String? = null,
    val shelfCode: String? = null,
)
```

Filtering must combine every active predicate and resolve a product shelf to its parent zone through synchronized `LocationNode` values.
- [ ] **Step 4: Implement editor/save state** with read-only SKU, prefilled fields, `resetEditor()`, and `save()` that validates current shelf membership, calls `productRepo.upsert`, then `syncAndRefresh` without clearing the editor on any failure.
- [ ] **Step 5: Run product-management tests and the full unit suite**; expect PASS.

### Task 3: Product and warehouse management Compose screens

**Files:**
- Create: `app/src/main/java/com/example/koistock/ui/common/ShelfSelector.kt`
- Create: `app/src/main/java/com/example/koistock/ui/warehouse/WarehouseManagementScreen.kt`
- Create: `app/src/main/java/com/example/koistock/ui/warehouse/ProductManagementScreen.kt`
- Modify: `app/src/main/java/com/example/koistock/ui/shell/AppDestination.kt`
- Modify: `app/src/main/java/com/example/koistock/ui/shell/AppShell.kt`
- Modify: `app/src/main/java/com/example/koistock/ui/shell/DashboardScreen.kt` only if its menu rendering requires no additional changes beyond the destination list.

**Interfaces:**
- `ShelfSelector(locations, selectedShelfCode, selectedZoneCode?, enabled, onShelfSelected)` emits only codes belonging to `LocationType.SHELF` with an existing zone parent.
- `WarehouseManagementScreen` receives product and zone ViewModels plus synchronized locations.

- [ ] **Step 1: Implement `ShelfSelector`** as Material 3 exposed dropdown controls: select zone first, then a shelf narrowed to that zone; render both code and name; never expose free text.
- [ ] **Step 2: Implement `ProductManagementScreen`** with search, filter dropdowns/chips, clear-all, product list, prefilled edit dialog, read-only SKU, fixed tracking selector, `ShelfSelector`, save, undo, loading/empty/error feedback.
- [ ] **Step 3: Implement `WarehouseManagementScreen`** with Material 3 primary tabs for Products and Zones/Shelves.
- [ ] **Step 4: Add one `Warehouse` destination** titled `Quản lý kho`, replace the standalone Zones dashboard entry, and wire the shared repositories/coordinator/ViewModels in `AppShell`.
- [ ] **Step 5: Compile** using `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:compileDebugKotlin`; expect PASS.

### Task 4: Data-backed zone and shelf editing

**Files:**
- Modify: `app/src/main/java/com/example/koistock/ui/zones/ZoneViewModel.kt`
- Modify: `app/src/main/java/com/example/koistock/ui/zones/ZoneScreen.kt`
- Modify: `app/src/test/java/com/example/koistock/ui/zones/ZoneViewModelTest.kt`

**Interfaces:**
- Consumes: `LocationRepo`, `WarehouseSyncCoordinator` and synchronized locations.
- Produces: add/edit methods whose code parameter is immutable for existing records and whose shelf parent must reference a current `ZONE`.

- [ ] **Step 1: Add failing tests** for blank names, duplicate new codes, immutable edited codes, invalid shelf parents, editing with prefilled values, sync after save, retained state on failure, and newly added zone becoming the suggested shelf parent.
- [ ] **Step 2: Run** `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "com.example.koistock.ui.zones.ZoneViewModelTest"`; verify RED.
- [ ] **Step 3: Extend `ZoneViewModel`** with explicit editor state and `saveZone`/`saveShelf`; validate against `locations.value`; upsert then call the coordinator; retain state for errors.
- [ ] **Step 4: Redesign `ZoneScreen`** into searchable grouped zone cards with child shelves, prefilled add/edit dialogs, immutable code fields while editing, and a parent-zone dropdown derived from current `ZONE` nodes.
- [ ] **Step 5: Run zone tests and compile**; expect PASS.

### Task 5: Reuse synchronized shelf selection in Putaway

**Files:**
- Modify: `app/src/main/java/com/example/koistock/ui/putaway/PutawayScreen.kt`
- Modify: `app/src/main/java/com/example/koistock/ui/shell/AppShell.kt`
- Modify: `app/src/test/java/com/example/koistock/ui/putaway/PutawayViewModelTest.kt`

**Interfaces:**
- `PutawayScreen(vm, locations)` consumes synchronized locations and passes only a selected shelf code to existing `PutawayViewModel.setLocationByTag`.

- [ ] **Step 1: Add a failing ViewModel test** proving blank/unknown location is not applied by introducing `setAvailableShelves(Set<String>)` and validating `setLocationByTag` against it.
- [ ] **Step 2: Run** `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "com.example.koistock.ui.putaway.PutawayViewModelTest"`; verify RED.
- [ ] **Step 3: Implement available-shelf validation** in the ViewModel and replace Putaway free text with `ShelfSelector`; update available shelf codes from synchronized `LocationNode` values.
- [ ] **Step 4: Run Putaway tests**; expect PASS.

### Task 6: Final verification

**Files:**
- Review every file listed above.

- [ ] **Step 1: Run focused tests** for warehouse sync, product management, zones, and Putaway.
- [ ] **Step 2: Run full verification**: `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:compileDebugKotlin testDebugUnitTest` and require `BUILD SUCCESSFUL`.
- [ ] **Step 3: Run `git diff --check`** and inspect `git diff` to confirm no unrelated user changes were overwritten.

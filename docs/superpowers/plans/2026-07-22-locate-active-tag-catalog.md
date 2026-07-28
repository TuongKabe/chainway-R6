# Locate Active-Tag Catalog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make “Tìm sản phẩm” list and search only SKUs that have at least one active EPC mapping.

**Architecture:** Add one list-active-tags operation to the existing tag API/repository. Move locate catalog loading, grouping, product enrichment, search normalization, loading, and error state into `LocateViewModel`; keep Compose presentation-only and preserve the existing RFID locate loop.

**Tech Stack:** Kotlin, Retrofit, Jetpack Compose Material 3, Kotlin Coroutines `StateFlow`, JUnit 4, kotlinx-coroutines-test.

## Global Constraints

- The EPC–SKU mapping table is the source of truth for whether a SKU is locatable.
- Only mappings whose status is exactly `active` after case normalization are included.
- One SKU is rendered once while retaining all of its active EPCs.
- Opening the Locate destination refreshes the catalog.
- Existing dirty R6 configuration changes must not be overwritten or reformatted.
- No backend table, assign flow, void flow, RFID matching, RSSI, or beep behavior is changed.

---

## File Structure

- `app/src/main/java/com/example/koistock/data/remote/KoiApiService.kt`: Retrofit contract for `GET api/epc-tags?status=active`.
- `app/src/main/java/com/example/koistock/data/remote/TagRepository.kt`: add `listActive()` to `TagRepo` and Firestore implementation.
- `app/src/main/java/com/example/koistock/data/remote/HttpRepositories.kt`: HTTP implementation of `listActive()`.
- `app/src/test/java/com/example/koistock/fakes/FakeRepositories.kt`: in-memory implementation used by catalog tests.
- `app/src/main/java/com/example/koistock/ui/locate/LocateViewModel.kt`: catalog state, grouping, product enrichment, normalized filtering, and existing RFID location behavior.
- `app/src/test/java/com/example/koistock/ui/locate/LocateViewModelTest.kt`: catalog and regression tests.
- `app/src/main/java/com/example/koistock/ui/locate/LocateScreen.kt`: render catalog state and selected catalog item without repository calls.
- `app/src/main/java/com/example/koistock/ui/shell/AppShell.kt`: inject `TagRepo` and `ProductRepo` into `LocateViewModel` and simplify the screen call.

### Task 1: Expose active EPC mappings

**Files:**
- Modify: `app/src/main/java/com/example/koistock/data/remote/KoiApiService.kt`
- Modify: `app/src/main/java/com/example/koistock/data/remote/TagRepository.kt`
- Modify: `app/src/main/java/com/example/koistock/data/remote/HttpRepositories.kt`
- Modify: `app/src/test/java/com/example/koistock/fakes/FakeRepositories.kt`
- Test: `app/src/test/java/com/example/koistock/data/remote/HttpTagRepositoryTest.kt`

**Interfaces:**
- Produces: `suspend fun TagRepo.listActive(): List<TagMapping>`.
- Consumes backend contract: `GET api/epc-tags?status=active` returning `ApiEnvelope<List<EpcTagDto>>`.

- [ ] **Step 1: Write the failing HTTP repository test**

Create a proxy-backed fake `KoiApiService` that returns active and void DTOs from `getActiveTags`, call `HttpTagRepository.listActive()`, and assert DTO fields map to `TagMapping` without losing EPC, SKU, status, serial, or location.

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "com.example.koistock.data.remote.HttpTagRepositoryTest"
```

Expected: compilation fails because `listActive` and `getActiveTags` do not exist.

- [ ] **Step 3: Implement the minimal repository contract**

Add:

```kotlin
@GET("api/epc-tags")
suspend fun getActiveTags(@Query("status") status: String = "active"): ApiEnvelope<List<EpcTagDto>>
```

Add `listActive()` to `TagRepo`. Implement HTTP mapping with `api.getActiveTags().data.map { it.toTagMapping() }`; implement Firestore with `whereEqualTo("status", "active")`; implement the fake by filtering its values.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the Step 2 command. Expected: `BUILD SUCCESSFUL`.

### Task 2: Build the locatable catalog in the ViewModel

**Files:**
- Modify: `app/src/main/java/com/example/koistock/ui/locate/LocateViewModel.kt`
- Modify: `app/src/test/java/com/example/koistock/ui/locate/LocateViewModelTest.kt`

**Interfaces:**
- Consumes: `TagRepo.listActive()`, `ProductRepo.observeAll()`, `ProductRepo.getBySku(sku)`.
- Produces:

```kotlin
data class LocatableProduct(val product: Product, val activeTags: List<TagMapping>)
sealed interface LocateCatalogState {
    data object Loading : LocateCatalogState
    data class Ready(val items: List<LocatableProduct>) : LocateCatalogState
    data class Error(val message: String) : LocateCatalogState
}
fun refreshCatalog()
fun filterLocatableProducts(items: List<LocatableProduct>, query: String): List<LocatableProduct>
```

- [ ] **Step 1: Write failing catalog rule tests**

Add tests proving: active mappings appear; untagged and void-only SKUs do not; multiple active EPCs produce one item; missing cache products are fetched by SKU; missing backend products produce `Error`; and search trims input and ignores case.

- [ ] **Step 2: Run the ViewModel tests and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "com.example.koistock.ui.locate.LocateViewModelTest"
```

Expected: compilation fails because catalog types and operations do not exist.

- [ ] **Step 3: Implement catalog loading and filtering**

Inject `TagRepo` and `ProductRepo`. On `refreshCatalog`, set `Loading`, load tags, filter status with `equals("active", ignoreCase = true)`, group by trimmed SKU, resolve cached products then missing products through `getBySku`, sort results by SKU, and publish `Ready`. Catch repository failures and publish `Error` with a concise Vietnamese message.

Implement normalized filtering as:

```kotlin
val normalized = query.trim()
if (normalized.isEmpty()) items else items.filter {
    it.product.sku.contains(normalized, ignoreCase = true) ||
        it.product.name.contains(normalized, ignoreCase = true)
}
```

- [ ] **Step 4: Run catalog and existing RFID tests and verify GREEN**

Run the Step 2 command. Expected: catalog tests and all existing RSSI/beep tests pass.

### Task 3: Render the catalog and remove UI repository access

**Files:**
- Modify: `app/src/main/java/com/example/koistock/ui/locate/LocateScreen.kt`
- Modify: `app/src/main/java/com/example/koistock/ui/shell/AppShell.kt`

**Interfaces:**
- Consumes: `LocateViewModel.catalogState`, `refreshCatalog()`, and `LocatableProduct.activeTags`.
- Produces: `LocateScreen(vm, isConnected, onOpenPairing)` with no `products` or `tagRepo` parameters.

- [ ] **Step 1: Update the screen to render ViewModel state**

Call `vm.refreshCatalog()` from `LaunchedEffect(vm)` when the destination opens. Render a progress indicator for `Loading`, an error card with **Thử lại** for `Error`, an empty-state message for `Ready(emptyList())`, and `ProductPicker` for non-empty `Ready` items.

- [ ] **Step 2: Use the selected catalog item directly**

Store selected SKU, resolve the matching `LocatableProduct`, and pass its sorted `activeTags` into `LocatePhase`. Remove `TagRepo`, the tag-loading `LaunchedEffect`, nullable tag loading state, and the impossible “SKU chưa có tag” branch.

- [ ] **Step 3: Wire dependencies in AppShell**

Construct `LocateViewModel(reader, scope, profile, beeper, tagRepo, productRepo)` and call the simplified `LocateScreen`. Preserve all unrelated R6 settings hunks already present in `AppShell.kt`.

- [ ] **Step 4: Compile and run focused tests**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:compileDebugKotlin testDebugUnitTest --tests "com.example.koistock.data.remote.HttpTagRepositoryTest" --tests "com.example.koistock.ui.locate.LocateViewModelTest"
```

Expected: `BUILD SUCCESSFUL`.

### Task 4: Full regression verification

**Files:**
- Verify only; no unrelated refactoring.

- [ ] **Step 1: Run the complete unit-test suite**

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL` with zero failed tests.

- [ ] **Step 2: Inspect the final diff**

Confirm the diff only changes the API/repositories, locate feature, fakes/tests, and the minimal Locate hunk in `AppShell.kt`. Confirm existing R6 working-tree changes remain intact.


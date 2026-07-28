# Locate Catalog Batch Loading Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace repeated sequential per-SKU enrichment with one lightweight concurrent metadata request per tagged SKU.

**Architecture:** `HttpLocateCatalogRepository` loads active tags once, groups unique item codes, and fetches their item metadata concurrently. `LocateViewModel` consumes that single repository contract; Compose remains unchanged.

**Tech Stack:** Kotlin coroutines, Retrofit, StateFlow, JUnit 4, kotlinx-coroutines-test.

## Global Constraints

- Exactly one `getActiveTags` and one `getItem` call per unique tagged SKU per catalog refresh.
- No `getBySku`, bin, quantity enrichment, or per-SKU tag calls in the Locate catalog path.
- Only active tags and active items are included.
- Inactive item metadata for an active tag is skipped without blocking valid catalog rows.
- Preserve unrelated R6 working-tree changes.

---

### Task 1: Add the batch catalog repository

**Files:**
- Create: `app/src/main/java/com/example/koistock/data/remote/LocateCatalogRepository.kt`
- Create: `app/src/test/java/com/example/koistock/data/remote/HttpLocateCatalogRepositoryTest.kt`

**Interfaces:**
- Consumes: `KoiApiService.getActiveTags()` and `KoiApiService.getItem(code)`.
- Produces: `interface LocateCatalogRepo { suspend fun load(): List<LocatableProduct> }` and `HttpLocateCatalogRepository`.

- [ ] **Step 1: Write a failing proxy API test**

The test returns active/void tags, records every proxy method name, calls `load()`, and asserts grouped EPCs, quantity, warehouse fallback, one `getActiveTags`, and one `getItem` per unique SKU with no other API calls.

- [ ] **Step 2: Verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "com.example.koistock.data.remote.HttpLocateCatalogRepositoryTest"
```

Expected: compilation fails because the repository contract does not exist.

- [ ] **Step 3: Implement the repository**

Use `coroutineScope`, defensive active filtering, grouping by trimmed `itemCode`, one `async { api.getItem(sku) }` per unique SKU, `awaitAll`, and direct DTO-to-`Product` mapping. Return `null` for inactive item metadata and remove nulls after `awaitAll`.

- [ ] **Step 4: Verify GREEN**

Run the Step 2 command. Expected: `BUILD SUCCESSFUL`.

### Task 2: Switch LocateViewModel to the batch repository

**Files:**
- Modify: `app/src/main/java/com/example/koistock/ui/locate/LocateViewModel.kt`
- Modify: `app/src/test/java/com/example/koistock/ui/locate/LocateViewModelTest.kt`
- Modify: `app/src/main/java/com/example/koistock/ui/shell/AppShell.kt`

**Interfaces:**
- Consumes: `LocateCatalogRepo.load()`.
- Produces: unchanged `LocateCatalogState` and `refreshCatalog()` behavior.

- [ ] **Step 1: Change catalog tests to a recording `LocateCatalogRepo` fake**

Assert `refreshCatalog()` publishes the fake items and calls `load()` once; assert a thrown repository error becomes `LocateCatalogState.Error`.

- [ ] **Step 2: Verify RED**

Run the focused Locate test. Expected: compilation fails because the constructor does not accept `catalogRepo`.

- [ ] **Step 3: Replace TagRepo/ProductRepo enrichment**

Inject `LocateCatalogRepo`, call only `load()` in `refreshCatalog()`, and construct `HttpLocateCatalogRepository(api)` in the Locate route. Remove Locate catalog injection of `tagRepo` and `productRepo`.

- [ ] **Step 4: Verify focused tests and compile**

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:compileDebugKotlin testDebugUnitTest --tests "com.example.koistock.data.remote.HttpLocateCatalogRepositoryTest" --tests "com.example.koistock.ui.locate.LocateViewModelTest"
```

Expected: `BUILD SUCCESSFUL`.

### Task 3: Regression verification

- [ ] **Step 1: Run all unit tests**

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Inspect the diff**

Confirm the old per-SKU `getBySku` path is absent from `LocateViewModel`, proxy test records exactly two API calls, and unrelated R6 hunks remain unchanged.

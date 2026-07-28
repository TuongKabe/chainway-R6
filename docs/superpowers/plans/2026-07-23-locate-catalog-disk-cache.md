# Locate Catalog Disk Cache Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show the last good Locate catalog immediately from disk and refresh only changed SKUs in the background.

**Architecture:** Preferences DataStore stores a versioned Gson snapshot. A cached HTTP repository compares fresh active-tag signatures with the snapshot, reuses unchanged product metadata, fetches only changed/new SKUs concurrently, and performs a full metadata validation every six hours.

**Tech Stack:** Kotlin coroutines, Preferences DataStore, Gson, Retrofit, StateFlow, JUnit 4, kotlinx-coroutines-test.

## Global Constraints

- Cache must never block showing the last good catalog.
- HTTP or cache-write failures must not delete a valid snapshot.
- Only active tags and active items are displayed.
- Full metadata validation interval is exactly `6 * 60 * 60 * 1000L` milliseconds.
- Preserve unrelated R6 and Assign working-tree changes.

---

### Task 1: Persist versioned Locate snapshots

**Files:**
- Create: `app/src/main/java/com/example/koistock/data/remote/LocateCatalogStore.kt`
- Create: `app/src/test/java/com/example/koistock/data/remote/LocateCatalogStoreTest.kt`

**Interfaces:**
- Produces `LocateCatalogSnapshot(schemaVersion, items, tagSignatureBySku, metadataVerifiedAt)`.
- Produces `LocateCatalogStore.read(): LocateCatalogSnapshot?` and `write(snapshot)`.

- [ ] **Step 1: Write failing DataStore tests**

Use `PreferenceDataStoreFactory` with a temporary preferences file. Assert an empty store returns null, a snapshot round-trips products/tags/signatures/timestamp, malformed JSON returns null, and a schema version other than `1` returns null.

- [ ] **Step 2: Verify RED**

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "com.example.koistock.data.remote.LocateCatalogStoreTest"
```

Expected: compilation fails because store and snapshot types do not exist.

- [ ] **Step 3: Implement the store**

Use one `stringPreferencesKey("locate_catalog_snapshot_v1")`, `data.first()`, `edit`, and Gson. Catch JSON parsing failures in `read` and return null. Accept only `schemaVersion == 1`.

- [ ] **Step 4: Verify GREEN**

Run Step 2 again. Expected: `BUILD SUCCESSFUL`.

### Task 2: Add delta refresh and six-hour validation

**Files:**
- Modify: `app/src/main/java/com/example/koistock/data/remote/LocateCatalogRepository.kt`
- Modify: `app/src/test/java/com/example/koistock/data/remote/HttpLocateCatalogRepositoryTest.kt`

**Interfaces:**
- `LocateCatalogRepo.loadCached(): List<LocatableProduct>?`
- `LocateCatalogRepo.refresh(): List<LocatableProduct>`
- `HttpLocateCatalogRepository(api, store, now, metadataTtlMs = 21_600_000L)`.

- [ ] **Step 1: Write failing delta tests**

Add recording fake store/API cases proving: unchanged tag signatures cause zero `getItem` calls; one changed SKU causes one `getItem`; removed tag SKU disappears; empty/expired cache fetches every active SKU; inactive refreshed item is excluded; successful refresh writes the new snapshot.

- [ ] **Step 2: Verify RED**

Run the existing HTTP Locate repository test. Expected: compilation fails because the repository still exposes only `load()`.

- [ ] **Step 3: Implement signature-based refresh**

Build signatures from tag fields sorted by EPC. Load fresh tags once, compare per-SKU signatures, reuse unchanged cached products, fetch required item DTOs with `async/awaitAll`, remove inactive/missing active-tag rows, and write the complete snapshot only after a successful refresh.

- [ ] **Step 4: Verify GREEN**

Run focused store and repository tests. Expected: `BUILD SUCCESSFUL`.

### Task 3: Publish cache first and refresh in background

**Files:**
- Modify: `app/src/main/java/com/example/koistock/ui/locate/LocateViewModel.kt`
- Modify: `app/src/test/java/com/example/koistock/ui/locate/LocateViewModelTest.kt`
- Modify: `app/src/main/java/com/example/koistock/ui/locate/LocateScreen.kt`

**Interfaces:**
- `LocateCatalogState.Ready(items, refreshing, warning)`.
- `LocateViewModel.loadCatalog()` loads cache then refreshes without replacing cached content by `Loading`.

- [ ] **Step 1: Write failing ViewModel tests**

Assert cache is published before a suspended refresh completes, refresh success replaces cache, refresh failure keeps cache with warning, and no-cache failure produces `Error`.

- [ ] **Step 2: Verify RED**

Run `LocateViewModelTest`. Expected: compilation fails against the old `load()` contract/state.

- [ ] **Step 3: Implement cache-first state flow**

Replace `refreshCatalog()` entry behavior with `loadCatalog()`: read cache, publish `Ready(cache, refreshing=true)`, refresh, then publish fresh `Ready`; on failure retain cached items and set warning. Keep retry using the same operation.

- [ ] **Step 4: Update Compose presentation**

Call `loadCatalog()` on entry. Keep the list visible while `refreshing`; render a small progress indicator and nonblocking warning with retry. Preserve selection, search, EPC selector, RSSI and beep flow.

- [ ] **Step 5: Verify focused tests and compile**

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:compileDebugKotlin testDebugUnitTest --tests "com.example.koistock.data.remote.LocateCatalogStoreTest" --tests "com.example.koistock.data.remote.HttpLocateCatalogRepositoryTest" --tests "com.example.koistock.ui.locate.LocateViewModelTest"
```

Expected: `BUILD SUCCESSFUL`.

### Task 4: Wire the shared DataStore and verify regressions

**Files:**
- Modify: `app/src/main/java/com/example/koistock/MainActivity.kt`
- Modify: `app/src/main/java/com/example/koistock/ui/shell/AppShell.kt`

- [ ] **Step 1: Pass DataStore into AppShell**

Add `dataStore: DataStore<Preferences>` to `AppShell`, pass the existing `MainActivity.dataStore`, remember `LocateCatalogStore(dataStore)`, and construct `HttpLocateCatalogRepository(api, store, now)` once.

- [ ] **Step 2: Run the complete suite**

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Inspect final diff**

Confirm Locate opens from cache, refresh uses signature deltas, TTL is six hours, no valid cache is erased on failure, and unrelated working-tree changes remain intact.

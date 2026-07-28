# Locate EPC Warehouse Display Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show the warehouse and precise stored position for each active EPC in the “Tìm sản phẩm – định vị theo EPC” flow.

**Architecture:** Preserve `warehouse`, `location_code`, and `bin` independently from the Supabase catalog payload through `CatalogTagDto` and `TagMapping`. Use a small pure formatter for display fallbacks, then render the values in both the EPC selector and the selected-EPC locate header.

**Tech Stack:** Kotlin 2.2.10, Jetpack Compose, Retrofit/Gson, DataStore cache, Supabase PostgreSQL RPC and Edge Function, JUnit 4.

## Global Constraints

- Only modify the locate catalog and `LocateScreen`; do not change `LookupScreen`.
- Do not change RFID inventory, RSSI, beep, or trigger behavior.
- Display `warehouse` separately from `location_code`; position falls back from `location_code` to `bin`, then `Chưa gán`.
- Existing cached JSON without the new fields must remain readable through nullable/default DTO properties.

---

### Task 1: Preserve warehouse and bin in the Android catalog model

**Files:**
- Modify: `app/src/main/java/com/example/koistock/data/model/TagMapping.kt`
- Modify: `app/src/main/java/com/example/koistock/data/remote/SupabaseCatalog.kt`
- Create: `app/src/test/java/com/example/koistock/data/remote/SupabaseLocateCatalogRepositoryTest.kt`

**Interfaces:**
- Consumes: `CatalogPayload`, `CatalogTagDto`, and `SupabaseLocateCatalogRepository`.
- Produces: `TagMapping.warehouse: String?`, `TagMapping.bin: String?`, and catalog mapping that uses named constructor arguments.

- [ ] **Step 1: Write the failing repository mapping test**

Create a fake `SupabaseCatalogApi` returning one item and one tag where `warehouse = "KHO-A"`, `locationCode = "A-01"`, and `bin = "BIN-7"`; use an in-memory `CatalogPayloadCache`. Assert after `refresh()`:

```kotlin
val tag = result.single().activeTags.single()
assertEquals("KHO-A", tag.warehouse)
assertEquals("A-01", tag.locationCode)
assertEquals("BIN-7", tag.bin)
assertEquals(21L, tag.syncRev)
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat testDebugUnitTest --tests "com.example.koistock.data.remote.SupabaseLocateCatalogRepositoryTest"
```

Expected: Kotlin compilation fails because `CatalogTagDto.bin`, `TagMapping.warehouse`, and `TagMapping.bin` do not exist.

- [ ] **Step 3: Add nullable fields without breaking old cache data**

Append fields to `TagMapping` so existing positional call sites retain their meanings:

```kotlin
val warehouse: String? = null,
val bin: String? = null,
```

Add them to `toMap()` and `fromMap()`. Add `val bin: String? = null` to `CatalogTagDto`. Replace the positional Supabase mapper with named arguments:

```kotlin
TagMapping(
    epc = tag.epc,
    sku = tag.itemCode,
    unitSerial = tag.serialNo,
    status = tag.status,
    locationCode = tag.locationCode,
    origin = "supabase",
    syncRev = tag.syncRev,
    warehouse = tag.warehouse,
    bin = tag.bin,
)
```

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the command from Step 2. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit the model slice**

```powershell
git add app/src/main/java/com/example/koistock/data/model/TagMapping.kt app/src/main/java/com/example/koistock/data/remote/SupabaseCatalog.kt app/src/test/java/com/example/koistock/data/remote/SupabaseLocateCatalogRepositoryTest.kt
git commit -m "feat: preserve EPC warehouse location"
```

### Task 2: Add bin to the Supabase catalog contract

**Files:**
- Remote migration: replace `public.koistock_catalog_payload(bigint)` through Supabase MCP.
- Remote deployment: deploy a new version of Edge Function `catalog` only if its response adapter requires a change.

**Interfaces:**
- Consumes: `koistock.epc_tag.bin`.
- Produces: every tag JSON object includes `bin` alongside `warehouse` and `locationCode`.

- [ ] **Step 1: Verify the current contract omits bin**

Call `/functions/v1/catalog/snapshot` with the publishable authorization headers and inspect a tag known to have a non-null `bin`. Expected before migration: the object has no `bin` key.

- [ ] **Step 2: Update the RPC using an idempotent migration**

In the tag `jsonb_build_object`, add:

```sql
'warehouse', warehouse,
'locationCode', location_code,
'bin', bin,
'syncRev', sync_rev
```

Keep `security definer`, the fixed `search_path`, revoked public execution, and the `service_role` grant unchanged.

- [ ] **Step 3: Verify the live snapshot contract**

Call the snapshot again. Assert it still returns 1,414 items and 884 tags at the current database state, and confirm tag JSON contains `bin` (including JSON null when no bin is assigned).

- [ ] **Step 4: Record the remote change**

Check `list_migrations` and confirm the new migration is present. No local commit is required because this repository currently has no Supabase migrations directory.

### Task 3: Format and render EPC-specific location

**Files:**
- Create: `app/src/main/java/com/example/koistock/ui/locate/EpcLocationText.kt`
- Create: `app/src/test/java/com/example/koistock/ui/locate/EpcLocationTextTest.kt`
- Modify: `app/src/main/java/com/example/koistock/ui/locate/LocateScreen.kt`

**Interfaces:**
- Consumes: `TagMapping.warehouse`, `TagMapping.locationCode`, and `TagMapping.bin`.
- Produces: `warehouseText(tag: TagMapping): String` and `positionText(tag: TagMapping): String`.

- [ ] **Step 1: Write failing formatter tests**

```kotlin
@Test fun position_prefersLocationCodeOverBin() {
    val tag = TagMapping("E", "S", locationCode = "A-01", bin = "BIN-7")
    assertEquals("A-01", positionText(tag))
}

@Test fun position_fallsBackToBinThenUnassigned() {
    assertEquals("BIN-7", positionText(TagMapping("E", "S", bin = "BIN-7")))
    assertEquals("Chưa gán", positionText(TagMapping("E", "S")))
}

@Test fun warehouse_usesSeparateWarehouseField() {
    assertEquals("KHO-A", warehouseText(TagMapping("E", "S", warehouse = "KHO-A")))
    assertEquals("Chưa gán", warehouseText(TagMapping("E", "S")))
}
```

- [ ] **Step 2: Run formatter tests and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat testDebugUnitTest --tests "com.example.koistock.ui.locate.EpcLocationTextTest"
```

Expected: compilation fails because the formatter functions do not exist.

- [ ] **Step 3: Implement minimal formatter functions**

```kotlin
internal fun warehouseText(tag: TagMapping): String =
    tag.warehouse?.trim()?.takeIf(String::isNotEmpty) ?: "Chưa gán"

internal fun positionText(tag: TagMapping): String =
    tag.locationCode?.trim()?.takeIf(String::isNotEmpty)
        ?: tag.bin?.trim()?.takeIf(String::isNotEmpty)
        ?: "Chưa gán"
```

- [ ] **Step 4: Render values in both locate states**

In `TagSelector`, beneath serial, render:

```kotlin
Text("Kho: ${warehouseText(tag)}", style = MaterialTheme.typography.bodySmall)
Text("Vị trí: ${positionText(tag)}", style = MaterialTheme.typography.bodySmall)
```

In `LocatePhase`, derive `selectedTag` from `selectedEpc` and render the same two lines beneath the SKU header. Do not use `product.locationCode` for these EPC-specific lines.

- [ ] **Step 5: Run formatter tests and the locate ViewModel suite**

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat testDebugUnitTest --tests "com.example.koistock.ui.locate.EpcLocationTextTest" --tests "com.example.koistock.ui.locate.LocateViewModelTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit the UI slice**

```powershell
git add app/src/main/java/com/example/koistock/ui/locate/EpcLocationText.kt app/src/main/java/com/example/koistock/ui/locate/LocateScreen.kt app/src/test/java/com/example/koistock/ui/locate/EpcLocationTextTest.kt
git commit -m "feat: show warehouse for locate EPC"
```

### Task 4: Full verification and device installation

**Files:**
- Verify: `app/build/outputs/apk/debug/app-debug.apk`

**Interfaces:**
- Consumes: completed Android and Supabase changes.
- Produces: tested APK installed on the connected `SM-S908E`.

- [ ] **Step 1: Run fresh full verification**

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --no-daemon --console=plain :app:compileDebugKotlin testDebugUnitTest :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL` and zero failing tests.

- [ ] **Step 2: Install without clearing offline data**

```powershell
$adb='C:\Users\LOQ\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$deviceLine = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\sdevice$' } | Select-Object -First 1
$deviceSerial = ($deviceLine -split '\s+')[0]
& $adb -s $deviceSerial install --no-streaming -r 'app\build\outputs\apk\debug\app-debug.apk'
```

Expected: `Success`. The `-r` option preserves the local catalog cache.

- [ ] **Step 3: Launch and smoke-test**

Open `com.example.koistock/.MainActivity`, enter “Tìm sản phẩm”, select `G65`, and confirm its EPC card and selected locate header show separate `Kho` and `Vị trí` lines.

- [ ] **Step 4: Review working-tree scope**

Run `git status --short` and ensure no unrelated files were staged or committed during this implementation.

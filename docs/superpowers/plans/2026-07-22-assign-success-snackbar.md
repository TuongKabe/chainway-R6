# Assign Success Snackbar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the blocking successful-assignment dialog with a short, actionless Material 3 Snackbar while retaining blocking dialogs for partial success, errors, and EPC/SKU conflicts.

**Architecture:** Pure presentation helpers will format success copy and decide which results require a dialog. `AssignTagScreen` will own a `SnackbarHostState`, acknowledge successful results once, enqueue their message, and keep existing dialog behavior for non-success results.

**Tech Stack:** Kotlin, Android Jetpack Compose Material 3, Kotlin Coroutines, JUnit 4.

## Global Constraints

- `AssignResult.Success` uses a Snackbar with no action button.
- `AssignResult.PartialSuccess` and `AssignResult.Error` remain blocking dialogs.
- SKU/EPC uniqueness conflicts remain unchanged.
- Snackbar copy is concise and omits barcode and backend note details.
- Assignment logic and backend calls remain unchanged.
- Preserve unrelated R6 configuration changes already present in the working tree.

---

## File Structure

- `app/src/main/java/com/example/koistock/ui/assign/AssignTagScreen.kt`: owns result routing, success formatting, Snackbar host, and existing dialogs.
- `app/src/test/java/com/example/koistock/ui/assign/AssignConflictPresentationTest.kt`: extends the existing presentation test suite with success copy and dialog-routing tests.

### Task 1: Route successful assignment to Snackbar

**Files:**
- Modify: `app/src/main/java/com/example/koistock/ui/assign/AssignTagScreen.kt`
- Modify: `app/src/test/java/com/example/koistock/ui/assign/AssignConflictPresentationTest.kt`

**Interfaces:**
- Consumes: `AssignResult.Success`, `AssignResult.PartialSuccess`, `AssignResult.Error`, and `AssignTagViewModel.acknowledgeResult()`.
- Produces: `AssignResult.Success.snackbarMessage(): String`, `AssignResult.requiresDialog(): Boolean`, and a `SnackbarHostState` rendered by `AssignTagScreen`.

- [ ] **Step 1: Write failing formatter and routing tests**

Add to `AssignConflictPresentationTest.kt`:

```kotlin
@Test
fun localSuccessSnackbar_includesEpcAndSku() {
    val result = AssignResult.Success(epc = "EPC-1", sku = "SKU1", note = "Đã gán tag thành công.")

    val message = result.snackbarMessage()

    assertTrue(message.contains("EPC-1"))
    assertTrue(message.contains("SKU1"))
    assertTrue(message.contains("Đã gán"))
}

@Test
fun webSuccessSnackbar_usesWebWording() {
    val result = AssignResult.Success(
        epc = "EPC-2",
        sku = "SKU2",
        note = "Đã gửi tag lên web và tự hoàn tất gán.",
    )

    val message = result.snackbarMessage()

    assertTrue(message.contains("EPC-2"))
    assertTrue(message.contains("SKU2"))
    assertTrue(message.contains("web", ignoreCase = true))
}

@Test
fun onlyNonSuccessResults_requireDialog() {
    assertFalse(AssignResult.Success("E", "S").requiresDialog())
    assertTrue(AssignResult.PartialSuccess("E", "S", "partial").requiresDialog())
    assertTrue(AssignResult.Error("error").requiresDialog())
}
```

Add `org.junit.Assert.assertFalse` to the test imports.

- [ ] **Step 2: Run the presentation tests and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "com.example.koistock.ui.assign.AssignConflictPresentationTest"
```

Expected: compilation fails because `snackbarMessage` and `requiresDialog` do not exist.

- [ ] **Step 3: Add the minimal pure presentation helpers**

In `AssignTagScreen.kt`, add:

```kotlin
internal fun AssignResult.Success.snackbarMessage(): String =
    if (note?.contains("web", ignoreCase = true) == true) {
        "Đã gửi EPC $epc cho SKU $sku về web"
    } else {
        "Đã gán EPC $epc cho SKU $sku"
    }

internal fun AssignResult.requiresDialog(): Boolean = this !is AssignResult.Success
```

- [ ] **Step 4: Run the presentation tests and verify GREEN**

Run the Step 2 command again.

Expected: all `AssignConflictPresentationTest` tests pass.

- [ ] **Step 5: Replace success dialog routing with Snackbar**

Add Material 3 Snackbar state and a result effect:

```kotlin
val snackbarHostState = remember { SnackbarHostState() }

LaunchedEffect(result) {
    (result as? AssignResult.Success)?.let { success ->
        vm.acknowledgeResult()
        snackbarHostState.showSnackbar(
            message = success.snackbarMessage(),
            duration = SnackbarDuration.Short,
        )
    }
}
```

Only render the existing result dialog for results that require it:

```kotlin
result?.takeIf { it.requiresDialog() }?.let { current ->
    AssignResultDialog(result = current, onDismiss = vm::acknowledgeResult)
}
```

Wrap the existing screen `Column` unchanged in `Box(modifier = Modifier.fillMaxSize())`. After the closing brace of that `Column`, but still inside the `Box`, add:

```kotlin
SnackbarHost(
    hostState = snackbarHostState,
    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
)
```

Keep `AssignResultDialog` exhaustive, but its success branch is no longer reached from the screen. Do not modify conflict dialog routing.

- [ ] **Step 6: Compile and run focused Assign tests**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:compileDebugKotlin testDebugUnitTest --tests "com.example.koistock.ui.assign.AssignConflictPresentationTest" --tests "com.example.koistock.ui.assign.AssignTagViewModelTest"
```

Expected: compilation succeeds and both test classes pass.

- [ ] **Step 7: Run the complete unit-test suite**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL` with no failed tests.

- [ ] **Step 8: Commit**

```powershell
git add app/src/main/java/com/example/koistock/ui/assign/AssignTagScreen.kt app/src/test/java/com/example/koistock/ui/assign/AssignConflictPresentationTest.kt
git commit -m "feat: show assign success in snackbar"
```

# RSSI Signal Parsing Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make changing Chainway R6 RSSI input produce changing locate signal values instead of silently falling back to `10/100`.

**Architecture:** Keep inventory-based locating and introduce a focused device-layer `RssiParser` that converts SDK text to dBm. Enable BLE RSSI reporting after connection, then pass parsed dBm through the existing signal mapping.

**Tech Stack:** Kotlin, Android, Chainway `RFIDWithUHFBLE`, JUnit 4, Gradle.

## Global Constraints

- Keep inventory-based locating and exact EPC filtering.
- Preserve `-70 dBm` only as fallback for missing or unsupported input.
- Do not change UI contracts or recalibrate the `-75..-29 dBm` range.

---

### Task 1: Testable RSSI parser

**Files:**
- Create: `app/src/main/java/com/example/koistock/device/RssiParser.kt`
- Create: `app/src/test/java/com/example/koistock/device/RssiParserTest.kt`
- Modify: `app/src/main/java/com/example/koistock/device/ChainwayRfidReader.kt:238-263`

**Interfaces:**
- Consumes: `UHFTAGInfo.rssi: String?`.
- Produces: `internal object RssiParser` and `fun parse(raw: String?): Int`.

- [ ] **Step 1: Write failing tests**

```kotlin
class RssiParserTest {
    @Test fun parsesSignedDecimal() = assertEquals(-62, RssiParser.parse("-62"))
    @Test fun parsesPositiveMagnitude() = assertEquals(-58, RssiParser.parse("58"))
    @Test fun parsesDecoratedDecimal() = assertEquals(-63, RssiParser.parse("RSSI: -62.6 dBm"))
    @Test fun parsesHexByte() = assertEquals(-59, RssiParser.parse("C5"))
    @Test fun invalidInputUsesFallback() = assertEquals(-70, RssiParser.parse("unknown"))
    @Test fun changingDecoratedInputDoesNotCollapseToFallback() {
        assertNotEquals(RssiParser.parse("RSSI: -71 dBm"), RssiParser.parse("RSSI: -45 dBm"))
    }
}
```

- [ ] **Step 2: Verify RED**

Run: `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "com.example.koistock.device.RssiParserTest"`

Expected: compilation fails because `RssiParser` does not exist.

- [ ] **Step 3: Implement the parser and wire it into the reader**

```kotlin
internal object RssiParser {
    private const val DefaultRssi = -70
    private val decimalPattern = Regex("[-+]?\\d+(?:[.,]\\d+)?")
    private val hexPattern = Regex("(?:0[xX])?[0-9A-Fa-f]{2}")

    fun parse(raw: String?): Int {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return DefaultRssi
        decimalPattern.find(text)?.value?.replace(',', '.')?.toDoubleOrNull()?.let { value ->
            val dbm = if (value > 0) -value else value
            return dbm.roundToInt().coerceIn(-100, 0)
        }
        if (hexPattern.matches(text)) {
            val hex = text.removePrefix("0x").removePrefix("0X").toInt(16)
            val signed = if (hex > 127) hex - 256 else hex
            return (if (signed > 0) -signed else signed).coerceIn(-100, 0)
        }
        return DefaultRssi
    }
}
```

Use `RssiParser.parse(raw)` in `UHFTAGInfo.toScannedTag()` and remove the obsolete reader-local parser.

- [ ] **Step 4: Verify GREEN**

Run: `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "com.example.koistock.device.RssiParserTest" --tests "com.example.koistock.ui.locate.LocateViewModelTest"`

Expected: all selected tests pass.

### Task 2: Enable BLE RSSI reporting and verify

**Files:**
- Modify: `app/src/main/java/com/example/koistock/device/ChainwayRfidReader.kt:122-130`

**Interfaces:**
- Consumes: successful `ConnectionStatus.CONNECTED` callback.
- Produces: RSSI-enabled SDK state before scan configuration.

- [ ] **Step 1: Enable RSSI after connection**

```kotlin
runCatching { sdk.setSupportRssi(true) }
scope.launch { applyScanConfig(ScanProfile()) }
```

- [ ] **Step 2: Compile and run the complete test suite**

Run: `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:compileDebugKotlin testDebugUnitTest`

Expected: `BUILD SUCCESSFUL` and all tests pass.

- [ ] **Step 3: Inspect the final diff**

Run: `git diff --check`

Expected: no whitespace errors and no unrelated file changes introduced by this implementation.

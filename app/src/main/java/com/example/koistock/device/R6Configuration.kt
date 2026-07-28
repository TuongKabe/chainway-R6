package com.example.koistock.device

enum class R6Region(val sdkCode: Int, val label: String, val bandLabel: String) {
    VIETNAM(0x35, "Việt Nam", "918–923 MHz"),
    USA(0x08, "USA", "902–928 MHz"),
    EUROPE(0x04, "Europe", "865–868 MHz");

    companion object {
        fun fromSdkCode(code: Int): R6Region? = entries.firstOrNull { it.sdkCode == code }
    }
}

data class R6DeviceConfig(val region: R6Region = R6Region.VIETNAM)

sealed interface Readback<out T> {
    data class Value<T>(val value: T) : Readback<T>
    data object Unsupported : Readback<Nothing>
    data class Failed(val message: String) : Readback<Nothing>
}

enum class ConfigField { POWER, REGION, SESSION, Q, MILLER, TAG_FOCUS, FAST_ID, READ_MODE }

data class ConfigCommandResult(
    val field: ConfigField,
    val success: Boolean,
    val message: String? = null,
)

data class R6ConfigSnapshot(
    val power: Readback<Int>,
    val region: Readback<R6Region>,
    val session: Readback<Int>,
    val q: Readback<Int>,
    val millerM: Readback<Int>,
    val tagFocus: Readback<Boolean> = Readback.Unsupported,
    val fastId: Readback<Boolean> = Readback.Unsupported,
)

data class ConfigMismatch(
    val field: ConfigField,
    val requested: String,
    val actual: String,
)

data class ConfigApplyResult(
    val requested: ScanProfile,
    val requestedRegion: R6Region,
    val commands: List<ConfigCommandResult>,
    val snapshot: R6ConfigSnapshot,
) {
    val mismatches: List<ConfigMismatch> get() = buildList {
        compare(ConfigField.POWER, requested.power, snapshot.power)
        compare(ConfigField.REGION, requestedRegion, snapshot.region)
        compare(ConfigField.SESSION, requested.session, snapshot.session)
        compare(ConfigField.Q, requested.q, snapshot.q)
        compare(ConfigField.MILLER, requested.millerM, snapshot.millerM)
        compare(ConfigField.TAG_FOCUS, requested.tagFocus, snapshot.tagFocus)
        compare(ConfigField.FAST_ID, requested.fastId, snapshot.fastId)
    }

    val isBlocking: Boolean get() =
        commands.any { !it.success && it.field in criticalFields } ||
            mismatches.any { it.field in criticalFields }

    private fun <T> MutableList<ConfigMismatch>.compare(
        field: ConfigField,
        expected: T,
        readback: Readback<T>,
    ) {
        val actual = (readback as? Readback.Value)?.value ?: return
        if (actual != expected) add(ConfigMismatch(field, expected.toString(), actual.toString()))
    }

    private companion object {
        val criticalFields = setOf(ConfigField.POWER, ConfigField.REGION, ConfigField.SESSION, ConfigField.Q)
    }
}

class VerifiedConfigGate {
    @Volatile
    var canScan: Boolean = true
        private set

    fun record(@Suppress("UNUSED_PARAMETER") result: ConfigApplyResult) {
        // Readback trên một số firmware R6 có cách mã hóa khác giá trị set (ví dụ 16 -> 15).
        // Kết quả vẫn dùng để cảnh báo trong Setting, nhưng không được vô hiệu hóa RFID.
        canScan = true
    }

    fun close() {
        canScan = false
    }

    fun allowPending() {
        canScan = true
    }
}

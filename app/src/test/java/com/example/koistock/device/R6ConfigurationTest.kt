package com.example.koistock.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R6ConfigurationTest {
    @Test
    fun vietnamRegionUsesChainwayCode0x35() {
        assertEquals(0x35, R6Region.VIETNAM.sdkCode)
        assertEquals("918–923 MHz", R6Region.VIETNAM.bandLabel)
    }

    @Test
    fun powerMismatchIsBlocking() {
        val requested = ScanProfile(power = 30)
        val result = ConfigApplyResult(
            requested = requested,
            requestedRegion = R6Region.VIETNAM,
            commands = listOf(ConfigCommandResult(ConfigField.POWER, success = true)),
            snapshot = matchingSnapshot(requested).copy(power = Readback.Value(26)),
        )

        assertTrue(result.isBlocking)
        assertEquals(ConfigField.POWER, result.mismatches.single().field)
    }

    @Test
    fun unsupportedOptionalFieldsDoNotBlock() {
        val requested = ScanProfile()
        val result = ConfigApplyResult(
            requested = requested,
            requestedRegion = R6Region.VIETNAM,
            commands = emptyList(),
            snapshot = matchingSnapshot(requested).copy(
                tagFocus = Readback.Unsupported,
                fastId = Readback.Unsupported,
            ),
        )

        assertFalse(result.isBlocking)
    }

    @Test
    fun failedCriticalReadbackWarnsButDoesNotBlockScanning() {
        val requested = ScanProfile()
        val result = ConfigApplyResult(
            requested = requested,
            requestedRegion = R6Region.VIETNAM,
            commands = emptyList(),
            snapshot = matchingSnapshot(requested).copy(
                region = Readback.Failed("Không đọc được region"),
            ),
        )

        assertFalse(result.isBlocking)
    }

    private fun matchingSnapshot(profile: ScanProfile) = R6ConfigSnapshot(
        power = Readback.Value(profile.power),
        region = Readback.Value(R6Region.VIETNAM),
        session = Readback.Value(profile.session),
        q = Readback.Value(profile.q),
        millerM = Readback.Value(profile.millerM),
        tagFocus = Readback.Value(profile.tagFocus),
        fastId = Readback.Value(profile.fastId),
    )
}

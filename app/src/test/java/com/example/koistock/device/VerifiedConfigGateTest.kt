package com.example.koistock.device

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifiedConfigGateTest {
    @Test fun allowsScanningWhileInitialConfigurationIsStillApplying() {
        assertTrue(VerifiedConfigGate().canScan)
    }

    @Test fun configurationMismatchNeverDisablesTagReading() {
        val gate = VerifiedConfigGate()
        gate.record(result(blocking = false))
        assertTrue(gate.canScan)
        gate.record(result(blocking = true))
        assertTrue(gate.canScan)
    }

    private fun result(blocking: Boolean): ConfigApplyResult {
        val profile = ScanProfile(power = 30)
        return ConfigApplyResult(
            requested = profile,
            requestedRegion = R6Region.VIETNAM,
            commands = if (blocking) listOf(ConfigCommandResult(ConfigField.POWER, false)) else emptyList(),
            snapshot = R6ConfigSnapshot(
                power = Readback.Value(profile.power),
                region = Readback.Value(R6Region.VIETNAM),
                session = Readback.Value(profile.session),
                q = Readback.Value(profile.q),
                millerM = Readback.Value(profile.millerM),
            ),
        )
    }
}

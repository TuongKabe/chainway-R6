package com.example.koistock.ui.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.koistock.device.FakeRfidReader
import com.example.koistock.device.R6Region
import com.example.koistock.device.Readback
import com.example.koistock.device.ScanProfileStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class R6SettingsViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun saveRegionWhenConnectedReadsBackAndMarksVerified() = runTest {
        val reader = FakeRfidReader()
        reader.connect("AA")
        val store = ScanProfileStore(
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { tmp.newFile("settings.preferences_pb") },
            ),
        )
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val vmScope = CoroutineScope(SupervisorJob() + dispatcher)
        val vm = R6SettingsViewModel(reader, store, vmScope, dispatcher)

        vm.saveRegion(R6Region.VIETNAM)
        val verified = withTimeout(2_000) {
            vm.state.first { it.regionStatus == VerificationStatus.VERIFIED }
        }

        assertEquals(VerificationStatus.VERIFIED, verified.regionStatus)
        assertEquals(R6Region.VIETNAM, (verified.snapshot?.region as Readback.Value<*>).value)
        vmScope.cancel()
    }
}

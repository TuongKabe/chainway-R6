package com.example.koistock.device

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ScanProfileStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun missingRegionDefaultsToVietnam() = runTest {
        val store = createStore(this)

        assertEquals(R6Region.VIETNAM, store.deviceConfig().first().region)
    }

    @Test
    fun oldProfileWithoutReadModeKeepsEpcDefault() = runTest {
        val store = createStore(this)

        assertEquals(ScanReadMode.EPC, store.profile(ScanFunction.LOCATE).first().readMode)
    }

    private fun createStore(scope: TestScope): ScanProfileStore {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope.backgroundScope,
            produceFile = { tmp.newFile("scan.preferences_pb") },
        )
        return ScanProfileStore(dataStore)
    }
}

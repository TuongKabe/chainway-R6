package com.example.koistock.data.remote

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.koistock.data.model.Product
import com.example.koistock.data.model.TagMapping
import com.example.koistock.data.model.TrackingMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class LocateCatalogStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun emptyStore_returnsNull() = runTest {
        assertNull(createStore(this, "empty").read())
    }

    @Test
    fun snapshot_roundTrips() = runTest {
        val store = createStore(this, "roundtrip")
        val snapshot = LocateCatalogSnapshot(
            items = listOf(
                LocatableProduct(
                    product = Product("SKU-A", "Koi A", "con", TrackingMode.SERIALIZED, 1),
                    activeTags = listOf(TagMapping("EPC-A", "SKU-A", syncRev = 7)),
                ),
            ),
            tagSignatureBySku = mapOf("SKU-A" to "signature"),
            metadataVerifiedAt = 123L,
        )

        store.write(snapshot)

        assertEquals(snapshot, store.read())
    }

    @Test
    fun malformedOrUnsupportedSnapshot_returnsNull() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { tmp.newFile("invalid.preferences_pb") },
        )
        val store = LocateCatalogStore(dataStore)
        val key = stringPreferencesKey("locate_catalog_snapshot_v1")
        dataStore.edit { it[key] = "not-json" }
        assertNull(store.read())

        dataStore.edit { it[key] = "{\"schemaVersion\":2,\"items\":[],\"tagSignatureBySku\":{},\"metadataVerifiedAt\":0}" }
        assertNull(store.read())
    }

    private fun createStore(scope: TestScope, name: String): LocateCatalogStore {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope.backgroundScope,
            produceFile = { tmp.newFile("$name.preferences_pb") },
        )
        return LocateCatalogStore(dataStore)
    }
}

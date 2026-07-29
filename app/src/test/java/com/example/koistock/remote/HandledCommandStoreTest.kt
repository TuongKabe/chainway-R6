package com.example.koistock.remote

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HandledCommandStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = HandledCommandStore(context)

    @Test
    fun `fresh command is not handled`() = runTest {
        assertFalse(store.isHandled("new-id"))
    }

    @Test
    fun `marked command is handled`() = runTest {
        store.markHandled("test-id")
        assertTrue(store.isHandled("test-id"))
    }

    @Test
    fun `different command is not handled after marking another`() = runTest {
        store.markHandled("id-1")
        assertFalse(store.isHandled("id-2"))
    }
}

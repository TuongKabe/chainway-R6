package com.example.koistock.ui.zones

import com.example.koistock.data.model.LocationNode
import com.example.koistock.data.model.LocationType
import com.example.koistock.fakes.FakeLocationRepo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ZoneViewModelTest {
    @Test
    fun addZone_savesZoneNode() = runTest {
        val repo = FakeLocationRepo()
        val vm = ZoneViewModel(repo, { 5 }, backgroundScope)
        assertTrue(vm.addZone("A", "Khu A") is ZoneAddResult.Ok)
        assertEquals(LocationType.ZONE, repo.items["A"]!!.type)
    }

    @Test
    fun addZone_duplicate_returnsError() = runTest {
        val repo = FakeLocationRepo(mutableMapOf("A" to LocationNode("A", "Khu A", LocationType.ZONE)))
        val vm = ZoneViewModel(repo, { 5 }, backgroundScope)
        assertTrue(vm.addZone("A", "x") is ZoneAddResult.Error)
    }

    @Test
    fun addShelf_withoutParent_returnsError() = runTest {
        val vm = ZoneViewModel(FakeLocationRepo(), { 5 }, backgroundScope)
        assertTrue(vm.addShelf("A", "A-03", "Kệ 3") is ZoneAddResult.Error)
    }

    @Test
    fun addShelf_withParent_savesShelfWithParent() = runTest {
        val repo = FakeLocationRepo(mutableMapOf("A" to LocationNode("A", "Khu A", LocationType.ZONE)))
        val vm = ZoneViewModel(repo, { 5 }, backgroundScope)
        assertTrue(vm.addShelf("A", "A-03", "Kệ 3") is ZoneAddResult.Ok)
        assertEquals("A", repo.items["A-03"]!!.parent)
        assertEquals(LocationType.SHELF, repo.items["A-03"]!!.type)
    }
}

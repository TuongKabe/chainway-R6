package com.example.koistock.ui.warehouse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.koistock.ui.zones.ZoneScreen
import com.example.koistock.ui.zones.ZoneViewModel

@Composable
fun WarehouseManagementScreen(
    productVm: ProductManagementViewModel,
    zoneVm: ZoneViewModel,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Sản phẩm") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Khu / kệ") })
        }
        when (selectedTab) {
            0 -> ProductManagementScreen(productVm)
            else -> ZoneScreen(zoneVm)
        }
    }
}

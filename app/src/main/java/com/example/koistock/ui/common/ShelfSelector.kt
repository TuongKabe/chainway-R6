package com.example.koistock.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.koistock.data.model.LocationNode
import com.example.koistock.data.model.LocationType

@Composable
fun ShelfSelector(
    locations: List<LocationNode>,
    selectedShelfCode: String?,
    enabled: Boolean = true,
    onShelfSelected: (String) -> Unit,
) {
    val zones = remember(locations) { locations.filter { it.type == LocationType.ZONE }.sortedBy { it.code } }
    val shelves = remember(locations) { locations.filter { it.type == LocationType.SHELF }.sortedBy { it.code } }
    val selectedShelf = shelves.firstOrNull { it.code == selectedShelfCode }
    var selectedZoneCode by rememberSaveable { mutableStateOf(selectedShelf?.parent) }
    var zoneMenu by remember { mutableStateOf(false) }
    var shelfMenu by remember { mutableStateOf(false) }

    LaunchedEffect(selectedShelfCode, locations) {
        selectedShelfCode?.let { code -> selectedZoneCode = shelves.firstOrNull { it.code == code }?.parent }
    }

    val availableShelves = shelves.filter { it.parent == selectedZoneCode }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { zoneMenu = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled && zones.isNotEmpty(),
            ) {
                val zone = zones.firstOrNull { it.code == selectedZoneCode }
                Text(zone?.let { "Khu ${it.code} · ${it.name}" } ?: "Chọn khu")
            }
            DropdownMenu(expanded = zoneMenu, onDismissRequest = { zoneMenu = false }) {
                zones.forEach { zone ->
                    DropdownMenuItem(
                        text = { Text("${zone.code} · ${zone.name}") },
                        onClick = {
                            selectedZoneCode = zone.code
                            zoneMenu = false
                            if (selectedShelf?.parent != zone.code) shelfMenu = true
                        },
                    )
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { shelfMenu = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled && selectedZoneCode != null && availableShelves.isNotEmpty(),
            ) {
                Text(selectedShelf?.takeIf { it.parent == selectedZoneCode }?.let { "Kệ ${it.code} · ${it.name}" } ?: "Chọn kệ")
            }
            DropdownMenu(expanded = shelfMenu, onDismissRequest = { shelfMenu = false }) {
                availableShelves.forEach { shelf ->
                    DropdownMenuItem(
                        text = { Text("${shelf.code} · ${shelf.name}") },
                        onClick = {
                            onShelfSelected(shelf.code)
                            shelfMenu = false
                        },
                    )
                }
            }
        }
    }
}

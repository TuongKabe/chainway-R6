package com.example.koistock.ui.zones

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.koistock.data.model.LocationNode
import com.example.koistock.data.model.LocationType
import kotlinx.coroutines.launch

@Composable
fun ZoneScreen(vm: ZoneViewModel) {
    val locations by vm.locations.collectAsState()
    val zones = locations.filter { it.type == LocationType.ZONE }.sortedBy { it.code }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var zoneCode by remember { mutableStateOf("") }
    var zoneName by remember { mutableStateOf("") }
    var shelfZone by remember { mutableStateOf<String?>(null) }
    var shelfCode by remember { mutableStateOf("") }
    var shelfName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<LocationNode?>(null) }

    editing?.let { item ->
        EditLocationDialog(
            item = item,
            zones = zones,
            onDismiss = { editing = null },
            onSave = { name, parent ->
                scope.launch {
                    val result = vm.updateLocation(item.code, name, parent)
                    message = result.messageFor(item.code)
                    if (result is ZoneAddResult.Ok) editing = null
                }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedTextField(
                query,
                { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Tìm mã hoặc tên khu/kệ") },
            )
        }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Thêm khu", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(zoneCode, { zoneCode = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Mã khu") })
                    OutlinedTextField(zoneName, { zoneName = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Tên khu") })
                    Button(
                        onClick = {
                            scope.launch {
                                val result = vm.addZone(zoneCode.trim(), zoneName.trim())
                                message = result.messageFor(zoneCode)
                                if (result is ZoneAddResult.Ok) {
                                    shelfZone = zoneCode.trim()
                                    zoneCode = ""
                                    zoneName = ""
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Lưu khu") }
                }
            }
        }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Thêm kệ", style = MaterialTheme.typography.titleMedium)
                    ZoneSelector(zones, shelfZone, onSelect = { shelfZone = it })
                    OutlinedTextField(shelfCode, { shelfCode = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Mã kệ") })
                    OutlinedTextField(shelfName, { shelfName = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Tên kệ") })
                    Button(
                        onClick = {
                            scope.launch {
                                val result = vm.addShelf(shelfZone.orEmpty(), shelfCode.trim(), shelfName.trim())
                                message = result.messageFor(shelfCode)
                                if (result is ZoneAddResult.Ok) {
                                    shelfCode = ""
                                    shelfName = ""
                                }
                            }
                        },
                        enabled = shelfZone != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Lưu kệ") }
                }
            }
        }
        message?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
        item { Text("Danh sách khu / kệ (${locations.size})", style = MaterialTheme.typography.titleMedium) }

        val visibleZones = zones.filter { zone ->
            query.isBlank() || zone.matches(query) || locations.any { it.parent == zone.code && it.matches(query) }
        }
        items(visibleZones, key = { it.code }) { zone ->
            val shelves = locations.filter {
                it.type == LocationType.SHELF && it.parent == zone.code && (query.isBlank() || zone.matches(query) || it.matches(query))
            }.sortedBy { it.code }
            Card(modifier = Modifier.fillMaxWidth(), onClick = { editing = zone }) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${zone.code} · ${zone.name}", fontWeight = FontWeight.SemiBold)
                    if (shelves.isEmpty()) {
                        Text("Chưa có kệ", style = MaterialTheme.typography.bodySmall)
                    } else {
                        shelves.forEach { shelf ->
                            OutlinedButton(onClick = { editing = shelf }, modifier = Modifier.fillMaxWidth()) {
                                Text("${shelf.code} · ${shelf.name}")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoneSelector(zones: List<LocationNode>, selected: String?, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), enabled = zones.isNotEmpty()) {
            val zone = zones.firstOrNull { it.code == selected }
            Text(zone?.let { "Khu cha: ${it.code} · ${it.name}" } ?: "Chọn khu cha")
        }
        DropdownMenu(expanded, { expanded = false }) {
            zones.forEach { zone ->
                DropdownMenuItem(
                    text = { Text("${zone.code} · ${zone.name}") },
                    onClick = { onSelect(zone.code); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun EditLocationDialog(
    item: LocationNode,
    zones: List<LocationNode>,
    onDismiss: () -> Unit,
    onSave: (String, String?) -> Unit,
) {
    var name by remember(item.code) { mutableStateOf(item.name) }
    var parent by remember(item.code) { mutableStateOf(item.parent) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item.type == LocationType.ZONE) "Sửa khu" else "Sửa kệ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(item.code, {}, readOnly = true, label = { Text("Mã (không đổi)") })
                OutlinedTextField(name, { name = it }, label = { Text("Tên") })
                if (item.type == LocationType.SHELF) ZoneSelector(zones, parent) { parent = it }
            }
        },
        confirmButton = { Button(onClick = { onSave(name, parent) }) { Text("Lưu") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } },
    )
}

private fun LocationNode.matches(query: String): Boolean =
    code.contains(query, ignoreCase = true) || name.contains(query, ignoreCase = true)

private fun ZoneAddResult.messageFor(code: String): String = when (this) {
    ZoneAddResult.Ok -> "Đã lưu $code"
    is ZoneAddResult.SavedButSyncFailed -> "Đã lưu $code nhưng chưa đồng bộ: $msg"
    is ZoneAddResult.Error -> msg
}

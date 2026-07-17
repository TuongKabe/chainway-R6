package com.example.koistock.ui.zones

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.koistock.data.model.LocationType
import kotlinx.coroutines.launch

@Composable
fun ZoneScreen(vm: ZoneViewModel) {
    val locations by vm.locations.collectAsState()
    val scope = rememberCoroutineScope()
    var zoneCode by remember { mutableStateOf("") }
    var zoneName by remember { mutableStateOf("") }
    var shelfZone by remember { mutableStateOf("") }
    var shelfCode by remember { mutableStateOf("") }
    var shelfName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    val sorted = remember(locations) {
        locations.sortedWith(compareBy({ it.parent ?: "" }, { it.code }))
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Thêm khu", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(zoneCode, { zoneCode = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Mã khu") })
                    OutlinedTextField(zoneName, { zoneName = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Tên khu") })
                    Button(
                        onClick = {
                            scope.launch {
                                when (val result = vm.addZone(zoneCode.trim(), zoneName.trim())) {
                                    ZoneAddResult.Ok -> {
                                        message = "Đã thêm khu $zoneCode"
                                        isError = false
                                        zoneCode = ""
                                        zoneName = ""
                                    }
                                    is ZoneAddResult.Error -> {
                                        message = result.msg
                                        isError = true
                                    }
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
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Thêm kệ", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(shelfZone, { shelfZone = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Khu cha") })
                    OutlinedTextField(shelfCode, { shelfCode = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Mã kệ") })
                    OutlinedTextField(shelfName, { shelfName = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Tên kệ") })
                    Button(
                        onClick = {
                            scope.launch {
                                when (val result = vm.addShelf(shelfZone.trim(), shelfCode.trim(), shelfName.trim())) {
                                    ZoneAddResult.Ok -> {
                                        message = "Đã thêm kệ $shelfCode"
                                        isError = false
                                        shelfCode = ""
                                        shelfName = ""
                                    }
                                    is ZoneAddResult.Error -> {
                                        message = result.msg
                                        isError = true
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Lưu kệ") }
                }
            }
        }

        message?.let { msg ->
            item {
                Text(
                    msg,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }

        item {
            Text("Danh sách khu / kệ (${locations.size})", style = MaterialTheme.typography.titleMedium)
        }

        items(sorted, key = { it.code }) { item ->
            Card {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(item.code, style = MaterialTheme.typography.titleMedium)
                        Text(item.name)
                    }
                    Text(if (item.type == LocationType.ZONE) "ZONE" else "SHELF")
                }
            }
        }
    }
}

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.koistock.data.model.LocationType
import com.example.koistock.ui.common.FeatureGuideButton
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

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Quản lý khu vực", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            FeatureGuideButton(
                title = "Hướng dẫn quản lý khu vực",
                quickSteps = listOf(
                    "Tạo khu trước.",
                    "Sau đó tạo kệ và gắn vào đúng khu cha.",
                    "Kiểm tra danh sách bên dưới để xác nhận mã không trùng.",
                ),
                notes = listOf(
                    "Mã khu và mã kệ nên ngắn, nhất quán để đội kho dễ dùng.",
                    "Kệ không thể tạo nếu chưa có khu cha.",
                ),
            )
        }
        Text("Thêm khu/kệ để đội kho có cấu trúc vị trí rõ ràng.")

        Card {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Thêm khu", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(zoneCode, { zoneCode = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Mã khu") })
                OutlinedTextField(zoneName, { zoneName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Tên khu") })
                Button(onClick = {
                    scope.launch {
                        message = when (val result = vm.addZone(zoneCode.trim(), zoneName.trim())) {
                            ZoneAddResult.Ok -> "Đã thêm khu $zoneCode"
                            is ZoneAddResult.Error -> result.msg
                        }
                    }
                }) { Text("Lưu khu") }
            }
        }

        Card {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Thêm kệ", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(shelfZone, { shelfZone = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Khu cha") })
                OutlinedTextField(shelfCode, { shelfCode = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Mã kệ") })
                OutlinedTextField(shelfName, { shelfName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Tên kệ") })
                Button(onClick = {
                    scope.launch {
                        message = when (val result = vm.addShelf(shelfZone.trim(), shelfCode.trim(), shelfName.trim())) {
                            ZoneAddResult.Ok -> "Đã thêm kệ $shelfCode"
                            is ZoneAddResult.Error -> result.msg
                        }
                    }
                }) { Text("Lưu kệ") }
            }
        }

        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(locations.sortedWith(compareBy({ it.parent ?: "" }, { it.code }))) { item ->
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
}

package com.example.koistock.ui.hardware

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.koistock.device.ConnectionState
import com.example.koistock.ui.common.FeatureGuideButton

@Composable
fun HardwareTestScreen(vm: HardwareTestViewModel) {
    val connectionState by vm.connectionState.collectAsState()
    val battery by vm.battery.collectAsState()
    val singleScan by vm.singleScan.collectAsState()
    val inventoryTags by vm.inventoryTags.collectAsState()
    val locateSignal by vm.locateSignal.collectAsState()
    val writeResult by vm.writeResult.collectAsState()
    val triggerPressed by vm.triggerPressed.collectAsState()
    val lastSeenEpc by vm.lastSeenEpc.collectAsState()
    val logs by vm.logs.collectAsState()

    var locateEpc by remember { mutableStateOf("") }
    var oldEpc by remember { mutableStateOf("") }
    var newEpc by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Kiểm tra phần cứng R6", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            FeatureGuideButton(
                title = "Hướng dẫn test phần cứng",
                quickSteps = listOf(
                    "Nhấn Đọc pin để kiểm tra pin reader.",
                    "Nhấn Quét 1 thẻ để thử single scan.",
                    "Nhấn Bắt đầu inventory rồi đi qua vài tag để kiểm tra quét hàng loạt.",
                    "Nhập EPC cần tìm hoặc EPC cũ/mới để test locate và write.",
                ),
                notes = listOf(
                    "Chỉ test write EPC với tag thử nghiệm, không dùng tag đang chạy thật.",
                    "Sau khi inventory xong nhớ bấm Dừng inventory.",
                ),
            )
        }

        Card {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Bảng debug", style = MaterialTheme.typography.titleMedium)
                Text("Kết nối: ${connectionLabel(connectionState)}")
                Text("Trigger: ${if (triggerPressed) "Đang nhấn" else "Đã nhả"}")
                Text("EPC cuối: ${lastSeenEpc ?: "Chưa có"}")
            }
        }

        Card {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. Pin và quét đơn", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::refreshBattery) { Text("Đọc pin") }
                    Button(onClick = vm::scanSingle) { Text("Quét 1 thẻ") }
                }
                Text("Pin: ${battery?.let { "$it%" } ?: "Chưa đọc"}")
                Text("EPC đơn: ${singleScan ?: "Chưa có"}")
            }
        }

        Card {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("2. Inventory", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::startInventory) { Text("Bắt đầu inventory") }
                    Button(onClick = vm::stopInventory) { Text("Dừng inventory") }
                }
                Text("Số EPC đã gom: ${inventoryTags.size}")
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    items(inventoryTags) { epc ->
                        Text("• $epc")
                    }
                }
            }
        }

        Card {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("3. Locate", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = locateEpc,
                    onValueChange = { locateEpc = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("EPC cần tìm") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.startLocate(locateEpc) }, enabled = locateEpc.isNotBlank()) { Text("Bắt đầu locate") }
                    Button(onClick = vm::stopLocate) { Text("Dừng locate") }
                }
                Text("Tín hiệu locate: $locateSignal / 100")
            }
        }

        Card {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("4. Ghi EPC", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = oldEpc,
                    onValueChange = { oldEpc = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("EPC hiện tại") },
                )
                OutlinedTextField(
                    value = newEpc,
                    onValueChange = { newEpc = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("EPC mới") },
                )
                Button(
                    onClick = { vm.writeEpc(oldEpc, newEpc) },
                    enabled = oldEpc.isNotBlank() && newEpc.isNotBlank(),
                ) {
                    Text("Ghi EPC")
                }
                writeResult?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
        }

        Card {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Nhật ký sự kiện", style = MaterialTheme.typography.titleMedium)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    items(logs) { line ->
                        Text("• $line")
                    }
                }
            }
        }
    }
}

private fun connectionLabel(state: ConnectionState): String = when (state) {
    is ConnectionState.Connected -> "Đã kết nối ${state.mac}"
    is ConnectionState.Connecting -> "Đang kết nối ${state.mac}"
    ConnectionState.Scanning -> "Đang tìm thiết bị"
    ConnectionState.Disconnected -> "Chưa kết nối"
}

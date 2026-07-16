package com.example.koistock.ui.hardware

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HardwareTestScreen(vm: HardwareTestViewModel) {
    val connectionState by vm.connectionState.collectAsState()
    val battery by vm.battery.collectAsState()
    val singleScan by vm.singleScan.collectAsState()
    val inventoryTags by vm.inventoryTags.collectAsState()
    val locateSignal by vm.locateSignal.collectAsState()
    val writeResult by vm.writeResult.collectAsState()
    val triggerPressed by vm.triggerPressed.collectAsState()
    val triggerHeld by vm.triggerHeld.collectAsState()
    val triggerCount by vm.triggerCount.collectAsState()
    val lastTriggerAt by vm.lastTriggerAt.collectAsState()
    val lastTriggerHoldMs by vm.lastTriggerHoldMs.collectAsState()
    val triggerTestOnly by vm.triggerTestOnly.collectAsState()
    val triggerHistory by vm.triggerHistory.collectAsState()
    val rawSdkLogs by vm.rawSdkLogs.collectAsState()
    val lastSeenEpc by vm.lastSeenEpc.collectAsState()
    val logs by vm.logs.collectAsState()

    var locateEpc by remember { mutableStateOf("") }
    var oldEpc by remember { mutableStateOf("") }
    var newEpc by remember { mutableStateOf("") }

    DisposableEffect(vm) {
        onDispose { vm.clear() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Kiểm tra phần cứng R6",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            FeatureGuideButton(
                title = "Hướng dẫn test phần cứng",
                quickSteps = listOf(
                    "Bật Trigger test only để đo thời gian bóp cò thật mà không kích quét.",
                    "Theo rule mới: dưới 1000ms là Bấm, từ 1000ms trở lên là Giữ.",
                    "Xem bảng time trigger để đối chiếu từng lần bóp cò trên máy.",
                    "Tắt Trigger test only khi muốn dùng cò để quét thực tế.",
                ),
                notes = listOf(
                    "Bảng time trigger lưu 12 lần bóp cò gần nhất.",
                    "Khi Trigger test only đang bật, cò trên máy sẽ không tự quét tag.",
                ),
            )
        }

        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Bảng debug", style = MaterialTheme.typography.titleMedium)
                Text("Kết nối: ${connectionLabel(connectionState)}")
                Text("Trigger: ${if (triggerPressed) "Đang nhấn" else "Đã nhả"}")
                Text("Giữ cò: ${if (triggerHeld) "Đang giữ" else "Chưa giữ"}")
                Text("Số nhịp trigger: $triggerCount")
                Text("Lần nhấn gần nhất: ${formatTimestamp(lastTriggerAt)}")
                Text("Thời gian cò gần nhất: ${formatHold(lastTriggerHoldMs)}")
                Text("Phân loại gần nhất: ${classify(lastTriggerHoldMs)}")
                Text("Chế độ cò: ${if (triggerTestOnly) "Trigger test only" else "Cò điều khiển quét"}")
                Text("EPC cuối: ${lastSeenEpc ?: "Chưa có"}")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Trigger test only", fontWeight = FontWeight.Medium)
                        Text(
                            "Bật để kiểm tra DOWN/UP và đo thời gian bóp cò, không chạy quét bằng cò.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = triggerTestOnly,
                        onCheckedChange = vm::setTriggerTestOnly,
                    )
                }
            }
        }

        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Bảng time trigger", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Rule: dưới 1000ms = Bấm, từ 1000ms trở lên = Giữ",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("Bắt đầu", fontWeight = FontWeight.Bold)
                    Text("Kết thúc", fontWeight = FontWeight.Bold)
                    Text("Thời gian", fontWeight = FontWeight.Bold)
                    Text("Loại", fontWeight = FontWeight.Bold)
                }
                triggerHistory.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(formatTimestamp(row.pressedAt))
                        Text(formatTimestamp(row.releasedAt))
                        Text("${row.durationMs}ms")
                        Text(row.kindLabel)
                    }
                }
                if (triggerHistory.isEmpty()) {
                    Text("Chưa có lần bóp cò nào được ghi nhận.")
                }
            }
        }

        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Raw SDK trigger", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Dùng bảng này để xem SDK thật sự báo DOWN hay UP khi anh bóp/thả cò.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (rawSdkLogs.isEmpty()) {
                    Text("Chưa có raw callback từ SDK.")
                } else {
                    rawSdkLogs.forEach { line ->
                        Text("• $line")
                    }
                }
            }
        }

        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("2. Inventory", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::startInventory) { Text("Bắt đầu inventory") }
                    Button(onClick = vm::stopInventory) { Text("Dừng inventory") }
                }
                Text("Số EPC đã gom: ${inventoryTags.size}")
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    inventoryTags.forEach { epc ->
                        Text("• $epc")
                    }
                }
            }
        }

        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("3. Locate", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = locateEpc,
                    onValueChange = { locateEpc = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("EPC cần tìm") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.startLocate(locateEpc) },
                        enabled = locateEpc.isNotBlank(),
                    ) {
                        Text("Bắt đầu locate")
                    }
                    Button(onClick = vm::stopLocate) { Text("Dừng locate") }
                }
                Text("Tín hiệu locate: $locateSignal / 100")
            }
        }

        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Nhật ký sự kiện", style = MaterialTheme.typography.titleMedium)
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    logs.forEach { line ->
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

private fun formatTimestamp(value: Long?): String {
    if (value == null) return "Chưa có"
    return SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(value))
}

private fun formatHold(value: Long?): String {
    if (value == null) return "Chưa có"
    return "${value}ms"
}

private fun classify(value: Long?): String {
    if (value == null) return "Chưa có"
    return if (value < 1_000L) "Bấm" else "Giữ"
}

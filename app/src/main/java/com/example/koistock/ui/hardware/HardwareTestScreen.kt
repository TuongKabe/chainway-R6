package com.example.koistock.ui.hardware

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalClipboardManager
import com.example.koistock.device.ConnectionState
import com.example.koistock.ui.theme.Ash
import com.example.koistock.ui.theme.ElectricBlue
import com.example.koistock.ui.theme.PaperMist
import com.example.koistock.ui.theme.SoftMint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HardwareTestScreen(vm: HardwareTestViewModel) {
    val clipboard = LocalClipboardManager.current
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
    var copyFeedback by remember { mutableStateOf<String?>(null) }

    DisposableEffect(vm) {
        onDispose { vm.clear() }
    }

    fun copyText(label: String, value: String) {
        clipboard.setText(AnnotatedString(value))
        copyFeedback = "Đã copy $label"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, Ash),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Đọc nhanh trạng thái reader, EPC vừa quét và log trigger để test ngoài hiện trường.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    copyFeedback ?: "Sẵn sàng test reader.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        HardwareCard(title = "Tổng quan") {
            HardwareField("Kết nối", connectionLabel(connectionState))
            HardwareField("Pin", battery?.let { "$it%" } ?: "Chưa đọc")
            HardwareField("Trigger", if (triggerPressed) "Đang nhấn" else "Đã nhả")
            HardwareField("Giữ cò", if (triggerHeld) "Đang giữ" else "Chưa giữ")
            HardwareField("Số nhịp trigger", triggerCount.toString())
            HardwareField("Lần nhấn gần nhất", formatTimestamp(lastTriggerAt))
            HardwareField("Thời gian cò gần nhất", formatHold(lastTriggerHoldMs))
            HardwareField("Phân loại", classify(lastTriggerHoldMs))
            HardwareField("EPC cuối", lastSeenEpc ?: "Chưa có")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Trigger test only", fontWeight = FontWeight.Medium)
                    Text(
                        "Bật để chỉ đo DOWN/UP và thời gian bóp cò.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = triggerTestOnly,
                    onCheckedChange = vm::setTriggerTestOnly,
                )
            }
        }

        HardwareCard(title = "Quét đơn") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::refreshBattery) { Text("Đọc pin") }
                Button(onClick = vm::scanSingle) { Text("Quét 1 thẻ") }
            }
            HardwareField("EPC đơn", singleScan ?: "Chưa có")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { singleScan?.let { copyText("EPC đơn", it) } },
                    enabled = !singleScan.isNullOrBlank(),
                ) { Text("Copy EPC đơn") }
                OutlinedButton(
                    onClick = { lastSeenEpc?.let { copyText("EPC cuối", it) } },
                    enabled = !lastSeenEpc.isNullOrBlank(),
                ) { Text("Copy EPC cuối") }
            }
        }

        HardwareCard(title = "Inventory") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::startInventory) { Text("Bắt đầu") }
                OutlinedButton(onClick = vm::stopInventory) { Text("Dừng") }
                OutlinedButton(
                    onClick = { copyText("danh sách EPC", inventoryTags.joinToString("\n")) },
                    enabled = inventoryTags.isNotEmpty(),
                ) { Text("Copy EPC") }
            }
            HardwareField("Số EPC đã gom", inventoryTags.size.toString())
            if (inventoryTags.isEmpty()) {
                Text("Chưa có EPC nào từ inventory.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    inventoryTags.forEachIndexed { index, epc ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(PaperMist, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                        ) {
                            Text(
                                text = "${index + 1}. $epc",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }

        HardwareCard(title = "Locate") {
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
                ) { Text("Bắt đầu locate") }
                OutlinedButton(onClick = vm::stopLocate) { Text("Dừng") }
                OutlinedButton(
                    onClick = { lastSeenEpc?.let { locateEpc = it } },
                    enabled = !lastSeenEpc.isNullOrBlank(),
                ) { Text("Dùng EPC cuối") }
            }
            HardwareField("Tín hiệu locate", "$locateSignal / 100")
        }

        HardwareCard(title = "Ghi EPC") {
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

        HardwareCard(title = "Bảng time trigger") {
            Text(
                "Rule: dưới 1000ms = Bấm, từ 1000ms trở lên = Giữ",
                style = MaterialTheme.typography.bodySmall,
            )
            if (triggerHistory.isEmpty()) {
                Text("Chưa có lần bóp cò nào được ghi nhận.")
            } else {
                triggerHistory.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .background(PaperMist, RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(formatTimestamp(row.pressedAt))
                        Text(formatTimestamp(row.releasedAt))
                        Text("${row.durationMs}ms")
                        Text(row.kindLabel)
                    }
                }
            }
        }

        HardwareCard(title = "Raw SDK trigger") {
            if (rawSdkLogs.isEmpty()) {
                Text("Chưa có raw callback từ SDK.")
            } else {
                rawSdkLogs.forEach { line ->
                    Text("• $line", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        HardwareCard(title = "Nhật ký sự kiện") {
            if (logs.isEmpty()) {
                Text("Chưa có log.")
            } else {
                logs.forEach { line ->
                    Text("• $line", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun HardwareCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Ash),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                content()
            },
        )
    }
}

@Composable
private fun HardwareField(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PaperMist, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge)
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

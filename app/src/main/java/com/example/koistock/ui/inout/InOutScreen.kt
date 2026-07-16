package com.example.koistock.ui.inout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.koistock.data.remote.CommitStockResult
import com.example.koistock.ui.common.FeatureGuideButton
import kotlinx.coroutines.launch

@Composable
fun InOutScreen(vm: InOutViewModel) {
    val mode by vm.mode.collectAsState()
    val pending by vm.pending.collectAsState()
    val commitState by vm.commitState.collectAsState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Nhập / Xuất kho", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            FeatureGuideButton(
                title = "Hướng dẫn nhập / xuất",
                quickSteps = listOf(
                    "Chọn chế độ IN hoặc OUT.",
                    "Bấm Bắt đầu quét để gom EPC vào lệnh.",
                    "Kiểm tra danh sách pending rồi bấm Xác nhận.",
                ),
                notes = listOf(
                    "Mỗi EPC chỉ được tính một lần trong một lệnh.",
                    "Phiên bản hiện tại đang dùng stock command demo để review luồng UI.",
                ),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.setMode(InOutMode.IN) }) { Text(if (mode == InOutMode.IN) "✓ IN" else "IN") }
            Button(onClick = { vm.setMode(InOutMode.OUT) }) { Text(if (mode == InOutMode.OUT) "✓ OUT" else "OUT") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::startScan) { Text("Bắt đầu quét") }
            Button(onClick = vm::stopScan) { Text("Dừng") }
            Button(onClick = { scope.launch { vm.confirm() } }, enabled = pending.isNotEmpty()) { Text("Xác nhận") }
        }
        Card {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Danh sách chờ", style = MaterialTheme.typography.titleMedium)
                if (pending.isEmpty()) {
                    Text("Chưa có SKU nào trong lệnh.")
                } else {
                    pending.forEach { (sku, qty) ->
                        Text("$sku • $qty")
                    }
                }
            }
        }
        commitState?.let { state ->
            val message = when (state) {
                is CommitStockResult.Success -> "Xác nhận thành công: ${state.commandId}"
                is CommitStockResult.InsufficientStock -> "Thiếu tồn ${state.sku}: có ${state.available}, cần ${state.requested}"
                is CommitStockResult.InvalidTag -> "Tag lỗi ${state.epc}: ${state.reason}"
            }
            Text(message, color = MaterialTheme.colorScheme.primary)
        }
    }
}

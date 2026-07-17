package com.example.koistock.ui.inout

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.koistock.data.remote.CommitStockResult
import com.example.koistock.ui.common.ScanTriggerDialog
import kotlinx.coroutines.launch

@Composable
fun InOutScreen(vm: InOutViewModel) {
    val mode by vm.mode.collectAsState()
    val pending by vm.pending.collectAsState()
    val commitState by vm.commitState.collectAsState()
    val isScanning by vm.isScanning.collectAsState()
    val scope = rememberCoroutineScope()
    var showScanDialog by remember { mutableStateOf(false) }

    DisposableEffect(vm) {
        onDispose { vm.clear() }
    }

    if (showScanDialog) {
        ScanTriggerDialog(
            isScanning = isScanning,
            statusLine = "Đã quét: ${pending.size} SKU / ${pending.values.sum()} tag",
            onStop = vm::stopScan,
            onClose = { showScanDialog = false },
            title = if (mode == InOutMode.IN) "Quét nhập kho" else "Quét xuất kho",
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.setMode(InOutMode.IN) }) { Text(if (mode == InOutMode.IN) "✓ IN" else "IN") }
            Button(onClick = { vm.setMode(InOutMode.OUT) }) { Text(if (mode == InOutMode.OUT) "✓ OUT" else "OUT") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showScanDialog = true }, modifier = Modifier.weight(1f)) { Text("Bắt đầu quét") }
            Button(
                onClick = { scope.launch { vm.confirm() } },
                enabled = pending.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) { Text("Xác nhận") }
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

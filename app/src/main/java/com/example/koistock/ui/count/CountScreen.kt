package com.example.koistock.ui.count

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.koistock.domain.ExpectedItem
import com.example.koistock.ui.common.ScanTriggerDialog
import com.example.koistock.util.shareCsv
import kotlinx.coroutines.launch

@Composable
fun CountScreen(
    vm: CountViewModel,
    expectedItems: List<ExpectedItem>,
) {
    val zone by vm.zone.collectAsState()
    val counted by vm.countedBySku.collectAsState()
    val rows by vm.rows.collectAsState()
    val isScanning by vm.isScanning.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var zoneInput by remember(zone) { mutableStateOf(zone.orEmpty()) }
    var showScanDialog by remember { mutableStateOf(false) }

    DisposableEffect(vm) {
        onDispose { vm.clear() }
    }

    if (showScanDialog) {
        ScanTriggerDialog(
            isScanning = isScanning,
            statusLine = "Đã quét: ${counted.size} SKU / ${counted.values.sum()} tag",
            onStop = vm::stopScan,
            onClose = { showScanDialog = false },
            title = "Quét theo khu",
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Chọn khu, quét EPC, đối chiếu chênh lệch và xuất CSV.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = zoneInput,
            onValueChange = {
                zoneInput = it
                vm.setZone(it)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Mã khu/kệ") },
            placeholder = { Text("Ví dụ: A-03") },
        )
        Button(
            onClick = { showScanDialog = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Bắt đầu quét")
        }

        Card {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("SKU đã đếm", style = MaterialTheme.typography.titleMedium)
                if (counted.isEmpty()) {
                    Text("Chưa có EPC nào được gom.")
                } else {
                    counted.forEach { (sku, qty) ->
                        Text("$sku • $qty tag")
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { scope.launch { vm.reconcile(expectedItems.filter { it.homeLocation == zoneInput || it.homeLocation.startsWith(zoneInput.substringBefore('-')) }) } }) {
                Text("Đối chiếu")
            }
            Button(onClick = { scope.launch { vm.saveCount() } }, enabled = counted.isNotEmpty()) {
                Text("Lưu count")
            }
            Button(
                onClick = { shareCsv(context, vm.csv(), "kiemke-${zoneInput.ifBlank { "draft" }}.csv") },
                enabled = rows.isNotEmpty(),
            ) {
                Text("Xuất CSV")
            }
        }

        Card {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Kết quả đối chiếu", style = MaterialTheme.typography.titleMedium)
                if (rows.isEmpty()) {
                    Text("Chưa chạy đối chiếu.")
                } else {
                    rows.forEach { row ->
                        Text("${row.sku} • ${row.name} • Đếm ${row.counted}/${row.expected} • ${row.status}")
                    }
                }
            }
        }
    }
}

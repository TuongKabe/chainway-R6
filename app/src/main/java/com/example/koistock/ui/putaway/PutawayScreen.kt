package com.example.koistock.ui.putaway

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import com.example.koistock.ui.common.ScanTriggerDialog
import kotlinx.coroutines.launch

@Composable
fun PutawayScreen(vm: PutawayViewModel) {
    val locationCode by vm.locationCode.collectAsState()
    val scanned by vm.scanned.collectAsState()
    val isScanning by vm.isScanning.collectAsState()
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<Int?>(null) }
    var locationInput by remember(locationCode) { mutableStateOf(locationCode.orEmpty()) }
    var showScanDialog by remember { mutableStateOf(false) }

    DisposableEffect(vm) {
        onDispose { vm.clear() }
    }

    if (showScanDialog) {
        ScanTriggerDialog(
            isScanning = isScanning,
            statusLine = "Đã quét: ${scanned.size} EPC",
            onStop = vm::stopCollect,
            onClose = { showScanDialog = false },
            title = "Quét EPC cần đặt vị trí",
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = locationInput,
            onValueChange = {
                locationInput = it
                vm.setLocationByTag(it.trim())
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Khu/kệ đích") },
            placeholder = { Text("Ví dụ: A-03") },
        )
        Button(
            onClick = {
                result = null
                showScanDialog = true
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = locationInput.isNotBlank(),
        ) {
            Text("Bắt đầu quét")
        }
        Text("Đã quét: ${scanned.size} EPC")
        Button(
            onClick = {
                scope.launch { result = vm.applyAssign() }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = scanned.isNotEmpty(),
        ) {
            Text(result?.let { "Đã gán $it món vào ${locationInput} ✓" } ?: "Gán vào vị trí")
        }
    }
}

package com.example.koistock.ui.putaway

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.koistock.ui.common.FeatureGuideButton
import kotlinx.coroutines.launch

@Composable
fun PutawayScreen(vm: PutawayViewModel) {
    val locationCode by vm.locationCode.collectAsState()
    val scanned by vm.scanned.collectAsState()
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<Int?>(null) }

    DisposableEffect(vm) {
        onDispose { vm.clear() }
    }

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
            Text("Đặt vị trí", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            FeatureGuideButton(
                title = "Hướng dẫn đặt vị trí",
                quickSteps = listOf(
                    "Xác nhận mã khu/kệ đích.",
                    "Bấm Bắt đầu quét và gom các EPC cần chuyển.",
                    "Bấm Gán vào vị trí để cập nhật vị trí mới.",
                ),
                notes = listOf(
                    "Hàng serialized sẽ cập nhật location trên tag mapping.",
                    "Hàng bulk hiện ghi transaction MOVE để giữ audit log.",
                ),
            )
        }
        Text("Khu/kệ: ${locationCode ?: "A-03 (demo)"}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                vm.setLocationByTag("A-03")
                vm.startCollect()
            }) {
                Text("Bắt đầu quét")
            }
            OutlinedButton(onClick = vm::stopCollect) {
                Text("Dừng")
            }
        }
        Text("Đã quét: ${scanned.size} EPC")
        Button(
            onClick = {
                scope.launch { result = vm.applyAssign() }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = scanned.isNotEmpty(),
        ) {
            Text(result?.let { "Đã gán $it món ✓" } ?: "Gán vào vị trí")
        }
    }
}

package com.example.koistock.ui.lookup

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.koistock.ui.common.FeatureGuideButton

@Composable
fun LookupScreen(
    vm: LookupViewModel,
    onAssign: (String) -> Unit,
) {
    val result by vm.result.collectAsState()

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
            Text(
                text = "Tra cứu hàng hóa",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            FeatureGuideButton(
                title = "Hướng dẫn tra cứu",
                quickSteps = listOf(
                    "Nhấn Quét thẻ để đọc một EPC.",
                    "Nếu tag đã map, app sẽ hiện thông tin SKU và vị trí.",
                    "Nếu tag chưa map, chuyển sang màn Gán tag.",
                ),
                notes = listOf(
                    "Giữ tag gần đầu đọc để tăng ổn định.",
                    "Tra cứu hiện cần dữ liệu SKU/tag đã có trong hệ thống.",
                ),
            )
        }
        Text(
            text = "Quét một thẻ RFID để xem thông tin sản phẩm, tồn kho và vị trí hiện tại.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = vm::scanOnce,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Quét thẻ")
        }

        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (val state = result) {
                    LookupResult.Idle -> {
                        Text("Sẵn sàng quét", style = MaterialTheme.typography.titleMedium)
                        Text("Chưa có kết quả nào. Bấm Quét thẻ để bắt đầu.")
                    }

                    LookupResult.NotFound -> {
                        Text("Không đọc được tag", style = MaterialTheme.typography.titleMedium)
                        Text("Kiểm tra kết nối R6 hoặc đưa tag lại gần đầu đọc hơn.")
                    }

                    is LookupResult.UnknownTag -> {
                        Text("Tag chưa được map", style = MaterialTheme.typography.titleMedium)
                        Text("EPC: ${state.epc}")
                        Button(onClick = { onAssign(state.epc) }) {
                            Text("Gán tag này")
                        }
                    }

                    is LookupResult.Found -> {
                        Text(state.product.name, style = MaterialTheme.typography.titleLarge)
                        Text("SKU: ${state.product.sku}")
                        Text("Đơn vị: ${state.product.unit}")
                        Text("Tồn: ${state.product.quantity}")
                        Text("Vị trí mặc định: ${state.product.locationCode}")
                        Text("EPC: ${state.tag.epc}")
                    }
                }
            }
        }
    }
}

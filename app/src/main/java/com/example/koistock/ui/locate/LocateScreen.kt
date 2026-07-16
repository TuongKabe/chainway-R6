package com.example.koistock.ui.locate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
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
fun LocateScreen(
    vm: LocateViewModel,
    sku: String,
) {
    val signal by vm.signal.collectAsState()
    val interval by vm.intervalMs.collectAsState()

    DisposableEffect(sku) {
        vm.startForSku(sku)
        onDispose { vm.stop() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Tìm sản phẩm", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            FeatureGuideButton(
                title = "Hướng dẫn tìm sản phẩm",
                quickSteps = listOf(
                    "Chọn đúng SKU hoặc EPC cần tìm.",
                    "Di chuyển cùng đầu đọc tới khu vực nghi ngờ.",
                    "Theo dõi thanh tín hiệu, tín hiệu càng cao thì càng gần tag mục tiêu.",
                ),
                notes = listOf(
                    "Nếu tín hiệu không đổi, kiểm tra lại EPC mục tiêu.",
                    "Tránh đứng quá gần nhiều tag giống nhau khi định vị.",
                ),
            )
        }
        Text("SKU đang dò: $sku")
        LinearProgressIndicator(
            progress = { signal / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
        )
        Text("Tín hiệu: $signal / 100")
        Text("Nhịp beep theo khoảng cách: $interval ms")
        Text(
            "Reader thật sẽ beep chậm khi còn xa và nhanh dần khi lại gần thẻ mục tiêu.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

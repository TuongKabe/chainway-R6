package com.example.koistock.ui.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ConnectionGuideScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Hướng dẫn kết nối Chainway R6",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )

        GuideSection(
            title = "1. Chuẩn bị",
            lines = listOf(
                "Bật Bluetooth trên điện thoại Android và đầu đọc R6.",
                "Đảm bảo R6 đã sạc đủ pin và đang ở chế độ BLE.",
                "Nếu máy Android từ API 31 trở lên, cấp quyền Bluetooth Scan và Bluetooth Connect.",
                "Nếu máy Android API 30 trở xuống, cấp thêm quyền vị trí để quét BLE.",
            ),
        )

        GuideSection(
            title = "2. Ghép nối trong app",
            lines = listOf(
                "Mở màn Kết nối R6.",
                "Bấm Tìm thiết bị để app quét các đầu đọc BLE xung quanh.",
                "Chọn đúng R6 theo tên hoặc địa chỉ MAC.",
                "Sau khi kết nối thành công, app sẽ lưu MAC gần nhất để tự kết nối lại.",
            ),
        )

        GuideSection(
            title = "3. Sau khi kết nối xong",
            lines = listOf(
                "Tra cứu: quét một thẻ để xem SKU và thông tin hàng hóa.",
                "Tìm sản phẩm: định vị tag mục tiêu theo tín hiệu.",
                "Kiểm kê / Nhập xuất / Đặt vị trí: dùng inventory để gom EPC hàng loạt.",
            ),
        )

        GuideSection(
            title = "4. Xử lý nhanh khi gặp lỗi",
            lines = listOf(
                "Không thấy R6: kiểm tra R6 có đang phát BLE và đang ở trong tầm kết nối.",
                "Kết nối rớt: đưa điện thoại lại gần thiết bị và thử ghép nối lại.",
                "Không quét được EPC: kiểm tra R6 đã vào chế độ UHF và app đã báo Đã kết nối.",
            ),
        )
    }
}

@Composable
private fun GuideSection(
    title: String,
    lines: List<String>,
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            lines.forEach { line ->
                Text("• $line", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

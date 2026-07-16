package com.example.koistock.ui.shell

data class AppDestination(
    val route: String,
    val title: String,
    val subtitle: String,
)

object AppDestinations {
    val Dashboard = AppDestination("dashboard", "Tổng quan", "Trang chủ vận hành")
    val Pairing = AppDestination("pairing", "Kết nối R6", "Ghép nối đầu đọc Chainway R6")
    val Guide = AppDestination("guide", "Hướng dẫn", "Checklist kết nối và cài đặt")
    val Lookup = AppDestination("lookup", "Tra cứu", "Quét một thẻ để xem thông tin")
    val Locate = AppDestination("locate", "Tìm sản phẩm", "Định vị theo EPC hoặc SKU")
    val Count = AppDestination("count", "Quét theo khu", "Kiểm kê và đối chiếu")
    val InOut = AppDestination("inout", "Nhập / Xuất", "Lệnh tăng giảm tồn kho")
    val Assign = AppDestination("assign", "Gán tag", "Liên kết EPC với SKU")
    val Putaway = AppDestination("putaway", "Đặt vị trí", "Gán vị trí khu kệ")
    val Zones = AppDestination("zones", "Khu vực", "Quản lý khu và kệ")
    val Hardware = AppDestination("hardware", "Test R6", "Kiểm tra scan, locate, write EPC")
    val Sync = AppDestination("sync", "Đồng bộ kho", "Kích hoạt đồng bộ PostgreSQL và Sheet")

    val menuItems = listOf(
        Lookup,
        Locate,
        Count,
        InOut,
        Assign,
        Putaway,
        Zones,
        Hardware,
        Sync,
        Guide,
    )
}

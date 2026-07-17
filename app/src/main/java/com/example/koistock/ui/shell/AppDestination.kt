package com.example.koistock.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sell
import androidx.compose.ui.graphics.vector.ImageVector

data class AppDestination(
    val route: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector? = null,
)

object AppDestinations {
    val Dashboard = AppDestination("dashboard", "Tổng quan", "Trang chủ vận hành")
    val Settings = AppDestination("settings", "Cài đặt", "Kết nối, kiểm tra thiết bị")
    val Pairing = AppDestination("pairing", "Kết nối R6", "Ghép nối đầu đọc Chainway R6")
    val Guide = AppDestination("guide", "Hướng dẫn", "Checklist kết nối và cài đặt")
    val Hardware = AppDestination("hardware", "Test R6", "Kiểm tra scan, locate, write EPC")

    val Lookup = AppDestination("lookup", "Tra cứu", "Quét một thẻ để xem thông tin", Icons.Filled.Search)
    val Locate = AppDestination("locate", "Tìm sản phẩm", "Định vị theo EPC hoặc SKU", Icons.Filled.LocationOn)
    val InOut = AppDestination("inout", "Nhập / Xuất", "Lệnh tăng giảm tồn kho", Icons.Filled.ImportExport)
    val Count = AppDestination("count", "Quét theo khu", "Kiểm kê và đối chiếu", Icons.Filled.Inventory2)
    val Assign = AppDestination("assign", "Gán tag", "Liên kết EPC với SKU", Icons.Filled.Sell)
    val Putaway = AppDestination("putaway", "Đặt vị trí", "Gán vị trí khu kệ", Icons.Filled.PinDrop)
    val Warehouse = AppDestination("warehouse", "Quản lý kho", "SKU, khu và kệ", Icons.Filled.GridView)

    /** Các ô nghiệp vụ hiển thị trên dashboard, sắp theo luồng công việc thực tế. */
    val menuItems = listOf(
        Lookup,
        Locate,
        InOut,
        Count,
        Assign,
        Putaway,
        Warehouse,
    )

    /** Tra cứu route -> title, dùng để đặt tiêu đề TopAppBar cho màn con. */
    val titleByRoute: Map<String, String> = listOf(
        Dashboard, Settings, Pairing, Guide, Hardware,
        Lookup, Locate, InOut, Count, Assign, Putaway, Warehouse,
    ).associate { it.route to it.title }

    const val ScanConfigArg = "function"
    const val ScanConfigRoutePattern = "scanconfig/{$ScanConfigArg}"

    fun scanConfigRoute(function: com.example.koistock.device.ScanFunction) = "scanconfig/${function.key}"
}

# KOIStock — Redesign UI theo chuẩn Material Design 3

- **Ngày:** 2026-07-17
- **Trạng thái:** Approved (đã duyệt, đang triển khai)
- **Nền tảng:** Android (Kotlin + Jetpack Compose Material 3)

## 1. Mục tiêu

Chuyển KOIStock từ trạng thái "demo có nhiều hướng dẫn/test rải rác" thành **app thật** theo chuẩn UI/UX Material 3:

1. Sửa crash khi mở "Tìm sản phẩm — định vị theo EPC".
2. Bố cục lại điều hướng, thêm nút back, dashboard mới (pin + nút đồng bộ).
3. Gom test/kỹ thuật vào một trang **Cài đặt** (icon bánh răng); bỏ nút hướng dẫn "?" rải rác.
4. Sắp xếp lại menu chức năng theo luồng công việc.

## 2. Nguyên nhân crash & cách sửa

`LocateScreen` gọi `vm.startForSku("SKU1")` (SKU hardcode) trong `DisposableEffect` ngay khi mở màn. Chuỗi gọi tới `sdk.startLocation(...)` của SDK Chainway; khi R6 **chưa kết nối** lệnh native ném exception không bắt → app chết.

Sửa 2 lớp:
- **Lớp thiết bị:** bọc mọi lệnh gọi SDK native trong `ChainwayRfidReader` (`startLocation`, `stopLocation`, `startInventory`, `stopInventory`, `beep`, `battery`, `writeEpc`, `scanSingle`) bằng `runCatching`/try-catch để không bao giờ làm crash UI.
- **Lớp UI:** bỏ SKU cứng. Màn Tìm sản phẩm chỉ bắt đầu dò sau khi người dùng **chọn SKU** và có kết nối; chưa kết nối thì hiện thông báo thay vì gọi SDK.

## 3. Điều hướng (Material 3)

- **TopAppBar theo ngữ cảnh:**
  - Dashboard: tiêu đề "KOIStock" + `BatteryIndicator` (icon %) + `IconButton` Đồng bộ + `IconButton` Cài đặt.
  - Màn con: `navigationIcon` mũi tên back (pop back stack) + tiêu đề màn + `BatteryIndicator`.
- Xóa route mồ côi `menu` + `MainMenuScreen` (trùng lưới dashboard).
- Thêm destination `Settings`. Bỏ route `Sync` dạng màn (thành hành động ở header) và các placeholder.

## 4. Dashboard mới

- Bỏ banner demo dài dòng.
- **Card trạng thái:** trạng thái kết nối R6 (MAC / "Chưa kết nối") + % pin; nút "Kết nối R6" khi chưa nối.
- **Lưới nghiệp vụ 2 cột** (`Card` bo góc 16dp, có icon + tiêu đề + phụ đề), sắp theo luồng dùng:
  1. Tra cứu (`Search`)
  2. Tìm sản phẩm (`LocationOn`)
  3. Nhập / Xuất (`ImportExport`)
  4. Kiểm kê / Quét theo khu (`Inventory2`)
  5. Gán tag (`Sell`)
  6. Đặt vị trí (`PinDrop`)
  7. Khu vực (`GridView`)

## 5. Nút Đồng bộ

Backend hiện **chưa có endpoint trigger Airflow**. Nút Đồng bộ = **làm mới dữ liệu từ backend**: gọi `HttpProductRepository.refresh()` + `HttpLocationRepository.refresh()` + `refreshBattery()`.
- Trạng thái: icon xoay/`CircularProgressIndicator` khi đang chạy.
- Kết quả: `Snackbar` "Đã cập nhật lúc HH:mm" (hoặc báo lỗi mạng).
- Mở rộng sau: khi backend có endpoint trigger, thay phần thân hàm sync.

## 6. Trang Cài đặt

Icon bánh răng ở dashboard → `SettingsScreen` liệt kê (dạng danh sách `ListItem`/Card):
- Kết nối R6 (mở `PairingScreen`).
- Test phần cứng R6 (mở `HardwareTestScreen`).
- Hướng dẫn kết nối (mở `ConnectionGuideScreen`).
- Thông tin backend (base URL, kiến trúc) — chỉ đọc.

## 7. Màn Tìm sản phẩm (2 pha)

- **Pha chọn:** danh sách sản phẩm + ô tìm kiếm (`OutlinedTextField`), lọc theo tên/SKU. Chọn 1 SKU.
- **Pha dò:** `LinearProgressIndicator` tín hiệu + nhịp beep + nút "Đổi sản phẩm" (quay lại pha chọn). `DisposableEffect(sku)` start locate; `onDispose` stop.
- Guard: nếu R6 chưa kết nối, hiện banner "Cần kết nối R6 để định vị" + nút mở Cài đặt/Kết nối.
- `LocateViewModel` giữ nguyên logic beep; `LocateScreen` nhận `products` từ AppShell.

## 8. Dọn dẹp

- Xóa `FeatureGuideButton` khỏi 8 màn nghiệp vụ; xóa file `ui/common/FeatureGuide.kt`, `ui/common/PlaceholderFeatureScreen.kt`, `ui/shell/MainMenuScreen.kt`.
- Bỏ text mô tả kiểu demo trong các màn; tiêu đề màn do TopAppBar cung cấp (bỏ tiêu đề trùng trong thân màn).

## 9. Hệ thống hình ảnh

- Giữ `colorScheme` Material 3 hiện có (light/dark), palette ElectricBlue/SoftMint/Tangerine/Ash…
- Thêm dependency `androidx.compose.material:material-icons-extended` (theo BOM) cho bộ icon chuẩn.
- Chuẩn: card bo góc 16dp, khoảng cách 16dp, touch target ≥ 48dp, dùng color role của theme (không hardcode màu chữ nền khi có sẵn role).
- `BatteryIndicator`: icon pin + %, đổi màu theo mức (≥50 xanh, ≥20 cam, <20 đỏ), "—" khi chưa có dữ liệu.

## 10. Kiểm thử

- Unit test hiện có cho `LocateViewModel` (logic beep) giữ nguyên; cập nhật nếu đổi chữ ký `LocateScreen`.
- Build `assembleDebug` + chạy `testDebugUnitTest` để xác nhận không vỡ.

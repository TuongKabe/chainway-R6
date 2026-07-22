# Locate Catalog Batch Loading Design

## Goal

Giảm thời gian tải lần đầu của màn **Tìm sản phẩm** bằng cách loại bỏ toàn bộ request `getBySku()` tuần tự.

## Architecture

Tạo `HttpLocateCatalogRepository` dùng trực tiếp `KoiApiService`. Repository gọi song song `GET /api/items` và `GET /api/epc-tags?status=active`, sau đó ghép hai response theo `itemCode` trong bộ nhớ. `LocateViewModel` chỉ gọi một hàm `load()` và không còn phụ thuộc `TagRepo` hoặc `ProductRepo` để dựng catalog.

Đây là hai batch request cho toàn bộ catalog, thay cho một request tag cộng tối đa bốn request cho từng SKU. Nếu backend sau này có endpoint SQL join riêng, chỉ cần thay implementation của `LocateCatalogRepository` mà không đổi ViewModel hoặc Compose.

## Rules

- Chỉ mapping có `status` bằng `active`, không phân biệt hoa/thường, được sử dụng.
- Chỉ item có `isActive == true` được ghép.
- Một SKU xuất hiện một lần và giữ toàn bộ EPC active của nó.
- SKU có tag nhưng không có item active tạo lỗi dữ liệu rõ ràng; không âm thầm bỏ qua.
- Hai batch request chạy đồng thời trong cùng coroutine scope.
- Không gọi `ProductRepo.getBySku`, API bin hoặc API tag theo từng SKU.

## Product mapping

Catalog chỉ cần tên, đơn vị, tracking mode, vị trí và số tag để hiển thị. `Product.quantity` được tính từ số tag active của SKU. `Product.locationCode` lấy từ `locationCode`, sau đó fallback sang `warehouse` của tag active đầu tiên, rồi `defaultWarehouse` của item.

## Error handling

Nếu một trong hai batch request lỗi, repository ném lỗi và `LocateViewModel` giữ cơ chế `LocateCatalogState.Error` cùng nút **Thử lại** hiện tại.

## Testing

- Test repository xác nhận gọi đúng một lần `getItems` và một lần `getActiveTags`.
- Test xác nhận không có request API nào khác.
- Test lọc item inactive và tag void.
- Test gom nhiều EPC vào một SKU và ánh xạ quantity/location.
- Test ViewModel sử dụng catalog repository và giữ nguyên các test tìm kiếm, RSSI, trigger và beep.

## Scope

Không sửa backend, database, quy trình gán tag, đồng bộ kho hoặc thuật toán RFID. Tối ưu chỉ thay đường tải catalog của màn định vị.

# Locate Catalog Batch Loading Design

## Goal

Giảm thời gian tải lần đầu của màn **Tìm sản phẩm** bằng cách loại bỏ toàn bộ request `getBySku()` tuần tự.

## Architecture

Tạo `HttpLocateCatalogRepository` dùng trực tiếp `KoiApiService`. Repository gọi `GET /api/epc-tags?status=active`, gom các SKU duy nhất, rồi gọi song song `GET /api/items/{code}` đúng một lần cho mỗi SKU. `LocateViewModel` chỉ gọi một hàm `load()` và không còn phụ thuộc `TagRepo` hoặc `ProductRepo` để dựng catalog.

Endpoint danh sách items hiện bị giới hạn và không phân trang được, nên không thể join đầy đủ bằng hai batch request. Thiết kế mới giữ dữ liệu đúng nhưng thay tối đa bốn request tuần tự cho từng SKU bằng đúng một request metadata nhẹ cho mỗi SKU, chạy đồng thời. Nếu backend sau này có endpoint SQL join riêng, chỉ cần thay implementation của `LocateCatalogRepository` mà không đổi ViewModel hoặc Compose.

## Rules

- Chỉ mapping có `status` bằng `active`, không phân biệt hoa/thường, được sử dụng.
- Chỉ item có `isActive == true` được ghép; item inactive có tag active được bỏ riêng và không làm lỗi toàn catalog.
- Một SKU xuất hiện một lần và giữ toàn bộ EPC active của nó.
- SKU có tag nhưng item inactive được bỏ khỏi danh sách. Lỗi HTTP khi tải metadata vẫn làm catalog báo lỗi.
- Các request metadata SKU chạy đồng thời trong cùng coroutine scope.
- Không gọi `ProductRepo.getBySku`, API bin hoặc API tag theo từng SKU.

## Product mapping

Catalog chỉ cần tên, đơn vị, tracking mode, vị trí và số tag để hiển thị. `Product.quantity` được tính từ số tag active của SKU. `Product.locationCode` lấy từ `locationCode`, sau đó fallback sang `warehouse` của tag active đầu tiên, rồi `defaultWarehouse` của item.

## Error handling

Nếu một trong hai batch request lỗi, repository ném lỗi và `LocateViewModel` giữ cơ chế `LocateCatalogState.Error` cùng nút **Thử lại** hiện tại.

## Testing

- Test repository xác nhận gọi đúng một lần `getActiveTags` và đúng một lần `getItem` cho mỗi SKU duy nhất.
- Test xác nhận không có request API nào khác.
- Test lọc item inactive và tag void.
- Test gom nhiều EPC vào một SKU và ánh xạ quantity/location.
- Test ViewModel sử dụng catalog repository và giữ nguyên các test tìm kiếm, RSSI, trigger và beep.

## Scope

Không sửa backend, database, quy trình gán tag, đồng bộ kho hoặc thuật toán RFID. Tối ưu chỉ thay đường tải catalog của màn định vị.

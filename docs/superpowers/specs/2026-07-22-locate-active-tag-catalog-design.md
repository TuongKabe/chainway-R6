# Locate Active-Tag Catalog Design

## Goal

Màn **Tìm sản phẩm** chỉ hiển thị những SKU có ít nhất một EPC đang ở trạng thái `active`, dùng bảng mapping EPC–SKU làm nguồn sự thật cho khả năng định vị.

## Business rules

- Một SKU xuất hiện khi có ít nhất một mapping tag có `status == "active"`.
- SKU không có tag hoặc chỉ có tag `void` không xuất hiện.
- Nhiều tag active của cùng SKU tạo một dòng SKU; người dùng chọn EPC ở bước định vị.
- Tìm kiếm khớp tên hoặc SKU, không phân biệt hoa/thường và bỏ khoảng trắng đầu/cuối.
- Catalog được tải lại mỗi lần mở màn Tìm sản phẩm để phản ánh tag vừa gán.
- Lỗi tải catalog được hiển thị rõ và có thao tác thử lại; không hiển thị danh sách toàn bộ sản phẩm làm dữ liệu thay thế vì sẽ vi phạm rule.

## Architecture

`GET /api/epc-tags?status=active` cung cấp toàn bộ mapping active. Android mở rộng `TagRepo` bằng thao tác lấy danh sách tag active. `LocateViewModel` tải mapping trước, gom theo SKU, ghép với dữ liệu sản phẩm đã đồng bộ và công bố các `LocatableProduct` cho Compose.

`LocateScreen` không còn tự gọi repository trong `LaunchedEffect`. Nó chỉ render state từ ViewModel: loading, error, danh sách catalog, SKU được chọn và các EPC active. Luồng đọc inventory, so khớp EPC, RSSI và beep hiện tại được giữ nguyên.

Nếu mapping trỏ tới SKU chưa có trong danh sách sản phẩm đã đồng bộ, ViewModel gọi `ProductRepo.getBySku(sku)` để bổ sung. Nếu sản phẩm thực sự không tồn tại hoặc không tải được thì catalog báo lỗi dữ liệu thay vì âm thầm bỏ SKU đã có tag.

## Data model and flow

`LocatableProduct` gồm `product: Product` và `activeTags: List<TagMapping>`. Danh sách được sắp xếp theo SKU và không chứa phần tử có `activeTags` rỗng.

Luồng vào màn:

1. `LocateViewModel.refreshCatalog()` tải toàn bộ tag active.
2. ViewModel lọc phòng vệ lại theo `status == "active"` và gom theo SKU.
3. ViewModel ghép từng nhóm với sản phẩm từ cache; SKU thiếu được tra trực tiếp qua repository.
4. ViewModel công bố `LocateCatalogState.Ready(items)` hoặc `Error(message)`.
5. Compose tìm kiếm trên `Ready.items`; khi chọn một item, dùng ngay `activeTags` để chọn EPC.

## Error handling

- Lỗi API tag: hiển thị lỗi tải danh sách SKU có tag và nút **Thử lại**.
- Mapping active trỏ tới SKU không tồn tại: báo SKU cụ thể bị thiếu dữ liệu sản phẩm.
- Không có tag active: hiển thị trạng thái rỗng đúng nghĩa, không yêu cầu đồng bộ sản phẩm.
- Tag bị void sau khi catalog đã tải sẽ biến mất ở lần refresh hoặc lần mở màn kế tiếp.

## Testing

- Repository chuyển đúng response danh sách tag thành `TagMapping`.
- Catalog gồm SKU có tag active và loại SKU chưa có tag/tag void.
- Nhiều EPC cùng SKU chỉ tạo một item và giữ đủ EPC active.
- SKU thiếu trong cache được bổ sung bằng `getBySku`.
- Mapping tới SKU không tồn tại tạo error state.
- Search chuẩn hóa khoảng trắng và không phân biệt hoa/thường.
- Các test RSSI, trigger, inventory và beep hiện có tiếp tục chạy.

## Scope

Không tạo bảng backend mới, không thay đổi quy trình gán/void tag và không thay đổi thuật toán định vị RFID. Backend cần hỗ trợ trả danh sách từ endpoint EPC-tag khi truyền `status=active`; repository Android sẽ dùng contract đó.

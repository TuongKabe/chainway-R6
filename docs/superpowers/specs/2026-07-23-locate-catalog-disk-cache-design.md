# Locate Catalog Disk Cache Design

## Goal

Màn **Tìm sản phẩm** hiển thị catalog đã lưu trên điện thoại ngay khi mở và chỉ tải lại dữ liệu cần thiết ở nền.

## Architecture

Thêm `LocateCatalogStore` dựa trên Preferences DataStore, lưu một JSON snapshot gồm danh sách `LocatableProduct`, chữ ký tag theo SKU và thời điểm metadata được xác minh đầy đủ. `CachedLocateCatalogRepository` bọc repository HTTP hiện tại: đọc cache để phục vụ UI trước, sau đó refresh nền và ghi snapshot mới.

`LocateViewModel` có hai bước rõ ràng: `loadCachedCatalog()` công bố cache ngay, rồi `refreshCatalog()` đồng bộ nền. Nếu không có cache, state giữ `Loading` đến khi lần tải server đầu hoàn tất. Nếu có cache mà refresh lỗi, danh sách cache vẫn hiển thị và state chứa cảnh báo không chặn.

## Cache format

Snapshot lưu:

- `items`: sản phẩm cùng các EPC active.
- `tagSignatureBySku`: chữ ký ổn định từ `epc`, `status`, `locationCode` và `syncRev` của từng SKU.
- `metadataVerifiedAt`: epoch millis của lần refresh metadata toàn bộ gần nhất.
- `schemaVersion`: bắt đầu từ `1`; version không hỗ trợ được coi như cache rỗng.

Gson hiện có trong Retrofit được dùng để serialize JSON; không thêm Room hoặc database migration.

## Refresh rules

1. Gọi `getActiveTags()` một lần.
2. Lọc tag active, gom theo SKU và tính chữ ký.
3. SKU mới hoặc có chữ ký thay đổi: gọi `getItem(sku)`.
4. SKU biến mất khỏi danh sách tag active: xóa khỏi snapshot.
5. SKU không đổi: tái sử dụng metadata và tag đã cache.
6. Mỗi 6 giờ, refresh metadata của mọi SKU active để bắt thay đổi tên, tracking mode hoặc `isActive` độc lập với tag.
7. Các request `getItem` cần thiết chạy đồng thời.
8. Item inactive bị loại khỏi snapshot và không chặn các SKU hợp lệ.

## UI behavior

- Có cache: hiển thị ngay, đồng bộ nền không thay bằng màn loading.
- Không có cache: hiển thị loading cho lần đầu.
- Refresh thành công: thay catalog bằng snapshot mới.
- Refresh lỗi khi có cache: giữ danh sách, hiện cảnh báo ngắn và cho thử lại.
- Refresh lỗi khi không có cache: hiện error state hiện tại.

## Assignment invalidation

Trong phạm vi đầu tiên, cache được đối chiếu nền mỗi lần mở Locate. API gán/void không trực tiếp ghi cache để tránh coupling giữa các feature; sau khi gán trên máy, mở Locate sẽ hiện cache ngay và cập nhật SKU mới khi refresh nền hoàn tất. Việc cập nhật cache tức thời từ Assign có thể bổ sung sau nếu thời gian nền chưa đáp ứng.

## Error and corruption handling

- JSON hỏng, schema sai hoặc snapshot thiếu dữ liệu bắt buộc: bỏ cache và tải server.
- Ghi DataStore lỗi không làm mất catalog vừa tải trong RAM.
- HTTP lỗi không xóa snapshot tốt gần nhất.

## Testing

- Store round-trip snapshot, cache rỗng, JSON hỏng và schema sai.
- Repository không gọi `getItem` cho SKU có chữ ký không đổi.
- SKU mới/tag thay đổi chỉ refresh SKU tương ứng.
- SKU không còn tag active bị xóa.
- TTL 6 giờ buộc refresh toàn bộ metadata.
- Item inactive bị loại riêng.
- ViewModel công bố cache trước khi refresh và giữ cache khi refresh lỗi.
- Toàn bộ test RSSI, trigger, beep và catalog hiện tại tiếp tục chạy.

## Scope

Không sửa backend, database server, quy trình RFID hoặc dữ liệu kho khác. Cache chỉ phục vụ catalog của màn định vị.

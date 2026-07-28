# Hiển thị vị trí kho theo EPC trong Tìm sản phẩm

## Mục tiêu

Trong tính năng **Tìm sản phẩm – định vị theo EPC**, hiển thị đúng kho và vị trí của từng EPC để người dùng biết khu vực cần tìm trước khi bắt đầu dò RFID.

## Phạm vi

- Chỉ thay đổi màn `LocateScreen` và dữ liệu catalog dùng cho màn này.
- Không thay đổi màn tra cứu/quét EPC (`LookupScreen`).
- Không thay đổi thuật toán dò, RSSI, tiếng beep hay điều khiển đầu đọc.

## Thiết kế dữ liệu

- Giữ riêng ba trường từ `koistock.epc_tag`: `warehouse`, `location_code`, `bin`.
- Mở rộng `TagMapping` để không gộp mất thông tin kho.
- Snapshot và delta Supabase trả đủ cả ba trường.
- Cache offline lưu đủ cả ba trường; dữ liệu cache cũ thiếu trường mới vẫn đọc được với giá trị rỗng.

## Hiển thị

- Danh sách chọn sản phẩm hiển thị vị trí tóm tắt lấy từ các EPC active.
- Mỗi dòng EPC trong bộ chọn tag hiển thị:
  - `Kho: <warehouse>` hoặc `Kho: Chưa gán`.
  - `Vị trí: <location_code>`; fallback sang `bin`; nếu đều thiếu thì `Chưa gán`.
- Khi đã chọn EPC và chuyển sang pha dò, phần thông tin sản phẩm tiếp tục hiển thị kho và vị trí của đúng EPC đang chọn.
- SKU có nhiều EPC ở nhiều kho không dùng vị trí chung của SKU để thay cho vị trí EPC.

## Kiểm thử

- Unit test xác nhận mapping Supabase giữ riêng `warehouse`, `location_code`, `bin`.
- Unit test xác nhận hàm định dạng ưu tiên `location_code`, sau đó `bin`, cuối cùng `Chưa gán`.
- Compile, chạy toàn bộ unit test, build APK, cài đè lên thiết bị và mở app.

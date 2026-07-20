# Chainway R6 Verified Configuration Design

## Mục tiêu

Bảo đảm cấu hình hiển thị trong KOIStock phản ánh đúng cấu hình mà Chainway R6 thực sự đang sử dụng. Người vận hành có thể đặt vùng tần số chung cho thiết bị, chỉnh profile riêng của từng chức năng, và thấy rõ mọi sai lệch giữa giá trị app yêu cầu với giá trị đọc lại từ máy.

## Phạm vi

Thiết kế này bao gồm:

1. Đọc các thông số cấu hình thực tế từ R6.
2. Kiểm tra kết quả từng lệnh cấu hình và xác nhận lại sau khi ghi.
3. Quản lý vùng tần số chung cho toàn thiết bị.
4. Bổ sung các thông số RFID được SDK hỗ trợ vào cấu hình riêng của từng chức năng.

Không bao gồm màn hình đo tầm đọc theo khoảng cách, preset thử nghiệm đọc xa, hoặc tự động đặt 30 dBm cho mọi chức năng.

## Nguyên tắc an toàn

- Vùng tần số là cấu hình chung của R6, không thuộc profile từng chức năng.
- Mặc định cài mới là Việt Nam, mã SDK `0x35`, dải `918–923 MHz`.
- App không tự đổi vùng tần số khi chuyển chức năng.
- Chỉ ghi vùng tần số khi người dùng chủ động lưu trong phần Thiết bị R6.
- Không coi giá trị đã lưu trong app là giá trị thực tế của máy.
- Không báo áp dụng thành công trước khi kiểm tra kết quả lệnh và đọc lại cấu hình.
- Không tăng toàn bộ chức năng lên 30 dBm; profile Gán tag và Tra cứu tiếp tục ưu tiên tránh đọc nhầm thẻ gần đó.

## Mô hình cấu hình

### Cấu hình chung của thiết bị

`R6DeviceConfig` lưu vùng tần số mong muốn:

- Region code của SDK.
- Tên hiển thị.
- Dải tần số.

Phiên bản đầu hỗ trợ tối thiểu lựa chọn Việt Nam và các region code đã được SDK/demo Chainway xác nhận. Việt Nam là giá trị mặc định. Các vùng khác phải hiển thị rõ dải tần để tránh chọn nhầm.

### Profile theo chức năng

Mỗi `ScanFunction` tiếp tục có một `ScanProfile` riêng, gồm:

- Chế độ cò.
- Công suất `1–30 dBm`.
- Session `S0–S3`.
- Q `0–15`.
- Miller/RF Link: `FM0`, `M2`, `M4`, `M8` khi SDK hiện tại hỗ trợ ánh xạ ổn định.
- TagFocus.
- FastID.
- Chế độ dữ liệu đọc: EPC hoặc EPC + TID. FastID và chế độ dữ liệu phải được trình bày nhất quán để người dùng không bật hai lựa chọn mâu thuẫn.

Giá trị ngoài giới hạn phải được chuẩn hóa trước khi lưu và trước khi gửi xuống SDK.

## Đọc thông số thực tế

Tầng `RfidReader` cung cấp một snapshot cấu hình thực tế, tách khỏi profile đã lưu. Snapshot gồm các giá trị SDK có thể đọc lại một cách đáng tin cậy:

- Công suất.
- Vùng tần số.
- Gen2 Session và Q.
- Miller/RF Link nếu SDK trả về.
- FastID và TagFocus nếu SDK có getter tương ứng.

Thông số không được firmware/SDK hỗ trợ đọc lại phải có trạng thái `Không hỗ trợ xác nhận`, không được suy đoán từ giá trị app vừa gửi.

## Quy trình áp dụng cấu hình

Khi bắt đầu một chức năng quét:

1. Lấy profile đã lưu của chức năng đó và chuẩn hóa giới hạn.
2. Gửi từng lệnh cấu hình xuống R6.
3. Thu kết quả thành công/thất bại của từng lệnh; không bỏ qua giá trị Boolean hoặc exception.
4. Đọc một snapshot toàn bộ sau khi hoàn tất các lệnh ghi.
5. So sánh giá trị yêu cầu với giá trị thực tế.
6. Chỉ bắt đầu inventory khi các thông số quan trọng đã áp dụng đúng.

Thông số quan trọng gồm công suất, region, Session và Q. Nếu một trong các thông số này sai lệch hoặc không thể ghi, app dừng bắt đầu lượt quét và hiển thị lỗi cụ thể. Thông số tùy chọn không được SDK hỗ trợ xác nhận sẽ hiển thị cảnh báo nhưng không chặn quét nếu lệnh ghi đã thành công.

Khi lưu vùng tần số chung:

1. Yêu cầu R6 đang kết nối.
2. Gửi region code đã chọn.
3. Kiểm tra kết quả lệnh.
4. Đọc lại region từ máy.
5. Chỉ lưu trạng thái thành công khi giá trị đọc lại trùng khớp.

Nếu R6 chưa kết nối, lựa chọn vẫn có thể được lưu làm giá trị mong muốn. Giao diện phải ghi rõ `Chưa áp dụng lên máy`; lần kết nối sau chỉ áp dụng theo hành động rõ ràng của luồng kết nối/cấu hình và phải xác nhận lại.

## Giao diện Setting

### Mục Thiết bị R6

Hiển thị:

- Trạng thái kết nối.
- Vùng tần số mong muốn.
- Vùng tần số thực tế đọc từ máy.
- Nút `Đọc lại từ máy`.
- Nút `Lưu và áp dụng`.
- Trạng thái `Đã xác nhận`, `Chưa áp dụng`, `Sai lệch`, hoặc `Không đọc được`.

Ví dụ sai lệch: `Yêu cầu Việt Nam 918–923 MHz, R6 đang dùng USA 902–928 MHz`.

### Cấu hình từng chức năng

Giữ màn hình hiện tại và bổ sung phần `R6 thực tế` khi thiết bị kết nối:

- Mỗi trường hiển thị giá trị đang chỉnh và, nếu có, giá trị máy đang dùng.
- Có nút `Đọc lại từ máy`.
- Nút Lưu thực hiện lưu profile, áp dụng cho R6 nếu đang kết nối, rồi đọc lại xác nhận.
- Nếu đang không kết nối, nút Lưu chỉ lưu profile và hiển thị `Đã lưu, chưa áp dụng lên R6`.
- `Đặt mặc định` chỉ khôi phục profile của chức năng hiện tại, không thay đổi region chung.

Màn hình phải giải thích rằng khoảng cách công bố trên 26 m là điều kiện thử nghiệm tối ưu với thẻ phù hợp; 30 dBm không bảo đảm mọi thẻ đều đạt khoảng cách đó.

## Trạng thái và báo lỗi

Kết quả áp dụng dùng một mô hình có cấu trúc thay vì Boolean tổng:

- Giá trị yêu cầu.
- Kết quả từng lệnh SDK.
- Snapshot đọc lại.
- Danh sách sai lệch.
- Danh sách thông số không hỗ trợ xác nhận.
- Mức độ: thành công, cảnh báo, hoặc chặn quét.

Thông báo lỗi phải nêu đúng trường, ví dụ:

- `Không đặt được công suất 30 dBm`.
- `Yêu cầu S0 nhưng R6 đang trả về S1`.
- `Không đọc được vùng tần số từ R6`.
- `RF Link đã gửi nhưng firmware không hỗ trợ đọc lại`.

## Tương thích và dữ liệu cũ

- Profile hiện có trong DataStore tiếp tục được sử dụng.
- Trường mới nhận giá trị mặc định của từng chức năng khi chưa tồn tại.
- Region chung dùng Việt Nam cho cài mới; dữ liệu cũ được đánh dấu chưa xác nhận cho đến khi đọc được từ R6.
- Không thay đổi SKU, dữ liệu kho, EPC hoặc lịch sử giao dịch.

## Kiểm thử

### Unit test

- Chuẩn hóa giới hạn của mọi trường profile.
- Lưu và đọc region chung.
- Di trú profile cũ thiếu trường mới.
- Ánh xạ mã region Việt Nam `0x35`.
- Tổng hợp kết quả khi một lệnh SDK thất bại.
- Phát hiện giá trị đọc lại không khớp giá trị yêu cầu.
- Phân biệt thông số quan trọng với thông số tùy chọn không hỗ trợ đọc lại.
- Không cho bắt đầu inventory khi công suất, region, Session hoặc Q sai lệch.

### Kiểm thử tích hợp với R6

- Kết nối R6 và đọc snapshot ban đầu.
- Đổi công suất, Session, Q và xác nhận giá trị đọc lại.
- Đặt region Việt Nam và xác nhận mã `0x35`.
- Chuyển chức năng và xác nhận region không đổi.
- Ngắt kết nối khi lưu và xác nhận trạng thái `Đã lưu, chưa áp dụng`.
- Kiểm tra firmware không hỗ trợ getter tùy chọn không làm app crash.

## Tiêu chí hoàn thành

- Setting phân biệt rõ giá trị đã lưu với giá trị R6 thực tế.
- Không còn lệnh cấu hình quan trọng bị bỏ qua kết quả.
- Region Việt Nam là cấu hình chung và không bị profile chức năng ghi đè.
- Mỗi chức năng chỉnh được các thông số RFID đã thống nhất trong phạm vi.
- App chặn quét và báo đúng nguyên nhân khi cấu hình quan trọng không được áp dụng.
- Toàn bộ unit test và build debug vượt qua.

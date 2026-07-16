# KOIStock — Thiết kế app Android quản lý kho RFID

- **Ngày:** 2026-07-15
- **Trạng thái:** Draft (chờ review)
- **Nền tảng:** Android (Kotlin + Jetpack Compose), đầu đọc UHF RFID Chainway **R6** (Bluetooth BLE)

---

## 1. Mục tiêu

App Android quản lý kho, dùng đầu đọc **Chainway R6** (UHF RFID, kết nối BLE) để quét/tra cứu hàng hóa, đồng bộ dữ liệu với **Firebase (nguồn chính)** và **Google Sheet (bản gương 2 chiều)** qua **Airflow**.

Bốn nghiệp vụ lõi của MVP:
1. **Tra cứu hàng hóa** — quét 1 tag/barcode → hiện thông tin, tồn, vị trí.
   - Kèm **Tìm sản phẩm (định vị RFID)** — chọn hàng rồi dò tới tag, bíp nhanh dần khi lại gần.
2. **Kiểm kê (cycle count)** — quét hàng loạt, đối chiếu danh sách, báo khớp/thừa/thiếu.
3. **Nhập / Xuất kho** — quét ghi tăng/giảm tồn.
4. **Gán tag cho hàng mới** — bind EPC ↔ SKU (có thể ghi EPC có cấu trúc).

Ngoài ra bắt buộc có:
- **Ghép nối & quản lý kết nối R6 (BLE)**.
- **Quản lý khu vực** — thêm/sửa Khu, Kệ.
- **Quét theo khu** — chọn khu → bấm cò R6 → ra danh sách SKU thuộc khu đó (liệt kê + nút đối chiếu kỳ vọng).
- **Đặt/phân vị trí** hàng theo khu vực.
- **Xuất file kiểm kê (CSV)**.
- **Đồng bộ kho** — nút bấm tay kích hoạt đồng bộ Firestore ↔ Sheet qua Airflow.
- **Gán tag điền thẳng vào ô Google Sheet (chế độ bàn phím HID)** — bấm cò R6 → EPC tự điền vào ô đang chọn trên máy tính.

## 2. Bối cảnh & ràng buộc

| Yếu tố | Quyết định |
|---|---|
| Thiết bị đọc | Chainway **R6** — UHF RFID qua **Bluetooth BLE** (class `RFIDWithUHFBLE`) |
| App chạy trên | Điện thoại/tablet Android thường (min SDK 24), ghép nối R6 qua BLE |
| Quy mô | 500–3.000 SKU, 2–5 người dùng đồng thời |
| Kết nối mạng kho | Luôn online (không cần offline-first; chỉ cache nhẹ của Firestore) |
| Nguồn dữ liệu chính | **Firebase Firestore** (source of truth) |
| Google Sheet | Bản gương **đồng bộ 2 chiều**, dùng để xem/nhập tay/báo cáo |
| Cỗ máy đồng bộ | **Apache Airflow self-hosted**, DAG chạy **1–2 lần/ngày hoặc trigger tay** |
| Đăng nhập | Không — máy dùng chung (Firebase Anonymous Auth) |
| Kiểu gắn tag | **Hybrid**: mỗi SKU chọn SERIALIZED hoặc BULK |
| Phân khu vực | 2 cấp: **Khu → Kệ** (mã ví dụ `A-03`) |

## 3. Kiến trúc tổng thể

```
┌──────────────────────────┐   BLE    ┌──────────┐
│  Android (Kotlin/Compose)│◄────────►│ Chainway │
│  ┌────────────────────┐  │          │   R6     │
│  │ RfidReader (bọc SDK)│ │          └──────────┘
│  │  RFIDWithUHFBLE     │  │
│  └────────────────────┘  │  realtime listener
│  Firebase Anonymous Auth │◄───────────────┐
└──────────────────────────┘                │
                                     ┌───────▼──────────────┐
                                     │  Firebase Firestore   │  (nguồn sự thật)
                                     │  products / tags /    │
                                     │  transactions /       │
                                     │  locations / syncMeta │
                                     └───────▲──────────────┘
                                             │ Firestore API + Sheets API
                                     ┌───────┴──────────────┐
                                     │  Airflow (self-hosted)│
                                     │  DAG đối soát 2 chiều  │
                                     │  1–2 lần/ngày / manual │
                                     └───────▲──────────────┘
                                             │ Sheets API
                                     ┌───────┴──────────────┐
                                     │  Google Sheet (gương) │
                                     │  tabs: Products/Tags/  │
                                     │  Transactions/Locations│
                                     └───────────────────────┘
```

- **App ↔ Firestore**: realtime (listener), người dùng thấy tồn kho tức thì.
- **Firestore ↔ Sheet**: batch qua Airflow, eventual (trễ = chu kỳ DAG).

## 4. Mô hình dữ liệu (Firestore; mỗi collection phản chiếu 1 tab Sheet)

Trường đồng bộ chung ở mọi bản ghi: `updatedAt` (server timestamp), `origin` (`app`|`sheet`), `syncRev` (chống lặp).

### `products` (mỗi SKU 1 doc)
`sku` (khóa), `name`, `unit`, `trackingMode` (`SERIALIZED`|`BULK`), `quantity` (dùng cho BULK; với SERIALIZED = số tag active, được tính), `locationCode` (vị trí mặc định/chính), `imageUrl?`.

### `tags` (ánh xạ EPC ↔ SKU)
`epc` (khóa), `sku`, `unitSerial?`, `status` (`active`|`void`), `locationCode` (vị trí hiện tại của đơn vị này — dùng cho SERIALIZED).

### `transactions` (append-only, không sửa/xóa)
`id`, `type` (`IN`|`OUT`|`COUNT`|`ADJUST`|`MOVE`), `sku`, `epc?`, `delta`, `locationCode?`, `deviceId`, `at`.
**Tồn kho suy ra từ chuỗi giao dịch** → tránh lỗi đọc-sửa-ghi khi nhiều người thao tác.

### `locations` (phân cấp Khu → Kệ)
`code` (khóa, ví dụ `A` hoặc `A-03`), `name`, `type` (`ZONE`|`SHELF`), `parent` (rỗng với Khu).

### `syncMeta`
`lastSyncAt`, watermark `updatedAt` cuối cho mỗi chiều, `syncRev` counter.

## 5. Logic gắn SKU theo tag (EPC ↔ SKU)

**Nguồn sự thật = bảng `tags`** (ánh xạ EPC→SKU). Tùy chọn ghi thêm **EPC có cấu trúc** `KOI-<sku>-<serial>` (hoặc GS1 SGTIN nếu có GS1 prefix) để đọc nhanh/đối soát, nhưng bảng `tags` vẫn quyết định.

**Luồng "Gán tag cho hàng mới":**
1. Quét tag trắng → lấy EPC hiện tại.
2. Chọn SKU (tìm theo tên/mã hoặc quét barcode sản phẩm).
3. (tùy chọn) `writeDataToEpc` ghi EPC có cấu trúc.
4. Ghi doc `tags`: epc→sku (+ `unitSerial` nếu SERIALIZED).

**Tính tồn theo `trackingMode`:**
- **SERIALIZED**: `quantity` = số tag `active` của SKU (đếm; có thể theo từng `locationCode`). Kiểm kê/định vị từng món chính xác.
- **BULK**: `quantity` = trường cập nhật qua `transactions` (delta). Tag đại diện SKU/lô.

## 6. Phân theo khu vực (zoning) — Khu → Kệ

**Tag vị trí (location tag):** mỗi kệ dán 1 tag RFID (hoặc QR) mã hóa `locationCode` (ví dụ `A-03`).

**Chế độ "Đặt vị trí" (put-away):**
1. Quét **tag vị trí** trước → chọn ngữ cảnh khu/kệ.
2. Quét loạt hàng → hàng vừa quét được gán `locationCode` đó (ghi vào `tags.locationCode` cho SERIALIZED, hoặc `transactions type=MOVE` cho BULK).

**Ứng dụng:**
- Tra cứu: hiện "đang ở `A-03`".
- Kiểm kê theo khu: chọn khu/kệ → chỉ đối chiếu hàng kỳ vọng thuộc đó → báo thừa/thiếu/lệch vị trí.
- Báo cáo Sheet: tồn theo khu, danh sách hàng sai vị trí.

## 7. Ghép nối & quản lý kết nối R6 (BLE)

| Bước | Việc | API SDK |
|---|---|---|
| Quyền | Bluetooth + Vị trí (bắt buộc cho BLE scan) | runtime permissions |
| Khởi tạo | 1 lần khi mở app | `RFIDWithUHFBLE.getInstance().init(context)` |
| Tìm thiết bị | Quét BLE, hiện danh sách R6 (tên/MAC/RSSI) | `startScanBTDevices()` / `stopScanBTDevices()` |
| Kết nối | Chọn R6 → nối + theo dõi trạng thái | `connect(mac, ConnectionStatusCallback)` |
| Nhớ máy | Lưu MAC cuối → tự nối lại khi mở app | DataStore/SharedPreferences |
| Trạng thái | Banner Đang nối / Đã nối / Mất kết nối | `ConnectionStatusCallback`, `getConnectStatus()` |
| Pin & phản hồi | Hiện % pin R6; bíp/LED khi quét trúng | `getBattery()`, `setBeep()`, `triggerBeep()`, `openLed()` |
| Nút cò R6 | Bấm cò (qua BLE) → start/stop kiểm kê | `setKeyEventCallback()` |
| Ngắt/đóng | Khi thoát | `disconnect()` → `free()` |

**Điều kiện tiên quyết:** các màn nghiệp vụ chỉ bật khi R6 đã nối; mất kết nối giữa chừng → banner cảnh báo + tự thử nối lại.

## 8. Màn hình & luồng nghiệp vụ

Màn chính: các nút lớn (thao tác 1 tay), thanh trên hiện trạng thái kết nối + pin R6. UI Compose, chữ to, phản hồi âm thanh/rung khi quét trúng.

| Màn | Luồng | SDK |
|---|---|---|
| **Ghép nối** | Quét BLE → chọn R6 → nối; auto-reconnect | `startScanBTDevices`, `connect` |
| **Tra cứu** | Quét 1 tag (`inventorySingleTag`)/barcode → `tags`→`products` → tên/tồn/vị trí. Tag lạ → gợi ý Gán tag | UHF single / barcode |
| **Tìm sản phẩm (định vị)** | Chọn 1 SKU/đơn vị → giữ cò R6 → dò tín hiệu tag mục tiêu → **bíp tít nhanh dần khi lại gần** (kiểu Geiger) | `startLocation`/`stopLocation` + `IUHFLocationCallback` |
| **Quét theo khu / Kiểm kê** | Chọn khu/kệ → giữ/bấm cò R6 quét liên tục (`startInventoryTag`+callback) → gom EPC duy nhất → **nhóm theo SKU → danh sách (SKU, số lượng đếm được)**. Nút **"Đối chiếu"** → so với hàng kỳ vọng ở khu đó → Khớp/Thừa/Thiếu/Lệch vị trí. Lưu `transactions type=COUNT`; **xuất CSV** phiên đếm | UHF inventory loop |
| **Nhập/Xuất** | Chọn IN/OUT → quét nhiều tag → mỗi tag `delta ±1` → tồn cập nhật realtime | UHF inventory/single |
| **Gán tag** | Quét tag trắng → chọn SKU → (tùy chọn) `writeDataToEpc` → ghi `tags` | UHF write / barcode |
| **Đặt vị trí** | Quét tag vị trí → quét loạt hàng → gán `locationCode` | UHF single + loop |
| **Quản lý khu vực** | Danh sách Khu/Kệ; **thêm/sửa** (code, name, type, parent) → ghi `locations`. Tùy chọn ghi tag vị trí ngay | UHF write (tag vị trí) |
| **Đồng bộ kho** | Nút bấm → gọi **REST API Airflow** kích hoạt DAG đồng bộ 2 chiều; hiện trạng thái chạy/kết quả | — |

### Gán tag điền thẳng vào ô Google Sheet (chế độ bàn phím HID)
Kịch bản thao tác tại bàn với máy tính: mở kho trên Google Sheet → chọn ô SKU cần gán → bấm cò R6 → **EPC tự điền vào ô đang chọn** + Enter.
Vì Google Sheet không cho app ngoài biết ô đang chọn, phải đi bằng **đường giả lập bàn phím**:

- **Cách A (chính) — Điện thoại làm bàn phím Bluetooth (Android HID bridge):**
  R6 → app (đọc EPC qua BLE, SDK mode) → điện thoại đóng vai **HID keyboard** (`android.bluetooth.BluetoothHidDevice`) ghép với máy tính → "gõ" EPC (+ định dạng `KOI-<sku>` nếu muốn, + Tab/Enter) vào ô đang focus. App vẫn có thể ghi Firestore song song.
  - Điện thoại giữ **2 kết nối BT đồng thời** (central với R6 + HID peripheral với PC) — 2 vai trò khác nhau, chất lượng ảnh hưởng không đáng kể (payload rất nhỏ).
  - Cần **Android 9+ (API 28)** cho `BluetoothHidDevice` → **feature-gate**; máy tính không cần cài phần mềm, chỉ ghép Bluetooth 1 lần.
- **Cách B (tùy chọn tối giản) — R6 tự ghép máy tính như bàn phím HID** (nếu firmware R6 hỗ trợ): chỉ 1 kết nối R6↔PC, không cần điện thoại; nhưng EPC là mã thô (không định dạng) và không cập nhật Firestore. **Cần kiểm tra R6 thật.**

### Tìm sản phẩm — định vị RFID (Geiger)
Chọn 1 SKU (hoặc 1 đơn vị serial cụ thể) → app đặt **EPC mục tiêu** làm bộ lọc → `startLocation(context, callback)`. Callback `getLocationValue(int value)` trả **0–100 theo độ gần** → app map thành **nhịp bíp**: xa = bíp thưa, gần = **tít nhanh dần**, sát tag = tít liên tục; kèm thanh cường độ trên màn hình. Giữ cò để dò, nhả cò `stopLocation()`.
- **SKU có nhiều tag**: dùng **EPC có cấu trúc** (`KOI-<sku>-*`) làm mặt nạ lọc để dò *bất kỳ* đơn vị nào của SKU đó; hoặc chọn đúng 1 serial để tìm chính xác 1 món.

### Xuất file kiểm kê (CSV)
Sau 1 phiên **Quét theo khu / Kiểm kê**, xuất kết quả ra **CSV** (mã hóa UTF-8 có BOM để mở đúng tiếng Việt trên Excel). Cột: `khu`, `kệ`, `sku`, `tên`, `số lượng đếm được`, `kỳ vọng`, `chênh lệch`, `trạng thái` (Khớp/Thừa/Thiếu/Lệch vị trí), `thời điểm`. Lưu vào bộ nhớ máy + mở Android share sheet để gửi/upload.

## 9. Tích hợp SDK Chainway

- Bọc SDK sau interface Kotlin **`RfidReader`** (connect/disconnect/scanSingle/startInventory/stopInventory/writeEpc/battery/keyEvents). Bên dưới hiện thực bằng `RFIDWithUHFBLE`. → App không phụ thuộc trực tiếp class Chainway ⇒ dễ test (fake reader), dễ đổi máy.
- Thêm `DeviceAPI_ver20251103_release.aar` vào `app/libs` + `dependencies` (`implementation(files(...))`), khai báo quyền BLE/vị trí trong Manifest.
- Callback inventory chạy luồng nền → đẩy qua `Flow`/`StateFlow` vào Compose.
- Nút quét trên màn hình **và** cò cứng R6 (`setKeyEventCallback`) đều gọi cùng hành động.
- **Barcode**: R6 có **imager 2D SE2707 là *tùy chọn***. Nếu bản R6 của anh có gắn thì dùng `scanBarcode()` (đọc 1D/2D: UPC/EAN, Code128, QR, Datamatrix, PDF417...); nếu không, ẩn nhánh barcode qua **feature flag** `hasBarcode`.
- **Công suất đọc**: R6 hỗ trợ `setPower` dải **1–30 (bước 1 dB)**; tầm đọc tối đa >26 m — cần cho tùy chọn "công suất theo kệ/lối đi" ở màn Kiểm kê/Đặt vị trí để tránh quét lẫn kệ bên cạnh.

> Thông số phần cứng đầy đủ: [chainway-r6-specifications.md](../../hardware/chainway-r6-specifications.md) — BLE 5.0, EPC C1 Gen2/ISO18000-6C, pin 5200 mAh (~2.5h liên tục), 1000+ thẻ/giây.

## 10. Đồng bộ 2 chiều qua Airflow

**DAG (1–2 lần/ngày hoặc trigger tay):**
```
task 1: đọc thay đổi Firestore từ watermark trước (updatedAt) → ghi/ cập nhật Sheet
task 2: đọc thay đổi Sheet từ watermark trước (_updatedAt)     → ghi/ cập nhật Firestore
task 3: giải xung đột (last-write-wins theo updatedAt) + lưu watermark + syncRev mới
```

- **Credential**: Airflow self-hosted dùng **service account key** truy cập Firestore + Sheets API.
- **Chống lặp vòng**: mỗi bản ghi mang `origin` + `syncRev`; khi ghi sang phía kia, đánh dấu để lần đối soát sau nhận ra "tiếng vọng" và bỏ qua (so `syncRev`/hash nội dung).
- **Xung đột**: last-write-wins theo `updatedAt`, mức từng dòng/doc.
- **Tồn kho**: không đồng bộ trực tiếp con số dễ đè nhau — sửa số tồn tay trên Sheet sinh 1 `transactions type=ADJUST` (đặt tuyệt đối) khi kéo về Firestore.
- Airflow lo retry, log, giám sát, backfill.

**Kích hoạt tay từ app (nút "Đồng bộ kho"):** app gọi **Airflow REST API** `POST /api/v1/dags/{dag_id}/dagRuns` (kèm auth) để chạy DAG ngay, rồi poll `GET .../dagRuns/{run_id}` hiển thị trạng thái (đang chạy / xong / lỗi). Cần Airflow bật REST API + cấp credential riêng cho app (không dùng chung service account của DAG).

## 11. Xử lý lỗi & ngoại lệ

- **Mất kết nối BLE / ra ngoài tầm / R6 ngủ / pin yếu** → banner + tự reconnect; chặn thao tác quét khi chưa nối.
- **Tag lạ** (EPC chưa map) → đưa vào danh sách "chưa gán", nhắc gán; không chặn luồng.
- **Init RFID lỗi / không có R6** → báo rõ; cho chạy chế độ chỉ barcode nếu có.
- **Quét trùng** trong 1 phiên → lọc theo EPC duy nhất.
- **Mất Firestore tạm** → cache offline lo tạm, hàng đợi tự đẩy khi có mạng.
- **Xung đột Sheet↔Firestore** → last-write-wins theo `updatedAt`, ghi log DAG để soát.

## 12. Kiểm thử

- **Unit test** (SDK đã bọc sau interface → dùng fake `RfidReader`): tính tồn từ chuỗi transaction; đối soát kiểm kê (khớp/thừa/thiếu); quy tắc last-write-wins; logic gán vị trí SERIALIZED vs BULK.
- **DAG test**: Airflow với Firestore emulator + Sheet test → kiểm loop-prevention và conflict.
- **Thủ công trên R6 thật**: ghép nối/auto-reconnect, quét UHF single + inventory loop, ghi EPC, nút cò, pin, tầm đọc theo công suất.

## 13. Ngoài phạm vi MVP

- Đăng nhập/phân quyền theo người dùng (hiện dùng chung máy).
- Offline-first đầy đủ (hiện luôn online).
- Đồng bộ Sheet realtime (hiện batch qua Airflow).
- Định vị realtime (radar) từng tag trong phòng.
- Báo cáo/dashboard nâng cao ngoài Sheet.

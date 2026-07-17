# Chainway R6 — Cơ chế cò & tinh chỉnh độ chính xác

> Bổ sung cho [chainway-r6-specifications.md](chainway-r6-specifications.md). Mô tả cách app KOIStock
> điều khiển cò và các thông số Chainway (SDK `RFIDWithUHFBLE`) ảnh hưởng độ chính xác đọc.

---

## 1. Cơ chế cò (trigger) trên R6

**Quan trọng:** cò trên R6 (qua BLE) phát sự kiện **bóp rời rạc**, KHÔNG có tín hiệu "giữ/thả" (press/hold/release) đáng tin cậy. Khi giữ cò, firmware bắn `onKeyDown` lặp lại và `onKeyUp` không phải lúc nào cũng tới. Vì vậy app **không** dùng mô hình "giữ để quét, thả để dừng".

App gom sự kiện qua `TriggerPressTracker` → phát ra một luồng `triggerEvents` chỉ gồm các lần **bóp** (press). Mỗi chức năng có **2 chế độ cò** (cấu hình trong: Cài đặt → Cấu hình quét theo chức năng):

| Chế độ | Chức năng quét đơn (Tra cứu, Gán tag) | Chức năng gom nhiều thẻ (Quét khu, Nhập/Xuất, Đặt vị trí) | Định vị |
|---|---|---|---|
| **Bóp 1 lần** (`SINGLE`) | Mỗi lần bóp → đọc **1 thẻ** (burst ~0.6s, lấy RSSI mạnh nhất) | Mỗi lần bóp → **một đợt quét ngắn ~1.5s** rồi tự dừng | (dùng như liên tục) |
| **Quét liên tục** (`CONTINUOUS`) | Bóp lần 1 bắt đầu đọc lặp, bóp lần 2 kết thúc | Bóp lần 1 bắt đầu, **bóp lần 2 kết thúc** | Bóp lần 1 dò, bóp lần 2 dừng |

> Định vị luôn dùng toggle (bóp bật/tắt) vì cần tín hiệu RSSI liên tục.

**Lý do "nhấn giữ" = 2 lần bóp:** vì không có sự kiện thả tin cậy, chế độ liên tục được hiện thực bằng **toggle theo lần bóp** (bóp 1 = bắt đầu, bóp 2 = kết thúc), thay vì bắt đầu-khi-giữ / dừng-khi-thả.

---

## 2. Thông số Chainway ảnh hưởng độ chính xác

Đọc từ SDK `RFIDWithUHFBLE` (`DeviceAPI_ver20251103_release.aar`). App áp các thông số này qua
`ChainwayRfidReader.applyScanConfig(profile)` mỗi khi mở màn chức năng, theo `ScanProfile` đã lưu.

| Thông số | API SDK | Ý nghĩa | Khuyến nghị |
|---|---|---|---|
| **Công suất RF** | `setPower(1..30)` dBm | Knob lớn nhất. Cao = xa/nhiều thẻ; **quá cao → đọc nhầm thẻ lân cận, phản xạ** | Quét đơn: 12–20. Gom khối lượng: 26–30 |
| **Session** | `Gen2Entity.querySession` (0..3) | S0: thẻ trả lời **mọi vòng** (quét đơn/định vị lặp); S1–S2: mỗi thẻ 1 lần/chu kỳ (đếm nhiều) | Quét đơn/định vị: **S0**. Đếm: **S1** |
| **Target** | `Gen2Entity.queryTarget` | Luân phiên nhóm thẻ A/B | App cố định **A** |
| **Q value** | `Gen2Entity.startQ` (0..15) | Số slot chống đụng độ | Ít thẻ → 0–2. Nhiều thẻ → 6–8 |
| **Miller (M)** | `Gen2Entity.queryM` | Mã hoá tín hiệu về: 0=FM0, 1=M2, 2=M4, 3=M8. M cao = **nhạy/chống nhiễu hơn**, chậm hơn | Kho nhiễu cao: M4/M8 |
| **TagFocus** | `setTagFocus(bool)` | Bật: thẻ im sau 1 lần đọc (Impinj). **Hại cho định vị lặp** | Tắt cho định vị/quét đơn |
| **FastID** | `setFastID(bool)` | Đọc kèm TID, chậm hơn | Tắt khi chỉ cần EPC |
| **Chế độ đọc** | `setEPCMode()` | Chỉ đọc EPC, nhanh & ổn hơn | App luôn dùng EPC mode |

### Kỹ thuật phần mềm bổ trợ
- **Quét đơn burst** (`scanBurst`): thay vì `inventorySingleTag()` (1 vòng, dễ trượt), app quét liên tục ngắn rồi lấy thẻ RSSI mạnh nhất → chính xác hơn nhiều cho tra cứu.
- **Định vị bằng inventory + RSSI** (không dùng `startLocation`): lọc EPC mục tiêu trong luồng inventory, đổi RSSI → tín hiệu 0..100, beep nhanh dần khi lại gần.
  - **Dải RSSI đo thực tế trên R6** (FCC/America, 26dBm): **−75 dBm khi xa nhất → −29 dBm khi sát thẻ**. App map đúng dải này ra 0..100 để tín hiệu trải đều hết thang.
  - `getRssi()` trả **String** và **không phải số nguyên thuần** → phải parse qua Double (và fallback hex), nếu dùng `toIntOrNull()` sẽ luôn fail và rơi về giá trị mặc định (bug từng gặp: tín hiệu kẹt cứng).
  - **Beep phải phát bằng loa điện thoại** (`ToneGenerator`/SoundPool), KHÔNG dùng buzzer R6: khi định vị bắt buộc `setBeep(false)` (buzzer kêu với mọi thẻ, không phân biệt được tag mục tiêu), lệnh này tắt luôn `triggerBeep`. Demo hãng cũng làm vậy.
  - Nhịp beep theo demo hãng: `>85→5ms`, `>66→100−s`, `>33→(100−s)×2`, còn lại `(100−s)×3` (app đặt sàn 45ms).

### Ngoài phần mềm
- Anten phân cực tròn: quét **vuông góc** mặt thẻ cho tín hiệu tốt nhất.
- Thẻ trên kim loại/chất lỏng cần thẻ **on-metal** chuyên dụng.
- Nếu hay đọc nhầm thẻ bên cạnh khi tra cứu đơn → **giảm công suất xuống 12–15**.

---

## 3. Mặc định theo chức năng trong app

| Chức năng | Chế độ cò | Power | Session | Q |
|---|---|---|---|---|
| Tra cứu | Bóp 1 lần | 16 | S0 | 2 |
| Gán tag | Bóp 1 lần | 12 | S0 | 0 |
| Tìm sản phẩm (định vị) | Liên tục | 26 | S0 | 4 |
| Quét theo khu | Liên tục | 30 | S1 | 6 |
| Nhập / Xuất | Liên tục | 30 | S1 | 6 |
| Đặt vị trí | Liên tục | 26 | S1 | 4 |

Tất cả chỉnh được và lưu bền vững (DataStore); có nút **Đặt mặc định** để khôi phục.

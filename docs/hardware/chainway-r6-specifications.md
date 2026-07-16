# Chainway R6 — Thông số kỹ thuật thiết bị

> Đầu đọc UHF RFID Bluetooth dùng cho app KOIStock. SDK: `RFIDWithUHFBLE` (BLE).
> Xem thiết kế: [2026-07-15-koistock-warehouse-design.md](../superpowers/specs/2026-07-15-koistock-warehouse-design.md)

---

# THÔNG SỐ KỸ THUẬT SẢN PHẨM (SPECIFICATIONS)

## Đặc tính Vật lý (Physical Characteristics)

* **Kích thước (Dimensions):** 151 x 76 x 132 mm / 5.94 x 2.99 x 5.19 in.
* **Trọng lượng (Weight):**
    * Cấu hình RFID: 438 g (bao gồm pin)
    * Cấu hình RFID + Đầu đọc (Engine): 446 g (bao gồm pin)
* **Màu sắc (Colour):** Đen (Black)
* **Nguồn điện (Power):**
    * Thông số pin: Pin rời 5200 mAh (removable battery)
    * Thời gian chờ (Standby): Trên 70 giờ (duy trì kết nối bluetooth)
    * Sử dụng liên tục (Continuous use): Lên đến 2.5 giờ (đọc UHF: 400 thẻ)
    * Thời gian sạc (Charging time): 4-5 giờ (với bộ chuyển đổi tiêu chuẩn và cáp USB)
* **Cổng giao tiếp (Interfaces):** USB 3.0 Type-C
* **Còi báo (Buzzer):** Hỗ trợ (Support)
* **Đèn LED chỉ báo (Indicator LED):** Nguồn (Power), Trạng thái hoạt động (Work), Bluetooth
* **Chất liệu sản phẩm (Product material):** Nhựa ABS, Hợp kim nhôm (Aluminum Alloy)
* **Kích thước điện thoại tương thích (Size of adapted phone):**
    * Đầu đọc dạng kẹp (Sled Reader): Chiều rộng 68~80 mm, độ dày trong vòng 22 mm
    * Khác: Dựa trên ốp lưng hoặc giá đỡ điện thoại

---

## Môi trường Sử dụng (User Environment)

* **Nhiệt độ hoạt động (Operating Temp.):** -4°F đến 122°F / -20°C đến 50°C
* **Nhiệt độ bảo quản (Storage Temp.):** -40°F đến 158°F / -40°C đến 70°C
* **Độ ẩm (Humidity):** 5%RH - 95%RH không ngưng tụ
* **Tiêu chuẩn chống va đập khi lăn (Tumble Specification):** 1000 lần x 0.5 m / 1.64 ft rơi ở nhiệt độ phòng
* **Tiêu chuẩn rơi (Drop Specification):** Rơi nhiều lần từ độ cao 1.5 m / 4.92 ft xuống nền bê tông trong phạm vi nhiệt độ hoạt động
* **Chuẩn bảo vệ (Sealing):** IP54 theo tiêu chuẩn niêm phong IEC
* **Chống tĩnh điện (ESD):** ±15 KV xả qua không khí (air discharge), ±8 KV xả trực tiếp (conductive discharge)

---

## Môi trường Phát triển (Developing Environment)

* **SDK:** Android / iOS
* **Công cụ phát triển (Tool):** Android Studio / Xcode
* **Ngôn ngữ lập trình (Language):** Java / Objective-C

---

## Giao tiếp & Truyền dữ liệu (Communication)

* **Bluetooth:** Bluetooth tiết kiệm năng lượng (BLE) 5.0

---

## Thu thập Dữ liệu (Data Collection)

### Tần số siêu cao (UHF)

* **Đầu đọc (Engine):** Dựa trên chip Impinj E Series
* **Giao thức (Protocol):** EPC C1 GEN2 / ISO18000-6C
* **Impinj Gen2X:** Được hỗ trợ (Supported)
* **Dải tần số và Công suất phát tối đa của hệ thống (Frequency Range and Maximum System Output Power):**
    * **FCC:** 902 MHz – 928 MHz; 36 dBm (EIRP)
    * **EU:** 865.6 MHz - 867.6 MHz; 35 dBm (EIRP)
    * **CH:** 920.5 MHz - 924.5 MHz; 35 dBm (EIRP)
* **Công suất phát (Output Power):** 1-30, bước điều chỉnh 1dB
* **Thông số Anten (Antenna parameter):** Anten phân cực tròn (Circular Polarized Antenna)
* **Phạm vi đọc tối đa (Max Read Range):** > 26 m (với thẻ Impinj M830)
* **Tốc độ đọc nhanh nhất (Fastest Read Rate):** 1000+ thẻ/giây (tags/sec)

*\* Ghi chú: Các phạm vi được đo lường trong môi trường ngoài trời thoáng đãng và ít nhiễu. Tỷ lệ thực tế được đo trong phòng thí nghiệm ít nhiễu, các kết quả này bị ảnh hưởng bởi loại thẻ và môi trường xung quanh.*

### Quét mã vạch (Barcode Scanning) - *Tùy chọn (Optional)*

* **Đầu quét mã vạch 2D (2D Imager Scanner):** SE2707
* **Hỗ trợ mã vạch 1D (1D Symbologies):** UPC/EAN, Code128, Code39, Code93, Code11, Interleaved 2 of 5, Discrete 2 of 5, Chinese 2 of 5, Codabar, MSI, RSS, v.v.
* **Hỗ trợ mã vạch 2D (2D Symbologies):** PDF417, MicroPDF417, Composite, RSS, TLC-39, Datamatrix, QR code, Micro QR code, Aztec, MaxiCode; Mã bưu điện (Postal Codes): US PostNet, US Planet, UK Postal, Australian Postal, Japan Postal, Dutch Postal (KIX), v.v.

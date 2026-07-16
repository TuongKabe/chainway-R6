# Chainway R6 Setup Guide for KOIStock

## Muc tieu

Tai lieu nay dung de noi team setup nhanh KOIStock voi Chainway R6 trong giai doan chua gan `google-services.json` va chua hook `ChainwayRfidReader` that.

## Checklist

1. Bat Bluetooth tren Android phone/tablet.
2. Bat nguon Chainway R6 va dua ve che do BLE.
3. Cap quyen Bluetooth / Location cho app theo phien ban Android.
4. Mo man `Ket noi R6` trong KOIStock.
5. Bam `Tim thiet bi`, chon dung ten hoac MAC cua R6.
6. Sau khi hook reader that, verify lai:
   - ket noi va mat ket noi
   - auto reconnect theo MAC da luu
   - inventory loop
   - scan single
   - write EPC
   - battery percent

## Tinh trang hien tai

- App da co shell va huong dan ket noi trong UI.
- `PlaceholderRfidReader` dang duoc dung de phat trien app shell.
- `google-services.json` se bo sung sau.
- `ChainwayRfidReader` se bo sung sau.

## Khi bo sung reader that

- Tao `ChainwayRfidReader` implement `RfidReader`.
- Dien callback BLE scan va connect state.
- Map callback inventory sang `SharedFlow<ScannedTag>`.
- Ghi ket qua hardware spike vao `docs/hardware/chainway-r6-sdk-spike.md`.

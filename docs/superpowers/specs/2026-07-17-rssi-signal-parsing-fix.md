# RSSI signal parsing fix

## Problem

While locating a product, the raw RSSI text changes but the large signal gauge remains at `10/100`. The current parser returns `DEFAULT_RSSI = -70` whenever it cannot parse the SDK string, and the locate mapping converts `-70 dBm` to `10`. This hides parse failures as a plausible fixed signal.

## Design

- Keep the existing inventory-based locating flow and exact EPC filtering.
- Enable RSSI reporting through `RFIDWithUHFBLE.setSupportRssi(true)` after a successful connection and before inventory configuration is applied.
- Move RSSI text conversion into a small testable parser in the device layer.
- Accept signed or unsigned integers, decimals, hexadecimal bytes, and numeric values decorated with common text such as `RSSI:` or `dBm`.
- Convert positive magnitudes to negative dBm, round decimals to the nearest integer, and clamp the result to `-100..0`.
- Preserve a safe fallback for missing or unsupported input, but expose parser behavior through tests so valid changing input cannot silently collapse to the fallback.

## Data flow

`UHFTAGInfo.rssi` is preserved as `rawRssi`, parsed into `ScannedTag.rssi`, filtered by target EPC in `LocateViewModel`, converted from the measured `-75..-29 dBm` range to `0..100`, and displayed by `SignalGauge`.

## Error handling

Null, blank, or strings without a numeric/hex value use the existing `-70 dBm` fallback. Valid SDK representations must not use the fallback. No backend or UI contract changes are required.

## Tests

- Add parser tests for signed decimal, positive magnitude, decimal with suffix, hexadecimal byte, and invalid input.
- Verify two changing decorated RSSI inputs produce distinct parsed values.
- Run device parser tests, `LocateViewModelTest`, and the complete debug unit-test suite.

## Scope

The change is limited to RSSI enablement and conversion. It does not replace inventory with the SDK location API, recalibrate the measured RSSI range, or redesign the locate screen.

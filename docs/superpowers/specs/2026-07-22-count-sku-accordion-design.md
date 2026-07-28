# Count SKU Accordion Design

## Goal

Improve the "Quét theo khu" scan result so operators can review scanned SKUs quickly without leaving the count workflow. The result uses the same product information as "Quản lý kho", but presents it as a compact accordion list.

## Scope

This change affects only the scanned-SKU result area in `CountScreen` and the count UI state needed to supply product details. Existing zone entry, scanning, reconciliation, count saving, and CSV export behavior remain unchanged.

## Layout and interaction

The screen continues to show the zone selector and scan action first. Beneath them, one summary line shows:

- Total unique SKUs scanned.
- Total unique tags scanned across those SKUs.

Below the summary, each scanned SKU appears as a compact card. Its collapsed header shows the product name, SKU, and scanned tag quantity. A trailing expand/collapse indicator communicates that the row is interactive.

Tapping a collapsed card expands details directly inside that card. Tapping it again collapses the card. Only one SKU may be expanded at a time; opening another SKU closes the previous one.

The expanded section mirrors the information available in "Quản lý kho":

- SKU.
- Product name.
- Current warehouse quantity and unit.
- Tracking mode (serialized or bulk).
- Default location rendered as parent zone to shelf when location data is available.

If a field cannot be resolved from synchronized warehouse data, the row shows a concise fallback such as "Chưa có vị trí mặc định" rather than hiding the field or failing the list.

Existing reconciliation results remain a separate section below the count actions. The accordion list does not duplicate reconciliation status, expected quantity, or variance.

## Components and data flow

`CountViewModel` remains responsible for deduplicating EPCs and grouping scanned tags by SKU. It will expose scan-result rows that combine the counted quantity with the corresponding `Product` data. Location nodes needed to render zone and shelf labels are supplied through the existing synchronized repository flow rather than copied into a new data source.

`CountScreen` owns the transient expanded-SKU selection because expansion is presentation state. A focused accordion-row composable renders the collapsed header and expanded product details. Stable SKU keys preserve expansion behavior while scan results update.

## State changes during scanning

Starting a new scan clears the prior scanned-SKU results, so any expanded SKU is also cleared when it is no longer present. New unique EPCs update the relevant SKU count without automatically opening or closing a valid expanded row.

The summary always derives from the same scan-result state as the list, ensuring the displayed SKU and tag totals cannot disagree with the visible rows.

## Error and empty states

- Before a tag is resolved, the list displays the existing empty-state guidance.
- EPCs that do not resolve to a known tag or product continue to be excluded, matching current count behavior.
- Missing location metadata does not block a scanned SKU from appearing.
- Product and location details are read-only in this workflow; editing remains in "Quản lý kho".

## Testing

- ViewModel tests verify that scanned EPCs remain unique, are grouped by SKU, and expose matching product details and totals.
- Presentation-state tests verify that tapping a row expands it, tapping again collapses it, and opening a second row closes the first.
- UI/content tests verify collapsed rows show product name, SKU, and scanned tag count; expanded rows show quantity/unit, tracking mode, and zone-to-shelf location.
- Existing count reconciliation, save, CSV, trigger, and verified-device-configuration tests remain passing.

## Out of scope

- Editing product details from the count screen.
- Showing expected quantity, variance, or reconciliation status inside the SKU accordion.
- Supporting multiple simultaneously expanded rows.
- Changing scan deduplication, persistence, CSV format, or backend contracts.

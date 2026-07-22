# Location-Scoped Stock Reconciliation Design

## Goal

Make "Quét theo khu" use each EPC as a representative marker for its SKU, then display authoritative DB stock for that SKU at the selected scope. With no location selected, reconciliation defaults to the entire warehouse instead of producing invalid comparisons from an empty location string.

## Root cause

The current app builds `ExpectedItem` from `Product.quantity` and one representative `Product.locationCode`. `Product.quantity` is aggregated across all bins or all active tags, so assigning that total to one location is incorrect whenever a SKU exists in multiple locations.

The screen also filters with `homeLocation.startsWith(zoneInput.substringBefore('-'))`. When `zoneInput` is blank, every string starts with the empty string, so all products enter a reconciliation whose location is `""`. This produces invalid misplaced/extra results before a location is selected.

## Scope semantics

The count screen supports three inventory scopes:

- **Entire warehouse:** blank location input. Expected quantity is aggregated across every location. Results use Match, Missing, and Extra; Misplaced is not meaningful at this scope.
- **Parent zone:** a synchronized `LocationNode` of type `ZONE`. Expected quantity includes that zone and every descendant shelf resolved through `LocationNode.parent` relationships.
- **Shelf:** a synchronized `LocationNode` of type `SHELF`. Expected quantity includes only that exact shelf.

Location membership is resolved from synchronized location nodes, never from string prefixes. A code such as `A` does not implicitly include `A-03` unless `A-03.parent == "A"`.

## Authoritative inventory

The expected snapshot comes directly from backend DB APIs:

- **BULK products:** sum `BinDto.actualQty` for bins whose `warehouse` belongs to the selected scope.
- **SERIALIZED products:** count `EpcTagDto` records with `status == "active"` whose warehouse/location belongs to the selected scope.
- **Entire warehouse:** sum all DB bins for BULK and count all active tags for SERIALIZED, including active serialized tags without a location so the whole-warehouse total matches inventory ownership.

Product master data supplies SKU, name, unit, and tracking mode only. `Product.quantity` and `Product.locationCode` are not used to construct location-scoped expected quantities.

Expected rows with quantity zero are omitted. A scanned SKU without positive expected quantity in the selected scope is Extra for whole-warehouse reconciliation, or Misplaced for a specific zone/shelf when it exists elsewhere in the DB snapshot.

## Representative-tag reconciliation

One SKU may have only one active EPC, so a scanned tag represents the presence of that SKU rather than one physical stock unit. The app must not subtract one scanned tag from a DB stock quantity.

For example, when one EPC for `SKU1` is scanned and DB stock at `A-03` is `10`, the result shows `1 tag đã quét` and `Tồn DB: 10`; it does not report `Thiếu 9`.

Status rules are:

- Positive DB stock in scope and representative tag scanned: Match.
- Positive DB stock in scope and no representative tag scanned: Missing.
- Representative tag scanned, no positive DB stock anywhere: Extra.
- Representative tag scanned outside the selected zone/shelf while the SKU has positive DB stock elsewhere: Misplaced.
- Whole-warehouse scope never emits Misplaced.

The row keeps scanned-tag count and DB stock quantity as separate values. UI and CSV labels must not imply that they are the same unit of measure.

## Components and data flow

A focused inventory-expectation repository loads active products, bins, tags, and locations from the backend and publishes one immutable snapshot. The snapshot exposes a pure function that produces `ExpectedItem` rows for an entire-warehouse, zone, or shelf scope.

`CountViewModel` owns the selected scope and reconciliation loading/error state. Pressing "Đối chiếu" requests expected items for the current scope and passes them to `CountReconciler`. `CountScreen` no longer filters `ExpectedItem` values or accepts a list derived by `AppShell`.

`CountReconciler` receives an explicit scope kind, scoped DB stock, global inventory ownership, and scanned representative-tag presence. It assigns status from presence/location rules instead of comparing tag count numerically with DB quantity. Whole-warehouse results never emit Misplaced; specific-location behavior retains Misplaced when a scanned SKU has stock outside the selected scope.

The expected snapshot is refreshed from DB when the user runs reconciliation. A failed refresh does not reuse a silently empty or stale list; the screen reports the failure and leaves the prior reconciliation result unchanged.

## UI behavior

- Empty location input is labeled and interpreted as "Toàn khu".
- The reconciliation action remains available when the input is empty.
- While DB inventory is loading, the action is disabled and shows progress.
- A load failure appears as a clear non-success message.
- The result section states the applied scope so operators can distinguish whole-warehouse results from a zone or shelf.
- Each result row displays scanned tag count and DB stock as distinct fields, for example `1 tag · Tồn DB 10 cái`.

Existing scanned-SKU accordion, scanning, count saving, and CSV export behavior remain unchanged.

## Error handling

- Unknown nonblank location code: stop and ask the operator to select or enter a synchronized zone/shelf.
- Invalid `actualQty`: report inventory data failure rather than converting it silently to zero.
- Product, bin, tag, or location API failure: fail reconciliation and make no result replacement.
- Active tag with no location: included only in entire-warehouse totals; excluded from specific zone/shelf totals.
- Multiple bin rows for one SKU/location: sum them.

## Testing

- Blank input builds whole-warehouse expected totals.
- BULK totals are summed from all bins for whole warehouse, descendant bins for a parent zone, and one bin for a shelf.
- SERIALIZED totals count only active tags and follow the same scope rules.
- Parent-zone membership uses `LocationNode.parent`, not code prefixes.
- Active serialized tags without location count only at whole-warehouse scope.
- Invalid nonblank location and invalid quantity fail without replacing prior results.
- Whole-warehouse reconciliation never returns Misplaced.
- Specific location reconciliation retains Misplaced for inventory owned elsewhere.
- One scanned representative tag with DB stock greater than one is Match, not Missing.
- Result formatting keeps tag count separate from DB stock quantity.
- Existing scan aggregation, accordion, save, CSV, trigger, and device-configuration tests remain passing.

## Out of scope

- Changing physical scan behavior or EPC deduplication.
- Writing corrected inventory back to DB.
- Inferring location hierarchy from naming conventions.
- Google Sheet removal; that separate request remains paused pending final scope confirmation.

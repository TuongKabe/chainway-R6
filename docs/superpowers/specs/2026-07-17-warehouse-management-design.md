# Warehouse Management Design

## Goal

Add a single warehouse-management destination that lets users search, filter, inspect, and edit SKU data; manage zones and shelves; and reuse synchronized data instead of repeatedly typing codes. Synchronization runs when the app opens and after every successful save.

## Scope

The destination contains two tabs: Products and Zones/Shelves. SKU, zone code, and shelf code are immutable during editing. This iteration does not add a category entity or offline database; product filtering uses fields already available from the backend.

## Shared data and synchronization

`AppShell` owns the shared product and location repositories and their observable state. Both tabs consume these flows rather than maintaining independent copies.

On app startup:

1. Run backend reconciliation with Google Sheet.
2. Refresh products and locations.
3. Publish refreshed lists through the existing repository flows.

After a successful product, zone, or shelf upsert:

1. Preserve the submitted form state until the save result is known.
2. Run backend reconciliation.
3. Refresh products and locations.
4. Close/reset the form only after the save succeeds.

If the upsert fails, the form remains populated and reports a save error. If the upsert succeeds but reconciliation or refresh fails, report that data was saved but synchronization failed; do not ask the user to re-enter the data.

## Products tab

### List and details

The list shows SKU, product name, current quantity, unit, tracking mode, and the default shelf rendered with its parent zone when available. Selecting a row opens an edit form prefilled from the selected `Product`.

SKU is read-only. Editable fields are name, unit, tracking mode, default shelf, and image URL. Tracking mode uses a fixed selector. Default location uses a shelf selector grouped by parent zone; arbitrary location text is not accepted. Undo restores all fields to the last loaded product.

### Search and filters

Search matches SKU or product name case-insensitively. Filters may be combined:

- Tracking: all, serialized, or bulk.
- Stock: all, in stock, or out of stock.
- Unit: values derived from the current product list.
- Zone: values derived from synchronized zone nodes.
- Shelf: values derived from synchronized shelves and narrowed by the selected zone.

Active filters appear as removable chips with a clear-all action. Search and filter state survive navigation to details and back, but are not persisted across app restarts.

No product-category field or category-management API is introduced in this iteration.

## Zones/Shelves tab

Locations render as groups of parent zones with child shelves. Search matches location code or name.

Adding or editing a zone uses a prefilled form. Adding or editing a shelf uses a prefilled form whose parent-zone field is a selector populated only with synchronized nodes of type `ZONE`. Users cannot type an arbitrary parent code.

Validation rejects blank codes for new records, blank names, duplicate codes, missing parent zones, and shelf parents that are not zones. Codes remain immutable while editing so existing product, tag, and transaction references are not broken. After adding a zone, that zone becomes the default selection when the user proceeds to add a shelf.

## Reuse in existing workflows

The Putaway screen replaces manual location-code entry with the synchronized shelf selector. It may display zone and shelf labels, but sends the existing shelf code through current repository contracts. Other workflows keep their current behavior in this iteration.

## Components

- `WarehouseManagementScreen`: tab container and shared sync/save feedback.
- `ProductManagementViewModel`: search, filters, selection, validation, save, reconcile, and refresh.
- `ProductManagementScreen`: list, filter controls, details, and edit form.
- `ZoneViewModel`: extended with edit validation and save/reconcile/refresh results.
- `ZoneScreen`: grouped location list and data-backed parent selector.
- Reusable `ShelfSelector`: consumes `LocationNode` values and returns a shelf code.
- `PutawayScreen`: uses `ShelfSelector` instead of free-text location input.

## Data contracts

Existing `Product`, `LocationNode`, `ProductRepo`, and `LocationRepo` contracts remain the source of truth. Repository refresh methods are exposed through focused refresh/sync coordination rather than duplicating HTTP calls in composables. Existing upsert endpoints remain unchanged.

## Error states

- Initial load failure: show an explicit retry action instead of an empty-list message.
- Save failure: retain form input and display the backend error.
- Saved but sync failed: report partial success and allow retry without resubmitting the edit.
- Stale shelf selection: block save and ask the user to choose an existing synchronized shelf.
- Empty data: distinguish a successful empty response from a failed load.

## Testing

- Product filtering: individual filters, combined filters, case-insensitive search, zone-to-shelf narrowing, and clear-all.
- Product editing: immutable SKU, prefilled fields, undo, required fields, stale shelf rejection, save success, save failure, and saved-but-sync-failed.
- Location management: duplicate code, immutable code, valid zone selector, invalid parent rejection, grouped ordering, and selecting a newly created zone for a new shelf.
- Putaway: only synchronized shelves can be selected and the selected shelf code reaches the existing ViewModel.
- Integration wiring: startup reconciliation/refresh and post-save reconciliation/refresh.

## Out of scope

- Renaming SKU, zone codes, or shelf codes.
- Product-category entities or backend schema changes.
- Background periodic synchronization.
- Offline persistence and conflict-resolution queues.
- Deleting products or locations.

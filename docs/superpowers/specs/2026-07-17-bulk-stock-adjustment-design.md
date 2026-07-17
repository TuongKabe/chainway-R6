# Bulk Stock Adjustment Design

## Problem

Warehouse product rows display quantity, but the product editor has no quantity input. `HttpProductRepository.upsert()` sends item master data only; quantity is reconstructed during refresh from active EPC tags for serialized products or warehouse-bin balances for bulk products. Editing `Product.quantity` through item upsert would therefore be discarded on the next synchronization.

## Design

Serialized products keep quantity read-only because their quantity is the count of active EPC tags. The editor explains that quantity changes through tag assignment and tag status workflows.

Bulk products show an `Điều chỉnh tồn kho` section. The user enters the desired physical quantity, not a signed delta. The app validates a whole number greater than or equal to zero, calculates `delta = desiredQuantity - currentQuantity`, displays the before/after values, and requires confirmation before submission.

If `delta == 0`, no command is sent and the UI reports that inventory is already correct. Otherwise the app sends one stock command with type `ADJUST`, the product SKU, calculated delta, selected synchronized shelf, device ID, and a unique command ID. After a successful command, it runs the shared warehouse synchronization and refresh flow.

## Location

The adjustment uses the product's currently selected/default synchronized shelf. Save is blocked when the shelf is blank, missing, or not a `SHELF`. Users select another valid shelf through the existing `ShelfSelector`; no location code is typed manually.

## Results and errors

- Command failure: retain the entered quantity and show the backend reason.
- Command success and sync success: close confirmation, refresh the product list, and show the new quantity.
- Command success but sync/refresh failure: report partial success and keep enough state to retry synchronization without sending the stock adjustment again.
- Serialized product: never expose an editable quantity field.

## Components

- Extend `ProductManagementViewModel` with adjustment state and `adjustBulkQuantity()`.
- Inject `StockCommandRepo`, device ID, command-ID generator, and shared sync callback.
- Extend `ProductManagementScreen` with a read-only serialized quantity message or bulk adjustment input and confirmation dialog.
- Reuse `StockMovement`, `CommitStockResult`, and `ShelfSelector`.

## Tests

- Serialized products reject manual quantity adjustment.
- Negative, decimal, and nonnumeric desired quantities are rejected.
- Zero delta sends no command.
- Positive and negative deltas produce one `ADJUST` command with the correct SKU, shelf, and delta.
- Command failure retains input and does not synchronize.
- Command success synchronizes exactly once.
- Partial sync failure does not resend the adjustment when synchronization is retried.

## Out of scope

- Editing EPC-derived serialized quantity.
- Deleting or deactivating tags from the product editor.
- Multi-shelf allocation in a single adjustment.
- Direct mutation of `Product.quantity` through item upsert.

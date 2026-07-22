# Assign Success Snackbar Design

## Goal

Make successful tag assignment feedback non-blocking. A successful result appears as a short Material 3 Snackbar instead of an `AlertDialog` with an "Đã hiểu" button.

## Behavior

- `AssignResult.Success` is formatted as a concise message and shown in a Snackbar.
- The Snackbar has no action button and dismisses automatically after the standard short duration.
- After the success result is handed to the Snackbar, the result is acknowledged so recomposition or navigation does not show it again.
- A web-session success says the EPC was sent for the session SKU; a local success says the EPC was assigned to the SKU.
- Barcode and optional backend notes are omitted from the Snackbar to keep it compact.

`AssignResult.PartialSuccess` and `AssignResult.Error` continue to use the blocking result dialog. SKU/EPC uniqueness conflicts continue to use their dedicated dialog and "Quản lý SKU đang giữ EPC" action.

## Components and state

`AssignTagScreen` owns a `SnackbarHostState` and renders it through `SnackbarHost`. A `LaunchedEffect` keyed by a successful result calls `showSnackbar`, then calls `AssignTagViewModel.acknowledgeResult()`.

A pure formatter converts `AssignResult.Success` to the exact Snackbar copy so local and web-session wording can be unit tested without Compose instrumentation. The existing result dialog accepts only partial-success and error results after the split.

## Error handling

- A new success arriving while another Snackbar is visible follows `SnackbarHostState` queue behavior.
- Partial success and errors are never downgraded to Snackbar messages.
- Dismissing or navigating away cancels the screen coroutine naturally; no success is persisted for replay after it has been acknowledged.

## Testing

- Local assignment success formatting includes EPC and SKU.
- Web-session success formatting includes EPC and SKU and uses web wording.
- Partial-success and error result types remain routed to the dialog.
- Existing auto-assignment, uniqueness, manual assignment, and warehouse-navigation tests remain passing.

## Out of scope

- Changing conflict dialogs.
- Changing partial-success or error dialogs.
- Adding Snackbar actions or retry buttons.
- Changing ViewModel assignment logic or backend calls.

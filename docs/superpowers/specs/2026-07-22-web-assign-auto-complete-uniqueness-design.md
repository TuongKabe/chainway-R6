# Web Assign Auto-Complete and SKU–EPC Uniqueness Design

## Goal

When Android has an active assign session from the webapp, scanning an EPC automatically validates and completes the session without requiring a confirmation button. The app blocks conflicts before sending anything to the web and enforces the workflow rule that one SKU may have at most one active EPC.

## Scope

This change covers the Android assign-session workflow, its conflict presentation, and navigation to the product that owns a conflicting EPC. It does not automatically replace or void an existing EPC, and it does not change backend schemas or add a server-side unique constraint.

## Automatic workflow

When a web assign session is loaded and the reader produces an EPC:

1. The app normalizes and captures the EPC once.
2. The app loads the active mapping for that EPC.
3. The app loads active mappings for the session SKU.
4. The app evaluates conflicts before calling either assign-session endpoint.
5. If validation passes, the app calls `submitScan` and then `confirm` automatically.
6. A successful confirmation updates the displayed session, beeps once, and reports completion.

The existing "Gửi tag và hoàn tất web" action is removed. Manual scanning without an active web session continues to populate the EPC for the existing local assign form and does not submit anything to the web.

Repeated scan events for the same EPC while validation or submission is in progress are ignored. This prevents duplicate endpoint calls from continuous trigger mode or reader callbacks.

## Uniqueness rules

Only mappings with `status == "active"` participate in uniqueness checks.

- If the scanned EPC has no active mapping and the session SKU has no active EPC, validation passes.
- If the scanned EPC is already active for the session SKU, validation passes so the current web session can be completed idempotently.
- If the scanned EPC is active for another SKU, validation fails.
- If the session SKU already has a different active EPC, validation fails.
- Voided or otherwise inactive mappings do not block assignment.

If repository lookup fails, validation fails closed: the app reports that it could not verify the assignment and does not send the EPC to the web.

## Conflict experience

On a uniqueness conflict, the app does not call `submitScan` or `confirm`. It keeps the existing mapping unchanged and shows:

- The conflict reason.
- Product name and SKU for the SKU that currently owns the relevant EPC.
- The active EPC that caused the conflict.
- A single primary action: "Quản lý SKU đang giữ EPC".

The action navigates to "Quản lý kho" and opens the matching SKU's existing product editor. The conflict screen itself never unlinks, replaces, voids, or rewrites an EPC. After reviewing or editing the SKU, the operator returns to the assign workflow and scans again.

If product details cannot be loaded, the conflict still shows the SKU code and EPC, and navigation uses the SKU code.

## Components and state

`AssignTagViewModel` owns validation, duplicate-scan suppression, automatic submission, and a structured conflict state. Validation is expressed as a focused result type so its rules can be unit tested independently from Compose.

`AssignTagScreen` observes the conflict state, removes the manual web-completion button, and renders the conflict information and management action.

`AppShell` receives the requested management SKU from `AssignTagScreen`, navigates to the warehouse destination with that SKU, and passes it to `ProductManagementViewModel.selectProduct` after products are available. Normal navigation to "Quản lý kho" without a SKU remains unchanged.

The repository contracts remain unchanged: `TagRepo.getByEpc` checks EPC ownership and `TagRepo.listBySku` checks the one-active-EPC rule.

## Error handling

- No active web session: scanning remains local and does not auto-submit.
- No readable EPC: keep the existing scan error.
- Validation lookup failure: show a verification error and do not call web endpoints.
- `submitScan` failure: show its backend error and leave the session available for retry.
- `confirm` failure after a successful submit: retain partial-success reporting because the web received the EPC but did not complete the session.
- Session changes while an operation is running: the in-flight operation finishes against the captured session ID; later scans use the latest loaded session.

## Testing

- Auto-submit and confirm after a valid scan with an active session.
- No endpoint calls when there is no active session.
- EPC already mapped to the same session SKU is allowed and completes the session.
- EPC mapped to another SKU produces a conflict and makes no endpoint calls.
- Session SKU with another active EPC produces a conflict and makes no endpoint calls.
- Inactive mappings do not block assignment.
- Repository lookup failure fails closed.
- Repeated EPC events while working produce only one submit/confirm sequence.
- Conflict state contains the owner SKU, product details when available, and the conflicting EPC.
- Management action navigates to the warehouse product editor for the conflict SKU.
- Existing local/manual tag assignment behavior remains passing.

## Server integrity limitation

Android preflight validation prevents conflicts in this device workflow but cannot guarantee global uniqueness when multiple clients assign concurrently. Absolute enforcement requires the backend to perform an atomic uniqueness check or maintain a unique active-SKU-to-EPC constraint. That backend change is outside this Android-only scope.

## Out of scope

- Automatically voiding or replacing an existing EPC.
- Editing tag mappings inside the conflict dialog.
- Adding backend transactions or database uniqueness constraints.
- Changing the webapp's session creation workflow.

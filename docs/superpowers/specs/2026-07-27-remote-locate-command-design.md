# Remote Locate Command Design

**Date:** 2026-07-27  
**Status:** Approved for planning

## Goal

Allow a separate Next.js website to send a product SKU to one fixed KOIStock Android device. If KOIStock is in the foreground, it immediately opens the existing Locate flow with that product selected. If the app is backgrounded or stopped, Android shows a notification; tapping it opens the same Locate flow. The user must still press **Bắt đầu dò** before RFID inventory begins.

## Scope

This feature covers the command path from the website to the Android app, command deduplication, navigation to the requested SKU, and user-visible error states. It does not automatically start RFID scanning, introduce multi-device selection, or redesign the existing Locate screen.

## Architecture

Use Supabase as the durable command record and foreground realtime channel, with Firebase Cloud Messaging (FCM) as the background delivery channel.

1. The fixed Android device registers its current FCM token under a stable logical device ID.
2. The website calls a server-side Next.js API route with a SKU. Browser code never receives Supabase service credentials or FCM credentials.
3. The API validates the SKU, inserts one `locate_commands` row, and sends an FCM data message containing the same command ID and SKU.
4. A foreground app receives the inserted row through Supabase Realtime and opens Locate immediately.
5. A background or stopped app receives an Android notification. Tapping it opens Locate with the SKU.
6. Both delivery paths feed one Android command handler, which deduplicates by command ID and rejects stale commands.

Supabase remains the audit trail and source for command identity. FCM is only a delivery mechanism; it does not become the source of truth.

## Supabase Data Model

### `devices`

- `id text primary key`: stable logical ID for the single KOIStock reader device.
- `fcm_token text not null`: current Firebase registration token.
- `updated_at timestamptz not null default now()`.
- `enabled boolean not null default true`.

The Android app upserts its token at launch and whenever Firebase rotates it. The deployment config supplies the fixed device ID; it is not chosen from the web UI.

### `locate_commands`

- `id uuid primary key default gen_random_uuid()`.
- `device_id text not null references devices(id)`.
- `sku text not null`.
- `created_at timestamptz not null default now()`.
- `expires_at timestamptz not null`.
- `created_by uuid null`: authenticated web user when available.
- `status text not null default 'pending'`, constrained to `pending`, `opened`, `rejected`, or `expired`.
- `opened_at timestamptz null`.
- `failure_reason text null`.

`expires_at` is set to five minutes after creation. Realtime is enabled for inserts on this table. Row-level security permits the authenticated Android identity to read commands only for its configured device ID. Creation occurs only through the trusted Next.js server route.

## Next.js API Contract

`POST /api/locate`

Request:

```json
{ "sku": "SKU-123" }
```

Server behavior:

1. Require the existing website authentication and authorization used for warehouse actions.
2. Trim the SKU, preserve its canonical stored form, and verify that it exists and has at least one active RFID tag.
3. Resolve the configured fixed device and require it to be enabled with a registered FCM token.
4. Insert the command with a five-minute expiry.
5. Send an FCM data notification with `commandId`, `sku`, and `expiresAt`.
6. Return the created command even if FCM delivery fails, because an online foreground app may still receive it through Realtime. Log and expose the push warning without deleting the command.

Success response:

```json
{
  "commandId": "uuid",
  "sku": "SKU-123",
  "status": "pending",
  "pushAccepted": true
}
```

Expected failures use explicit HTTP statuses: `400` invalid SKU, `401/403` unauthorized, `404` unknown SKU or no active tag, `409` device disabled/unregistered, and `500` command creation failure.

The web button disables while the request is pending and then reports either “Đã gửi đến máy tìm kho” or the returned actionable error. It does not wait for the operator to open the app.

## Android Command Handling

Introduce one app-level `RemoteLocateCommand` model and one command coordinator shared by all entry points:

```text
RemoteLocateCommand(commandId, sku, expiresAt)
```

The coordinator:

- validates that the command is not expired;
- ignores a command ID already handled on this installation;
- stops any active locate session before changing products;
- navigates to the existing Locate destination with the requested SKU;
- records the command as handled locally before navigation is emitted;
- best-effort updates Supabase status to `opened`, `rejected`, or `expired`.

Handled command IDs are persisted in a small bounded DataStore collection so process restarts and dual Realtime/FCM delivery cannot reopen the same command. Keeping the newest 100 IDs is sufficient for the fixed-device workflow.

### Foreground delivery

While the authenticated app process is active, a lifecycle-aware Supabase Realtime subscription listens for new commands for the configured device. A valid new command is passed to the coordinator and navigation occurs immediately.

### Background and stopped delivery

A `FirebaseMessagingService` receives the data message and displays a notification containing the SKU. Android does not force-open a full-screen activity for this warehouse action. The notification `PendingIntent` carries the command fields into `MainActivity`; both cold-start intent handling and `onNewIntent` pass them to the same coordinator.

### Locate destination

The Locate route accepts an optional encoded SKU argument. `LocateScreen` retains the requested SKU while its catalog loads. Once ready, it selects the exact SKU case-insensitively and displays the normal tag selector and **Bắt đầu dò** button. It never calls `start()` automatically.

If the SKU is missing from the catalog or has no active tags, the screen shows an actionable message and an option to return to the product picker. If the R6 is disconnected, the existing connection banner remains responsible for prompting connection.

## Command Replacement Rules

- A new, distinct command always wins over the product currently displayed.
- If RFID location is active, the app calls the existing stop operation before switching SKU.
- Duplicate delivery of the same command ID has no visible effect.
- An expired command never changes navigation; it is marked expired when possible.
- Multiple valid commands received in order are processed in order, with the newest command becoming the visible SKU.

## Security

- FCM service-account credentials and the Supabase service role stay only on the server.
- The API route requires authenticated, authorized warehouse users and validates all input server-side.
- The Android client uses only its restricted Supabase identity and Firebase client configuration.
- Realtime policies restrict reads by device ID.
- Logs include command ID, device ID, user ID, and result, but never credentials or FCM tokens.

## Testing

### Android unit tests

- valid command emits navigation to its SKU;
- duplicate command is ignored across coordinator recreation;
- expired command is rejected;
- a new command stops an active locate session;
- requested SKU is selected after asynchronous catalog loading;
- missing SKU and SKU without active tags show the defined error state;
- opening a notification and receiving Realtime for the same ID navigates once.

### Next.js/API tests

- authorization and SKU validation;
- command insertion and five-minute expiry;
- fixed-device lookup;
- FCM payload shape;
- graceful response when command insert succeeds but FCM sending fails;
- no service credentials are bundled into browser code.

### Manual verification

- app foreground: web click opens the requested product immediately without starting RFID;
- app background: notification appears and opens the requested product when tapped;
- app process stopped: notification cold-starts the correct product;
- disconnected R6: requested product opens with the existing connection prompt;
- repeated delivery and rapid consecutive SKUs obey the replacement rules.

## Rollout

Deploy database migration and server API first, then install the Firebase-enabled Android build and confirm token registration, then enable the web button. Monitor command rows and push errors during initial use. The web button remains disabled or reports device-unregistered until the fixed device has successfully registered a token.

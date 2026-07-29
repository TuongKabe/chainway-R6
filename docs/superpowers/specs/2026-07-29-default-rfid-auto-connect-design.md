# Default RFID Auto-Connect Design

## Goal

Reduce the normal app-start connection flow to zero manual steps for the single Chainway R6 reader used with this installation. The app should reuse the last successfully connected Bluetooth MAC address and only ask the user to choose a device when direct reconnection cannot succeed.

## Current State

- `DevicePrefs` already persists the last successfully connected MAC address.
- `ConnectionViewModel.tryAutoReconnect()` already supports a direct connection attempt and refreshes battery and power after success.
- The app requests Bluetooth permissions at launch, but it never invokes the existing auto-reconnect flow.
- Manual pairing remains available through `PairingScreen`.

## Startup Flow

1. `MainActivity` requests the Bluetooth permissions required by the Android version.
2. The app waits until the permission request has completed. It must not call the RFID SDK before this point.
3. The shell performs one startup connection decision per activity creation:
   - If the reader is already connected, no action is taken.
   - If a saved MAC exists, the app attempts a direct connection without scanning.
   - If no saved MAC exists, the attempt is considered unsuccessful without calling the reader.
4. On success, the user remains on the dashboard. Battery and power values are refreshed through the existing connection logic.
5. On failure, the app navigates to the pairing screen and starts a device scan automatically.

The startup flow must execute at most once per `MainActivity` creation. Connection-state recompositions or navigation changes must not cause repeated direct connection attempts or navigation loops.

## Pairing and Default Device

The existing manual pairing behavior remains the source of truth for selecting the default reader. Every successful manual connection saves that reader's MAC address, replacing the previous value. No additional device picker setting or multi-device history is introduced.

When startup fallback opens the pairing screen, scanning begins automatically. The existing scan button remains available so the user can retry if the reader was initially powered off or out of range.

## UI Behavior

- While the direct connection is in progress, the existing `ConnectionState.Connecting` presentation is used; no new blocking splash screen is added.
- A successful direct connection leaves the dashboard and current startup experience intact.
- A failed or unavailable default connection opens the pairing destination once.
- The pairing screen continues to close after a successful connection.

## Responsibility Boundaries

- `MainActivity` owns permission readiness and passes that readiness into the Compose shell.
- `AppShell` coordinates the one-time startup attempt and fallback navigation because it owns the navigation controller.
- `ConnectionViewModel` owns access to saved device preferences and the direct connection operation.
- `PairingScreen` owns automatic scan-on-entry for the fallback/manual device selection experience.

This keeps Android permission handling, navigation, connection logic, and screen behavior independently testable.

## Error Handling

- Permission denial is treated as an unavailable connection: the app opens pairing, where the user can grant permissions through the normal Android/app flow before retrying. The RFID SDK is not invoked before the permission callback.
- A missing saved MAC opens pairing without a reader connection call.
- A false result or handled connection failure opens pairing once.
- The design does not add background retries, continuous scanning, or a foreground service.

## Testing

Automated tests will cover:

- startup does not begin before permission resolution;
- a saved MAC triggers one direct connection attempt;
- successful auto-connect keeps the dashboard active and refreshes reader metadata;
- missing saved MAC requests pairing without invoking a reader connection;
- failed auto-connect requests pairing;
- fallback pairing starts scanning automatically;
- recomposition/state changes do not repeat startup connection or fallback navigation.

Existing connection, scanning, and manual pairing behavior must remain green.

## Out of Scope

- Multiple saved readers or user-selectable connection priority.
- Background/foreground-service reconnection after the app has been closed.
- Continuous automatic retries while the reader is powered off.
- Changes to Chainway R6 firmware or Bluetooth bonding behavior.

# Remote Locate Command Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Send a SKU from `kandtr2026/da-nguyen-shop` to one fixed KOIStock Android device, immediately opening Locate in the foreground or opening it from a notification when backgrounded, without automatically starting RFID.

**Architecture:** Next.js creates a durable Supabase command and sends the same command through FCM. Android funnels foreground push, notification intents, and Supabase Realtime events through one deduplicating coordinator, then navigates to an SKU-aware Locate route. Supabase records command lifecycle; DataStore prevents duplicate handling after process recreation.

**Tech Stack:** Next.js App Router, TypeScript, Supabase/PostgreSQL/Realtime, Firebase Admin SDK, Android Kotlin/Compose, Firebase Messaging, DataStore, JUnit/coroutines-test.

## Global Constraints

- The target is one configured Android device; do not add device selection UI.
- Commands expire exactly five minutes after creation.
- Never automatically call `LocateViewModel.start()` from a remote command.
- Foreground commands navigate immediately; background/stopped commands require notification tap.
- The newest distinct valid command wins and stops any active locate session.
- FCM credentials and Supabase service-role credentials remain server-only.
- The web repository was unavailable while planning. Paths below use standard App Router layout; preserve the interfaces and place files in the repo's equivalent existing directories if its aliases differ.

---

## File Map

### Web repository: `kandtr2026/da-nguyen-shop`

- Create `supabase/migrations/202607270001_remote_locate_commands.sql`: tables, indexes, RLS, Realtime publication.
- Create `src/lib/locate/locate-command.ts`: validation, command creation, and push orchestration.
- Create `src/lib/firebase/admin.ts`: server-only Firebase Admin singleton.
- Create `src/app/api/locate/route.ts`: authenticated HTTP boundary.
- Create `src/app/api/devices/register/route.ts`: register/rotate the fixed device token and bind its Supabase user.
- Modify the existing product/order action component that owns “Tìm kho”: call `POST /api/locate` and render progress/result.
- Test with the repo's existing test convention; suggested paths are `src/lib/locate/locate-command.test.ts` and `src/app/api/locate/route.test.ts`.

### Android repository: `KOIStock`

- Modify `gradle/libs.versions.toml`, root `build.gradle.kts`, and `app/build.gradle.kts`: Google Services, Firebase Messaging, Supabase Realtime/Auth dependencies.
- Modify `app/src/main/AndroidManifest.xml`: messaging service and notification permission.
- Create `app/src/main/java/com/example/koistock/remote/RemoteLocateCommand.kt`: wire model and validation.
- Create `app/src/main/java/com/example/koistock/remote/HandledCommandStore.kt`: bounded persistent deduplication.
- Create `app/src/main/java/com/example/koistock/remote/RemoteLocateCoordinator.kt`: single command state machine.
- Create `app/src/main/java/com/example/koistock/remote/LocateMessagingService.kt`: FCM entry point and notification.
- Create `app/src/main/java/com/example/koistock/remote/LocateRealtimeSubscriber.kt`: lifecycle-aware Supabase subscription.
- Modify `app/src/main/java/com/example/koistock/MainActivity.kt`: cold-start and `onNewIntent` handling.
- Modify `app/src/main/java/com/example/koistock/ui/shell/AppDestination.kt` and `AppShell.kt`: SKU route and command navigation.
- Modify `app/src/main/java/com/example/koistock/ui/locate/LocateScreen.kt`: select requested SKU after catalog load.
- Add focused unit tests under `app/src/test/java/com/example/koistock/remote/` and `ui/locate/`.

---

### Task 1: Supabase command schema and security

**Files:**
- Create: `supabase/migrations/202607270001_remote_locate_commands.sql`

**Interfaces:**
- Produces: `devices(id, fcm_token, supabase_user_id, enabled, updated_at)` and `locate_commands(id, device_id, sku, created_at, expires_at, created_by, status, opened_at, failure_reason)`.

- [ ] **Step 1: Write the migration assertions using the repo's existing Supabase SQL test harness**

Assert that an authenticated device user can select only rows whose `devices.supabase_user_id = auth.uid()`, ordinary clients cannot insert commands, and service-role operations can insert/update. If no SQL harness exists, create `supabase/tests/remote_locate_commands.sql` using `begin;`, `set local role authenticated`, `set_config('request.jwt.claim.sub', ...)`, assertions through the repo's pgTAP helpers, and `rollback;`.

- [ ] **Step 2: Run the SQL test and confirm it fails because the tables do not exist**

Run the repository's Supabase test command (normally `supabase test db`). Expected: missing relation `devices` or `locate_commands`.

- [ ] **Step 3: Add the migration**

Use UUID FK types matching the existing auth/product schema. The core definitions must include:

```sql
create table public.devices (
  id text primary key,
  fcm_token text not null,
  supabase_user_id uuid not null unique references auth.users(id) on delete cascade,
  enabled boolean not null default true,
  updated_at timestamptz not null default now()
);

create table public.locate_commands (
  id uuid primary key default gen_random_uuid(),
  device_id text not null references public.devices(id),
  sku text not null,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null default (now() + interval '5 minutes'),
  created_by uuid references auth.users(id),
  status text not null default 'pending' check (status in ('pending','opened','rejected','expired')),
  opened_at timestamptz,
  failure_reason text,
  check (expires_at > created_at)
);
create index locate_commands_device_created_idx
  on public.locate_commands(device_id, created_at desc);
alter table public.devices enable row level security;
alter table public.locate_commands enable row level security;
```

Add select/update policies tied through `devices.supabase_user_id = auth.uid()`. Add `locate_commands` to `supabase_realtime` only if it is not already a publication member; make the migration idempotent using a catalog check.

- [ ] **Step 4: Run SQL tests and schema lint**

Expected: all RLS assertions pass and `supabase db lint` reports no new errors.

- [ ] **Step 5: Commit**

```bash
git add supabase/migrations/202607270001_remote_locate_commands.sql supabase/tests/remote_locate_commands.sql
git commit -m "feat: add remote locate command schema"
```

### Task 2: Server command service and FCM delivery

**Files:**
- Create: `src/lib/firebase/admin.ts`
- Create: `src/lib/locate/locate-command.ts`
- Test: `src/lib/locate/locate-command.test.ts`

**Interfaces:**
- Produces: `sendLocateCommand(input: { sku: string; userId: string }): Promise<{ commandId: string; sku: string; status: 'pending'; pushAccepted: boolean }>`.

- [ ] **Step 1: Write failing service tests**

Cover trimmed/canonical SKU, unknown SKU, no active tags, disabled/unregistered device, exact five-minute expiry, FCM payload keys `commandId`, `sku`, `expiresAt`, and successful DB insert with `pushAccepted: false` when FCM rejects.

- [ ] **Step 2: Run the focused test**

Run the repo's package manager equivalent of `npm test -- src/lib/locate/locate-command.test.ts`. Expected: module not found.

- [ ] **Step 3: Implement the Firebase singleton and service**

`admin.ts` must import `server-only`, initialize once with `FIREBASE_PROJECT_ID`, `FIREBASE_CLIENT_EMAIL`, and newline-normalized `FIREBASE_PRIVATE_KEY`, and export `getMessaging()`. The service must query the existing product and active-tag tables using their real names, resolve `process.env.KOISTOCK_DEVICE_ID`, insert the command before push, and send a data-only message plus Android notification metadata. Never roll back/delete the command on push failure.

- [ ] **Step 4: Run tests**

Expected: focused service tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/lib/firebase/admin.ts src/lib/locate/locate-command.ts src/lib/locate/locate-command.test.ts
git commit -m "feat: send remote locate commands"
```

### Task 3: Authenticated API routes and web button

**Files:**
- Create: `src/app/api/locate/route.ts`
- Create: `src/app/api/devices/register/route.ts`
- Modify: existing component containing the “Tìm kho” action
- Test: `src/app/api/locate/route.test.ts`

**Interfaces:**
- Consumes: `sendLocateCommand` from Task 2.
- Produces: `POST /api/locate` request `{ sku: string }`; response `{ commandId, sku, status, pushAccepted }`.

- [ ] **Step 1: Write failing route tests**

Assert `401` without session, `403` without warehouse permission, `400` for blank/non-string SKU, mapped `404/409`, and `200` for success including `pushAccepted`.

- [ ] **Step 2: Run route tests and confirm failure**

Expected: missing route/service wiring.

- [ ] **Step 3: Implement routes**

Reuse the repo's SSR Supabase session helper and warehouse authorization. `devices/register` accepts the fixed device ID, FCM token, and authenticated Supabase user; protect it with a one-time deployment enrollment secret or existing device authorization middleware. Never accept an arbitrary target device from `/api/locate`.

- [ ] **Step 4: Wire the button**

On click, disable the button, POST the row SKU, then show `Đã gửi đến máy tìm kho`; if `pushAccepted` is false show `Đã tạo lệnh; thông báo đẩy chưa gửi được`. Render the API's safe error message and always re-enable in `finally`.

- [ ] **Step 5: Run focused tests, typecheck, and build**

Expected: route/component tests pass, TypeScript has no errors, production build succeeds.

- [ ] **Step 6: Commit**

```bash
git add src/app/api/locate src/app/api/devices/register src
git commit -m "feat: add find-in-warehouse action"
```

### Task 4: Android command coordinator and persistent deduplication

**Files:**
- Create: `app/src/main/java/com/example/koistock/remote/RemoteLocateCommand.kt`
- Create: `app/src/main/java/com/example/koistock/remote/HandledCommandStore.kt`
- Create: `app/src/main/java/com/example/koistock/remote/RemoteLocateCoordinator.kt`
- Test: corresponding files under `app/src/test/java/com/example/koistock/remote/`

**Interfaces:**
- Produces: `data class RemoteLocateCommand(val commandId: String, val sku: String, val expiresAtEpochMs: Long)` and `RemoteLocateCoordinator.commands: SharedFlow<RemoteLocateCommand>`.

- [ ] **Step 1: Write failing tests**

Test blank fields rejected, expired command marked expired, duplicate ignored after store recreation, newest valid distinct command emitted, and store capped at the newest 100 IDs.

- [ ] **Step 2: Run focused Android tests**

Run: `./gradlew testDebugUnitTest --tests "com.example.koistock.remote.*"`. Expected: compilation failure because types do not exist.

- [ ] **Step 3: Implement minimal model/store/coordinator**

Use injected `now: () -> Long`, repository callbacks `markOpened`, `markRejected`, `markExpired`, and a DataStore string set plus ordered timestamp map so trimming is deterministic. Persist before emitting to prevent Realtime/FCM races.

- [ ] **Step 4: Run focused tests**

Expected: all remote package tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/koistock/remote app/src/test/java/com/example/koistock/remote
git commit -m "feat: coordinate remote locate commands"
```

### Task 5: FCM registration, notification, and Activity intents

**Files:**
- Modify: Gradle catalog/build files and `AndroidManifest.xml`
- Create: `LocateMessagingService.kt`
- Modify: `MainActivity.kt`
- Test: `RemoteLocateIntentParserTest.kt`

- [ ] **Step 1: Test intent/data parsing**

Cover valid map, missing fields, malformed expiry, and identical parsing from FCM data and Activity extras.

- [ ] **Step 2: Add Firebase Messaging dependencies and Google Services plugin**

Use the existing Firebase BOM. Add `firebase-messaging-ktx`, Google Services plugin, and require the externally supplied `app/google-services.json` without committing secrets.

- [ ] **Step 3: Implement service and notification**

Create notification channel `remote_locate`, use a unique request code derived from command ID, `FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE`, and extras for all three command fields. Foreground messages submit directly to the coordinator; background delivery posts the notification. Upsert rotated tokens through the registration API.

- [ ] **Step 4: Handle cold/warm Activity intents**

Parse `intent` in `onCreate` and `onNewIntent`, then submit to the same coordinator. Add `POST_NOTIFICATIONS` runtime handling only on API 33+.

- [ ] **Step 5: Run tests and compile**

Run the parser tests and `./gradlew :app:compileDebugKotlin`. Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add gradle app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/java/com/example/koistock/MainActivity.kt app/src/main/java/com/example/koistock/remote
git commit -m "feat: receive locate push notifications"
```

### Task 6: SKU-aware navigation and Locate selection

**Files:**
- Modify: `AppDestination.kt`, `AppShell.kt`, `LocateScreen.kt`
- Test: `app/src/test/java/com/example/koistock/ui/locate/RemoteLocateSelectionTest.kt`

- [ ] **Step 1: Write failing selection tests**

Extract and test a pure function returning `Waiting`, `Selected(item)`, or `Unavailable(reason)` for requested SKU plus catalog state. Cover case-insensitive exact match, asynchronous loading, absent SKU, and no active tags.

- [ ] **Step 2: Run the focused test and confirm failure**

Expected: missing selection function/state.

- [ ] **Step 3: Add encoded route helpers**

Define `LocateSkuArg`, route pattern `locate?sku={sku}`, and `locateRoute(sku) = "locate?sku=${Uri.encode(sku)}"`. Add a nullable nav argument; dashboard navigation without SKU remains valid.

- [ ] **Step 4: Connect coordinator to navigation**

Collect commands once at shell level. Before navigating, stop the currently retained Locate VM if active, then navigate with `launchSingleTop = true` while replacing the previous Locate destination. Do not start RFID.

- [ ] **Step 5: Apply requested SKU after catalog load**

Pass `requestedSku` into `LocateScreen`; key saved selection by the requested SKU so a later command replaces the prior product. Display an actionable unavailable message and “Chọn sản phẩm khác”.

- [ ] **Step 6: Run tests and compile**

Run `LocateViewModelTest`, `RemoteLocateSelectionTest`, then `:app:compileDebugKotlin`. Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/koistock/ui app/src/test/java/com/example/koistock/ui
git commit -m "feat: open locate screen by remote sku"
```

### Task 7: Supabase Realtime foreground subscription and end-to-end verification

**Files:**
- Create: `LocateRealtimeSubscriber.kt`
- Modify: dependency catalog/build files and app composition root
- Test: `LocateRealtimeSubscriberTest.kt`

- [ ] **Step 1: Write subscriber tests**

With a fake event source, verify only the configured device is accepted, payload maps to the shared command model, lifecycle stop unsubscribes, reconnect does not duplicate handled IDs, and status callbacks use the command ID.

- [ ] **Step 2: Add Supabase Kotlin Auth/Realtime dependencies and implement subscriber**

Authenticate anonymously once, register that `auth.uid()` with the device route, subscribe to inserts filtered by fixed device ID, and submit each row to the coordinator. Start/stop with process foreground lifecycle; do not run a permanent Android background service.

- [ ] **Step 3: Run the entire Android unit suite**

Run: `./gradlew testDebugUnitTest`. Expected: all tests pass.

- [ ] **Step 4: Build and perform manual matrix**

Run `./gradlew :app:assembleDebug`, install on the fixed device, register its token, and verify: foreground immediate navigation; background notification tap; force-stopped process notification tap; disconnected R6 banner; duplicate delivery once; two rapid SKUs leave the newest visible; no RFID scan starts until button press.

- [ ] **Step 5: Verify Supabase audit rows**

For each manual case confirm `pending` becomes `opened`, expired commands remain unopened/expire, and no credentials or FCM tokens appear in client/server logs.

- [ ] **Step 6: Commit**

```bash
git add app docs
git commit -m "feat: complete remote locate delivery"
```

## Deployment Order

1. Apply the Supabase migration and configure Realtime/RLS.
2. Configure Firebase Admin environment variables and `KOISTOCK_DEVICE_ID` in Next.js.
3. Deploy the API routes while keeping the web button feature-flagged off.
4. Build/install Android with Firebase and Supabase public configuration; complete anonymous auth and device token registration.
5. Enable the web button and run the manual matrix.
6. Monitor `locate_commands`, registration failures, FCM errors, and duplicate rates during initial use.

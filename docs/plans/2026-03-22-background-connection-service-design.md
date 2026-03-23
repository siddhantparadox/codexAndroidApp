# Background Connection Service Design

## Goal

Keep the desktop connection alive while the app is off-screen, and only allow
it to fall away when the user explicitly disconnects or fully removes the app
task. Returning to the app should feel continuous instead of forcing a manual
reconnect.

## Decision

- Add an app-owned foreground service that runs whenever a saved desktop host is
  active.
- Use a quiet ongoing notification as the user-visible affordance for the
  background connection.
- Treat unexpected socket closure as reconnectable as long as the active host
  still exists.
- Stop the service when the app task is removed so "close the app" still ends
  the background session.

## Why This Approach

- A normal backgrounded process cannot reliably keep a WebSocket alive on
  modern Android.
- A foreground service is the supported platform mechanism for long-running,
  user-visible connectivity work.
- The app already has an app-scoped repository and reconnect logic, so the
  service can keep the existing transport stack alive instead of replacing it.

## Service Contract

- Service name: `ConnectionForegroundService`
- Start condition: any active host exists while the app is in the foreground.
- Stop condition:
  - no active host remains
  - user taps `Disconnect`
  - app task is removed from recents
- Notification style:
  - low importance
  - ongoing
  - title like `Connected to <desktop>`
  - compact body explaining that the desktop session is being kept alive
  - actions for `Open app` and `Disconnect`

## Foreground Service Type

- Use the `connectedDevice` foreground-service type.
- Declare `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, and
  `CHANGE_NETWORK_STATE` in the manifest.

Rationale:

- Android's foreground-service guidance describes `connectedDevice` as the type
  for interacting with external devices over Bluetooth, NFC, USB, IR, or
  network.
- This app maintains a persistent LAN connection to a desktop bridge, which is
  a closer fit than `dataSync`.
- `dataSync` is intended for transfer/sync workloads and is a worse match for an
  always-on desktop-control session.

This type choice is an inference from the Android docs based on the app's LAN
desktop-bridge behavior.

## Repository Behavior Changes

- Add an explicit `clearActiveHost()` path so the service can disconnect from
  the notification.
- Add an explicit `ensureActiveHostConnection()` path so the app/service can
  reassert the connection when they come to the foreground.
- Change transport-close handling so clean socket closures also enter
  reconnecting state when an active host remains selected.

## App Lifecycle Behavior

- The UI layer starts the service when an active host is present and the app is
  visible.
- The service observes repository host/connection flows and updates its
  notification while the app is backgrounded.
- Reopening the app reissues a service start, which also doubles as a quick
  reconnect nudge if the connection dropped cleanly while off-screen.

## Out of Scope

- Cloud relay or push-based reconnect.
- Background persistence after device reboot.
- Keeping the connection alive after the user explicitly removes the app task.
- Notification-rich error handling beyond the ongoing connection status.

## Risks

- If notification permission is denied on newer Android versions, the
  foreground-service UI surface may be reduced even though the service still
  runs.
- Starting the service from the wrong lifecycle phase would trigger
  foreground-service start restrictions, so startup must remain foreground-only.
- The service and repository must avoid fighting each other during intentional
  disconnect and reconnect flows.

## Verification

- Validate connect -> background -> return without losing session continuity.
- Validate disconnect from the notification action.
- Validate app-task removal stops the service.
- Validate clean socket closure enters reconnecting state rather than staying
  disconnected.

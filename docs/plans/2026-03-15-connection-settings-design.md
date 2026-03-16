# Connection Settings Design

Document status: Draft
Date: 2026-03-15

## Goal

Make the desktop connection understandable, recoverable, and predictable from
the Android app instead of treating connection management as a thin host picker.

Because the remote Codex app-server `ws` transport is still experimental, the
first slice should focus on connection health, reconnect behavior, and clear
recovery actions before adding advanced controls.

## Scope

### In

- richer connection state for reconnecting, retry attempts, last error, and
  last successful connection
- clearer connection UI in the host manager and settings
- manual recovery actions such as retry, disconnect, and host management
- better alerts and diagnostics for temporary drops versus hard failures
- unit tests plus real-device validation for drop and reconnect flows

### Out

- replacing WebSocket with a new desktop relay or `stdio` bridge
- large pairing or onboarding redesign beyond what the settings need
- exposing low-level transport tuning unless a real product need appears

## Action Items

- Audit the current connection flow across the repository, view model, host
  connection screen, and settings screen to document what state is already
  available and what is still hidden from the UI.
- Expand the connection model to represent `reconnecting`, retry attempt count,
  last disconnect reason, last successful connection time, and active transport
  details.
- Refactor the reconnect pipeline so the UI can observe scheduled retries,
  active retry attempts, reconnect success, and hard-stop failures instead of
  only generic connected and error phases.
- Add a connection details section that shows the active host, address and
  port, transport type, current status, and the latest connection event in
  plain language.
- Add explicit user actions for `Retry now`, `Disconnect`, and `Forget host`,
  and decide whether `Edit host` belongs in the same slice.
- Improve settings so connection alerts distinguish between `reconnecting`,
  `reconnected`, and `needs attention` instead of only generic disconnect
  notifications.
- Add lightweight diagnostics for support and debugging, such as the last
  transport error and last reconnect timestamp, without exposing noisy
  developer-only logs in normal UI.
- Keep the defaults simple: auto-reconnect should stay on, while advanced
  behavior remains internal unless repeated failures show a need for user-facing
  controls.
- Add unit tests for connection-state transitions, reconnect backoff behavior,
  and alert generation so future transport changes do not regress the UX.
- Run real-device validation with the desktop host over USB-debug-assisted
  logcat monitoring, including forced socket resets, host restarts, Wi-Fi
  interruptions, and reconnect recovery.

## Open Questions

- Should connection settings expose only health and recovery information, or
  also user-configurable retry behavior?
- Should `Forget host` and `Edit host` ship in the first settings pass, or can
  they follow once diagnostics are in place?
- Do we want a visible distinction between a temporary reconnecting state and a
  hard offline state everywhere in the app, or only in the connection and
  settings surfaces?

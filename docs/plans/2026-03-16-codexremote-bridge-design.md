# CodexRemote Bridge Design

Document status: Approved
Date: 2026-03-16

## Goal

Improve the reliability of active mobile Codex conversations by replacing the
current direct phone-to-`codex app-server` WebSocket path with a local desktop
bridge, while preserving the existing one-command user experience:

```bash
npx codexremote
```

The user should still run one desktop command, scan one QR code, and use the
Android app as the mobile control surface. The transport and reconnect behavior
should become more resilient without making the product feel more complex.

## Product decision

`codexremote` remains the standard desktop entry point.

It will no longer be a thin launcher that exposes raw `codex app-server`
WebSocket transport directly to the phone. Instead, it will become a local
desktop bridge that:

- owns one warm Codex session on the desktop
- talks to `codex app-server` over `stdio`
- exposes a phone-facing LAN WebSocket on port `4500`
- keeps the desktop-side Codex session alive across transient phone disconnects
- supports reconnect and catch-up behavior without forcing a fresh Codex
  session

This design supersedes the earlier assumption in [`spec.md`](../../spec.md) and
[`2026-03-14-codexremote-connection-design.md`](./2026-03-14-codexremote-connection-design.md)
that the phone should connect directly to a raw `codex app-server` WebSocket
listener.

## OpenAI docs basis

This design is driven by the current official Codex docs:

- The Codex app-server supports two transports: `stdio` and `websocket`.
  `stdio` is the default transport, while `ws://IP:PORT` is documented as
  experimental.
- The CLI reference describes `codex app-server --listen ws://IP:PORT` as
  experimental and intended for development/testing.
- In WebSocket mode, app-server uses bounded queues. When ingress is saturated,
  it can reject requests with JSON-RPC error `-32001` and clients should retry
  with exponential backoff and jitter.
- Clients must initialize once per transport connection, and later requests on
  the same connection are tied to that connection lifecycle.

Official references:

- [Codex App Server](https://developers.openai.com/codex/app-server/)
- [Codex CLI Reference](https://developers.openai.com/codex/cli/reference/)

## Why the current design is fragile

Today the app uses a direct mobile-to-app-server WebSocket connection:

```text
Android app -> ws://desktop:4500 -> codex app-server
```

This has two reliability problems:

1. The phone is coupled directly to the experimental app-server WebSocket
   transport.
2. A network interruption during an active turn is also a session interruption,
   because the same transport connection carries both the mobile link and the
   Codex session lifecycle.

The symptom reported in testing matches this failure mode: idle connection is
usually stable, but mid-conversation streaming can disconnect and reconnect.
That is consistent with an active streaming path, queue pressure, or transport
state loss rather than a simple idle keepalive issue.

## Proposed architecture

### Transport split

The new transport shape should be:

```text
Android app -> WebSocket -> codexremote bridge -> stdio -> codex app-server
```

### User-facing desktop flow

The user flow remains:

1. Run `npx codexremote`
2. Scan the QR code from the Android app
3. Connect to the remembered desktop
4. Keep using the app while `codexremote` stays running

### Ports

- Port `4500` remains the user-facing desktop connection port for the Android
  app
- Port `4501` remains the Usage Wrapped sidecar port

Keeping `4500` avoids breaking the current connection model, saved host
profiles, mental model, and docs.

## Bridge responsibilities

`codexremote` will own the desktop-side session lifecycle and transport
translation.

Core responsibilities:

- spawn `codex app-server` over `stdio`
- keep the spawned Codex process alive while the bridge is alive
- expose one LAN WebSocket server for the phone on `4500`
- print QR/bootstrap information for the phone
- keep a warm desktop-side Codex session even when the phone temporarily
  disconnects
- buffer recent outbound bridge messages for reconnect catch-up
- preserve the current Usage Wrapped startup and reuse behavior on `4501`

Non-goals for the first bridge version:

- cloud relay
- public internet access
- desktop-native UI
- rollout-file mirroring
- push notifications
- end-to-end encryption beyond trusted-LAN bootstrap

## Session lifecycle

### Desktop-side session

The bridge owns the durable session to Codex.

Expected behavior:

- the bridge starts `codex app-server` over `stdio`
- the bridge performs the app-server `initialize` / `initialized` handshake
- the bridge keeps that Codex-side connection warm until `codexremote` exits
- phone disconnects do not automatically tear down the Codex-side app-server
  session

### Phone-side session

The phone owns a reconnectable presentation session to the bridge.

Expected behavior:

- the phone connects to the bridge over WebSocket
- the bridge accepts a fresh phone connection without restarting Codex
- reconnecting the phone should not create a new underlying desktop Codex
  session unless the bridge itself has exited or restarted

## Reconnect model

The reconnect model should optimize for user-visible continuity rather than
perfect transport transparency.

### Primary recovery

The bridge keeps a bounded in-memory replay window of recent outbound messages.

When the phone reconnects:

- the phone reattaches to the bridge
- the bridge can replay recently missed outbound messages when available
- the current active turn should continue from the same desktop-side Codex
  session

### Canonical fallback

If the replay window is insufficient or reconnect state is ambiguous, the
Android app should recover from app-server canonical state instead of assuming
the in-memory mobile transcript is authoritative.

Fallback recovery should use:

- `thread/read`
- `thread/resume`
- current thread status and turn/item state from app-server

This keeps the system simple and docs-aligned while still preserving most of
the benefit of smoother reconnects.

## Phone-facing protocol

The bridge should preserve the current app-server-shaped JSON-RPC surface for
normal conversation traffic as much as possible.

### Principle

- regular thread and turn RPCs should still look like app-server traffic to the
  Android app
- bridge-specific behavior should live under a small reserved bridge namespace
  only when needed for reconnect and diagnostics

This keeps Android churn lower and lets the app retain most of its current
repository and transcript logic.

### Recommended bridge-only additions

The first bridge version may add a small internal control layer for:

- reconnect attach/resume
- bridge session metadata
- replay-window acknowledgment
- diagnostics such as bridge uptime or reconnect reason

The bridge should not invent a broad new mobile backend. It should stay narrow
and transport-focused.

## Android app changes

The Android app should move from “direct app-server socket owner” to “bridge
client with app-server-shaped traffic.”

### Keep

- the existing thread, transcript, approvals, and settings UI
- the existing app-server item model and JSON parsing
- the existing QR-first onboarding direction
- the existing Usage Wrapped integration on `4501`

### Change

- treat `codexremote` as the primary desktop endpoint, not raw app-server
- update connection copy so users think in terms of “desktop bridge” or
  “desktop connection” rather than “manual app-server socket”
- adjust reconnect behavior so reconnecting to the bridge does not imply
  clearing desktop-side Codex context
- add bridge-aware connection states such as `Connected`, `Reconnecting`,
  `Desktop offline`, and `Needs attention`
- keep raw manual host/port entry only as an advanced or debug fallback

## CodexRemote changes

The current `codexremote` implementation starts or reuses app-server on `4500`
and tells the phone to connect directly to it. That implementation should be
replaced by a bridge process model.

### Required changes

- replace direct `codex app-server --listen ws://0.0.0.0:4500` bootstrap as the
  default path
- add a Codex transport layer that spawns `codex app-server` over `stdio`
- add a phone-facing WebSocket server on `4500`
- add bridge session state and graceful reconnect handling
- preserve the current QR/bootstrap UX
- preserve the Usage Wrapped sidecar startup and reuse rules

### Fallback behavior

Raw direct app-server WebSocket mode can remain in the product only as:

- advanced manual fallback
- developer debugging path

It should not remain the primary path for ordinary users.

## Security posture

This remains a trusted-LAN design.

The bridge improves reliability and ownership of session lifecycle, but it does
not turn the product into a hardened internet-facing remote access system.

The app and docs should continue to warn users:

- use the desktop bridge only on a private trusted network
- do not expose the desktop endpoint directly to the public internet

## Rollout recommendation

### Phase 1

- keep `npx codexremote` as the user-facing command
- refactor `codexremote` into a local bridge
- switch the Codex-side transport to `stdio`
- expose bridge WebSocket on `4500`
- preserve `4501` Usage Wrapped support
- add basic reconnect and desktop-session continuity

### Phase 2

- add bounded replay-buffer reconnect catch-up
- improve Android reconnect UX and bridge diagnostics
- hide raw manual app-server mode behind advanced settings

### Phase 3

- evaluate whether replay plus canonical resync is sufficient
- only consider rollout mirroring or stronger pairing if real-world testing
  shows a persistent UX gap

## Validation

Real-device validation should cover:

- active turn survives transient phone Wi-Fi drop
- reconnect does not restart the underlying Codex desktop session
- follow-up prompt after reconnect still targets the same thread and active turn
  state
- bridge restart produces a clear offline or repair state
- Usage Wrapped remains available during normal bridge use
- advanced raw WebSocket fallback still works for debugging

## Summary

The right move is not to remove WebSockets entirely. The right move is to keep
WebSockets on the phone-facing side and remove them from the fragile
desktop-to-Codex side.

That gives the product the best user outcome:

- same one-command setup
- same QR-first pairing
- better mid-conversation reliability
- fewer session resets
- no new cloud dependency

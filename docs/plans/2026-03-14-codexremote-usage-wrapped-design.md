# CodexRemote Usage Wrapped Design

Document status: Approved
Date: 2026-03-14

## Goal

Make `npx codexremote` start everything the Android app needs for both:

- the main Codex mobile connection on port `4500`
- the Usage Wrapped history endpoint on port `4501`

The user should not need a second desktop command.

## Problem

Today `codexremote` only starts `codex app-server` on `4500`.

The Android app derives Usage Wrapped as a separate HTTP endpoint on
`host.port + 1`, which means:

- app-server lives on `4500`
- Usage Wrapped lives on `4501`

Because `codexremote` does not start the usage-history sidecar, the phone can
connect to Codex but Usage Wrapped fails with a connect timeout to `4501`.

## Product decision

`codexremote` will become the single desktop bootstrap for both services.

When a user runs:

```bash
npx codexremote
```

the tool should:

1. start or reuse `codex app-server` on `4500`
2. start or reuse a local HTTP Usage Wrapped sidecar on `4501`
3. print one terminal status card
4. keep both services alive while the terminal stays open

## Implementation approach

The Usage Wrapped sidecar will be implemented directly in Node inside the npm
package instead of launching the existing JVM service.

### Why

- `npx codexremote` users should not need Java
- the npm package should stay self-contained
- the existing usage aggregation logic is small enough to port
- this keeps macOS, Linux, and Windows behavior uniform

### Non-goals

- changing the Android client protocol
- changing the `4500` / `4501` port relationship
- adding a cloud relay, proxy, or remote storage layer

## Sidecar behavior

The Node sidecar should expose:

- `GET /healthz`
- `GET /usage-wrapped`

It should read session files from:

- `%CODEX_HOME%/sessions` when `CODEX_HOME` is set
- otherwise `~/.codex/sessions`

It should aggregate the same high-level data the Android client already expects:

- generated timestamp
- date range
- overview metrics
- token totals
- cost estimate
- highlights
- activity by day

## CodexRemote lifecycle

### Startup

1. Validate that port `4500` is free or reusable for Codex app-server.
2. Validate that port `4501` is free or reusable for Usage Wrapped.
3. Start `codex app-server` if needed.
4. Start the embedded Usage Wrapped sidecar if needed.
5. Print a terminal summary showing:
   - desktop name
   - WebSocket address
   - connection code
   - QR code
   - Usage Wrapped status
6. Keep running until the user stops the process.

### Reuse rules

- If `4500` is already a Codex app-server, reuse it.
- If `4501` is already a healthy Usage Wrapped endpoint, reuse it.
- If either port is occupied by something else, fail clearly.

### Shutdown

- If `codexremote` started a child process or sidecar itself, it should stop it
  on exit.
- If it reused an existing service, it should leave it alone.

## Code organization

Keep the npm package modular instead of growing `codexremote.mjs` into one large
file.

Recommended files:

- `bin/codexremote.mjs`
- `lib/network.mjs`
- `lib/appServer.mjs`
- `lib/usageWrappedService.mjs`
- `lib/usageWrappedAggregator.mjs`
- `lib/usageWrappedCost.mjs`

## Error handling

Desktop-side failures should stay explicit:

- app-server port conflict
- usage sidecar port conflict
- invalid or unreadable session directory
- malformed session files should be skipped, not crash the whole service
- health probe timeout should fail startup clearly

## Verification

- local `node ./bin/codexremote.mjs`
- verify `GET /healthz` on `4501`
- verify `GET /usage-wrapped` returns JSON
- verify the Android Usage Wrapped screen loads from the phone
- update README so users understand that one command now enables both features

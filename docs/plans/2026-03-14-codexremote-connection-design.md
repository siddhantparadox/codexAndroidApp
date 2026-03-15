# CodexRemote Connection Design

Document status: Approved
Date: 2026-03-14

## Goal

Replace the current manual host-first mobile connection flow with a QR-first
bootstrap that starts Codex app-server on the desktop and lets the Android app
connect with minimal typing.

## Product decision

The desktop bootstrap entry point will be:

```bash
npx codexremote
```

`codexremote` is not a project picker or a desktop shell replacement. Its only
job is to start `codex app-server`, expose the mobile connection target on the
trusted local network, and display bootstrap information for the phone.

The Android app will refactor the existing host connection flow rather than add
a separate parallel pairing flow.

## Constraints from current Codex docs

- The mobile client still depends on the Codex app-server WebSocket transport.
- The documented WebSocket listener shape is `ws://IP:PORT`.
- The documented WebSocket transport is currently experimental.
- The design remains LAN-first and is not intended for direct public internet
  exposure.
- The official docs do not currently describe a first-party mobile pairing or
  discovery layer, so this design adds a lightweight bootstrap layer on top of
  the existing app-server transport instead of inventing a new backend.

## Desktop experience

### User flow

1. User runs `npx codexremote`.
2. `codexremote` verifies that `codex` is available.
3. `codexremote` verifies that port `4500` can be used.
4. If a Codex app-server is already available on `4500`, `codexremote` reuses
   it.
5. Otherwise `codexremote` starts:

   ```bash
   codex app-server --listen ws://0.0.0.0:4500
   ```

6. `codexremote` detects the best LAN IPv4 address for the machine.
7. `codexremote` renders a terminal-only bootstrap UI with:
   - status text such as `Mobile access is on`
   - desktop display name
   - LAN address and port
   - QR code
   - short code fallback
   - clear stop instruction such as `Press Ctrl+C to stop`
8. `codexremote` keeps running while the app-server process is alive.

### Non-goals

- No browser UI
- No native desktop window
- No project or folder picker
- No desktop-side login or account UI
- No second custom transport beyond the existing app-server WebSocket

## Port policy

- Default port is always `4500`.
- `codexremote` should not silently switch to a random port.
- If port `4500` is already in use by Codex app-server, reuse it and display the
  bootstrap UI.
- If port `4500` is occupied by something else, fail with a clear error instead
  of guessing.
- An advanced override such as `--port` can be added later, but is not part of
  the default user story.

The fixed default port makes saved mobile reconnects reliable across restarts.

## Bootstrap payload

The QR payload should be a small versioned bootstrap object rather than a raw
WebSocket URL alone.

Recommended fields:

- `version`
- `desktopId`
- `desktopName`
- `host`
- `port`

This gives the mobile client enough information to:

- label the desktop clearly
- remember a stable desktop identity
- update connection details on rescan without creating duplicates
- present a confirmation screen before connecting

The short code fallback should be intentionally small and local-only. It should
encode the same connection target in a compact typed format, without requiring a
secondary cloud or relay service.

## Android experience

### First-run connection flow

The existing connection screen should become QR-first when no desktop is saved.

Primary actions:

- `Scan QR code`
- small secondary action: `Type short code instead`
- tertiary action under advanced options: `Enter address manually`

Manual host fields remain available, but they should no longer be the default
entry point for most users.

### Confirmation flow

After a QR scan or short-code entry, the app shows a confirmation sheet with:

- desktop name
- local network endpoint
- trusted-network warning
- primary `Connect` action

### Returning user flow

The app should remember the paired desktop and prefer reconnect over repair.

Returning behavior:

- show `Reconnect to <desktop name>` on later launches
- automatically try reconnect to the saved endpoint
- if the desktop terminal is no longer running, show `Desktop offline`
- offer `Retry`
- offer `Scan again`
- keep `Type short code` as a smaller fallback action

The user should not need to rescan every session.

## Android data model updates

The current `HostProfile` model is too manual-entry-oriented. It should evolve
into a remembered desktop profile with a stable identity.

Needed profile shape:

- stable `desktopId`
- display name
- current host address
- current port
- active state
- optional last-seen metadata for reconnect UX

Rescanning the same desktop should update the saved desktop record rather than
create a duplicate host entry.

## Android refactor scope

This work should adapt the current connection architecture instead of replacing
the transport layer.

### Keep

- existing WebSocket JSON-RPC transport
- existing reconnect behavior in the repository
- existing host activation model where one host is active at a time
- existing dashboard connection strip as the primary health surface

### Change

- refactor the host connection screen to QR-first onboarding
- move manual host entry behind advanced options
- add QR bootstrap parsing
- add short-code parsing
- upsert desktop profiles by stable desktop identity
- update dashboard copy and actions around offline/reconnect states

## File-level impact

Primary Android files expected to change:

- `app/src/main/java/dev/codex/mobile/feature/connection/HostConnectionScreen.kt`
- `app/src/main/java/dev/codex/mobile/feature/connection/HostConnectionViewModel.kt`
- `app/src/main/java/dev/codex/mobile/core/model/HostProfile.kt`
- `app/src/main/java/dev/codex/mobile/core/data/appserver/AppServerCodexRepository.kt`
- `app/src/main/java/dev/codex/mobile/feature/dashboard/DashboardScreen.kt`

Additional files can be added for:

- QR payload parsing
- short-code encoding/decoding
- scan-result models
- confirmation-sheet UI

## Failure handling

### Desktop-side failures

- `codex` missing: show install guidance and stop
- Codex not authenticated: show guidance and stop
- no usable LAN IPv4 detected: show diagnostics and stop
- port conflict with non-Codex process: show conflict and stop
- app-server launch failure: show the error and stop

### Mobile-side failures

- invalid QR payload: show scan error
- malformed short code: show entry error
- saved desktop unavailable: show offline state, not pairing reset
- connect timeout: show retry guidance
- changed desktop IP: rescan should repair the saved desktop entry

## Security posture

- The connection target is a trusted local-network WebSocket endpoint.
- This design is a bootstrap convenience layer, not a hardened pairing or auth
  protocol.
- The UI must continue to warn that the desktop endpoint should stay on a
  private trusted network and not be exposed publicly.

## Rollout recommendation

### Phase 1

- Build terminal-only `npx codexremote`
- Add QR-first Android onboarding
- Keep manual entry under advanced options
- Preserve remembered desktop reconnect

### Phase 2

- Add short-code fallback
- Improve diagnostics around offline desktops and port conflicts

### Phase 3

- Consider optional LAN discovery as a convenience enhancement
- Consider a richer desktop helper only if terminal bootstrap proves
  insufficient

## Validation

- Verify `codexremote` on macOS and Linux
- Verify `codexremote` on Windows, with WSL as the preferred documented path
- Verify first-run scan from Android on a real phone
- Verify reconnect after app relaunch
- Verify offline state after stopping the desktop terminal
- Verify rescan repairs the saved desktop after an IP change


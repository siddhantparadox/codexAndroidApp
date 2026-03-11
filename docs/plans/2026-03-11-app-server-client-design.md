# Codex App-Server Client Design

Document status: Approved
Date: 2026-03-11

## Goal

Replace the in-memory demo repository with a real WebSocket JSON-RPC client for `codex app-server` while keeping the current Android UI shell intact.

## Product decision

V1 will assume the desktop owns authentication.

- The user installs and authenticates Codex CLI on the laptop.
- The user runs `codex app-server --listen ws://<host>:4500`.
- The Android app connects to the running app-server and reads auth state with `account/read`.
- Mobile login flows are out of scope for this slice.

This keeps the phone as a control surface for the desktop runtime instead of making the phone responsible for host authentication.

## Supported app-server surface

This slice targets the stable app-server contract needed for the mobile MVP:

- `initialize`
- `initialized`
- `account/read`
- `thread/list`
- `thread/read`
- `thread/resume`
- `turn/start`
- `turn/steer`
- `turn/interrupt`
- `item/started`
- `item/completed`
- `item/agentMessage/delta`
- `item/plan/delta`
- `item/reasoning/summaryTextDelta`
- `item/commandExecution/outputDelta`
- `item/fileChange/outputDelta`
- `item/commandExecution/requestApproval`
- `item/fileChange/requestApproval`
- `thread/status/changed`
- `turn/*`
- `serverRequest/resolved`

Out of scope for the first real transport pass:

- mobile-triggered login flows
- review mode
- skills browser
- apps/connectors browser
- archive/unarchive UI
- thread fork/rollback UI

## Architecture

### Transport layer

Add a low-level WebSocket transport backed by OkHttp.

Responsibilities:

- open and close a WebSocket connection
- send JSON-RPC requests with incrementing ids
- match responses to pending callers
- surface server notifications as a `Flow`
- report socket errors and disconnects

The transport stays generic and works only with wire messages, not app state.

### Session layer

Add a typed app-server session on top of the transport.

Responsibilities:

- send `initialize` followed by `initialized`
- expose typed RPC methods such as `accountRead`, `threadList`, `threadRead`, `threadResume`, `turnStart`, `turnSteer`, and `turnInterrupt`
- keep track of pending approval server requests so the repository can answer them later

The session owns JSON-RPC semantics. The repository should not build raw protocol messages directly.

### Repository layer

Replace `DemoCodexRepository` with a reducer-backed repository.

Responsibilities:

- manage local preferences and saved host profiles
- maintain connection state
- maintain account/auth state from `account/read`
- maintain thread summaries from `thread/list` plus status notifications
- maintain thread details from `thread/read` plus item and turn notifications
- maintain pending approvals from server requests
- map wire models to UI models already used by screens

The host app-server remains the source of truth for threads and items.

## Repository behavior

### Connection

When the active host changes:

1. close any existing socket
2. connect to `ws://<host>:<port>`
3. send `initialize`
4. send `initialized`
5. call `account/read`
6. call `thread/list`

On reconnect:

1. re-run the initialization handshake
2. refresh account state
3. refresh thread list
4. re-read currently open thread details when needed

### Thread detail

When the user opens a thread:

1. call `thread/read(includeTurns=true)` to hydrate history
2. if the thread is active or needs live updates, call `thread/resume` to subscribe this connection to notifications

When the user sends input:

- if the thread is active, use `turn/steer`
- otherwise, resume if necessary and use `turn/start`

When the user interrupts:

- call `turn/interrupt`

### Approvals

On `item/commandExecution/requestApproval` or `item/fileChange/requestApproval`:

- create a pending approval entry keyed by thread id, turn id, item id, and request id
- expose it in the approval queue

When the user accepts, accepts-for-session, declines, or cancels:

- send the approval response for the pending request
- wait for `serverRequest/resolved`
- remove the pending approval entry
- trust `item/completed` for the final command or file-change state

## UI impact

The screen structure stays the same.

Required UI updates:

- Home and Host Connection should display real connection state
- Home and Host Connection should display desktop auth/account status from `account/read`
- Threads should render real server-backed thread summaries
- Thread Detail should render real item history and streaming deltas
- Approvals should reflect only active server approval requests

The app must not reintroduce demo-only concepts that app-server does not expose directly.

## Local persistence

For this integration pass, persist only client-owned data:

- saved host profiles
- active host selection
- UI preferences

Do not persist desktop credentials or invent durable thread data beyond lightweight UI caching.

## Rollout order

1. Add network dependencies and Android network permissions for LAN WebSocket use.
2. Add wire-level JSON-RPC transport.
3. Add typed app-server session methods.
4. Add repository reducer and local host/preferences persistence.
5. Replace demo thread list with real `thread/list`.
6. Replace demo thread detail with `thread/read` plus live notifications.
7. Replace demo approvals with real request/response handling.
8. Add reconnect and error-state handling.
9. Verify on a real Android device against a laptop-hosted `codex app-server`.

## Validation

- unit test the reducer against recorded JSON-RPC notifications
- unit test approval request lifecycle handling
- run `assembleDebug`
- run `testDebugUnitTest`
- verify on a real phone connected to a real laptop host over LAN

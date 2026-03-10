# Codex Android Client Spec

Document status: Draft v0.1
Date: 2026-03-10
Target stack: Android, Kotlin 2.3.10

## 1. Product Summary

Build a native Android app in Kotlin that lets a user connect to Codex running on their laptop and use Codex from their phone. The Android app is a thin but rich client over OpenAI Codex app-server, not a separate AI backend.

Primary environment assumptions:

- Laptop host is Windows-first.
- Phone and laptop are on the same trusted local network for v1.
- Codex runs on the laptop via Codex CLI and exposes `codex app-server` over WebSocket.
- The phone does not run Codex locally and does not store OpenAI credentials for model access.

Working product idea:

- A clean mobile client for thread history, live Codex conversations, approvals, and lightweight project monitoring.

## 2. Core Decision

V1 will be LAN-first and connect directly to a laptop-hosted Codex app-server over WebSocket.

Reasoning:

- This is the smallest viable architecture.
- It keeps all model auth and local file access on the laptop.
- It avoids building a custom cloud relay before the core mobile UX is proven.

Important constraint:

- Codex app-server WebSocket transport is currently documented as experimental, so v1 is suitable for trusted local-network use, not public internet exposure.

## 3. Desktop-Side Requirement

The user needs a Codex runtime on the laptop, but not necessarily a separate desktop GUI app.

Minimum required desktop setup:

- Install Codex CLI on the laptop.
- Authenticate Codex on the laptop with ChatGPT or an OpenAI API key.
- Run `codex app-server --listen ws://<laptop-ip>:4500`.

Optional desktop software:

- Codex desktop app on Windows is optional.
- If installed, it is useful for local desktop use, but the Android client should not depend on it.

## 4. Goals

- Let the user start, resume, and monitor Codex threads from Android.
- Stream turn output in real time.
- Let the user send prompts and follow-up steering input from the phone.
- Let the user review and approve command execution or file changes from the phone.
- Present all core actions in a minimal, elegant, low-noise UI.
- Keep the laptop as the execution boundary for shell commands, file changes, skills, and tools.

## 5. Non-Goals

- Running Codex models directly on the phone.
- Replacing the desktop Codex workflow completely.
- Public internet connectivity in v1.
- Multi-user collaboration in v1.
- Full parity with every Codex desktop or VS Code feature in v1.
- Building a custom OpenAI backend unrelated to Codex app-server.

## 6. Target Users

Primary user:

- A developer who already uses Codex on a laptop and wants a high-quality mobile companion for remote prompting, monitoring, and approvals.

Primary jobs:

- Check on an active Codex task away from the desk.
- Send a follow-up prompt while a task is running.
- Approve or decline commands and file changes.
- Start a quick new task from the phone.
- Review thread history and recent output.

## 7. Supported Host Platforms

Primary host target:

- Windows native with PowerShell and Codex native Windows sandbox support.

Secondary host target:

- Windows with WSL-hosted Codex.

Android client target:

- Android 10+ minimum target direction.
- Best experience on Android 12+.

## 8. Functional Scope

### 8.1 MVP

- Save one or more laptop connection profiles.
- Connect to laptop-hosted Codex app-server over WebSocket.
- Initialize the app-server session.
- Read auth/account state from the host.
- List recent threads.
- Start a new thread.
- Resume an existing thread.
- Send `turn/start`.
- Send `turn/steer`.
- Send `turn/interrupt`.
- Render streamed turn events and items.
- Render approvals and let the user accept, decline, cancel, or accept-for-session where supported.
- Display basic model and connection info.
- Persist thread summaries and recent UI state locally for quick reload.

### 8.2 Post-MVP

- QR-code based pairing/bootstrap.
- Local network discovery.
- Review mode support.
- Skills browser.
- Apps/connectors browser.
- Archived thread browsing.
- Richer diff preview for file changes.
- Tablet two-pane layout.
- Optional desktop companion relay for pairing, auth, and hardened transport.

## 9. Codex App-Server Methods for V1

Required request/response methods:

- `initialize`
- `thread/list`
- `thread/start`
- `thread/resume`
- `thread/read`
- `turn/start`
- `turn/steer`
- `turn/interrupt`
- `model/list`
- `account/read`

Required notifications and server requests:

- `thread/started`
- `thread/status/changed`
- `turn/started`
- `turn/completed`
- `item/started`
- `item/completed`
- `item/agentMessage/delta`
- `item/reasoning/summaryTextDelta`
- `item/commandExecution/outputDelta`
- `item/commandExecution/requestApproval`
- `item/fileChange/requestApproval`
- `serverRequest/resolved`
- error events

Optional but planned:

- `review/start`
- `skills/list`
- `app/list`
- `thread/archive`
- `thread/unarchive`

## 10. UX Product Principles

- Minimal by default: show only the information needed to act.
- Elegant, not generic: avoid stock chatbot styling.
- Fast to scan: hierarchy should prioritize status, active work, and pending approvals.
- Touch-first: every primary action reachable with one thumb.
- Calm motion: subtle transitions, no decorative animation.
- Respect the laptop boundary: the phone is a control surface, not the execution environment.

## 11. Visual Design System

### 11.1 Design Direction

The UI should feel like a high-end developer tool on mobile: quiet, crisp, editorial, and intentional.

Desired tone:

- Clean
- Minimal
- Precise
- Warm-neutral
- Technical without looking industrial

Avoid:

- Purple-heavy AI styling
- Bright gradient-heavy "consumer AI" visuals
- Rounded bubble-chat excess
- Dense terminal-like clutter everywhere

### 11.2 Typography

- Headline font: `Manrope`
- Body font: `IBM Plex Sans`
- Monospace font: `JetBrains Mono`

Type hierarchy:

- Large editorial screen titles
- Compact section labels in all caps or semi-bold micro labels
- Monospaced inline code, commands, paths, and timestamps

### 11.3 Color Palette

Light theme base:

- Background: warm ivory
- Surface: soft paper white
- Primary text: charcoal
- Secondary text: muted stone
- Accent: deep teal
- Success: forest green
- Warning: muted amber
- Error: restrained brick red

Dark theme:

- Same hierarchy, not pure black
- Background should be deep graphite
- Accent should stay teal, not neon

### 11.4 Layout and Components

- Edge-to-edge layout
- Generous padding
- Strong vertical rhythm
- Card-based sections only where grouping adds value
- Large status pill for connection and turn state
- Composer anchored at the bottom with clear send and interrupt actions

### 11.5 Motion

- Fade/slide transitions in the 180ms to 240ms range
- Streaming text should feel live but not jittery
- Approvals should arrive with a subtle emphasis transition
- Support reduced motion system settings

### 11.6 Accessibility

- Minimum 4.5:1 contrast for text
- Minimum 48dp touch targets
- Clear focus states for keyboard and accessibility users
- Screen-reader friendly content descriptions for icons and status chips
- Do not rely on color alone for approvals, errors, or active states

## 12. Information Architecture

Primary navigation:

- Home
- Threads
- Approvals
- Settings

Home screen:

- Active connection card
- Current running turn, if any
- Quick actions
- Recent threads

Threads screen:

- Search
- Recent threads list
- Status chip per thread
- Archived threads later

Thread detail screen:

- Header with project, model, status, host
- Scrollable timeline of items
- Composer
- Interrupt action when active
- Approval banner when needed

Approvals screen:

- Queue of pending approvals
- Item preview
- Command or file-change context
- Accept / accept for session / decline / cancel actions

Settings screen:

- Saved hosts
- Theme
- LAN security warnings
- Connection test
- About app-server version and host environment

## 13. User Flows

### 13.1 First-Time Setup

1. User installs Android app.
2. User installs Codex CLI on laptop.
3. User authenticates Codex on laptop.
4. User starts laptop app-server over WebSocket.
5. User opens Android app and creates a host profile.
6. User enters host name, IP, port, and optional project label.
7. App tests connectivity and performs `initialize`.
8. App lands on Home and fetches threads.

### 13.2 Start a New Task

1. User taps New Thread.
2. App optionally lets the user pick a model from `model/list`.
3. User writes prompt.
4. App sends `thread/start`, then `turn/start`.
5. Thread detail opens and streams live events.

### 13.3 Continue Existing Work

1. User opens a thread from history.
2. App calls `thread/resume` or `thread/read` depending on state.
3. User sees prior thread items.
4. User sends follow-up text via `turn/start` or `turn/steer` if active.

### 13.4 Approval Handling

1. Server emits approval request.
2. App shows a persistent approval surface and local notification if app is backgrounded.
3. User reviews command or file context.
4. User accepts or declines.
5. App sends decision and updates thread timeline after `serverRequest/resolved`.

## 14. Technical Architecture

App architecture style:

- Single Android app module initially, with clean package boundaries.
- MVVM with unidirectional state flow.
- Repository layer backed by WebSocket JSON-RPC client.
- Kotlin coroutines and `StateFlow`.

Primary packages:

- `app`
- `core/designsystem`
- `core/network`
- `core/storage`
- `feature/home`
- `feature/threads`
- `feature/threaddetail`
- `feature/approvals`
- `feature/settings`

Key libraries:

- Kotlin 2.3.10
- Jetpack Compose
- Material 3 with custom design tokens
- Kotlinx Coroutines
- Kotlinx Serialization
- DataStore
- OkHttp WebSocket client
- Coil for any remote logos or host-provided images

Build direction:

- Prefer stable Android Gradle Plugin compatibility with Kotlin 2.3.10.
- Use Compose compiler/plugin versions aligned with Kotlin 2.3.10 at implementation time.

## 15. Networking and Protocol Layer

Transport:

- WebSocket to the laptop app-server.

Message protocol:

- JSON-RPC 2.0 style messages as documented by Codex app-server.

Connection behavior:

- One active socket connection per host session.
- Heartbeat or idle-state monitoring on the client side.
- Automatic reconnect with exponential backoff and jitter.
- On reconnect, refresh thread state from the server instead of trusting in-memory client state.

Overload handling:

- If app-server returns `-32001` overload errors, show a retrying state and back off automatically.

## 16. Data Model Direction

Core entities:

- `HostProfile`
- `ConnectionState`
- `ThreadSummary`
- `ThreadDetail`
- `TurnState`
- `TimelineItem`
- `PendingApproval`
- `ModelInfo`

Timeline item types to support in UI:

- User message
- Agent message
- Reasoning summary
- Command execution
- File change
- MCP tool call
- Web search
- Review-mode markers
- System status item

## 17. Local Storage

Persist locally:

- Saved host profiles
- Last successful connection target
- Recent thread summaries
- UI preferences
- Draft composer text per thread

Do not persist:

- OpenAI API keys for the laptop-hosted runtime
- ChatGPT access tokens from the laptop runtime
- Raw host filesystem content beyond thread data needed for UI cache

Storage choices:

- DataStore for preferences and lightweight persisted state
- Optional Room later if thread caching becomes more complex

## 18. Security Model

V1 security posture:

- Trusted LAN only
- No direct internet exposure
- User must explicitly enter or confirm a local host endpoint

Important risk:

- Codex app-server does not provide a mobile-specific pairing or end-user auth layer by default in this architecture.
- A raw LAN WebSocket endpoint is not sufficient for public or untrusted-network use.

V1 mitigations:

- Strong in-app warning that the host must stay on a trusted network
- Default documentation to bind only to a private LAN IP
- Recommend firewall scoping on the laptop
- Allow users to disable saved hosts quickly

Future mitigation:

- Add an optional desktop companion or secure local relay that provides pairing, device authorization, and TLS termination.

## 19. Background Behavior

When a turn is active:

- Keep a foreground service alive while the user chooses to monitor an active task.
- Show a persistent notification with thread title and current state.
- Surface approval events as high-priority notifications.

If the app is killed:

- The phone loses the live socket.
- The laptop-side Codex work continues.
- On reopening, the app reconnects and refreshes thread state.

## 20. Error Handling

User-facing states:

- Connecting
- Connected
- Reconnecting
- Disconnected
- Host unavailable
- Server overloaded
- Approval expired
- Turn failed

Error UX rules:

- Errors should be human-readable first, technical second.
- Always provide retry or recover actions.
- Preserve draft input on failures.
- Keep failure surfaces visually restrained, not alarming by default.

## 21. Performance Targets

- Cold start to usable host list under 1.5 seconds on a modern device
- Thread list load under 1 second after connection on LAN for normal histories
- Streaming latency should feel near-real-time on stable Wi-Fi
- UI must remain smooth while command output streams continuously

## 22. MVP Acceptance Criteria

- User can connect from Android to a Windows laptop running Codex app-server on the same LAN.
- User can see recent threads.
- User can start a new thread and send a prompt.
- User can view streamed agent output in real time.
- User can send a follow-up prompt.
- User can interrupt an active turn.
- User can receive and respond to command and file-change approvals.
- UI looks polished, minimal, and coherent in light and dark themes.

## 23. Phased Roadmap

### Phase 1: Foundation

- Host profiles
- WebSocket connection
- Thread list
- Thread detail
- Streaming output
- Approvals
- Minimal settings

### Phase 2: Power Features

- Model picker
- Review mode
- Skills and apps/connectors list
- Better diff previews
- Search and filters
- Tablet layout

### Phase 3: Hardening

- QR bootstrap
- Optional secure desktop companion
- Better discovery and diagnostics
- Remote access strategy beyond LAN

## 24. Open Questions

- Should v1 support only one active host connection at a time, or multiple simultaneous hosts?
- Should the first release include a model picker, or keep model choice entirely laptop-side?
- Should thread creation require choosing a project/cwd, or use the host default and keep the phone UI simpler?
- Is a lightweight desktop bootstrap script enough for secure pairing, or do we want a formal companion app later?

## 25. Recommendation

Proceed with a focused MVP:

- Kotlin 2.3.10
- Jetpack Compose
- Windows-hosted Codex CLI app-server
- Android LAN client
- Minimal but premium mobile UX
- Approvals-first feature set

This is the highest-leverage path because it proves the product value quickly while keeping architecture aligned with the official Codex app-server surface.

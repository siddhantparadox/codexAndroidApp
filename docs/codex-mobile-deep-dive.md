# Codex Mobile Deep Dive

## Overview

Codex Mobile is a native Android companion for Codex. It lets a user monitor, steer, and approve desktop Codex work from a phone while the actual execution stays on the desktop.

The product is intentionally narrow:

- the desktop is the execution boundary and source of truth
- the phone is a rich control surface, not a model runtime
- the connection model is LAN-first
- the desktop host is started by running `npx codexremote`

What the app supports today:

- connect to a desktop by QR code, short code, or manual host entry
- restore and maintain an active desktop session
- browse many threads efficiently
- open live thread transcripts
- send follow-up prompts
- approve or decline blocked actions
- inspect diffs and technical execution history
- view local usage analytics derived from desktop session logs

This is a power-user companion app, not a general-purpose consumer chat app.

## Problem and Product Thesis

Codex workflows are often long-running. A desktop session may continue working, ask for approval, or finish with a useful result after the user has stepped away from the laptop. That creates three obvious problems:

1. progress becomes hard to monitor away from the desk
2. approvals block the desktop flow until the user comes back
3. the most useful operational context stays trapped in the desktop UI

Codex Mobile exists to solve that without trying to recreate the full desktop experience. The product thesis is simple: keep Codex on the desktop, but make the phone good enough to supervise and steer real work.

## System Design

System flow:

- the Android app connects to the `codexremote` bridge over LAN WebSocket on port `4500`
- the Android app reads usage analytics from the usage sidecar over LAN HTTP on port `4501`
- the bridge talks to `codex app-server` locally over `stdio`
- the usage sidecar reads local Codex session logs from `CODEX_HOME`

There are four runtime pieces:

1. **Android app**
   - native Kotlin app with Compose UI
   - holds UI state, host profiles, cached thread state, and connection state
   - talks to the desktop bridge over WebSocket and the usage sidecar over HTTP

2. **`codexremote`**
   - desktop bootstrap command
   - starts or reuses the mobile bridge on `4500`
   - starts or reuses the usage sidecar on `4501`
   - prints QR and short connection codes for the phone

3. **Bridge server**
   - runs on the desktop
   - launches `codex app-server` behind the scenes over `stdio`
   - exposes a WebSocket endpoint the phone can connect to
   - currently accepts one active mobile client at a time

4. **Usage Wrapped sidecar**
   - reads local Codex session history from `CODEX_HOME`
   - computes usage and activity summaries
   - exposes them to the phone over a small HTTP surface

This design keeps execution, permissions, and local session data on the desktop while letting the phone operate as a remote surface.

## Stack and Project Structure

### Stack

- Kotlin
- Jetpack Compose
- Material 3
- Android ViewModel + StateFlow
- Navigation Compose
- Kotlin serialization
- OkHttp WebSockets
- Google code scanner for QR bootstrap
- Node.js for `codexremote`

Build profile:

- `compileSdk` 36
- `targetSdk` 36
- `minSdk` 29
- Kotlin `2.3.10`
- Android Gradle Plugin `8.13.2`

### Repository layout

- `app/`: Android client
- `codexremote/`: desktop bridge CLI
- `usage-wrapped-service/`: Kotlin JVM usage-history module
- `docs/plans/`: architecture and UX decision docs

Inside the Android app, code is split by responsibility:

- `app/`: application shell and graph
- `navigation/`: routing
- `core/`: models, repository contract, persistence, transport, theme
- `feature/`: screen-specific UI and state

## Android Architecture

The app uses a repository-centered architecture.

### App shell

The entry path is:

- `CodexMobileApp`
- `MainActivity`
- `CodexMobileRoot`
- `CodexNavHost`

`CodexMobileRoot` is the app-level coordinator. It applies theme preference, manages app-wide alerts, requests notification permission when needed, and starts the background connection service while a host is active.

Top-level destinations are:

- Dashboard
- Threads
- Approvals
- Settings

Secondary destinations are:

- Host Connection
- Usage Wrapped
- Thread Detail

### State model

`CodexRepository` is the interface the UI depends on. It exposes flows and actions for:

- hosts
- connection state
- account and rate limits
- thread summaries and thread detail
- approvals and tool prompts
- usage wrapped analytics
- unread result digests and in-app notifications

`AppServerCodexRepository` is the main implementation. It is the orchestration layer for:

- local state restore and persistence
- session lifecycle and reconnect logic
- thread snapshot merging
- approval and request tracking
- composer catalog refresh
- unread result and notification handling

This keeps protocol and synchronization complexity out of the screens and ViewModels.

### Transport and persistence

The transport stack is split into two layers:

- `CodexJsonRpcTransport`: raw WebSocket request/response and server-event handling
- `CodexAppServerSession`: semantic app-server operations like `thread/list`, `thread/read`, `turn/start`, `turn/interrupt`, `model/list`, and `skills/list`

Local persistence is file-backed rather than database-backed. `AppLocalStateStore` stores:

- app preferences
- remembered hosts
- cached thread items
- persisted thread settings

This was a deliberate choice. The app needs fast reload and continuity, not a heavy relational offline model.

### Dependency wiring

The app uses a lightweight manual graph in `CodexAppGraph` rather than Hilt. That keeps the app boot path explicit and low-ceremony while the dependency graph is still small.

## Feature Design

### Dashboard

The Dashboard is the control-room surface. It combines:

- active host and connection state
- active thread
- recent threads
- quota snapshot
- a usage-wrapped entry point
- account information

Its job is to answer two questions quickly: "Is my desktop connected?" and "What needs attention right now?"

### Threads

The Threads screen evolved from a flat list into a directory-grouped view. Threads are now:

- grouped by visible workspace folder
- sorted by recency within each group
- collapsible by folder
- limited to a few visible rows per folder with `Show more`
- searchable by thread title, preview, and visible directory name

The row design was also tightened:

- first line: title, optional digest, relative time
- second line: compact metadata like `status • model • branch`

This was done to make the screen denser and more useful on a phone without turning it into a wall of cards.

### Thread Detail

Thread Detail is the most important screen in the app. It separates the transcript into three different kinds of content:

- conversational messages
- technical execution history
- decisions and requests

Instead of rendering the raw item stream directly, the app first converts it into a transcript-row model. That row model includes:

- user messages
- agent messages
- technical strips
- approval cards
- tool input cards
- pending placeholders

This allows the screen to be designed for comprehension rather than blindly mirroring the protocol.

The key UX decision here is the **technical pill strip**. Technical artifacts such as reasoning, commands, patches, MCP calls, web search, and tool activity are compressed into colored pills. Tapping a pill opens a lightweight inline inspector, and full formatted content opens in a bottom sheet. Diffs use a dedicated diff review sheet.

This design solves two problems at once:

- it keeps the conversation readable on mobile
- it reduces the amount of heavy UI work in the main scroll path

### Approvals

Approvals are surfaced both inline in threads and in a dedicated Approvals screen. The dedicated queue handles:

- command approvals
- file change approvals
- permission approvals
- tool-driven approval-like prompts

This is critical because mobile is most valuable when it can unblock desktop work remotely.

### Usage Wrapped

Usage Wrapped is the analytics surface. It shows:

- overview metrics
- quota windows
- activity heatmap
- highlights
- token totals
- approximate API-equivalent cost

The important product choice is that these analytics come from **local desktop history**, not from a cloud backend.

### Host Management and Pairing

The Host Connection flow supports:

- QR bootstrap
- short code bootstrap
- manual host entry
- remembered host selection
- rename and remove actions

This matters because setup is part of the product. If the user cannot reliably connect the phone to the right desktop, the rest of the app is irrelevant.

## Important Product and Architecture Decisions

### Thin client, rich client

The phone is intentionally a thin client in terms of execution and a rich client in terms of UX. The desktop runs Codex. The phone makes that desktop workflow observable and steerable.

### LAN-first instead of a hosted backend

The app is designed around a trusted local network and a user-run desktop bridge rather than a relay service.

Benefits:

- low cost
- simple deployment
- privacy-friendly for a power-user tool
- fast product iteration

Trade-off:

- requires a running desktop bridge
- not intended for public-internet exposure

### File-backed state instead of Room

The app persists a small amount of important state without committing to a full offline database architecture. This is enough for continuity and reload speed.

### One-client desktop bridge

The bridge accepts one active mobile client at a time. That keeps the bridge simpler and aligned with the real product assumption: one person, one desktop, one companion phone.

## Reliability and Lifecycle

Reliability mattered because the app is useless if it frequently lands in a broken reconnect state.

The repository handles:

- restoring the active host on startup
- reconnecting when the app returns or the service restarts
- preserving useful local cache across reconnect
- tracking clear connection phases like `Connecting`, `Reconnecting`, `Connected`, `Disconnected`, and `Error`
- retrying with backoff

Host matching and host upsert logic were also tightened so rescanning a known desktop updates the remembered host rather than leaving stale endpoint data behind.

The biggest Android lifecycle decision was adding `ConnectionForegroundService`. A normal backgrounded app cannot be trusted to hold a socket forever, so the app uses a foreground service with an ongoing notification to keep the desktop session alive while a host is active.

## Performance and UX Work

Thread-heavy UI was one of the hardest parts of the product. The main performance and readability decisions were:

- building a transcript-row model instead of rendering raw protocol items directly
- keeping list items typed and stable for lazy rendering
- reducing whole-list invalidation where possible
- fast-pathing simple text rendering
- moving heavy technical detail out of the main scroll path
- replacing heavy thread cards with denser list rows

The technical pill strip and bottom-sheet detail model was especially important. It improved both scanability and scroll performance on large threads.

## Quality Strategy

Quality work in this project was practical rather than ceremonial.

### Real device first

The project explicitly prioritizes real Android phone testing over emulator-first validation, especially for:

- QR scanning
- connection lifecycle
- foreground service behavior
- notifications
- transcript performance

### Automated coverage

The repo includes unit and UI tests for logic-heavy areas such as:

- thread presentation helpers
- thread folder section grouping
- diff parsing
- transcript row building
- connection bootstrap parsing and matching
- host upsert behavior
- threads filtering and search

The desktop-side bridge and usage aggregation code also have their own tests.

### Operational visibility

The app uses lightweight structured logging through `AppLog` so connection and thread lifecycle issues can be investigated during real-device testing.

## What We Actually Shipped

This is not just a mockup. The implemented app includes:

- native Android shell and navigation
- QR, short code, and manual bootstrap flows
- `codexremote` desktop bootstrap command
- desktop bridge to `codex app-server`
- usage-wrapped local analytics
- thread list with folder grouping and search by directory name
- compact thread rows with better title fallback
- full thread transcripts with live updates
- technical pill strip with bottom-sheet detail model
- diff review flow
- approval queue and inline approvals
- user-input and dynamic-tool request handling
- reconnect and host restore behavior
- background connection via foreground service

## Trade-Offs and Limitations

The app is coherent because it accepts a clear boundary rather than pretending to be everything.

Current limitations:

- LAN-first, not internet-first
- depends on a running desktop bridge
- cleartext local-network transport
- no cloud relay or hosted account layer
- no desktopless mode
- one active mobile client per desktop bridge

Those are real constraints, but they are also what kept the product focused and shippable.

## Result

Codex Mobile proves a useful product shape: a phone can be a serious companion to a desktop Codex workflow if the desktop remains the execution environment and the phone is optimized for monitoring, approvals, and transcript navigation.

The strongest outcomes of the project are:

- a clean system design that keeps risk and complexity on the desktop side
- a mobile UX that can handle dense thread history without collapsing into noise
- a practical approval and steering workflow away from the laptop
- a setup story built around a single command, a QR code, and a trusted local network

That is the core story of the app.

# Codex Mobile Polished Demo Design

## Goal

Build a polished Android demo that translates the approved Stitch project into a real Jetpack Compose app shell. The first milestone optimizes for demo quality and interaction polish rather than live Codex app-server integration.

## Source Of Truth

- `spec.md`
- Stitch project `17475932543340911584`
- Stitch screens:
  - Dashboard
  - Threads
  - Thread detail / active session
  - Approval queue
  - Settings
  - Host connection manager

## Recommended Approach

Use a polished vertical demo:

- real Android app structure
- real navigation and theme system
- fake repository with believable Codex state and interactions
- architecture ready for a future LAN/WebSocket repository swap

This gives the strongest polished-demo outcome without blocking on transport work.

## Architecture

- Single `:app` module for milestone one
- Clear package boundaries:
  - `app`
  - `core/data`
  - `core/designsystem`
  - `core/model`
  - `feature/*`
- Hilt + StateFlow + Navigation Compose

## UI Direction

- Warm ivory light theme
- Graphite dark theme
- Deep teal accent
- Editorial hierarchy, soft cards, pill chips, restrained motion
- Bottom navigation on top-level screens only
- Thread detail uses a focused full-screen composer layout

## Milestone Scope

- Dashboard
- Threads list with search and filter
- Thread detail with timeline and reply composer
- Approval queue with state transitions
- Settings with theme toggles
- Host connection manager with saved hosts and add form

## Deferred

- Live Codex app-server WebSocket integration
- Foreground service / notifications
- Persistence beyond in-memory demo state
- Real diff rendering and approval protocol handling

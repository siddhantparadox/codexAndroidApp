# Multi-Thread In-App Results Design

## Goal

Allow the user to work across multiple active Codex threads from mobile without
losing awareness of background progress, completions, approvals, or patch-ready
results.

## Product Direction

- Keep notifications in-app only for V1.
- Treat the Threads screen as the main coordination surface for concurrent work.
- Keep the UI calm and lightweight instead of introducing a heavy inbox or
  notification center.
- Make background thread progress visible without forcing the user back into a
  thread detail screen.

## App-Server Constraints

- Concurrent work is thread-scoped, not app-scoped. `turn/start`,
  `turn/steer`, `turn/completed`, and `thread/status/changed` are all emitted
  against a `threadId`.
- `thread/list` returns stored thread summaries including runtime `status`.
- `thread/read` can load stored thread detail without resuming the thread and is
  the right source when the UI needs richer result summaries for a thread the
  user has not reopened.
- `turn/diff/updated` provides the latest aggregated unified diff for the active
  turn, but item notifications remain the authoritative source of truth for
  thread content.

## UX Summary

### Threads list

- Multiple threads may be running at once.
- The user can send a message in one thread, leave, open another thread, and
  send again without blocking on the first thread.
- Thread cards should communicate one of four primary runtime outcomes:
  `Running`, `Needs Approval`, `Done`, or `Failed`.
- Running cards should use a subtle animated treatment, not a loud full-width
  banner inside the list.

### Top completion stack

- When an off-screen thread finishes, show a compact in-app completion banner at
  the top of the app shell.
- Each banner should:
  - identify the thread
  - summarize the outcome
  - open the thread on tap
  - auto-dismiss after about four seconds
  - support swipe dismissal
- If multiple threads complete close together, stack up to three banners and
  collapse any additional items into a `+N more` summary row.

### Unread result digest on cards

- When a thread completes and the user has not reopened it yet, the thread card
  should show a concise unread digest below the preview.
- Example digests:
  - `Patch ready · 2 files · +18 -4`
  - `Reply ready`
  - `Command failed`
  - `Approval needed`
- The digest clears after the user opens the thread.

## Interaction Model

### Running threads

- Thread cards show a pulsing status indicator or animated refresh glyph.
- The card remains tappable and should navigate into the live thread detail
  screen.
- The list should not reorder aggressively during a single visible interaction
  beyond normal `updatedAt` behavior.

### Completion

- If the user is viewing the completed thread when it finishes, do not show a
  top completion banner.
- If the user is elsewhere in the app, enqueue a completion banner.
- Completion banners are ephemeral awareness, not durable history.

### Approvals

- Approval-required threads stay visually distinct and should not be mixed into
  the generic completion banner treatment.
- Approval-needed cards continue to surface their status directly in the thread
  list and queue surfaces.

## Data Model Changes

Add a lightweight per-thread mobile-only result state layered on top of the
existing app-server data model.

- Track whether a thread has an unread mobile result.
- Track the latest result digest for a thread.
- Track whether a completion banner has already been emitted for the latest
  completion.
- Track the last observed active turn status so the client can detect
  `active -> completed`, `active -> failed`, and `active -> approval` changes.

Suggested shapes:

- `ThreadResultDigest`
  - `kind`: reply ready, patch ready, command failed, approval needed, other
  - `title`
  - `supportingText`
  - `fileCount`
  - `addedLineCount`
  - `removedLineCount`
- `InAppThreadNotification`
  - `id`
  - `threadId`
  - `title`
  - `message`
  - `createdAtEpochSeconds`

These models should live in the mobile client layer and should not alter the
serialized app-server wire models.

## Digest Synthesis

### Patch-ready threads

- Use `ThreadItem.FileChange` as the primary source.
- Aggregate:
  - changed file count
  - total added lines
  - total removed lines
- Reuse the existing diff parsing logic already added for thread detail.

### Reply-ready threads

- Prefer the final `agentMessage` text when there is no file change and no
  failure.
- Use a short summary rather than the full assistant answer.

### Failed threads

- Prefer the latest failed `commandExecution` or final failed turn status.
- Surface a short failure message and keep the full details inside the thread.

### Approval-needed threads

- Prefer existing approval state from the repository and keep the digest simple:
  `Approval needed`.

## Data Flow

### Real-time path

- Continue listening to `turn/started`, `turn/completed`, `item/*`, and
  `thread/status/changed`.
- When a thread transitions out of `Running`, evaluate whether a digest or
  banner should be created.

### Detail hydration path

- If the thread was not open and the client lacks enough item detail to produce
  a digest, call `thread/read(includeTurns=true)` after completion.
- Hydrate only the minimum needed to build the digest and mark the thread as
  unread-ready.

### Clearing unread state

- When the user opens a thread, clear any unread digest and remove any queued
  completion banner for that thread.

## UI Architecture

### No new UI library

- Do not add a third-party notification or banner library.
- Use Jetpack Compose primitives:
  - `AnimatedVisibility`
  - `animateItem`
  - `SwipeToDismissBox`
  - `LazyColumn`
  - a custom overlay host near the app root

### Screen responsibilities

- `ThreadsScreen`
  - show running state
  - show unread result digests
  - keep manual and background sync behavior
- app-level scaffold or nav host
  - render the in-app completion banner stack so it can appear above any screen
- repository
  - detect thread lifecycle transitions
  - synthesize digests
  - emit in-app notifications

## Visual Direction

- Keep the current premium minimal visual language.
- Completion banners should feel like lightweight live system notices, not chat
  bubbles.
- Use tinted surfaces and restrained motion.
- Keep stacked banners narrow in height and aligned with the current card
  radius, spacing, and typography.

## Edge Cases

- If several threads finish at once, cap the visible stack and collapse the
  rest.
- If the same thread completes, then re-enters a running state, treat that as a
  new lifecycle and replace the prior unread digest when it finishes again.
- If a thread is archived or removed before a banner is tapped, dismiss the
  banner silently.
- If digest hydration fails, fall back to a generic `Thread complete` message.

## Testing

- Unit test lifecycle transition detection for:
  - running -> completed
  - running -> failed
  - running -> approval needed
  - completed -> running -> completed
- Unit test digest synthesis for:
  - file changes
  - command failures
  - final reply only
- UI test banner queue behavior:
  - auto-dismiss
  - swipe-dismiss
  - tap-to-open
  - stacked overflow
- Device verification:
  - start work in thread A
  - back out and start work in thread B
  - confirm both cards show running state
  - confirm a banner appears only for the off-screen completion
  - confirm unread digest appears on the thread card until opened

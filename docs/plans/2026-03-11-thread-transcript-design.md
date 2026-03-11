# Thread Transcript Design

## Goal

Render thread detail as a real chat transcript that matches the Codex app-server
surface without inventing non-server concepts.

## Transcript Contract

- `userMessage` renders as right-aligned user chat bubbles.
- `agentMessage` renders as left-aligned Codex chat bubbles.
- `agentMessage.phase=commentary` renders as an in-progress Codex working bubble.
- `agentMessage.phase=final_answer` renders as the final Codex answer bubble.
- All other `ThreadItem` variants render as structured inline transcript cards in
  chronological order.
- Lower-level non-item events that affect the conversation remain visible in a
  secondary activity/details treatment rather than being forced into chat bubbles.
- Approvals remain app-server-driven and render inline for the matching thread.

## Supported App-Server Surface

The transcript model should represent the current app-server item surface:

- `userMessage`
- `agentMessage`
- `plan`
- `reasoning`
- `commandExecution`
- `fileChange`
- `mcpToolCall`
- `dynamicToolCall`
- `collabToolCall` / schema variant `collabAgentToolCall`
- `webSearch`
- `imageView`
- `imageGeneration`
- `enteredReviewMode`
- `exitedReviewMode`
- `contextCompaction`

The thread screen should also surface the relevant non-item event stream:

- `turn/started`
- `turn/completed`
- `turn/diff/updated`
- `turn/plan/updated`
- `thread/status/changed`
- `thread/tokenUsage/updated`
- `mcpToolCall/progress`
- `terminalInteraction`
- realtime/raw-response passthrough events when present

## Data Model

- Replace the narrow thread item model with app-server-shaped transcript types.
- Keep a stable per-item `id` so deltas can append into the right transcript
  entry.
- Preserve item ordering from thread turns and live notifications.
- Store secondary activity rows separately from primary transcript items so the UI
  can present them with lower emphasis.
- Keep approval items separate, then merge them into thread UI by `threadId`.

## Rendering Rules

### Chat bubbles

- User text and input metadata stay in a single user bubble.
- Codex commentary and final answers stay in Codex bubbles.
- Bubble layout should read like a messaging app first, then a developer tool.

### Structured cards

- Plans render as plan cards.
- Reasoning renders as expandable reasoning cards with summary first and raw text
  second when available.
- Commands render with command, cwd, parsed action hints, streamed output, exit
  code, and duration.
- File changes render with summary plus expandable change rows and diff text.
- MCP, dynamic, collab, and web-search items render as tool/action cards with
  status, inputs, and outputs.
- Review mode, compaction, image viewer, and image generation items render as
  compact system cards.

### Activity/details

- Turn lifecycle, token usage, terminal input, MCP progress, and other
  lower-level event notifications render in a lower-emphasis expandable details
  rail/section on the thread screen.

## Scroll Behavior

- Opening a thread lands at the latest transcript item.
- Live streaming auto-scrolls while the user remains pinned near the bottom.
- If the user scrolls upward, auto-scroll pauses until they return near the
  bottom.

## Implementation Notes

- Mapper/reducer work comes first so the UI has complete transcript data.
- UI work should split bubble rendering from structured card rendering to avoid a
  single overloaded renderer.
- Existing approvals flow should be reused and threaded into the detail screen
  instead of creating a second approval model.

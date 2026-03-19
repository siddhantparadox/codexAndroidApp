# Thread Message Rendering Design

## Goal

Upgrade thread user and Codex message bubbles into a native Android transcript
viewer with selectable text, explicit copy actions, tighter spacing, and
substantially better markdown fidelity.

## Decision

- Keep the app server as the content source. It continues emitting plain message
  text and markdown-like content.
- Move rendering and interaction ownership fully into the Android client.
- Replace the current hand-rolled rich-text parser with a Compose-native
  markdown renderer.
- Preserve Codex-specific UX on top of markdown rendering rather than pushing
  HTML or server-rendered UI into the mobile app.

## Library Choice

- Adopt `com.mikepenz:multiplatform-markdown-renderer` with the Material 3
  module.
- Do not use a WebView for transcript rendering.
- Do not expand the existing custom parser beyond small compatibility helpers.

Rationale:

- The library already supports headings, lists, block quotes, code blocks,
  tables, links, and larger documents.
- The app already uses Jetpack Compose and Material 3, so the integration fits
  the current stack.
- A WebView would make selection, copy, accessibility, and app-specific link
  actions harder to control.

## In Scope

- User and Codex message bubbles in thread detail.
- Selectable message text via long press.
- Visible per-message copy action.
- Better rendering of markdown links, code, lists, emphasis, headings, block
  quotes, and tables.
- Reduced outer margins and denser bubble layout.
- Reuse of the same markdown renderer in transcript-adjacent technical content
  where practical.

## Out of Scope

- Arbitrary raw HTML rendering from model output.
- Server-side formatting or HTML generation.
- A full in-app document/file viewer for markdown links in this change.
- Syntax highlighting and image markdown support unless the library integration
  makes them nearly free.

## Rendering Contract

- `ThreadItem.UserMessage` and `ThreadItem.AgentMessage` continue to be the
  canonical message types.
- Message text is rendered as markdown in the client.
- External links should open through Android link handling.
- Local Codex file-path links should remain recognizable for later app-specific
  routing, even if the first version falls back to plain link handling or copy.
- Code fences should render inside distinct code containers with a dedicated
  copy affordance.

## Interaction Contract

- Long-pressing message text should enter text selection.
- Every user and Codex bubble should expose a lightweight copy affordance.
- Copying a whole message and copying a single code block are separate actions.
- Bubble-level action UI must not interfere with text selection or link taps.

## Layout Changes

- Tighten transcript horizontal padding from the current screen margin.
- Slightly reduce bubble interior padding.
- Increase usable bubble width so messages feel less cramped on phones.
- Keep the existing left/right bubble alignment and activity/technical strip
  separation.

## Testing

- Add unit coverage for any new markdown helper or message-action helper logic.
- Add targeted UI verification for copy affordances and rendered markdown states
  where practical.
- Run Kotlin/unit validation plus a debug build to catch Compose API mismatch.

## Risks

- Markdown library defaults may not match the existing design system, so wrapper
  styling is required.
- Selection, link taps, and bubble actions can conflict if composed naively.
- Streaming updates can cause visible re-parse churn unless markdown state is
  retained across content updates.

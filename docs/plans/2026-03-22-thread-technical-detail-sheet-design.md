# Thread Technical Detail Sheet Design

## Goal

Keep all technical pills visible in the transcript while removing heavy technical
content from the scroll path. Selecting a pill should show a lightweight inline
inspector, and full formatted technical content should open in a bottom sheet on
demand.

## Decision

- Keep the full pill strip visible for scanability and timeline context.
- Replace the current expanded inline technical detail panel with a compact
  inline inspector.
- Move full markdown, code, JSON, patch, and attachment rendering into a
  bottom sheet that opens only when requested.
- Keep the existing diff review flow for file changes, but remove per-file diff
  cards from the transcript itself.

## Why This Approach

- It preserves the current "all pills are visible" requirement.
- It keeps the transcript focused on browsing and message reading instead of
  long technical payloads.
- It defers the most expensive text measurement and layout work until the user
  explicitly asks for it.
- It matches existing mobile patterns already used for diff review and other
  deep-detail interactions.

## Inline Inspector Contract

- Tapping a pill selects it and shows a compact inspector below the strip.
- The inspector contains:
  - item type badge
  - status or live state
  - title
  - short preview text
  - one primary action to open richer content
- The inspector must not render long markdown, code blocks, diff summaries, or
  large attachment previews.
- Only one pill can be selected at a time.

## Detail Sheet Contract

- The bottom sheet owns full technical presentation for the selected item.
- Full content remains formatted per item type:
  - markdown for plan, reasoning, prompts, and review notes
  - code blocks for command output and raw payloads
  - structured sections for MCP and tool arguments/results
  - attachment rendering for returned images
  - patch summaries with entry points into diff review
- The sheet is opened explicitly from the inline inspector, not by scroll or
  automatic expansion.

## File Change Behavior

- File change pills keep using the existing diff viewer sheet for actual diff
  inspection.
- The transcript-inline inspector for file changes becomes lightweight.
- Rich per-file summaries move out of the transcript and into the technical
  detail sheet.

## In Scope

- `ThreadTechnicalPillStrip` interaction and rendering changes.
- Thread-level bottom sheet state for selected technical content.
- Reuse of existing detailed renderers inside the new bottom sheet.
- Slimming inline patch behavior so it matches the new inspector model.

## Out of Scope

- Server-side lazy loading of technical item payloads.
- Changing pill ordering or grouping behavior.
- Reworking transcript data models beyond what is needed to open the sheet.
- Replacing the existing diff viewer itself.

## Risks

- Full technical rendering may need to be split into reusable inspector and
  sheet components to avoid duplicated logic.
- Opening a file-change detail sheet and then a diff review sheet must be
  coordinated cleanly to avoid awkward stacked-sheet behavior.
- Some technical item types may need custom primary-action labels to stay clear
  and compact.

## Verification

- Run unit and build validation for the refactor.
- Verify on the phone that heavy-thread scrolling feels better with no loss of
  pill visibility.
- Re-run focused frame metrics on the large technical thread after the change.

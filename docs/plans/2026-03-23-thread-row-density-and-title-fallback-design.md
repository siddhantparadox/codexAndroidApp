# Thread Row Density And Title Fallback Design

## Goal

Refine the nested thread rows on the Threads screen so they feel denser, cleaner, and more informative without drifting back toward bulky card UI. At the same time, remove the user-visible `Untitled thread` feeling from normal thread browsing by deriving a better display title when a thread arrives without a name.

## Chosen Approach

Keep the folder-based sectioned `LazyColumn`, but tighten the nested thread rows into a compact two-line treatment.

The first line remains the scan line: title on the left, optional inline digest when there is high-signal output, and relative time pinned on the right. The second line becomes a single muted metadata line built from `status • model • branch`, with blank values omitted and normal idle state hidden.

## Row Layout Rules

- Nested rows stay lightweight and tappable.
- Rows use less internal padding and smaller vertical gaps between siblings.
- Idle rows stay visually flat or near-flat.
- Only active, attention, or digest rows receive a subtle tinted background.
- The first line prioritizes title and time.
- Inline result digest remains optional and should disappear before time on narrow layouts.
- The second line is always a single ellipsized line.
- No chips, no third line, and no large supporting preview blocks in the folder list.

## Metadata Rules

- `status` is shown only for `Active`, `Needs Approval`, `Needs Input`, and `Error`.
- Normal idle or stored state is omitted from the metadata line.
- `model` uses the thread’s current model display name when present.
- `branch` uses the Git branch when present.
- Metadata items are joined with ` • ` and dropped when blank.

## Display Title Fallback

Thread screens should stop hardcoding `Untitled thread` as the default browsing label.

Display title fallback order:

1. `thread.name`
2. first meaningful preview text
3. `<folder name> thread`
4. final generic fallback only if everything else is blank

This fallback lives in the shared thread presentation layer so the Threads screen, dashboard, and thread detail header all use the same naming behavior.

## Scope

- Update the shared thread presentation helpers with:
  - display title fallback
  - compact metadata-line composition
- Update the nested thread row UI to consume those helpers.
- Tighten row spacing and `Show more` spacing in the Threads screen.
- Replace the screen-local hardcoded `Untitled thread` fallback in other thread surfaces with the shared helper.

## Testing

- Add unit tests for:
  - display title fallback order
  - compact metadata composition
  - idle-status omission
- Add a focused Compose/UI test to confirm the Threads row renders:
  - preview-derived title fallback
  - compact `status • model • branch` metadata


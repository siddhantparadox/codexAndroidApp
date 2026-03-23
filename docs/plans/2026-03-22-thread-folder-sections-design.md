# Thread Folder Sections Design

## Goal

Rework the Threads screen so threads are grouped by visible folder name instead of shown as a flat list. The grouped view should feel close to the approved reference: folder headers stay visible, threads are nested below each folder, large folders show only the most recent few items first, and expansion/collapse feels smooth on mobile.

## Chosen Approach

Use a single sectioned `LazyColumn` on the existing Threads screen.

Each section represents a folder group derived from thread `cwd`. The section header uses the final folder segment as the primary label. Section contents render lightweight thread rows instead of the current large thread cards. This keeps scroll behavior simple, avoids nested lazy lists, and matches the approved visual direction.

## Grouping Rules

- Filtered thread results are grouped after search and filter are applied.
- The visible folder label is the last path segment from `cwd`.
- Threads with blank or missing `cwd` are grouped under `Unknown Folder`.
- Sorting inside each folder is newest first by `updatedAtEpochSeconds`.
- Folder sections are ordered by the newest thread they contain.
- When two sections would show the same visible folder label, the UI shows a muted path subtitle to disambiguate them while still keeping the folder-name-first presentation.

## Section Behavior

- All folder sections start expanded.
- Each expanded section initially shows the first `4` threads.
- If a section contains more than `4` threads, a low-emphasis `Show more` row appears after the visible rows.
- Tapping `Show more` expands that section to show all of its threads.
- Tapping the folder header collapses or expands that section.
- Collapse and expand affect only the selected section.

## Row Design

- Replace the current bulky card treatment with a lighter row layout.
- Primary row line shows thread title on the left and relative time on the right.
- If a result digest is important, it can appear inline with the title area in a compact form.
- Secondary text is limited to a single muted supporting line when useful.
- Rows remain fully tappable to open the thread.

## State Ownership

- Search and filter state remain in `ThreadsViewModel`.
- Folder section derivation is implemented as pure mapping logic so it is deterministic and unit-testable.
- Collapse state and per-folder `Show more` state remain screen-local with `rememberSaveable`, because they are transient view preferences.
- Stable UI keys use a real folder identity, even when the visible folder label is shared across sections.

## Animation

- Folder headers include a chevron that rotates between expanded and collapsed states.
- Section contents expand and collapse with lightweight size and placement animation.
- Thread rows use subtle placement animation when rows are revealed or hidden.
- No nested scroll containers are introduced.

## Empty and Edge States

- If the filtered result set is empty, the screen shows a compact empty state instead of empty folder shells.
- Single-thread folders render without extra affordances.
- Duplicate visible folder names display a muted disambiguation path subtitle.
- Unknown-folder threads render in a dedicated fallback section.

## Testing

- Add unit tests for:
  - grouping by visible folder name
  - per-folder sorting
  - folder ordering by newest thread
  - duplicate-label disambiguation
  - unknown-folder fallback
  - initial visible-thread threshold and `Show more` behavior
- Add a focused Compose/UI test for:
  - folder collapse/expand
  - `Show more` expansion
- Manually verify on a real Android phone with:
  - a long folder
  - a single-thread folder
  - duplicate folder-name sections
  - a thread that shows a result digest


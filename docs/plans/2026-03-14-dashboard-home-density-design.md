## Dashboard Home Density Design

### Goal
- Reduce the visual weight of the active-thread card on the home screen.
- Add a compact view of the top three recent threads without introducing scroll.

### Approved Direction
- Keep the active thread as the primary hero, but shrink it to roughly the top half of the main content area.
- Use the remaining space for a `Recent threads` panel that shows the three most recently updated threads.
- Exclude the active thread from the recent list when it would otherwise duplicate the hero.

### UI Shape
- Header stays unchanged.
- Connection strip stays unchanged.
- Main content becomes a stacked split:
  - Compact active-thread card
  - Recent-threads card with three rows
- Footer stays unchanged.

### Recent Threads Row
- One tap target per row.
- Show thread title, status, and one short supporting line.
- Supporting line should prioritize workspace/meta plus relative update time.

### Data Rules
- Recent threads are sorted by `updatedAtEpochSeconds` descending.
- If an active thread exists, remove it from the recent list before taking the top three.
- If there is no active thread, use the top three threads overall.

### Constraints
- Preserve the fixed, no-scroll homepage.
- Preserve existing dashboard visual language and interaction patterns.

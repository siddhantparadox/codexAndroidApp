# Thread Directory Search Design

## Goal

Extend the existing Threads screen search so it matches the visible directory name in addition to thread title and preview text.

## Chosen Behavior

- Search continues to use the existing single text field on the Threads screen.
- A thread matches when the query appears in:
  - thread title
  - thread preview
  - visible directory name derived from the last `cwd` path segment
- Search does not match the full `cwd` path.
- Grouping behavior is unchanged. Filtering still happens first, then the remaining threads are grouped into folder sections.

## UI Change

- Update the search placeholder so it reflects the broader match scope.

## Testing

- Add a unit test that proves a thread can be found by directory name alone.
- Add a unit test that proves the full path does not become searchable by accident.

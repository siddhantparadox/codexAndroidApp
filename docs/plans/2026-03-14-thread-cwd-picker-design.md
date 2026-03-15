# Thread Cwd Picker Design

## Goal

Replace the direct `+` action on the Threads screen with a lightweight picker
that starts a new thread in one of the previously used working directories for
the currently connected desktop host.

## Product Decision

- V1 only supports choosing from existing `cwd` values already present in thread
  history.
- V1 does not support manual path entry.
- V1 does not browse the remote filesystem.

## App-Server Constraints

- `thread/start` accepts an optional `cwd`.
- Thread summaries already include `cwd`.
- The mobile client already refreshes thread summaries per active host, so the
  picker can derive its options from the currently loaded thread list.

## UX Summary

- Tapping `+` opens a modal bottom sheet titled `Start new thread`.
- The sheet contains:
  - a search field
  - a scrollable list of existing `cwd` paths
- Each row shows:
  - a folder icon
  - the `cwd` path
  - a subtle `last used` timestamp
- Tapping a row immediately starts a new thread with that `cwd` and navigates
  into the created thread.

## List Behavior

- Source data is the current host's thread summaries.
- Ignore blank `cwd` values.
- Deduplicate by exact path.
- Keep the most recently updated thread for each path.
- Sort by most recent `updatedAt`.
- Filter client-side with case-insensitive path search.

## Empty and Error States

- If no prior `cwd` values exist, show an empty state in the sheet instead of
  attempting thread creation.
- If search returns no matches, show `No matching folders`.
- If `thread/start` fails for the selected path, keep the sheet open and show an
  inline error message.

## Implementation Notes

- Extend the repository `createThread` API to accept an optional `cwd`.
- Send `cwd` in the `thread/start` payload only when it is non-blank.
- Keep existing thread creation navigation unchanged after success.
- Keep the `+` button disabled when the app-server connection is unavailable.

## Testing

- Unit test `cwd` option derivation:
  - ignores blank values
  - deduplicates exact matches
  - sorts by latest `updatedAt`
- Unit test client-side search filtering.
- Unit test `thread/start` payload generation with and without `cwd`.

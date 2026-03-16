# Host Connection Actions Design

Document status: Approved
Date: 2026-03-15

## Goal

Let users manage remembered desktop connections from the Android app without
making destructive actions too easy to trigger from the top-level settings
overview.

## Approved UX

- Keep [Settings] as an overview and entry point into the host manager. Do not
  place a destructive remove button inline on the settings cards.
- Add a trailing overflow action on each remembered desktop in the Host
  Connection screen.
- Open a bottom sheet with `Connect`, `Rename connection`, and
  `Remove connection`.
- `Rename connection` edits only the saved display name. Address and port stay
  unchanged.
- `Remove connection` opens a confirmation sheet. If the host is active, the
  sheet warns that removing it will disconnect the phone.
- If the removed host is the only saved connection, the app still allows the
  action, disconnects immediately, and shows an empty state instead of a list.

## Behavior Notes

- Removing the active host must not auto-fallback to another remembered
  desktop. The app stays disconnected until the user explicitly chooses another
  host or pairs again.
- After removing the last host, the Host Connection screen keeps the existing
  connect entry points visible: QR scan, code entry, and advanced manual entry.
- Save for rename is disabled when the trimmed value is blank or unchanged.

## Validation

- Add unit coverage for host rename and removal behavior, including removing the
  active host and removing the last saved host.

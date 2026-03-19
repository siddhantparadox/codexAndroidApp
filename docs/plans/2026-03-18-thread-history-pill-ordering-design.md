# Thread History Pill Ordering Design

## Goal

Restore technical pills to their original positions inside the transcript when a thread is reopened from desktop history. Pills must no longer be recovered only as late `item/*` notifications because that appends them to the end of the thread.

## Chosen Approach

Patch `codexremote` to augment `thread/read` and `thread/resume` responses from rollout JSONL before the bridge forwards them to Android. The rollout already contains chronological `response_item` entries for user messages, assistant messages, reasoning, and tool calls. The bridge will use that rollout sequence as the ordering skeleton, then fill in only the missing technical items.

## Why This Approach

- It preserves exact turn-local ordering instead of relying on Android-side insertion heuristics.
- It keeps the app-server response as the source of truth for transcript hydration.
- It reduces synthetic history replay to live updates only, which avoids appending historical pills at the end.

## Merge Rules

- Keep the existing app-server `thread.turns[*].items` entries whenever they already exist.
- Build a rollout-derived per-turn sequence of placeholders and technical items.
- For non-technical placeholders, consume the next matching existing app-server item of the same category.
- For technical entries, reuse an existing app-server item with the same id if present; otherwise inject the synthesized rollout item.
- Append any unmatched existing items at the end of the turn to avoid dropping data when rollout parsing is incomplete.

## Live Behavior

- Historical augmentation happens only on the response payload for `thread/read` / `thread/resume`.
- The rollout mirror still tails new desktop-origin items after open, but it should no longer replay historical tool items as notifications during hydration.

## Verification

- Add bridge-side tests that assert augmented `thread.turns[*].items` ordering.
- Keep the existing live-tail tests to ensure new desktop-origin tool items still stream after the thread is opened.

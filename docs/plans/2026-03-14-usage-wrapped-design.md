# Usage Wrapped Design

## Goal

Add a richer `Usage wrapped` experience to the Android app that summarizes a
user's Codex activity over time while preserving the current quick-access usage
sheet for live quota checks.

## Verified Data Sources

The official Codex app-server is sufficient for live quota only:

- `account/rateLimits/read`
- `account/rateLimits/updated`

These provide the current `5h` and `7d` quota windows, but not historical
analytics.

The desktop's local Codex home provides the history needed for a wrapped view:

- `~/.codex/sessions/*.jsonl`
  - contains `session_meta`
  - contains `turn_context`
  - contains repeated `token_count` events
- `~/.codex/session_index.jsonl`
  - contains thread names and update timestamps
- `~/.codex/history.jsonl`
  - useful for prompt history, but not the primary analytics source

Observed session data on the current desktop includes:

- session timestamps
- `cwd`
- `source`
- `model_provider`
- cumulative token counters:
  - `input_tokens`
  - `cached_input_tokens`
  - `output_tokens`
  - `reasoning_output_tokens`
  - `total_tokens`

## Product Direction

Split usage into two levels:

1. `Usage sheet`
   - keep the current bottom sheet behind the header icon
   - show live `5h` and `7d` quota usage from app-server
   - optimize for fast, in-the-moment checks

2. `Usage wrapped`
   - open a dedicated full-screen screen from the usage sheet
   - show historical activity derived from desktop-local Codex logs
   - optimize for reflection and trend discovery

This keeps the homepage lightweight and avoids forcing a dense analytics layout
into a bottom sheet.

## Best Mobile UX

The wrapped screen should not copy the desktop mock one-to-one. On phone, the
best layout is a vertically stacked dashboard with strong card hierarchy:

1. Hero summary
   - started date
   - active days
   - streak
   - sessions

2. Live quota row
   - `5h` usage
   - `7d` usage

3. Activity heatmap
   - default to the last 12 months
   - tap a day to show token totals and session count

4. Highlights
   - most active day
   - most active project
   - most used source (`cli`, `vscode`, etc.) if reliable

5. Token breakdown
   - input
   - cached input
   - output
   - reasoning

6. Optional later
   - top models
   - approximate cost

## Metrics That Are Safe For V1

These are supported by verified local data:

- first recorded session date
- sessions count
- active days count
- longest streak / current streak
- projects count by distinct `cwd`
- daily token totals
- weekly token totals
- token-type totals
- most active day
- most active project
- usage heatmap

## Metrics To Defer

These should not ship until data quality is proven:

- exact dollar cost
- top model leaderboard
- per-model spend
- monthly rollups that imply official billing accuracy

The session logs clearly expose token counters, but model labeling and pricing
need further validation before they become user-facing analytics.

## Architecture

The Android app should not parse raw desktop session logs directly.

Recommended architecture:

1. Desktop summary service
   - reads `~/.codex/sessions/*.jsonl`
   - computes a compact analytics summary
   - exposes it over the same trusted LAN pattern as the current desktop host

2. Android app
   - requests the precomputed summary
   - renders the wrapped screen
   - caches the last successful summary for offline viewing

This keeps parsing and aggregation close to the source data, avoids expensive
phone-side processing, and lets the mobile UI stay simple.

## Proposed Summary Contract

The mobile app should target a desktop-produced summary shaped roughly like:

```json
{
  "generatedAt": "2026-03-14T21:30:00Z",
  "range": {
    "start": "2025-08-30",
    "end": "2026-03-14"
  },
  "overview": {
    "startedAt": "2025-08-30",
    "activeDays": 54,
    "sessionCount": 147,
    "projectCount": 36,
    "currentStreakDays": 13,
    "longestStreakDays": 18
  },
  "liveQuota": {
    "fiveHourUsedPercent": 3,
    "fiveHourResetsAt": 1773537981,
    "weeklyUsedPercent": 11,
    "weeklyResetsAt": 1773866847
  },
  "tokenTotals": {
    "input": 1694841792,
    "cachedInput": 1608845987,
    "output": 8611895,
    "reasoning": 4302108,
    "total": 1703453687
  },
  "highlights": {
    "mostActiveDay": {
      "date": "2026-02-23",
      "totalTokens": 126909021
    },
    "mostActiveProject": {
      "cwd": "D:/projects/codexAndroidApp",
      "sessionCount": 41,
      "totalTokens": 228004991
    }
  },
  "activity": [
    {
      "date": "2026-03-14",
      "sessionCount": 4,
      "totalTokens": 340724
    }
  ]
}
```

## Android Scope

Once a desktop summary exists, the Android work is:

- add wrapped summary models
- add repository support for fetching wrapped summary data
- add a full-screen `Usage wrapped` destination
- add cached summary rendering
- link `Usage sheet` -> `Usage wrapped`

## Current Constraint

This repository only contains the Android client.

It does not include desktop host code that can read `~/.codex/sessions` and
serve the wrapped summary to the phone. That desktop-side surface must be added
before the mobile UI can be made fully functional.

## Recommendation

Implement in this order:

1. Desktop summary generator and LAN endpoint
2. Android summary models and repository contract
3. Full-screen wrapped UI
4. Cached offline rendering
5. Optional model and cost experiments

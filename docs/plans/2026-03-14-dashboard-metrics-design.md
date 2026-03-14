# Dashboard Metrics Design

## Goal

Add app-server usage and quota signals to the mobile homepage without requiring
scrolling.

## Product Direction

- Keep the homepage as a glanceable operational dashboard, not a thread feed.
- Preserve the current primary focus on connection health and the active thread.
- Add usage and quota as compact supporting telemetry.
- Fit the entire dashboard on a standard phone screen with no vertical scroll.

## App-Server Inputs

- `thread/tokenUsage/updated`
  - Use last-turn total tokens.
  - Use thread total tokens.
  - Derive context remaining from `modelContextWindow` when present.
- `account/rateLimits/read`
  - Use current used percent.
  - Use reset timestamp.
- `account/rateLimits/updated`
  - Keep the quota display live when the server emits updates.

## Layout Summary

The homepage should render as a fixed-height vertical dashboard with five zones:

1. Header row
   - app logo and title
   - compact search/profile affordances
2. Connection strip
   - host name
   - connection state
   - account summary
3. Active thread hero
   - thread title
   - status chip
   - compact metadata row
   - single-line preview
4. Metrics row
   - `Usage` card with last-turn tokens, thread total tokens, and context
     remaining
   - `Quota` card with ChatGPT used percent and reset time
5. Footer summary row
   - synced thread count
   - attention count
   - `View all` affordance

## UI Notes

- Replace the current `LazyColumn` stack with a fixed `Column`.
- Compress the connection card into a shorter strip.
- Keep the active thread as the largest visual block.
- Use a two-card grid for metrics to preserve scanability.
- Remove the recent-thread list from the homepage and summarize it in the
  footer.

## Data Model Changes

- Add a lightweight rate-limit model for the homepage.
- Expose rate-limit state through the repository.
- Extend dashboard UI state with:
  - usage summary
  - quota summary
  - thread count
  - attention count

## Non-Goals

- Do not show approximate dollar cost.
- Do not add charts or historical trend views.
- Do not add scrolling containers inside the homepage.

# Typography System Design

Document status: Approved
Date: 2026-03-14

## Goal

Make typography consistent across the app by defining a complete Material 3
type scale, removing accidental fallback styles, and introducing a small set of
 semantic text roles for repeated UI patterns.

The thread list should become visibly more compact without reducing readability.

## Current problems

- `Type.kt` defines only part of the Material 3 typography scale.
- The app uses slots that are not overridden in the theme, including
  `headlineSmall`, `titleSmall`, `bodySmall`, and `labelMedium`.
- Those missing slots fall back to Material defaults, which creates a mixed
  typography system.
- `labelSmall` is currently monospace globally, so timestamps, bottom-nav
  labels, and other small UI text use a code font even when they are not code.
- Several thread-detail surfaces explicitly copy in `FontFamily.Monospace`
  instead of using a shared semantic code style.
- Thread list cards read too large and too roomy for an inbox-style surface.

## Product decision

The first pass will keep the current clean sans-serif direction instead of
adding branded font assets. The work will focus on consistency, density, and
clear hierarchy first.

## Typography system

### Base font families

- Primary app text: `FontFamily.SansSerif`
- Code and technical text only: `FontFamily.Monospace`

Monospace should be reserved for:

- code snippets
- diff lines
- technical pill metadata
- inline command or file identifiers when styled as code

Monospace should not be the default for generic metadata, timestamps, nav
labels, or helper copy.

### Material scale

Define the full Material 3 type scale in `Type.kt`, even for slots that are not
heavily used today. The app should no longer rely on stock fallback typography.

Recommended compact scale:

- `headlineLarge`: 30sp / 36sp
- `headlineMedium`: 24sp / 30sp
- `headlineSmall`: 20sp / 26sp
- `titleLarge`: 18sp / 24sp
- `titleMedium`: 16sp / 22sp
- `titleSmall`: 15sp / 20sp
- `bodyLarge`: 16sp / 24sp
- `bodyMedium`: 14sp / 20sp
- `bodySmall`: 13sp / 18sp
- `labelLarge`: 12sp / 16sp
- `labelMedium`: 11sp / 16sp
- `labelSmall`: 10sp / 14sp

Letter-spacing should remain subtle and only be applied where it has clear UI
value, mainly labels and large headlines.

## Semantic text roles

Add design-system typography aliases so features stop choosing random Material
slots directly for common UI patterns.

Recommended roles:

- `screenTitle`
- `panelHeadline`
- `cardTitle`
- `listItemTitle`
- `bodyText`
- `supportingText`
- `denseSupportingText`
- `sectionLabel`
- `statusText`
- `metaText`
- `codeInline`
- `codeBlock`

Role mapping:

- `screenTitle` -> `headlineMedium`
- `panelHeadline` -> `headlineSmall`
- `cardTitle` -> `titleMedium`
- `listItemTitle` -> `titleMedium`
- `bodyText` -> `bodyLarge`
- `supportingText` -> `bodyMedium`
- `denseSupportingText` -> `bodySmall`
- `sectionLabel` -> `labelLarge`
- `statusText` -> `labelLarge`
- `metaText` -> `labelSmall`
- `codeInline` -> `labelMedium` + monospace
- `codeBlock` -> `bodySmall` + monospace

The semantic layer should be small and practical. It is not meant to replace
Material typography entirely, only to stabilize the app’s repeated patterns.

## Thread compaction rules

### Thread list

The thread list should read like a dense inbox rather than a collection of
large editorial cards.

Changes:

- thread title uses `listItemTitle` instead of `titleLarge`
- thread preview uses `denseSupportingText` instead of `bodyMedium`
- thread metadata and timestamp use `metaText`
- result digest uses `statusText`
- keep card spacing and structure intact unless density still feels too loose

This should reduce visual bulk while keeping titles readable on a phone.

### Thread detail

Do not shrink actual conversation content aggressively. Keep the main message
body readable.

Compact only the technical/supporting layers:

- diff labels and command metadata use `codeInline` or `codeBlock`
- approval/supporting copy uses `denseSupportingText`
- technical strip headings use `titleSmall` or `cardTitle`
- timestamps and auxiliary labels use `metaText`

## Refactor scope

### Design-system changes

- update `Type.kt` to define the full scale
- add shared font-family constants
- add semantic typography aliases in a dedicated theme file

### High-impact screen changes

- thread list
- dashboard cards and section headers
- connection flow cards and confirmation sheet
- settings headers and preference cards
- shared components such as chips, section headers, and in-app alerts

### Technical-text cleanup

Replace repeated `copy(fontFamily = FontFamily.Monospace)` patterns with shared
semantic code styles where feasible.

## Non-goals

- introducing branded or licensed font files in this pass
- reworking screen layout density beyond typography-driven improvements
- redesigning every screen from scratch

## Verification

- build the app and run unit tests
- install the debug APK on the connected phone
- review thread list density on device
- review settings, dashboard, connection, and thread detail surfaces for
  consistency

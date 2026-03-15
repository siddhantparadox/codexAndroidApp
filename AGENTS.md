# Agent Instructions

This document defines the working rules for agents operating in this repository.
It is a living document and should be updated as the project, architecture,
tooling, and team conventions evolve.

## Project Type

- This is a Kotlin Android app.
- Prefer Kotlin-first, Android-native solutions.
- Prefer modern Android patterns and official Android guidance unless the
  project explicitly adopts a different approach.

## UI and Design Tools

- Use Stitch MCP for UI and UX exploration, screen ideation, and design
  generation whenever it is relevant to the task.
- Prefer using Stitch MCP when creating new screens, refining visual direction,
  exploring layout variants, or generating high-quality UI concepts for the
  app.
- Keep generated UI aligned with the product spec, Android-native patterns,
  accessibility requirements, and the project's premium minimal design
  direction.

## Testing and Device Setup

- Real Android phone testing is the primary target for this project.
- Prefer real-device setup, run, debug, and verification workflows over
  emulator-first workflows.
- Do not assume emulator availability or require emulator-only steps unless a
  task specifically depends on it.
- When giving setup or testing instructions, provide physical-device guidance
  first and emulator guidance only as a secondary fallback.
- When an agent is asked to run the app on a phone, prefer this workflow:
  - Build or reuse the latest debug APK at
    `app/build/outputs/apk/debug/app-debug.apk`.
  - Verify the phone is connected with `adb devices`.
  - Install with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
  - Clear logs before a fresh test session with `adb logcat -c`.
  - Force-stop before relaunch when needed with
    `adb shell am force-stop dev.codex.mobile`.
  - Launch with `adb shell am start -n dev.codex.mobile/.MainActivity`.
  - Attach focused logs with `adb logcat -s CodexMobile` and keep them running
    while the user tests.
- If `adb` is not on `PATH`, prefer the local SDK platform-tools binary at
  `C:/Users/sgupt/AppData/Local/Android/Sdk/platform-tools/adb.exe`.
- When the user needs the desktop host for live testing, start it with
  `npx codexremote` and keep that terminal running.
- When the phone needs to connect to the desktop host, prefer the QR code or
  short code shown by `npx codexremote`. Use the current LAN IPv4 address of
  the desktop machine and port `4500` only as a manual fallback.

## Kotlin Conventions

- Follow the official Kotlin coding conventions.
- Apply Kotlin coding conventions for libraries whenever designing or modifying
  public APIs, reusable modules, shared components, SDK-like surfaces, or any
  code that may become part of a stable project-facing API.
- For library-style or public-facing Kotlin code:
  - Always explicitly specify member visibility.
  - Always explicitly specify function return types and property types.
  - Provide KDoc comments for all public members, except overrides that do not
    require new documentation.

## Version Policy

- Always use the latest stable release of any tool, library, framework, SDK,
  plugin, or dependency.
- Do not choose beta, alpha, canary, RC, preview, or deprecated versions unless
  the project explicitly requires them.
- When version compatibility is uncertain, verify against the latest official
  documentation before making a choice.

## Research Policy

- Search the web whenever you are uncertain about a fact, API, compatibility
  constraint, release version, configuration detail, or best practice.
- Use the latest documentation available at the time of work.
- Prefer official documentation, primary sources, and authoritative release
  notes over secondary summaries.

## Skills Policy

- Use required skills whenever the task matches their trigger conditions.
- Do not ignore a required skill when it is relevant to the work being done.
- When multiple skills apply, use the minimal set needed to complete the task
  correctly.

## Code Organization

- Keep the codebase modular.
- Avoid large, overloaded source files.
- Each file should have a focused responsibility and a clear reason to exist.
- Different concerns should live in different files rather than being grouped
  into one large implementation file.
- Prefer splitting code by responsibility, such as:
  - UI screen
  - UI component
  - ViewModel or state holder
  - model or DTO
  - repository
  - use case
  - mapper
  - utility
- When a file starts mixing unrelated responsibilities or becomes difficult to
  scan, refactor it into smaller cohesive files.
- Prefer maintainable separation over convenience-driven file growth.
- Keep project structure easy to navigate and predictable as the app grows.

## Maintenance Policy

- Treat this file as active project guidance, not static documentation.
- Refine and extend it when the project gains new constraints, conventions,
  architecture decisions, or workflow requirements.

# Dynamic Tool Call Design

Document status: Approved
Date: 2026-03-16

## Goal

Add docs-aligned support for experimental app-server dynamic tool calls in the
Android client, starting with one mobile-native tool: `pick_photo`.

## Official Doc Constraints

- `dynamicTools` are declared on `thread/start`.
- Dynamic tool execution uses an `item/tool/call` server request to the client.
- The lifecycle is `item/started` -> `item/tool/call` -> client response ->
  `item/completed`.
- `dynamicToolCall` items represent client-executed dynamic tool invocations,
  and the completed item can carry returned `contentItems` and/or `success`.

Source: https://developers.openai.com/codex/app-server/#dynamic-tool-calls-experimental

## Approved UX

- Keep the first rollout narrow to one tool: `pick_photo`.
- When Codex invokes the tool, render an inline thread card:
  - title: `Codex wants a photo`
  - subtitle: short reason derived from tool arguments when present
  - primary action: `Choose Photo`
  - secondary action: `Cancel`
- Tapping `Choose Photo` opens the native Android photo picker immediately.
- After the user selects an image, return that image through the dynamic tool
  response flow and let the completed `dynamicToolCall` item become the final
  transcript state.
- If the user cancels, respond through the dynamic tool request path rather
  than leaving the request hanging.

## Scope

### In scope

- Register one dynamic tool on new thread creation.
- Handle `item/tool/call` server requests in the repository.
- Surface pending `pick_photo` requests inline in thread detail.
- Launch the existing Android photo picker from a ViewModel-driven UI event.
- Return one selected image.
- Add mapper, payload, repository, and transcript-row tests.

### Out of scope

- Multiple dynamic tools.
- Camera capture.
- Multi-select image picking.
- A generalized client tool registry editor.
- A dedicated approvals-tab surface for dynamic tools.

## Wire-Shape Strategy

The current docs clearly define the lifecycle but do not provide a detailed
example schema for the `dynamicTools` declaration or the `item/tool/call`
request/response bodies in the fetched excerpts. Implementation should
therefore:

- follow the documented lifecycle exactly
- keep dynamic-tool payload code isolated in a small set of helpers
- reuse the app's existing image encoding path so returned image content stays
  consistent with current user-attached image uploads
- keep cancellation/failure handling explicit and narrow so the wire shape can
  be adjusted later if OpenAI publishes fuller examples

## Architecture

- Add a dynamic tool descriptor helper used by `thread/start`.
- Add a pending dynamic tool request model stored separately from approvals and
  user-input requests.
- Add repository methods for observing and resolving dynamic tool requests.
- Add a small ViewModel event channel for launching the Android picker.
- Add an inline thread card composable for pending `pick_photo` actions.

## Validation

- Add JSON payload tests for `thread/start` dynamic tool registration.
- Add mapper tests for `item/tool/call` request parsing and response payloads.
- Add transcript-row tests for attaching pending dynamic tool requests to the
  matching `dynamicToolCall` item.
- Run debug unit tests after implementation.

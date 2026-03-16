# Approval Unification Design

Document status: Approved
Date: 2026-03-15

## Goal

Reduce user work around approvals by giving the Android app one clear place to
see and act on anything that is truly blocking execution, while preserving
neutral clarification prompts as simple input requests.

## Approved UX

- Use one unified approvals list for classic app-server approvals and
  approval-shaped app or MCP tool prompts.
- Only classify `item/tool/requestUserInput` as an approval when the incoming
  request is clearly approval-shaped rather than a normal clarification.
- When ambiguous, prefer treating a request as clarification instead of
  approval.
- Keep approval entries visible both in the approvals tab and inline in the
  thread, backed by the same underlying pending request.
- Use approval-specific labels for app or MCP prompts instead of generic
  clarification copy when the request is acting as an approval.
- Make `Accept` the primary action, `Decline` secondary, and `Cancel` tertiary.

## Classification Rules

- Treat a request as approval when it is tied to a side-effectful tool action
  and the available choices are effectively approval decisions such as Accept,
  Decline, and Cancel.
- Treat a request as clarification when it asks for missing facts,
  preferences, parameters, or other neutral turn inputs.
- Keep `mcpServer/elicitation/request` handling unchanged unless the payload is
  clearly being used as an approval gate.

## Implementation Phases

### Phase 1

- Add a shared approval-facing view model layer that combines classic approval
  requests with approval-shaped user-input requests.
- Render those combined entries in the approvals tab.
- Keep thread detail behavior intact, but update approval-shaped cards to use
  approval-specific copy and actions.

### Phase 2

- Preserve command approval metadata that exists before approval, especially
  `commandActions`, so the user can inspect intent at decision time.
- Surface that metadata in approval cards and inline approval UI.

### Phase 3

- Extend generic `item/tool/requestUserInput` response handling so the app can
  send explicit accept, decline, or cancel actions when the server is using the
  request as an approval transport instead of a plain question prompt.

## Validation

- Add mapper coverage for approval-shaped user-input classification and for
  command approval metadata.
- Add repository and UI-facing tests for unified approval entries.
- Add response payload tests for generic tool user-input accept, decline, and
  cancel handling where supported by classification.

import fs from "node:fs";

import { defaultSessionRoot } from "./usageWrappedAggregator.mjs";

const LOAD_THREAD_METHODS = new Set(["thread/read", "thread/resume"]);
const DEFAULT_POLL_INTERVAL_MS = 700;
const DEFAULT_IDLE_TIMEOUT_MS = 60_000;

export function createRolloutLiveMirrorController({
  sendNotification,
  sessionRoot = defaultSessionRoot(),
  fsModule = fs,
  now = () => Date.now(),
  setIntervalFn = setInterval,
  clearIntervalFn = clearInterval,
  pollIntervalMs = DEFAULT_POLL_INTERVAL_MS,
  idleTimeoutMs = DEFAULT_IDLE_TIMEOUT_MS,
} = {}) {
  const pendingThreadLoads = new Map();
  const mirrorsByThreadId = new Map();
  const rolloutPathByThreadId = new Map();

  function observeClientMessage(message) {
    const method = readString(message?.method);
    if (LOAD_THREAD_METHODS.has(method) && message?.id != null) {
      const threadId = readThreadId(message?.params);
      if (threadId) {
        pendingThreadLoads.set(String(message.id), threadId);
      }
      return;
    }

    if (method === "thread/unsubscribe") {
      stopMirror(readThreadId(message?.params));
    }
  }

  function transformTransportMessage(message) {
    const responseId = message?.id == null ? null : String(message.id);
    if (responseId && pendingThreadLoads.has(responseId) && message?.method == null) {
      const threadId = pendingThreadLoads.get(responseId);
      pendingThreadLoads.delete(responseId);
      if (!message?.error && threadId) {
        return ensureMirror(threadId).hydrateResponse(message);
      }
      return message;
    }

    if (message?.method === "thread/closed" || message?.method === "thread/archived") {
      stopMirror(readThreadId(message?.params));
    }

    return message;
  }

  function close() {
    for (const mirror of mirrorsByThreadId.values()) {
      mirror.stop();
    }
    mirrorsByThreadId.clear();
    pendingThreadLoads.clear();
  }

  function ensureMirror(threadId) {
    const existing = mirrorsByThreadId.get(threadId);
    if (existing) {
      return existing;
    }

    const mirror = createThreadRolloutMirror({
      threadId,
      sendNotification,
      resolveRolloutPath,
      fsModule,
      now,
      setIntervalFn,
      clearIntervalFn,
      pollIntervalMs,
      idleTimeoutMs,
      onStop() {
        if (mirrorsByThreadId.get(threadId) === mirror) {
          mirrorsByThreadId.delete(threadId);
        }
      },
    });
    mirrorsByThreadId.set(threadId, mirror);
    return mirror;
  }

  function resolveRolloutPath(threadId) {
    const cachedPath = rolloutPathByThreadId.get(threadId);
    if (cachedPath && safeStatSync(cachedPath, fsModule)?.isFile()) {
      return cachedPath;
    }

    const rolloutPath = findRolloutFileForThread({ sessionRoot, threadId, fsModule });
    if (rolloutPath) {
      rolloutPathByThreadId.set(threadId, rolloutPath);
    }
    return rolloutPath;
  }

  function stopMirror(threadId) {
    if (!threadId) {
      return;
    }

    const mirror = mirrorsByThreadId.get(threadId);
    if (!mirror) {
      return;
    }

    mirror.stop();
    mirrorsByThreadId.delete(threadId);
  }

  return {
    observeClientMessage,
    transformTransportMessage,
    close,
  };
}

function createThreadRolloutMirror({
  threadId,
  sendNotification,
  resolveRolloutPath,
  fsModule,
  now,
  setIntervalFn,
  clearIntervalFn,
  pollIntervalMs,
  idleTimeoutMs,
  onStop = () => {},
}) {
  let stopped = false;
  let rolloutPath = null;
  let lastSize = 0;
  let partialLine = "";
  let lastActivityAt = now();
  let liveState = createMirrorState(threadId);

  const intervalId = setIntervalFn(() => {
    tick();
  }, pollIntervalMs);

  function hydrateResponse(message) {
    if (stopped) {
      return message;
    }

    lastActivityAt = now();
    rolloutPath = resolveRolloutPath(threadId);
    if (!rolloutPath) {
      return message;
    }

    const originalThread = readThreadResult(message);
    const snapshot = buildHistorySnapshot({
      threadId,
      rolloutPath,
      fsModule,
      thread: originalThread,
    });
    if (!snapshot || snapshot.isDesktopOrigin === false) {
      stop();
      return message;
    }

    liveState = snapshot.liveState;
    lastSize = snapshot.fileSize;
    partialLine = "";
    if (snapshot.augmentedThread && snapshot.augmentedThread !== originalThread) {
      return {
        ...message,
        result: {
          ...message.result,
          thread: snapshot.augmentedThread,
        },
      };
    }
    return message;
  }

  function refreshState() {
    if (stopped) {
      return;
    }

    lastActivityAt = now();
    rolloutPath = resolveRolloutPath(threadId);
    if (!rolloutPath) {
      return;
    }

    const snapshot = buildHistorySnapshot({ threadId, rolloutPath, fsModule });
    if (!snapshot || snapshot.isDesktopOrigin === false) {
      stop();
      return;
    }

    liveState = snapshot.liveState;
    lastSize = snapshot.fileSize;
    partialLine = "";
  }

  function stop() {
    if (stopped) {
      return;
    }

    stopped = true;
    clearIntervalFn(intervalId);
    onStop();
  }

  function tick() {
    if (stopped) {
      return;
    }

    if (!rolloutPath) {
      rolloutPath = resolveRolloutPath(threadId);
      if (!rolloutPath) {
        if (now() - lastActivityAt >= idleTimeoutMs) {
          stop();
        }
        return;
      }

      refreshState();
      return;
    }

    const stat = safeStatSync(rolloutPath, fsModule);
    if (!stat?.isFile()) {
      if (now() - lastActivityAt >= idleTimeoutMs) {
        stop();
      }
      return;
    }

    const fileSize = stat.size;
    if (fileSize < lastSize) {
      refreshState();
      return;
    }

    if (fileSize === lastSize) {
      if (now() - lastActivityAt >= idleTimeoutMs) {
        stop();
      }
      return;
    }

    const chunk = readFileSlice({
      filePath: rolloutPath,
      start: lastSize,
      endExclusive: fileSize,
      fsModule,
    });
    lastSize = fileSize;
    lastActivityAt = now();
    if (!chunk) {
      return;
    }

    const combined = `${partialLine}${chunk}`;
    const lines = combined.split(/\r?\n/);
    partialLine = lines.pop() || "";
    for (const rawLine of lines) {
      const line = rawLine.trim();
      if (!line) {
        continue;
      }
      const entry = safeParseJson(line);
      if (!entry) {
        continue;
      }
      for (const notification of liveNotificationsFromEntry(entry, liveState)) {
        sendNotification(notification);
      }
    }
  }

  return { hydrateResponse, stop };
}

function buildHistorySnapshot({ threadId, rolloutPath, fsModule, thread = null }) {
  const stat = safeStatSync(rolloutPath, fsModule);
  if (!stat?.isFile()) {
    return null;
  }

  const contents = readFileSlice({
    filePath: rolloutPath,
    start: 0,
    endExclusive: stat.size,
    fsModule,
  });
  const state = createMirrorState(threadId);

  for (const rawLine of contents.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line) {
      continue;
    }
    const entry = safeParseJson(line);
    if (!entry) {
      continue;
    }
    processReplayEntry(entry, state);
  }

  return {
    fileSize: stat.size,
    isDesktopOrigin: state.isDesktopOrigin,
    augmentedThread: thread ? augmentThreadHistory(thread, state) : null,
    liveState: state,
  };
}

function createMirrorState(threadId) {
  return {
    threadId,
    currentTurnId: null,
    sessionMeta: null,
    isDesktopOrigin: null,
    turnsById: new Map(),
    itemsByCallId: new Map(),
    pendingItemsByCallId: new Map(),
  };
}

function processReplayEntry(entry, state) {
  if (entry?.type === "session_meta") {
    populateSessionMetaState(state, entry.payload);
    return;
  }

  if (state.isDesktopOrigin === false) {
    return;
  }

  if (entry?.type === "event_msg") {
    const eventType = readString(entry?.payload?.type);
    if (eventType === "task_started") {
      state.currentTurnId = readString(entry?.payload?.turn_id) || readString(entry?.payload?.turnId) || null;
      ensureTurnState(state, state.currentTurnId);
      return;
    }

    if (eventType === "task_complete") {
      finalizeClosedTurn(state);
      state.currentTurnId = null;
    }
    return;
  }

  if (entry?.type !== "response_item") {
    return;
  }

  processReplayResponseItem(entry.payload || {}, state);
}

function finalizeClosedTurn(state, notifications = null) {
  for (const [callId, pendingItem] of state.pendingItemsByCallId.entries()) {
    if (pendingItem.turnId !== state.currentTurnId) {
      continue;
    }

    state.pendingItemsByCallId.delete(callId);
    const completedItem = finalizePendingItem(pendingItem, null);
    state.itemsByCallId.set(callId, completedItem);
    if (notifications) {
      notifications.push(
        createNotification(
          "item/completed",
          state.threadId,
          completedItem,
        ),
      );
    }
  }
}

function ensureTurnState(state, turnId) {
  const normalizedTurnId = readString(turnId);
  if (!normalizedTurnId) {
    return null;
  }

  const existing = state.turnsById.get(normalizedTurnId);
  if (existing) {
    return existing;
  }

  const created = {
    id: normalizedTurnId,
    descriptors: [],
  };
  state.turnsById.set(normalizedTurnId, created);
  return created;
}

function appendExistingItemDescriptor(state, turnId, itemType) {
  const turnState = ensureTurnState(state, turnId);
  if (!turnState) {
    return;
  }

  turnState.descriptors.push({
    kind: "existing",
    itemType,
  });
}

function appendToolItemDescriptor(state, turnId, callId) {
  const turnState = ensureTurnState(state, turnId);
  if (!turnState) {
    return;
  }

  turnState.descriptors.push({
    kind: "tool",
    callId,
  });
}

function resolvePayloadTurnId(payload, state) {
  return readString(payload?.turn_id) || readString(payload?.turnId) || state.currentTurnId;
}

function augmentThreadHistory(thread, state) {
  const turns = Array.isArray(thread?.turns) ? thread.turns : null;
  if (!turns || turns.length === 0) {
    return thread;
  }

  let changed = false;
  const nextTurns = turns.map((turn) => {
    const turnState = state.turnsById.get(readString(turn?.id));
    if (!turnState || turnState.descriptors.length === 0) {
      return turn;
    }

    const mergedItems = mergeTurnItems(Array.isArray(turn?.items) ? turn.items : [], turnState, state);
    if (mergedItems === turn?.items) {
      return turn;
    }

    changed = true;
    return {
      ...turn,
      items: mergedItems,
    };
  });

  if (!changed) {
    return thread;
  }

  return {
    ...thread,
    turns: nextTurns,
  };
}

function mergeTurnItems(existingItems, turnState, state) {
  const matchedIndexes = new Set();
  const mergedItems = [];

  for (const descriptor of turnState.descriptors) {
    if (descriptor.kind === "existing") {
      const existingItem = takeNextUnmatchedItem(
        existingItems,
        matchedIndexes,
        (candidate) => readString(candidate?.type) === descriptor.itemType,
      );
      if (existingItem) {
        mergedItems.push(existingItem);
      }
      continue;
    }

    if (descriptor.kind !== "tool") {
      continue;
    }

    const existingToolItem = takeNextUnmatchedItem(
      existingItems,
      matchedIndexes,
      (candidate) => readString(candidate?.id) === descriptor.callId,
    );
    const toolItem = existingToolItem || state.itemsByCallId.get(descriptor.callId);
    if (toolItem) {
      mergedItems.push(toolItem);
    }
  }

  if (mergedItems.length === 0) {
    return existingItems;
  }

  const reorderedItems = [
    ...mergedItems,
    ...existingItems.filter((_, index) => !matchedIndexes.has(index)),
  ];
  if (
    reorderedItems.length === existingItems.length
    && reorderedItems.every((item, index) => item === existingItems[index])
  ) {
    return existingItems;
  }

  return reorderedItems;
}

function takeNextUnmatchedItem(items, matchedIndexes, predicate) {
  for (let index = 0; index < items.length; index += 1) {
    if (matchedIndexes.has(index)) {
      continue;
    }

    const candidate = items[index];
    if (!predicate(candidate)) {
      continue;
    }

    matchedIndexes.add(index);
    return candidate;
  }

  return null;
}

function readThreadResult(message) {
  const thread = message?.result?.thread;
  return thread && typeof thread === "object" ? thread : null;
}

function liveNotificationsFromEntry(entry, state) {
  if (entry?.type === "session_meta") {
    populateSessionMetaState(state, entry.payload);
    return [];
  }

  if (state.isDesktopOrigin === false) {
    return [];
  }

  if (entry?.type === "event_msg") {
    const eventType = readString(entry?.payload?.type);
    if (eventType === "task_started") {
      state.currentTurnId = readString(entry?.payload?.turn_id) || readString(entry?.payload?.turnId) || null;
      ensureTurnState(state, state.currentTurnId);
      return [];
    }

    if (eventType === "task_complete") {
      const notifications = [];
      finalizeClosedTurn(state, notifications);
      state.currentTurnId = null;
      return notifications;
    }

    return [];
  }

  if (entry?.type !== "response_item") {
    return [];
  }

  const notifications = [];
  processReplayResponseItem(entry.payload || {}, state, notifications, true);
  return notifications;
}

function processReplayResponseItem(payload, state, notifications = null, liveMode = false) {
  const payloadType = readString(payload.type);
  const turnId = resolvePayloadTurnId(payload, state);
  if (payloadType === "message") {
    const role = readString(payload.role).toLowerCase();
    if (role === "user") {
      appendExistingItemDescriptor(state, turnId, "userMessage");
    } else if (role === "assistant") {
      appendExistingItemDescriptor(state, turnId, "agentMessage");
    }
    return;
  }

  if (payloadType === "reasoning") {
    appendExistingItemDescriptor(state, turnId, "reasoning");
    return;
  }

  processToolResponseItem(payload, state, notifications, liveMode, turnId);
}

function processToolResponseItem(payload, state, notifications, liveMode, turnId) {
  const payloadType = readString(payload.type);
  if (payloadType === "function_call" || payloadType === "custom_tool_call") {
    const pendingItem = createPendingItemFromToolPayload(payload, state, turnId);
    if (!pendingItem) {
      return;
    }

    state.itemsByCallId.set(pendingItem.callId, pendingItem.item);
    appendToolItemDescriptor(state, pendingItem.turnId, pendingItem.callId);
    if (!pendingItem.expectsOutput) {
      if (notifications) {
        notifications.push(createNotification("item/completed", state.threadId, pendingItem.item));
      }
      return;
    }

    state.pendingItemsByCallId.set(pendingItem.callId, pendingItem);
    if (liveMode) {
      notifications.push(createNotification("item/started", state.threadId, pendingItem.item));
    }
    return;
  }

  if (payloadType !== "function_call_output" && payloadType !== "custom_tool_call_output") {
    return;
  }

  const callId = readString(payload.call_id) || readString(payload.callId);
  if (!callId) {
    return;
  }

  const pendingItem = state.pendingItemsByCallId.get(callId);
  if (!pendingItem) {
    return;
  }

  state.pendingItemsByCallId.delete(callId);
  const completedItem = finalizePendingItem(pendingItem, payload.output);
  state.itemsByCallId.set(callId, completedItem);
  if (notifications) {
    notifications.push(
      createNotification(
        "item/completed",
        state.threadId,
        completedItem,
      ),
    );
  }
}

function createPendingItemFromToolPayload(payload, state, turnIdOverride = null) {
  const callId = readString(payload.call_id) || readString(payload.callId);
  const toolName = readString(payload.name);
  if (!callId || !toolName) {
    return null;
  }

  const payloadType = readString(payload.type);
  const rawArguments = payloadType === "custom_tool_call"
    ? readString(payload.input)
    : readString(payload.arguments);
  const argumentsObject = parseToolArguments(rawArguments);
  const turnId = turnIdOverride || state.currentTurnId;

  if (isContextCompactionTool(toolName)) {
    return {
      callId,
      turnId,
      expectsOutput: false,
      kind: "contextCompaction",
      item: {
        type: "contextCompaction",
        id: callId,
      },
    };
  }

  if (isShellCommandTool(toolName)) {
    return {
      callId,
      turnId,
      expectsOutput: true,
      kind: "commandExecution",
      item: {
        type: "commandExecution",
        id: callId,
        command: resolveCommand(argumentsObject, toolName),
        cwd: resolveWorkingDirectory(argumentsObject, state),
        status: "inProgress",
      },
    };
  }

  if (toolName === "apply_patch") {
    return {
      callId,
      turnId,
      expectsOutput: true,
      kind: "fileChange",
      item: {
        type: "fileChange",
        id: callId,
        changes: parseApplyPatchChanges(rawArguments),
        status: "inProgress",
      },
    };
  }

  if (isMcpTool(toolName)) {
    const { server, tool } = splitMcpToolName(toolName);
    return {
      callId,
      turnId,
      expectsOutput: true,
      kind: "mcpToolCall",
      item: {
        type: "mcpToolCall",
        id: callId,
        server,
        tool,
        status: "inProgress",
        arguments: displayArguments(rawArguments, argumentsObject),
      },
    };
  }

  const imagePath = extractImageViewPath(toolName, argumentsObject);
  if (imagePath) {
    return {
      callId,
      turnId,
      expectsOutput: false,
      kind: "imageView",
      item: {
        type: "imageView",
        id: callId,
        path: imagePath,
      },
    };
  }

  const webSearchItem = buildWebSearchItem(callId, toolName, argumentsObject);
  if (webSearchItem) {
    return {
      callId,
      turnId,
      expectsOutput: false,
      kind: "webSearch",
      item: webSearchItem,
    };
  }

  return {
    callId,
    turnId,
    expectsOutput: true,
    kind: "dynamicToolCall",
    item: {
      type: "dynamicToolCall",
      id: callId,
      tool: toolName,
      status: "inProgress",
      arguments: displayArguments(rawArguments, argumentsObject),
    },
  };
}

function finalizePendingItem(pendingItem, rawOutput) {
  switch (pendingItem.kind) {
    case "commandExecution":
      return finalizeCommandExecutionItem(pendingItem.item, rawOutput);
    case "fileChange":
      return finalizeFileChangeItem(pendingItem.item, rawOutput);
    case "mcpToolCall":
      return finalizeMcpToolItem(pendingItem.item, rawOutput);
    case "dynamicToolCall":
      return finalizeDynamicToolItem(pendingItem.item, rawOutput);
    default:
      return pendingItem.item;
  }
}

function finalizeCommandExecutionItem(item, rawOutput) {
  const result = parseCommandExecutionOutput(rawOutput);
  return {
    ...item,
    status: result.status,
    aggregatedOutput: result.aggregatedOutput,
    exitCode: result.exitCode,
    durationMs: result.durationMs,
  };
}

function finalizeFileChangeItem(item, rawOutput) {
  const envelope = parseOutputEnvelope(rawOutput);
  return {
    ...item,
    status: envelope.failed ? "failed" : "completed",
    toolOutput: envelope.text || null,
  };
}

function finalizeMcpToolItem(item, rawOutput) {
  const envelope = parseOutputEnvelope(rawOutput);
  return {
    ...item,
    status: envelope.failed ? "failed" : "completed",
    result: envelope.parsedObject ?? (envelope.text ? { output: envelope.text } : undefined),
    error: envelope.failed ? { message: firstNonEmptyLine(envelope.text) || "Tool call failed" } : undefined,
  };
}

function finalizeDynamicToolItem(item, rawOutput) {
  const envelope = parseOutputEnvelope(rawOutput);
  return {
    ...item,
    status: envelope.failed ? "failed" : "completed",
    success: !envelope.failed,
    contentItems: envelope.text
      ? [{
        type: "inputText",
        text: envelope.text,
      }]
      : [],
  };
}

function parseCommandExecutionOutput(rawOutput) {
  const text = readString(rawOutput);
  if (!text) {
    return {
      status: "completed",
      aggregatedOutput: null,
      exitCode: null,
      durationMs: null,
    };
  }

  if (text.toLowerCase().startsWith("execution error:")) {
    return {
      status: "failed",
      aggregatedOutput: text,
      exitCode: null,
      durationMs: null,
    };
  }

  const exitCodeMatch = text.match(/^Exit code:\s*(-?\d+)/m);
  const durationMatch = text.match(/^Wall time:\s*([0-9.]+)\s*seconds/m);
  const outputMatch = text.match(/(?:^|\n)Output:\n([\s\S]*)$/m);
  const exitCode = exitCodeMatch ? Number.parseInt(exitCodeMatch[1], 10) : null;
  const durationMs = durationMatch ? Math.round(Number.parseFloat(durationMatch[1]) * 1000) : null;

  return {
    status: exitCode != null && exitCode !== 0 ? "failed" : "completed",
    aggregatedOutput: outputMatch?.[1]?.trimEnd() || text,
    exitCode,
    durationMs,
  };
}

function parseOutputEnvelope(rawOutput) {
  const directText = readString(rawOutput);
  const parsed = safeParseJson(directText);
  const parsedObject = parsed && typeof parsed === "object" && !Array.isArray(parsed)
    ? parsed
    : null;
  const metadata = parsedObject?.metadata && typeof parsedObject.metadata === "object"
    ? parsedObject.metadata
    : {};
  const exitCode = Number.isFinite(Number(metadata.exit_code)) ? Number(metadata.exit_code) : null;
  const text = readString(parsedObject?.output) || directText;

  return {
    text,
    parsedObject,
    failed: Boolean((exitCode != null && exitCode !== 0) || text.toLowerCase().startsWith("execution error:")),
  };
}

function parseApplyPatchChanges(input) {
  const lines = readString(input).split(/\r?\n/);
  const changes = [];
  let activeChange = null;
  let diffLines = [];

  function flush() {
    if (!activeChange) {
      return;
    }
    changes.push({
      path: activeChange.path,
      kind: activeChange.kind,
      diff: diffLines.join("\n").trimEnd(),
    });
    activeChange = null;
    diffLines = [];
  }

  for (const line of lines) {
    if (line.startsWith("*** Add File: ")) {
      flush();
      activeChange = {
        path: line.slice("*** Add File: ".length).trim(),
        kind: "added",
      };
      diffLines = [line];
      continue;
    }

    if (line.startsWith("*** Delete File: ")) {
      flush();
      activeChange = {
        path: line.slice("*** Delete File: ".length).trim(),
        kind: "deleted",
      };
      diffLines = [line];
      continue;
    }

    if (line.startsWith("*** Update File: ")) {
      flush();
      activeChange = {
        path: line.slice("*** Update File: ".length).trim(),
        kind: "modified",
      };
      diffLines = [line];
      continue;
    }

    if (!activeChange) {
      continue;
    }

    if (line.startsWith("*** Move to: ")) {
      activeChange.path = line.slice("*** Move to: ".length).trim();
      activeChange.kind = "renamed";
    }

    if (line === "*** Begin Patch" || line === "*** End Patch") {
      continue;
    }

    diffLines.push(line);
  }

  flush();
  return changes;
}

function buildWebSearchItem(callId, toolName, argumentsObject) {
  const normalizedToolName = readString(toolName).toLowerCase();
  const toolKey = normalizedToolName.startsWith("web.")
    ? normalizedToolName.slice("web.".length)
    : normalizedToolName;
  if (!["search_query", "image_query", "open", "find"].includes(toolKey)) {
    return null;
  }

  if (toolKey === "search_query" || toolKey === "image_query") {
    const queries = extractQueryValues(argumentsObject, toolKey);
    return {
      type: "webSearch",
      id: callId,
      query: queries[0] || "Search",
      action: {
        type: "search",
        query: queries[0] || undefined,
        queries,
      },
    };
  }

  if (toolKey === "open") {
    const url = readString(argumentsObject.url) || readString(argumentsObject.ref_id);
    return {
      type: "webSearch",
      id: callId,
      query: url || "Open page",
      action: {
        type: "openPage",
        url,
      },
    };
  }

  const url = readString(argumentsObject.url) || readString(argumentsObject.ref_id);
  const pattern = readString(argumentsObject.pattern);
  return {
    type: "webSearch",
    id: callId,
    query: pattern || url || "Find in page",
    action: {
      type: "findInPage",
      url,
      pattern,
    },
  };
}

function extractQueryValues(argumentsObject, key) {
  const values = Array.isArray(argumentsObject?.[key])
    ? argumentsObject[key]
      .map((entry) => (entry && typeof entry === "object" ? readString(entry.q) : ""))
      .filter(Boolean)
    : [];
  if (values.length > 0) {
    return values;
  }

  const directQuery = readString(argumentsObject.q) || readString(argumentsObject.query);
  return directQuery ? [directQuery] : [];
}

function extractImageViewPath(toolName, argumentsObject) {
  if (readString(toolName).toLowerCase() !== "view_image") {
    return null;
  }
  return readString(argumentsObject.path) || readString(argumentsObject.url) || null;
}

function isShellCommandTool(toolName) {
  return readString(toolName).toLowerCase() === "shell_command";
}

function isMcpTool(toolName) {
  return readString(toolName).startsWith("mcp__");
}

function isContextCompactionTool(toolName) {
  const normalizedToolName = readString(toolName).toLowerCase();
  return normalizedToolName.includes("compact") && normalizedToolName.includes("context");
}

function splitMcpToolName(toolName) {
  const [, server = "", tool = ""] = readString(toolName).split("__");
  return { server, tool };
}

function displayArguments(rawArguments, argumentsObject) {
  const rawText = readString(rawArguments);
  if (rawText) {
    const parsed = safeParseJson(rawText);
    if (parsed && typeof parsed === "object") {
      return JSON.stringify(parsed);
    }
    return rawText;
  }

  if (!argumentsObject || typeof argumentsObject !== "object") {
    return "";
  }

  return JSON.stringify(argumentsObject);
}

function resolveCommand(argumentsObject, fallback) {
  return readString(argumentsObject.command)
    || readString(argumentsObject.cmd)
    || readString(argumentsObject.raw_command)
    || readString(argumentsObject.rawCommand)
    || fallback;
}

function resolveWorkingDirectory(argumentsObject, state) {
  return readString(argumentsObject.workdir)
    || readString(argumentsObject.cwd)
    || readString(state?.sessionMeta?.cwd)
    || null;
}

function parseToolArguments(rawArguments) {
  const parsed = safeParseJson(readString(rawArguments));
  return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : {};
}

function populateSessionMetaState(state, payload) {
  const sessionMeta = payload && typeof payload === "object" ? payload : {};
  state.sessionMeta = {
    id: readString(sessionMeta.id),
    originator: readString(sessionMeta.originator),
    source: readString(sessionMeta.source),
    cwd: readString(sessionMeta.cwd),
  };
  if (state.isDesktopOrigin == null) {
    state.isDesktopOrigin = isDesktopRolloutOrigin(state.sessionMeta);
  }
}

export function isDesktopRolloutOrigin(sessionMeta) {
  const originator = readString(sessionMeta?.originator).toLowerCase();
  const source = readString(sessionMeta?.source).toLowerCase();
  if (!originator && !source) {
    return false;
  }

  if (
    originator.includes("mobile") ||
    originator.includes("android") ||
    originator.includes("ios") ||
    source.includes("mobile") ||
    source.includes("android") ||
    source.includes("ios") ||
    source.includes("app-server")
  ) {
    return false;
  }

  return (
    originator.includes("tui") ||
    originator.includes("desktop") ||
    originator.includes("vscode") ||
    source === "cli" ||
    source === "vscode" ||
    source === "desktop"
  );
}

function findRolloutFileForThread({ sessionRoot, threadId, fsModule }) {
  const rootStat = safeStatSync(sessionRoot, fsModule);
  if (!rootStat?.isDirectory()) {
    return null;
  }

  const files = walkRolloutFiles(sessionRoot, fsModule);
  files.sort((left, right) => {
    const rightTime = safeStatSync(right, fsModule)?.mtimeMs || 0;
    const leftTime = safeStatSync(left, fsModule)?.mtimeMs || 0;
    return rightTime - leftTime;
  });

  for (const filePath of files) {
    const firstLine = readFirstNonEmptyLine(filePath, fsModule);
    const sessionMeta = safeParseJson(firstLine);
    if (sessionMeta?.type !== "session_meta") {
      continue;
    }
    if (readString(sessionMeta?.payload?.id) === threadId) {
      return filePath;
    }
  }

  return null;
}

function walkRolloutFiles(root, fsModule) {
  const stat = safeStatSync(root, fsModule);
  if (!stat?.isDirectory()) {
    return [];
  }

  const entries = fsModule.readdirSync(root, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const filePath = `${root}/${entry.name}`.replaceAll("\\", "/");
    if (entry.isDirectory()) {
      files.push(...walkRolloutFiles(filePath, fsModule));
      continue;
    }
    if (entry.isFile() && entry.name.startsWith("rollout-") && entry.name.endsWith(".jsonl")) {
      files.push(filePath);
    }
  }
  return files;
}

function readFirstNonEmptyLine(filePath, fsModule) {
  try {
    const contents = fsModule.readFileSync(filePath, "utf8");
    return contents.split(/\r?\n/).find((line) => line.trim()) || "";
  } catch {
    return "";
  }
}

function readFileSlice({ filePath, start, endExclusive, fsModule }) {
  const length = Math.max(0, endExclusive - start);
  if (length === 0) {
    return "";
  }

  const fileDescriptor = fsModule.openSync(filePath, "r");
  try {
    const buffer = Buffer.alloc(length);
    const bytesRead = fsModule.readSync(fileDescriptor, buffer, 0, length, start);
    return buffer.toString("utf8", 0, bytesRead);
  } finally {
    fsModule.closeSync(fileDescriptor);
  }
}

function safeStatSync(filePath, fsModule) {
  try {
    return fsModule.statSync(filePath);
  } catch {
    return null;
  }
}

function createNotification(method, threadId, item) {
  return {
    method,
    params: {
      threadId,
      item,
    },
  };
}

function readThreadId(params) {
  return readString(params?.threadId) || readString(params?.thread_id);
}

function safeParseJson(text) {
  if (typeof text !== "string" || !text.trim()) {
    return null;
  }

  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

function readString(value) {
  return typeof value === "string" ? value.trim() : "";
}

function firstNonEmptyLine(text) {
  return readString(text)
    .split(/\r?\n/)
    .map((line) => line.trim())
    .find(Boolean) || "";
}





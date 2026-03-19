import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";

import { createRolloutLiveMirrorController } from "../lib/rolloutLiveMirror.mjs";

test("augments desktop thread/read history with tool items in rollout order", async () => {
  const rootDir = await fs.mkdtemp(path.join(os.tmpdir(), "codexremote-rollout-history-"));
  const sessionRoot = path.join(rootDir, "sessions", "2026", "03", "18");
  await fs.mkdir(sessionRoot, { recursive: true });

  const rolloutLines = [
    {
      type: "session_meta",
      payload: {
        id: "thread-1",
        originator: "codex-tui",
        source: "cli",
        cwd: "D:/projects/codexAndroidApp",
      },
    },
    {
      type: "event_msg",
      payload: {
        type: "task_started",
        turn_id: "turn-1",
      },
    },
    {
      type: "response_item",
      payload: {
        type: "message",
        role: "user",
      },
    },
    {
      type: "response_item",
      payload: {
        type: "function_call",
        name: "shell_command",
        arguments: JSON.stringify({
          command: "git status --short",
          workdir: "D:/projects/codexAndroidApp",
        }),
        call_id: "call-shell",
      },
    },
    {
      type: "response_item",
      payload: {
        type: "function_call_output",
        call_id: "call-shell",
        output: "Exit code: 0\nWall time: 0.4 seconds\nOutput:\nM app/src/main/java/dev/codex/mobile/MainActivity.kt\n",
      },
    },
    {
      type: "response_item",
      payload: {
        type: "reasoning",
      },
    },
    {
      type: "response_item",
      payload: {
        type: "message",
        role: "assistant",
      },
    },
    {
      type: "response_item",
      payload: {
        type: "function_call",
        name: "search_query",
        arguments: JSON.stringify({
          search_query: [{ q: "tool call pills" }],
        }),
        call_id: "call-web",
      },
    },
    {
      type: "response_item",
      payload: {
        type: "custom_tool_call",
        status: "completed",
        name: "apply_patch",
        call_id: "call-patch",
        input: [
          "*** Begin Patch",
          "*** Add File: D:/projects/codexAndroidApp/tmp/new.txt",
          "+hello",
          "*** End Patch",
        ].join("\n"),
      },
    },
    {
      type: "response_item",
      payload: {
        type: "custom_tool_call_output",
        call_id: "call-patch",
        output: JSON.stringify({
          output: "Success. Updated the following files:\nA D:/projects/codexAndroidApp/tmp/new.txt\n",
          metadata: {
            exit_code: 0,
            duration_seconds: 0.2,
          },
        }),
      },
    },
    {
      type: "response_item",
      payload: {
        type: "function_call",
        name: "mcp__openaiDeveloperDocs__search_openai_docs",
        arguments: JSON.stringify({ query: "codex app server items" }),
        call_id: "call-mcp",
      },
    },
    {
      type: "response_item",
      payload: {
        type: "function_call_output",
        call_id: "call-mcp",
        output: JSON.stringify({
          hits: [{ url: "https://developers.openai.com/codex/app-server/#items" }],
        }),
      },
    },
    {
      type: "event_msg",
      payload: {
        type: "task_complete",
        turn_id: "turn-1",
      },
    },
  ];

  const rolloutPath = path.join(sessionRoot, "rollout-thread-1.jsonl");
  await fs.writeFile(rolloutPath, `${rolloutLines.map((line) => JSON.stringify(line)).join("\n")}\n`, "utf8");

  const notifications = [];
  const controller = createRolloutLiveMirrorController({
    sendNotification: (notification) => {
      notifications.push(notification);
    },
    sessionRoot: path.join(rootDir, "sessions"),
    pollIntervalMs: 20,
    idleTimeoutMs: 200,
  });

  try {
    controller.observeClientMessage({
      id: "request-1",
      method: "thread/read",
      params: {
        threadId: "thread-1",
      },
    });

    const response = controller.transformTransportMessage({
      id: "request-1",
      result: {
        thread: {
          id: "thread-1",
          turns: [
            {
              id: "turn-1",
              items: [
                { type: "userMessage", id: "user-1", text: "Fix pills" },
                { type: "reasoning", id: "reason-1", summary: ["Need ordering"] },
                { type: "agentMessage", id: "assistant-1", text: "Inspecting bridge" },
              ],
            },
          ],
        },
      },
    });

    assert.equal(notifications.length, 0);
    const items = response.result.thread.turns[0].items;
    assert.deepEqual(
      items.map((item) => item.id),
      ["user-1", "call-shell", "reason-1", "assistant-1", "call-web", "call-patch", "call-mcp"],
    );
    assert.deepEqual(
      items.map((item) => item.type),
      ["userMessage", "commandExecution", "reasoning", "agentMessage", "webSearch", "fileChange", "mcpToolCall"],
    );

    const commandItem = items.find((item) => item.id === "call-shell");
    assert.equal(commandItem.command, "git status --short");
    assert.equal(commandItem.status, "completed");
    assert.equal(commandItem.exitCode, 0);
    assert.match(commandItem.aggregatedOutput, /MainActivity/);

    const webItem = items.find((item) => item.id === "call-web");
    assert.equal(webItem.query, "tool call pills");
    assert.equal(webItem.action.type, "search");

    const fileChangeItem = items.find((item) => item.id === "call-patch");
    assert.equal(fileChangeItem.status, "completed");
    assert.equal(fileChangeItem.changes[0].path, "D:/projects/codexAndroidApp/tmp/new.txt");
    assert.equal(fileChangeItem.changes[0].kind, "added");

    const mcpItem = items.find((item) => item.id === "call-mcp");
    assert.equal(mcpItem.server, "openaiDeveloperDocs");
    assert.equal(mcpItem.tool, "search_openai_docs");
    assert.equal(mcpItem.status, "completed");
  } finally {
    controller.close();
    await fs.rm(rootDir, { recursive: true, force: true });
  }
});

test("tails new desktop tool items after history augmentation", async () => {
  const rootDir = await fs.mkdtemp(path.join(os.tmpdir(), "codexremote-rollout-tail-"));
  const sessionRoot = path.join(rootDir, "sessions", "2026", "03", "18");
  await fs.mkdir(sessionRoot, { recursive: true });

  const rolloutPath = path.join(sessionRoot, "rollout-thread-2.jsonl");
  const initialLines = [
    {
      type: "session_meta",
      payload: {
        id: "thread-2",
        originator: "codex-tui",
        source: "cli",
        cwd: "D:/projects/codexAndroidApp",
      },
    },
    {
      type: "event_msg",
      payload: {
        type: "task_started",
        turn_id: "turn-2",
      },
    },
    {
      type: "response_item",
      payload: {
        type: "message",
        role: "user",
      },
    },
  ];
  await fs.writeFile(rolloutPath, `${initialLines.map((line) => JSON.stringify(line)).join("\n")}\n`, "utf8");

  const notifications = [];
  const controller = createRolloutLiveMirrorController({
    sendNotification: (notification) => {
      notifications.push(notification);
    },
    sessionRoot: path.join(rootDir, "sessions"),
    pollIntervalMs: 20,
    idleTimeoutMs: 500,
  });

  try {
    controller.observeClientMessage({
      id: "request-2",
      method: "thread/read",
      params: {
        threadId: "thread-2",
      },
    });
    const response = controller.transformTransportMessage({
      id: "request-2",
      result: {
        thread: {
          id: "thread-2",
          turns: [
            {
              id: "turn-2",
              items: [{ type: "userMessage", id: "user-2", text: "Attach logcat" }],
            },
          ],
        },
      },
    });

    assert.deepEqual(response.result.thread.turns[0].items.map((item) => item.id), ["user-2"]);
    assert.equal(notifications.length, 0);

    await fs.appendFile(
      rolloutPath,
      `${JSON.stringify({
        type: "response_item",
        payload: {
          type: "function_call",
          name: "shell_command",
          arguments: JSON.stringify({
            command: "adb logcat -s CodexMobile",
            workdir: "D:/projects/codexAndroidApp",
          }),
          call_id: "call-live-shell",
        },
      })}\n`,
      "utf8",
    );

    await waitForCondition(() => notifications.some((notification) => notification.method === "item/started"));
    const startedNotification = notifications.find((notification) => notification.method === "item/started");
    assert.equal(startedNotification.params.item.type, "commandExecution");
    assert.equal(startedNotification.params.item.command, "adb logcat -s CodexMobile");

    await fs.appendFile(
      rolloutPath,
      `${JSON.stringify({
        type: "response_item",
        payload: {
          type: "function_call_output",
          call_id: "call-live-shell",
          output: "Exit code: 0\nWall time: 0.1 seconds\nOutput:\nmonitor attached\n",
        },
      })}\n`,
      "utf8",
    );

    await waitForCondition(() => notifications.some((notification) => notification.method === "item/completed" && notification.params.item.id === "call-live-shell"));
    const completedNotification = notifications.find((notification) => notification.method === "item/completed" && notification.params.item.id === "call-live-shell");
    assert.equal(completedNotification.params.item.status, "completed");
    assert.equal(completedNotification.params.item.exitCode, 0);
    assert.match(completedNotification.params.item.aggregatedOutput, /monitor attached/);
  } finally {
    controller.close();
    await fs.rm(rootDir, { recursive: true, force: true });
  }
});

test("ignores non-desktop rollout files when augmenting thread/read", async () => {
  const rootDir = await fs.mkdtemp(path.join(os.tmpdir(), "codexremote-rollout-mobile-"));
  const sessionRoot = path.join(rootDir, "sessions", "2026", "03", "18");
  await fs.mkdir(sessionRoot, { recursive: true });

  const rolloutLines = [
    {
      type: "session_meta",
      payload: {
        id: "thread-3",
        originator: "codex-mobile",
        source: "app-server",
      },
    },
    {
      type: "event_msg",
      payload: {
        type: "task_started",
        turn_id: "turn-3",
      },
    },
    {
      type: "response_item",
      payload: {
        type: "function_call",
        name: "shell_command",
        arguments: JSON.stringify({ command: "echo should-not-mirror" }),
        call_id: "call-mobile-shell",
      },
    },
  ];

  await fs.writeFile(
    path.join(sessionRoot, "rollout-thread-3.jsonl"),
    `${rolloutLines.map((line) => JSON.stringify(line)).join("\n")}\n`,
    "utf8",
  );

  const notifications = [];
  const controller = createRolloutLiveMirrorController({
    sendNotification: (notification) => {
      notifications.push(notification);
    },
    sessionRoot: path.join(rootDir, "sessions"),
    pollIntervalMs: 20,
    idleTimeoutMs: 120,
  });

  try {
    controller.observeClientMessage({
      id: "request-3",
      method: "thread/read",
      params: {
        threadId: "thread-3",
      },
    });

    const originalResponse = {
      id: "request-3",
      result: {
        thread: {
          id: "thread-3",
          turns: [
            {
              id: "turn-3",
              items: [{ type: "userMessage", id: "user-3", text: "No mirror" }],
            },
          ],
        },
      },
    };
    const response = controller.transformTransportMessage(originalResponse);

    assert.deepEqual(response, originalResponse);
    await sleep(120);
    assert.equal(notifications.length, 0);
  } finally {
    controller.close();
    await fs.rm(rootDir, { recursive: true, force: true });
  }
});

async function waitForCondition(predicate, timeoutMs = 1_000) {
  const startedAt = Date.now();
  while (!predicate()) {
    if (Date.now() - startedAt >= timeoutMs) {
      throw new Error("Timed out waiting for condition.");
    }
    await sleep(10);
  }
}

function sleep(timeoutMs) {
  return new Promise((resolve) => setTimeout(resolve, timeoutMs));
}

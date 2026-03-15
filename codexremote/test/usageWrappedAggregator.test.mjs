import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";

import { summarizeUsageWrapped } from "../lib/usageWrappedAggregator.mjs";

test("summarizeUsageWrapped aggregates streaks, activity, and costs from session logs", async () => {
  const rootDir = await fs.mkdtemp(path.join(os.tmpdir(), "codexremote-usage-"));
  const sessionRoot = path.join(rootDir, "sessions", "2026", "03", "14");
  await fs.mkdir(sessionRoot, { recursive: true });

  const sessionOne = [
    {
      type: "session_meta",
      payload: {
        timestamp: "2026-03-12T12:00:00Z",
        cwd: "D:/projects/codexAndroidApp",
        source: "vscode",
      },
    },
    {
      type: "turn_context",
      payload: {
        model: "gpt-5.4",
      },
    },
    {
      type: "event_msg",
      payload: {
        type: "token_count",
        info: {
          total_token_usage: {
            input_tokens: 1000000,
            cached_input_tokens: 1000000,
            output_tokens: 1000000,
            reasoning_output_tokens: 1000000,
            total_tokens: 4000000,
          },
        },
      },
    },
  ];

  const sessionTwo = [
    {
      type: "session_meta",
      payload: {
        timestamp: "2026-03-13T12:00:00Z",
        cwd: "D:/projects/codexAndroidApp",
        source: "vscode",
      },
    },
    {
      type: "turn_context",
      payload: {
        model: "gpt-5.3-codex-spark",
      },
    },
    {
      type: "event_msg",
      payload: {
        type: "token_count",
        info: {
          total_token_usage: {
            input_tokens: 1000000,
            cached_input_tokens: 1000000,
            output_tokens: 1000000,
            reasoning_output_tokens: 1000000,
            total_tokens: 4000000,
          },
        },
      },
    },
  ];

  await fs.writeFile(
    path.join(sessionRoot, "session-one.jsonl"),
    `${sessionOne.map((line) => JSON.stringify(line)).join("\n")}\n`,
    "utf8",
  );
  await fs.writeFile(
    path.join(sessionRoot, "session-two.jsonl"),
    `${sessionTwo.map((line) => JSON.stringify(line)).join("\n")}\n`,
    "utf8",
  );

  try {
    const summary = await summarizeUsageWrapped({
      sessionRoot: path.join(rootDir, "sessions"),
      timeZone: "America/New_York",
    });

    assert.equal(summary.overview.startedAt, "2026-03-12");
    assert.equal(summary.overview.activeDays, 2);
    assert.equal(summary.overview.sessionCount, 2);
    assert.equal(summary.overview.projectCount, 1);
    assert.equal(summary.overview.longestStreakDays, 2);
    assert.equal(summary.tokenTotals.total, 8000000);
    assert.equal(summary.highlights.mostActiveProject.cwd, "D:/projects/codexAndroidApp");
    assert.equal(summary.highlights.mostUsedSource.source, "vscode");
    assert.equal(summary.costEstimate.approximateUsd, 62.68);
    assert.equal(summary.costEstimate.coveragePercent, 100);
    assert.match(
      summary.costEstimate.note,
      /gpt-5\.3-codex-spark mapped to gpt-5\.3-codex public API pricing\./,
    );
  } finally {
    await fs.rm(rootDir, { recursive: true, force: true });
  }
});

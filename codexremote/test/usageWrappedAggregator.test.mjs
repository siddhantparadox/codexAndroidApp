import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";

import { summarizeUsageWrapped } from "../lib/usageWrappedAggregator.mjs";
import { estimateUsageWrappedCost } from "../lib/usageWrappedCost.mjs";

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
            reasoning_output_tokens: 400000,
            total_tokens: 2000000,
          },
          last_token_usage: {
            input_tokens: 200000,
            cached_input_tokens: 200000,
            output_tokens: 200000,
            reasoning_output_tokens: 80000,
            total_tokens: 400000,
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
            reasoning_output_tokens: 400000,
            total_tokens: 2000000,
          },
          last_token_usage: {
            input_tokens: 1000000,
            cached_input_tokens: 1000000,
            output_tokens: 1000000,
            reasoning_output_tokens: 400000,
            total_tokens: 2000000,
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
    assert.equal(summary.tokenTotals.total, 4000000);
    assert.equal(summary.highlights.mostActiveProject.cwd, "D:/projects/codexAndroidApp");
    assert.equal(summary.highlights.mostUsedSource.source, "vscode");
    assert.equal(summary.costEstimate.approximateUsd, 29.43);
    assert.equal(summary.costEstimate.coveragePercent, 100);
    assert.match(
      summary.costEstimate.note,
      /gpt-5\.3-codex-spark mapped to gpt-5\.3-codex public API pricing\./,
    );
  } finally {
    await fs.rm(rootDir, { recursive: true, force: true });
  }
});

test("estimateUsageWrappedCost bills cached input and reasoning without double counting", () => {
  const estimate = estimateUsageWrappedCost({
    "gpt-5.4": {
      input: 1000000,
      cachedInput: 800000,
      output: 100000,
      reasoning: 40000,
      total: 1100000,
    },
  });

  assert.equal(estimate.approximateUsd, 2.2);
  assert.equal(estimate.coveragePercent, 100);
});

test("estimateUsageWrappedCost prices gpt-5.1-codex-mini separately", () => {
  const estimate = estimateUsageWrappedCost({
    "gpt-5.1-codex-mini": {
      input: 1000000,
      cachedInput: 500000,
      output: 100000,
      reasoning: 50000,
      total: 1100000,
    },
  });

  assert.equal(estimate.approximateUsd, 0.34);
  assert.equal(estimate.coveragePercent, 100);
});

test("summarizeUsageWrapped applies gpt-5.4 long-context pricing when detected", async () => {
  const rootDir = await fs.mkdtemp(path.join(os.tmpdir(), "codexremote-usage-long-context-"));
  const sessionRoot = path.join(rootDir, "sessions");
  await fs.mkdir(sessionRoot, { recursive: true });

  const session = [
    {
      type: "session_meta",
      payload: {
        timestamp: "2026-03-14T12:00:00Z",
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
            input_tokens: 400000,
            cached_input_tokens: 0,
            output_tokens: 40000,
            reasoning_output_tokens: 10000,
            total_tokens: 440000,
          },
          last_token_usage: {
            input_tokens: 400000,
            cached_input_tokens: 0,
            output_tokens: 40000,
            reasoning_output_tokens: 10000,
            total_tokens: 440000,
          },
        },
      },
    },
  ];

  await fs.writeFile(
    path.join(sessionRoot, "long-context.jsonl"),
    `${session.map((line) => JSON.stringify(line)).join("\n")}\n`,
    "utf8",
  );

  try {
    const summary = await summarizeUsageWrapped({
      sessionRoot,
      timeZone: "America/New_York",
    });

    assert.equal(summary.tokenTotals.total, 440000);
    assert.equal(summary.costEstimate.approximateUsd, 2.9);
  } finally {
    await fs.rm(rootDir, { recursive: true, force: true });
  }
});

test("summarizeUsageWrapped corrects model totals when cumulative counters move backward", async () => {
  const rootDir = await fs.mkdtemp(path.join(os.tmpdir(), "codexremote-usage-regression-"));
  const sessionRoot = path.join(rootDir, "sessions");
  await fs.mkdir(sessionRoot, { recursive: true });

  const session = [
    {
      type: "session_meta",
      payload: {
        timestamp: "2026-03-14T12:00:00Z",
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
            input_tokens: 80000,
            cached_input_tokens: 0,
            output_tokens: 8000,
            reasoning_output_tokens: 2000,
            total_tokens: 88000,
          },
          last_token_usage: {
            input_tokens: 80000,
            cached_input_tokens: 0,
            output_tokens: 8000,
            reasoning_output_tokens: 2000,
            total_tokens: 88000,
          },
        },
      },
    },
    {
      type: "event_msg",
      payload: {
        type: "token_count",
        info: {
          total_token_usage: {
            input_tokens: 60000,
            cached_input_tokens: 0,
            output_tokens: 6000,
            reasoning_output_tokens: 1500,
            total_tokens: 66000,
          },
          last_token_usage: {
            input_tokens: 60000,
            cached_input_tokens: 0,
            output_tokens: 6000,
            reasoning_output_tokens: 1500,
            total_tokens: 66000,
          },
        },
      },
    },
    {
      type: "event_msg",
      payload: {
        type: "token_count",
        info: {
          total_token_usage: {
            input_tokens: 70000,
            cached_input_tokens: 0,
            output_tokens: 7000,
            reasoning_output_tokens: 1700,
            total_tokens: 77000,
          },
          last_token_usage: {
            input_tokens: 70000,
            cached_input_tokens: 0,
            output_tokens: 7000,
            reasoning_output_tokens: 1700,
            total_tokens: 77000,
          },
        },
      },
    },
  ];

  await fs.writeFile(
    path.join(sessionRoot, "regression.jsonl"),
    `${session.map((line) => JSON.stringify(line)).join("\n")}\n`,
    "utf8",
  );

  try {
    const summary = await summarizeUsageWrapped({
      sessionRoot,
      timeZone: "America/New_York",
    });

    assert.equal(summary.tokenTotals.total, 77000);
    assert.equal(summary.costEstimate.approximateUsd, 0.28);
  } finally {
    await fs.rm(rootDir, { recursive: true, force: true });
  }
});

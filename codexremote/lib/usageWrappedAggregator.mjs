import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import readline from "node:readline";

import { estimateUsageWrappedCost } from "./usageWrappedCost.mjs";

const UNKNOWN_MODEL_ID = "_unknown";
const GPT_54_MODEL_ID = "gpt-5.4";
const GPT_54_LONG_CONTEXT_MODEL_ID = "gpt-5.4-long-context";
const LONG_CONTEXT_INPUT_THRESHOLD = 272_000;

export function defaultCodexHome() {
  const override = process.env.CODEX_HOME;
  return override && override.trim() ? override : path.join(os.homedir(), ".codex");
}

export function defaultSessionRoot() {
  return path.join(defaultCodexHome(), "sessions");
}

export async function summarizeUsageWrapped({
  sessionRoot = defaultSessionRoot(),
  timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone,
} = {}) {
  const sessions = await loadSessions(sessionRoot);
  return summarizeSessions({ sessions, timeZone });
}

export function summarizeSessions({
  sessions,
  timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone,
  now = new Date(),
} = {}) {
  const sortedSessions = [...sessions].sort(
    (left, right) => new Date(left.startedAt).getTime() - new Date(right.startedAt).getTime(),
  );

  if (sortedSessions.length === 0) {
    return createEmptySummary(now);
  }

  const dates = sortedSessions.map((session) => toDateKey(session.startedAt, timeZone));
  const uniqueDates = [...new Set(dates)].sort();
  const activityByDate = buildActivityByDate(sortedSessions, dates);
  const projectStats = buildProjectStats(sortedSessions);
  const sourceStats = buildSourceStats(sortedSessions);
  const tokenTotalsByModel = buildTokenTotalsByModel(sortedSessions);
  const tokenTotals = sortedSessions.reduce(
    (sum, session) => plusTotals(sum, session.tokenTotals),
    emptyTotals(),
  );

  return {
    generatedAt: now.toISOString(),
    range: {
      start: uniqueDates[0] ?? null,
      end: uniqueDates[uniqueDates.length - 1] ?? null,
    },
    overview: {
      startedAt: uniqueDates[0] ?? null,
      activeDays: uniqueDates.length,
      sessionCount: sortedSessions.length,
      projectCount: Object.keys(projectStats).length,
      currentStreakDays: currentStreakDays(uniqueDates, toDateKey(now, timeZone)),
      longestStreakDays: longestStreakDays(uniqueDates),
    },
    tokenTotals,
    costEstimate: estimateUsageWrappedCost(tokenTotalsByModel),
    highlights: {
      mostActiveDay: maxBy(
        Object.values(activityByDate),
        (day) => [day.totalTokens, day.sessionCount],
      ),
      mostActiveProject: maxBy(
        Object.values(projectStats),
        (project) => [project.totalTokens, project.sessionCount],
      ),
      mostUsedSource: maxBy(
        Object.values(sourceStats),
        (source) => [source.sessionCount],
      ),
    },
    activity: Object.values(activityByDate).sort((left, right) => left.date.localeCompare(right.date)),
  };
}

async function loadSessions(sessionRoot) {
  if (!fs.existsSync(sessionRoot)) {
    return [];
  }

  const files = await walkJsonlFiles(sessionRoot);
  const sessions = [];
  for (const filePath of files) {
    const session = await parseSession(filePath);
    if (session) {
      sessions.push(session);
    }
  }
  return sessions;
}

async function walkJsonlFiles(root) {
  const results = [];
  const entries = await fs.promises.readdir(root, { withFileTypes: true });
  for (const entry of entries) {
    const resolvedPath = path.join(root, entry.name);
    if (entry.isDirectory()) {
      results.push(...await walkJsonlFiles(resolvedPath));
      continue;
    }
    if (entry.isFile() && resolvedPath.endsWith(".jsonl")) {
      results.push(resolvedPath);
    }
  }
  return results;
}

async function parseSession(filePath) {
  let startedAt = null;
  let cwd = null;
  let source = null;
  let tokenTotals = emptyTotals();
  let currentModel = null;
  const tokenTotalsByModel = {};

  const lines = readline.createInterface({
    input: fs.createReadStream(filePath, { encoding: "utf8" }),
    crlfDelay: Infinity,
  });

  try {
    for await (const line of lines) {
      if (!line.trim()) continue;
      let payload;
      try {
        payload = JSON.parse(line);
      } catch {
        continue;
      }

      switch (payload?.type) {
        case "session_meta": {
          const meta = payload.payload ?? {};
          startedAt = meta.timestamp || startedAt;
          cwd = meta.cwd || cwd;
          source = meta.source || source;
          break;
        }

        case "turn_context": {
          const context = payload.payload ?? {};
          currentModel = context.model || currentModel;
          break;
        }

        case "event_msg": {
          const event = payload.payload ?? {};
          if (event.type !== "token_count") {
            break;
          }
          const totals = event.info?.total_token_usage;
          if (!totals) {
            break;
          }
          const updatedTotals = {
            input: numericValue(totals.input_tokens),
            cachedInput: numericValue(totals.cached_input_tokens),
            output: numericValue(totals.output_tokens),
            reasoning: numericValue(totals.reasoning_output_tokens),
            total: numericValue(totals.total_tokens),
          };
          const lastUsage = event.info?.last_token_usage;
          const lastTotals = {
            input: numericValue(lastUsage?.input_tokens),
            cachedInput: numericValue(lastUsage?.cached_input_tokens),
            output: numericValue(lastUsage?.output_tokens),
            reasoning: numericValue(lastUsage?.reasoning_output_tokens),
            total: numericValue(lastUsage?.total_tokens),
          };
          const modelId = pricingBucketModelId(currentModel, lastTotals);
          const correction = regressionTotals(tokenTotals, updatedTotals);
          if (modelId !== UNKNOWN_MODEL_ID && isNonEmptyTotals(correction)) {
            tokenTotalsByModel[modelId] = minusTotals(tokenTotalsByModel[modelId] ?? emptyTotals(), correction);
          }
          const delta = deltaTotals(updatedTotals, tokenTotals);
          tokenTotals = updatedTotals;
          if (modelId !== UNKNOWN_MODEL_ID && isNonEmptyTotals(delta)) {
            tokenTotalsByModel[modelId] = plusTotals(tokenTotalsByModel[modelId] ?? emptyTotals(), delta);
          }
          break;
        }

        default:
          break;
      }
    }
  } finally {
    lines.close();
  }

  if (!startedAt) {
    return null;
  }

  return {
    startedAt,
    cwd,
    source,
    tokenTotals,
    tokenTotalsByModel,
  };
}

function buildActivityByDate(sessions, dates) {
  const dayStats = {};
  sessions.forEach((session, index) => {
    const date = dates[index];
    const existing = dayStats[date] ?? {
      date,
      sessionCount: 0,
      totalTokens: 0,
    };
    dayStats[date] = {
      date,
      sessionCount: existing.sessionCount + 1,
      totalTokens: existing.totalTokens + session.tokenTotals.total,
    };
  });
  return dayStats;
}

function buildProjectStats(sessions) {
  const stats = {};
  for (const session of sessions) {
    if (!session.cwd || !String(session.cwd).trim()) continue;
    const existing = stats[session.cwd] ?? {
      cwd: session.cwd,
      sessionCount: 0,
      totalTokens: 0,
    };
    stats[session.cwd] = {
      cwd: session.cwd,
      sessionCount: existing.sessionCount + 1,
      totalTokens: existing.totalTokens + session.tokenTotals.total,
    };
  }
  return stats;
}

function buildSourceStats(sessions) {
  const stats = {};
  for (const session of sessions) {
    if (!session.source || !String(session.source).trim()) continue;
    const existing = stats[session.source] ?? {
      source: session.source,
      sessionCount: 0,
    };
    stats[session.source] = {
      source: session.source,
      sessionCount: existing.sessionCount + 1,
    };
  }
  return stats;
}

function buildTokenTotalsByModel(sessions) {
  const totalsByModel = {};
  for (const session of sessions) {
    for (const [modelId, totals] of Object.entries(session.tokenTotalsByModel ?? {})) {
      totalsByModel[modelId] = plusTotals(totalsByModel[modelId] ?? emptyTotals(), totals);
    }
  }
  return totalsByModel;
}

function currentStreakDays(activeDates, todayKey) {
  if (activeDates.length === 0) {
    return 0;
  }

  const latestActiveDate = activeDates[activeDates.length - 1];
  const streakAnchor = latestActiveDate === todayKey
    ? todayKey
    : latestActiveDate === shiftDateKey(todayKey, -1)
      ? latestActiveDate
      : null;
  if (!streakAnchor) {
    return 0;
  }

  const activeSet = new Set(activeDates);
  let cursor = streakAnchor;
  let streak = 0;
  while (activeSet.has(cursor)) {
    streak += 1;
    cursor = shiftDateKey(cursor, -1);
  }
  return streak;
}

function longestStreakDays(activeDates) {
  if (activeDates.length === 0) {
    return 0;
  }

  let longest = 1;
  let current = 1;
  for (let index = 0; index < activeDates.length - 1; index += 1) {
    const previous = activeDates[index];
    const next = activeDates[index + 1];
    if (shiftDateKey(previous, 1) === next) {
      current += 1;
      longest = Math.max(longest, current);
    } else {
      current = 1;
    }
  }
  return longest;
}

function maxBy(values, ranker) {
  if (values.length === 0) {
    return null;
  }

  return values.reduce((best, candidate) => {
    if (!best) return candidate;
    return compareRanks(ranker(candidate), ranker(best)) > 0 ? candidate : best;
  }, null);
}

function compareRanks(left, right) {
  const length = Math.max(left.length, right.length);
  for (let index = 0; index < length; index += 1) {
    const leftValue = left[index] ?? 0;
    const rightValue = right[index] ?? 0;
    if (leftValue > rightValue) return 1;
    if (leftValue < rightValue) return -1;
  }
  return 0;
}

function createEmptySummary(now) {
  return {
    generatedAt: now.toISOString(),
    range: {
      start: null,
      end: null,
    },
    overview: {
      startedAt: null,
      activeDays: 0,
      sessionCount: 0,
      projectCount: 0,
      currentStreakDays: 0,
      longestStreakDays: 0,
    },
    tokenTotals: emptyTotals(),
    costEstimate: null,
    highlights: {
      mostActiveDay: null,
      mostActiveProject: null,
      mostUsedSource: null,
    },
    activity: [],
  };
}

function emptyTotals() {
  return {
    input: 0,
    cachedInput: 0,
    output: 0,
    reasoning: 0,
    total: 0,
  };
}

function plusTotals(left, right) {
  return {
    input: (left.input || 0) + (right.input || 0),
    cachedInput: (left.cachedInput || 0) + (right.cachedInput || 0),
    output: (left.output || 0) + (right.output || 0),
    reasoning: (left.reasoning || 0) + (right.reasoning || 0),
    total: (left.total || 0) + (right.total || 0),
  };
}

function minusTotals(left, right) {
  return {
    input: Math.max((left.input || 0) - (right.input || 0), 0),
    cachedInput: Math.max((left.cachedInput || 0) - (right.cachedInput || 0), 0),
    output: Math.max((left.output || 0) - (right.output || 0), 0),
    reasoning: Math.max((left.reasoning || 0) - (right.reasoning || 0), 0),
    total: Math.max((left.total || 0) - (right.total || 0), 0),
  };
}

function regressionTotals(previous, current) {
  return {
    input: Math.max((previous.input || 0) - (current.input || 0), 0),
    cachedInput: Math.max((previous.cachedInput || 0) - (current.cachedInput || 0), 0),
    output: Math.max((previous.output || 0) - (current.output || 0), 0),
    reasoning: Math.max((previous.reasoning || 0) - (current.reasoning || 0), 0),
    total: Math.max((previous.total || 0) - (current.total || 0), 0),
  };
}

function deltaTotals(current, previous) {
  return {
    input: Math.max((current.input || 0) - (previous.input || 0), 0),
    cachedInput: Math.max((current.cachedInput || 0) - (previous.cachedInput || 0), 0),
    output: Math.max((current.output || 0) - (previous.output || 0), 0),
    reasoning: Math.max((current.reasoning || 0) - (previous.reasoning || 0), 0),
    total: Math.max((current.total || 0) - (previous.total || 0), 0),
  };
}

function isNonEmptyTotals(totals) {
  return (
    totals.total > 0 ||
    totals.input > 0 ||
    totals.cachedInput > 0 ||
    totals.output > 0 ||
    totals.reasoning > 0
  );
}

function numericValue(value) {
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : 0;
}

function pricingBucketModelId(modelId, lastTotals) {
  const normalizedModelId = String(modelId || "").trim().toLowerCase();
  if (!normalizedModelId) {
    return UNKNOWN_MODEL_ID;
  }
  if (normalizedModelId === GPT_54_MODEL_ID && (lastTotals.input || 0) > LONG_CONTEXT_INPUT_THRESHOLD) {
    return GPT_54_LONG_CONTEXT_MODEL_ID;
  }
  return normalizedModelId;
}

function toDateKey(value, timeZone) {
  const formatter = new Intl.DateTimeFormat("en-US", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
  const parts = formatter.formatToParts(new Date(value));
  const year = parts.find((part) => part.type === "year")?.value;
  const month = parts.find((part) => part.type === "month")?.value;
  const day = parts.find((part) => part.type === "day")?.value;
  return `${year}-${month}-${day}`;
}

function shiftDateKey(dateKey, dayDelta) {
  const [year, month, day] = dateKey.split("-").map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  date.setUTCDate(date.getUTCDate() + dayDelta);
  const shiftedYear = String(date.getUTCFullYear()).padStart(4, "0");
  const shiftedMonth = String(date.getUTCMonth() + 1).padStart(2, "0");
  const shiftedDay = String(date.getUTCDate()).padStart(2, "0");
  return `${shiftedYear}-${shiftedMonth}-${shiftedDay}`;
}

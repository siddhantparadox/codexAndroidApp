import { execFileSync, spawn } from "node:child_process";
import WebSocket from "ws";

import { sleep } from "./network.mjs";

export const DEFAULT_APP_SERVER_PORT = 4500;
export const STARTUP_TIMEOUT_MS = 10_000;
const PROBE_TIMEOUT_MS = 1_500;
const APP_SERVER_URL = `ws://0.0.0.0:${DEFAULT_APP_SERVER_PORT}`;
const LOCAL_PROBE_URL = `ws://127.0.0.1:${DEFAULT_APP_SERVER_PORT}`;

export function resolveCodexLaunch() {
  if (process.platform !== "win32") {
    return {
      command: "codex",
      args: ["app-server", "--listen", APP_SERVER_URL],
    };
  }

  const codexCommand = resolveWindowsCodexCommand();
  const cmdLauncher = process.env.ComSpec || "cmd.exe";

  return {
    command: cmdLauncher,
    args: ["/d", "/s", "/c", codexCommand, "app-server", "--listen", APP_SERVER_URL],
  };
}

export function spawnCodexAppServer() {
  const codexLaunch = resolveCodexLaunch();
  let startupError = null;
  const child = spawn(codexLaunch.command, codexLaunch.args, {
    stdio: ["ignore", "ignore", "inherit"],
    windowsHide: false,
  });

  child.once("error", (error) => {
    startupError = error;
  });

  return {
    child,
    readStartupError() {
      return startupError;
    },
  };
}

export function probeCodexAppServer(version = "0.2.0") {
  return new Promise((resolve) => {
    let settled = false;
    const socket = new WebSocket(LOCAL_PROBE_URL);
    const timeout = setTimeout(() => finish(false), PROBE_TIMEOUT_MS);

    function finish(result) {
      if (settled) return;
      settled = true;
      clearTimeout(timeout);
      socket.removeAllListeners();
      try {
        socket.close();
      } catch {
        // no-op
      }
      resolve(result);
    }

    socket.on("open", () => {
      socket.send(
        JSON.stringify({
          method: "initialize",
          id: 1,
          params: {
            clientInfo: {
              name: "codexremote",
              title: "CodexRemote",
              version,
            },
          },
        }),
      );
    });

    socket.on("message", (data) => {
      try {
        const message = JSON.parse(String(data));
        if (message?.id === 1) {
          finish(true);
        }
      } catch {
        finish(false);
      }
    });

    socket.on("error", () => finish(false));
    socket.on("close", () => finish(false));
  });
}

export async function waitForCodexAppServer(child, readStartupError = () => null, version = "0.2.0") {
  const startTime = Date.now();
  while (Date.now() - startTime < STARTUP_TIMEOUT_MS) {
    const startupError = readStartupError();
    if (startupError) {
      throw new Error(`Failed to start codex app-server: ${startupError.message}`);
    }
    if (child.exitCode !== null) {
      throw new Error("codex app-server exited before it became ready.");
    }
    if (await probeCodexAppServer(version)) {
      return;
    }
    await sleep(250);
  }
  throw new Error(`Timed out waiting for codex app-server to start on port ${DEFAULT_APP_SERVER_PORT}.`);
}

export function waitForChildExit(child) {
  return new Promise((resolve, reject) => {
    child.once("exit", (code, signal) => {
      if (code === 0 || signal === "SIGTERM" || signal === "SIGINT") {
        resolve();
      } else {
        reject(new Error(`codex app-server exited unexpectedly (${signal ?? code}).`));
      }
    });
  });
}

function resolveWindowsCodexCommand() {
  try {
    const result = execFileSync("where.exe", ["codex.cmd"], {
      encoding: "utf8",
      windowsHide: true,
      stdio: ["ignore", "pipe", "ignore"],
    });
    const firstMatch = result
      .split(/\r?\n/)
      .map((line) => line.trim())
      .find(Boolean);
    if (firstMatch) {
      return firstMatch;
    }
  } catch {
    // Fall through to PATH-based resolution below.
  }

  return "codex.cmd";
}

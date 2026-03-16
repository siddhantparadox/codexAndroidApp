import { execFileSync, spawn } from "node:child_process";
import { EventEmitter } from "node:events";
import { createInterface } from "node:readline";

export const STARTUP_TIMEOUT_MS = 10_000;
const REQUEST_TIMEOUT_MS = 30_000;

export class CodexAppServerProcess extends EventEmitter {
  #child = null;
  #exitPromise = Promise.resolve();
  #initializeResult = null;
  #lineReader = null;
  #nextRequestId = 1;
  #pendingRequests = new Map();
  #versionName;
  #closing = false;

  constructor({ versionName }) {
    super();
    this.#versionName = versionName;
  }

  get initializeResult() {
    return this.#initializeResult;
  }

  async start() {
    if (this.#child) {
      return;
    }

    const codexLaunch = resolveCodexLaunch();
    const child = spawn(codexLaunch.command, codexLaunch.args, {
      stdio: ["pipe", "pipe", "inherit"],
      windowsHide: false,
    });

    this.#child = child;
    this.#lineReader = createInterface({
      input: child.stdout,
      crlfDelay: Infinity,
    });
    this.#lineReader.on("line", (line) => {
      this.#handleStdoutLine(line);
    });

    child.on("error", (error) => {
      this.#rejectPendingRequests(error);
    });

    this.#exitPromise = new Promise((resolve, reject) => {
      child.once("exit", (code, signal) => {
        this.#lineReader?.close();
        this.#lineReader = null;
        this.#child = null;

        const gracefulExit =
          this.#closing ||
          code === 0 ||
          signal === "SIGTERM" ||
          signal === "SIGINT";
        const exitError = gracefulExit
          ? null
          : new Error(`codex app-server exited unexpectedly (${signal ?? code}).`);

        if (exitError) {
          this.#rejectPendingRequests(exitError);
        } else {
          this.#rejectPendingRequests(new Error("codex app-server closed."));
        }

        this.emit("exit", {
          code,
          signal,
          graceful: gracefulExit,
          error: exitError,
        });

        if (exitError) {
          reject(exitError);
        } else {
          resolve();
        }
      });
    });

    await new Promise((resolve, reject) => {
      function cleanup() {
        child.off("spawn", handleSpawn);
        child.off("error", handleError);
      }

      function handleSpawn() {
        cleanup();
        resolve();
      }

      function handleError(error) {
        cleanup();
        reject(error);
      }

      child.once("spawn", handleSpawn);
      child.once("error", handleError);
    });
  }

  async initialize() {
    if (this.#initializeResult) {
      return this.#initializeResult;
    }

    const result = await this.request(
      "initialize",
      {
        clientInfo: {
          name: "codexremote_bridge",
          title: "CodexRemote Bridge",
          version: this.#versionName,
        },
        capabilities: {
          experimentalApi: true,
        },
      },
      STARTUP_TIMEOUT_MS,
    );
    this.notify("initialized", {});
    this.#initializeResult = result;
    return result;
  }

  request(method, params = {}, timeoutMs = REQUEST_TIMEOUT_MS) {
    const requestId = `bridge-${this.#nextRequestId}`;
    this.#nextRequestId += 1;

    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        this.#pendingRequests.delete(requestId);
        reject(new Error(`Timed out waiting for codex app-server response to ${method}.`));
      }, timeoutMs);

      this.#pendingRequests.set(requestId, {
        resolve(value) {
          clearTimeout(timeout);
          resolve(value);
        },
        reject(error) {
          clearTimeout(timeout);
          reject(error);
        },
      });

      try {
        this.send({
          id: requestId,
          method,
          params,
        });
      } catch (error) {
        clearTimeout(timeout);
        this.#pendingRequests.delete(requestId);
        reject(error);
      }
    });
  }

  notify(method, params = {}) {
    this.send({
      method,
      params,
    });
  }

  send(message) {
    const child = this.#child;
    if (!child?.stdin?.writable) {
      throw new Error("codex app-server stdin is not writable.");
    }

    const line = `${JSON.stringify(message)}\n`;
    child.stdin.write(line, "utf8");
  }

  async close() {
    this.#closing = true;
    this.#lineReader?.close();
    this.#lineReader = null;

    const child = this.#child;
    if (!child) {
      return;
    }

    child.kill();
    await this.#exitPromise.catch(() => {});
  }

  waitForExit() {
    return this.#exitPromise;
  }

  #handleStdoutLine(line) {
    const trimmedLine = line.trim();
    if (!trimmedLine) {
      return;
    }

    let message;
    try {
      message = JSON.parse(trimmedLine);
    } catch (error) {
      process.stderr.write(`Invalid JSON from codex app-server: ${error.message}\n`);
      return;
    }

    const requestId = message?.id == null ? null : String(message.id);
    if (requestId && this.#pendingRequests.has(requestId) && message.method == null) {
      const pending = this.#pendingRequests.get(requestId);
      this.#pendingRequests.delete(requestId);
      const errorMessage = message?.error?.message;
      if (errorMessage) {
        pending.reject(new Error(errorMessage));
      } else {
        pending.resolve(message.result ?? {});
      }
      return;
    }

    this.emit("message", message);
  }

  #rejectPendingRequests(error) {
    const pendingEntries = Array.from(this.#pendingRequests.values());
    this.#pendingRequests.clear();
    pendingEntries.forEach((pending) => {
      pending.reject(error);
    });
  }
}

export function resolveCodexLaunch() {
  if (process.platform !== "win32") {
    return {
      command: "codex",
      args: ["app-server", "--listen", "stdio://"],
    };
  }

  const codexCommand = resolveWindowsCodexCommand();
  const cmdLauncher = process.env.ComSpec || "cmd.exe";

  return {
    command: cmdLauncher,
    args: ["/d", "/s", "/c", codexCommand, "app-server", "--listen", "stdio://"],
  };
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

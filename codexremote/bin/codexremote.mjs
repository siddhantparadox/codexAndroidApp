#!/usr/bin/env node

import net from "node:net";
import os from "node:os";
import process from "node:process";
import { execFileSync, spawn } from "node:child_process";
import qrcode from "qrcode-terminal";
import WebSocket from "ws";

const DEFAULT_PORT = 4500;
const STARTUP_TIMEOUT_MS = 10_000;
const PROBE_TIMEOUT_MS = 1_500;
const CONNECT_TIMEOUT_MS = 750;
const APP_SERVER_URL = `ws://0.0.0.0:${DEFAULT_PORT}`;
const LOCAL_PROBE_URL = `ws://127.0.0.1:${DEFAULT_PORT}`;

if (process.argv.includes("--help") || process.argv.includes("-h")) {
  printUsage();
  process.exit(0);
}

let ownedChild = null;

try {
  const desktopName = os.hostname();
  const desktopId = desktopName;
  const host = pickLanIpv4Address();
  if (!host) {
    throw new Error("No LAN IPv4 address was found. Connect to Wi-Fi or Ethernet and try again.");
  }

  const tcpOccupied = await isTcpPortOccupied(DEFAULT_PORT);
  const canReuseExisting = tcpOccupied ? await probeCodexAppServer() : false;

  if (tcpOccupied && !canReuseExisting) {
    throw new Error(
      `Port ${DEFAULT_PORT} is already in use by another process. Stop that process or free the port before running codexremote.`,
    );
  }

  if (!tcpOccupied) {
    const codexLaunch = resolveCodexLaunch();
    let startupError = null;

    ownedChild = spawn(codexLaunch.command, codexLaunch.args, {
      stdio: ["ignore", "ignore", "inherit"],
      windowsHide: false,
    });

    ownedChild.once("error", (error) => {
      startupError = error;
    });

    await waitForCodexAppServer(ownedChild, () => startupError);
  }

  const payload = {
    version: 1,
    desktopId,
    desktopName,
    host,
    port: DEFAULT_PORT,
  };
  const connectionCode = encodeConnectionCode(host, DEFAULT_PORT);

  printBootstrapCard({
    desktopName,
    host,
    port: DEFAULT_PORT,
    connectionCode,
    payload,
    reusingExisting: canReuseExisting,
  });

  registerShutdownHandlers();
  if (ownedChild) {
    await waitForChildExit(ownedChild);
  } else {
    await waitForSignal();
  }
} catch (error) {
  if (ownedChild) {
    ownedChild.kill();
  }
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(1);
}

function printUsage() {
  console.log("Usage: npx codexremote");
  console.log("");
  console.log("Starts Codex app-server on port 4500 and prints a QR code for Codex Mobile.");
}

function resolveCodexLaunch() {
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

function pickLanIpv4Address() {
  const networks = os.networkInterfaces();
  const candidates = [];

  for (const addresses of Object.values(networks)) {
    if (!addresses) continue;
    for (const address of addresses) {
      if (address.family !== "IPv4" || address.internal) continue;
      candidates.push(address.address);
    }
  }

  const privateCandidate = candidates.find(isPrivateIpv4Address);
  return privateCandidate ?? candidates[0] ?? null;
}

function isPrivateIpv4Address(value) {
  return (
    value.startsWith("10.") ||
    value.startsWith("192.168.") ||
    /^172\.(1[6-9]|2\d|3[0-1])\./.test(value)
  );
}

function isTcpPortOccupied(port) {
  return new Promise((resolve) => {
    const socket = new net.Socket();
    const onFailure = (error) => {
      socket.destroy();
      if (error?.code === "ECONNREFUSED") {
        resolve(false);
      } else {
        resolve(false);
      }
    };

    socket.setTimeout(CONNECT_TIMEOUT_MS);
    socket.once("connect", () => {
      socket.destroy();
      resolve(true);
    });
    socket.once("timeout", () => onFailure(new Error("timeout")));
    socket.once("error", onFailure);
    socket.connect(port, "127.0.0.1");
  });
}

function probeCodexAppServer() {
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
              version: "0.1.0",
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

async function waitForCodexAppServer(child, readStartupError = () => null) {
  const startTime = Date.now();
  while (Date.now() - startTime < STARTUP_TIMEOUT_MS) {
    const startupError = readStartupError();
    if (startupError) {
      throw new Error(`Failed to start codex app-server: ${startupError.message}`);
    }
    if (child.exitCode !== null) {
      throw new Error("codex app-server exited before it became ready.");
    }
    if (await probeCodexAppServer()) {
      return;
    }
    await sleep(250);
  }
  throw new Error("Timed out waiting for codex app-server to start on port 4500.");
}

function printBootstrapCard({ desktopName, host, port, connectionCode, payload, reusingExisting }) {
  console.log("");
  console.log(reusingExisting ? "Codex app-server is already running on port 4500." : "Mobile access is on.");
  console.log(`Desktop: ${desktopName}`);
  console.log(`Address: ws://${host}:${port}`);
  console.log(`Connection code: ${connectionCode}`);
  console.log("");
  qrcode.generate(JSON.stringify(payload), { small: true });
  console.log("");
  console.log("Scan the QR code from Codex Mobile.");
  console.log("Press Ctrl+C to stop.");
}

function encodeConnectionCode(host, port) {
  const segments = host.split(".").map((segment) => Number(segment));
  if (segments.length !== 4 || segments.some((segment) => !Number.isInteger(segment) || segment < 0 || segment > 255)) {
    throw new Error("Only IPv4 LAN addresses can be encoded as a connection code.");
  }

  const bytes = Buffer.alloc(8);
  for (let index = 0; index < 4; index += 1) {
    bytes[index] = segments[index];
  }
  bytes[4] = (port >>> 8) & 0xff;
  bytes[5] = port & 0xff;
  const checksum = checksum16(bytes.subarray(0, 6));
  bytes[6] = (checksum >>> 8) & 0xff;
  bytes[7] = checksum & 0xff;

  return bytes.toString("hex").toUpperCase().match(/.{1,4}/g).join("-");
}

function checksum16(bytes) {
  let checksum = 0;
  for (const value of bytes) {
    checksum = (checksum + value) & 0xffff;
  }
  return checksum;
}

function registerShutdownHandlers() {
  const shutdown = () => {
    if (ownedChild) {
      ownedChild.kill();
    }
  };

  process.on("SIGINT", () => {
    shutdown();
    process.exit(0);
  });
  process.on("SIGTERM", () => {
    shutdown();
    process.exit(0);
  });
  process.on("exit", shutdown);
}

function waitForChildExit(child) {
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

function waitForSignal() {
  return new Promise(() => {
    // Intentionally unresolved. The process exits via signal handlers.
  });
}

function sleep(ms) {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}

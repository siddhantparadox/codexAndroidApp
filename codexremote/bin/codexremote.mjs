#!/usr/bin/env node

import process from "node:process";
import qrcode from "qrcode-terminal";

import {
  DEFAULT_APP_SERVER_PORT,
  probeCodexAppServer,
  spawnCodexAppServer,
  waitForChildExit,
  waitForCodexAppServer,
} from "../lib/appServer.mjs";
import { isTcpPortOccupied, pickLanIpv4Address } from "../lib/network.mjs";
import {
  DEFAULT_USAGE_WRAPPED_PORT,
  probeUsageWrappedService,
  startUsageWrappedService,
} from "../lib/usageWrappedService.mjs";

const VERSION = "0.2.0";

if (process.argv.includes("--help") || process.argv.includes("-h")) {
  printUsage();
  process.exit(0);
}

let ownedChild = null;
let ownedUsageService = null;

try {
  const desktopName = process.env.CODEXREMOTE_DESKTOP_NAME || process.env.COMPUTERNAME || process.env.HOSTNAME || "Codex Desktop";
  const desktopId = desktopName;
  const host = pickLanIpv4Address();
  if (!host) {
    throw new Error("No LAN IPv4 address was found. Connect to Wi-Fi or Ethernet and try again.");
  }

  const appServerOccupied = await isTcpPortOccupied(DEFAULT_APP_SERVER_PORT);
  const reusingAppServer = appServerOccupied ? await probeCodexAppServer(VERSION) : false;
  if (appServerOccupied && !reusingAppServer) {
    throw new Error(
      `Port ${DEFAULT_APP_SERVER_PORT} is already in use by another process. Stop that process or free the port before running codexremote.`,
    );
  }

  if (!appServerOccupied) {
    const codexLaunch = spawnCodexAppServer();
    ownedChild = codexLaunch.child;
    await waitForCodexAppServer(ownedChild, codexLaunch.readStartupError, VERSION);
  }

  const usageServiceOccupied = await isTcpPortOccupied(DEFAULT_USAGE_WRAPPED_PORT);
  const reusingUsageService = usageServiceOccupied ? await probeUsageWrappedService(DEFAULT_USAGE_WRAPPED_PORT) : false;
  if (usageServiceOccupied && !reusingUsageService) {
    throw new Error(
      `Port ${DEFAULT_USAGE_WRAPPED_PORT} is already in use by another process. Stop that process or free the port before running codexremote.`,
    );
  }

  if (!usageServiceOccupied) {
    ownedUsageService = await startUsageWrappedService({
      port: DEFAULT_USAGE_WRAPPED_PORT,
      host: "0.0.0.0",
    });
  }

  const payload = {
    version: 1,
    desktopId,
    desktopName,
    host,
    port: DEFAULT_APP_SERVER_PORT,
  };
  const connectionCode = encodeConnectionCode(host, DEFAULT_APP_SERVER_PORT);

  printBootstrapCard({
    desktopName,
    host,
    port: DEFAULT_APP_SERVER_PORT,
    usageWrappedPort: DEFAULT_USAGE_WRAPPED_PORT,
    connectionCode,
    payload,
    reusingAppServer,
    reusingUsageService,
  });

  registerShutdownHandlers();
  if (ownedChild) {
    await waitForChildExit(ownedChild);
  } else {
    await waitForSignal();
  }
} catch (error) {
  await shutdownOwnedProcesses();
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(1);
}

function printUsage() {
  console.log("Usage: npx codexremote");
  console.log("");
  console.log("Starts Codex app-server on port 4500, starts Usage Wrapped on port 4501, and prints a QR code for Codex Mobile.");
}

function printBootstrapCard({
  desktopName,
  host,
  port,
  usageWrappedPort,
  connectionCode,
  payload,
  reusingAppServer,
  reusingUsageService,
}) {
  console.log("");
  console.log(reusingAppServer ? "Codex app-server is already running on port 4500." : "Mobile access is on.");
  console.log(reusingUsageService ? "Usage Wrapped is already running on port 4501." : "Usage Wrapped is on.");
  console.log(`Desktop: ${desktopName}`);
  console.log(`Address: ws://${host}:${port}`);
  console.log(`Usage Wrapped: http://${host}:${usageWrappedPort}/usage-wrapped`);
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
  const shutdownAndExit = async () => {
    await shutdownOwnedProcesses();
    process.exit(0);
  };

  process.on("SIGINT", shutdownAndExit);
  process.on("SIGTERM", shutdownAndExit);
  process.on("exit", () => {
    if (ownedChild) {
      ownedChild.kill();
    }
    if (ownedUsageService) {
      ownedUsageService.server.close();
    }
  });
}

async function shutdownOwnedProcesses() {
  if (ownedChild) {
    ownedChild.kill();
    ownedChild = null;
  }
  if (ownedUsageService) {
    try {
      await ownedUsageService.close();
    } catch {
      // no-op
    }
    ownedUsageService = null;
  }
}

function waitForSignal() {
  return new Promise(() => {
    // Intentionally unresolved. The process exits via signal handlers.
  });
}
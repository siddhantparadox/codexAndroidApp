import { once } from "node:events";
import WebSocket, { WebSocketServer } from "ws";

import { CodexAppServerProcess, STARTUP_TIMEOUT_MS } from "./appServer.mjs";
import { createRolloutLiveMirrorController } from "./rolloutLiveMirror.mjs";

export const BRIDGE_STATUS_METHOD = "bridge/status";
export const DEFAULT_BRIDGE_HOST = "0.0.0.0";
export const DEFAULT_BRIDGE_PORT = 4500;
const BRIDGE_KIND = "codexremote_bridge";
const PROBE_TIMEOUT_MS = 1_500;

export class CodexRemoteBridgeServer {
  #host;
  #initializeResult;
  #pendingServerRequests = new Map();
  #port;
  #server = null;
  #transport;
  #versionName;
  #activeClient = null;
  #rolloutLiveMirror;
  #boundTransportMessage = (message) => {
    this.#handleTransportMessage(message);
  };

  constructor({
    transport,
    initializeResult,
    versionName,
    rolloutLiveMirrorFactory = createRolloutLiveMirrorController,
    host = DEFAULT_BRIDGE_HOST,
    port = DEFAULT_BRIDGE_PORT,
  }) {
    this.#transport = transport;
    this.#initializeResult = initializeResult;
    this.#versionName = versionName;
    this.#host = host;
    this.#port = port;
    this.#rolloutLiveMirror = rolloutLiveMirrorFactory({
      sendNotification: (message) => {
        if (!this.#activeClient?.codexremoteReady) {
          return;
        }
        this.#sendJson(this.#activeClient, message);
      },
    });
  }

  get port() {
    const address = this.#server?.address();
    return typeof address === "object" && address ? address.port : this.#port;
  }

  async start() {
    if (this.#server) {
      return;
    }

    const server = new WebSocketServer({
      host: this.#host,
      port: this.#port,
      perMessageDeflate: false,
    });
    server.on("connection", (socket) => {
      this.#handleConnection(socket);
    });

    this.#server = server;
    this.#transport.on("message", this.#boundTransportMessage);
    await once(server, "listening");
  }

  async close() {
    this.#transport.off("message", this.#boundTransportMessage);
    this.#rolloutLiveMirror?.close?.();

    if (this.#activeClient?.readyState === WebSocket.OPEN) {
      this.#activeClient.close(1_001, "Bridge shutting down");
    }
    this.#activeClient = null;

    const server = this.#server;
    this.#server = null;
    if (!server) {
      return;
    }

    await new Promise((resolve) => {
      server.close(() => resolve());
    });
  }

  #handleConnection(socket) {
    if (this.#activeClient && this.#activeClient !== socket && this.#activeClient.readyState === WebSocket.OPEN) {
      this.#activeClient.close(1_012, "Replaced by a newer Codex Mobile connection");
    }

    socket.codexremoteReady = false;
    this.#activeClient = socket;
    socket.on("message", (data) => {
      this.#handleClientMessage(socket, String(data));
    });
    socket.on("close", () => {
      if (this.#activeClient === socket) {
        this.#activeClient = null;
      }
    });
  }

  #handleClientMessage(socket, text) {
    let message;
    try {
      message = JSON.parse(text);
    } catch {
      socket.close(1_003, "Expected JSON-RPC payload");
      return;
    }

    if (message?.method === "initialize" && message.id != null) {
      this.#sendJson(socket, {
        id: message.id,
        result: this.#initializeResult,
      });
      return;
    }

    if (message?.method === "initialized" && message.id == null) {
      socket.codexremoteReady = true;
      this.#flushPendingServerRequests(socket);
      return;
    }

    if (message?.method === BRIDGE_STATUS_METHOD && message.id != null) {
      this.#sendJson(socket, {
        id: message.id,
        result: {
          kind: BRIDGE_KIND,
          version: this.#versionName,
          transport: "stdio",
          port: this.port,
        },
      });
      return;
    }

    this.#rolloutLiveMirror?.observeClientMessage?.(message);
    this.#transport.send(message);
  }

  #handleTransportMessage(message) {
    if (message?.method && message.id != null) {
      this.#pendingServerRequests.set(String(message.id), message);
    }

    if (message?.method === "serverRequest/resolved") {
      const requestId = message?.params?.requestId;
      if (requestId != null) {
        this.#pendingServerRequests.delete(String(requestId));
      }
    }

    if (!this.#activeClient?.codexremoteReady) {
      this.#rolloutLiveMirror?.transformTransportMessage?.(message);
      return;
    }

    const outgoingMessage = this.#rolloutLiveMirror?.transformTransportMessage?.(message) ?? message;
    this.#sendJson(this.#activeClient, outgoingMessage);
  }

  #flushPendingServerRequests(socket) {
    for (const message of this.#pendingServerRequests.values()) {
      this.#sendJson(socket, message);
    }
  }

  #sendJson(socket, message) {
    if (socket.readyState !== WebSocket.OPEN) {
      return;
    }

    socket.send(JSON.stringify(message));
  }
}

export async function startCodexRemoteBridge({
  versionName,
  host = DEFAULT_BRIDGE_HOST,
  port = DEFAULT_BRIDGE_PORT,
}) {
  const transport = new CodexAppServerProcess({ versionName });

  try {
    await withTimeout(
      transport.start(),
      STARTUP_TIMEOUT_MS,
      `Timed out starting codex app-server over stdio after ${STARTUP_TIMEOUT_MS}ms.`,
    );
    const initializeResult = await withTimeout(
      transport.initialize(),
      STARTUP_TIMEOUT_MS,
      `Timed out waiting for codex app-server initialize response after ${STARTUP_TIMEOUT_MS}ms.`,
    );

    const bridgeServer = new CodexRemoteBridgeServer({
      transport,
      initializeResult,
      versionName,
      host,
      port,
    });
    await bridgeServer.start();

    return {
      bridgeServer,
      transport,
      async close() {
        await bridgeServer.close();
        await transport.close();
      },
      waitForExit() {
        return transport.waitForExit();
      },
    };
  } catch (error) {
    await transport.close().catch(() => {});
    throw error;
  }
}

export function probeCodexRemoteBridge(versionName = "0.3.1", port = DEFAULT_BRIDGE_PORT) {
  return new Promise((resolve) => {
    let settled = false;
    let initializeComplete = false;
    const socket = new WebSocket(`ws://127.0.0.1:${port}`, {
      perMessageDeflate: false,
    });
    const timeout = setTimeout(() => finish(null), PROBE_TIMEOUT_MS);

    function finish(result) {
      if (settled) {
        return;
      }
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
          id: 1,
          method: "initialize",
          params: {
            clientInfo: {
              name: "codexremote_probe",
              title: "CodexRemote Probe",
              version: versionName,
            },
          },
        }),
      );
    });

    socket.on("message", (data) => {
      let message;
      try {
        message = JSON.parse(String(data));
      } catch {
        finish(null);
        return;
      }

      if (!initializeComplete && message?.id === 1) {
        initializeComplete = true;
        socket.send(
          JSON.stringify({
            method: "initialized",
            params: {},
          }),
        );
        socket.send(
          JSON.stringify({
            id: 2,
            method: BRIDGE_STATUS_METHOD,
            params: {},
          }),
        );
        return;
      }

      if (message?.id === 2) {
        if (message?.result?.kind === BRIDGE_KIND) {
          finish(message.result);
        } else {
          finish(null);
        }
      }
    });

    socket.on("error", () => finish(null));
    socket.on("close", () => finish(null));
  });
}

function withTimeout(promise, timeoutMs, message) {
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => {
      reject(new Error(message));
    }, timeoutMs);

    promise.then(
      (value) => {
        clearTimeout(timeout);
        resolve(value);
      },
      (error) => {
        clearTimeout(timeout);
        reject(error);
      },
    );
  });
}



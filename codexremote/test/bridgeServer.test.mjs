import assert from "node:assert/strict";
import { EventEmitter, once } from "node:events";
import test from "node:test";
import WebSocket from "ws";

import {
  CodexRemoteBridgeServer,
  probeCodexRemoteBridge,
} from "../lib/bridgeServer.mjs";

class FakeTransport extends EventEmitter {
  sent = [];

  send(message) {
    this.sent.push(message);
  }
}

test("bridge intercepts initialize and forwards later requests to the stdio transport", async (t) => {
  const transport = new FakeTransport();
  const bridge = new CodexRemoteBridgeServer({
    transport,
    initializeResult: {
      serverInfo: {
        name: "fake-codex",
      },
    },
    versionName: "0.3.1",
    host: "127.0.0.1",
    port: 0,
  });
  await bridge.start();
  t.after(async () => {
    await bridge.close();
  });

  const socket = await openSocket(bridge.port);
  t.after(async () => {
    await closeSocket(socket);
  });

  const initializeResponsePromise = waitForMessage(socket, (message) => message.id === 1);
  socket.send(JSON.stringify({
    id: 1,
    method: "initialize",
    params: {},
  }));
  const initializeResponse = await initializeResponsePromise;
  assert.equal(initializeResponse.result.serverInfo.name, "fake-codex");

  socket.send(JSON.stringify({
    method: "initialized",
    params: {},
  }));
  socket.send(JSON.stringify({
    id: 2,
    method: "account/read",
    params: {},
  }));

  await waitForCondition(() => transport.sent.length === 1);
  assert.deepEqual(
    transport.sent,
    [
      {
        id: 2,
        method: "account/read",
        params: {},
      },
    ],
  );

  const forwardedResponsePromise = waitForMessage(socket, (message) => message.id === 2);
  transport.emit("message", {
    id: 2,
    result: {
      ok: true,
    },
  });
  const forwardedResponse = await forwardedResponsePromise;
  assert.equal(forwardedResponse.result.ok, true);
});

test("bridge replays unresolved server requests after reconnect and drops them after resolution", async (t) => {
  const transport = new FakeTransport();
  const bridge = new CodexRemoteBridgeServer({
    transport,
    initializeResult: {
      serverInfo: {
        name: "fake-codex",
      },
    },
    versionName: "0.3.1",
    host: "127.0.0.1",
    port: 0,
  });
  await bridge.start();
  t.after(async () => {
    await bridge.close();
  });

  const firstSocket = await connectInitializedSocket(bridge.port);
  t.after(async () => {
    await closeSocket(firstSocket);
  });

  const firstRequestPromise = waitForMessage(firstSocket, (message) => message.id === "approval-1");
  transport.emit("message", {
    id: "approval-1",
    method: "item/commandExecution/requestApproval",
    params: {
      threadId: "thread-1",
    },
  });
  const firstRequest = await firstRequestPromise;
  assert.equal(firstRequest.method, "item/commandExecution/requestApproval");

  await closeSocket(firstSocket);

  const secondSocket = await connectInitializedSocket(bridge.port);
  t.after(async () => {
    await closeSocket(secondSocket);
  });
  const replayedRequest = await waitForMessage(secondSocket, (message) => message.id === "approval-1");
  assert.equal(replayedRequest.method, "item/commandExecution/requestApproval");

  transport.emit("message", {
    method: "serverRequest/resolved",
    params: {
      requestId: "approval-1",
    },
  });

  await closeSocket(secondSocket);

  const thirdSocket = await connectInitializedSocket(bridge.port);
  t.after(async () => {
    await closeSocket(thirdSocket);
  });
  const replayAfterResolution = await waitForMessage(thirdSocket, (message) => message.id === "approval-1", 150)
    .then(() => "replayed")
    .catch(() => "missing");
  assert.equal(replayAfterResolution, "missing");
});

test("probeCodexRemoteBridge identifies a healthy bridge", async (t) => {
  const transport = new FakeTransport();
  const bridge = new CodexRemoteBridgeServer({
    transport,
    initializeResult: {
      serverInfo: {
        name: "fake-codex",
      },
    },
    versionName: "0.3.1",
    host: "127.0.0.1",
    port: 0,
  });
  await bridge.start();
  t.after(async () => {
    await bridge.close();
  });

  const status = await probeCodexRemoteBridge("0.3.1", bridge.port);
  assert.equal(status.kind, "codexremote_bridge");
  assert.equal(status.transport, "stdio");
});

test("bridge transforms thread/read responses through the rollout mirror", async (t) => {
  const transport = new FakeTransport();
  const mirrorEvents = [];
  const bridge = new CodexRemoteBridgeServer({
    transport,
    initializeResult: {
      serverInfo: {
        name: "fake-codex",
      },
    },
    versionName: "0.3.1",
    host: "127.0.0.1",
    port: 0,
    rolloutLiveMirrorFactory: () => ({
      observeClientMessage(message) {
        mirrorEvents.push({ source: "client", message });
      },
      transformTransportMessage(message) {
        mirrorEvents.push({ source: "transport", message });
        return {
          ...message,
          result: {
            ...message.result,
            transformed: true,
          },
        };
      },
      close() {},
    }),
  });
  await bridge.start();
  t.after(async () => {
    await bridge.close();
  });

  const socket = await connectInitializedSocket(bridge.port);
  t.after(async () => {
    await closeSocket(socket);
  });

  socket.send(JSON.stringify({
    id: 4,
    method: "thread/read",
    params: {
      threadId: "thread-1",
    },
  }));

  await waitForCondition(() => transport.sent.length === 1);
  const forwardedResponsePromise = waitForMessage(socket, (message) => message.id === 4);
  transport.emit("message", {
    id: 4,
    result: {
      thread: {
        id: "thread-1",
      },
    },
  });

  const forwardedResponse = await forwardedResponsePromise;
  assert.equal(forwardedResponse.result.transformed, true);
  await waitForCondition(() => mirrorEvents.length >= 2);
  assert.equal(mirrorEvents[0].source, "client");
  assert.equal(mirrorEvents[0].message.method, "thread/read");
  assert.equal(mirrorEvents[1].source, "transport");
  assert.equal(mirrorEvents[1].message.id, 4);
});

async function connectInitializedSocket(port) {
  const socket = await openSocket(port);

  const initializeResponsePromise = waitForMessage(socket, (message) => message.id === 1);
  socket.send(JSON.stringify({
    id: 1,
    method: "initialize",
    params: {},
  }));
  await initializeResponsePromise;

  socket.send(JSON.stringify({
    method: "initialized",
    params: {},
  }));

  return socket;
}

function openSocket(port) {
  return new Promise((resolve, reject) => {
    const socket = new WebSocket(`ws://127.0.0.1:${port}`, {
      perMessageDeflate: false,
    });

    const timeout = setTimeout(() => {
      cleanup();
      reject(new Error("Timed out opening websocket."));
    }, 1_000);

    function cleanup() {
      clearTimeout(timeout);
      socket.off("open", handleOpen);
      socket.off("error", handleError);
    }

    function handleOpen() {
      cleanup();
      resolve(socket);
    }

    function handleError(error) {
      cleanup();
      reject(error);
    }

    socket.on("open", handleOpen);
    socket.on("error", handleError);
  });
}

function closeSocket(socket) {
  if (socket.readyState === WebSocket.CLOSED) {
    return Promise.resolve();
  }

  return new Promise((resolve) => {
    socket.once("close", () => resolve());
    socket.close();
  });
}

function waitForCondition(predicate, timeoutMs = 1_000) {
  return new Promise((resolve, reject) => {
    const startedAt = Date.now();

    function poll() {
      if (predicate()) {
        resolve();
        return;
      }
      if (Date.now() - startedAt >= timeoutMs) {
        reject(new Error("Timed out waiting for condition."));
        return;
      }
      setTimeout(poll, 10);
    }

    poll();
  });
}

function waitForMessage(socket, predicate, timeoutMs = 1_000) {
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => {
      cleanup();
      reject(new Error("Timed out waiting for websocket message."));
    }, timeoutMs);

    function cleanup() {
      clearTimeout(timeout);
      socket.off("message", handleMessage);
      socket.off("close", handleClose);
      socket.off("error", handleError);
    }

    function handleMessage(data) {
      const message = JSON.parse(String(data));
      if (!predicate(message)) {
        return;
      }
      cleanup();
      resolve(message);
    }

    function handleClose() {
      cleanup();
      reject(new Error("Socket closed before expected message."));
    }

    function handleError(error) {
      cleanup();
      reject(error);
    }

    socket.on("message", handleMessage);
    socket.on("close", handleClose);
    socket.on("error", handleError);
  });
}



import http from "node:http";

import { defaultSessionRoot, summarizeUsageWrapped } from "./usageWrappedAggregator.mjs";

export const DEFAULT_USAGE_WRAPPED_PORT = 4501;
const PROBE_TIMEOUT_MS = 1_500;

export async function startUsageWrappedService({
  host = "0.0.0.0",
  port = DEFAULT_USAGE_WRAPPED_PORT,
  sessionRoot = defaultSessionRoot(),
} = {}) {
  const server = http.createServer(async (request, response) => {
    const url = new URL(request.url ?? "/", `http://${request.headers.host ?? "localhost"}`);

    if (url.pathname === "/healthz") {
      return respondJson(response, 200, { ok: true });
    }

    if (url.pathname !== "/usage-wrapped") {
      return respondJson(response, 404, { error: "Not found" });
    }

    if (request.method !== "GET") {
      return respondJson(response, 405, { error: "Method not allowed" });
    }

    try {
      const summary = await summarizeUsageWrapped({ sessionRoot });
      return respondJson(response, 200, summary);
    } catch (error) {
      return respondJson(response, 500, {
        error: error instanceof Error && error.message
          ? error.message
          : "Unable to build usage summary.",
      });
    }
  });

  await listen(server, port, host);

  return {
    host,
    port,
    sessionRoot,
    server,
    async close() {
      await new Promise((resolve, reject) => {
        server.close((error) => {
          if (error) {
            reject(error);
          } else {
            resolve();
          }
        });
      });
    },
  };
}

export function probeUsageWrappedService(port = DEFAULT_USAGE_WRAPPED_PORT) {
  return new Promise((resolve) => {
    const request = http.get(
      {
        host: "127.0.0.1",
        port,
        path: "/healthz",
        timeout: PROBE_TIMEOUT_MS,
      },
      (response) => {
        let body = "";
        response.setEncoding("utf8");
        response.on("data", (chunk) => {
          body += chunk;
        });
        response.on("end", () => {
          if (response.statusCode !== 200) {
            resolve(false);
            return;
          }
          try {
            const parsed = JSON.parse(body);
            resolve(parsed?.ok === true);
          } catch {
            resolve(false);
          }
        });
      },
    );

    request.on("timeout", () => {
      request.destroy();
      resolve(false);
    });
    request.on("error", () => {
      resolve(false);
    });
  });
}

function listen(server, port, host) {
  return new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(port, host, () => {
      server.removeListener("error", reject);
      resolve();
    });
  });
}

function respondJson(response, statusCode, body) {
  const payload = JSON.stringify(body, null, 2);
  response.writeHead(statusCode, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": Buffer.byteLength(payload),
  });
  response.end(payload);
}

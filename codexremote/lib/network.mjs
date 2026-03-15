import net from "node:net";
import os from "node:os";

export const CONNECT_TIMEOUT_MS = 750;

export function pickLanIpv4Address() {
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

export function isTcpPortOccupied(port) {
  return new Promise((resolve) => {
    const socket = new net.Socket();
    const onFailure = () => {
      socket.destroy();
      resolve(false);
    };

    socket.setTimeout(CONNECT_TIMEOUT_MS);
    socket.once("connect", () => {
      socket.destroy();
      resolve(true);
    });
    socket.once("timeout", onFailure);
    socket.once("error", onFailure);
    socket.connect(port, "127.0.0.1");
  });
}

export function sleep(ms) {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}

function isPrivateIpv4Address(value) {
  return (
    value.startsWith("10.") ||
    value.startsWith("192.168.") ||
    /^172\.(1[6-9]|2\d|3[0-1])\./.test(value)
  );
}

import ws from "k6/ws";
import { check } from "k6";
import { sleep } from "k6";
import { Counter, Trend } from "k6/metrics";

const targetConnections = Number.parseInt(
  __ENV.K6_TARGET_CONNECTIONS || "500",
  10,
);
const holdDurationMs = Number.parseInt(
  __ENV.K6_HOLD_DURATION_MS || "30000",
  10,
);
const connectionRampMs = Number.parseInt(
  __ENV.K6_CONNECTION_RAMP_MS || "10000",
  10,
);
const openedConnections = new Counter("opened_connections");
const connectionSetupLatency = new Trend("connection_setup_latency", true);

export const options = {
  scenarios: {
    connection_capacity: {
      executor: "shared-iterations",
      vus: targetConnections,
      iterations: targetConnections,
      maxDuration: `${Math.ceil((connectionRampMs + holdDurationMs) / 1000) + 30}s`,
    },
  },
  thresholds: {
    checks: ["rate>0.99"],
    opened_connections: [`count>=${targetConnections}`],
    connection_setup_latency: ["p(95)<2000"],
  },
};

export default function () {
  const token = __ENV.AUTH_TOKEN;
  const gateway = __ENV.WS_URL || "ws://localhost:8083/ws/chat";
  if (!token) throw new Error("AUTH_TOKEN is required");

  sleep(((__VU - 1) / targetConnections) * (connectionRampMs / 1000));
  const startedAt = Date.now();
  const response = ws.connect(
    `${gateway}?token=${encodeURIComponent(token)}`,
    {},
    (socket) => {
      socket.on("open", () => {
        openedConnections.add(1);
        connectionSetupLatency.add(Date.now() - startedAt);
        socket.setTimeout(() => socket.close(), holdDurationMs);
      });
    },
  );

  check(response, {
    "websocket upgraded": (result) => result?.status === 101,
  });
}

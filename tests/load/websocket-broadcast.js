import ws from "k6/ws";
import { check, fail, sleep } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";

const connections = Number.parseInt(__ENV.K6_TARGET_CONNECTIONS || "1000", 10);
const producers = Number.parseInt(__ENV.K6_PRODUCERS || "2", 10);
const messagesPerSecond = Number.parseInt(
  __ENV.K6_MESSAGES_PER_SECOND || "10",
  10,
);
const rampMs = Number.parseInt(__ENV.K6_CONNECTION_RAMP_MS || "40000", 10);
const warmupMs = Number.parseInt(__ENV.K6_WARMUP_MS || "3000", 10);
const sustainMs = Number.parseInt(__ENV.K6_SUSTAIN_MS || "30000", 10);
const drainMs = Number.parseInt(__ENV.K6_DRAIN_MS || "5000", 10);
const messagesPerProducer = Math.floor(
  (sustainMs * messagesPerSecond) / (1000 * producers),
);
const expectedPerConnection = messagesPerProducer * producers;

const openedConnections = new Counter("opened_connections");
const publishedBroadcasts = new Counter("published_broadcasts");
const broadcastDeliveries = new Counter("broadcast_deliveries");
const deliverySuccess = new Rate("delivery_success");
const deliveryLatency = new Trend("delivery_latency", true);

export const options = {
  scenarios: {
    redis_websocket_broadcast: {
      executor: "shared-iterations",
      vus: connections,
      iterations: connections,
      maxDuration: `${Math.ceil((rampMs + warmupMs + sustainMs + drainMs) / 1000) + 30}s`,
    },
  },
  thresholds: {
    checks: ["rate>0.99"],
    opened_connections: [`count>=${connections}`],
    delivery_success: ["rate>0.99"],
    delivery_latency: ["p(95)<1500"],
  },
};

function randomUuid() {
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(
    /[xy]/g,
    (character) => {
      const value = Math.floor(Math.random() * 16);
      return (character === "x" ? value : (value & 0x3) | 0x8).toString(16);
    },
  );
}

export default function () {
  const token = __ENV.AUTH_TOKEN;
  const gateway = __ENV.WS_URL || "ws://localhost:8083/ws/chat";
  if (!token) fail("AUTH_TOKEN is required");

  const connectionDelayMs = ((__VU - 1) / connections) * rampMs;
  sleep(connectionDelayMs / 1000);
  let deliveries = 0;

  const response = ws.connect(
    `${gateway}?token=${encodeURIComponent(token)}`,
    {},
    (socket) => {
      socket.on("open", () => {
        openedConnections.add(1);
        const publishDelayMs = rampMs - connectionDelayMs + warmupMs;
        if (__VU <= producers) {
          socket.setTimeout(() => {
            let published = 0;
            const intervalMs = (1000 * producers) / messagesPerSecond;
            socket.setInterval(() => {
              if (published >= messagesPerProducer) return;
              const broadcastId = randomUuid();
              const sentAt = Date.now();
              published += 1;
              publishedBroadcasts.add(1);
              socket.send(
                JSON.stringify({
                  commandId: `broadcast-${broadcastId}`,
                  commandType: "BROADCAST_TEST",
                  payload: { broadcastId, sentAt },
                }),
              );
            }, intervalMs);
          }, publishDelayMs);
        }
        socket.setTimeout(
          () => socket.close(),
          rampMs - connectionDelayMs + warmupMs + sustainMs + drainMs,
        );
      });

      socket.on("message", (raw) => {
        const event = JSON.parse(raw);
        if (event.eventType !== "BROADCAST_TEST") return;
        const sentAt = event.payload?.payload?.sentAt;
        if (!Number.isFinite(sentAt)) return;
        deliveries += 1;
        broadcastDeliveries.add(1);
        deliveryLatency.add(Date.now() - sentAt);
      });
    },
  );

  const received = Math.min(deliveries, expectedPerConnection);
  for (let index = 0; index < received; index += 1) deliverySuccess.add(true);
  for (let index = received; index < expectedPerConnection; index += 1)
    deliverySuccess.add(false);

  check(response, {
    "websocket upgraded": (result) => result?.status === 101,
  });
}

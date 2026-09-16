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

const openedConnections = new Counter("opened_connections");
const publishedMessages = new Counter("published_messages");
const acknowledgedMessages = new Counter("acknowledged_messages");
const acknowledgementSuccess = new Rate("acknowledgement_success");
const acknowledgementLatency = new Trend("acknowledgement_latency", true);
const fanoutDeliveries = new Counter("fanout_deliveries");
const fanoutLatency = new Trend("fanout_latency", true);

export const options = {
  scenarios: {
    room_fanout: {
      executor: "shared-iterations",
      vus: connections,
      iterations: connections,
      maxDuration: `${Math.ceil((rampMs + warmupMs + sustainMs) / 1000) + 30}s`,
    },
  },
  thresholds: {
    checks: ["rate>0.99"],
    opened_connections: [`count>=${connections}`],
    acknowledgement_success: ["rate>0.99"],
    acknowledgement_latency: ["p(95)<1500"],
    fanout_deliveries: ["count>0"],
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
  const conversationId = __ENV.CONVERSATION_ID;
  const gateway = __ENV.WS_URL || "ws://localhost:8083/ws/chat";
  if (!token || !conversationId)
    fail("AUTH_TOKEN and CONVERSATION_ID are required");

  const connectionDelayMs = ((__VU - 1) / connections) * rampMs;
  sleep(connectionDelayMs / 1000);

  const pending = {};
  let sent = 0;
  let acknowledged = 0;
  let debugMessageLogged = false;
  const commandId = (kind) => `${kind}-${__VU}-${Date.now()}-${randomUuid()}`;
  const response = ws.connect(
    `${gateway}?token=${encodeURIComponent(token)}`,
    {},
    (socket) => {
      socket.on("open", () => {
        openedConnections.add(1);
        socket.send(
          JSON.stringify({
            commandId: commandId("subscribe"),
            commandType: "SUBSCRIBE_CONVERSATION",
            conversationId,
            payload: {},
          }),
        );

        const publishDelayMs = rampMs - connectionDelayMs + warmupMs;
        if (__VU <= producers) {
          socket.setTimeout(() => {
            const intervalMs = Math.max(
              1,
              (1000 * producers) / messagesPerSecond,
            );
            socket.setInterval(() => {
              const clientMessageId = randomUuid();
              const sentAt = Date.now();
              pending[clientMessageId] = sentAt;
              sent += 1;
              publishedMessages.add(1);
              socket.send(
                JSON.stringify({
                  commandId: commandId("message"),
                  commandType: "SEND_MESSAGE",
                  conversationId,
                  payload: {
                    clientMessageId,
                    content: `k6-fanout:${clientMessageId}:${sentAt}`,
                  },
                }),
              );
            }, intervalMs);
          }, publishDelayMs);
        }

        socket.setTimeout(
          () => socket.close(),
          rampMs - connectionDelayMs + warmupMs + sustainMs,
        );
      });

      socket.on("message", (raw) => {
        const event = JSON.parse(raw);
        const message = event.payload?.message;
        if (
          __ENV.K6_DEBUG === "true" &&
          __VU === 1 &&
          event.eventType === "MESSAGE_CREATED" &&
          !debugMessageLogged
        ) {
          console.log(raw);
          debugMessageLogged = true;
        }
        if (
          event.eventType !== "MESSAGE_CREATED" ||
          !message?.content?.startsWith("k6-fanout:")
        )
          return;

        const sentAt = Number.parseInt(message.content.split(":").at(-1), 10);
        fanoutDeliveries.add(1);
        fanoutLatency.add(Date.now() - sentAt);

        if (pending[message.clientMessageId] !== undefined) {
          acknowledgementLatency.add(
            Date.now() - pending[message.clientMessageId],
          );
          delete pending[message.clientMessageId];
          acknowledged += 1;
          acknowledgedMessages.add(1);
          acknowledgementSuccess.add(true);
        }
      });
    },
  );

  for (let missing = acknowledged; missing < sent; missing += 1) {
    acknowledgementSuccess.add(false);
  }
  check(response, {
    "websocket upgraded": (result) => result?.status === 101,
  });
}

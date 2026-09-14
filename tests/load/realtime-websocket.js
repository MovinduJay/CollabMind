import ws from "k6/ws";
import { check } from "k6";
import { Counter, Trend } from "k6/metrics";

const acknowledgements = new Counter("message_acknowledgements");
const acknowledgementLatency = new Trend(
  "message_acknowledgement_latency",
  true,
);

const smoke = __ENV.K6_SMOKE === "true";

export const options = smoke
  ? {
      scenarios: {
        room_chat: { executor: "constant-vus", vus: 2, duration: "10s" },
      },
      thresholds: {
        checks: ["rate>0.99"],
        message_acknowledgements: ["count>0"],
        message_acknowledgement_latency: ["p(95)<1500"],
      },
    }
  : {
      scenarios: {
        room_chat: {
          executor: "ramping-vus",
          startVUs: 0,
          stages: [
            { duration: "20s", target: 10 },
            { duration: "40s", target: 10 },
            { duration: "20s", target: 0 },
          ],
        },
      },
      thresholds: {
        checks: ["rate>0.99"],
        message_acknowledgements: ["count>0"],
        message_acknowledgement_latency: ["p(95)<1500"],
      },
    };

export default function () {
  const token = __ENV.AUTH_TOKEN;
  const conversationId = __ENV.CONVERSATION_ID;
  const gateway = __ENV.WS_URL || "ws://localhost:8083/ws/chat";
  if (!token || !conversationId)
    throw new Error("AUTH_TOKEN and CONVERSATION_ID are required");

  const started = Date.now();
  const response = ws.connect(
    `${gateway}?token=${encodeURIComponent(token)}`,
    {},
    (socket) => {
      socket.on("open", () => {
        socket.send(
          JSON.stringify({ type: "SUBSCRIBE", payload: { conversationId } }),
        );
        socket.send(
          JSON.stringify({
            type: "SEND_MESSAGE",
            payload: {
              conversationId,
              content: `k6 message ${__VU}-${__ITER}`,
            },
          }),
        );
      });
      socket.on("message", (raw) => {
        const event = JSON.parse(raw);
        if (event.type === "MESSAGE_CREATED") {
          acknowledgements.add(1);
          acknowledgementLatency.add(Date.now() - started);
          socket.close();
        }
      });
      socket.setTimeout(() => socket.close(), 10_000);
    },
  );

  check(response, {
    "websocket upgraded": (result) => result && result.status === 101,
  });
}

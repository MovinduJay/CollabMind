const token = process.env.WS_TOKEN;
const conversationId = process.env.WS_CONVERSATION_ID;

if (!token || !conversationId) {
  throw new Error("WS_TOKEN and WS_CONVERSATION_ID are required");
}

const socket = new WebSocket(
  `ws://localhost:8083/ws/chat?token=${encodeURIComponent(token)}`,
);
const timeout = setTimeout(() => {
  socket.close();
  throw new Error("Timed out waiting for MESSAGE_CREATED");
}, 15_000);

socket.addEventListener("open", () => {
  socket.send(
    JSON.stringify({
      commandId: crypto.randomUUID(),
      commandType: "SUBSCRIBE_CONVERSATION",
      conversationId,
      payload: {},
    }),
  );
  setTimeout(() => {
    socket.send(
      JSON.stringify({
        commandId: crypto.randomUUID(),
        commandType: "SEND_MESSAGE",
        conversationId,
        payload: {
          clientMessageId: crypto.randomUUID(),
          content: "CI WebSocket smoke message",
        },
      }),
    );
  }, 250);
});

socket.addEventListener("message", ({ data }) => {
  const event = JSON.parse(String(data));
  if (event.eventType === "MESSAGE_CREATED") {
    clearTimeout(timeout);
    socket.close();
    console.log("Realtime MESSAGE_CREATED received");
  }
});

socket.addEventListener("error", () => {
  clearTimeout(timeout);
  throw new Error("WebSocket connection failed");
});

export const config = {
  identityApi: import.meta.env.VITE_IDENTITY_API ?? "http://localhost:8082",
  chatApi: import.meta.env.VITE_CHAT_API ?? "http://localhost:8081",
  realtimeWs: import.meta.env.VITE_REALTIME_WS ?? "ws://localhost:8083/ws/chat",
  aiApi: import.meta.env.VITE_AI_API ?? "http://localhost:8084",
  mcpApi: import.meta.env.VITE_MCP_API ?? "http://localhost:8085"
};

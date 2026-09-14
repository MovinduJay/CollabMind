const local = window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1";
const httpOrigin = window.location.origin;
const wsOrigin = `${window.location.protocol === "https:" ? "wss:" : "ws:"}//${window.location.host}`;

export const config = {
  identityApi: import.meta.env.VITE_IDENTITY_API ?? (local ? "http://localhost:8082" : httpOrigin),
  chatApi: import.meta.env.VITE_CHAT_API ?? (local ? "http://localhost:8081" : httpOrigin),
  realtimeWs: import.meta.env.VITE_REALTIME_WS ?? (local ? "ws://localhost:8083/ws/chat" : `${wsOrigin}/ws/chat`),
  aiApi: import.meta.env.VITE_AI_API ?? (local ? "http://localhost:8084" : httpOrigin),
  mcpApi: import.meta.env.VITE_MCP_API ?? (local ? "http://localhost:8085" : httpOrigin)
};

import { useCallback, useRef, useState } from "react";
import { config } from "../config";
import type { ChatMessage, ServerEvent } from "../types";

type ConnectionStatus = "DISCONNECTED" | "CONNECTING" | "CONNECTED";

function id() {
  return crypto.randomUUID();
}

export function useRealtimeRoom() {
  const socketRef = useRef<WebSocket | null>(null);
  const activeConversationRef = useRef<string>("");

  const [status, setStatus] = useState<ConnectionStatus>("DISCONNECTED");
  const [events, setEvents] = useState<ServerEvent[]>([]);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [aiStages, setAiStages] = useState<string[]>([]);
  const [lastError, setLastError] = useState("");

  const appendMessage = useCallback((message: ChatMessage) => {
    setMessages((current) => {
      if (current.some((item) => item.id === message.id)) {
        return current;
      }

      return [...current, message].sort(
        (first, second) => first.sequenceNumber - second.sequenceNumber
      );
    });
  }, []);

  const sendCommand = useCallback((command: object) => {
    const socket = socketRef.current;

    if (!socket || socket.readyState !== WebSocket.OPEN) {
      setLastError("Realtime connection is not ready yet.");
      return false;
    }

    socket.send(JSON.stringify(command));
    return true;
  }, []);

  const subscribe = useCallback((conversationId: string) => {
    activeConversationRef.current = conversationId;

    return sendCommand({
      commandId: id(),
      commandType: "SUBSCRIBE_CONVERSATION",
      conversationId,
      payload: {}
    });
  }, [sendCommand]);

  const connect = useCallback((token: string, conversationId?: string) => {
    socketRef.current?.close();
    setLastError("");
    setStatus("CONNECTING");

    if (conversationId) {
      activeConversationRef.current = conversationId;
    }

    const socket = new WebSocket(`${config.realtimeWs}?token=${encodeURIComponent(token)}`);
    socketRef.current = socket;

    socket.onopen = () => {
      setStatus("CONNECTED");

      const roomToSubscribe = activeConversationRef.current;

      if (roomToSubscribe) {
        socket.send(
          JSON.stringify({
            commandId: id(),
            commandType: "SUBSCRIBE_CONVERSATION",
            conversationId: roomToSubscribe,
            payload: {}
          })
        );
      }
    };

    socket.onclose = () => {
      setStatus("DISCONNECTED");
    };

    socket.onerror = () => {
      setStatus("DISCONNECTED");
      setLastError("Realtime connection failed.");
    };

    socket.onmessage = (rawMessage) => {
      const event = JSON.parse(rawMessage.data) as ServerEvent;

      setEvents((current) => [event, ...current].slice(0, 80));

      if (event.eventType === "SUBSCRIPTION_REJECTED") {
        setLastError(event.payload?.reason ?? "Could not subscribe to this room.");
      }

      if (event.eventType === "MESSAGE_CREATED" || event.eventType === "AI_MESSAGE_CREATED") {
        const message = event.payload?.message as ChatMessage | undefined;

        if (message) {
          appendMessage(message);
        }
      }

      if (event.eventType === "AI_STAGE_UPDATED") {
        const stage = [
          event.payload?.agentType ?? "AI",
          event.payload?.stage ?? "UNKNOWN",
          event.payload?.detail ?? ""
        ].join(" · ");

        setAiStages((current) => [stage, ...current].slice(0, 20));
      }

      if (event.eventType === "AI_RESPONSE_FAILED") {
        setAiStages((current) => [
          `FAILED · ${event.payload?.reason ?? "AI response failed"}`,
          ...current
        ].slice(0, 20));
      }
    };
  }, [appendMessage]);

  const disconnect = useCallback(() => {
    socketRef.current?.close();
    socketRef.current = null;
    setStatus("DISCONNECTED");
  }, []);

  const sendMessage = useCallback((conversationId: string, content: string) => {
    return sendCommand({
      commandId: id(),
      commandType: "SEND_MESSAGE",
      conversationId,
      payload: {
        clientMessageId: id(),
        content
      }
    });
  }, [sendCommand]);

  const replaceMessages = useCallback((nextMessages: ChatMessage[]) => {
    setMessages(
      [...nextMessages].sort(
        (first, second) => first.sequenceNumber - second.sequenceNumber
      )
    );
  }, []);

  const clear = useCallback(() => {
    setEvents([]);
    setMessages([]);
    setAiStages([]);
    setLastError("");
  }, []);

  return {
    status,
    events,
    messages,
    aiStages,
    lastError,
    connect,
    disconnect,
    subscribe,
    sendMessage,
    replaceMessages,
    clear
  };
}

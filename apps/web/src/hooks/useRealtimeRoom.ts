import { useCallback, useRef, useState } from "react";
import { config } from "../config";
import type { ChatMessage, ServerEvent } from "../types";

type ConnectionStatus = "DISCONNECTED" | "CONNECTING" | "CONNECTED";

function id() {
  return crypto.randomUUID();
}

export function useRealtimeRoom() {
  const socketRef = useRef<WebSocket | null>(null);

  const [status, setStatus] = useState<ConnectionStatus>("DISCONNECTED");
  const [events, setEvents] = useState<ServerEvent[]>([]);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [aiStages, setAiStages] = useState<string[]>([]);

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

  const connect = useCallback((token: string) => {
    socketRef.current?.close();

    setStatus("CONNECTING");

    const socket = new WebSocket(`${config.realtimeWs}?token=${encodeURIComponent(token)}`);
    socketRef.current = socket;

    socket.onopen = () => {
      setStatus("CONNECTED");
    };

    socket.onclose = () => {
      setStatus("DISCONNECTED");
    };

    socket.onerror = () => {
      setStatus("DISCONNECTED");
    };

    socket.onmessage = (rawMessage) => {
      const event = JSON.parse(rawMessage.data) as ServerEvent;

      setEvents((current) => [event, ...current].slice(0, 80));

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

  const subscribe = useCallback((conversationId: string) => {
    socketRef.current?.send(
      JSON.stringify({
        commandId: id(),
        commandType: "SUBSCRIBE_CONVERSATION",
        conversationId,
        payload: {}
      })
    );
  }, []);

  const sendMessage = useCallback((conversationId: string, content: string) => {
    socketRef.current?.send(
      JSON.stringify({
        commandId: id(),
        commandType: "SEND_MESSAGE",
        conversationId,
        payload: {
          clientMessageId: id(),
          content
        }
      })
    );
  }, []);

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
  }, []);

  return {
    status,
    events,
    messages,
    aiStages,
    connect,
    disconnect,
    subscribe,
    sendMessage,
    replaceMessages,
    clear
  };
}

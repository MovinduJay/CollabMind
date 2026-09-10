import React, { useEffect, useMemo, useState } from "react";
import { Bot, Copy, Link2, MessageSquare, Plus, Send, Sparkles, Users } from "lucide-react";
import { useNavigate, useParams } from "react-router-dom";
import { api } from "../../api/client";
import { Panel } from "../../components/Panel";
import { useRealtimeRoom } from "../../hooks/useRealtimeRoom";
import { clearSession, loadSession, saveSession } from "../auth/session";
import type { AuthSession } from "../../types";

const quickPrompts = [
  "@planner give me a clean 3-step plan for building this SaaS MVP",
  "@critic what are the biggest risks in this product idea?",
  "@summarizer summarize the key decisions from this room",
  "@shopping find me a birthday gift under Rs. 10,000",
  "@github summarize octocat/Hello-World",
  "@github search issues in spring-projects/spring-petclinic about docker"
];

function guestEmail() {
  return `guest+${crypto.randomUUID()}@collabmind.local`;
}

function extractConversationId(input: string) {
  const value = input.trim();

  if (!value) {
    return "";
  }

  try {
    const url = new URL(value);
    const parts = url.pathname.split("/").filter(Boolean);
    const roomIndex = parts.indexOf("r");

    if (roomIndex >= 0 && parts[roomIndex + 1]) {
      return parts[roomIndex + 1];
    }

    return parts[parts.length - 1] ?? "";
  } catch {
    return value
      .replace(/^.*\/r\//, "")
      .replace(/[?#].*$/, "")
      .trim();
  }
}

export function WorkspacePage() {
  const navigate = useNavigate();
  const params = useParams();
  const realtime = useRealtimeRoom();

  const [session, setSession] = useState<AuthSession | null>(() => loadSession());
  const [displayName, setDisplayName] = useState("");
  const [roomName, setRoomName] = useState("Untitled room");
  const [roomLinkInput, setRoomLinkInput] = useState("");
  const [activeConversationId, setActiveConversationId] = useState(params.conversationId ?? "");
  const [messageInput, setMessageInput] = useState("");
  const [error, setError] = useState("");

  const shareUrl = useMemo(() => {
    if (!activeConversationId) return "";
    return `${window.location.origin}/r/${activeConversationId}`;
  }, [activeConversationId]);

  useEffect(() => {
    if (params.conversationId) {
      setActiveConversationId(params.conversationId);
    }
  }, [params.conversationId]);

  async function ensureGuestSession(): Promise<AuthSession> {
    if (session) {
      return session;
    }

    if (!displayName.trim()) {
      throw new Error("Enter your name first.");
    }

    const nextSession = await api.register({
      displayName: displayName.trim(),
      email: guestEmail(),
      password: crypto.randomUUID()
    });

    saveSession(nextSession);
    setSession(nextSession);

    return nextSession;
  }

  function openSharedRoom() {
    setError("");

    const conversationId = extractConversationId(roomLinkInput);

    if (!conversationId) {
      setError("Paste a room link or room ID.");
      return;
    }

    setActiveConversationId(conversationId);
    navigate(`/r/${conversationId}`);
  }

  async function createRoom() {
    setError("");

    try {
      const guest = await ensureGuestSession();

      const conversation = await api.createConversation(guest.accessToken, {
        name: roomName.trim() || "Untitled room"
      });

      setActiveConversationId(conversation.id);
      navigate(`/r/${conversation.id}`);
      realtime.clear();
      realtime.connect(guest.accessToken, conversation.id);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Could not create room.");
    }
  }

  async function enterRoom() {
    setError("");

    try {
      const guest = await ensureGuestSession();

      if (!activeConversationId) {
        throw new Error("Room link is missing.");
      }

      await api.joinConversation(guest.accessToken, activeConversationId);

      const history = await api.latestMessages(guest.accessToken, activeConversationId);
      realtime.replaceMessages(history);

      realtime.connect(guest.accessToken, activeConversationId);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Could not enter room.");
    }
  }

  function sendMessage() {
    if (!messageInput.trim() || !activeConversationId) {
      return;
    }

    const sent = realtime.sendMessage(activeConversationId, messageInput.trim());

    if (!sent) {
      setError("Realtime connection is not ready. Click Enter room again.");
      return;
    }

    setMessageInput("");
  }

  async function copyLink() {
    if (!shareUrl) return;
    await navigator.clipboard.writeText(shareUrl);
  }

  function leaveRoom() {
    realtime.disconnect();
    realtime.clear();
    clearSession();
    setSession(null);
    setActiveConversationId("");
    setDisplayName("");
    navigate("/");
  }

  const hasRoom = Boolean(activeConversationId);
  const isInRoom = Boolean(session && hasRoom && realtime.status === "CONNECTED");

  if (!session && !hasRoom) {
    return (
      <main className="landing-shell">
        <section className="landing-hero">
          <div className="brand-pill">
            <Sparkles size={18} />
            CollabMind
          </div>

          <h1>Create a temporary AI room.</h1>
          <p>
            Start a room, share the link, and chat with people plus AI agents.
            Rooms are designed to be temporary and inactivity-based.
          </p>

          <div className="create-card">
            <label>
              Your name
              <input
                value={displayName}
                onChange={(event) => setDisplayName(event.target.value)}
                placeholder="Movindu"
              />
            </label>

            <label>
              Room name
              <input
                value={roomName}
                onChange={(event) => setRoomName(event.target.value)}
                placeholder="Project discussion"
              />
            </label>

            <button onClick={createRoom}>
              <Plus size={18} />
              Create room
            </button>

            <div className="room-divider">
              <span />
              <strong>or join a room</strong>
              <span />
            </div>

            <label>
              Room link or room ID
              <input
                value={roomLinkInput}
                onChange={(event) => setRoomLinkInput(event.target.value)}
                placeholder="Paste a shared room link"
                onKeyDown={(event) => {
                  if (event.key === "Enter") {
                    openSharedRoom();
                  }
                }}
              />
            </label>

            <button className="secondary-button" onClick={openSharedRoom}>
              <Link2 size={18} />
              Open room
            </button>

            {error ? <div className="error-box">{error}</div> : null}
          </div>
        </section>
      </main>
    );
  }

  if (!session && hasRoom) {
    return (
      <main className="landing-shell">
        <section className="join-card">
          <div className="brand-pill">
            <Users size={18} />
            Shared room
          </div>

          <h1>Enter the room</h1>
          <p>Add your name to join this temporary conversation.</p>

          <label>
            Your name
            <input
              value={displayName}
              onChange={(event) => setDisplayName(event.target.value)}
              placeholder="Your name"
            />
          </label>

          {error ? <div className="error-box">{error}</div> : null}

          <button onClick={enterRoom}>
            <MessageSquare size={18} />
            Join room
          </button>
        </section>
      </main>
    );
  }

  return (
    <main className="room-shell">
      <header className="room-header">
        <div>
          <div className="brand-pill">
            <Sparkles size={16} />
            CollabMind Room
          </div>
          <h1>{roomName || "Temporary room"}</h1>
          <p>Temporary room · Auto-delete after inactivity will be handled by backend TTL.</p>
        </div>

        <div className="room-actions">
          <button className="secondary-button" onClick={copyLink} disabled={!shareUrl}>
            <Copy size={16} />
            Copy link
          </button>
          <button className="secondary-button danger" onClick={leaveRoom}>
            Leave
          </button>
        </div>
      </header>

      <section className="share-bar">
        <Link2 size={16} />
        <span>{shareUrl}</span>
      </section>

      <section className="room-grid">
        <Panel title="Conversation" description="Message the room or mention an AI agent.">
          {error ? <div className="error-box">{error}</div> : null}
          {realtime.lastError ? <div className="error-box">{realtime.lastError}</div> : null}

          <div className="prompt-row">
            {quickPrompts.map((prompt) => (
              <button
                key={prompt}
                className="prompt-chip"
                onClick={() => setMessageInput(prompt)}
              >
                <Bot size={14} />
                {prompt}
              </button>
            ))}
          </div>

          <div className="message-list">
            {realtime.messages.length === 0 ? (
              <div className="empty-state">
                <MessageSquare size={34} />
                <strong>No messages yet</strong>
                <span>Start the conversation or mention @planner, @shopping, or @github.</span>
              </div>
            ) : (
              realtime.messages.map((message) => (
                <article key={message.id} className={`chat-message ${message.messageType.toLowerCase()}`}>
                  <div className="message-header">
                    <strong>
                      {message.messageType === "AI"
                        ? `${message.agentType ?? "AI"} Agent`
                        : message.senderId === session?.userId
                          ? "You"
                          : "Member"}
                    </strong>
                    <span>#{message.sequenceNumber}</span>
                  </div>
                  <pre>{message.content}</pre>
                </article>
              ))
            )}
          </div>

          <div className="composer">
            <input
              value={messageInput}
              onChange={(event) => setMessageInput(event.target.value)}
              placeholder={isInRoom ? "Type a message..." : "Click Enter room first..."}
              onKeyDown={(event) => {
                if (event.key === "Enter") {
                  sendMessage();
                }
              }}
            />

            {realtime.status === "CONNECTED" ? (
              <button onClick={sendMessage} disabled={!messageInput.trim()}>
                <Send size={16} />
                Send
              </button>
            ) : (
              <button onClick={enterRoom}>
                Enter room
              </button>
            )}
          </div>
        </Panel>

        <aside className="agent-panel">
          <Panel title="AI Activity" description="Agent progress appears while AI is responding.">
            <div className="activity-list">
              {realtime.aiStages.length === 0 ? (
                <p className="muted">No AI activity yet.</p>
              ) : (
                realtime.aiStages.map((stage, index) => (
                  <div className="activity-item" key={`${stage}-${index}`}>
                    <Bot size={14} />
                    <span>{stage}</span>
                  </div>
                ))
              )}
            </div>
          </Panel>
        </aside>
      </section>
    </main>
  );
}




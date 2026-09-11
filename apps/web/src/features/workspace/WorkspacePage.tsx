import { useEffect, useMemo, useState } from "react";
import {
  Bot,
  Copy,
  Link2,
  LogOut,
  MessageSquare,
  MoreVertical,
  Paperclip,
  Plus,
  Search,
  Send,
  Smile,
  Sparkles,
  Users
} from "lucide-react";
import { useNavigate, useParams } from "react-router-dom";
import { api } from "../../api/client";
import { useRealtimeRoom } from "../../hooks/useRealtimeRoom";
import { clearSession, loadSession, saveSession } from "../auth/session";
import type { AuthSession } from "../../types";

const quickPrompts = [
  "@ai give me a clean 3-step plan for building this SaaS MVP",
  "@ai what are the biggest risks in this product idea?",
  "@ai summarize the key decisions from this room",
  "@ai find me a birthday gift under Rs. 10,000",
  "@ai summarize octocat/Hello-World",
  "@ai search issues in spring-projects/spring-petclinic about docker"
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
  const [memberNames, setMemberNames] = useState<Record<string, string>>({});
  const [error, setError] = useState("");

  const shareUrl = useMemo(() => {
    if (!activeConversationId) {
      return "";
    }

    return `${window.location.origin}/r/${activeConversationId}`;
  }, [activeConversationId]);

  useEffect(() => {
    if (params.conversationId) {
      setActiveConversationId(params.conversationId);
    }
  }, [params.conversationId]);

  useEffect(() => {
    if (!session) {
      return;
    }

    setDisplayName((current) => current || session.displayName);

    setMemberNames((current) => ({
      ...current,
      [session.userId]: session.displayName
    }));
  }, [session]);

  useEffect(() => {
    if (!session) {
      return;
    }

    const missingSenderIds = Array.from(
      new Set(
        realtime.messages
          .filter((message) => message.messageType === "USER")
          .map((message) => message.senderId)
      )
    ).filter((senderId) => !memberNames[senderId]);

    if (missingSenderIds.length === 0) {
      return;
    }

    api.userProfiles(session.accessToken, missingSenderIds)
      .then((profiles) => {
        setMemberNames((current) => {
          const next = { ...current };

          profiles.forEach((profile) => {
            next[profile.userId] = profile.displayName;
          });

          return next;
        });
      })
      .catch(() => {
        // Keep UI fallback only. Do not store "Member xxxx" permanently,
        // because the profile endpoint may succeed on a later message.
      });
  }, [session, realtime.messages, memberNames]);

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

    setMemberNames((current) => ({
      ...current,
      [nextSession.userId]: nextSession.displayName
    }));

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

  async function loadRoomMemberNames(
    token: string,
    conversationId: string,
    currentSession: AuthSession
  ) {
    try {
      const members = await api.conversationMembers(token, conversationId);

      const userIds = Array.from(
        new Set([
          currentSession.userId,
          ...members.map((member) => member.userId)
        ])
      );

      const profiles = await api.userProfiles(token, userIds);

      setMemberNames((current) => {
        const next: Record<string, string> = {
          ...current,
          [currentSession.userId]: currentSession.displayName
        };

        profiles.forEach((profile) => {
          next[profile.userId] = profile.displayName;
        });

        return next;
      });
    } catch {
      setMemberNames((current) => ({
        ...current,
        [currentSession.userId]: currentSession.displayName
      }));
    }
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
      realtime.replaceMessages([]);

      await loadRoomMemberNames(guest.accessToken, conversation.id, guest);

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

      await loadRoomMemberNames(guest.accessToken, activeConversationId, guest);

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
    if (!shareUrl) {
      return;
    }

    await navigator.clipboard.writeText(shareUrl);
  }

  function leaveRoom() {
    realtime.disconnect();
    realtime.clear();
    clearSession();

    setSession(null);
    setActiveConversationId("");
    setDisplayName("");
    setMemberNames({});
    navigate("/");
  }

  function displaySenderName(senderId: string) {
    if (senderId === session?.userId) {
      return `${session.displayName} (you)`;
    }

    return memberNames[senderId] ?? `Member ${senderId.slice(0, 8)}`;
  }

  const hasRoom = Boolean(activeConversationId);
  const isInRoom = Boolean(session && hasRoom && realtime.status === "CONNECTED");
  const initials = (session?.displayName || displayName || "CM")
    .split(" ")
    .map((part) => part[0])
    .join("")
    .slice(0, 2)
    .toUpperCase();

  if (!hasRoom) {
    return (
      <main className="landing-shell">
        <section className="landing-hero">
          <div className="landing-intro">
            <div className="brand-pill">
              <span className="landing-logo"><Sparkles size={18} /></span>
              CollabMind
            </div>

            <div className="landing-copy">
              <span className="eyebrow">AI-powered group chat</span>
              <h1>Think better,<br />together.</h1>
              <p>
                Open a private room, invite your team, and bring AI into the
                conversation whenever you need it.
              </p>
            </div>

            <div className="landing-features">
              <span><MessageSquare size={16} /> Real-time chat</span>
              <span><Bot size={16} /> AI on demand</span>
              <span><Link2 size={16} /> One-link invites</span>
            </div>
          </div>

          <div className="create-card">
            <div className="create-card-heading">
              <span className="card-icon"><Plus size={19} /></span>
              <div>
                <h2>Start a new room</h2>
                <p>No account needed. Just add your name.</p>
              </div>
            </div>

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
              Create and enter room
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
              Join existing room
            </button>

            {error ? <div className="error-box">{error}</div> : null}

            <small className="privacy-note">Temporary rooms automatically expire after inactivity.</small>
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
    <main className="chat-app">
      <aside className="chat-sidebar">
        <header className="sidebar-header">
          <div className="brand-lockup">
            <span className="brand-logo"><Sparkles size={18} /></span>
            <strong>CollabMind</strong>
          </div>
          <button className="icon-button" aria-label="More options"><MoreVertical size={20} /></button>
        </header>

        <div className="sidebar-search">
          <Search size={17} />
          <input aria-label="Search conversations" placeholder="Search conversations" />
        </div>

        <div className="conversation-label">Conversations</div>
        <button className="conversation-item active">
          <span className="room-avatar"><Users size={19} /></span>
          <span className="conversation-copy">
            <span className="conversation-title">
              <strong>{roomName || "Temporary room"}</strong>
              <small>now</small>
            </span>
            <span className="conversation-preview">
              {realtime.messages[realtime.messages.length - 1]?.content || "Start the conversation"}
            </span>
          </span>
        </button>

        <footer className="sidebar-profile">
          <span className="user-avatar">{initials}</span>
          <span><strong>{session?.displayName}</strong><small>Online</small></span>
          <button className="icon-button" onClick={leaveRoom} aria-label="Leave room" title="Leave room">
            <LogOut size={18} />
          </button>
        </footer>
      </aside>

      <section className="chat-main">
        <header className="chat-header">
          <span className="room-avatar"><Users size={19} /></span>
          <div className="chat-heading">
            <strong>{roomName || "Temporary room"}</strong>
            <span>{isInRoom ? "online" : "connecting..."}</span>
          </div>
          <div className="chat-header-actions">
            <button className="header-action" onClick={copyLink} disabled={!shareUrl} title="Copy invite link">
              <Copy size={18} /><span>Invite</span>
            </button>
            <button className="icon-button" aria-label="Conversation options"><MoreVertical size={20} /></button>
          </div>
        </header>

        <div className="message-area">
          {(error || realtime.lastError) ? (
            <div className="error-box">{error || realtime.lastError}</div>
          ) : null}

          <div className="day-divider"><span>Today</span></div>

          {realtime.messages.length === 0 ? (
            <div className="welcome-message">
              <span className="welcome-icon"><Bot size={25} /></span>
              <h2>Start a conversation</h2>
              <p>Chat with your team, or mention <strong>@ai</strong> to bring an AI agent into the discussion.</p>
              <div className="prompt-row">
                {quickPrompts.slice(0, 3).map((prompt) => (
                  <button key={prompt} className="prompt-chip" onClick={() => setMessageInput(prompt)}>
                    {prompt.replace("@ai ", "")}
                  </button>
                ))}
              </div>
            </div>
          ) : (
            realtime.messages.map((message) => {
              const isOwn = message.messageType === "USER" && message.senderId === session?.userId;
              return (
                <article key={message.id} className={`message-row ${isOwn ? "own" : ""}`}>
                  {!isOwn ? (
                    <span className={`message-avatar ${message.messageType === "AI" ? "ai" : ""}`}>
                      {message.messageType === "AI" ? <Bot size={17} /> : displaySenderName(message.senderId).slice(0, 1).toUpperCase()}
                    </span>
                  ) : null}
                  <div className={`chat-message ${message.messageType.toLowerCase()}`}>
                    {!isOwn ? <strong>{message.messageType === "AI" ? `${message.agentType ?? "AI"} Agent` : displaySenderName(message.senderId)}</strong> : null}
                    <pre>{message.content}</pre>
                    <small>#{message.sequenceNumber}</small>
                  </div>
                </article>
              );
            })
          )}
        </div>

        <div className="composer">
          <button className="composer-icon" aria-label="Emoji"><Smile size={21} /></button>
          <button className="composer-icon attachment" aria-label="Attach file"><Paperclip size={20} /></button>
          <input
            value={messageInput}
            onChange={(event) => setMessageInput(event.target.value)}
            placeholder={isInRoom ? "Type a message" : "Enter the room to start chatting"}
            onKeyDown={(event) => { if (event.key === "Enter") sendMessage(); }}
          />
          {realtime.status === "CONNECTED" ? (
            <button className="send-button" onClick={sendMessage} disabled={!messageInput.trim()} aria-label="Send message"><Send size={19} /></button>
          ) : (
            <button className="enter-button" onClick={enterRoom}>Enter room</button>
          )}
        </div>
      </section>
    </main>
  );
}






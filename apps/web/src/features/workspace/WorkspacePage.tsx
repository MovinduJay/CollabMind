import { useEffect, useMemo, useState } from "react";
import {
  Bot,
  ChevronLeft,
  ChevronRight,
  Check,
  Copy,
  ExternalLink,
  Link2,
  LogOut,
  MessageSquare,
  MoreVertical,
  Paperclip,
  Plus,
  Search,
  Send,
  Smile,
  Users,
  X
} from "lucide-react";
import { useRef } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { api, ApiError } from "../../api/client";
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

const chatEmojis = [
  "😀", "😂", "😍", "🥳", "😎", "🤔", "😅", "😭",
  "👍", "👏", "🙏", "💪", "🙌", "🔥", "❤️", "✨",
  "✅", "🎉", "💡", "🚀", "👀", "🤝", "💯", "😊"
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

function renderMessageContent(content: string) {
  return content.split(/(@(?:ai|kapruka)\b)/gi).map((part, index) =>
    /^@(?:ai|kapruka)$/i.test(part) ? <strong className="ai-mention" key={index}>{part}</strong> : part
  );
}

function agentDisplayName(agentType?: string | null) {
  if (!agentType || agentType === "RESEARCHER") return "AI Agent";
  if (agentType === "KAPRUKA") return "Kapruka Agent";
  return `${agentType} Agent`;
}

function splitAiResponse(content: string) {
  const sections = content.split(/\n\s*\n/).map((section) => section.trim()).filter(Boolean);
  return sections.length > 0 ? sections : [content];
}

type KaprukaProduct = {
  id: string;
  name: string;
  summary?: string;
  price?: { amount?: number | null; currency?: string };
  in_stock?: boolean;
  image_url?: string | null;
  url: string;
};

function parseKaprukaMessage(content: string) {
  const pattern = /\[\[KAPRUKA_PRODUCTS\]\]([\s\S]*?)\[\[\/KAPRUKA_PRODUCTS\]\]/;
  const match = content.match(pattern);
  if (!match) return { text: content, products: [] as KaprukaProduct[] };

  try {
    const payload = JSON.parse(match[1]) as { results?: KaprukaProduct[] };
    return {
      text: content.replace(pattern, "").trim(),
      products: Array.isArray(payload.results) ? payload.results : []
    };
  } catch {
    return { text: content.replace(pattern, "").trim(), products: [] as KaprukaProduct[] };
  }
}

function formatKaprukaPrice(product: KaprukaProduct) {
  const amount = product.price?.amount;
  if (typeof amount !== "number") return "View price";
  if ((product.price?.currency ?? "LKR") === "LKR") {
    return `Rs. ${amount.toLocaleString("en-LK", { maximumFractionDigits: 2 })}`;
  }
  return `${product.price?.currency} ${amount.toLocaleString("en-LK", { maximumFractionDigits: 2 })}`;
}

function KaprukaProductCarousel({ products }: { products: KaprukaProduct[] }) {
  const trackRef = useRef<HTMLDivElement>(null);

  function move(direction: -1 | 1) {
    trackRef.current?.scrollBy({ left: direction * trackRef.current.clientWidth, behavior: "smooth" });
  }

  return (
    <section className="kapruka-showcase" aria-label="Kapruka products">
      <header className="kapruka-showcase-header">
        <div><strong>Here are the results</strong><span>{products.length} options from Kapruka</span></div>
        {products.length > 4 ? (
          <div className="kapruka-carousel-actions">
            <button onClick={() => move(-1)} aria-label="Previous products"><ChevronLeft size={18} /></button>
            <button onClick={() => move(1)} aria-label="Next products"><ChevronRight size={18} /></button>
          </div>
        ) : null}
      </header>
      <div className="kapruka-products" ref={trackRef}>
        {products.map((product) => (
          <a className="kapruka-product-card" href={product.url} target="_blank" rel="noreferrer" key={product.id}>
            <span className="kapruka-product-image">
              {product.image_url ? <img src={product.image_url} alt={product.name} loading="lazy" /> : <Bot size={28} />}
              <span className="kapruka-source">Kapruka</span>
            </span>
            <span className="kapruka-product-copy">
              <strong>{product.name}</strong>
              <span className="kapruka-product-footer">
                <span>
                  <b className="kapruka-product-price">{formatKaprukaPrice(product)}</b>
                  <small className={product.in_stock ? "in-stock" : "out-of-stock"}>{product.in_stock ? "In stock" : "Check availability"}</small>
                </span>
                <i aria-hidden="true"><ExternalLink size={16} /></i>
              </span>
            </span>
          </a>
        ))}
      </div>
    </section>
  );
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
  const [openMenu, setOpenMenu] = useState<"sidebar" | "conversation" | null>(null);
  const [inviteCopied, setInviteCopied] = useState(false);
  const [showParticipants, setShowParticipants] = useState(false);
  const [showEmojiPicker, setShowEmojiPicker] = useState(false);

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
    if (!openMenu && !showEmojiPicker && !showParticipants) {
      return;
    }

    function closeFloatingUi(event: PointerEvent) {
      const target = event.target as HTMLElement;

      if (target.closest(".menu-wrap, .emoji-wrap, .participants-panel, .participants-button")) {
        return;
      }

      setOpenMenu(null);
      setShowEmojiPicker(false);
      setShowParticipants(false);
    }

    document.addEventListener("pointerdown", closeFloatingUi);
    return () => document.removeEventListener("pointerdown", closeFloatingUi);
  }, [openMenu, showEmojiPicker, showParticipants]);

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

  async function ensureGuestSession(forceRefresh = false): Promise<AuthSession> {
    if (session && !forceRefresh) {
      return session;
    }

    const guestName = displayName.trim() || session?.displayName.trim();

    if (!guestName) {
      throw new Error("Enter your name first.");
    }

    if (forceRefresh) {
      clearSession();
    }

    const nextSession = await api.register({
      displayName: guestName,
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

      let activeGuest = guest;
      let conversation;

      try {
        conversation = await api.createConversation(activeGuest.accessToken, {
          name: roomName.trim() || "Untitled room"
        });
      } catch (exception) {
        if (!(exception instanceof ApiError) || exception.status !== 401) {
          throw exception;
        }

        activeGuest = await ensureGuestSession(true);
        conversation = await api.createConversation(activeGuest.accessToken, {
          name: roomName.trim() || "Untitled room"
        });
      }

      setActiveConversationId(conversation.id);
      navigate(`/r/${conversation.id}`);

      realtime.clear();
      realtime.replaceMessages([]);

      await loadRoomMemberNames(activeGuest.accessToken, conversation.id, activeGuest);

      realtime.connect(activeGuest.accessToken, conversation.id);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Could not create room.");
    }
  }

  async function enterRoom() {
    setError("");

    try {
      let guest = await ensureGuestSession();

      if (!activeConversationId) {
        throw new Error("Room link is missing.");
      }

      try {
        await api.joinConversation(guest.accessToken, activeConversationId);
      } catch (exception) {
        if (!(exception instanceof ApiError) || exception.status !== 401) {
          throw exception;
        }

        guest = await ensureGuestSession(true);
        await api.joinConversation(guest.accessToken, activeConversationId);
      }

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
    setShowEmojiPicker(false);
  }

  function addEmoji(emoji: string) {
    setMessageInput((current) => `${current}${emoji}`);
    setShowEmojiPicker(false);
  }

  function selectMention(mention: "ai" | "kapruka") {
    setMessageInput((current) => current.replace(/(^|\s)@[a-z]*$/i, `$1@${mention} `));
  }

  async function copyLink() {
    if (!shareUrl) {
      return;
    }

    try {
      await navigator.clipboard.writeText(shareUrl);
      setInviteCopied(true);
      window.setTimeout(() => setInviteCopied(false), 2200);
    } catch {
      setError("Could not copy the invite link. Please try again.");
    }
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

  function goToNewRoom() {
    realtime.disconnect();
    realtime.clear();
    setActiveConversationId("");
    setOpenMenu(null);
    navigate("/");
  }

  async function copyInviteFromMenu() {
    await copyLink();
    setOpenMenu(null);
  }

  function displaySenderName(senderId: string) {
    if (senderId === session?.userId) {
      return `${session.displayName} (you)`;
    }

    return memberNames[senderId] ?? `Member ${senderId.slice(0, 8)}`;
  }

  const hasRoom = Boolean(activeConversationId);
  const isInRoom = Boolean(session && hasRoom && realtime.status === "CONNECTED");
  const showAiSuggestion = /(^|\s)@[a-z]*$/i.test(messageInput);
  const mentionQuery = messageInput.match(/(?:^|\s)@([a-z]*)$/i)?.[1]?.toLowerCase() ?? "";
  const mentionOptions = [
    { id: "ai" as const, label: "@ai", description: "Ask the AI assistant" },
    { id: "kapruka" as const, label: "@kapruka", description: "Search live Kapruka products" }
  ].filter((option) => option.id.startsWith(mentionQuery));
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
              <span className="landing-logo"><MessageSquare size={18} /></span>
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
            <span className="brand-logo"><MessageSquare size={19} /></span>
            <strong>CollabMind</strong>
          </div>
          <div className="menu-wrap">
            <button
              className={`icon-button ${openMenu === "sidebar" ? "selected" : ""}`}
              aria-label="More options"
              aria-expanded={openMenu === "sidebar"}
              onClick={() => { setOpenMenu((current) => current === "sidebar" ? null : "sidebar"); setShowEmojiPicker(false); setShowParticipants(false); }}
            ><MoreVertical size={20} /></button>
            {openMenu === "sidebar" ? (
              <div className="dropdown-menu sidebar-menu">
                <button onClick={goToNewRoom}><Plus size={16} /> New room</button>
                <button className="menu-danger" onClick={leaveRoom}><LogOut size={16} /> Leave room</button>
              </div>
            ) : null}
          </div>
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
            <button className="header-action participants-button" onClick={() => { setShowParticipants((current) => !current); setOpenMenu(null); setShowEmojiPicker(false); }} title="View participants">
              <Users size={18} /><span>Participants</span>
            </button>
            <button className={`header-action ${inviteCopied ? "copied" : ""}`} onClick={copyLink} disabled={!shareUrl} title="Copy invite link">
              {inviteCopied ? <Check size={18} /> : <Copy size={18} />}
              <span>{inviteCopied ? "Copied" : "Invite"}</span>
            </button>
            <div className="menu-wrap">
              <button
                className={`icon-button ${openMenu === "conversation" ? "selected" : ""}`}
                aria-label="Conversation options"
                aria-expanded={openMenu === "conversation"}
                onClick={() => { setOpenMenu((current) => current === "conversation" ? null : "conversation"); setShowEmojiPicker(false); setShowParticipants(false); }}
              ><MoreVertical size={20} /></button>
              {openMenu === "conversation" ? (
                <div className="dropdown-menu conversation-menu">
                  <button onClick={copyInviteFromMenu}><Copy size={16} /> Copy invite link</button>
                  <button onClick={goToNewRoom}><Plus size={16} /> New room</button>
                  <button className="menu-danger" onClick={leaveRoom}><LogOut size={16} /> Leave room</button>
                </div>
              ) : null}
            </div>
          </div>
        </header>

        {showParticipants ? (
          <>
            <button className="panel-backdrop" aria-label="Close participants" onClick={() => setShowParticipants(false)} />
            <aside className="participants-panel" aria-label="Room participants">
              <header>
                <div>
                  <h2>Participants</h2>
                  <p>{Object.keys(memberNames).length} in this room</p>
                </div>
                <button className="icon-button" onClick={() => setShowParticipants(false)} aria-label="Close participants"><X size={20} /></button>
              </header>
              <div className="participant-list">
                {Object.entries(memberNames).map(([userId, name]) => {
                  const participantInitials = name.split(" ").map((part) => part[0]).join("").slice(0, 2).toUpperCase();
                  return (
                    <div className="participant-item" key={userId}>
                      <span className="participant-avatar">{participantInitials}</span>
                      <span className="participant-name">
                        <strong>{name}{userId === session?.userId ? " (you)" : ""}</strong>
                        <small><i /> Online</small>
                      </span>
                    </div>
                  );
                })}
              </div>
            </aside>
          </>
        ) : null}

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
              const kapruka = message.messageType === "AI"
                ? parseKaprukaMessage(message.content)
                : { text: message.content, products: [] as KaprukaProduct[] };
              const messageParts = message.messageType === "AI"
                ? (kapruka.products.length > 0 ? [] : splitAiResponse(kapruka.text))
                : [message.content];
              return (
                <article key={message.id} className={`message-row ${isOwn ? "own" : ""}`}>
                  {!isOwn ? (
                    <span className={`message-avatar ${message.messageType === "AI" ? "ai" : ""}`}>
                      {message.messageType === "AI" ? <Bot size={17} /> : displaySenderName(message.senderId).slice(0, 1).toUpperCase()}
                    </span>
                  ) : null}
                  <div className="message-stack">
                    {kapruka.products.length > 0 && !isOwn ? (
                      <strong className="product-agent-label">{agentDisplayName(message.agentType)}</strong>
                    ) : null}
                    {messageParts.map((part, partIndex) => (
                      <div
                        className={`chat-message ${message.messageType.toLowerCase()} ${partIndex > 0 ? "continued" : ""}`}
                        key={`${message.id}-${partIndex}`}
                        style={message.messageType === "AI" ? { animationDelay: `${partIndex * 160}ms` } : undefined}
                      >
                        {!isOwn && partIndex === 0 ? <strong>{message.messageType === "AI" ? agentDisplayName(message.agentType) : displaySenderName(message.senderId)}</strong> : null}
                        <pre>{renderMessageContent(part)}</pre>
                      </div>
                    ))}
                    {kapruka.products.length > 0 ? (
                      <KaprukaProductCarousel products={kapruka.products} />
                    ) : null}
                  </div>
                </article>
              );
            })
          )}
          {realtime.isAiTyping ? (
            <div className="message-row ai-typing-row" aria-label="AI is typing">
              <span className="message-avatar ai"><Bot size={17} /></span>
              <div className="typing-bubble"><span /><span /><span /></div>
            </div>
          ) : null}
        </div>

        <div className="composer">
          {showAiSuggestion ? (
            <div className="mention-picker">
              {mentionOptions.map((option) => (
                <button key={option.id} onClick={() => selectMention(option.id)}>
                  <span className="mention-avatar"><Bot size={18} /></span>
                  <span><strong>{option.label}</strong><small>{option.description}</small></span>
                </button>
              ))}
            </div>
          ) : null}
          <div className="emoji-wrap">
            <button
              className={`composer-icon ${showEmojiPicker ? "selected" : ""}`}
              aria-label="Choose emoji"
              aria-expanded={showEmojiPicker}
              onClick={() => { setShowEmojiPicker((current) => !current); setOpenMenu(null); setShowParticipants(false); }}
            ><Smile size={21} /></button>
            {showEmojiPicker ? (
              <div className="emoji-picker" role="dialog" aria-label="Emoji picker">
                <div className="emoji-picker-title">Choose an emoji</div>
                <div className="emoji-grid">
                  {chatEmojis.map((emoji) => (
                    <button key={emoji} onClick={() => addEmoji(emoji)} aria-label={`Insert ${emoji}`}>{emoji}</button>
                  ))}
                </div>
              </div>
            ) : null}
          </div>
          <button className="composer-icon attachment" aria-label="Attach file"><Paperclip size={20} /></button>
          <input
            value={messageInput}
            onChange={(event) => setMessageInput(event.target.value)}
            placeholder={isInRoom ? "Type a message" : "Enter the room to start chatting"}
            onKeyDown={(event) => {
              if (event.key === "Enter" && showAiSuggestion) {
                event.preventDefault();
                selectMention(mentionOptions[0]?.id ?? "ai");
              } else if (event.key === "Enter") {
                sendMessage();
              }
            }}
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






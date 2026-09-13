import { config } from "../config";
import type {
  AuthSession,
  ChatMessage,
  Conversation,
  ConversationMember,
  ToolAuditSummary,
  UserProfile
} from "../types";

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number
  ) {
    super(message);
  }
}

async function request<T>(
  url: string,
  options: RequestInit = {}
): Promise<T> {
  const response = await fetch(url, options);

  if (!response.ok) {
    const body = await response.text();
    throw new ApiError(body || response.statusText, response.status);
  }

  return response.json();
}

function jsonHeaders(token?: string): HeadersInit {
  return {
    "Content-Type": "application/json",
    ...(token ? { Authorization: `Bearer ${token}` } : {})
  };
}

export const api = {
  register(input: {
    displayName: string;
    email: string;
    password: string;
  }) {
    return request<AuthSession>(`${config.identityApi}/api/auth/register`, {
      method: "POST",
      headers: jsonHeaders(),
      body: JSON.stringify(input)
    });
  },

  login(input: {
    email: string;
    password: string;
  }) {
    return request<AuthSession>(`${config.identityApi}/api/auth/login`, {
      method: "POST",
      headers: jsonHeaders(),
      body: JSON.stringify(input)
    });
  },

  createConversation(token: string, input: {
    name: string;
  }) {
    return request<Conversation>(`${config.chatApi}/api/conversations`, {
      method: "POST",
      headers: jsonHeaders(token),
      body: JSON.stringify(input)
    });
  },

  renameConversation(token: string, conversationId: string, name: string) {
    return request<Conversation>(`${config.chatApi}/api/conversations/${conversationId}`, {
      method: "PATCH",
      headers: jsonHeaders(token),
      body: JSON.stringify({ name })
    });
  },

  listConversations(token: string) {
    return request<Conversation[]>(`${config.chatApi}/api/conversations`, {
      headers: {
        Authorization: `Bearer ${token}`
      }
    });
  },

  joinConversation(token: string, conversationId: string) {
    return request<Conversation>(`${config.chatApi}/api/conversations/${conversationId}/members`, {
      method: "POST",
      headers: jsonHeaders(token),
      body: JSON.stringify({})
    });
  },

  conversationMembers(token: string, conversationId: string) {
    return request<ConversationMember[]>(
      `${config.chatApi}/api/conversations/${conversationId}/members`,
      {
        headers: {
          Authorization: `Bearer ${token}`
        }
      }
    );
  },

  latestMessages(token: string, conversationId: string) {
    return request<ChatMessage[]>(
      `${config.chatApi}/api/conversations/${conversationId}/messages/latest?limit=50`,
      {
        headers: {
          Authorization: `Bearer ${token}`
        }
      }
    );
  },

  userProfiles(token: string, userIds: string[]) {
    return request<UserProfile[]>(`${config.identityApi}/api/users/profiles`, {
      method: "POST",
      headers: jsonHeaders(token),
      body: JSON.stringify({ userIds })
    });
  },

  auditSummary() {
    return request<ToolAuditSummary>(`${config.mcpApi}/mcp/audit/summary`);
  },

  health(url: string) {
    return request<{ status: string }>(url);
  }
};

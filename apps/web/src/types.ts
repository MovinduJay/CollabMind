export type AuthSession = {
  accessToken: string;
  userId: string;
  email: string;
  displayName: string;
};

export type Conversation = {
  id: string;
  name: string;
  createdByUserId: string;
  createdAt: string;
};

export type ConversationMember = {
  userId: string;
  role: string;
  joinedAt?: string | null;
};

export type ChatMessage = {
  id: string;
  conversationId: string;
  senderId: string;
  clientMessageId: string;
  sequenceNumber: number;
  messageType: "USER" | "AI";
  content: string;
  agentType?: string | null;
  sourceMessageId?: string | null;
  createdAt?: string | null;
};

export type ServerEvent = {
  eventId: string;
  eventType: string;
  conversationId: string | null;
  occurredAt: string;
  payload: any;
};

export type UserProfile = {
  userId: string;
  displayName: string;
};

export type ToolAuditSummary = {
  totalInvocations: number;
  successfulInvocations: number;
  failedInvocations: number;
  successRate: number;
  averageLatencyMs: number;
  maxLatencyMs: number;
  invocationsByTool: Record<string, number>;
};

export type ServiceHealth = {
  name: string;
  status: "UP" | "DOWN" | "CHECKING";
};

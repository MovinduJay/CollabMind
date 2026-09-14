# CollabMind

CollabMind is a backend-focused, multi-service realtime collaboration platform with AI agents.

Users can create conversations, join shared rooms, send realtime WebSocket messages, and mention AI agents such as `@ai`, `@ai`, `@ai`, and `@researcher`. The system persists chat history, broadcasts messages across active room subscribers, and asynchronously generates AI responses through a separate AI orchestration service.

This project is built to demonstrate production-style backend engineering: microservices, JWT authentication, WebSocket communication, async processing, database persistence, Flyway migrations, idempotency, rate limiting, and automated backend smoke tests.

---

## Core Features

- JWT-based registration and login
- JWT-secured REST APIs
- JWT-secured WebSocket connection
- Conversation creation and membership
- Two-user realtime room support
- WebSocket subscription and presence tracking
- User message persistence
- AI message persistence
- AI agents triggered through mentions
- Async AI response orchestration
- AI lifecycle WebSocket events
- Duplicate AI request protection
- AI cooldown/rate limiting
- PostgreSQL per service
- Flyway baseline migrations
- REST and realtime E2E test scripts

---

## Services

| Service | Port | Responsibility |
|---|---:|---|
| identity-service | 8082 | User registration, login, JWT issuing |
| chat-core | 8081 | Conversations, memberships, message persistence, message history |
| ai-orchestrator | 8084 | AI provider strategy, agent responses, audit logs |
| realtime-gateway | 8083 | WebSocket auth, subscriptions, presence, fan-out, AI flow orchestration |
| tool-mcp-server | 8085 | MCP-style tool bridge for external tool calls |

---

## High-Level Architecture

```text
Browser / WebSocket Test Client
        |
        | JWT-secured WebSocket
        v
realtime-gateway
        |
        | REST + JWT
        v
chat-core  <------ PostgreSQL: collabmind_chat
        |
        | REST
        v
ai-orchestrator <--- PostgreSQL: collabmind_ai
        |
        v
AI Provider Strategy
(mock / Gemini-ready)

identity-service <--- PostgreSQL: collabmind_identity
        |
        v
JWT issuing and authentication
```

---

## Realtime AI Flow

```text
1. User connects to realtime-gateway using JWT.
2. User subscribes to a conversation.
3. realtime-gateway validates membership with chat-core.
4. User sends a message through WebSocket.
5. realtime-gateway persists the message in chat-core.
6. realtime-gateway detects AI mention, for example @ai.
7. AI request guard checks duplicate/rate-limit rules.
8. realtime-gateway fetches recent user-only context from chat-core.
9. realtime-gateway calls ai-orchestrator.
10. ai-orchestrator generates response using selected provider.
11. realtime-gateway saves AI response back to chat-core.
12. realtime-gateway broadcasts AI response to room subscribers.
```

---

## Tech Stack

- Java 23
- Spring Boot
- Spring Security
- Spring WebSocket
- Spring Data JPA
- PostgreSQL
- Flyway
- Maven
- JWT / JJWT
- PowerShell test scripts

---

## Local Setup

Detailed local setup is available here:

```text
docs/LOCAL_SETUP.md
```

Required local databases:

```sql
CREATE DATABASE collabmind_identity;
CREATE DATABASE collabmind_chat;
CREATE DATABASE collabmind_ai;
```

Run order:

```text
1. IdentityServiceApplication
2. ChatCoreApplication
3. AiOrchestratorApplication
4. RealtimeGatewayApplication
```

---

## Health Check

```powershell
.\scripts\verify-health.ps1
```

Expected:

```text
identity-service: UP
chat-core: UP
ai-orchestrator: UP
realtime-gateway: UP
```

---

## Smoke Tests

REST chat/history smoke test:

```powershell
.\scripts\smoke-chat-history.ps1
```

Realtime AI E2E test:

```powershell
.\scripts\e2e-realtime-ai.ps1
```

Expected:

```text
Smoke test passed.
E2E realtime AI test passed.
```

---

## WebSocket Events

Important server events:

```text
CONNECTED
SUBSCRIBED_CONVERSATION
PRESENCE_UPDATED
MESSAGE_CREATED
AI_STAGE_UPDATED
AI_MESSAGE_CREATED
AI_RESPONSE_FAILED
AI_RATE_LIMITED
AI_REQUEST_DUPLICATE_IGNORED
```

---

## Why This Project Exists

Most junior portfolio chat projects only show basic CRUD. CollabMind is designed to show deeper backend engineering:

- secure service-to-service flow
- realtime messaging
- distributed service boundaries
- async AI processing
- failure-stage reporting
- idempotent message handling
- LLM cost protection
- automated backend testing
- database migration discipline

---

## Current Status

The backend is functional locally with automated smoke tests. The current focus is backend architecture and production-style engineering rather than UI polish.

---

## MCP-Ready Tool Architecture

CollabMind includes an MCP-ready tool abstraction inside `ai-orchestrator`.

Current supported tool-style agent:

```text
@ai
```

Example:

```text
@ai find me a birthday gift under Rs. 10,000
```

Current flow:

```text
realtime-gateway
? ai-orchestrator
? AiToolService
? MockShoppingToolProvider
```

The mock provider can later be replaced with a real MCP provider without changing the WebSocket or chat persistence flow.

More details:

```text
docs/MCP_INTEGRATION.md

Kubernetes deployment architecture and beginner guide:

```text
docs/KUBERNETES.md
```

Complete CI/CD and release controls:

```text
docs/CI_CD.md
```
```



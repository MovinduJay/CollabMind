package org.collabmind.realtime.event;

import org.collabmind.realtime.chatcore.client.ChatCoreMessageResponse;
import java.time.Instant;
import java.util.UUID;

public record AiRequestedEvent(String eventId, String commandId, UUID userId, ChatCoreMessageResponse sourceMessage, String agentType, Instant occurredAt) {}

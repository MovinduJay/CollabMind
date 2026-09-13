package org.collabmind.realtime.event;

import org.collabmind.realtime.chatcore.client.ChatCoreMessageResponse;
import java.time.Instant;

public record MessageCreatedEvent(String eventId, String commandId, ChatCoreMessageResponse message, Instant occurredAt) {}

package org.collabmind.chatcore.messaging.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "messages",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_message_conversation_sequence",
                        columnNames = {"conversation_id", "sequence_number"}
                ),
                @UniqueConstraint(
                        name = "uk_message_sender_client_message",
                        columnNames = {"sender_id", "client_message_id"}
                )
        }
)
public class Message {

    public enum MessageType {
        USER,
        AI
    }

    public static final UUID AI_SYSTEM_SENDER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "client_message_id", nullable = false)
    private UUID clientMessageId;

    @Column(name = "sequence_number", nullable = false)
    private long sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false)
    private MessageType messageType;

    @Column(name = "content", nullable = false, length = 12000)
    private String content;

    @Column(name = "agent_type", length = 40)
    private String agentType;

    @Column(name = "source_message_id")
    private UUID sourceMessageId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Message() {
    }

    private Message(
            UUID conversationId,
            UUID senderId,
            UUID clientMessageId,
            long sequenceNumber,
            MessageType messageType,
            String content,
            String agentType,
            UUID sourceMessageId
    ) {
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.clientMessageId = clientMessageId;
        this.sequenceNumber = sequenceNumber;
        this.messageType = messageType;
        this.content = content;
        this.agentType = agentType;
        this.sourceMessageId = sourceMessageId;
        this.createdAt = Instant.now();
    }

    public static Message userMessage(
            UUID conversationId,
            UUID senderId,
            UUID clientMessageId,
            long sequenceNumber,
            String content
    ) {
        return new Message(
                conversationId,
                senderId,
                clientMessageId,
                sequenceNumber,
                MessageType.USER,
                content,
                null,
                null
        );
    }

    public static Message aiMessage(
            UUID conversationId,
            UUID clientMessageId,
            long sequenceNumber,
            String agentType,
            UUID sourceMessageId,
            String content
    ) {
        return new Message(
                conversationId,
                AI_SYSTEM_SENDER_ID,
                clientMessageId,
                sequenceNumber,
                MessageType.AI,
                content,
                agentType,
                sourceMessageId
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public UUID getSenderId() {
        return senderId;
    }

    public UUID getClientMessageId() {
        return clientMessageId;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public String getContent() {
        return content;
    }

    public String getAgentType() {
        return agentType;
    }

    public UUID getSourceMessageId() {
        return sourceMessageId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}



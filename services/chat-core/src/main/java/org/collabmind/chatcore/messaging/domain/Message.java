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

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
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
    @Column(nullable = false, length = 30)
    private MessageType messageType;

    @Column(nullable = false, length = 4000)
    private String content;

    @Column(nullable = false)
    private Instant createdAt;

    protected Message() {
    }

    private Message(
            UUID conversationId,
            UUID senderId,
            UUID clientMessageId,
            long sequenceNumber,
            MessageType messageType,
            String content
    ) {
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.clientMessageId = clientMessageId;
        this.sequenceNumber = sequenceNumber;
        this.messageType = messageType;
        this.content = content;
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
                content
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}

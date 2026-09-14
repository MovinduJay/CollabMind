package org.collabmind.chatcore.conversation.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "conversations",
        indexes = {
                @Index(name = "idx_conversations_created_by_user_id", columnList = "created_by_user_id"),
                @Index(name = "idx_conversations_created_at", columnList = "created_at")
        }
)
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "next_sequence", nullable = false)
    private long nextSequence = 1;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Conversation() {
    }

    public Conversation(String name, UUID createdByUserId) {
        this.name = name.trim();
        this.createdByUserId = createdByUserId;
        this.nextSequence = 1;
    }

    public long allocateNextSequence() {
        long allocatedSequence = nextSequence;
        nextSequence++;
        return allocatedSequence;
    }

    public void rename(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Conversation name cannot be blank");
        }
        this.name = name.trim();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public long getNextSequence() {
        return nextSequence;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

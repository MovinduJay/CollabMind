package org.collabmind.chatcore.conversation.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private UUID createdByUserId;

    @Column(nullable = false)
    private long nextSequence;

    @Column(nullable = false)
    private Instant createdAt;

    protected Conversation() {
    }

    public Conversation(String name, UUID createdByUserId) {
        this.name = name;
        this.createdByUserId = createdByUserId;
        this.nextSequence = 1L;
        this.createdAt = Instant.now();
    }

    public long allocateNextSequence() {
        long allocatedSequence = this.nextSequence;
        this.nextSequence++;
        return allocatedSequence;
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

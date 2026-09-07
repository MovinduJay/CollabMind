package org.collabmind.chatcore.membership.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "conversation_members",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_conversation_member",
                        columnNames = {"conversation_id", "user_id"}
                )
        },
        indexes = {
                @Index(name = "idx_conversation_members_conversation_id", columnList = "conversation_id"),
                @Index(name = "idx_conversation_members_user_id", columnList = "user_id")
        }
)
public class ConversationMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    protected ConversationMember() {
    }

    public ConversationMember(
            UUID conversationId,
            UUID userId,
            Role role
    ) {
        this.conversationId = conversationId;
        this.userId = userId;
        this.role = role;
    }

    public UUID getId() {
        return id;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Role getRole() {
        return role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public enum Role {
        OWNER,
        MEMBER
    }
}

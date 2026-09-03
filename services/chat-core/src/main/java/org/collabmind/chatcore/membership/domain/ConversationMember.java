package org.collabmind.chatcore.membership.domain;

import jakarta.persistence.*;

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
        }
)
public class ConversationMember {

    public enum Role {
        OWNER,
        MEMBER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    @Column(nullable = false)
    private Instant joinedAt;

    protected ConversationMember() {
    }

    public ConversationMember(UUID conversationId, UUID userId, Role role) {
        this.conversationId = conversationId;
        this.userId = userId;
        this.role = role;
        this.joinedAt = Instant.now();
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
}

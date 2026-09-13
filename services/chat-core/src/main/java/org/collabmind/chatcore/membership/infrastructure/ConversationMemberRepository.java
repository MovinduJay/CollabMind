package org.collabmind.chatcore.membership.infrastructure;

import org.collabmind.chatcore.membership.domain.ConversationMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, UUID> {
    void deleteByConversationId(UUID conversationId);

    boolean existsByConversationIdAndUserId(UUID conversationId, UUID userId);

    long countByConversationId(UUID conversationId);

    List<ConversationMember> findByConversationId(UUID conversationId);

    List<ConversationMember> findByUserId(UUID userId);
}

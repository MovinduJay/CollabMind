package org.collabmind.chatcore.membership.infrastructure;

import org.collabmind.chatcore.membership.domain.ConversationMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, UUID> {

    boolean existsByConversationIdAndUserId(UUID conversationId, UUID userId);

    long countByConversationId(UUID conversationId);
}

package org.collabmind.chatcore.messaging.infrastructure;

import org.collabmind.chatcore.messaging.domain.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    Optional<Message> findBySenderIdAndClientMessageId(UUID senderId, UUID clientMessageId);

    @Query("""
            select m
            from Message m
            where m.conversationId = :conversationId
              and m.sequenceNumber > :afterSequence
            order by m.sequenceNumber asc
            """)
    List<Message> findMessagesAfter(UUID conversationId, long afterSequence, Pageable pageable);
}

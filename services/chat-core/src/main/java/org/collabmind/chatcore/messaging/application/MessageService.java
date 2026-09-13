package org.collabmind.chatcore.messaging.application;

import org.collabmind.chatcore.common.exception.ConversationNotFoundException;
import org.collabmind.chatcore.conversation.domain.Conversation;
import org.collabmind.chatcore.conversation.infrastructure.ConversationRepository;
import org.collabmind.chatcore.conversation.application.RoomActivityService;
import org.collabmind.chatcore.membership.infrastructure.ConversationMemberRepository;
import org.collabmind.chatcore.messaging.domain.Message;
import org.collabmind.chatcore.messaging.infrastructure.MessageRepository;
import org.collabmind.chatcore.messaging.web.MessageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class MessageService {

    private static final UUID AI_SENDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final MessageRepository messageRepository;
    private final RoomActivityService roomActivityService;

    public MessageService(
            ConversationRepository conversationRepository,
            ConversationMemberRepository memberRepository,
            MessageRepository messageRepository,
            RoomActivityService roomActivityService
    ) {
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
        this.messageRepository = messageRepository;
        this.roomActivityService = roomActivityService;
    }

    @Transactional
    public MessageResponse sendUserMessage(
            UUID conversationId,
            UUID senderUserId,
            UUID clientMessageId,
            String content
    ) {
        requireConversationMember(conversationId, senderUserId);

        MessageResponse response = messageRepository
                .findBySenderIdAndClientMessageId(senderUserId, clientMessageId)
                .map(MessageResponse::from)
                .orElseGet(() -> createUserMessage(
                        conversationId,
                        senderUserId,
                        clientMessageId,
                        content
                ));
        roomActivityService.touch(conversationId);
        return response;
    }

    @Transactional
    public MessageResponse saveAiMessage(
            UUID conversationId,
            UUID authenticatedUserId,
            UUID clientMessageId,
            UUID sourceMessageId,
            String agentType,
            String content
    ) {
        requireConversationMember(conversationId, authenticatedUserId);

        MessageResponse response = messageRepository
                .findBySenderIdAndClientMessageId(AI_SENDER_ID, clientMessageId)
                .map(MessageResponse::from)
                .orElseGet(() -> createAiMessage(
                        conversationId,
                        clientMessageId,
                        sourceMessageId,
                        agentType,
                        content
                ));
        roomActivityService.touch(conversationId);
        return response;
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> findMessagesAfter(
            UUID conversationId,
            UUID authenticatedUserId,
            long afterSequence,
            int limit
    ) {
        requireConversationMember(conversationId, authenticatedUserId);

        int safeLimit = safeLimit(limit);

        return messageRepository
                .findMessagesAfter(
                        conversationId,
                        Math.max(0, afterSequence),
                        PageRequest.of(0, safeLimit)
                )
                .stream()
                .map(MessageResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> findLatestMessages(
            UUID conversationId,
            UUID authenticatedUserId,
            int limit
    ) {
        requireConversationMember(conversationId, authenticatedUserId);

        int safeLimit = safeLimit(limit);

        return messageRepository
                .findLatestMessages(
                        conversationId,
                        PageRequest.of(0, safeLimit)
                )
                .stream()
                .sorted(Comparator.comparingLong(Message::getSequenceNumber))
                .map(MessageResponse::from)
                .toList();
    }

    private MessageResponse createUserMessage(
            UUID conversationId,
            UUID senderUserId,
            UUID clientMessageId,
            String content
    ) {
        Conversation conversation = conversationRepository
                .findByIdForUpdate(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));

        long sequenceNumber = conversation.allocateNextSequence();

        Message message = Message.userMessage(
                conversation.getId(),
                senderUserId,
                clientMessageId,
                sequenceNumber,
                content
        );

        return MessageResponse.from(messageRepository.save(message));
    }

    private MessageResponse createAiMessage(
            UUID conversationId,
            UUID clientMessageId,
            UUID sourceMessageId,
            String agentType,
            String content
    ) {
        Conversation conversation = conversationRepository
                .findByIdForUpdate(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));

        long sequenceNumber = conversation.allocateNextSequence();

        Message message = Message.aiMessage(
                conversation.getId(),
                AI_SENDER_ID,
                clientMessageId,
                sourceMessageId,
                agentType,
                sequenceNumber,
                content
        );

        return MessageResponse.from(messageRepository.save(message));
    }

    private void requireConversationMember(
            UUID conversationId,
            UUID authenticatedUserId
    ) {
        boolean conversationExists = conversationRepository.existsById(conversationId);

        if (!conversationExists) {
            throw new ConversationNotFoundException(conversationId);
        }

        boolean member = memberRepository.existsByConversationIdAndUserId(
                conversationId,
                authenticatedUserId
        );

        if (!member) {
            throw new IllegalArgumentException("User is not a member of this conversation");
        }
    }

    private int safeLimit(int requestedLimit) {
        if (requestedLimit <= 0) {
            return DEFAULT_LIMIT;
        }

        return Math.min(requestedLimit, MAX_LIMIT);
    }
}

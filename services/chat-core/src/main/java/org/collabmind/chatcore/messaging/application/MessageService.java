package org.collabmind.chatcore.messaging.application;

import org.collabmind.chatcore.conversation.domain.Conversation;
import org.collabmind.chatcore.conversation.infrastructure.ConversationRepository;
import org.collabmind.chatcore.membership.infrastructure.ConversationMemberRepository;
import org.collabmind.chatcore.messaging.domain.Message;
import org.collabmind.chatcore.messaging.infrastructure.MessageRepository;
import org.collabmind.chatcore.messaging.web.MessageResponse;
import org.collabmind.chatcore.messaging.web.SendMessageRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.collabmind.chatcore.common.exception.ConversationNotFoundException;
import org.collabmind.chatcore.common.exception.UserNotConversationMemberException;
import org.collabmind.chatcore.messaging.web.SaveAiMessageRequest;

import java.util.List;
import java.util.UUID;

@Service
public class MessageService {

    private static final int MAX_PAGE_SIZE = 100;

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;


    public MessageService(
            MessageRepository messageRepository,
            ConversationRepository conversationRepository,
            ConversationMemberRepository memberRepository
    ) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public MessageResponse sendMessage(UUID conversationId, SendMessageRequest request) {
        return messageRepository
                .findBySenderIdAndClientMessageId(request.senderId(), request.clientMessageId())
                .map(MessageResponse::from)
                .orElseGet(() -> createNewMessage(conversationId, request));
    }

    private MessageResponse createNewMessage(UUID conversationId, SendMessageRequest request) {
        boolean isMember = memberRepository.existsByConversationIdAndUserId(
                conversationId,
                request.senderId()
        );

        if (!isMember) {
            throw new IllegalArgumentException("User is not a member of this conversation");
        }

        Conversation conversation = conversationRepository.findByIdForUpdate(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));
        long sequenceNumber = conversation.allocateNextSequence();

        Message message = Message.userMessage(
                conversation.getId(),
                request.senderId(),
                request.clientMessageId(),
                sequenceNumber,
                request.content()
        );

        Message savedMessage = messageRepository.save(message);

        return MessageResponse.from(savedMessage);
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> getMessagesAfter(
            UUID conversationId,
            long afterSequence,
            int limit
    ) {
        int safeLimit = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);

        return messageRepository
                .findMessagesAfter(
                        conversationId,
                        afterSequence,
                        PageRequest.of(0, safeLimit)
                )
                .stream()
                .map(MessageResponse::from)
                .toList();
    }

    @Transactional
    public MessageResponse saveAiMessage(
            UUID conversationId,
            SaveAiMessageRequest request
    ) {
        return messageRepository
                .findBySenderIdAndClientMessageId(
                        Message.AI_SYSTEM_SENDER_ID,
                        request.clientMessageId()
                )
                .map(MessageResponse::from)
                .orElseGet(() -> createNewAiMessage(conversationId, request));
    }

    private MessageResponse createNewAiMessage(
            UUID conversationId,
            SaveAiMessageRequest request
    ) {
        boolean requesterIsMember = memberRepository.existsByConversationIdAndUserId(
                conversationId,
                request.requesterUserId()
        );

        if (!requesterIsMember) {
            throw new UserNotConversationMemberException(
                    request.requesterUserId(),
                    conversationId
            );
        }

        Conversation conversation = conversationRepository.findByIdForUpdate(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));

        long sequenceNumber = conversation.allocateNextSequence();

        Message message = Message.aiMessage(
                conversationId,
                request.clientMessageId(),
                sequenceNumber,
                request.agentType(),
                request.sourceMessageId(),
                request.content()
        );

        Message savedMessage = messageRepository.save(message);

        return MessageResponse.from(savedMessage);
    }
}

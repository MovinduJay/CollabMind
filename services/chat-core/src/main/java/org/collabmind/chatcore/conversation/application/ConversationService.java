package org.collabmind.chatcore.conversation.application;

import org.collabmind.chatcore.conversation.domain.Conversation;
import org.collabmind.chatcore.conversation.infrastructure.ConversationRepository;
import org.collabmind.chatcore.conversation.web.ConversationResponse;
import org.collabmind.chatcore.conversation.web.CreateConversationRequest;
import org.collabmind.chatcore.membership.domain.ConversationMember;
import org.collabmind.chatcore.membership.infrastructure.ConversationMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.collabmind.chatcore.common.exception.ConversationNotFoundException;
import org.collabmind.chatcore.conversation.web.ConversationMembershipResponse;

import java.util.UUID;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;

    public ConversationService(
            ConversationRepository conversationRepository,
            ConversationMemberRepository memberRepository
    ) {
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public ConversationResponse createConversation(CreateConversationRequest request) {
        Conversation conversation = new Conversation(
                request.name(),
                request.creatorUserId()
        );

        Conversation savedConversation = conversationRepository.save(conversation);

        ConversationMember ownerMembership = new ConversationMember(
                savedConversation.getId(),
                request.creatorUserId(),
                ConversationMember.Role.OWNER
        );

        memberRepository.save(ownerMembership);

        return ConversationResponse.from(savedConversation, 1);
    }

    @Transactional
    public ConversationResponse joinConversation(UUID conversationId, UUID userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));

        boolean alreadyMember = memberRepository.existsByConversationIdAndUserId(
                conversationId,
                userId
        );

        if (!alreadyMember) {
            ConversationMember member = new ConversationMember(
                    conversationId,
                    userId,
                    ConversationMember.Role.MEMBER
            );

            memberRepository.save(member);
        }

        long memberCount = memberRepository.countByConversationId(conversationId);

        return ConversationResponse.from(conversation, memberCount);
    }

    @Transactional(readOnly = true)
    public ConversationMembershipResponse checkMembership(UUID conversationId, UUID userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));

        boolean member = memberRepository.existsByConversationIdAndUserId(
                conversation.getId(),
                userId
        );

        return new ConversationMembershipResponse(
                conversation.getId(),
                userId,
                member
        );
    }
}

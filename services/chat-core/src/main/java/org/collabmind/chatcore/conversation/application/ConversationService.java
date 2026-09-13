package org.collabmind.chatcore.conversation.application;

import org.collabmind.chatcore.common.exception.ConversationNotFoundException;
import org.collabmind.chatcore.conversation.domain.Conversation;
import org.collabmind.chatcore.conversation.infrastructure.ConversationRepository;
import org.collabmind.chatcore.conversation.web.ConversationMemberResponse;
import org.collabmind.chatcore.conversation.web.ConversationMembershipResponse;
import org.collabmind.chatcore.conversation.web.ConversationResponse;
import org.collabmind.chatcore.membership.domain.ConversationMember;
import org.collabmind.chatcore.membership.infrastructure.ConversationMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final RoomActivityService roomActivityService;

    public ConversationService(
            ConversationRepository conversationRepository,
            ConversationMemberRepository memberRepository,
            RoomActivityService roomActivityService
    ) {
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
        this.roomActivityService = roomActivityService;
    }

    @Transactional
    public ConversationResponse createConversation(
            String name,
            UUID authenticatedUserId
    ) {
        Conversation conversation = new Conversation(
                name,
                authenticatedUserId
        );

        Conversation savedConversation = conversationRepository.save(conversation);

        ConversationMember owner = new ConversationMember(
                savedConversation.getId(),
                authenticatedUserId,
                ConversationMember.Role.OWNER
        );

        memberRepository.save(owner);
        roomActivityService.touch(savedConversation.getId());

        return ConversationResponse.from(savedConversation, 1);
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> listMyConversations(UUID authenticatedUserId) {
        List<UUID> conversationIds = memberRepository.findByUserId(authenticatedUserId)
                .stream()
                .map(ConversationMember::getConversationId)
                .distinct()
                .toList();

        if (conversationIds.isEmpty()) {
            return List.of();
        }

        return conversationRepository.findByIdIn(conversationIds)
                .stream()
                .sorted(Comparator.comparing(
                        Conversation::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ).reversed())
                .map(conversation -> ConversationResponse.from(
                        conversation,
                        memberRepository.countByConversationId(conversation.getId())
                ))
                .toList();
    }

    @Transactional
    public ConversationResponse joinConversation(
            UUID conversationId,
            UUID authenticatedUserId
    ) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));

        boolean alreadyMember = memberRepository.existsByConversationIdAndUserId(
                conversationId,
                authenticatedUserId
        );

        if (!alreadyMember) {
            ConversationMember member = new ConversationMember(
                    conversationId,
                    authenticatedUserId,
                    ConversationMember.Role.MEMBER
            );

            memberRepository.save(member);
        }

        long memberCount = memberRepository.countByConversationId(conversationId);
        roomActivityService.touch(conversationId);

        return ConversationResponse.from(conversation, memberCount);
    }

    @Transactional(readOnly = true)
    public List<ConversationMemberResponse> listMembers(
            UUID conversationId,
            UUID authenticatedUserId
    ) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));

        requireConversationMember(
                conversation.getId(),
                authenticatedUserId
        );

        return memberRepository.findByConversationId(conversation.getId())
                .stream()
                .map(ConversationMemberResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversationMembershipResponse checkMembership(
            UUID conversationId,
            UUID userId
    ) {
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

    private void requireConversationMember(
            UUID conversationId,
            UUID authenticatedUserId
    ) {
        boolean member = memberRepository.existsByConversationIdAndUserId(
                conversationId,
                authenticatedUserId
        );

        if (!member) {
            throw new IllegalArgumentException("User is not a member of this conversation");
        }
    }
}

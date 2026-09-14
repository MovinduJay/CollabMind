package org.collabmind.chatcore.conversation.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationTest {

    @Test
    void trimsNamesAndAllocatesMonotonicMessageSequences() {
        Conversation conversation = new Conversation("  Product team  ", UUID.randomUUID());

        assertThat(conversation.getName()).isEqualTo("Product team");
        assertThat(conversation.allocateNextSequence()).isEqualTo(1);
        assertThat(conversation.allocateNextSequence()).isEqualTo(2);
    }

    @Test
    void rejectsBlankRoomTitles() {
        Conversation conversation = new Conversation("Product team", UUID.randomUUID());

        assertThatThrownBy(() -> conversation.rename("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Conversation name cannot be blank");
    }
}

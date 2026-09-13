package org.collabmind.realtime.websocket.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiMentionServiceTest {

    private final AiMentionService service = new AiMentionService();

    @Test
    void routesKaprukaMentionToDedicatedAgent() {
        assertThat(service.detectAgentType("@kapruka gifts under Rs. 10,000"))
                .contains("KAPRUKA");
    }

    @Test
    void doesNotRouteOrdinaryKaprukaTextWithoutMention() {
        assertThat(service.detectAgentType("Kapruka has gifts"))
                .isEmpty();
    }
}

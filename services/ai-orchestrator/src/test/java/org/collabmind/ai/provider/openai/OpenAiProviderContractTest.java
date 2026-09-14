package org.collabmind.ai.provider.openai;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.collabmind.ai.agent.domain.AgentType;
import org.collabmind.ai.agent.web.AiPromptRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiProviderContractTest {
    private WireMockServer server;

    @BeforeEach
    void startServer() {
        server = new WireMockServer(0);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void sendsAResponsesApiRequestAndExtractsOutputText() {
        server.stubFor(post(urlEqualTo("/v1/responses"))
                .withHeader("Authorization", equalTo("Bearer test-key"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("gpt-5-nano")))
                .willReturn(okJson("{\"output_text\":\"Here are the results.\"}")));
        OpenAiProvider provider = new OpenAiProvider(http11RestClient(), server.baseUrl(), "test-key", "gpt-5-nano");

        String output = provider.generateResponse(new AiPromptRequest(
                UUID.randomUUID(), UUID.randomUUID(), AgentType.KAPRUKA,
                "find a cake under 10k", List.of()), "Earlier the user asked for a birthday gift.");

        assertThat(output).isEqualTo("Here are the results.");
        server.verify(postRequestedFor(urlEqualTo("/v1/responses"))
                .withRequestBody(containing("find a cake under 10k")));
    }

    @Test
    void failsFastWhenTheApiKeyIsMissing() {
        OpenAiProvider provider = new OpenAiProvider(http11RestClient(), server.baseUrl(), "", "gpt-5-nano");
        assertThatThrownBy(() -> provider.generateResponse(new AiPromptRequest(
                UUID.randomUUID(), UUID.randomUUID(), AgentType.RESEARCHER, "hello", List.of()), ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("API key");
    }

    private RestClient.Builder http11RestClient() {
        return RestClient.builder().requestFactory(new SimpleClientHttpRequestFactory());
    }
}

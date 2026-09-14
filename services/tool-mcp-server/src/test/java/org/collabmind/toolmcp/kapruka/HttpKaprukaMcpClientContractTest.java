package org.collabmind.toolmcp.kapruka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

class HttpKaprukaMcpClientContractTest {
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
    void completesTheMcpHandshakeAndForwardsStructuredSearchFilters() {
        server.stubFor(post(urlEqualTo("/mcp"))
                .withRequestBody(matchingJsonPath("$.method", equalTo("initialize")))
                .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":{}}")
                        .withHeader("mcp-session-id", "session-123")));
        server.stubFor(post(urlEqualTo("/mcp"))
                .withRequestBody(matchingJsonPath("$.method", equalTo("notifications/initialized")))
                .willReturn(aResponse().withStatus(202).withHeader("mcp-session-id", "session-123")));
        server.stubFor(post(urlEqualTo("/mcp"))
                .withRequestBody(matchingJsonPath("$.method", equalTo("tools/call")))
                .willReturn(okJson("{\"jsonrpc\":\"2.0\",\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"{\\\"results\\\":[{\\\"id\\\":\\\"cake-1\\\",\\\"name\\\":\\\"Chocolate Cake\\\"}]}\"}]}}")
                        .withHeader("mcp-session-id", "session-123")));
        HttpKaprukaMcpClient client = new HttpKaprukaMcpClient(
                new ObjectMapper(), URI.create(server.baseUrl() + "/mcp"), 5);

        String result = client.search(new KaprukaSearchCriteria(
                "cake", null, new BigDecimal("10000"), "LKR", 12));

        assertThat(result).contains("Chocolate Cake");
        server.verify(postRequestedFor(urlEqualTo("/mcp"))
                .withHeader("Mcp-Session-Id", equalTo("session-123"))
                .withRequestBody(matchingJsonPath("$.params.arguments.params.max_price", equalTo("10000")))
                .withRequestBody(matchingJsonPath("$.params.arguments.params.q", equalTo("cake"))));
    }
}

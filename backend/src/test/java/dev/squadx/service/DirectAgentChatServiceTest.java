package dev.squadx.service;

import dev.squadx.model.Agent;
import dev.squadx.model.LiveSession;
import dev.squadx.model.Squad;
import dev.squadx.model.Organization;
import dev.squadx.model.enums.AgentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectAgentChatServiceTest {

    @Mock
    private ObjectProvider<ChatClient> chatClientProvider;

    @Test
    @DisplayName("should generate reply with available ChatClient")
    void shouldGenerateReply() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClientProvider.getIfAvailable()).thenReturn(chatClient);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content()).thenReturn("Ready to help.");

        DirectAgentChatService service = new DirectAgentChatService(chatClientProvider);

        Agent agent = Agent.builder()
                .name("Builder")
                .agentType(AgentType.BACKEND)
                .systemPrompt("You are a backend expert.")
                .squad(Squad.builder().organization(Organization.builder().name("Org").slug("org").build()).build())
                .build();
        LiveSession session = LiveSession.builder().agent(agent).build();
        session.setId(10L);

        Optional<String> reply = service.generateReply(session, "How do I fix this API?", "Edson");

        assertThat(reply).contains("Ready to help.");
    }

    @Test
    @DisplayName("should return fallback when ChatClient is unavailable")
    void shouldReturnFallbackWhenChatClientUnavailable() {
        when(chatClientProvider.getIfAvailable()).thenReturn(null);
        DirectAgentChatService service = new DirectAgentChatService(chatClientProvider);

        Agent agent = Agent.builder()
                .name("Builder")
                .agentType(AgentType.BACKEND)
                .squad(Squad.builder().organization(Organization.builder().name("Org").slug("org").build()).build())
                .build();
        LiveSession session = LiveSession.builder().agent(agent).build();
        session.setId(10L);

        assertThat(service.generateReply(session, "Ping", "Edson"))
                .hasValueSatisfying(reply -> assertThat(reply).contains("I received your message"));
    }
}

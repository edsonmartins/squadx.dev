package dev.squadx.service;

import dev.squadx.dto.liveview.LiveSessionResponse;
import dev.squadx.dto.supabase.SupabaseLiveSession;
import dev.squadx.model.enums.LiveSessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupabaseLiveSessionServiceTest {

    @Mock
    private WebClient supabaseWebClient;

    @Mock
    private WebClient supabaseRpcClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    private SupabaseLiveSessionService service;

    @BeforeEach
    void setUp() {
        service = new SupabaseLiveSessionService(supabaseWebClient, supabaseRpcClient);
    }

    @Nested
    @DisplayName("getSessionByCode()")
    class GetSessionByCode {

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("should return session when found by join code")
        void shouldReturnSessionByCode() {
            SupabaseLiveSession session = new SupabaseLiveSession();
            session.setId("uuid-123");
            session.setJoinCode("ABCD1234");
            session.setTaskId(5L);
            session.setStatus("active");

            when(supabaseWebClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToFlux(SupabaseLiveSession.class))
                    .thenReturn(Flux.just(session));

            Optional<SupabaseLiveSession> result = service.getSessionByCode("ABCD1234");

            assertThat(result).isPresent();
            assertThat(result.get().getJoinCode()).isEqualTo("ABCD1234");
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("should return empty when no session matches code")
        void shouldReturnEmptyWhenNotFound() {
            when(supabaseWebClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToFlux(SupabaseLiveSession.class))
                    .thenReturn(Flux.empty());

            Optional<SupabaseLiveSession> result = service.getSessionByCode("ZZZZ9999");

            assertThat(result).isEmpty();
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("should return empty when WebClient throws exception")
        void shouldReturnEmptyOnException() {
            when(supabaseWebClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenThrow(
                    WebClientResponseException.create(500, "Server Error", null, null, null));

            Optional<SupabaseLiveSession> result = service.getSessionByCode("ABCD1234");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("toResponse()")
    class ToResponse {

        @Test
        @DisplayName("should map Supabase session to LiveSessionResponse with correct status")
        void shouldMapToResponse() {
            SupabaseLiveSession supabaseSession = new SupabaseLiveSession();
            supabaseSession.setJoinCode("CODE1234");
            supabaseSession.setTaskId(42L);
            supabaseSession.setStatus("active");
            supabaseSession.setMaxViewers(25);
            supabaseSession.setCreatedAt(Instant.now());

            LiveSessionResponse response = service.toResponse(supabaseSession);

            assertThat(response.getCode()).isEqualTo("CODE1234");
            assertThat(response.getTaskId()).isEqualTo(42L);
            assertThat(response.getStatus()).isEqualTo(LiveSessionStatus.ACTIVE);
            assertThat(response.getMaxViewers()).isEqualTo(25);
            assertThat(response.getCurrentViewers()).isZero();
        }

        @Test
        @DisplayName("should map null status to PENDING")
        void shouldMapNullStatusToPending() {
            SupabaseLiveSession supabaseSession = new SupabaseLiveSession();
            supabaseSession.setJoinCode("CODE0000");
            supabaseSession.setTaskId(1L);
            supabaseSession.setStatus(null);

            LiveSessionResponse response = service.toResponse(supabaseSession);

            assertThat(response.getStatus()).isEqualTo(LiveSessionStatus.PENDING);
        }
    }
}

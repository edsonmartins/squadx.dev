package dev.squadx.websocket;

import dev.squadx.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthInterceptorTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserDetailsService userDetailsService;

    @InjectMocks
    private WebSocketAuthInterceptor interceptor;

    private final UserDetails principal =
            new User("alice@example.com", "pw", Collections.emptyList());

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private Message<byte[]> connectMessage(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        // Keep headers mutable so the interceptor can setUser(), as the real inbound
        // channel does — a sealed accessor would make setUser throw.
        accessor.setLeaveMutable(true);
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void connectWithoutAuthorizationHeaderIsRejected() {
        assertThatThrownBy(() -> interceptor.preSend(connectMessage(null), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing Authorization");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void connectWithInvalidOrExpiredTokenIsRejected() {
        when(jwtService.extractUsername(anyString())).thenReturn("alice@example.com");
        when(userDetailsService.loadUserByUsername("alice@example.com")).thenReturn(principal);
        when(jwtService.isTokenValid(anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(connectMessage("Bearer bad.jwt"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired token");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void connectWithUnparseableTokenIsRejected() {
        when(jwtService.extractUsername(anyString())).thenThrow(new RuntimeException("malformed"));

        assertThatThrownBy(() -> interceptor.preSend(connectMessage("Bearer garbage"), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void connectWithValidTokenAuthenticates() {
        when(jwtService.extractUsername(anyString())).thenReturn("alice@example.com");
        when(userDetailsService.loadUserByUsername("alice@example.com")).thenReturn(principal);
        when(jwtService.isTokenValid(anyString(), any())).thenReturn(true);

        Message<?> result = interceptor.preSend(connectMessage("Bearer good.jwt"), null);

        assertThat(result).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(principal);
        assertThat(StompHeaderAccessor.wrap(result).getUser()).isNotNull();
    }

    @Test
    void nonConnectCommandPassesThroughWithoutAuth() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/whatever");
        Message<byte[]> send = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(send, null);

        assertThat(result).isSameAs(send);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}

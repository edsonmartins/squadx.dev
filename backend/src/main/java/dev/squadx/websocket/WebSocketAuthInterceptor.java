package dev.squadx.websocket;

import dev.squadx.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            List<String> authorization = accessor.getNativeHeader("Authorization");

            // Reject anonymous CONNECT: no Authorization header means no principal.
            // Previously this branch was skipped and the connection was allowed
            // through unauthenticated (threat-model #4).
            if (authorization == null || authorization.isEmpty()) {
                throw new IllegalArgumentException("Missing Authorization header on STOMP CONNECT");
            }

            String token = authorization.get(0);
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            try {
                String username = jwtService.extractUsername(token);
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // A parseable-but-invalid token (expired / wrong subject) must also
                // be rejected, not silently allowed through with no authentication.
                if (!jwtService.isTokenValid(token, userDetails)) {
                    throw new IllegalArgumentException("Invalid or expired token on STOMP CONNECT");
                }

                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
                accessor.setUser(auth);
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid token on STOMP CONNECT");
            }
        }

        return message;
    }
}

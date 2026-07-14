package dev.squadx.websocket;

import dev.squadx.model.User;
import dev.squadx.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final StompSubscriptionAuthorizer subscriptionAuthorizer;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            // Enforce per-destination org scoping so a session cannot subscribe to another
            // tenant's broadcast topic (threat-model #4). The principal was set at CONNECT.
            subscriptionAuthorizer.authorize(accessor.getDestination(), currentUser(accessor));
            return message;
        }

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

    /** Extract the authenticated {@link User} bound to the session at CONNECT, or null. */
    private User currentUser(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof User user) {
            return user;
        }
        return null;
    }
}

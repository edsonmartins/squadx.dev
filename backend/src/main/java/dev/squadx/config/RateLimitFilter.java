package dev.squadx.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Redis-backed rate limiting filter.
 * <p>
 * Limits:
 * - Login endpoint (POST /api/v1/auth/login): 5 req/min per IP
 * - Unauthenticated (auth) endpoints: 20 req/min per IP
 * - Authenticated endpoints: 100 req/min per user ID
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String RATE_LIMIT_PREFIX = "rate_limit:";
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private static final int LOGIN_LIMIT = 5;
    private static final int UNAUTHENTICATED_LIMIT = 20;
    private static final int AUTHENTICATED_LIMIT = 100;

    @Nullable
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        // Skip rate limiting for preflight requests and health/docs endpoints
        if ("OPTIONS".equalsIgnoreCase(method)
                || path.startsWith("/actuator/")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/api-docs")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        int limit;
        String key;

        // Determine which bucket this request falls into
        if (isLoginEndpoint(path, method)) {
            key = RATE_LIMIT_PREFIX + "login:" + clientIp;
            limit = LOGIN_LIMIT;
        } else if (isAuthEndpoint(path)) {
            key = RATE_LIMIT_PREFIX + "auth:" + clientIp;
            limit = UNAUTHENTICATED_LIMIT;
        } else {
            // For authenticated endpoints, check if we have a principal
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()
                    && !"anonymousUser".equals(auth.getPrincipal())) {
                String userId = auth.getName();
                key = RATE_LIMIT_PREFIX + "user:" + userId;
                limit = AUTHENTICATED_LIMIT;
            } else {
                // Fallback: unauthenticated request hitting a protected endpoint
                // (will likely be rejected by security, but still rate-limit)
                key = RATE_LIMIT_PREFIX + "ip:" + clientIp;
                limit = UNAUTHENTICATED_LIMIT;
            }
        }

        if (isRateLimited(key, limit)) {
            writeRateLimitResponse(response, limit);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Uses Redis INCR + EXPIRE for a fixed-window counter.
     * Returns true if the request should be blocked.
     */
    private boolean isRateLimited(String key, int limit) {
        if (stringRedisTemplate == null) {
            return false;
        }
        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count == null) {
                return false;
            }
            if (count == 1L) {
                stringRedisTemplate.expire(key, WINDOW);
            }
            return count > limit;
        } catch (Exception e) {
            // If Redis is unavailable, fail open (allow the request)
            log.warn("Rate limiting check failed, allowing request: {}", e.getMessage());
            return false;
        }
    }

    private void writeRateLimitResponse(HttpServletResponse response, int limit) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> body = Map.of(
                "status", 429,
                "error", "Too Many Requests",
                "message", String.format("Rate limit exceeded. Maximum %d requests per minute.", limit),
                "timestamp", Instant.now().toString()
        );

        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private boolean isLoginEndpoint(String path, String method) {
        return "POST".equalsIgnoreCase(method)
                && path.equals("/api/v1/auth/login");
    }

    private boolean isAuthEndpoint(String path) {
        return path.startsWith("/api/v1/auth/");
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // Take the first IP (original client)
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}

package dev.squadx.service;

import dev.squadx.dto.liveview.LiveSessionResponse;
import dev.squadx.dto.supabase.SupabaseLiveSession;
import dev.squadx.model.enums.LiveSessionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for interacting with Supabase live_sessions table.
 * This allows the Spring Boot backend to read sessions created by the Python client.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SupabaseLiveSessionService {

    @Qualifier("supabaseWebClient")
    private final WebClient supabaseWebClient;

    @Qualifier("supabaseRpcClient")
    private final WebClient supabaseRpcClient;

    /**
     * Get a live session by join code from Supabase.
     *
     * @param joinCode The 8-character join code
     * @return The session if found
     */
    public Optional<SupabaseLiveSession> getSessionByCode(String joinCode) {
        try {
            List<SupabaseLiveSession> sessions = supabaseWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/live_sessions")
                            .queryParam("join_code", "eq." + joinCode)
                            .queryParam("select", "*")
                            .build())
                    .retrieve()
                    .bodyToFlux(SupabaseLiveSession.class)
                    .collectList()
                    .block();

            if (sessions != null && !sessions.isEmpty()) {
                return Optional.of(sessions.get(0));
            }
            return Optional.empty();
        } catch (WebClientResponseException e) {
            log.error("Failed to get session by code from Supabase: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Get a live session by task ID from Supabase.
     *
     * @param taskId The task ID
     * @return The active session for the task if found
     */
    public Optional<SupabaseLiveSession> getActiveSessionByTaskId(Long taskId) {
        try {
            List<SupabaseLiveSession> sessions = supabaseWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/live_sessions")
                            .queryParam("task_id", "eq." + taskId)
                            .queryParam("status", "in.(created,active)")
                            .queryParam("select", "*")
                            .queryParam("order", "created_at.desc")
                            .queryParam("limit", "1")
                            .build())
                    .retrieve()
                    .bodyToFlux(SupabaseLiveSession.class)
                    .collectList()
                    .block();

            if (sessions != null && !sessions.isEmpty()) {
                return Optional.of(sessions.get(0));
            }
            return Optional.empty();
        } catch (WebClientResponseException e) {
            log.error("Failed to get session by task ID from Supabase: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Get all active sessions from Supabase.
     *
     * @return List of active sessions
     */
    public List<SupabaseLiveSession> getActiveSessions() {
        try {
            return supabaseWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/live_sessions")
                            .queryParam("status", "in.(created,active)")
                            .queryParam("select", "*")
                            .queryParam("order", "created_at.desc")
                            .build())
                    .retrieve()
                    .bodyToFlux(SupabaseLiveSession.class)
                    .collectList()
                    .block();
        } catch (WebClientResponseException e) {
            log.error("Failed to get active sessions from Supabase: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Create a new live session in Supabase using the RPC function.
     *
     * @param taskId     The task ID
     * @param hostUserId Optional host user ID
     * @param mode       Session mode (p2p or sfu)
     * @param maxViewers Maximum viewers allowed
     * @return The created session
     */
    public Optional<SupabaseLiveSession> createSession(Long taskId, String hostUserId, String mode, Integer maxViewers) {
        try {
            Map<String, Object> params = Map.of(
                    "p_task_id", taskId,
                    "p_host_user_id", hostUserId != null ? hostUserId : "",
                    "p_mode", mode != null ? mode : "p2p",
                    "p_max_viewers", maxViewers != null ? maxViewers : 25
            );

            SupabaseLiveSession session = supabaseRpcClient.post()
                    .uri("/create_live_session")
                    .bodyValue(params)
                    .retrieve()
                    .bodyToMono(SupabaseLiveSession.class)
                    .block();

            if (session != null) {
                log.info("Created Supabase session for task {}: {}", taskId, session.getJoinCode());
                return Optional.of(session);
            }
            return Optional.empty();
        } catch (WebClientResponseException e) {
            log.error("Failed to create session in Supabase: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Update session status in Supabase.
     *
     * @param sessionId The session UUID
     * @param status    New status
     * @return true if updated successfully
     */
    public boolean updateSessionStatus(String sessionId, String status) {
        try {
            supabaseWebClient.patch()
                    .uri(uriBuilder -> uriBuilder
                            .path("/live_sessions")
                            .queryParam("id", "eq." + sessionId)
                            .build())
                    .bodyValue(Map.of("status", status))
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();

            log.info("Updated Supabase session {} status to {}", sessionId, status);
            return true;
        } catch (WebClientResponseException e) {
            log.error("Failed to update session status in Supabase: {}", e.getMessage());
            return false;
        }
    }

    /**
     * End a session in Supabase.
     *
     * @param sessionId The session UUID
     * @return true if ended successfully
     */
    public boolean endSession(String sessionId) {
        return updateSessionStatus(sessionId, "ended");
    }

    /**
     * Convert Supabase session to LiveSessionResponse DTO.
     *
     * @param supabaseSession The Supabase session
     * @return LiveSessionResponse for API responses
     */
    public LiveSessionResponse toResponse(SupabaseLiveSession supabaseSession) {
        LiveSessionStatus status = mapStatus(supabaseSession.getStatus());

        return LiveSessionResponse.builder()
                .code(supabaseSession.getJoinCode())
                .taskId(supabaseSession.getTaskId())
                .status(status)
                .maxViewers(supabaseSession.getMaxViewers())
                .currentViewers(0) // Would need to query participants
                .createdAt(supabaseSession.getCreatedAt())
                .build();
    }

    /**
     * Map Supabase status string to LiveSessionStatus enum.
     */
    private LiveSessionStatus mapStatus(String status) {
        if (status == null) return LiveSessionStatus.PENDING;
        return switch (status.toLowerCase()) {
            case "active" -> LiveSessionStatus.ACTIVE;
            case "paused" -> LiveSessionStatus.PAUSED;
            case "ended" -> LiveSessionStatus.ENDED;
            default -> LiveSessionStatus.PENDING;
        };
    }
}

package dev.squadx.service;

import dev.squadx.dto.liveview.JoinSessionRequest;
import dev.squadx.dto.liveview.LiveChatMessageRequest;
import dev.squadx.dto.liveview.LiveChatMessageResponse;
import dev.squadx.dto.liveview.LiveSessionRequest;
import dev.squadx.dto.liveview.LiveSessionResponse;
import dev.squadx.dto.liveview.ParticipantResponse;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.integration.SquadxLiveClient;
import dev.squadx.model.*;
import dev.squadx.model.enums.LiveSessionStatus;
import dev.squadx.repository.AgentRepository;
import dev.squadx.repository.LiveSessionParticipantRepository;
import dev.squadx.repository.LiveSessionRepository;
import dev.squadx.repository.OrganizationMemberRepository;
import dev.squadx.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveViewService {

    private final LiveSessionRepository liveSessionRepository;
    private final LiveSessionParticipantRepository participantRepository;
    private final TaskRepository taskRepository;
    private final AgentRepository agentRepository;
    private final OrganizationMemberRepository orgMemberRepository;
    private final WebSocketEventService webSocketEventService;
    private final SquadxLiveClient squadxLiveClient;

    @Value("${squadx.live.base-url:https://live.squadx.dev}")
    private String liveBaseUrl;

    private static final String CODE_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom random = new SecureRandom();

    @Transactional
    public LiveSessionResponse createSession(LiveSessionRequest request, User user) {
        Task task = taskRepository.findById(request.getTaskId())
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        // Check if user has access to the task's organization
        Long organizationId = task.getProject().getOrganization().getId();
        if (!hasOrganizationAccess(user, organizationId)) {
            throw new ForbiddenException("You don't have access to this task");
        }

        // Check if there's already an active session for this task
        liveSessionRepository.findByTaskIdAndStatus(request.getTaskId(), LiveSessionStatus.ACTIVE)
                .ifPresent(session -> {
                    throw new BadRequestException("Task already has an active live session");
                });

        String sessionCode = generateUniqueCode();

        LiveSession session = new LiveSession();
        session.setCode(sessionCode);
        session.setTask(task);
        session.setHostUser(user);
        session.setContainerId(request.getContainerId());
        session.setMaxViewers(request.getMaxViewers());
        session.setResolution(request.getResolution());
        session.setStatus(LiveSessionStatus.PENDING);

        session = liveSessionRepository.save(session);

        // Add host as participant
        LiveSessionParticipant hostParticipant = new LiveSessionParticipant();
        hostParticipant.setSession(session);
        hostParticipant.setUser(user);
        hostParticipant.setIsHost(true);
        hostParticipant.setCanControl(true);
        participantRepository.save(hostParticipant);

        log.info("Live session created: {} for task: {} by user: {}",
                sessionCode, task.getId(), user.getEmail());

        return mapToResponse(session);
    }

    @Transactional
    public LiveSessionResponse ensureDirectAgentSession(Long agentId, User user) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent not found"));

        Long organizationId = agent.getSquad().getOrganization().getId();
        if (!hasOrganizationAccess(user, organizationId)) {
            throw new ForbiddenException("You don't have access to this agent");
        }

        LiveSession existing = liveSessionRepository.findByAgentIdAndStatus(agentId, LiveSessionStatus.ACTIVE)
                .orElse(null);
        if (existing != null) {
            hydrateExternalSession(existing);
            ensureExternalAgentParticipantIfAvailable(existing);
            existing = liveSessionRepository.save(existing);
            return mapToResponse(existing);
        }

        LiveSession session = new LiveSession();
        session.setCode(generateUniqueCode());
        session.setAgent(agent);
        session.setHostUser(user);
        session.setMaxViewers(25);
        session.setResolution("1280x720");
        session.setStatus(LiveSessionStatus.ACTIVE);
        session = liveSessionRepository.save(session);

        LiveSessionParticipant hostParticipant = new LiveSessionParticipant();
        hostParticipant.setSession(session);
        hostParticipant.setUser(user);
        hostParticipant.setIsHost(true);
        hostParticipant.setCanControl(true);
        participantRepository.save(hostParticipant);

        hydrateExternalSession(session);
        ensureExternalAgentParticipantIfAvailable(session);
        session = liveSessionRepository.save(session);

        webSocketEventService.sendLiveSessionStarted(session.getCode(), session.getId());
        log.info("Direct agent session ensured: {} for agent {}", session.getCode(), agent.getId());

        return mapToResponse(session);
    }

    @Transactional
    public LiveSessionResponse joinSession(JoinSessionRequest request, User user) {
        LiveSession session = liveSessionRepository.findByCodeAndStatus(request.getCode(), LiveSessionStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Active session not found with code: " + request.getCode()));

        // Check if user has access to the task's organization
        Long organizationId = getSessionOrganizationId(session);
        if (!hasOrganizationAccess(user, organizationId)) {
            throw new ForbiddenException("You don't have access to this session");
        }

        // Check if session is full
        long currentViewers = participantRepository.countBySessionIdAndLeftAtIsNull(session.getId());
        if (currentViewers >= session.getMaxViewers()) {
            throw new BadRequestException("Session is full");
        }

        // Check if user is already a participant
        LiveSessionParticipant existingParticipant = participantRepository
                .findBySessionIdAndUserIdAndLeftAtIsNull(session.getId(), user.getId())
                .orElse(null);

        if (existingParticipant != null) {
            // User already in session, return current state
            return mapToResponse(session);
        }

        // Add user as participant
        LiveSessionParticipant participant = new LiveSessionParticipant();
        participant.setSession(session);
        participant.setUser(user);
        participant.setIsHost(false);
        participant.setCanControl(false);
        participantRepository.save(participant);

        // Notify via WebSocket
        webSocketEventService.sendParticipantJoined(
                session.getCode(),
                user.getId(),
                user.getFullName(),
                (int) currentViewers + 1
        );

        log.info("User {} joined live session: {}", user.getEmail(), session.getCode());

        return mapToResponse(session);
    }

    @Transactional
    public void leaveSession(String code, User user) {
        LiveSession session = liveSessionRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        LiveSessionParticipant participant = participantRepository
                .findBySessionIdAndUserIdAndLeftAtIsNull(session.getId(), user.getId())
                .orElseThrow(() -> new BadRequestException("You are not in this session"));

        participant.setLeftAt(LocalDateTime.now());
        participantRepository.save(participant);

        // Count remaining viewers
        long remainingViewers = participantRepository.countBySessionIdAndLeftAtIsNull(session.getId());

        // Notify via WebSocket
        webSocketEventService.sendParticipantLeft(
                session.getCode(),
                user.getId(),
                user.getFullName(),
                (int) remainingViewers
        );

        log.info("User {} left live session: {}", user.getEmail(), session.getCode());
    }

    @Transactional
    public LiveSessionResponse startSession(Long sessionId, User user) {
        LiveSession session = liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        if (!session.getHostUser().getId().equals(user.getId())) {
            throw new ForbiddenException("Only the host can start the session");
        }

        if (session.getStatus() != LiveSessionStatus.PENDING) {
            throw new BadRequestException("Session is not in pending status");
        }

        hydrateExternalSession(session);
        session.setStatus(LiveSessionStatus.ACTIVE);
        ensureExternalAgentParticipantIfAvailable(session);
        session = liveSessionRepository.save(session);

        // Notify via WebSocket
        webSocketEventService.sendLiveSessionStarted(session.getCode(), session.getId());

        log.info("Live session started: {}", session.getCode());

        return mapToResponse(session);
    }

    @Transactional
    public LiveChatMessageResponse sendAgentMessage(Long sessionId, LiveChatMessageRequest request, User user) {
        LiveSession session = getManagedSession(sessionId, user);
        ensureSessionActive(session);
        ensureExternalSessionAvailable(session);

        String participantId = ensureExternalAgentParticipant(session);
        Map<String, Object> message = squadxLiveClient.sendChatMessage(
                session.getExternalSessionId(),
                request.getContent(),
                participantId,
                request.getRecipientId()
        );

        if (message.isEmpty()) {
            throw new BadRequestException("Failed to send message to SquadX Live");
        }

        liveSessionRepository.save(session);
        return mapChatMessage(message);
    }

    @Transactional(readOnly = true)
    public List<LiveChatMessageResponse> getChatHistory(Long sessionId, int limit, String before, String recipientId, User user) {
        LiveSession session = getManagedSession(sessionId, user);
        ensureExternalSessionAvailable(session);

        return squadxLiveClient.getChatHistory(session.getExternalSessionId(), limit, before, recipientId)
                .stream()
                .map(this::mapChatMessage)
                .toList();
    }

    @Transactional
    public LiveSessionResponse endSession(Long sessionId, User user) {
        LiveSession session = liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        if (!session.getHostUser().getId().equals(user.getId())) {
            throw new ForbiddenException("Only the host can end the session");
        }

        if (session.getExternalSessionId() != null && !session.getExternalSessionId().isBlank()) {
            squadxLiveClient.endSession(session.getExternalSessionId());
        }

        session.setStatus(LiveSessionStatus.ENDED);
        session.setEndedAt(LocalDateTime.now());
        session = liveSessionRepository.save(session);

        // Mark all participants as left
        List<LiveSessionParticipant> activeParticipants = participantRepository
                .findBySessionIdAndLeftAtIsNull(session.getId());
        for (LiveSessionParticipant participant : activeParticipants) {
            participant.setLeftAt(LocalDateTime.now());
            participantRepository.save(participant);
        }

        // Notify via WebSocket
        webSocketEventService.sendLiveSessionEnded(session.getCode(), session.getId());

        log.info("Live session ended: {}", session.getCode());

        return mapToResponse(session);
    }

    @Transactional
    public LiveSessionResponse grantControl(Long sessionId, Long userId, User host) {
        LiveSession session = liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        if (!session.getHostUser().getId().equals(host.getId())) {
            throw new ForbiddenException("Only the host can grant control");
        }

        LiveSessionParticipant participant = participantRepository
                .findBySessionIdAndUserIdAndLeftAtIsNull(session.getId(), userId)
                .orElseThrow(() -> new BadRequestException("Participant not found in session"));

        participant.setCanControl(true);
        participantRepository.save(participant);

        // Notify via WebSocket
        webSocketEventService.sendControlGranted(session.getCode(), userId);

        return mapToResponse(session);
    }

    @Transactional
    public LiveSessionResponse revokeControl(Long sessionId, Long userId, User host) {
        LiveSession session = liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        if (!session.getHostUser().getId().equals(host.getId())) {
            throw new ForbiddenException("Only the host can revoke control");
        }

        LiveSessionParticipant participant = participantRepository
                .findBySessionIdAndUserIdAndLeftAtIsNull(session.getId(), userId)
                .orElseThrow(() -> new BadRequestException("Participant not found in session"));

        participant.setCanControl(false);
        participantRepository.save(participant);

        // Notify via WebSocket
        webSocketEventService.sendControlRevoked(session.getCode(), userId);

        return mapToResponse(session);
    }

    @Transactional(readOnly = true)
    public LiveSessionResponse getByCode(String code, User user) {
        LiveSession session = liveSessionRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        Long organizationId = getSessionOrganizationId(session);
        if (!hasOrganizationAccess(user, organizationId)) {
            throw new ForbiddenException("You don't have access to this session");
        }

        return mapToResponse(session);
    }

    @Transactional(readOnly = true)
    public LiveSessionResponse getByTaskId(Long taskId, User user) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        Long organizationId = task.getProject().getOrganization().getId();
        if (!hasOrganizationAccess(user, organizationId)) {
            throw new ForbiddenException("You don't have access to this task");
        }

        LiveSession session = liveSessionRepository.findByTaskIdAndStatus(taskId, LiveSessionStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("No active session for this task"));

        return mapToResponse(session);
    }

    @Transactional(readOnly = true)
    public List<LiveSessionResponse> getActiveSessionsByOrganization(Long organizationId, User user) {
        if (!hasOrganizationAccess(user, organizationId)) {
            throw new ForbiddenException("You don't have access to this organization");
        }

        return liveSessionRepository.findActiveByOrganizationId(organizationId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private boolean hasOrganizationAccess(User user, Long organizationId) {
        return orgMemberRepository.existsByOrganizationIdAndUserId(organizationId, user.getId());
    }

    private String generateUniqueCode() {
        String code;
        do {
            code = generateCode();
        } while (liveSessionRepository.existsByCode(code));
        return code;
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARACTERS.charAt(random.nextInt(CODE_CHARACTERS.length())));
        }
        return sb.toString();
    }

    private String getViewerUrl(String code) {
        return liveBaseUrl + "/" + code;
    }

    private String getHostUrl(String code) {
        return liveBaseUrl + "/host/" + code;
    }

    private void hydrateExternalSession(LiveSession session) {
        if (session.getExternalSessionId() != null && !session.getExternalSessionId().isBlank()) {
            return;
        }

        Long taskId = session.getTask() != null ? session.getTask().getId() : null;
        Agent linkedAgent = getSessionAgent(session);
        Long agentId = linkedAgent != null ? linkedAgent.getId() : null;

        Map<String, String> externalSession = squadxLiveClient.createSession(taskId, agentId, "p2p");
        if (externalSession == null || externalSession.isEmpty()) {
            return;
        }

        session.setExternalSessionId(blankToNull(externalSession.get("sessionId")));
        session.setExternalJoinCode(blankToNull(externalSession.get("joinCode")));
        session.setExternalJoinUrl(blankToNull(externalSession.get("joinUrl")));
    }

    private String ensureExternalAgentParticipant(LiveSession session) {
        if (session.getExternalAgentParticipantId() != null && !session.getExternalAgentParticipantId().isBlank()) {
            return session.getExternalAgentParticipantId();
        }

        Agent agent = getSessionAgent(session);
        if (agent == null) {
            throw new BadRequestException("Session has no linked agent for live communication");
        }

        String joinCode = session.getExternalJoinCode();
        if (joinCode == null || joinCode.isBlank()) {
            throw new BadRequestException("Live session has no external join code");
        }

        String displayName = "Agent " + agent.getName();
        Map<String, Object> participant = squadxLiveClient.joinSession(joinCode, displayName);
        String participantId = blankToNull(stringValue(participant.get("id")));
        if (participantId == null) {
            throw new BadRequestException("Failed to register agent in SquadX Live session");
        }

        session.setExternalAgentParticipantId(participantId);
        session.setExternalAgentDisplayName(displayName);
        return participantId;
    }

    private void ensureExternalAgentParticipantIfAvailable(LiveSession session) {
        if (getSessionAgent(session) == null) {
            return;
        }

        try {
            ensureExternalAgentParticipant(session);
        } catch (BadRequestException e) {
            log.warn("Failed to provision external agent participant for session {}: {}", session.getId(), e.getMessage());
        }
    }

    private LiveSession getManagedSession(Long sessionId, User user) {
        LiveSession session = liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        Long organizationId = getSessionOrganizationId(session);
        if (!hasOrganizationAccess(user, organizationId)) {
            throw new ForbiddenException("You don't have access to this session");
        }
        return session;
    }

    private void ensureSessionActive(LiveSession session) {
        if (session.getStatus() != LiveSessionStatus.ACTIVE) {
            throw new BadRequestException("Live session is not active");
        }
    }

    private void ensureExternalSessionAvailable(LiveSession session) {
        if (session.getExternalSessionId() == null || session.getExternalSessionId().isBlank()) {
            throw new BadRequestException("Session is not linked to SquadX Live");
        }
    }

    private LiveSessionResponse mapToResponse(LiveSession session) {
        List<ParticipantResponse> participants = participantRepository
                .findBySessionIdAndLeftAtIsNull(session.getId())
                .stream()
                .map(this::mapParticipant)
                .toList();

        long currentViewers = participants.size();

        return LiveSessionResponse.builder()
                .id(session.getId())
                .code(session.getCode())
                .taskId(session.getTask() != null ? session.getTask().getId() : null)
                .taskTitle(session.getTask() != null ? session.getTask().getTitle() : null)
                .agentId(session.getAgent() != null ? session.getAgent().getId() : getAgentIdFromTask(session))
                .agentName(resolveAgentName(session))
                .sessionMode(session.getTask() != null ? "TASK" : "DIRECT_AGENT")
                .hostUserId(session.getHostUser().getId())
                .hostUserName(session.getHostUser().getFullName())
                .containerId(session.getContainerId())
                .status(session.getStatus())
                .maxViewers(session.getMaxViewers())
                .currentViewers((int) currentViewers)
                .resolution(session.getResolution())
                .viewerUrl(resolveViewerUrl(session))
                .hostUrl(resolveHostUrl(session))
                .externalSessionId(session.getExternalSessionId())
                .externalJoinCode(session.getExternalJoinCode())
                .externalJoinUrl(session.getExternalJoinUrl())
                .externalAgentParticipantId(session.getExternalAgentParticipantId())
                .externalAgentDisplayName(session.getExternalAgentDisplayName())
                .participants(participants)
                .createdAt(session.getCreatedAt())
                .endedAt(session.getEndedAt())
                .build();
    }

    private Long getSessionOrganizationId(LiveSession session) {
        if (session.getTask() != null) {
            return session.getTask().getProject().getOrganization().getId();
        }
        if (session.getAgent() != null) {
            return session.getAgent().getSquad().getOrganization().getId();
        }
        throw new BadRequestException("Session is not linked to a task or agent");
    }

    private Agent getSessionAgent(LiveSession session) {
        if (session.getAgent() != null) {
            return session.getAgent();
        }
        return session.getTask() != null ? session.getTask().getAssignedAgent() : null;
    }

    private Long getAgentIdFromTask(LiveSession session) {
        Agent agent = getSessionAgent(session);
        return agent != null ? agent.getId() : null;
    }

    private String resolveAgentName(LiveSession session) {
        Agent agent = getSessionAgent(session);
        return agent != null ? agent.getName() : null;
    }

    private String resolveViewerUrl(LiveSession session) {
        if (session.getExternalJoinUrl() != null && !session.getExternalJoinUrl().isBlank()) {
            return session.getExternalJoinUrl();
        }
        return getViewerUrl(session.getCode());
    }

    private String resolveHostUrl(LiveSession session) {
        if (session.getExternalJoinUrl() != null && !session.getExternalJoinUrl().isBlank()) {
            return session.getExternalJoinUrl();
        }
        return getHostUrl(session.getCode());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private LiveChatMessageResponse mapChatMessage(Map<String, Object> message) {
        return LiveChatMessageResponse.builder()
                .id(stringValue(message.get("id")))
                .sessionId(stringValue(message.getOrDefault("session_id", message.get("sessionId"))))
                .participantId(stringValue(message.getOrDefault("participant_id", message.get("participantId"))))
                .displayName(stringValue(message.getOrDefault("display_name", message.get("displayName"))))
                .content(stringValue(message.get("content")))
                .messageType(stringValue(message.getOrDefault("message_type", message.get("messageType"))))
                .recipientId(stringValue(message.getOrDefault("recipient_id", message.get("recipientId"))))
                .createdAt(parseInstant(message.getOrDefault("created_at", message.get("createdAt"))))
                .build();
    }

    private Instant parseInstant(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private ParticipantResponse mapParticipant(LiveSessionParticipant participant) {
        return ParticipantResponse.builder()
                .id(participant.getId())
                .userId(participant.getUser().getId())
                .userName(participant.getUser().getFullName())
                .userEmail(participant.getUser().getEmail())
                .canControl(participant.getCanControl())
                .isHost(participant.getIsHost())
                .joinedAt(participant.getJoinedAt())
                .leftAt(participant.getLeftAt())
                .build();
    }
}

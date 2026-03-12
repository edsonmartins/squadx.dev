package dev.squadx.service;

import dev.squadx.dto.liveview.JoinSessionRequest;
import dev.squadx.dto.liveview.LiveSessionRequest;
import dev.squadx.dto.liveview.LiveSessionResponse;
import dev.squadx.dto.liveview.ParticipantResponse;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.*;
import dev.squadx.model.enums.LiveSessionStatus;
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
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveViewService {

    private final LiveSessionRepository liveSessionRepository;
    private final LiveSessionParticipantRepository participantRepository;
    private final TaskRepository taskRepository;
    private final OrganizationMemberRepository orgMemberRepository;
    private final WebSocketEventService webSocketEventService;

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
    public LiveSessionResponse joinSession(JoinSessionRequest request, User user) {
        LiveSession session = liveSessionRepository.findByCodeAndStatus(request.getCode(), LiveSessionStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Active session not found with code: " + request.getCode()));

        // Check if user has access to the task's organization
        Long organizationId = session.getTask().getProject().getOrganization().getId();
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

        session.setStatus(LiveSessionStatus.ACTIVE);
        session = liveSessionRepository.save(session);

        // Notify via WebSocket
        webSocketEventService.sendLiveSessionStarted(session.getCode(), session.getId());

        log.info("Live session started: {}", session.getCode());

        return mapToResponse(session);
    }

    @Transactional
    public LiveSessionResponse endSession(Long sessionId, User user) {
        LiveSession session = liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        if (!session.getHostUser().getId().equals(user.getId())) {
            throw new ForbiddenException("Only the host can end the session");
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

    public LiveSessionResponse getByCode(String code, User user) {
        LiveSession session = liveSessionRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        Long organizationId = session.getTask().getProject().getOrganization().getId();
        if (!hasOrganizationAccess(user, organizationId)) {
            throw new ForbiddenException("You don't have access to this session");
        }

        return mapToResponse(session);
    }

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
                .taskId(session.getTask().getId())
                .taskTitle(session.getTask().getTitle())
                .hostUserId(session.getHostUser().getId())
                .hostUserName(session.getHostUser().getFullName())
                .containerId(session.getContainerId())
                .status(session.getStatus())
                .maxViewers(session.getMaxViewers())
                .currentViewers((int) currentViewers)
                .resolution(session.getResolution())
                .viewerUrl(getViewerUrl(session.getCode()))
                .hostUrl(getHostUrl(session.getCode()))
                .participants(participants)
                .createdAt(session.getCreatedAt())
                .endedAt(session.getEndedAt())
                .build();
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

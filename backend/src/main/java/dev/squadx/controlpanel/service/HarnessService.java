package dev.squadx.controlpanel.service;

import dev.squadx.controlpanel.dto.harness.HarnessRequest;
import dev.squadx.controlpanel.dto.harness.HarnessResponse;
import dev.squadx.controlpanel.model.Harness;
import dev.squadx.controlpanel.model.enums.HarnessStatus;
import dev.squadx.controlpanel.repository.HarnessRepository;
import dev.squadx.exception.BadRequestException;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.Agent;
import dev.squadx.model.Organization;
import dev.squadx.model.User;
import dev.squadx.repository.AgentRepository;
import dev.squadx.repository.OrganizationMemberRepository;
import dev.squadx.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Cadastro de harnesses e seleção de modelo LLM (harness-connectors, ADR-0003). Org-scoped.
 */
@Service
@RequiredArgsConstructor
public class HarnessService {

    private final HarnessRepository harnessRepository;
    private final OrganizationRepository organizationRepository;
    private final AgentRepository agentRepository;
    private final OrganizationMemberRepository memberRepository;

    /** TTL da sessão MCP — "conectado" = última sessão dentro deste intervalo. */
    @org.springframework.beans.factory.annotation.Value("${squadx.workspace.session-ttl-seconds:3600}")
    private long sessionTtlSeconds;

    @Transactional
    public HarnessResponse register(HarnessRequest request, User currentUser) {
        validateUserAccess(request.getOrganizationId(), currentUser);
        if (harnessRepository.existsByOrganizationIdAndKey(request.getOrganizationId(), request.getKey())) {
            throw new BadRequestException("A harness with key '" + request.getKey() + "' already exists");
        }
        Organization org = organizationRepository.findById(request.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));

        Harness harness = Harness.builder()
                .organization(org)
                .key(request.getKey())
                .name(request.getName())
                .vendor(request.getVendor())
                .status(HarnessStatus.AVAILABLE)
                .models(request.getModels() != null ? new ArrayList<>(request.getModels()) : new ArrayList<>())
                .agent(resolveAgent(request.getAgentId()))
                .build();

        return mapToResponse(harnessRepository.save(harness));
    }

    @Transactional(readOnly = true)
    public List<HarnessResponse> list(Long organizationId, User currentUser) {
        validateUserAccess(organizationId, currentUser);
        return harnessRepository.findByOrganizationId(organizationId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public HarnessResponse selectModel(Long harnessId, String model, User currentUser) {
        Harness harness = harnessRepository.findById(harnessId)
                .orElseThrow(() -> new ResourceNotFoundException("Harness not found"));
        validateUserAccess(harness.getOrganization().getId(), currentUser);
        if (!harness.getModels().contains(model)) {
            throw new BadRequestException("Model '" + model + "' is not available for this harness");
        }
        harness.setModel(model);
        return mapToResponse(harnessRepository.save(harness));
    }

    /** Modelo LLM resolvido para um agente via o harness ao qual está ligado (R2). */
    @Transactional(readOnly = true)
    public Optional<String> resolveModelForAgent(Long agentId) {
        return harnessRepository.findByAgentId(agentId).map(Harness::getModel);
    }

    /**
     * Handshake vivo: marca a última conexão do harness (chamado ao abrir uma sessão MCP do
     * workspace com {@code harness_key}). Silencioso se o harness não estiver cadastrado.
     */
    @Transactional
    public void touchConnection(Long organizationId, String harnessKey) {
        if (harnessKey == null || harnessKey.isBlank()) {
            return;
        }
        harnessRepository.findByOrganizationIdAndKey(organizationId, harnessKey).ifPresent(h -> {
            h.setLastConnectedAt(java.time.Instant.now());
            harnessRepository.save(h);
        });
    }

    private Agent resolveAgent(Long agentId) {
        if (agentId == null) {
            return null;
        }
        return agentRepository.findById(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent not found"));
    }

    private void validateUserAccess(Long organizationId, User currentUser) {
        if (!memberRepository.existsByOrganizationIdAndUserId(organizationId, currentUser.getId())) {
            throw new ForbiddenException("User does not have access to this organization");
        }
    }

    /** Status efetivo: CONNECTED enquanto a última sessão estiver dentro do TTL (derivado, sem job). */
    private HarnessStatus effectiveStatus(Harness harness) {
        java.time.Instant last = harness.getLastConnectedAt();
        if (last != null && last.isAfter(java.time.Instant.now().minusSeconds(sessionTtlSeconds))) {
            return HarnessStatus.CONNECTED;
        }
        return harness.getStatus();
    }

    private HarnessResponse mapToResponse(Harness harness) {
        return HarnessResponse.builder()
                .id(harness.getId())
                .key(harness.getKey())
                .name(harness.getName())
                .vendor(harness.getVendor())
                .status(effectiveStatus(harness))
                .lastConnectedAt(harness.getLastConnectedAt())
                .model(harness.getModel())
                .models(new ArrayList<>(harness.getModels()))
                .organizationId(harness.getOrganization().getId())
                .agentId(harness.getAgent() != null ? harness.getAgent().getId() : null)
                .agentName(harness.getAgent() != null ? harness.getAgent().getName() : null)
                .createdAt(harness.getCreatedAt())
                .updatedAt(harness.getUpdatedAt())
                .build();
    }
}

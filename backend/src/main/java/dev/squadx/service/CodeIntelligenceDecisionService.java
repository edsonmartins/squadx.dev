package dev.squadx.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.squadx.dto.intelligence.*;
import dev.squadx.exception.*;
import dev.squadx.integration.BrainSentryClient;
import dev.squadx.model.*;
import dev.squadx.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Map;

/** Human-gated decision candidates; this service never mutates code or review verdicts. */
@Service @RequiredArgsConstructor
public class CodeIntelligenceDecisionService {
    private final CodeIntelligenceSnapshotRepository snapshots;
    private final CodeIntelligenceDecisionRepository decisions;
    private final OrganizationMemberRepository members;
    private final BrainSentryClient brainSentry;
    private final ObjectMapper mapper;

    @Transactional
    public DecisionCandidateResponse propose(DecisionCandidateRequest request, User user) {
        CodeIntelligenceSnapshot snapshot = accessibleSnapshot(request.snapshotId(), user);
        CodeIntelligenceDecision decision = decisions.save(CodeIntelligenceDecision.builder()
                .snapshot(snapshot).title(request.title()).rationale(request.rationale())
                .evidenceJson(request.evidenceJson()).status("PENDING").build());
        Map<String, Object> memory = brainSentry.createMemory(snapshot.getOrganization().getId(), Map.of(
                "content", request.rationale(), "summary", request.title(), "category", "DECISION_CANDIDATE",
                "importance", "IMPORTANT", "memoryType", "SEMANTIC",
                "tags", java.util.List.of("squadx", "code-intelligence", "pending-approval"),
                "metadata", Map.of("decisionId", decision.getId(), "snapshotId", snapshot.getId(),
                        "revision", snapshot.getRevision(), "provider", snapshot.getProvider(), "evidence", request.evidenceJson()),
                "sourceType", "squadx-code-intelligence", "sourceReference", "decision:" + decision.getId()));
        Object memoryId = memory.get("id");
        if (memoryId != null) { decision.setBrainsentryMemoryId(String.valueOf(memoryId)); decision = decisions.save(decision); }
        return map(decision);
    }

    @Transactional
    public DecisionCandidateResponse review(Long id, DecisionReviewRequest request, User user) {
        CodeIntelligenceDecision decision = decisions.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Decision candidate not found"));
        if (!members.existsByOrganizationIdAndUserId(decision.getSnapshot().getOrganization().getId(), user.getId()))
            throw new ForbiddenException("User does not have access to this organization");
        String status = request.decision().trim().toUpperCase();
        if (!status.equals("APPROVED") && !status.equals("REJECTED")) throw new BadRequestException("Decision must be APPROVED or REJECTED");
        if (!decision.getStatus().equals("PENDING")) throw new BadRequestException("Decision candidate was already reviewed");
        decision.setStatus(status); decision.setReviewedBy(user.getId()); decision.setReviewedAt(Instant.now());
        return map(decisions.save(decision));
    }

    private CodeIntelligenceSnapshot accessibleSnapshot(Long id, User user) {
        CodeIntelligenceSnapshot s = snapshots.findById(id).orElseThrow(() -> new ResourceNotFoundException("Code intelligence snapshot not found"));
        if (!members.existsByOrganizationIdAndUserId(s.getOrganization().getId(), user.getId())) throw new ForbiddenException("User does not have access to this organization");
        return s;
    }
    private DecisionCandidateResponse map(CodeIntelligenceDecision d) { return new DecisionCandidateResponse(d.getId(), d.getSnapshot().getId(), d.getTitle(), d.getRationale(), d.getEvidenceJson(), d.getStatus(), d.getBrainsentryMemoryId(), d.getReviewedBy(), d.getReviewedAt()); }
}

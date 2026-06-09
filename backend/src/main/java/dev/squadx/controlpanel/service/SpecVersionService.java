package dev.squadx.controlpanel.service;

import dev.squadx.controlpanel.dto.version.SpecVersionResponse;
import dev.squadx.controlpanel.model.Change;
import dev.squadx.controlpanel.model.SpecVersion;
import dev.squadx.controlpanel.repository.ChangeRepository;
import dev.squadx.controlpanel.repository.SpecVersionRepository;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.User;
import dev.squadx.repository.OrganizationMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Versionamento semântico da spec de uma mudança (ADR-0001, RFC-0002 §1). O Control Panel é dono
 * da autoria/versionamento; cada versão aprovada será materializada no Git.
 */
@Service
@RequiredArgsConstructor
public class SpecVersionService {

    private final SpecVersionRepository specVersionRepository;
    private final ChangeRepository changeRepository;
    private final OrganizationMemberRepository memberRepository;

    @Transactional
    public SpecVersionResponse createVersion(Long changeId, String summary, User currentUser) {
        Change change = loadForUser(changeId, currentUser);

        int next = specVersionRepository.findTopByChangeIdOrderByVersionDesc(changeId)
                .map(v -> v.getVersion() + 1).orElse(1);

        specVersionRepository.findByChangeIdAndCurrentTrue(changeId).ifPresent(prev -> {
            prev.setCurrent(false);
            specVersionRepository.save(prev);
        });

        SpecVersion version = specVersionRepository.save(SpecVersion.builder()
                .change(change)
                .version(next)
                .current(true)
                .summary(summary)
                .author(currentUser)
                .build());

        return mapToResponse(version);
    }

    @Transactional(readOnly = true)
    public List<SpecVersionResponse> getHistory(Long changeId, User currentUser) {
        loadForUser(changeId, currentUser);
        return specVersionRepository.findByChangeIdOrderByVersionDesc(changeId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private Change loadForUser(Long changeId, User currentUser) {
        Change change = changeRepository.findById(changeId)
                .orElseThrow(() -> new ResourceNotFoundException("Change not found"));
        Long orgId = change.getProject().getOrganization().getId();
        if (!memberRepository.existsByOrganizationIdAndUserId(orgId, currentUser.getId())) {
            throw new ForbiddenException("User does not have access to this organization");
        }
        return change;
    }

    private SpecVersionResponse mapToResponse(SpecVersion v) {
        return SpecVersionResponse.builder()
                .id(v.getId())
                .version(v.getVersion())
                .current(v.isCurrent())
                .summary(v.getSummary())
                .commit(v.getCommit())
                .changeId(v.getChange().getId())
                .authorId(v.getAuthor() != null ? v.getAuthor().getId() : null)
                .authorName(v.getAuthor() != null ? v.getAuthor().getFullName() : null)
                .createdAt(v.getCreatedAt())
                .build();
    }
}

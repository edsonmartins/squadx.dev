package dev.squadx.controlpanel.service;

import dev.squadx.controlpanel.model.Change;
import dev.squadx.controlpanel.model.SpecVersion;
import dev.squadx.controlpanel.repository.ChangeRepository;
import dev.squadx.controlpanel.repository.SpecVersionRepository;
import dev.squadx.exception.ResourceNotFoundException;
import dev.squadx.model.User;
import dev.squadx.repository.OrganizationMemberRepository;
import dev.squadx.exception.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Owns semantic spec history; Git commit is attached later by materialization. */
@Service
@RequiredArgsConstructor
public class SpecVersionService {
    private final SpecVersionRepository versionRepository;
    private final ChangeRepository changeRepository;
    private final OrganizationMemberRepository memberRepository;

    @Transactional(readOnly = true)
    public List<SpecVersion> history(Long changeId) {
        return versionRepository.findByChangeIdOrderByCreatedAtDesc(changeId);
    }

    @Transactional(readOnly = true)
    public List<dev.squadx.controlpanel.dto.change.SpecVersionResponse> historyForUser(Long changeId, User user) {
        Change change = changeRepository.findById(changeId)
                .orElseThrow(() -> new ResourceNotFoundException("Change not found"));
        if (user == null || !memberRepository.existsByOrganizationIdAndUserId(
                change.getProject().getOrganization().getId(), user.getId())) {
            throw new ForbiddenException("User does not have access to this organization");
        }
        return history(changeId).stream().map(version -> dev.squadx.controlpanel.dto.change.SpecVersionResponse.builder()
                .id(version.getId()).version(version.getVersion()).current(version.isCurrent())
                .summary(version.getSummary()).authorId(version.getAuthor() != null ? version.getAuthor().getId() : null)
                .commitSha(version.getCommitSha()).createdAt(version.getCreatedAt()).build()).toList();
    }

    @Transactional
    public SpecVersion createCurrent(Long changeId, String version, String summary, User author) {
        Change change = changeRepository.findById(changeId)
                .orElseThrow(() -> new ResourceNotFoundException("Change not found"));
        versionRepository.findFirstByChangeIdAndCurrentTrue(changeId).ifPresent(current -> {
            current.setCurrent(false);
            versionRepository.save(current);
        });
        return versionRepository.save(SpecVersion.builder().change(change).version(version)
                .summary(summary).author(author).current(true).build());
    }
}

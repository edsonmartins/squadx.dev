package dev.squadx.service;

import dev.squadx.dto.intelligence.SearchCodeRequest;
import dev.squadx.intelligence.CodeIntelligenceModels.*;
import dev.squadx.intelligence.CodeIntelligenceProvider;
import dev.squadx.intelligence.CodeIntelligenceProviderRegistry;
import dev.squadx.model.*;
import dev.squadx.model.enums.IntelligenceSnapshotStatus;
import dev.squadx.repository.CodeIntelligenceSnapshotRepository;
import dev.squadx.repository.OrganizationMemberRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CodeIntelligenceQueryServiceTest {

    @Test
    void searchesOnlyReadyAccessibleSnapshotThroughItsProvider() {
        var snapshots = mock(CodeIntelligenceSnapshotRepository.class);
        var members = mock(OrganizationMemberRepository.class);
        var registry = mock(CodeIntelligenceProviderRegistry.class);
        var provider = mock(CodeIntelligenceProvider.class);
        User user = User.builder().build(); user.setId(1L);
        Organization org = Organization.builder().build(); org.setId(2L);
        Project project = Project.builder().organization(org).build(); project.setId(3L);
        CodeIntelligenceSnapshot snapshot = CodeIntelligenceSnapshot.builder()
                .organization(org).project(project).provider("repowise")
                .revision("abcdef1").repositoryUrl("repo")
                .externalSnapshotId("repowise:cjE:abcdef1")
                .status(IntelligenceSnapshotStatus.READY).build(); snapshot.setId(4L);
        SearchResult expected = new SearchResult(new ResultMetadata("repowise", "1", "external",
                "abcdef1", 0.9, Instant.now(), Map.of()), List.of(), false);
        when(snapshots.findById(4L)).thenReturn(Optional.of(snapshot));
        when(members.existsByOrganizationIdAndUserId(2L, 1L)).thenReturn(true);
        when(registry.requireProvider("repowise", Capability.SEARCH)).thenReturn(provider);
        when(provider.search(any())).thenReturn(expected);
        var service = new CodeIntelligenceQueryService(snapshots, members, registry);

        SearchResult result = service.search(new SearchCodeRequest(4L, "payment", 0, 20), user);

        assertThat(result).isSameAs(expected);
        verify(provider).search(new SearchQuery("repowise:cjE:abcdef1", "payment", 0, 20));
    }
}

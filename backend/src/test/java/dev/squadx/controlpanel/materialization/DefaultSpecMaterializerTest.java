package dev.squadx.controlpanel.materialization;

import dev.squadx.controlpanel.mcp.SpecMaterializer;
import dev.squadx.controlpanel.model.Change;
import dev.squadx.controlpanel.model.SpecVersion;
import dev.squadx.controlpanel.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultSpecMaterializerTest {

    @Mock private ChangeRepository changeRepository;
    @Mock private RequirementRepository requirementRepository;
    @Mock private ScenarioRepository scenarioRepository;
    @Mock private SpecTaskRepository specTaskRepository;
    @Mock private SpecVersionRepository specVersionRepository;
    @Mock private ChangeFolderRenderer renderer;
    @Mock private GitCommitGateway gitCommitGateway;

    @InjectMocks private DefaultSpecMaterializer materializer;

    private Change change;
    private SpecVersion version;

    @BeforeEach
    void setUp() {
        change = Change.builder().module("auth").build();
        change.setId(5L);
        version = SpecVersion.builder().change(change).version(1).current(true).build();

        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put("openspec/changes/auth/specs/auth/spec.md", "# Spec\n");
        lenient().when(renderer.render(any(), any(), any())).thenReturn(files);
        lenient().when(changeRepository.findById(5L)).thenReturn(Optional.of(change));
        lenient().when(requirementRepository.findByChangeId(5L)).thenReturn(List.of());
        lenient().when(specTaskRepository.findByChangeId(5L)).thenReturn(List.of());
        lenient().when(specVersionRepository.save(any(SpecVersion.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void commitsViaGateway() {
        when(specVersionRepository.findByChangeIdAndCurrentTrue(5L)).thenReturn(Optional.of(version));
        when(gitCommitGateway.commit(eq("auth"), any(), anyString()))
                .thenReturn(new GitCommitGateway.CommitResult("sha1"));

        SpecMaterializer.MaterializationResult result = materializer.materialize(5L);

        assertThat(result.available()).isTrue();
        assertThat(result.commit()).isEqualTo("sha1");
        assertThat(version.getCommit()).isEqualTo("sha1");
        assertThat(version.getContentHash()).isNotBlank();
    }

    @Test
    void idempotentNoOpOnSecondMaterialize() {
        when(specVersionRepository.findByChangeIdAndCurrentTrue(5L)).thenReturn(Optional.of(version));
        when(gitCommitGateway.commit(eq("auth"), any(), anyString()))
                .thenReturn(new GitCommitGateway.CommitResult("sha1"));

        materializer.materialize(5L);   // commits
        materializer.materialize(5L);   // same content + commit present → no-op

        verify(gitCommitGateway, times(1)).commit(any(), any(), anyString());
    }

    @Test
    void noGatewayCommitLeavesPending() {
        when(specVersionRepository.findByChangeIdAndCurrentTrue(5L)).thenReturn(Optional.of(version));
        when(gitCommitGateway.commit(eq("auth"), any(), anyString()))
                .thenReturn(new GitCommitGateway.CommitResult(null));

        SpecMaterializer.MaterializationResult result = materializer.materialize(5L);

        assertThat(result.available()).isFalse();
        assertThat(result.commit()).isNull();
        assertThat(result.message()).contains("pending");
        assertThat(version.getContentHash()).isNotBlank();
    }

    @Test
    void createsAutoV1WhenNoCurrentVersion() {
        when(specVersionRepository.findByChangeIdAndCurrentTrue(5L)).thenReturn(Optional.empty());
        when(gitCommitGateway.commit(eq("auth"), any(), anyString()))
                .thenReturn(new GitCommitGateway.CommitResult("sha1"));

        SpecMaterializer.MaterializationResult result = materializer.materialize(5L);

        assertThat(result.version()).isEqualTo("v1");
        verify(specVersionRepository, atLeastOnce()).save(any(SpecVersion.class));
    }
}

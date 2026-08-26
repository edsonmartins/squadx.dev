package dev.squadx.service;

import dev.squadx.dto.intelligence.EnsureSnapshotRequest;
import dev.squadx.model.*;
import dev.squadx.model.enums.IntelligenceJobStatus;
import dev.squadx.model.enums.IntelligenceSnapshotStatus;
import dev.squadx.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CodeIntelligenceSnapshotServiceTest {

    @Mock ProjectRepository projectRepository;
    @Mock OrganizationMemberRepository memberRepository;
    @Mock CodeIntelligenceSnapshotRepository snapshotRepository;
    @Mock CodeIntelligenceIndexJobRepository jobRepository;
    private CodeIntelligenceSnapshotService service;

    @BeforeEach
    void setUp() {
        service = new CodeIntelligenceSnapshotService(projectRepository, memberRepository,
                snapshotRepository, jobRepository);
    }

    @Test
    void createsOneImmutableSnapshotAndPendingJob() {
        User user = User.builder().build(); user.setId(7L);
        Organization organization = Organization.builder().build(); organization.setId(8L);
        Project project = Project.builder().organization(organization)
                .repositoryUrl("https://github.com/acme/repo.git").build(); project.setId(9L);
        when(projectRepository.findById(9L)).thenReturn(Optional.of(project));
        when(memberRepository.existsByOrganizationIdAndUserId(8L, 7L)).thenReturn(true);
        when(snapshotRepository.findByProjectIdAndRevisionAndProvider(9L, "abcdef1", "repowise"))
                .thenReturn(Optional.empty());
        when(snapshotRepository.save(any())).thenAnswer(invocation -> {
            CodeIntelligenceSnapshot snapshot = invocation.getArgument(0); snapshot.setId(10L); return snapshot;
        });
        when(jobRepository.save(any())).thenAnswer(invocation -> {
            CodeIntelligenceIndexJob job = invocation.getArgument(0); job.setId(11L); return job;
        });

        var response = service.ensure(new EnsureSnapshotRequest(9L, "ABCDEF1", "RepoWise"), user);

        assertThat(response.created()).isTrue();
        assertThat(response.revision()).isEqualTo("abcdef1");
        assertThat(response.provider()).isEqualTo("repowise");
        assertThat(response.jobId()).isEqualTo(11L);
        verify(jobRepository).save(argThat(job -> job.getStatus() == IntelligenceJobStatus.PENDING));
    }

    @Test
    void reusesExistingSnapshotAndActiveJob() {
        User user = User.builder().build(); user.setId(7L);
        Organization organization = Organization.builder().build(); organization.setId(8L);
        Project project = Project.builder().organization(organization).repositoryUrl("repo").build(); project.setId(9L);
        CodeIntelligenceSnapshot snapshot = CodeIntelligenceSnapshot.builder()
                .project(project).organization(organization).repositoryUrl("repo")
                .revision("abcdef1").provider("repowise").status(IntelligenceSnapshotStatus.PENDING).build();
        snapshot.setId(10L);
        CodeIntelligenceIndexJob job = CodeIntelligenceIndexJob.builder()
                .snapshot(snapshot).status(IntelligenceJobStatus.PENDING).build(); job.setId(11L);
        when(projectRepository.findById(9L)).thenReturn(Optional.of(project));
        when(memberRepository.existsByOrganizationIdAndUserId(8L, 7L)).thenReturn(true);
        when(snapshotRepository.findByProjectIdAndRevisionAndProvider(9L, "abcdef1", "repowise"))
                .thenReturn(Optional.of(snapshot));
        when(jobRepository.findBySnapshotIdAndStatusIn(eq(10L), any())).thenReturn(List.of(job));

        var response = service.ensure(new EnsureSnapshotRequest(9L, "abcdef1", "repowise"), user);

        assertThat(response.created()).isFalse();
        assertThat(response.jobId()).isEqualTo(11L);
        verify(snapshotRepository, never()).save(any());
    }
}


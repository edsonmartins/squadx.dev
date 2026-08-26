package dev.squadx.service;

import dev.squadx.dto.artifact.PublishArtifactRequest;
import dev.squadx.exception.ForbiddenException;
import dev.squadx.model.*;
import dev.squadx.repository.ExecutionArtifactRepository;
import dev.squadx.repository.ExecutionRepository;
import dev.squadx.repository.OrganizationMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExecutionArtifactServiceTest {
    @Mock ExecutionArtifactRepository artifactRepository;
    @Mock ExecutionRepository executionRepository;
    @Mock OrganizationMemberRepository memberRepository;
    private ExecutionArtifactService service;
    private Execution execution;
    private User user;

    @BeforeEach
    void setUp() {
        service = new ExecutionArtifactService(artifactRepository, executionRepository, memberRepository);
        Organization organization = new Organization(); organization.setId(10L);
        Project project = Project.builder().organization(organization).name("P").slug("p").build();
        Task task = Task.builder().project(project).title("T").build();
        execution = Execution.builder().task(task).build(); execution.setId(20L);
        user = new User(); user.setId(30L);
    }

    @Test
    void publishesWithServerCalculatedChecksum() {
        allowAccess();
        when(artifactRepository.findByExecutionIdAndArtifactKey(20L, "architecture.current.ir"))
                .thenReturn(Optional.empty());
        when(artifactRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        service.publish(20L, request("hello"), user);

        ArgumentCaptor<ExecutionArtifact> saved = ArgumentCaptor.forClass(ExecutionArtifact.class);
        verify(artifactRepository).save(saved.capture());
        assertThat(saved.getValue().getChecksumSha256())
                .isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }

    @Test
    void replacesArtifactWithSameStableKey() {
        ExecutionArtifact existing = ExecutionArtifact.builder().execution(execution)
                .artifactKey("architecture.current.ir").content("old").build();
        allowAccess();
        when(artifactRepository.findByExecutionIdAndArtifactKey(20L, "architecture.current.ir"))
                .thenReturn(Optional.of(existing));
        when(artifactRepository.save(existing)).thenReturn(existing);

        service.publish(20L, request("new"), user);

        assertThat(existing.getContent()).isEqualTo("new");
        verify(artifactRepository).save(existing);
    }

    @Test
    void deniesCrossOrganizationAccess() {
        when(executionRepository.findById(20L)).thenReturn(Optional.of(execution));
        when(memberRepository.existsByOrganizationIdAndUserId(10L, 30L)).thenReturn(false);
        assertThatThrownBy(() -> service.list(20L, user)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(artifactRepository);
    }

    private void allowAccess() {
        when(executionRepository.findById(20L)).thenReturn(Optional.of(execution));
        when(memberRepository.existsByOrganizationIdAndUserId(10L, 30L)).thenReturn(true);
    }

    private static PublishArtifactRequest request(String content) {
        PublishArtifactRequest request = new PublishArtifactRequest();
        request.setArtifactKey("architecture.current.ir");
        request.setType("ARCHITECTURE_MAP"); request.setFormat("JSON");
        request.setName("Architecture"); request.setContent(content);
        return request;
    }
}

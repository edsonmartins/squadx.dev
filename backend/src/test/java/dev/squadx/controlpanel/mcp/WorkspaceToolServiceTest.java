package dev.squadx.controlpanel.mcp;

import dev.squadx.controlpanel.dto.change.ChangeResponse;
import dev.squadx.controlpanel.mcp.dto.*;
import dev.squadx.controlpanel.model.Change;
import dev.squadx.controlpanel.model.Requirement;
import dev.squadx.controlpanel.model.Scenario;
import dev.squadx.controlpanel.model.SpecTask;
import dev.squadx.controlpanel.model.enums.EventSource;
import dev.squadx.controlpanel.model.enums.SpecTaskStatus;
import dev.squadx.controlpanel.model.enums.TaskEventType;
import dev.squadx.controlpanel.repository.RequirementRepository;
import dev.squadx.controlpanel.repository.ScenarioRepository;
import dev.squadx.controlpanel.repository.SpecTaskRepository;
import dev.squadx.controlpanel.service.ChangeService;
import dev.squadx.controlpanel.service.RequirementService;
import dev.squadx.controlpanel.service.SpecEventService;
import dev.squadx.controlpanel.service.SpecTaskService;
import dev.squadx.exception.BadRequestException;
import dev.squadx.intelligence.CodeIntelligenceModels;
import dev.squadx.intelligence.CodeIntelligenceProvider;
import dev.squadx.intelligence.CodeIntelligenceProviderRegistry;
import dev.squadx.model.CodeIntelligenceSnapshot;
import dev.squadx.model.enums.IntelligenceSnapshotStatus;
import dev.squadx.model.User;
import dev.squadx.repository.CodeIntelligenceSnapshotRepository;
import dev.squadx.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class WorkspaceToolServiceTest {

    @Mock private ChangeService changeService;
    @Mock private RequirementService requirementService;
    @Mock private SpecTaskService specTaskService;
    @Mock private SpecEventService specEventService;
    @Mock private SpecTaskRepository specTaskRepository;
    @Mock private RequirementRepository requirementRepository;
    @Mock private ScenarioRepository scenarioRepository;
    @Mock private UserRepository userRepository;
    @Mock private CodeIntelligenceSnapshotRepository codeIntelligenceSnapshots;
    @Mock private CodeIntelligenceProviderRegistry codeIntelligenceProviders;
    @Mock private SpecMaterializer materializer;

    @InjectMocks private WorkspaceToolService toolService;

    private final WorkspaceSession session = new WorkspaceSession(1L, 100L, 7L, 5L, "Backend Agent");
    private User user;

    @BeforeEach
    void setUp() throws Exception {
        user = User.builder().email("u@example.com").fullName("U").build();
        user.setId(1L);
        // repositoryRoot vem de @Value (não injetado em teste unitário); sem ele o
        // scaffoldTests faz Path.of(null) → NPE.
        var field = WorkspaceToolService.class.getDeclaredField("repositoryRoot");
        field.setAccessible(true);
        field.set(toolService, java.nio.file.Files.createTempDirectory("squadx-intel").toString());
    }

    private SpecTask taskInChange(Long changeId) {
        Change change = Change.builder().build();
        change.setId(changeId);
        SpecTask t = SpecTask.builder().change(change).title("T").status(SpecTaskStatus.EM_CURSO).build();
        t.setId(42L);
        return t;
    }

    private void mockUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    }

    @Test
    void getChangeDelegates() {  // R1
        mockUser();
        when(changeService.getById(5L, user)).thenReturn(ChangeResponse.builder().id(5L).build());
        when(requirementService.getByChangeId(5L, user)).thenReturn(List.of());
        when(specTaskService.getByChangeId(5L, user)).thenReturn(List.of());

        GetChangeResponse r = toolService.getChange(session);
        assertThat(r.getChange().getId()).isEqualTo(5L);
    }

    @Test
    void updateTaskStatusEmCursoTransitions() {  // R2
        mockUser();
        when(specTaskRepository.findById(42L)).thenReturn(Optional.of(taskInChange(5L)));

        toolService.updateTaskStatus(session,
                UpdateTaskStatusRequest.builder().taskId(42L).status("em_curso").build());

        verify(specEventService).record(eq(42L), eq(TaskEventType.STARTED), eq(EventSource.MCP),
                anyString(), any(), any());
    }

    @Test
    void updateTaskStatusImplementadoRecordsEvent() {  // R2
        mockUser();
        when(specTaskRepository.findById(42L)).thenReturn(Optional.of(taskInChange(5L)));

        toolService.updateTaskStatus(session,
                UpdateTaskStatusRequest.builder().taskId(42L).status("implementado").note("done").build());

        verify(specEventService).record(eq(42L), eq(TaskEventType.IMPLEMENTED), eq(EventSource.MCP),
                anyString(), eq("done"), any());
        verify(specTaskService, never()).transition(anyLong(), any(), any());
    }

    @Test
    void updateTaskStatusRejectsForbiddenValue() {  // R2
        mockUser();
        when(specTaskRepository.findById(42L)).thenReturn(Optional.of(taskInChange(5L)));

        assertThatThrownBy(() -> toolService.updateTaskStatus(session,
                UpdateTaskStatusRequest.builder().taskId(42L).status("concluida").build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("E_VALIDATION");
    }

    @Test
    void rejectsTaskOutsideScope() {  // R6
        mockUser();
        when(specTaskRepository.findById(42L)).thenReturn(Optional.of(taskInChange(999L)));

        assertThatThrownBy(() -> toolService.updateTaskStatus(session,
                UpdateTaskStatusRequest.builder().taskId(42L).status("em_curso").build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("E_SCOPE");
    }

    @Test
    void reportBlockerRecordsEvent() {  // R3
        mockUser();
        when(specTaskRepository.findById(42L)).thenReturn(Optional.of(taskInChange(5L)));

        toolService.reportBlocker(session,
                ReportBlockerRequest.builder().taskId(42L).reason("waiting on API").build());

        verify(specEventService).record(eq(42L), eq(TaskEventType.BLOCKED), eq(EventSource.MCP),
                anyString(), eq("waiting on API"), any());
    }

    @Test
    void scaffoldTestsGeneratesMethodPerScenario() {  // R5
        Change change = Change.builder().build();
        change.setId(5L);
        Requirement req = Requirement.builder().change(change).requirementId("R1").build();
        req.setId(9L);
        when(requirementRepository.findById(9L)).thenReturn(Optional.of(req));
        when(scenarioRepository.findByRequirementId(9L)).thenReturn(List.of(
                Scenario.builder().requirement(req).name("login inválido")
                        .whenCondition("w").thenResult("t").covered(false).build()));

        ScaffoldTestsRequest request = new ScaffoldTestsRequest();
        request.setRequirementId(9L);
        ScaffoldTestsResponse r = toolService.scaffoldTests(session, request);

        assertThat(r.getMethods()).hasSize(1);
        assertThat(r.getMethods().get(0).getMethodName()).isEqualTo("R1_login_invalido");
        assertThat(r.getCoverage().getTotal()).isEqualTo(1);
        assertThat(r.getCoverage().getCovered()).isEqualTo(0);
    }

    @Test
    void materializeChangeDelegatesToPort() {  // R4
        when(materializer.materialize(5L))
                .thenReturn(SpecMaterializer.MaterializationResult.unavailable("pending"));

        MaterializeResponse r = toolService.materializeChange(session);
        assertThat(r.isOk()).isFalse();
        assertThat(r.getMessage()).isEqualTo("pending");
    }

    @Test
    void searchCodeUsesLatestReadyNativeSnapshot() {
        CodeIntelligenceSnapshot snapshot = CodeIntelligenceSnapshot.builder()
                .project(dev.squadx.model.Project.builder().build())
                .organization(dev.squadx.model.Organization.builder().build())
                .repositoryUrl("https://example.invalid/repo.git")
                .revision("abc1234").provider("native")
                .externalSnapshotId("native:7:abc1234")
                .status(IntelligenceSnapshotStatus.READY).build();
        CodeIntelligenceProvider provider = mock(CodeIntelligenceProvider.class);
        var result = new CodeIntelligenceModels.SearchResult(
                new CodeIntelligenceModels.ResultMetadata("native", "0.1", "native:7:abc1234",
                        "abc1234", 1.0, java.time.Instant.now(), java.util.Map.of()), List.of(), false);
        when(codeIntelligenceSnapshots.findFirstByProjectIdAndStatusOrderByIndexedAtDesc(
                eq(7L), eq(IntelligenceSnapshotStatus.READY))).thenReturn(Optional.of(snapshot));
        when(codeIntelligenceProviders.requireProvider("native", CodeIntelligenceModels.Capability.SEARCH))
                .thenReturn(provider);
        when(provider.search(any())).thenReturn(result);

        assertThat(toolService.searchCode(session, new SearchCodeRequest() {{
            setQuery("Spring"); setLimit(5);
        }})).isSameAs(result);
        verify(provider).search(argThat(q -> "native:7:abc1234".equals(q.snapshotId())
                && "Spring".equals(q.query()) && q.size() == 5));
    }

    @Test
    void searchCodeRejectsProjectWithoutReadyNativeSnapshot() {
        when(codeIntelligenceSnapshots.findFirstByProjectIdAndStatusOrderByIndexedAtDesc(
                eq(7L), eq(IntelligenceSnapshotStatus.READY))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> toolService.searchCode(session, new SearchCodeRequest() {{
            setQuery("Spring");
        }})).isInstanceOf(BadRequestException.class).hasMessageContaining("E_CONFLICT");
    }
}

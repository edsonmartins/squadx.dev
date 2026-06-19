package dev.squadx.controlpanel.service;

import dev.squadx.controlpanel.model.Change;
import dev.squadx.controlpanel.model.SpecEvent;
import dev.squadx.controlpanel.model.SpecTask;
import dev.squadx.controlpanel.model.enums.EventSource;
import dev.squadx.model.Project;
import dev.squadx.controlpanel.model.enums.SpecTaskStatus;
import dev.squadx.controlpanel.model.enums.TaskEventType;
import dev.squadx.controlpanel.repository.SpecEventRepository;
import dev.squadx.controlpanel.repository.SpecTaskRepository;
import dev.squadx.integration.IntegrationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PrReviewCommentPollerTest {

    private static final String API = "https://api.github.com";
    private SpecTaskRepository specTaskRepository;
    private SpecEventRepository specEventRepository;
    private SpecEventService specEventService;
    private MockRestServiceServer server;
    private PrReviewCommentPoller poller;

    @BeforeEach
    void setUp() {
        IntegrationConfig config = new IntegrationConfig();
        config.getGit().setEnabled(true);
        config.getGit().setToken("tkn");
        config.getGit().setApiUrl(API);
        specTaskRepository = mock(SpecTaskRepository.class);
        specEventRepository = mock(SpecEventRepository.class);
        specEventService = mock(SpecEventService.class);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        poller = new PrReviewCommentPoller(config, builder, specTaskRepository,
                specEventRepository, specEventService);
    }

    private SpecTask taskInValidation() {
        SpecTask task = mock(SpecTask.class);
        Change change = mock(Change.class);
        Project project = mock(Project.class);
        when(task.getId()).thenReturn(1L);
        when(task.getChange()).thenReturn(change);
        when(change.getProject()).thenReturn(project);
        when(project.getRepositoryUrl()).thenReturn("https://github.com/o/r");
        return task;
    }

    private SpecEvent prOpened() {
        SpecEvent e = mock(SpecEvent.class);
        when(e.getType()).thenReturn(TaskEventType.PR_OPENED);
        when(e.getSourceRef()).thenReturn("pr-7");
        return e;
    }

    @Test
    void ingestsHumanCommentsSkippingOwnPass5Comments() {
        // Build the mocks BEFORE stubbing the repositories — nesting when() calls inside a
        // thenReturn() argument is what Mockito rejects as unfinished stubbing.
        SpecTask task = taskInValidation();
        SpecEvent prOpened = prOpened();
        when(specTaskRepository.findByStatus(SpecTaskStatus.EM_VALIDACAO)).thenReturn(List.of(task));
        when(specEventRepository.findBySpecTaskIdOrderByOccurredAtAscIdAsc(1L)).thenReturn(List.of(prOpened));

        server.expect(requestTo(API + "/repos/o/r/pulls/7/comments")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [{"id":11,"body":"please fix the null check","user":{"login":"alice"},"created_at":"2024-01-01T00:00:00Z"},
                         {"id":12,"body":"<!-- squadx-pass5:abc123 -->\\nours","user":{"login":"squadx-bot"}}]""",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/repos/o/r/issues/7/comments")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"id\":21,\"body\":\"nit: rename\",\"user\":{\"login\":\"bob\"}}]",
                        MediaType.APPLICATION_JSON));

        poller.poll();

        // Human review + issue comments are ingested as idempotent REVIEW_COMMENT events.
        verify(specEventService).record(eq(1L), eq(TaskEventType.REVIEW_COMMENT), eq(EventSource.GIT),
                eq("review-comment-11"), contains("alice"), any());
        verify(specEventService).record(eq(1L), eq(TaskEventType.REVIEW_COMMENT), eq(EventSource.GIT),
                eq("issue-comment-21"), contains("bob"), any());
        // Our own Pass 5 comment (carries the marker) is never re-ingested.
        verify(specEventService, never()).record(anyLong(), any(), any(), eq("review-comment-12"), any(), any());
        server.verify();
    }

    @Test
    void noOpWhenGitDisabled() {
        IntegrationConfig config = new IntegrationConfig();
        config.getGit().setEnabled(false);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer disabled = MockRestServiceServer.bindTo(builder).build();
        PrReviewCommentPoller p = new PrReviewCommentPoller(config, builder, specTaskRepository,
                specEventRepository, specEventService);

        p.poll();

        verifyNoInteractions(specTaskRepository, specEventService);
        disabled.verify();
    }
}

package dev.squadx.controlpanel.validation;

import dev.squadx.controlpanel.materialization.GitHubDiffClient;
import dev.squadx.controlpanel.materialization.GitHubReviewClient;
import dev.squadx.integration.IntegrationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PullwiseConformanceReviewerTest {

    private static final String URL = "http://pullwise";
    private GitHubDiffClient diffClient;
    private GitHubReviewClient reviewClient;
    private MockRestServiceServer server;
    private PullwiseConformanceReviewer reviewer;

    @BeforeEach
    void setUp() {
        IntegrationConfig config = new IntegrationConfig();
        config.getPullwise().setEnabled(true);
        config.getPullwise().setUrl(URL);
        config.getPullwise().setApiKey("k");
        diffClient = mock(GitHubDiffClient.class);
        reviewClient = mock(GitHubReviewClient.class);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        reviewer = new PullwiseConformanceReviewer(config, builder, diffClient, reviewClient);
    }

    private ConformanceReviewer.ConformanceRequest request() {
        return new ConformanceReviewer.ConformanceRequest(1L, "https://github.com/o/r", "5", "sha",
                List.of(new ConformanceReviewer.ScenarioRef("C1", "when", "then")));
    }

    @Test
    void divergesWhenPullwiseSaysSo() {
        when(diffClient.fetchPullRequestDiff("https://github.com/o/r", "5")).thenReturn("diff --git a b");
        server.expect(requestTo(URL + "/api/conformance/review")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"diverges\":true,\"summary\":\"missing X\"," +
                                "\"criteria\":[{\"name\":\"C1\",\"ok\":false,\"note\":\"not handled\"}]}",
                        MediaType.APPLICATION_JSON));

        ConformanceVerdict verdict = reviewer.review(request());

        assertThat(verdict.diverges()).isTrue();
        assertThat(verdict.critique()).contains("missing X").contains("C1");
        // The divergent finding is published to the PR as an idempotent review.
        verify(reviewClient).publishConformanceReview(
                eq("https://github.com/o/r"), eq("5"), eq("sha"), anyString(), argThat(f -> f.size() == 1));
        server.verify();
    }

    @Test
    void okWhenPullwiseConformant() {
        when(diffClient.fetchPullRequestDiff(anyString(), anyString())).thenReturn("diff");
        server.expect(requestTo(URL + "/api/conformance/review")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"diverges\":false,\"summary\":\"ok\",\"criteria\":[]}",
                        MediaType.APPLICATION_JSON));

        assertThat(reviewer.review(request()).diverges()).isFalse();
    }

    @Test
    void degradesWhenNoPrContext() {
        ConformanceVerdict verdict = reviewer.review(new ConformanceReviewer.ConformanceRequest(
                1L, null, null, "sha", List.of(new ConformanceReviewer.ScenarioRef("C1", "w", "t"))));
        assertThat(verdict.diverges()).isFalse();
        verify(diffClient, never()).fetchPullRequestDiff(any(), any());
        server.verify(); // no HTTP call
    }

    @Test
    void degradesWhenDiffUnavailable() {
        when(diffClient.fetchPullRequestDiff(anyString(), anyString())).thenReturn(null);
        assertThat(reviewer.review(request()).diverges()).isFalse();
        server.verify(); // no HTTP call
    }
}

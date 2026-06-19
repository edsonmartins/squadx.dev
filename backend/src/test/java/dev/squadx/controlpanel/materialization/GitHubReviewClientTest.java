package dev.squadx.controlpanel.materialization;

import dev.squadx.integration.IntegrationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;

class GitHubReviewClientTest {

    private static final String API = "https://api.github.com";
    private MockRestServiceServer server;
    private GitHubReviewClient client;

    @BeforeEach
    void setUp() {
        IntegrationConfig config = new IntegrationConfig();
        config.getGit().setEnabled(true);
        config.getGit().setToken("tkn");
        config.getGit().setApiUrl(API);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GitHubReviewClient(config, builder);
    }

    private GitHubReviewClient.ReviewFinding inline() {
        return new GitHubReviewClient.ReviewFinding("C1 diverges", "not handled", "src/Api.java", 42);
    }

    private GitHubReviewClient.ReviewFinding summaryOnly() {
        return new GitHubReviewClient.ReviewFinding("C2 diverges", "missing test", null, null);
    }

    @Test
    void postsReviewWithInlineAndSummaryFindings() {
        server.expect(requestTo(API + "/repos/o/r/pulls/5/comments")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/repos/o/r/pulls/5/reviews")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.event").value("COMMENT"))
                .andExpect(jsonPath("$.commit_id").value("headsha"))
                .andExpect(jsonPath("$.comments[0].path").value("src/Api.java"))
                .andExpect(jsonPath("$.comments[0].line").value(42))
                .andExpect(jsonPath("$.comments[0].side").value("RIGHT"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.publishConformanceReview("https://github.com/o/r", "5", "headsha",
                "Pass 5 diverged", List.of(inline(), summaryOnly()));

        server.verify();
    }

    @Test
    void skipsFindingsWhoseMarkerAlreadyExists() {
        String marker = inline().marker();
        // The inline finding is already on the PR; only the summary-only finding is new.
        server.expect(requestTo(API + "/repos/o/r/pulls/5/comments")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"body\":\"" + marker + "\\nold\"}]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/repos/o/r/pulls/5/reviews")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.comments").doesNotExist()) // the only fresh finding is summary-only
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.publishConformanceReview("https://github.com/o/r", "5", "headsha",
                "Pass 5 diverged", List.of(inline(), summaryOnly()));

        server.verify();
    }

    @Test
    void noOpWhenNothingNew() {
        String marker = summaryOnly().marker();
        server.expect(requestTo(API + "/repos/o/r/pulls/5/comments")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"body\":\"" + marker + "\\nold\"}]", MediaType.APPLICATION_JSON));
        // No POST expected — all findings already posted.

        client.publishConformanceReview("https://github.com/o/r", "5", "headsha",
                "Pass 5 diverged", List.of(summaryOnly()));

        server.verify();
    }

    @Test
    void noOpWhenGitDisabled() {
        IntegrationConfig config = new IntegrationConfig();
        config.getGit().setEnabled(false);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer disabledServer = MockRestServiceServer.bindTo(builder).build();
        GitHubReviewClient disabled = new GitHubReviewClient(config, builder);

        disabled.publishConformanceReview("https://github.com/o/r", "5", "sha", "s", List.of(inline()));

        disabledServer.verify(); // no HTTP at all
    }
}

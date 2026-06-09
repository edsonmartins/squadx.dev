package dev.squadx.controlpanel.materialization;

import dev.squadx.integration.IntegrationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GitHubCommitGatewayTest {

    private static final String API = "https://api.github.com";
    private IntegrationConfig config;
    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private GitHubCommitGateway gateway;

    @BeforeEach
    void setUp() {
        config = new IntegrationConfig();
        config.getGit().setEnabled(true);
        config.getGit().setToken("tkn");
        config.getGit().setApiUrl(API);
        config.getGit().setBranchPrefix("spec/");
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new GitHubCommitGateway(config, builder);
    }

    private GitCommitGateway.GitTarget target() {
        return new GitCommitGateway.GitTarget("https://github.com/o/r", "main", "auth");
    }

    private Map<String, String> files() {
        return Map.of("openspec/changes/auth/spec.md", "# Spec\n");
    }

    @Test
    void commitsToExistingBranch() {
        server.expect(requestTo(API + "/repos/o/r/git/ref/heads/spec/auth")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"object\":{\"sha\":\"head1\"}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/repos/o/r/git/commits/head1")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"tree\":{\"sha\":\"basetree\"}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/repos/o/r/git/blobs")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"sha\":\"blob1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/repos/o/r/git/trees")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"sha\":\"newtree\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/repos/o/r/git/commits")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"sha\":\"commit1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/repos/o/r/git/refs/heads/spec/auth")).andExpect(method(HttpMethod.PATCH))
                .andRespond(withStatus(HttpStatus.OK));

        GitCommitGateway.CommitResult result = gateway.commit(target(), files(), "msg");

        assertThat(result.sha()).isEqualTo("commit1");
        assertThat(result.conflict()).isFalse();
        server.verify();
    }

    @Test
    void createsBranchWhenAbsent() {
        server.expect(requestTo(API + "/repos/o/r/git/ref/heads/spec/auth")).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(API + "/repos/o/r/git/ref/heads/main")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"object\":{\"sha\":\"base1\"}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/repos/o/r/git/refs")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"object\":{\"sha\":\"base1\"}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/repos/o/r/git/commits/base1")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"tree\":{\"sha\":\"basetree\"}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/repos/o/r/git/blobs")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"sha\":\"blob1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/repos/o/r/git/trees")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"sha\":\"newtree\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/repos/o/r/git/commits")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"sha\":\"commit1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/repos/o/r/git/refs/heads/spec/auth")).andExpect(method(HttpMethod.PATCH))
                .andRespond(withStatus(HttpStatus.OK));

        GitCommitGateway.CommitResult result = gateway.commit(target(), files(), "msg");

        assertThat(result.sha()).isEqualTo("commit1");
        server.verify();
    }

    @Test
    void reportsConflictWhenRefUpdateRejected() {
        server.expect(requestTo(API + "/repos/o/r/git/ref/heads/spec/auth")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"object\":{\"sha\":\"head1\"}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/repos/o/r/git/commits/head1")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"tree\":{\"sha\":\"basetree\"}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/repos/o/r/git/blobs")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"sha\":\"blob1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/repos/o/r/git/trees")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"sha\":\"newtree\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/repos/o/r/git/commits")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"sha\":\"commit1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/repos/o/r/git/refs/heads/spec/auth")).andExpect(method(HttpMethod.PATCH))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));

        GitCommitGateway.CommitResult result = gateway.commit(target(), files(), "msg");

        assertThat(result.sha()).isNull();
        assertThat(result.conflict()).isTrue();
    }

    @Test
    void skipsWhenDisabled() {
        config.getGit().setEnabled(false);
        GitCommitGateway.CommitResult result = gateway.commit(target(), files(), "msg");
        assertThat(result.sha()).isNull();
        assertThat(result.conflict()).isFalse();
        server.verify(); // no HTTP calls expected
    }

    @Test
    void skipsWhenRepositoryUrlMissing() {
        GitCommitGateway.CommitResult result =
                gateway.commit(new GitCommitGateway.GitTarget(null, "main", "auth"), files(), "msg");
        assertThat(result.sha()).isNull();
        server.verify();
    }

    @Test
    void openPullRequestReusesExistingOpenPr() {
        server.expect(requestTo(API + "/repos/o/r/pulls?head=o:spec/auth&state=open"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"html_url\":\"https://github.com/o/r/pull/7\"}]",
                        MediaType.APPLICATION_JSON));

        GitCommitGateway.PullRequestResult result = gateway.openPullRequest(target(), "t", "b");

        assertThat(result.url()).isEqualTo("https://github.com/o/r/pull/7");
        server.verify(); // no POST
    }

    @Test
    void openPullRequestCreatesWhenNoneOpen() {
        server.expect(requestTo(API + "/repos/o/r/pulls?head=o:spec/auth&state=open"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/repos/o/r/pulls")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"html_url\":\"https://github.com/o/r/pull/8\"}",
                        MediaType.APPLICATION_JSON));

        GitCommitGateway.PullRequestResult result = gateway.openPullRequest(target(), "t", "b");

        assertThat(result.url()).isEqualTo("https://github.com/o/r/pull/8");
        server.verify();
    }

    @Test
    void openPullRequestSkipsWhenDisabled() {
        config.getGit().setEnabled(false);
        assertThat(gateway.openPullRequest(target(), "t", "b").url()).isNull();
        server.verify(); // no HTTP calls
    }

    @Test
    void parsesOwnerRepoFromUrlForms() {
        assertThat(GitHubCommitGateway.parseOwnerRepo("https://github.com/o/r")).containsExactly("o", "r");
        assertThat(GitHubCommitGateway.parseOwnerRepo("https://github.com/o/r.git")).containsExactly("o", "r");
        assertThat(GitHubCommitGateway.parseOwnerRepo("git@github.com:o/r.git")).containsExactly("o", "r");
        assertThat(GitHubCommitGateway.parseOwnerRepo("https://gitlab.com/o/r")).isNull();
    }
}

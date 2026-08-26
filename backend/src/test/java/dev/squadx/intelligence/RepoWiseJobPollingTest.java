package dev.squadx.intelligence;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;
import java.time.Duration;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RepoWiseJobPollingTest {
    @Test
    void mapsCompletedExternalJobToReadySnapshot() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://repowise");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("http://repowise/api/jobs/job-7"))
                .andRespond(withSuccess("{\"id\":\"job-7\",\"status\":\"completed\"}", MediaType.APPLICATION_JSON));
        Path root = Files.createTempDirectory("repowise-provider-test");
        RepoWiseProvider provider = new RepoWiseProvider(builder.build(), root, 3, Duration.ofSeconds(30));

        var result = provider.refreshSnapshot(new CodeIntelligenceModels.RepositorySnapshot(
                "snapshot-1", 1L, 2L, "repo", "abcdef1", "repowise", "http-api",
                CodeIntelligenceModels.SnapshotStatus.INDEXING, null, "job-7"));

        assertThat(result.status()).isEqualTo(CodeIntelligenceModels.SnapshotStatus.READY);
        assertThat(result.externalJobId()).isEqualTo("job-7");
        server.verify();
    }
}

package dev.squadx.intelligence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;

import static dev.squadx.intelligence.CodeIntelligenceModels.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

class RepoWiseProviderTest {

    @TempDir Path root;

    @Test
    void mapsFullTextSearchToCanonicalEvidence() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://repowise");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("http://repowise/api/search?query=payment&search_type=fulltext&limit=20&repo_id=repo-1"))
                .andRespond(withSuccess("""
                        [{"page_id":"p1","title":"Payment","page_type":"module",
                          "target_path":"src/Payment.java","score":0.91,
                          "snippet":"class Payment","search_type":"fulltext"}]
                        """, MediaType.APPLICATION_JSON));
        RepoWiseProvider provider = new RepoWiseProvider(builder.build(), root, 3, Duration.ofSeconds(30));

        SearchResult result = provider.search(new SearchQuery(snapshot("repo-1", "abcdef1"),
                "payment", 0, 20));

        assertThat(result.metadata().provider()).isEqualTo("repowise");
        assertThat(result.metadata().revision()).isEqualTo("abcdef1");
        assertThat(result.hits()).singleElement().satisfies(hit -> {
            assertThat(hit.location().path()).isEqualTo("src/Payment.java");
            assertThat(hit.evidence()).singleElement()
                    .extracting(EvidenceRef::revision).isEqualTo("abcdef1");
        });
        server.verify();
    }

    @Test
    void opensCircuitAfterConfiguredFailures() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://repowise");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("http://repowise/api/meta/version")).andRespond(withServerError());
        server.expect(once(), requestTo("http://repowise/api/meta/version")).andRespond(withServerError());
        RepoWiseProvider provider = new RepoWiseProvider(builder.build(), root, 2, Duration.ofMinutes(1));

        assertThatThrownBy(provider::healthy).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(provider::healthy).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(provider::healthy).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("circuit breaker is open");
        server.verify();
    }

    private String snapshot(String repoId, String revision) {
        return "repowise:" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(repoId.getBytes(StandardCharsets.UTF_8)) + ":" + revision;
    }
}

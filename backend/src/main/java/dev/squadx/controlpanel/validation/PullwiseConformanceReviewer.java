package dev.squadx.controlpanel.validation;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;

import java.util.List;
import java.util.UUID;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pullwise.conformance.enabled", havingValue = "true")
public class PullwiseConformanceReviewer implements ConformanceReviewer {
    private final RestClient.Builder restClientBuilder;
    @Value("${pullwise.conformance.url:http://pullwise:8080}") private String url;
    @Value("${pullwise.conformance.api-key:}") private String apiKey;
    @Value("${pullwise.conformance.timeout-ms:30000}") private int timeoutMs;
    @Value("${pullwise.conformance.failure-threshold:3}") private int failureThreshold;
    @Value("${pullwise.conformance.open-seconds:30}") private int openSeconds;
    private final AtomicInteger failures = new AtomicInteger();
    private final AtomicLong openUntil = new AtomicLong();

    @Override
    public ConformanceVerdict review(ConformanceRequest request) {
        if (request.gitDiff() == null || request.gitDiff().isBlank()) {
            throw new IllegalStateException("Git diff is required for Pullwise conformance review");
        }
        var criteria = request.scenarios().stream()
                .map(s -> new Criterion(s.name(), s.when(), s.then())).toList();
        long now = System.currentTimeMillis();
        if (openUntil.get() > now) throw new IllegalStateException("Pullwise conformance circuit breaker is open");
        Response response = null;
        RuntimeException failure = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                HttpClient httpClient = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(Math.max(timeoutMs, 1000))).build();
                JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
                requestFactory.setReadTimeout(Duration.ofMillis(Math.max(timeoutMs, 1000)));
                response = restClientBuilder.baseUrl(url).requestFactory(requestFactory).build()
                        .post().uri("/api/conformance/review")
                        .header("X-API-Key", apiKey).header("X-Correlation-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).body(new Request(request.gitDiff(), criteria))
                        .retrieve().body(Response.class);
                failures.set(0); openUntil.set(0); break;
            } catch (RuntimeException e) {
                failure = e;
                if (attempt == 1 || !isRetryable(e)) break;
            }
        }
        if (response == null) {
            if (failures.incrementAndGet() >= Math.max(failureThreshold, 1)) {
                openUntil.set(System.currentTimeMillis() + Duration.ofSeconds(Math.max(openSeconds, 1)).toMillis());
            }
            throw failure != null ? failure : new IllegalStateException("Pullwise conformance failed");
        }
        if (response == null) throw new IllegalStateException("Empty Pullwise conformance response");
        return new ConformanceVerdict(response.diverges(), response.summary());
    }

    private record Request(String gitDiff, List<Criterion> criteria) {}
    private record Criterion(String name, String when, String then) {}
    private record Response(boolean diverges, String summary, List<CriterionResult> criteria) {}
    private record CriterionResult(String name, boolean ok, String note) {}

    private boolean isRetryable(RuntimeException failure) {
        if (failure instanceof HttpClientErrorException clientError) {
            int status = clientError.getStatusCode().value();
            return status >= 500;
        }
        return true;
    }
}

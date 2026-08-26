package dev.squadx.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Bounded telemetry for code-intelligence providers; no tenant, query or path labels. */
@Component
public class CodeIntelligenceMetrics {
    private final MeterRegistry registry;

    public CodeIntelligenceMetrics(MeterRegistry registry) { this.registry = registry; }

    public void providerCall(String capability, String provider, long elapsedMs, boolean success) {
        String[] tags = {"capability", label(capability), "provider", label(provider),
                "outcome", success ? "success" : "error"};
        Counter.builder("squadx.code_intelligence.provider.calls")
                .description("Code-intelligence provider calls")
                .tags(tags).register(registry).increment();
        Timer.builder("squadx.code_intelligence.provider.latency")
                .description("Code-intelligence provider latency")
                .publishPercentileHistogram().tags(tags).register(registry)
                .record(Math.max(0, elapsedMs), TimeUnit.MILLISECONDS);
    }

    public void indexJob(String provider, String outcome, long elapsedMs) {
        String[] tags = {"provider", label(provider), "outcome", label(outcome)};
        Counter.builder("squadx.code_intelligence.index.jobs")
                .description("Code-intelligence indexing jobs")
                .tags(tags).register(registry).increment();
        Timer.builder("squadx.code_intelligence.index.duration")
                .description("Code-intelligence indexing duration")
                .publishPercentileHistogram().tags(tags).register(registry)
                .record(Math.max(0, elapsedMs), TimeUnit.MILLISECONDS);
    }

    public void shadow(String primary, String shadow, double divergence) {
        Counter.builder("squadx.code_intelligence.shadow.comparisons")
                .description("Shadow comparisons completed without affecting verdict")
                .tags("primary", label(primary), "shadow", label(shadow)).register(registry).increment();
        registry.summary("squadx.code_intelligence.shadow.divergence", "Observed shadow divergence")
                .record(divergence);
    }

    private String label(String value) {
        return value == null || value.isBlank() ? "unknown" : value.toLowerCase(Locale.ROOT);
    }
}

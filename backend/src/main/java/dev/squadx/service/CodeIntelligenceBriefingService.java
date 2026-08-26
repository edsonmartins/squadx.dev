package dev.squadx.service;

import dev.squadx.intelligence.CodeIntelligenceModels.Capability;
import dev.squadx.intelligence.CodeIntelligenceModels.SearchHit;
import dev.squadx.intelligence.CodeIntelligenceModels.SearchQuery;
import dev.squadx.intelligence.CodeIntelligenceModels.ArchitectureQuery;
import dev.squadx.model.CodeIntelligenceSnapshot;
import dev.squadx.model.Task;
import dev.squadx.repository.CodeIntelligenceSnapshotRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds bounded, optional repository context for an agent task briefing. */
@Service
@Slf4j
public class CodeIntelligenceBriefingService {
    private final CodeIntelligenceSnapshotRepository snapshots;
    private final dev.squadx.intelligence.CodeIntelligenceProviderRegistry providers;
    private final boolean enabled;
    private final int maxHits;
    private final int maxChars;

    public CodeIntelligenceBriefingService(
            CodeIntelligenceSnapshotRepository snapshots,
            dev.squadx.intelligence.CodeIntelligenceProviderRegistry providers,
            @Value("${intelligence.briefing.enabled:false}") boolean enabled,
            @Value("${intelligence.briefing.max-hits:5}") int maxHits,
            @Value("${intelligence.briefing.max-chars:6000}") int maxChars) {
        this.snapshots = snapshots;
        this.providers = providers;
        this.enabled = enabled;
        this.maxHits = Math.max(1, Math.min(maxHits, 20));
        this.maxChars = Math.max(500, Math.min(maxChars, 12000));
    }

    public Map<String, Object> contextFor(Task task) {
        if (!enabled || task == null || task.getProject() == null) return Map.of();
        try {
            CodeIntelligenceSnapshot snapshot = snapshots
                    .findFirstByProjectIdAndStatusOrderByIndexedAtDesc(
                            task.getProject().getId(), dev.squadx.model.enums.IntelligenceSnapshotStatus.READY)
                    .orElse(null);
            if (snapshot == null || snapshot.getExternalSnapshotId() == null) return Map.of();
            var provider = providers.requireProvider(snapshot.getProvider(), Capability.SEARCH);
            String query = truncate((task.getTitle() == null ? "" : task.getTitle()) + " "
                    + (task.getDescription() == null ? "" : task.getDescription()), 500);
            if (query.isBlank()) return Map.of();
            var result = provider.search(new SearchQuery(snapshot.getExternalSnapshotId(), query, 0, maxHits));
            List<Map<String, Object>> hits = result.hits().stream().limit(maxHits).map(this::mapHit).toList();
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("provider", snapshot.getProvider());
            context.put("revision", snapshot.getRevision());
            context.put("snapshot_id", snapshot.getId());
            context.put("hits", hits);
            try {
                var architectureProvider = providers.requireProvider(snapshot.getProvider(), Capability.ARCHITECTURE);
                var architecture = architectureProvider.getArchitecture(new ArchitectureQuery(snapshot.getExternalSnapshotId()));
                context.put("architecture_snapshot", architecture);
            } catch (Exception ignored) {
                // Architecture is additive; a provider without this capability must not block work.
            }
            if (hits.isEmpty() && !context.containsKey("architecture_snapshot")) return Map.of();
            return context;
        } catch (Exception e) {
            log.debug("Code-intelligence briefing unavailable for task {}: {}", task.getId(), e.getMessage());
            return Map.of();
        }
    }

    private Map<String, Object> mapHit(SearchHit hit) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("path", hit.location().path());
        value.put("start_line", hit.location().startLine());
        value.put("end_line", hit.location().endLine());
        value.put("symbol_id", hit.symbolId());
        value.put("snippet", truncate(hit.snippet(), Math.max(100, maxChars / Math.max(1, maxHits))));
        return value;
    }

    private String truncate(String value, int limit) {
        if (value == null) return "";
        String normalized = value.trim();
        return normalized.length() <= limit ? normalized : normalized.substring(0, limit) + "…";
    }
}

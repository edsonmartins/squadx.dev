package dev.squadx.intelligence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static dev.squadx.intelligence.CodeIntelligenceModels.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RipgrepFallbackProviderTest {

    @TempDir Path root;

    @Test
    void advertisesSearchOnly() {
        var provider = new RipgrepFallbackProvider(new ObjectMapper(), root.toString());

        assertThat(provider.descriptor().id()).isEqualTo("ripgrep");
        assertThat(provider.descriptor().capabilities()).containsExactly(Capability.SEARCH);
    }

    @Test
    void rejectsInvalidRevisionBeforeAccessingRepository() {
        var provider = new RipgrepFallbackProvider(new ObjectMapper(), root.toString());

        assertThatThrownBy(() -> provider.ensureSnapshot(
                new SnapshotRequest(1L, 2L, "https://example/repo.git", "main")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid Git revision");
    }

    @Test
    void rejectsBlankAndOversizedSearchQueries() {
        var provider = new RipgrepFallbackProvider(new ObjectMapper(), root.toString());

        assertThatThrownBy(() -> provider.search(new SearchQuery("ripgrep:2:abcdef1", " ", 0, 20)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> provider.search(new SearchQuery(
                "ripgrep:2:abcdef1", "x".repeat(501), 0, 20)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

package dev.squadx.intelligence;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.squadx.intelligence.CodeIntelligenceModels.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodeIntelligenceModelsTest {

    @Test
    void canonicalResultsPreserveProviderAndRevisionAndCopyCollections() {
        List<SearchHit> mutable = new ArrayList<>();
        ResultMetadata metadata = new ResultMetadata("repowise", "0.39.0", "snapshot-1",
                "abc1234", 0.9, Instant.now(), Map.of("mode", "shadow"));
        SearchResult result = new SearchResult(metadata, mutable, false);
        mutable.add(new SearchHit(new CodeLocation("src/App.java", 1, 2),
                "class App", null, 0.8, List.of()));

        assertThat(result.metadata().provider()).isEqualTo("repowise");
        assertThat(result.metadata().revision()).isEqualTo("abc1234");
        assertThat(result.hits()).isEmpty();
        assertThatThrownBy(() -> result.hits().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsConfidenceOutsideCanonicalRange() {
        assertThatThrownBy(() -> new ResultMetadata("provider", "1", "snapshot", "abc1234",
                1.1, Instant.now(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void indexManifestIsRevisionScopedAndImmutable() {
        var files = new ArrayList<IndexedFile>();
        var manifest = new CodeIndexManifest("native:1:abc1234", "abc1234", files,
                List.of(new CodeSymbol("symbol:App", "App", "class", "java",
                        new CodeLocation("src/App.java", 1, 4))), List.of(), List.of());
        files.add(new IndexedFile("src/App.java", "java", 42, "hash"));

        assertThat(manifest.files()).isEmpty();
        assertThatThrownBy(() -> manifest.symbols().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new IndexedFile("", "java", 1, "hash"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

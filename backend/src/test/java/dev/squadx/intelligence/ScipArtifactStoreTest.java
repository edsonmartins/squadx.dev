package dev.squadx.intelligence;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.assertj.core.api.Assertions.*;

class ScipArtifactStoreTest {
    @Test
    void storesAndReadsOnlyMatchingArtifact() throws Exception {
        var store = new ScipArtifactStore(Files.createTempDirectory("scip-store-"));
        byte[] artifact = "scip-fixture".getBytes();
        String sha = ScipArtifactStore.sha256(artifact);
        store.put("native:1:abc1234", artifact, sha);
        assertThat(store.read("native:1:abc1234", sha)).isEqualTo(artifact);
        assertThatThrownBy(() -> store.read("native:1:abc1234", "bad"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsInvalidSnapshotIdsAndChecksums() throws Exception {
        var store = new ScipArtifactStore(Files.createTempDirectory("scip-store-"));
        byte[] artifact = "scip-fixture".getBytes();
        assertThatThrownBy(() -> store.put("../escape", artifact, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.put("native:1:abc1234", artifact, "bad"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

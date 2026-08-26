package dev.squadx.intelligence;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

class NativeIndexManifestBuilderTest {

    @Test
    void buildsRevisionPinnedFilesAndSkipsGeneratedDirectories() throws Exception {
        var root = Files.createTempDirectory("native-index-");
        Files.createDirectories(root.resolve("src"));
        Files.createDirectories(root.resolve("target"));
        Files.writeString(root.resolve("src/App.java"), "class App {}\n");
        Files.writeString(root.resolve("README.md"), "# App\n");
        Files.writeString(root.resolve("target/generated.java"), "class Generated {}\n");

        var manifest = new NativeIndexManifestBuilder().build(root, "native:1:abc1234", "abc1234");

        assertThat(manifest.snapshotId()).isEqualTo("native:1:abc1234");
        assertThat(manifest.revision()).isEqualTo("abc1234");
        assertThat(manifest.files()).extracting("path")
                .containsExactly("README.md", "src/App.java");
        assertThat(manifest.files()).extracting("language")
                .containsExactly("markdown", "java");
        assertThat(manifest.files().get(1).contentHash())
                .isEqualTo("7857929bc003002041fc53ce4fc1b50753a888a667ec0fbd797bc5af02866432");
        assertThat(manifest.symbols()).extracting("qualifiedName")
                .containsExactly("App");
        assertThat(manifest.symbols().get(0).id())
                .isEqualTo("symbol:src/App.java:1:App");
    }
}

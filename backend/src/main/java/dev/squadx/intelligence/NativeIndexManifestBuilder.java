package dev.squadx.intelligence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static dev.squadx.intelligence.CodeIntelligenceModels.CodeIndexManifest;
import static dev.squadx.intelligence.CodeIntelligenceModels.IndexedFile;
import static dev.squadx.intelligence.CodeIntelligenceModels.CodeSymbol;
import static dev.squadx.intelligence.CodeIntelligenceModels.CodeLocation;

/** Builds the parser-neutral file portion of a revision-pinned native index. */
public final class NativeIndexManifestBuilder {
    private static final Set<String> IGNORED_DIRECTORIES = Set.of(".git", "node_modules", "target", "build", "dist");

    public CodeIndexManifest build(Path repository, String snapshotId, String revision) {
        Path root = repository.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) throw new IllegalArgumentException("Repository directory is required");
        List<IndexedFile> files = new ArrayList<>();
        List<CodeSymbol> symbols = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> !ignored(root, path))
                    .sorted(Comparator.comparing(path -> root.relativize(path).toString()))
                    .forEach(path -> {
                        files.add(indexFile(root, path));
                        symbols.addAll(extractSymbols(root, path));
                    });
        } catch (IOException e) {
            throw new IllegalStateException("Unable to build native index manifest", e);
        }
        return new CodeIndexManifest(snapshotId, revision, files, symbols, List.of(), List.of());
    }

    private List<CodeSymbol> extractSymbols(Path root, Path path) {
        String language = language(path);
        if (!(Set.of("java", "python", "typescript", "javascript", "go", "rust", "kotlin").contains(language))) {
            return List.of();
        }
        try {
            if (Files.size(path) > 2_000_000) return List.of();
            Pattern pattern = switch (language) {
                case "java", "kotlin" -> Pattern.compile("\\b(class|interface|enum|record|fun)\\s+([A-Za-z_$][\\w$]*)");
                case "python" -> Pattern.compile("^\\s*(class|def|async\\s+def)\\s+([A-Za-z_]\\w*)");
                case "typescript", "javascript" -> Pattern.compile("\\b(class|function|interface|type|enum)\\s+([A-Za-z_$][\\w$]*)");
                case "go" -> Pattern.compile("\\b(type|func)\\s+([A-Za-z_]\\w*)");
                case "rust" -> Pattern.compile("\\b(struct|enum|trait|fn|mod)\\s+([A-Za-z_]\\w*)");
                default -> null;
            };
            if (pattern == null) return List.of();
            List<CodeSymbol> result = new ArrayList<>();
            List<String> lines = Files.readAllLines(path);
            String relative = root.relativize(path).toString();
            for (int i = 0; i < lines.size(); i++) {
                Matcher matcher = pattern.matcher(lines.get(i));
                while (matcher.find()) {
                    String name = matcher.group(2);
                    String id = "symbol:" + relative + ":" + (i + 1) + ":" + name;
                    result.add(new CodeSymbol(id, name, matcher.group(1), language,
                            new CodeLocation(relative, i + 1, i + 1)));
                }
            }
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to extract symbols from " + path, e);
        }
    }

    private boolean ignored(Path root, Path path) {
        Path relative = root.relativize(path);
        for (Path part : relative) {
            if (IGNORED_DIRECTORIES.contains(part.toString())) return true;
        }
        return false;
    }

    private IndexedFile indexFile(Path root, Path path) {
        try {
            return new IndexedFile(root.relativize(path).toString(), language(path),
                    Files.size(path), sha256(path));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to index file " + path, e);
        }
    }

    private String language(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot < 0) return "unknown";
        return switch (name.substring(dot + 1)) {
            case "java" -> "java";
            case "py" -> "python";
            case "ts", "tsx" -> "typescript";
            case "js", "jsx" -> "javascript";
            case "go" -> "go";
            case "rs" -> "rust";
            case "kt", "kts" -> "kotlin";
            case "c", "h" -> "c";
            case "cpp", "cc", "cxx", "hpp" -> "cpp";
            case "md" -> "markdown";
            case "yml", "yaml" -> "yaml";
            case "json" -> "json";
            default -> "unknown";
        };
    }

    private String sha256(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) != -1;) digest.update(buffer, 0, read);
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}

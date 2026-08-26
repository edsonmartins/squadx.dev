package dev.squadx.controlpanel.mcp;

/**
 * Porta de materialização da spec no Git (RFC-0002). O default é {@link NoopSpecMaterializer};
 * a implementação real chega na capability {@code spec-versioning-materialization}.
 */
public interface SpecMaterializer {

    MaterializationResult materialize(Long changeId);

    record MaterializationResult(boolean available, String version, String commit, String message) {

        public static MaterializationResult unavailable(String message) {
            return new MaterializationResult(false, null, null, message);
        }

        public static MaterializationResult of(String version, String commit) {
            return new MaterializationResult(true, version, commit, null);
        }
    }
}


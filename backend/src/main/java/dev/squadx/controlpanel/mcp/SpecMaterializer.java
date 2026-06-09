package dev.squadx.controlpanel.mcp;

/**
 * Porta de materialização da spec no Git (RFC-0002). Implementada por
 * {@code DefaultSpecMaterializer} (render determinístico + commit via {@code GitCommitGateway}).
 */
public interface SpecMaterializer {

    MaterializationResult materialize(Long changeId);

    record MaterializationResult(boolean available, String version, String commit, String prUrl,
                                 String message) {

        public static MaterializationResult unavailable(String message) {
            return new MaterializationResult(false, null, null, null, message);
        }

        public static MaterializationResult of(String version, String commit, String prUrl) {
            return new MaterializationResult(true, version, commit, prUrl, null);
        }
    }
}

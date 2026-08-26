package dev.squadx.intelligence;

import java.nio.file.Path;

/** Pluggable contract for language-specific SCIP producers. */
public interface ScipIndexer {
    /** Returns true when this indexer can process the repository languages. */
    boolean supports(Path repository);

    /** Produces a deterministic SCIP artifact for the pinned revision. */
    byte[] index(Path repository, String revision);
}

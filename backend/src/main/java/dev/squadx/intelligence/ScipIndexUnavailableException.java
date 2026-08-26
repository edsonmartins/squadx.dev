package dev.squadx.intelligence;

/** Explicit failure used when no approved SCIP producer is installed. */
public class ScipIndexUnavailableException extends IllegalStateException {
    public ScipIndexUnavailableException(String message) {
        super(message);
    }
}

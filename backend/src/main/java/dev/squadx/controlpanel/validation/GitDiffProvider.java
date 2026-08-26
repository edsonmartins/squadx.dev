package dev.squadx.controlpanel.validation;

/** Resolves the immutable unified diff associated with a Pass 5 revision. */
public interface GitDiffProvider {
    String diff(Long specTaskId, String revision);
}

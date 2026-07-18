package dev.squadx.model.enums;

/**
 * Default streaming quality a user prefers for the Live View.
 * Serialized by name over JSON (AUTO/HD/SD); the frontend Select uses the same tokens.
 */
public enum LiveViewQuality {
    AUTO,
    HD,
    SD
}

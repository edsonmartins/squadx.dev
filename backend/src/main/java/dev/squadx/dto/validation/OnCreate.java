package dev.squadx.dto.validation;

/**
 * Bean Validation group for create-only constraints. Lets a request DTO be shared
 * between create (which requires immutable fields) and update (which doesn't),
 * by applying those constraints only when validating with this group.
 */
public interface OnCreate {
}

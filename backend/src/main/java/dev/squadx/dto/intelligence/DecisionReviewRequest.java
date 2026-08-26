package dev.squadx.dto.intelligence;
import jakarta.validation.constraints.NotBlank;
public record DecisionReviewRequest(@NotBlank String decision) {}

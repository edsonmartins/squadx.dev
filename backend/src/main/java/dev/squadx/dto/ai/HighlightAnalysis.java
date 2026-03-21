package dev.squadx.dto.ai;

import java.util.List;

/**
 * Structured AI output for execution log analysis.
 */
public record HighlightAnalysis(List<HighlightEntry> highlights) {

    public record HighlightEntry(
            String type,
            String title,
            String description,
            double confidence
    ) {}
}

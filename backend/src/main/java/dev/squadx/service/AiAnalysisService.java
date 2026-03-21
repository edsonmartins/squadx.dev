package dev.squadx.service;

import dev.squadx.dto.ai.HighlightAnalysis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI-powered analysis service using Spring AI.
 * Provides intelligent execution log analysis, summary generation,
 * and PR description generation.
 *
 * Only available when squadx.ai.enabled=true.
 */
@Service
@ConditionalOnProperty(prefix = "squadx.ai", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class AiAnalysisService {

    private final ChatClient chatClient;

    /**
     * Analyze execution logs using LLM to find highlights.
     */
    public HighlightAnalysis analyzeExecutionLogs(List<String> logs) {
        String logsText = String.join("\n", logs);

        return chatClient.prompt()
                .user("""
                        Analyze these execution logs and identify important highlights.
                        For each highlight, provide:
                        - type: one of BUG_FOUND, FEATURE_COMPLETED, TEST_PASSED, TEST_FAILED, \
                        ERROR_DETECTED, COMMIT_MADE, DEPLOY, CUSTOM
                        - title: brief summary (max 80 chars)
                        - description: detailed explanation
                        - confidence: 0.0 to 1.0

                        Logs:
                        %s
                        """.formatted(logsText))
                .call()
                .entity(HighlightAnalysis.class);
    }

    /**
     * Generate a human-readable summary of an execution.
     */
    public String generateExecutionSummary(String logs) {
        return chatClient.prompt()
                .user("""
                        Summarize these execution logs in 2-3 sentences. Focus on:
                        - What was accomplished
                        - Any errors or issues
                        - Final outcome

                        Logs:
                        %s
                        """.formatted(logs))
                .call()
                .content();
    }

    /**
     * Generate a PR description from a git diff.
     */
    public String generatePrDescription(String diff, String taskTitle) {
        return chatClient.prompt()
                .user("""
                        Generate a pull request description for the following changes.
                        Task: %s

                        Format as markdown with:
                        ## Summary
                        (2-3 bullet points)

                        ## Changes
                        (list of changes)

                        ## Test plan
                        (suggested testing steps)

                        Diff:
                        %s
                        """.formatted(taskTitle, diff))
                .call()
                .content();
    }
}

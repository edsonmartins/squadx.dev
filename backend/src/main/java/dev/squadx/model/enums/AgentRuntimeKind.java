package dev.squadx.model.enums;

/**
 * How an agent executes work.
 * <ul>
 *   <li>{@code NATIVE} – SquadX's built-in LangGraph + LiteLLM agentic loop.</li>
 *   <li>{@code EXTERNAL_CLI} – shells out to an external coding-agent CLI
 *       (see {@link CliProvider}) inside the hardened sandbox.</li>
 * </ul>
 */
public enum AgentRuntimeKind {
    NATIVE,
    EXTERNAL_CLI
}

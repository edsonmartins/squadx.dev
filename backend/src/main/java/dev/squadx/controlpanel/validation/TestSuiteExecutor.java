package dev.squadx.controlpanel.validation;

/** Executes the repository's deterministic test suite for a merged revision. */
public interface TestSuiteExecutor {
    TestExecutionResult execute(Long specTaskId, String revision);

    record TestExecutionResult(boolean passed, String summary) {
        public static TestExecutionResult passed(String summary) {
            return new TestExecutionResult(true, summary);
        }

        public static TestExecutionResult failed(String summary) {
            return new TestExecutionResult(false, summary);
        }
    }
}

package dev.squadx.service;

import dev.squadx.model.AutopilotRun;
import dev.squadx.model.enums.AutopilotTriggerType;
import dev.squadx.service.AutopilotWorkService.PerformResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orchestrates a single autopilot firing. This is the bean referenced by JobRunr
 * recurring jobs ({@link #runScheduled}). It is intentionally non-transactional so
 * that {@link AutopilotWorkService#perform} and {@link AutopilotWorkService#record}
 * run in independent transactions — a failed perform still records a FAILED run.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutopilotExecutor {

    private final AutopilotWorkService workService;

    /** Invoked by the JobRunr recurring job. */
    public void runScheduled(Long autopilotId) {
        execute(autopilotId, AutopilotTriggerType.CRON);
    }

    /** Fires the autopilot and records the outcome. Returns the recorded run (may be null). */
    public AutopilotRun execute(Long autopilotId, AutopilotTriggerType trigger) {
        PerformResult result;
        try {
            result = workService.perform(autopilotId, trigger);
        } catch (Exception e) {
            log.error("Autopilot {} firing failed: {}", autopilotId, e.getMessage(), e);
            result = PerformResult.failed(e.getMessage());
        }

        AutopilotRun run = workService.record(autopilotId, trigger, result);
        log.info("Autopilot {} fired ({}) -> {}", autopilotId, trigger, result.status());
        return run;
    }
}

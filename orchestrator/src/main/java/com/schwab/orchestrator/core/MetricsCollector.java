package com.schwab.orchestrator.core;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Computes reliability metrics from a RunState snapshot: success rate, retry/rollback frequency, MTTR, e2e latency. */
public final class MetricsCollector {

    public RunMetrics collect(RunState run) {
        long succeeded = 0, failed = 0, rolledBack = 0, skipped = 0;
        int totalRetries = 0;
        List<Duration> recoveryTimes = new ArrayList<>();

        for (StageRuntimeInfo info : run.allStageInfo().values()) {
            switch (info.getStatus()) {
                case SUCCEEDED: succeeded++; break;
                case FAILED: failed++; break;
                case ROLLED_BACK: rolledBack++; break;
                case SKIPPED: skipped++; break;
                default: break; // still in flight; not counted in terminal buckets
            }
            totalRetries += info.getRetryCount();
            if (info.isEverFailed() && info.getStatus() == StageStatus.SUCCEEDED
                    && info.getFirstAttemptAt() != null && info.getTerminalAt() != null) {
                recoveryTimes.add(Duration.between(info.getFirstAttemptAt(), info.getTerminalAt()));
            }
        }

        Duration mttr = null;
        if (!recoveryTimes.isEmpty()) {
            long avgMillis = (long) recoveryTimes.stream().mapToLong(Duration::toMillis).average().orElse(0);
            mttr = Duration.ofMillis(avgMillis);
        }

        Duration e2e = run.getCompletedAt() == null ? null : Duration.between(run.getStartedAt(), run.getCompletedAt());

        return new RunMetrics(run.allStageInfo().size(), succeeded, failed, rolledBack, skipped, totalRetries, mttr, e2e);
    }
}

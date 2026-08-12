package com.schwab.orchestrator.core;

import java.time.Duration;

public final class RunMetrics {
    public final int totalStages;
    public final long succeeded;
    public final long failed;
    public final long rolledBack;
    public final long skipped;
    public final double successRate;
    public final int totalRetries;
    public final double retryFrequency;     // retries per stage
    public final double rollbackFrequency;  // rolled-back stages / total stages
    public final Duration meanTimeToRecover; // MTTR across stages that failed at least once then succeeded, nullable
    public final Duration endToEndLatency;   // nullable if run not yet complete

    public RunMetrics(int totalStages, long succeeded, long failed, long rolledBack, long skipped,
                       int totalRetries, Duration meanTimeToRecover, Duration endToEndLatency) {
        this.totalStages = totalStages;
        this.succeeded = succeeded;
        this.failed = failed;
        this.rolledBack = rolledBack;
        this.skipped = skipped;
        this.successRate = totalStages == 0 ? 0.0 : (double) succeeded / totalStages;
        this.totalRetries = totalRetries;
        this.retryFrequency = totalStages == 0 ? 0.0 : (double) totalRetries / totalStages;
        this.rollbackFrequency = totalStages == 0 ? 0.0 : (double) rolledBack / totalStages;
        this.meanTimeToRecover = meanTimeToRecover;
        this.endToEndLatency = endToEndLatency;
    }

    @Override
    public String toString() {
        return String.format(
                "stages=%d succeeded=%d failed=%d rolledBack=%d skipped=%d successRate=%.1f%% retries=%d retryFreq=%.2f rollbackFreq=%.2f mttr=%s e2eLatency=%s",
                totalStages, succeeded, failed, rolledBack, skipped, successRate * 100, totalRetries, retryFrequency,
                rollbackFrequency, meanTimeToRecover == null ? "n/a" : meanTimeToRecover.toMillis() + "ms",
                endToEndLatency == null ? "n/a" : endToEndLatency.toMillis() + "ms");
    }
}

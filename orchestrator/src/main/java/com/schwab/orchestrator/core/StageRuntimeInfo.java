package com.schwab.orchestrator.core;

import java.time.Instant;

/** Mutable per-stage bookkeeping used for status tracking and metrics (retry counts, timestamps, etc). */
public final class StageRuntimeInfo {
    private volatile StageStatus status = StageStatus.PENDING;
    private volatile int attempts = 0;
    private volatile int retryCount = 0;
    private volatile Instant firstAttemptAt;
    private volatile Instant terminalAt; // when it reached a terminal status (possibly re-set if re-planned)
    private volatile String lastMessage = "";
    private volatile boolean everFailed = false;
    private volatile int planVersion = 0; // bumped each time invalidate() re-plans this stage; gates re-approval

    public StageStatus getStatus() { return status; }
    public void setStatus(StageStatus status) { this.status = status; }
    public int getAttempts() { return attempts; }
    public void incrementAttempts() { attempts++; }
    public int getRetryCount() { return retryCount; }
    public void incrementRetryCount() { retryCount++; }
    public Instant getFirstAttemptAt() { return firstAttemptAt; }
    public void markFirstAttemptIfAbsent() { if (firstAttemptAt == null) firstAttemptAt = Instant.now(); }
    public Instant getTerminalAt() { return terminalAt; }
    public void markTerminalNow() { terminalAt = Instant.now(); }
    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
    public boolean isEverFailed() { return everFailed; }
    public void markEverFailed() { everFailed = true; }
    public int getPlanVersion() { return planVersion; }
    public void incrementPlanVersion() { planVersion++; }
}

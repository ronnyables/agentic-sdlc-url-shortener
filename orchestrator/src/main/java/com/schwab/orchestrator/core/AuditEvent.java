package com.schwab.orchestrator.core;

import java.time.Instant;

public final class AuditEvent {
    public final Instant timestamp;
    public final String runId;
    public final String stageId; // nullable for run-level events
    public final AuditEventType type;
    public final String actor;
    public final String detail;

    public AuditEvent(String runId, String stageId, AuditEventType type, String actor, String detail) {
        this.timestamp = Instant.now();
        this.runId = runId;
        this.stageId = stageId;
        this.type = type;
        this.actor = actor;
        this.detail = detail;
    }

    @Override
    public String toString() {
        return String.format("%s [%s] %-24s stage=%-20s actor=%-24s %s",
                timestamp, runId, type, stageId == null ? "-" : stageId, actor, detail);
    }
}

package com.schwab.orchestrator.core;

import java.time.Instant;

public final class PendingApproval {
    public final String stageId;
    public final String displayName;
    public final Instant requestedAt;
    public final String reason;

    public PendingApproval(String stageId, String displayName, String reason) {
        this.stageId = stageId;
        this.displayName = displayName;
        this.requestedAt = Instant.now();
        this.reason = reason;
    }
}

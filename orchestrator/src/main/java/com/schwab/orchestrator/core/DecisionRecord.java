package com.schwab.orchestrator.core;

import java.time.Instant;

/** A single entry in the cross-stage decision lineage (who decided what, and why). */
public final class DecisionRecord {
    public final Instant timestamp;
    public final String stageId;
    public final String actor;      // e.g. "agent:RequirementsAgent" or "human:reviewer1"
    public final String decision;   // short label, e.g. "NORMALIZED_REQUIREMENT" or "APPROVED_RELEASE"
    public final String rationale;

    public DecisionRecord(String stageId, String actor, String decision, String rationale) {
        this.timestamp = Instant.now();
        this.stageId = stageId;
        this.actor = actor;
        this.decision = decision;
        this.rationale = rationale;
    }

    @Override
    public String toString() {
        return timestamp + " [" + stageId + "] " + actor + " -> " + decision + " (" + rationale + ")";
    }
}

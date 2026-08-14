package com.schwab.orchestrator.core;

/** Lifecycle states for a single stage within one workflow run. */
public enum StageStatus {
    PENDING,            // not yet eligible to run (dependencies incomplete)
    READY,              // dependencies satisfied, about to be dispatched
    RUNNING,            // executor currently running (including retries)
    WAITING_APPROVAL,   // entry gate passed, blocked on a human decision
    SUCCEEDED,          // executor + exit gate + guardrails all passed
    FAILED,             // retries and fallback (if any) exhausted, or entry gate / guardrail rejected it permanently
    SKIPPED,            // blocked because an upstream dependency failed/was skipped/rolled back
    ROLLED_BACK,        // was previously SUCCEEDED, compensating action executed due to a downstream failure
    STALE               // was SUCCEEDED but an upstream artifact changed; scheduled for re-execution (re-plan)
}

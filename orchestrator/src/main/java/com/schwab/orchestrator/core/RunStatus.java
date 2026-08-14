package com.schwab.orchestrator.core;

public enum RunStatus {
    RUNNING,
    PAUSED_FOR_APPROVAL,
    SAFE_STOPPED,
    COMPLETED_SUCCESS,
    COMPLETED_WITH_FAILURES
}

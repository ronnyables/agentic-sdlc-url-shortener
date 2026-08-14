package com.schwab.orchestrator.core;

/** Undoes the committed effect of a previously-succeeded stage during a rollback cascade. */
@FunctionalInterface
public interface CompensatingAction {
    void compensate(WorkflowContext context) throws Exception;
}

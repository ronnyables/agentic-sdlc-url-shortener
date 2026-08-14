package com.schwab.orchestrator.core;

@FunctionalInterface
public interface StageExecutor {
    StageResult execute(WorkflowContext context, int attemptNumber) throws Exception;
}

package com.schwab.orchestrator.core;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Drives one workflow run to completion (or to a pause/safe-stop point).
 *
 * Scheduling model: rather than a single static topological pass, {@link #advance()}
 * recomputes the "ready" set on every call from current stage statuses. This is what
 * gives us, from one simple loop: parallel execution within a synchronization layer
 * (all ready stages in a round are submitted concurrently and joined), sequential
 * ordering across layers (a stage only becomes ready once every dependency is
 * SUCCEEDED), human-approval pausing (ready set naturally empties out around a
 * WAITING_APPROVAL stage until {@link #decideApproval} is called), rollback/skip
 * cascades (a FAILED or ROLLED_BACK dependency permanently blocks its dependents),
 * and dynamic re-planning (a stage marked STALE via {@link #invalidate} is picked
 * up by the exact same readiness check as a fresh PENDING stage).
 */
public final class WorkflowEngine {

    private final WorkflowGraph graph;
    private final WorkflowContext context;
    private final RunState runState;
    private final AuditTrail audit;
    private final GuardrailEngine guardrails;
    private final EngineConfig config;
    private final ExecutorService pool;

    public WorkflowEngine(WorkflowGraph graph, WorkflowContext context, AuditTrail audit,
                           GuardrailEngine guardrails, EngineConfig config, String scenarioName) {
        graph.validate();
        this.graph = graph;
        this.context = context;
        this.audit = audit;
        this.guardrails = guardrails;
        this.config = config;
        this.runState = new RunState(context.getRunId(), scenarioName, graph);
        this.pool = Executors.newFixedThreadPool(config.getParallelism(), r -> {
            Thread t = new Thread(r, "workflow-worker");
            t.setDaemon(true);
            return t;
        });
    }

    public RunState getRunState() { return runState; }
    public WorkflowContext getContext() { return context; }

    /** Starts (or resumes) execution, running until the graph is fully terminal, paused for approval, or safe-stopped. */
    public RunState advance() {
        if (runState.getStatus() == RunStatus.RUNNING) {
            audit.record(context.getRunId(), null, AuditEventType.RUN_STARTED, "system",
                    "workflow started with " + graph.size() + " stages");
        }
        runState.setStatus(RunStatus.RUNNING);

        while (true) {
            List<StageDefinition> ready = computeReadyStages();
            if (ready.isEmpty()) {
                if (!runState.getPendingApprovals().isEmpty()) {
                    runState.setStatus(RunStatus.PAUSED_FOR_APPROVAL);
                    return runState;
                }
                break; // nothing ready and nothing pending approval -> run has terminated
            }

            List<Future<Void>> futures = new ArrayList<>();
            for (StageDefinition stage : ready) {
                futures.add(pool.submit((Callable<Void>) () -> { runOneStage(stage); return null; }));
            }
            for (Future<Void> f : futures) {
                try {
                    f.get();
                } catch (Exception e) {
                    // Defensive: executeStage() already catches and records stage-level failures.
                    // This branch only triggers on engine-internal bugs, which we surface loudly.
                    throw new IllegalStateException("Internal scheduling failure", e);
                }
            }

            if (checkSafeStop()) {
                return runState;
            }
        }

        boolean anyUnrecovered = runState.allStageInfo().values().stream()
                .anyMatch(i -> i.getStatus() == StageStatus.FAILED || i.getStatus() == StageStatus.ROLLED_BACK);
        runState.markCompleted();
        runState.setStatus(anyUnrecovered ? RunStatus.COMPLETED_WITH_FAILURES : RunStatus.COMPLETED_SUCCESS);
        audit.record(context.getRunId(), null, AuditEventType.RUN_COMPLETED, "system",
                "final status=" + runState.getStatus());
        return runState;
    }

    /** Human decision on a stage currently WAITING_APPROVAL. Does not resume the run by itself - call advance() after. */
    public void decideApproval(String stageId, boolean approved, String approver, String comment) {
        StageRuntimeInfo info = runState.info(stageId);
        if (info == null || info.getStatus() != StageStatus.WAITING_APPROVAL) {
            throw new IllegalStateException("Stage '" + stageId + "' is not currently waiting for approval");
        }
        runState.removePendingApproval(stageId);
        if (approved) {
            context.recordDecision(stageId, "human:" + approver, "APPROVED#" + info.getPlanVersion(), comment);
            audit.record(context.getRunId(), stageId, AuditEventType.STAGE_APPROVED, "human:" + approver, comment);
            info.setStatus(StageStatus.PENDING); // becomes eligible again on the next advance()
        } else {
            context.recordDecision(stageId, "human:" + approver, "REJECTED", comment);
            audit.record(context.getRunId(), stageId, AuditEventType.STAGE_REJECTED, "human:" + approver, comment);
            failPermanently(stageId, info, "rejected by " + approver + ": " + comment);
        }
    }

    /**
     * Dynamic re-planning hook: call when an upstream artifact changes after stages
     * downstream of it have already succeeded. Marks the stage and all of its
     * transitive dependents STALE so the next advance() re-executes them.
     */
    public void invalidate(String stageId, String reason) {
        Set<String> toInvalidate = new LinkedHashSet<>();
        toInvalidate.add(stageId);
        toInvalidate.addAll(graph.transitiveDependents(stageId));
        for (String id : toInvalidate) {
            StageRuntimeInfo info = runState.info(id);
            if (info != null && (info.getStatus() == StageStatus.SUCCEEDED || info.getStatus() == StageStatus.SKIPPED)) {
                info.setStatus(StageStatus.STALE);
                info.incrementPlanVersion(); // any prior approval was for the old plan and no longer counts
                audit.record(context.getRunId(), id, AuditEventType.STAGE_INVALIDATED, "system", reason);
            }
        }
        if (runState.getStatus() != RunStatus.RUNNING) {
            runState.setStatus(RunStatus.RUNNING);
        }
    }

    public void shutdown() {
        pool.shutdownNow();
    }

    // ---------------------------------------------------------------- internals

    private List<StageDefinition> computeReadyStages() {
        List<StageDefinition> ready = new ArrayList<>();
        for (StageDefinition stage : graph.allStages()) {
            StageRuntimeInfo info = runState.info(stage.getId());
            StageStatus status = info.getStatus();
            if (status != StageStatus.PENDING && status != StageStatus.STALE) {
                continue;
            }
            boolean depsSatisfied = true;
            boolean depsBlocked = false;
            for (String dep : stage.getDependsOn()) {
                StageStatus depStatus = runState.info(dep).getStatus();
                if (depStatus == StageStatus.FAILED || depStatus == StageStatus.ROLLED_BACK) {
                    depsBlocked = true;
                } else if (depStatus != StageStatus.SUCCEEDED) {
                    depsSatisfied = false;
                }
            }
            if (depsBlocked) {
                info.setStatus(StageStatus.SKIPPED);
                info.markTerminalNow();
                audit.record(context.getRunId(), stage.getId(), AuditEventType.STAGE_SKIPPED, "system",
                        "one or more dependencies failed or were rolled back");
                continue;
            }
            if (depsSatisfied) {
                ready.add(stage);
            }
        }
        return ready;
    }

    private void runOneStage(StageDefinition stage) {
        StageRuntimeInfo info = runState.info(stage.getId());
        info.setStatus(StageStatus.RUNNING);
        audit.record(context.getRunId(), stage.getId(), AuditEventType.STAGE_ENTERED, "system", "entering stage");

        GateResult entry = stage.getEntryGate().evaluate(context);
        if (!entry.isPassed()) {
            audit.record(context.getRunId(), stage.getId(), AuditEventType.ENTRY_GATE_BLOCKED, "system", entry.getReason());
            failPermanently(stage.getId(), info, "entry gate blocked: " + entry.getReason());
            return;
        }

        if (stage.isRequiresApproval() && !alreadyApproved(stage.getId(), info)) {
            info.setStatus(StageStatus.WAITING_APPROVAL);
            runState.addPendingApproval(new PendingApproval(stage.getId(), stage.getDisplayName(), "high-impact action requires human sign-off"));
            audit.record(context.getRunId(), stage.getId(), AuditEventType.STAGE_WAITING_APPROVAL, "system",
                    "paused for human approval");
            return;
        }

        executeWithRetries(stage, info);
    }

    private boolean alreadyApproved(String stageId, StageRuntimeInfo info) {
        String requiredTag = "APPROVED#" + info.getPlanVersion();
        return context.decisionLineage().stream()
                .anyMatch(d -> d.stageId.equals(stageId) && d.decision.equals(requiredTag));
    }

    private void executeWithRetries(StageDefinition stage, StageRuntimeInfo info) {
        int attempt = 0;
        StageResult lastResult = null;
        while (attempt <= stage.getMaxRetries()) {
            attempt++;
            info.incrementAttempts();
            info.markFirstAttemptIfAbsent();
            audit.record(context.getRunId(), stage.getId(), AuditEventType.STAGE_ATTEMPT_STARTED, "agent:" + stage.getId(),
                    "attempt " + attempt + "/" + (stage.getMaxRetries() + 1));
            try {
                lastResult = stage.getExecutor().execute(context, attempt);
            } catch (Exception e) {
                lastResult = StageResult.failed("executor threw: " + e.getMessage(), e);
            }

            if (lastResult.isSuccess()) {
                if (tryFinalizeSuccess(stage, info, lastResult)) {
                    return;
                }
                // exit gate / guardrails blocked it -> treat as a failed attempt, eligible for retry
                lastResult = StageResult.failed("post-execution checks blocked the result");
            }

            info.markEverFailed();
            runState.incrementTotalFailures();
            audit.record(context.getRunId(), stage.getId(), AuditEventType.STAGE_ATTEMPT_FAILED, "agent:" + stage.getId(),
                    lastResult.getMessage());

            if (attempt <= stage.getMaxRetries()) {
                info.incrementRetryCount();
                long backoff = stage.getRetryBackoffMillis() * (1L << Math.min(attempt - 1, 6)); // bounded exponential backoff
                audit.record(context.getRunId(), stage.getId(), AuditEventType.STAGE_RETRY_SCHEDULED, "system",
                        "retrying in " + backoff + "ms (attempt " + (attempt + 1) + ")");
                sleep(backoff);
            }
        }

        // retries exhausted - try the fallback path once, if configured
        if (stage.getFallbackExecutor() != null) {
            audit.record(context.getRunId(), stage.getId(), AuditEventType.STAGE_FALLBACK_INVOKED, "system",
                    "primary executor exhausted retries, invoking fallback");
            try {
                StageResult fallbackResult = stage.getFallbackExecutor().execute(context, attempt + 1);
                if (fallbackResult.isSuccess() && tryFinalizeSuccess(stage, info, fallbackResult)) {
                    return;
                }
                lastResult = fallbackResult.isSuccess()
                        ? StageResult.failed("fallback result blocked by post-execution checks")
                        : fallbackResult;
            } catch (Exception e) {
                lastResult = StageResult.failed("fallback executor threw: " + e.getMessage(), e);
            }
        }

        failPermanently(stage.getId(), info, "exhausted retries and fallback: "
                + (lastResult == null ? "unknown error" : lastResult.getMessage()));
    }

    /** Runs exit gate + guardrails; if both pass, commits the stage as SUCCEEDED and merges its artifacts. Returns false if blocked. */
    private boolean tryFinalizeSuccess(StageDefinition stage, StageRuntimeInfo info, StageResult result) {
        GateResult exit = stage.getExitGate().evaluate(context, result);
        if (!exit.isPassed()) {
            audit.record(context.getRunId(), stage.getId(), AuditEventType.EXIT_GATE_BLOCKED, "system", exit.getReason());
            return false;
        }
        for (GuardrailResult gr : guardrails.evaluate(context, stage, result)) {
            if (!gr.isPassed()) {
                audit.record(context.getRunId(), stage.getId(), AuditEventType.GUARDRAIL_BLOCKED, "guardrail:" + gr.getGuardrailName(), gr.getMessage());
                return false;
            }
        }
        result.getArtifacts().forEach((k, v) -> context.putArtifact(stage.getId() + "." + k, v));
        info.setStatus(StageStatus.SUCCEEDED);
        info.markTerminalNow();
        audit.record(context.getRunId(), stage.getId(), AuditEventType.STAGE_SUCCEEDED, "agent:" + stage.getId(), result.getMessage());
        return true;
    }

    private void failPermanently(String stageId, StageRuntimeInfo info, String reason) {
        info.setStatus(StageStatus.FAILED);
        info.markTerminalNow();
        info.setLastMessage(reason);
        audit.record(context.getRunId(), stageId, AuditEventType.STAGE_FAILED, "system", reason);
        cascadeRollback(stageId, reason);
    }

    /** Walks direct dependencies of a failed stage and rolls back any that are rollbackable and currently SUCCEEDED. */
    private void cascadeRollback(String failedStageId, String reason) {
        StageDefinition failedStage = graph.get(failedStageId);
        Deque<String> toConsider = new ArrayDeque<>(failedStage.getDependsOn());
        Set<String> visited = new LinkedHashSet<>();
        while (!toConsider.isEmpty()) {
            String depId = toConsider.poll();
            if (!visited.add(depId)) continue;
            StageDefinition dep = graph.get(depId);
            StageRuntimeInfo depInfo = runState.info(depId);
            if (dep.isRollbackable() && depInfo.getStatus() == StageStatus.SUCCEEDED) {
                try {
                    dep.getCompensatingAction().compensate(context);
                    depInfo.setStatus(StageStatus.ROLLED_BACK);
                    depInfo.markTerminalNow();
                    audit.record(context.getRunId(), depId, AuditEventType.STAGE_ROLLED_BACK, "system",
                            "compensating for downstream failure at '" + failedStageId + "': " + reason);
                    toConsider.addAll(dep.getDependsOn()); // cascade further upstream
                } catch (Exception e) {
                    audit.record(context.getRunId(), depId, AuditEventType.STAGE_ROLLED_BACK, "system",
                            "compensating action itself failed: " + e.getMessage());
                }
            }
        }
    }

    private boolean checkSafeStop() {
        if (runState.getTotalFailures() >= config.getMaxTotalFailuresBeforeSafeStop()
                && runState.getStatus() != RunStatus.SAFE_STOPPED) {
            runState.setStatus(RunStatus.SAFE_STOPPED);
            audit.record(context.getRunId(), null, AuditEventType.SAFE_STOP_TRIGGERED, "system",
                    "total failures (" + runState.getTotalFailures() + ") reached safe-stop threshold ("
                            + config.getMaxTotalFailuresBeforeSafeStop() + ") - halting remaining work for human review");
            for (StageDefinition s : graph.allStages()) {
                StageRuntimeInfo info = runState.info(s.getId());
                if (info.getStatus() == StageStatus.PENDING || info.getStatus() == StageStatus.STALE) {
                    info.setStatus(StageStatus.SKIPPED);
                    info.markTerminalNow();
                    audit.record(context.getRunId(), s.getId(), AuditEventType.STAGE_SKIPPED, "system", "safe-stop in effect");
                }
            }
            runState.markCompleted();
            return true;
        }
        return false;
    }

    private void sleep(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

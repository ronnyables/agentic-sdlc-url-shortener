package com.schwab.orchestrator.core;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RunState {
    private final String runId;
    private final String scenarioName;
    private final Instant startedAt = Instant.now();
    private volatile Instant completedAt;
    private volatile RunStatus status = RunStatus.RUNNING;
    private final Map<String, StageRuntimeInfo> stageInfo = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<PendingApproval> pendingApprovals = new CopyOnWriteArrayList<>();
    private volatile int totalFailures = 0;

    public RunState(String runId, String scenarioName, WorkflowGraph graph) {
        this.runId = runId;
        this.scenarioName = scenarioName;
        for (StageDefinition s : graph.allStages()) {
            stageInfo.put(s.getId(), new StageRuntimeInfo());
        }
    }

    public String getRunId() { return runId; }
    public String getScenarioName() { return scenarioName; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void markCompleted() { this.completedAt = Instant.now(); }
    public RunStatus getStatus() { return status; }
    public void setStatus(RunStatus status) { this.status = status; }
    public StageRuntimeInfo info(String stageId) { return stageInfo.get(stageId); }
    public Map<String, StageRuntimeInfo> allStageInfo() { return Map.copyOf(stageInfo); }
    public List<PendingApproval> getPendingApprovals() { return List.copyOf(pendingApprovals); }
    public void addPendingApproval(PendingApproval approval) { pendingApprovals.add(approval); }
    public void removePendingApproval(String stageId) { pendingApprovals.removeIf(p -> p.stageId.equals(stageId)); }
    public void incrementTotalFailures() { totalFailures++; }
    public int getTotalFailures() { return totalFailures; }

    public Map<String, String> statusSnapshot() {
        Map<String, String> snapshot = new LinkedHashMap<>();
        stageInfo.forEach((k, v) -> snapshot.put(k, v.getStatus().name()));
        return snapshot;
    }
}

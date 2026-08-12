package com.schwab.orchestrator.core;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Shared, mutable state that flows across stages: the artifact bag (outputs
 * each stage contributes and later stages consume) and the append-only
 * decision lineage. One instance per workflow run.
 */
public final class WorkflowContext {

    private final String runId;
    private final String rawRequirement;
    private final ConcurrentHashMap<String, Object> artifacts = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<DecisionRecord> decisionLog = new CopyOnWriteArrayList<>();

    public WorkflowContext(String runId, String rawRequirement) {
        this.runId = runId;
        this.rawRequirement = rawRequirement;
    }

    public String getRunId() { return runId; }
    public String getRawRequirement() { return rawRequirement; }

    public void putArtifact(String key, Object value) {
        artifacts.put(key, value);
    }

    /** Returns true if this write actually changed a previously-set value (relevant for re-planning). */
    public boolean putArtifactIfChanged(String key, Object value) {
        Object previous = artifacts.put(key, value);
        return previous == null || !Objects.equals(previous, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getArtifact(String key) {
        return (T) artifacts.get(key);
    }

    public <T> T getArtifact(String key, T defaultValue) {
        Object v = artifacts.get(key);
        return v == null ? defaultValue : (T) v;
    }

    public Map<String, Object> allArtifacts() {
        return Map.copyOf(artifacts);
    }

    public void recordDecision(String stageId, String actor, String decision, String rationale) {
        decisionLog.add(new DecisionRecord(stageId, actor, decision, rationale));
    }

    public List<DecisionRecord> decisionLineage() {
        return List.copyOf(decisionLog);
    }
}

package com.schwab.orchestrator.core;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Immutable, declarative description of one node in the workflow DAG. */
public final class StageDefinition {

    private final String id;
    private final String displayName;
    private final Set<String> dependsOn;
    private final Gate entryGate;
    private final ExitGate exitGate;
    private final boolean requiresApproval;
    private final int maxRetries;
    private final long retryBackoffMillis;
    private final StageExecutor executor;
    private final StageExecutor fallbackExecutor; // nullable
    private final CompensatingAction compensatingAction; // nullable - null means "not rollbackable"
    private final List<String> guardrailNames; // guardrail ids from the GuardrailEngine registry to apply at exit

    private StageDefinition(Builder b) {
        this.id = b.id;
        this.displayName = b.displayName;
        this.dependsOn = new LinkedHashSet<>(b.dependsOn);
        this.entryGate = b.entryGate;
        this.exitGate = b.exitGate;
        this.requiresApproval = b.requiresApproval;
        this.maxRetries = b.maxRetries;
        this.retryBackoffMillis = b.retryBackoffMillis;
        this.executor = b.executor;
        this.fallbackExecutor = b.fallbackExecutor;
        this.compensatingAction = b.compensatingAction;
        this.guardrailNames = List.copyOf(b.guardrailNames);
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public Set<String> getDependsOn() { return dependsOn; }
    public Gate getEntryGate() { return entryGate; }
    public ExitGate getExitGate() { return exitGate; }
    public boolean isRequiresApproval() { return requiresApproval; }
    public int getMaxRetries() { return maxRetries; }
    public long getRetryBackoffMillis() { return retryBackoffMillis; }
    public StageExecutor getExecutor() { return executor; }
    public StageExecutor getFallbackExecutor() { return fallbackExecutor; }
    public CompensatingAction getCompensatingAction() { return compensatingAction; }
    public boolean isRollbackable() { return compensatingAction != null; }
    public List<String> getGuardrailNames() { return guardrailNames; }

    public static Builder builder(String id, String displayName) {
        return new Builder(id, displayName);
    }

    public static final class Builder {
        private final String id;
        private final String displayName;
        private final Set<String> dependsOn = new LinkedHashSet<>();
        private Gate entryGate = Gate.ALWAYS_PASS;
        private ExitGate exitGate = ExitGate.ALWAYS_PASS;
        private boolean requiresApproval = false;
        private int maxRetries = 1;
        private long retryBackoffMillis = 50;
        private StageExecutor executor;
        private StageExecutor fallbackExecutor;
        private CompensatingAction compensatingAction;
        private final Set<String> guardrailNames = new LinkedHashSet<>();

        private Builder(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public Builder dependsOn(String... stageIds) {
            this.dependsOn.addAll(List.of(stageIds));
            return this;
        }

        public Builder entryGate(Gate gate) { this.entryGate = gate; return this; }
        public Builder exitGate(ExitGate gate) { this.exitGate = gate; return this; }
        public Builder requiresApproval(boolean value) { this.requiresApproval = value; return this; }
        public Builder maxRetries(int value) { this.maxRetries = value; return this; }
        public Builder retryBackoffMillis(long value) { this.retryBackoffMillis = value; return this; }
        public Builder executor(StageExecutor executor) { this.executor = executor; return this; }
        public Builder fallbackExecutor(StageExecutor fallback) { this.fallbackExecutor = fallback; return this; }
        public Builder compensatingAction(CompensatingAction action) { this.compensatingAction = action; return this; }
        public Builder guardrails(String... names) { this.guardrailNames.addAll(List.of(names)); return this; }

        public StageDefinition build() {
            if (executor == null) throw new IllegalStateException("Stage " + id + " has no executor");
            return new StageDefinition(this);
        }
    }
}

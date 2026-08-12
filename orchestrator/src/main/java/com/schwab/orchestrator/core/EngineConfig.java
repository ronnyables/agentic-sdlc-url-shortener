package com.schwab.orchestrator.core;

/** Run-level governance controls: how much autonomy the engine has before it must stop and ask for help. */
public final class EngineConfig {
    private final int maxTotalFailuresBeforeSafeStop;
    private final int parallelism;

    public EngineConfig(int maxTotalFailuresBeforeSafeStop, int parallelism) {
        this.maxTotalFailuresBeforeSafeStop = maxTotalFailuresBeforeSafeStop;
        this.parallelism = parallelism;
    }

    public static EngineConfig defaults() {
        return new EngineConfig(3, 4);
    }

    public int getMaxTotalFailuresBeforeSafeStop() { return maxTotalFailuresBeforeSafeStop; }
    public int getParallelism() { return parallelism; }
}

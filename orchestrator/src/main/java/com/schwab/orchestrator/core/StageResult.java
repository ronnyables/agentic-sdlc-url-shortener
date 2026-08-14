package com.schwab.orchestrator.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** Outcome of one stage-executor invocation (one attempt). */
public final class StageResult {

    private final boolean success;
    private final String message;
    private final Map<String, Object> artifacts;
    private final Throwable error;

    private StageResult(boolean success, String message, Map<String, Object> artifacts, Throwable error) {
        this.success = success;
        this.message = message;
        this.artifacts = artifacts;
        this.error = error;
    }

    public static StageResult ok(String message, Map<String, Object> artifacts) {
        return new StageResult(true, message, artifacts, null);
    }

    public static StageResult ok(String message) {
        return new StageResult(true, message, new LinkedHashMap<>(), null);
    }

    public static StageResult failed(String message) {
        return new StageResult(false, message, Map.of(), null);
    }

    public static StageResult failed(String message, Throwable error) {
        return new StageResult(false, message, Map.of(), error);
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public Map<String, Object> getArtifacts() { return artifacts; }
    public Throwable getError() { return error; }
}

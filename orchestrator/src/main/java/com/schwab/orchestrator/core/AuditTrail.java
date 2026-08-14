package com.schwab.orchestrator.core;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Append-only, thread-safe audit log providing audit-grade observability/traceability for a run. */
public final class AuditTrail {

    private final CopyOnWriteArrayList<AuditEvent> events = new CopyOnWriteArrayList<>();

    public void record(String runId, String stageId, AuditEventType type, String actor, String detail) {
        events.add(new AuditEvent(runId, stageId, type, actor, detail));
    }

    public List<AuditEvent> events() {
        return Collections.unmodifiableList(events);
    }

    public List<AuditEvent> forStage(String stageId) {
        return events.stream().filter(e -> stageId.equals(e.stageId)).collect(java.util.stream.Collectors.toList());
    }

    public String renderTimeline() {
        StringBuilder sb = new StringBuilder();
        for (AuditEvent e : events) {
            sb.append(e).append(System.lineSeparator());
        }
        return sb.toString();
    }
}

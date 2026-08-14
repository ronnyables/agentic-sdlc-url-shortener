package com.schwab.orchestrator.agents;

import com.schwab.orchestrator.core.StageExecutor;
import com.schwab.orchestrator.core.StageResult;
import com.schwab.orchestrator.core.WorkflowContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts the design into an explicit, ordered/parallel task breakdown with
 * dependencies - the artifact a human reviewer would use to see the actual
 * decomposition the agent chose, independent of how the orchestration engine
 * itself schedules the SDLC-stage graph.
 */
public final class TaskDecompositionAgent implements StageExecutor {

    @SuppressWarnings("unchecked")
    @Override
    public StageResult execute(WorkflowContext context, int attemptNumber) {
        List<String> apiChanges = context.getArtifact("design.apiChanges");
        List<String> dataModelChanges = context.getArtifact("design.dataModelChanges");
        if (apiChanges == null) apiChanges = List.of();
        if (dataModelChanges == null) dataModelChanges = List.of();

        List<Map<String, Object>> tasks = new ArrayList<>();
        int taskId = 1;

        for (String change : dataModelChanges) {
            tasks.add(task(taskId++, "Data model: " + change, List.of()));
        }
        List<String> dataModelTaskIds = tasks.stream().map(t -> (String) t.get("id")).collect(java.util.stream.Collectors.toList());

        for (String change : apiChanges) {
            tasks.add(task(taskId++, "Implement: " + change, dataModelTaskIds));
        }
        List<String> implTaskIds = tasks.stream()
                .filter(t -> ((String) t.get("description")).startsWith("Implement"))
                .map(t -> (String) t.get("id"))
                .collect(java.util.stream.Collectors.toList());

        // Testing and documentation both depend on implementation but not on each other - parallelizable.
        String testTaskId = "T" + taskId++;
        tasks.add(taskWithId(testTaskId, "Write/extend unit + integration tests for the change", implTaskIds));
        String docTaskId = "T" + taskId++;
        tasks.add(taskWithId(docTaskId, "Update API docs and README for the change", implTaskIds));

        String releaseTaskId = "T" + taskId++;
        tasks.add(taskWithId(releaseTaskId, "Release-readiness review and sign-off", List.of(testTaskId, docTaskId)));

        Map<String, Object> artifacts = new LinkedHashMap<>();
        artifacts.put("taskCount", tasks.size());
        artifacts.put("tasks", tasks);
        artifacts.put("parallelizableAfterImplementation", List.of(testTaskId, docTaskId));

        context.recordDecision("task-decomposition", "agent:TaskDecompositionAgent", "DECOMPOSED",
                tasks.size() + " tasks derived from " + apiChanges.size() + " API change(s) and "
                        + dataModelChanges.size() + " data model change(s); testing/docs parallelized, release gated on both");

        return StageResult.ok("Decomposed into " + tasks.size() + " tasks", artifacts);
    }

    private Map<String, Object> task(int id, String description, List<String> dependsOn) {
        return taskWithId("T" + id, description, dependsOn);
    }

    private Map<String, Object> taskWithId(String id, String description, List<String> dependsOn) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("id", id);
        t.put("description", description);
        t.put("dependsOn", dependsOn);
        return t;
    }
}

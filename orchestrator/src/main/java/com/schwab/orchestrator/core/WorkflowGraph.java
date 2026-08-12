package com.schwab.orchestrator.core;

import java.util.*;

/** Explicit dependency graph (DAG) of stages, validated for missing/cyclic dependencies. */
public final class WorkflowGraph {

    private final Map<String, StageDefinition> stages = new LinkedHashMap<>();

    public WorkflowGraph addStage(StageDefinition stage) {
        if (stages.containsKey(stage.getId())) {
            throw new IllegalArgumentException("Duplicate stage id: " + stage.getId());
        }
        stages.put(stage.getId(), stage);
        return this;
    }

    public Collection<StageDefinition> allStages() {
        return stages.values();
    }

    public StageDefinition get(String id) {
        StageDefinition s = stages.get(id);
        if (s == null) throw new NoSuchElementException("Unknown stage id: " + id);
        return s;
    }

    public boolean contains(String id) {
        return stages.containsKey(id);
    }

    public Set<String> directDependents(String id) {
        Set<String> result = new LinkedHashSet<>();
        for (StageDefinition s : stages.values()) {
            if (s.getDependsOn().contains(id)) {
                result.add(s.getId());
            }
        }
        return result;
    }

    /** All transitive dependents (stages that would be blocked if `id` never succeeds). */
    public Set<String> transitiveDependents(String id) {
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>(directDependents(id));
        while (!queue.isEmpty()) {
            String next = queue.poll();
            if (visited.add(next)) {
                queue.addAll(directDependents(next));
            }
        }
        return visited;
    }

    /** Validates that every dependency exists and the graph is acyclic. Throws IllegalStateException otherwise. */
    public void validate() {
        for (StageDefinition s : stages.values()) {
            for (String dep : s.getDependsOn()) {
                if (!stages.containsKey(dep)) {
                    throw new IllegalStateException("Stage '" + s.getId() + "' depends on unknown stage '" + dep + "'");
                }
            }
        }
        // Kahn's algorithm doubles as cycle detection.
        Map<String, Integer> inDegree = new HashMap<>();
        for (StageDefinition s : stages.values()) inDegree.put(s.getId(), s.getDependsOn().size());
        Deque<String> ready = new ArrayDeque<>();
        for (var e : inDegree.entrySet()) if (e.getValue() == 0) ready.add(e.getKey());
        int visited = 0;
        while (!ready.isEmpty()) {
            String id = ready.poll();
            visited++;
            for (String dependent : directDependents(id)) {
                int updated = inDegree.merge(dependent, -1, Integer::sum);
                if (updated == 0) ready.add(dependent);
            }
        }
        if (visited != stages.size()) {
            throw new IllegalStateException("Workflow graph contains a cycle - not a valid DAG");
        }
    }

    /** Stages grouped into sequential layers; within a layer, stages are independent and may run in parallel. */
    public List<List<StageDefinition>> topologicalLayers() {
        validate();
        Map<String, Integer> inDegree = new HashMap<>();
        for (StageDefinition s : stages.values()) inDegree.put(s.getId(), s.getDependsOn().size());
        List<List<StageDefinition>> layers = new ArrayList<>();
        Set<String> done = new HashSet<>();
        while (done.size() < stages.size()) {
            List<StageDefinition> layer = new ArrayList<>();
            for (StageDefinition s : stages.values()) {
                if (!done.contains(s.getId()) && done.containsAll(s.getDependsOn())) {
                    layer.add(s);
                }
            }
            if (layer.isEmpty()) {
                throw new IllegalStateException("Unable to make progress building layers - check graph for cycles");
            }
            layers.add(layer);
            for (StageDefinition s : layer) done.add(s.getId());
        }
        return layers;
    }

    public int size() { return stages.size(); }
}

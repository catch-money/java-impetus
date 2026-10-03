package io.github.jockerCN.flow;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable dependency graph, validated once when a flow definition is built. */
final class FlowGraph {

    private final Map<String, Set<String>> dependencies;
    private final Map<String, Set<String>> dependents;

    FlowGraph(Map<String, Set<String>> dependsOn) {
        Objects.requireNonNull(dependsOn, "dependsOn");
        Map<String, Set<String>> parents = new LinkedHashMap<>();
        Map<String, Set<String>> children = new LinkedHashMap<>();
        dependsOn.forEach((id, refs) -> {
            Objects.requireNonNull(id, "node id");
            Objects.requireNonNull(refs, "dependencies");
            parents.put(id, Collections.unmodifiableSet(new LinkedHashSet<>(refs)));
            children.put(id, new LinkedHashSet<>());
        });
        for (Map.Entry<String, Set<String>> entry : parents.entrySet()) {
            for (String parent : entry.getValue()) {
                if (!parents.containsKey(parent)) {
                    throw new IllegalArgumentException("Unknown dependency " + parent + " for " + entry.getKey());
                }
                children.get(parent).add(entry.getKey());
            }
        }
        Map<String, Integer> remaining = new HashMap<>();
        ArrayDeque<String> ready = new ArrayDeque<>();
        for (String id : parents.keySet()) {
            int count = parents.get(id).size();
            remaining.put(id, count);
            if (count == 0) {
                ready.addLast(id);
            }
        }
        int visited = 0;
        while (!ready.isEmpty()) {
            String id = ready.removeFirst();
            visited++;
            for (String child : children.get(id)) {
                int count = remaining.merge(child, -1, Integer::sum);
                if (count == 0) {
                    ready.addLast(child);
                }
            }
        }
        if (visited != parents.size()) {
            throw new IllegalArgumentException("dependsOn contains a cycle");
        }
        children.replaceAll((id, refs) -> Collections.unmodifiableSet(refs));
        dependencies = Collections.unmodifiableMap(parents);
        dependents = Collections.unmodifiableMap(children);
    }

    Set<String> ids() {
        return dependencies.keySet();
    }

    Set<String> dependencies(String id) {
        return dependencies.get(id);
    }

    Set<String> dependents(String id) {
        return dependents.get(id);
    }

    boolean reaches(String from, String target) {
        ArrayDeque<String> pending = new ArrayDeque<>(dependents(from));
        Set<String> visited = new HashSet<>();
        while (!pending.isEmpty()) {
            String next = pending.removeFirst();
            if (next.equals(target)) {
                return true;
            }
            if (visited.add(next)) {
                pending.addAll(dependents(next));
            }
        }
        return false;
    }

}

package net.monacraft.mwd.compiler;

import net.monacraft.mwd.config.PackAssignment;
import net.monacraft.mwd.pack.DatapackAnalysis;
import net.monacraft.mwd.resource.*;
import java.util.*;

public final class ConflictResolver {
    public Resolution resolve(List<PackAssignment> assignments, Map<String, DatapackAnalysis> analyses) {
        Map<String, Integer> priorities = new HashMap<>(); assignments.forEach(p -> priorities.put(p.id(), p.priority()));
        Map<ResourceKey, List<ResourceNode>> candidates = new LinkedHashMap<>();
        for (PackAssignment assignment : assignments) {
            DatapackAnalysis analysis = analyses.get(assignment.id());
            if (analysis == null) continue;
            for (ResourceNode node : analysis.resources()) if (node.key().type().worldgen())
                candidates.computeIfAbsent(node.key(), ignored -> new ArrayList<>()).add(node);
        }
        Map<ResourceKey, ResourceNode> winners = new LinkedHashMap<>(); List<ResourceConflict> conflicts = new ArrayList<>();
        candidates.forEach((key, nodes) -> {
            List<ResourceNode> sorted = nodes.stream().sorted(Comparator.comparingInt((ResourceNode n) -> priorities.getOrDefault(n.provider(), 0)).reversed().thenComparing(ResourceNode::provider)).toList();
            winners.put(key, sorted.getFirst());
            if (sorted.size() > 1) conflicts.add(new ResourceConflict(key, sorted.stream().map(ResourceNode::provider).toList(),
                    sorted.getFirst().provider(), "Higher priority (ties resolve by pack id)"));
        });
        return new Resolution(Collections.unmodifiableMap(winners), List.copyOf(conflicts));
    }
    public record Resolution(Map<ResourceKey, ResourceNode> resources, List<ResourceConflict> conflicts) {}
}


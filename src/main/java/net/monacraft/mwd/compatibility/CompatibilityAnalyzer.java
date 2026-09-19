package net.monacraft.mwd.compatibility;

import net.monacraft.mwd.resource.*;
import java.util.*;

public final class CompatibilityAnalyzer {
    public CompatibilityReport analyze(String packId, Collection<ResourceNode> nodes, ResourceGraph graph,
                                       Set<String> namespaces, List<String> baseProblems, List<String> unknownRefs) {
        EnumMap<ScopeClass, Integer> counts = new EnumMap<>(ScopeClass.class);
        List<ResourceKey> unknown = new ArrayList<>();
        for (ResourceNode node : nodes) {
            ScopeClass scope = node.key().type().scope();
            counts.merge(scope, 1, Integer::sum);
            if (scope == ScopeClass.UNKNOWN) unknown.add(node.key());
        }
        List<String> problems = new ArrayList<>(baseProblems);
        for (ResourceReference missing : graph.missingPackReferences(namespaces))
            problems.add("Missing reference: " + missing.source() + " " + missing.jsonPath() + " -> " + missing.target());
        for (List<ResourceKey> cycle : graph.cycles()) problems.add("Reference cycle: " + cycle);
        CompatibilityLevel level;
        if (!problems.isEmpty() || !unknown.isEmpty()) level = CompatibilityLevel.UNSUPPORTED;
        else if (counts.getOrDefault(ScopeClass.SERVER_GLOBAL, 0) > 0) level = CompatibilityLevel.REQUIRES_GLOBAL_RESOURCES;
        else if (counts.getOrDefault(ScopeClass.RUNTIME_SCOPABLE, 0) > 0 || !unknownRefs.isEmpty()) level = CompatibilityLevel.PARTIAL;
        else level = CompatibilityLevel.SUPPORTED;
        return new CompatibilityReport(packId, level, Collections.unmodifiableMap(counts), List.copyOf(problems),
                List.copyOf(unknown), List.copyOf(unknownRefs));
    }
}


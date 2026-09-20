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
        for (ResourceReference missing : graph.missingPackReferences(namespaces)) {
            if (missing.source().type().worldgen())
                problems.add("Missing reference: " + missing.source() + " " + missing.jsonPath() + " -> " + missing.target());
        }
        for (List<ResourceKey> cycle : graph.cycles()) {
            // The untyped JSON reference graph can resolve e.g. a placed
            // feature to a configured feature with the same location as
            // itself. Those two-entry self paths are not actual recursion.
            if (!isApparentSelfReference(cycle) && cycle.stream().anyMatch(key -> key.type().worldgen()))
                problems.add("Reference cycle: " + cycle);
        }
        boolean unsafeUnknown = unknown.stream().anyMatch(CompatibilityReport::isUnsafeUnknownResource);
        CompatibilityLevel level;
        if (!problems.isEmpty() || unsafeUnknown) level = CompatibilityLevel.UNSUPPORTED;
        else if (counts.getOrDefault(ScopeClass.SERVER_GLOBAL, 0) > 0) level = CompatibilityLevel.REQUIRES_GLOBAL_RESOURCES;
        else if (counts.getOrDefault(ScopeClass.RUNTIME_SCOPABLE, 0) > 0
                || counts.getOrDefault(ScopeClass.UNKNOWN, 0) > 0 || !unknownRefs.isEmpty()) level = CompatibilityLevel.PARTIAL;
        else level = CompatibilityLevel.SUPPORTED;
        return new CompatibilityReport(packId, level, Collections.unmodifiableMap(counts), List.copyOf(problems),
                List.copyOf(unknown), List.copyOf(unknownRefs));
    }

    private static boolean isApparentSelfReference(List<ResourceKey> cycle) {
        return cycle.size() == 2 && cycle.getFirst().equals(cycle.getLast());
    }

}

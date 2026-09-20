package net.monacraft.mwd.compatibility;

import net.monacraft.mwd.resource.ResourceKey;
import java.util.*;

public record CompatibilityReport(
        String packId,
        CompatibilityLevel result,
        Map<ScopeClass, Integer> counts,
        List<String> problems,
        List<ResourceKey> unknownResources,
        List<String> unknownReferences
) {
    /**
     * Whether the world-generation subset can be cloned without enabling the
     * source pack's server-global resources. Non-worldgen resources are
     * deliberately omitted by SCOPED_WORLDGEN and therefore do not make the
     * generated pack unsafe by themselves.
     */
    public boolean safeForScopedCompilation() {
        return problems.isEmpty() && unsafeScopedUnknownResources().isEmpty();
    }

    public List<ResourceKey> unsafeScopedUnknownResources() {
        return unknownResources.stream()
                .filter(CompatibilityReport::looksLikeWorldgenResource)
                .toList();
    }

    public boolean safeForStrictMode() {
        return safeForScopedCompilation();
    }

    private static boolean looksLikeWorldgenResource(ResourceKey key) {
        String path = key.location().path();
        return path.equals("worldgen") || path.startsWith("worldgen/")
                || path.equals("dimension") || path.startsWith("dimension/")
                || path.equals("dimension_type") || path.startsWith("dimension_type/");
    }
}

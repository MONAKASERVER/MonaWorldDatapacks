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
    public boolean safeForStrictMode() {
        return result == CompatibilityLevel.SUPPORTED && problems.isEmpty()
                && unknownResources.isEmpty() && unknownReferences.isEmpty()
                && counts.getOrDefault(ScopeClass.SERVER_GLOBAL, 0) == 0;
    }
}

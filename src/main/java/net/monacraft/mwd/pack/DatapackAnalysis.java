package net.monacraft.mwd.pack;

import net.monacraft.mwd.compatibility.CompatibilityReport;
import net.monacraft.mwd.resource.*;
import java.nio.file.Path;
import java.util.*;

public record DatapackAnalysis(String id, Path archive, String sha256, DatapackMetadata metadata,
                               Set<String> namespaces, List<ResourceNode> resources,
                               ResourceGraph graph, List<String> errors, List<String> unknownReferences,
                               CompatibilityReport compatibility) {
    public long count(ResourceType type) { return resources.stream().filter(r -> r.key().type() == type).count(); }
    public long worldgenCount() { return resources.stream().filter(r -> r.key().type().worldgen()).count(); }
}


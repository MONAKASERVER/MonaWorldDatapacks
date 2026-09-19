package net.monacraft.mwd.compiler;

import net.monacraft.mwd.resource.*;
import java.util.*;

public final class NamespaceMapper {
    private final String targetNamespace;
    private final Map<ResourceKey, ResourceLocation> mappings;
    private final Map<ResourceLocation, ResourceLocation> unambiguous;

    public NamespaceMapper(String targetNamespace, Collection<ResourceKey> keys) {
        this.targetNamespace = targetNamespace;
        Map<ResourceKey, ResourceLocation> result = new LinkedHashMap<>();
        Set<String> claimed = new HashSet<>();
        for (ResourceKey key : keys.stream().sorted().toList()) {
            String candidate = key.location().path();
            String claim = key.type() + ":" + candidate;
            if (!claimed.add(claim)) candidate = key.location().namespace() + '/' + candidate;
            result.put(key, new ResourceLocation(targetNamespace, candidate));
        }
        mappings = Collections.unmodifiableMap(result);
        Map<ResourceLocation, List<ResourceLocation>> byLocation = new HashMap<>();
        result.forEach((key, value) -> byLocation.computeIfAbsent(key.location(), ignored -> new ArrayList<>()).add(value));
        Map<ResourceLocation, ResourceLocation> simple = new HashMap<>();
        byLocation.forEach((key, values) -> { if (values.stream().distinct().count() == 1) simple.put(key, values.getFirst()); });
        unambiguous = Collections.unmodifiableMap(simple);
    }
    public String targetNamespace() { return targetNamespace; }
    public ResourceLocation map(ResourceKey key) { return mappings.getOrDefault(key, key.location()); }
    public ResourceLocation mapReference(ResourceLocation location) { return unambiguous.getOrDefault(location, location); }
    public Map<ResourceKey, ResourceLocation> mappings() { return mappings; }
}


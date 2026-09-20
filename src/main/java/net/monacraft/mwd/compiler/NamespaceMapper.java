package net.monacraft.mwd.compiler;

import net.monacraft.mwd.resource.*;
import java.util.*;

public final class NamespaceMapper {
    private final String targetNamespace;
    private final Map<ResourceKey, ResourceLocation> mappings;
    private final Map<ResourceLocation, ResourceLocation> unambiguousValues;
    private final Map<ResourceLocation, ResourceLocation> unambiguousTags;

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
        unambiguousValues = unambiguous(result, false);
        unambiguousTags = unambiguous(result, true);
    }
    public String targetNamespace() { return targetNamespace; }
    public ResourceLocation map(ResourceKey key) { return mappings.getOrDefault(key, key.location()); }
    public ResourceLocation mapReference(ResourceLocation location) { return mapReference(location, false); }
    public ResourceLocation mapReference(ResourceType expectedType, ResourceLocation location) {
        return mappings.getOrDefault(new ResourceKey(expectedType, location), location);
    }
    public ResourceLocation mapReference(ResourceLocation location, boolean tag) {
        return (tag ? unambiguousTags : unambiguousValues).getOrDefault(location, location);
    }
    public Map<ResourceKey, ResourceLocation> mappings() { return mappings; }

    private static Map<ResourceLocation, ResourceLocation> unambiguous(Map<ResourceKey, ResourceLocation> mappings, boolean tags) {
        Map<ResourceLocation, List<ResourceLocation>> byLocation = new HashMap<>();
        mappings.forEach((key, value) -> {
            if (key.type().tag() == tags)
                byLocation.computeIfAbsent(key.location(), ignored -> new ArrayList<>()).add(value);
        });
        Map<ResourceLocation, ResourceLocation> simple = new HashMap<>();
        byLocation.forEach((key, values) -> { if (values.size() == 1) simple.put(key, values.getFirst()); });
        return Collections.unmodifiableMap(simple);
    }
}

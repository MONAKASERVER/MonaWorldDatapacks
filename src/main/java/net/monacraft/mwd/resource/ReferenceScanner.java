package net.monacraft.mwd.resource;

import com.google.gson.*;
import java.util.*;

public final class ReferenceScanner {
    private static final Set<String> REFERENCE_FIELDS = Set.of(
            "type", "settings", "preset", "biome", "biomes", "feature", "features",
            "noise", "argument", "density", "final_density", "initial_density_without_jaggedness",
            "barrier", "fluid_level_floodedness", "fluid_level_spread", "lava",
            "temperature", "vegetation", "continents", "erosion", "depth", "ridges",
            "start_pool", "fallback", "processor_list", "processors", "template_pool",
            "structure", "structures", "preferred_biomes", "spawn_overrides",
            "carvers", "effects", "sound", "particle", "block", "fluid", "timeline", "timelines",
            "predicate", "conditions", "function", "functions", "loot_table"
    );

    public ScanResult scan(ResourceKey source, JsonElement root) {
        List<ResourceReference> known = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        visit(source, root, "$", null, false, known, unknown);
        return new ScanResult(List.copyOf(known), List.copyOf(unknown));
    }

    private void visit(ResourceKey source, JsonElement element, String path, String field, boolean tagValues,
                       List<ResourceReference> known, List<String> unknown) {
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                String childPath = path + '.' + entry.getKey();
                boolean values = source.type().tag() && "values".equals(entry.getKey());
                visit(source, entry.getValue(), childPath, entry.getKey(), values, known, unknown);
            }
        } else if (element.isJsonArray()) {
            int i = 0;
            for (JsonElement child : element.getAsJsonArray()) {
                visit(source, child, path + '[' + i++ + ']', field, tagValues, known, unknown);
            }
        } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String raw = element.getAsString();
            String candidate = raw.startsWith("#") ? raw.substring(1) : raw;
            Optional<ResourceLocation> location = ResourceLocation.tryParse(candidate);
            if (location.isEmpty() || (!candidate.contains(":") && !tagValues)) return;
            boolean schemaKnown = tagValues || REFERENCE_FIELDS.contains(field);
            if (schemaKnown) known.add(new ResourceReference(source, location.get(), path, true));
            else if (candidate.contains(":")) unknown.add(source + " " + path + " -> " + candidate);
        }
    }

    public record ScanResult(List<ResourceReference> references, List<String> unknownReferenceCandidates) {}
}

package net.monacraft.mwd.compiler;

import com.google.gson.*;
import net.monacraft.mwd.resource.ResourceLocation;
import net.monacraft.mwd.resource.ResourceType;
import java.util.*;

public final class JsonResourceRewriter {
    private static final Set<String> FIELDS = Set.of(
            "type", "settings", "preset", "biome", "biomes", "feature", "features", "noise", "argument",
            "density", "final_density", "initial_density_without_jaggedness", "barrier", "fluid_level_floodedness",
            "fluid_level_spread", "lava", "temperature", "vegetation", "continents", "erosion", "depth", "ridges",
            "start_pool", "fallback", "processor_list", "processors", "template_pool", "structure", "structures",
            "preferred_biomes", "carvers", "timeline", "timelines", "predicate", "conditions", "function", "functions", "loot_table", "name"
    );
    private static final Set<String> TEXT_FIELDS = Set.of(
            "description", "translation_key", "text", "message", "title", "subtitle"
    );

    public JsonElement rewrite(JsonElement input, ResourceType ownerType, NamespaceMapper mapper) {
        JsonElement copy = input.deepCopy();
        visit(copy, ownerType, null, ownerType == ResourceType.TAG, mapper);
        return copy;
    }

    private void visit(JsonElement node, ResourceType ownerType, String field, boolean tagContext, NamespaceMapper mapper) {
        if (node.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : node.getAsJsonObject().entrySet())
                visit(entry.getValue(), ownerType, entry.getKey(), tagContext && "values".equals(entry.getKey()), mapper);
        } else if (node.isJsonArray()) {
            for (int i = 0; i < node.getAsJsonArray().size(); i++) {
                JsonElement child = node.getAsJsonArray().get(i);
                JsonElement changed = rewritePrimitive(child, field, tagContext, mapper);
                if (changed != child) node.getAsJsonArray().set(i, changed); else visit(child, ownerType, field, tagContext, mapper);
            }
        } else if (node.isJsonPrimitive()) {
            // Parent object replacement is handled in object loop below through mutable primitive replacement.
        }
        if (node.isJsonObject()) {
            JsonObject object = node.getAsJsonObject();
            for (String key : new ArrayList<>(object.keySet())) {
                JsonElement old = object.get(key);
                JsonElement changed = rewritePrimitive(old, key, tagContext && "values".equals(key), mapper);
                if (changed != old) object.add(key, changed);
            }
        }
    }

    private JsonElement rewritePrimitive(JsonElement element, String field, boolean tagValues, NamespaceMapper mapper) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) return element;
        if (!tagValues && TEXT_FIELDS.contains(field)) return element;
        String raw = element.getAsString();
        boolean tag = raw.startsWith("#");
        String value = tag ? raw.substring(1) : raw;
        Optional<ResourceLocation> parsed = ResourceLocation.tryParse(value);
        if (parsed.isEmpty()) return element;
        ResourceLocation mapped = mapper.mapReference(parsed.get());
        // Known schema fields are rewritten as before. For newer worldgen
        // schema fields, rewrite only when the value exactly resolves to a
        // resource that this compilation is actually cloning. Unrelated
        // block/sound/etc. identifiers remain unchanged because the mapper
        // has no entry for them.
        if (!tagValues && !FIELDS.contains(field) && mapped.equals(parsed.get())) return element;
        return mapped.equals(parsed.get()) ? element : new JsonPrimitive((tag ? "#" : "") + mapped);
    }
}

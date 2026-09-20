package net.monacraft.mwd.compiler;

import com.google.gson.*;
import net.monacraft.mwd.resource.ResourceLocation;
import net.monacraft.mwd.resource.ResourceType;
import java.util.*;

public final class JsonResourceRewriter {
    private static final Set<String> NON_WORLDGEN_REFERENCE_FIELDS = Set.of(
            "description", "translation_key", "text", "message", "title", "subtitle",
            "Name", "block", "blocks", "fluid", "item", "sound", "particle", "effects"
    );

    public JsonElement rewrite(JsonElement input, ResourceType ownerType, NamespaceMapper mapper) {
        JsonElement copy = input.deepCopy();
        if (ownerType == ResourceType.DIMENSION && copy.isJsonObject()) {
            JsonObject root = copy.getAsJsonObject();
            JsonElement type = root.get("type");
            if (type != null) root.add("type", rewriteLocation(type, ResourceType.DIMENSION_TYPE, false, mapper));
        }
        visit(copy, ownerType, null, ownerType.tag(), 0, mapper);
        return copy;
    }

    private void visit(JsonElement node, ResourceType ownerType, String field, boolean tagContext, int depth, NamespaceMapper mapper) {
        if (node.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : node.getAsJsonObject().entrySet())
                visit(entry.getValue(), ownerType, entry.getKey(), tagContext && "values".equals(entry.getKey()), depth + 1, mapper);
        } else if (node.isJsonArray()) {
            for (int i = 0; i < node.getAsJsonArray().size(); i++) {
                JsonElement child = node.getAsJsonArray().get(i);
                JsonElement changed = rewritePrimitive(child, ownerType, field, tagContext, depth, null, mapper);
                if (changed != child) node.getAsJsonArray().set(i, changed); else visit(child, ownerType, field, tagContext, depth + 1, mapper);
            }
        } else if (node.isJsonPrimitive()) {
            // Parent object replacement is handled in object loop below through mutable primitive replacement.
        }
        if (node.isJsonObject()) {
            JsonObject object = node.getAsJsonObject();
            for (String key : new ArrayList<>(object.keySet())) {
                JsonElement old = object.get(key);
                JsonElement changed = rewritePrimitive(old, ownerType, key, tagContext && "values".equals(key), depth, object, mapper);
                if (changed != old) object.add(key, changed);
            }
        }
    }

    private JsonElement rewritePrimitive(JsonElement element, ResourceType ownerType, String field, boolean tagValues,
                                         int depth, JsonObject container, NamespaceMapper mapper) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) return element;
        String raw = element.getAsString();
        boolean tag = raw.startsWith("#");
        if (tagValues) {
            if (tag) return rewriteLocation(element, true, mapper);
            if (ownerType == ResourceType.BIOME_TAG) return rewriteLocation(element, false, mapper);
            return element;
        }
        if ("type".equals(field) || NON_WORLDGEN_REFERENCE_FIELDS.contains(field)) return element;
        // Do not reinterpret enum-like strings such as cardinal_light=nether.
        if (!tag && !raw.contains(":")) return element;
        ResourceType expected = expectedType(ownerType, field, tag, depth, container);
        return rewriteLocation(element, expected, tag, mapper);
    }

    private JsonElement rewriteLocation(JsonElement element, boolean tag, NamespaceMapper mapper) {
        return rewriteLocation(element, null, tag, mapper);
    }

    private JsonElement rewriteLocation(JsonElement element, ResourceType expectedType, boolean tag, NamespaceMapper mapper) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) return element;
        String raw = element.getAsString();
        String value = tag ? raw.substring(1) : raw;
        Optional<ResourceLocation> parsed = ResourceLocation.tryParse(value);
        if (parsed.isEmpty()) return element;
        ResourceLocation mapped = expectedType == null
                ? mapper.mapReference(parsed.get(), tag)
                : mapper.mapReference(expectedType, parsed.get());
        return mapped.equals(parsed.get()) ? element : new JsonPrimitive((tag ? "#" : "") + mapped);
    }

    private static ResourceType expectedType(ResourceType ownerType, String field, boolean tag, int depth, JsonObject container) {
        if (tag) {
            if (ownerType == ResourceType.STRUCTURE && "biomes".equals(field)) return ResourceType.BIOME_TAG;
            if (Set.of("replaceable", "infiniburn", "cannot_replace", "can_replace").contains(field)) return ResourceType.BLOCK_TAG;
            return null;
        }
        if (ownerType == ResourceType.DIMENSION && "settings".equals(field)) return ResourceType.NOISE_SETTINGS;
        if (ownerType == ResourceType.DIMENSION && "preset".equals(field)) return ResourceType.MULTI_NOISE_PARAMETER_LIST;
        if (ownerType == ResourceType.BIOME && "features".equals(field)) return ResourceType.PLACED_FEATURE;
        if (ownerType == ResourceType.BIOME && "carvers".equals(field)) return ResourceType.CONFIGURED_CARVER;
        if (ownerType == ResourceType.PLACED_FEATURE && "feature".equals(field))
            return depth == 0 || (container != null && container.has("placement"))
                    ? ResourceType.CONFIGURED_FEATURE : ResourceType.PLACED_FEATURE;
        if ((ownerType == ResourceType.PLACED_FEATURE || ownerType == ResourceType.CONFIGURED_FEATURE)
                && Set.of("features", "vegetation_feature", "default").contains(field)) return ResourceType.PLACED_FEATURE;
        if (ownerType == ResourceType.CONFIGURED_FEATURE && "feature".equals(field)) return ResourceType.PLACED_FEATURE;
        if (ownerType == ResourceType.DENSITY_FUNCTION && "noise".equals(field)) return ResourceType.NOISE;
        if (ownerType == ResourceType.DENSITY_FUNCTION && Set.of("coordinate", "argument", "input").contains(field)) return ResourceType.DENSITY_FUNCTION;
        if (ownerType == ResourceType.NOISE_SETTINGS && Set.of(
                "barrier", "fluid_level_floodedness", "fluid_level_spread", "lava", "temperature",
                "vegetation", "continents", "erosion", "depth", "ridges", "final_density",
                "initial_density_without_jaggedness").contains(field)) return ResourceType.DENSITY_FUNCTION;
        if (ownerType == ResourceType.STRUCTURE && "start_pool".equals(field)) return ResourceType.TEMPLATE_POOL;
        if (ownerType == ResourceType.STRUCTURE_SET && "structures".equals(field)) return ResourceType.STRUCTURE;
        if (ownerType == ResourceType.TEMPLATE_POOL && Set.of("fallback", "template_pool").contains(field)) return ResourceType.TEMPLATE_POOL;
        if (ownerType == ResourceType.TEMPLATE_POOL && "processors".equals(field)) return ResourceType.PROCESSOR_LIST;
        if (ownerType == ResourceType.TEMPLATE_POOL && "location".equals(field)) return ResourceType.STRUCTURE_TEMPLATE;
        if (ownerType == ResourceType.TEMPLATE_POOL && "feature".equals(field)) return ResourceType.PLACED_FEATURE;
        if (ownerType == ResourceType.PROCESSOR_LIST && "predicate_type".equals(field)) return ResourceType.PREDICATE;
        return null;
    }
}

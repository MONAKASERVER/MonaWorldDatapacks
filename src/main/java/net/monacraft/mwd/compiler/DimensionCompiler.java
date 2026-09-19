package net.monacraft.mwd.compiler;

import com.google.gson.*;
import net.monacraft.mwd.config.WorldAssignment;
import net.monacraft.mwd.resource.*;
import java.util.*;

public final class DimensionCompiler {
    public JsonElement create(WorldAssignment assignment, Map<ResourceKey, ResourceNode> resources,
                              NamespaceMapper mapper, JsonResourceRewriter rewriter) {
        Optional<ResourceNode> source = resources.values().stream()
                .filter(n -> n.key().type() == ResourceType.DIMENSION && n.key().location().equals(assignment.sourceDimension()))
                .findFirst();
        if (source.isPresent()) return rewriter.rewrite(source.get().json(), ResourceType.DIMENSION, mapper);

        JsonObject dimension = new JsonObject();
        String env = assignment.environment().toUpperCase(Locale.ROOT);
        if (env.equals("NETHER")) {
            dimension.addProperty("type", "minecraft:the_nether");
            dimension.add("generator", netherGenerator(mapper));
        } else if (env.equals("THE_END")) {
            dimension.addProperty("type", "minecraft:the_end");
            dimension.add("generator", endGenerator(mapper));
        } else {
            dimension.addProperty("type", "minecraft:overworld");
            dimension.add("generator", overworldGenerator(mapper));
        }
        return dimension;
    }
    private JsonObject netherGenerator(NamespaceMapper mapper) {
        JsonObject gen = new JsonObject(); gen.addProperty("type", "minecraft:noise");
        gen.addProperty("settings", mapper.mapReference(new ResourceLocation("minecraft", "nether")).toString());
        JsonObject source = new JsonObject(); source.addProperty("type", "minecraft:multi_noise");
        source.addProperty("preset", mapper.mapReference(new ResourceLocation("minecraft", "nether")).toString());
        gen.add("biome_source", source); return gen;
    }
    private JsonObject endGenerator(NamespaceMapper mapper) {
        JsonObject gen = new JsonObject(); gen.addProperty("type", "minecraft:noise");
        gen.addProperty("settings", mapper.mapReference(new ResourceLocation("minecraft", "end")).toString());
        JsonObject source = new JsonObject(); source.addProperty("type", "minecraft:the_end");
        gen.add("biome_source", source); return gen;
    }
    private JsonObject overworldGenerator(NamespaceMapper mapper) {
        JsonObject gen = new JsonObject(); gen.addProperty("type", "minecraft:noise");
        gen.addProperty("settings", mapper.mapReference(new ResourceLocation("minecraft", "overworld")).toString());
        JsonObject source = new JsonObject(); source.addProperty("type", "minecraft:multi_noise");
        source.addProperty("preset", mapper.mapReference(new ResourceLocation("minecraft", "overworld")).toString());
        gen.add("biome_source", source); return gen;
    }
}


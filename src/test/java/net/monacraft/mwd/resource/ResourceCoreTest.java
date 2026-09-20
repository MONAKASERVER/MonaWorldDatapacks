package net.monacraft.mwd.resource;

import com.google.gson.JsonParser;
import net.monacraft.mwd.compiler.*;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ResourceCoreTest {
    @Test void parsesAndRejectsResourceLocations() {
        assertThat(ResourceLocation.parse("incendium:terrain/lower")).isEqualTo(new ResourceLocation("incendium", "terrain/lower"));
        assertThat(ResourceLocation.parse("stone").toString()).isEqualTo("minecraft:stone");
        assertThatThrownBy(() -> ResourceLocation.parse("Bad Namespace:path")).isInstanceOf(IllegalArgumentException.class);
        assertThat(ResourceType.match("worldgen/world_preset/normal").type()).isEqualTo(ResourceType.WORLD_PRESET);
        assertThat(ResourceType.match("worldgen/flat_level_generator_preset/classic_flat").type()).isEqualTo(ResourceType.FLAT_LEVEL_GENERATOR_PRESET);
        assertThat(ResourceType.match("tags/worldgen/biome/structure/has_castle").type()).isEqualTo(ResourceType.BIOME_TAG);
        assertThat(ResourceType.match("tags/block/dunes").type()).isEqualTo(ResourceType.BLOCK_TAG);
    }

    @Test void mapsOnlyOwnedResourcesAndRewritesKnownJsonFields() {
        ResourceKey key = new ResourceKey(ResourceType.NOISE_SETTINGS, new ResourceLocation("minecraft", "nether"));
        NamespaceMapper mapper = new NamespaceMapper("mwd_test", List.of(key));
        var input = JsonParser.parseString("{\"settings\":\"minecraft:nether\",\"description\":\"minecraft:nether is cool\"}");
        var output = new JsonResourceRewriter().rewrite(input, ResourceType.DIMENSION, mapper).getAsJsonObject();
        assertThat(output.get("settings").getAsString()).isEqualTo("mwd_test:nether");
        assertThat(output.get("description").getAsString()).isEqualTo("minecraft:nether is cool");
    }

    @Test void rewritesExistingWorldgenTargetsInNewSchemaFieldsButNotText() {
        ResourceKey key = new ResourceKey(ResourceType.DENSITY_FUNCTION, new ResourceLocation("fixture", "terrain/new_field"));
        ResourceKey collision = new ResourceKey(ResourceType.CONFIGURED_FEATURE, new ResourceLocation("minecraft", "glowstone"));
        NamespaceMapper mapper = new NamespaceMapper("mwd_test", List.of(key, collision));
        var input = JsonParser.parseString("{\"future_density_field\":\"fixture:terrain/new_field\",\"description\":\"fixture:terrain/new_field\",\"Name\":\"minecraft:glowstone\",\"block\":\"minecraft:glowstone\",\"type\":\"minecraft:glowstone\"}");

        var output = new JsonResourceRewriter().rewrite(input, ResourceType.NOISE_SETTINGS, mapper).getAsJsonObject();

        assertThat(output.get("future_density_field").getAsString()).isEqualTo("mwd_test:terrain/new_field");
        assertThat(output.get("description").getAsString()).isEqualTo("fixture:terrain/new_field");
        assertThat(output.get("Name").getAsString()).isEqualTo("minecraft:glowstone");
        assertThat(output.get("block").getAsString()).isEqualTo("minecraft:glowstone");
        assertThat(output.get("type").getAsString()).isEqualTo("minecraft:glowstone");
    }

    @Test void treatsConventionPreviewMetadataAsOptionalButKeepsUnknownWorldgenStrict() {
        ResourceKey colors = new ResourceKey(ResourceType.UNKNOWN, new ResourceLocation("c", "worldgen/biome_colors"));
        ResourceKey icons = new ResourceKey(ResourceType.UNKNOWN, new ResourceLocation("c", "worldgen/structure_icons"));
        ResourceKey unsafe = new ResourceKey(ResourceType.UNKNOWN, new ResourceLocation("incendium", "worldgen/new_registry/value"));

        assertThat(net.monacraft.mwd.compatibility.CompatibilityReport.isUnsafeUnknownResource(colors)).isFalse();
        assertThat(net.monacraft.mwd.compatibility.CompatibilityReport.isUnsafeUnknownResource(icons)).isFalse();
        assertThat(net.monacraft.mwd.compatibility.CompatibilityReport.isUnsafeUnknownResource(unsafe)).isTrue();
    }

    @Test void rewritesNestedBlockTagsWithoutTurningBlocksIntoTagIds() {
        ResourceKey tag = new ResourceKey(ResourceType.BLOCK_TAG, new ResourceLocation("minecraft", "glowstone"));
        NamespaceMapper mapper = new NamespaceMapper("mwd_test", List.of(tag));
        var input = JsonParser.parseString("{\"values\":[\"minecraft:glowstone\",\"#minecraft:glowstone\"]}");

        var output = new JsonResourceRewriter().rewrite(input, ResourceType.BLOCK_TAG, mapper).getAsJsonObject();

        assertThat(output.getAsJsonArray("values").get(0).getAsString()).isEqualTo("minecraft:glowstone");
        assertThat(output.getAsJsonArray("values").get(1).getAsString()).isEqualTo("#mwd_test:glowstone");
    }

    @Test void usesExpectedRegistryInsteadOfSameNamedDifferentRegistry() {
        ResourceKey placedOnly = new ResourceKey(ResourceType.PLACED_FEATURE, new ResourceLocation("minecraft", "basalt_pillar"));
        ResourceKey configured = new ResourceKey(ResourceType.CONFIGURED_FEATURE, new ResourceLocation("fixture", "custom"));
        NamespaceMapper mapper = new NamespaceMapper("mwd_test", List.of(placedOnly, configured));

        var input = JsonParser.parseString("{\"feature\":\"minecraft:basalt_pillar\",\"other_feature\":\"fixture:custom\"}");
        var output = new JsonResourceRewriter().rewrite(input, ResourceType.PLACED_FEATURE, mapper).getAsJsonObject();

        assertThat(output.get("feature").getAsString()).isEqualTo("minecraft:basalt_pillar");
        assertThat(output.get("other_feature").getAsString()).isEqualTo("mwd_test:custom");
    }

    @Test void detectsCyclesAndMissingPackReferences() {
        ResourceKey a = new ResourceKey(ResourceType.DENSITY_FUNCTION, new ResourceLocation("test", "a"));
        ResourceKey b = new ResourceKey(ResourceType.DENSITY_FUNCTION, new ResourceLocation("test", "b"));
        ResourceNode na = node(a, new ResourceReference(a, b.location(), "$.argument", true));
        ResourceNode nb = node(b, new ResourceReference(b, a.location(), "$.argument", true));
        ResourceGraph graph = new ResourceGraph(List.of(na, nb));
        assertThat(graph.cycles()).isNotEmpty();
        ResourceReference missing = new ResourceReference(a, new ResourceLocation("test", "missing"), "$.noise", true);
        ResourceGraph broken = new ResourceGraph(List.of(node(a, missing)));
        assertThat(broken.missingPackReferences(Set.of("test"))).containsExactly(missing);
    }
    private static ResourceNode node(ResourceKey key, ResourceReference... refs) {
        return new ResourceNode(key, JsonParser.parseString("{}"), Path.of(key.location().path()), List.of(refs), "fixture");
    }
}

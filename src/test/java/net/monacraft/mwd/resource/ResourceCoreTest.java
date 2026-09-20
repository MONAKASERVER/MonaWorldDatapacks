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
        NamespaceMapper mapper = new NamespaceMapper("mwd_test", List.of(key));
        var input = JsonParser.parseString("{\"future_density_field\":\"fixture:terrain/new_field\",\"description\":\"fixture:terrain/new_field\",\"block\":\"minecraft:stone\"}");

        var output = new JsonResourceRewriter().rewrite(input, ResourceType.NOISE_SETTINGS, mapper).getAsJsonObject();

        assertThat(output.get("future_density_field").getAsString()).isEqualTo("mwd_test:terrain/new_field");
        assertThat(output.get("description").getAsString()).isEqualTo("fixture:terrain/new_field");
        assertThat(output.get("block").getAsString()).isEqualTo("minecraft:stone");
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

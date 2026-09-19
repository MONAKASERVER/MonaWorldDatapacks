package net.monacraft.mwd.compiler;

import com.google.gson.JsonParser;
import net.monacraft.mwd.compatibility.*;
import net.monacraft.mwd.config.PackAssignment;
import net.monacraft.mwd.pack.*;
import net.monacraft.mwd.resource.*;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ConflictResolverTest {
    @Test void higherPriorityPackWinsAndConflictIsReported() {
        ResourceKey key = new ResourceKey(ResourceType.NOISE_SETTINGS, new ResourceLocation("minecraft", "nether"));
        DatapackAnalysis low = analysis("low", node(key, "low")); DatapackAnalysis high = analysis("high", node(key, "high"));
        var result = new ConflictResolver().resolve(List.of(new PackAssignment("low", 10), new PackAssignment("high", 20)), Map.of("low", low, "high", high));
        assertThat(result.resources().get(key).provider()).isEqualTo("high");
        assertThat(result.conflicts()).singleElement().satisfies(c -> assertThat(c.winner()).isEqualTo("high"));
    }
    private static ResourceNode node(ResourceKey key, String provider) { return new ResourceNode(key, JsonParser.parseString("{}"), Path.of("x"), List.of(), provider); }
    private static DatapackAnalysis analysis(String id, ResourceNode node) {
        ResourceGraph graph = new ResourceGraph(List.of(node)); CompatibilityReport report = new CompatibilityReport(id, CompatibilityLevel.SUPPORTED, Map.of(ScopeClass.WORLDGEN_SCOPABLE, 1), List.of(), List.of(), List.of());
        return new DatapackAnalysis(id, Path.of(id + ".zip"), "hash", new DatapackMetadata(PackVersion.MINECRAFT_1_21_11, PackVersion.MINECRAFT_1_21_11, ""), Set.of("minecraft"), List.of(node), graph, List.of(), List.of(), report);
    }
}

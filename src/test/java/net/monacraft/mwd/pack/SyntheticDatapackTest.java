package net.monacraft.mwd.pack;

import net.monacraft.mwd.compatibility.CompatibilityLevel;
import net.monacraft.mwd.profile.*;
import net.monacraft.mwd.resource.ResourceType;
import net.monacraft.mwd.security.ZipLimits;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.assertj.core.api.Assertions.*;

class SyntheticDatapackTest {
    @TempDir Path temp;

    @Test void scansIncendiumLikePackWithoutEnablingOriginal() throws IOException {
        Path pack = fixture("incendium.zip", Map.of(
                "data/minecraft/worldgen/noise_settings/nether.json", "{\"final_density\":\"incendium:terrain/lower\"}",
                "data/incendium/worldgen/density_function/terrain/lower.json", "{\"type\":\"minecraft:constant\",\"argument\":0.0}"
        ));
        DatapackAnalysis analysis = new DatapackScanner(ZipLimits.defaults()).scan("incendium", pack);
        assertThat(analysis.compatibility().result()).isEqualTo(CompatibilityLevel.SUPPORTED);
        assertThat(analysis.worldgenCount()).isEqualTo(2);
        assertThat(new IncendiumProfile().matches(analysis)).isTrue();
    }

    @Test void scansStellarityLikePackAndSeparatesRuntimeClassification() throws IOException {
        Path pack = fixture("stellarity.zip", Map.of(
                "data/minecraft/worldgen/noise_settings/end.json", "{\"final_density\":\"stellarity:terrain/end\"}",
                "data/stellarity/worldgen/density_function/terrain/end.json", "{\"type\":\"minecraft:constant\",\"argument\":0.0}",
                "data/stellarity/function/load.mcfunction", "say ignored by JSON scanner"
        ));
        DatapackAnalysis analysis = new DatapackScanner(ZipLimits.defaults()).scan("stellarity", pack);
        assertThat(analysis.count(ResourceType.FUNCTION)).isEqualTo(1);
        assertThat(analysis.compatibility().result()).isEqualTo(CompatibilityLevel.REQUIRES_GLOBAL_RESOURCES);
        assertThat(new StellarityProfile().matches(analysis)).isTrue();
    }

    private Path fixture(String name, Map<String, String> files) throws IOException {
        Path target = temp.resolve(name);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(target))) {
            write(out, "pack.mcmeta", "{\"pack\":{\"min_format\":[94,1],\"max_format\":[94,1],\"description\":\"synthetic\"}}");
            for (Map.Entry<String, String> entry : files.entrySet()) write(out, entry.getKey(), entry.getValue());
        }
        return target;
    }
    private static void write(ZipOutputStream out, String name, String value) throws IOException {
        out.putNextEntry(new ZipEntry(name)); out.write(value.getBytes()); out.closeEntry();
    }
}

package net.monacraft.mwd.compiler;

import net.monacraft.mwd.config.*;
import net.monacraft.mwd.pack.*;
import net.monacraft.mwd.resource.ResourceLocation;
import net.monacraft.mwd.security.ZipLimits;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.assertj.core.api.Assertions.*;

class DatapackCompilerIntegrationTest {
    @TempDir Path temp;

    @Test void producesIsolatedDimensionWithoutGlobalMinecraftOverride() throws IOException {
        Path data = temp.resolve("plugins/MonaWorldDatapacks"); Files.createDirectories(data.resolve("packs"));
        Path source = data.resolve("packs/incendium.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(source))) {
            write(out, "pack.mcmeta", "{\"pack\":{\"min_format\":[94,1],\"max_format\":[94,1],\"description\":\"fixture\"}}");
            write(out, "data/minecraft/worldgen/noise_settings/nether.json", "{\"final_density\":\"incendium:terrain/lower\"}");
            write(out, "data/incendium/worldgen/density_function/terrain/lower.json", "{\"type\":\"minecraft:constant\",\"argument\":0.0}");
        }
        PluginConfiguration config = new PluginConfiguration(false, true, "ZIP", "mwd", ZipLimits.defaults(), true, true, true, true, false, true, true);
        DatapackManager manager = new DatapackManager(data, config.zipLimits());
        WorldAssignment assignment = new WorldAssignment("test_nether", true, "NETHER", List.of(new PackAssignment("incendium", 100)),
                "INCENDIUM", new ResourceLocation("minecraft", "the_nether"), "SCOPED_WORLDGEN", ExistingWorldPolicy.REFUSE);
        CompilationResult result = new DatapackCompiler(data, config, manager).compile(assignment);
        assertThat(result.success()).as(String.join("; ", result.messages())).isTrue();
        try (ZipFile zip = new ZipFile(result.output().toFile())) {
            assertThat(zip.getEntry("data/mwd_test_nether/worldgen/noise_settings/nether.json")).isNotNull();
            assertThat(zip.getEntry("data/mwd_test_nether/dimension/test_nether.json")).isNotNull();
            assertThat(zip.getEntry("data/minecraft/worldgen/noise_settings/nether.json")).isNull();
            String noise = new String(zip.getInputStream(zip.getEntry("data/mwd_test_nether/worldgen/noise_settings/nether.json")).readAllBytes());
            assertThat(noise).contains("mwd_test_nether:terrain/lower").doesNotContain("incendium:terrain/lower");
            String meta = new String(zip.getInputStream(zip.getEntry("pack.mcmeta")).readAllBytes());
            assertThat(meta).contains("min_format", "94", "1");
        }
    }
    private static void write(ZipOutputStream out, String name, String value) throws IOException {
        out.putNextEntry(new ZipEntry(name)); out.write(value.getBytes()); out.closeEntry();
    }
}

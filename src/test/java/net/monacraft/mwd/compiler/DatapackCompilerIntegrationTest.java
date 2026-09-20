package net.monacraft.mwd.compiler;

import net.monacraft.mwd.config.*;
import net.monacraft.mwd.pack.*;
import net.monacraft.mwd.resource.ResourceLocation;
import net.monacraft.mwd.security.ZipLimits;
import net.kyori.adventure.nbt.*;
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
            write(out, "data/incendium/worldgen/structure/castle.json", "{\"type\":\"minecraft:jigsaw\",\"biomes\":\"#incendium:structure/has_castle\"}");
            write(out, "data/incendium/tags/worldgen/biome/structure/has_castle.json", "{\"values\":[\"minecraft:nether_wastes\"]}");
            write(out, "data/incendium/worldgen/template_pool/castle/start.json", "{\"fallback\":\"minecraft:empty\",\"elements\":[{\"weight\":1,\"element\":{\"element_type\":\"minecraft:single_pool_element\",\"location\":\"incendium:castle/start\",\"processors\":\"minecraft:empty\",\"projection\":\"rigid\"}}]}");
            write(out, "data/incendium/structure/castle/start.nbt", structureNbt("incendium:castle/start"));
        }
        PluginConfiguration config = new PluginConfiguration(false, true, "ZIP", "mwd", ZipLimits.defaults(), true, true, true, true, false, true, true);
        DatapackManager manager = new DatapackManager(data, config.zipLimits());
        WorldAssignment assignment = new WorldAssignment("test_nether", true, "NETHER", List.of(new PackAssignment("incendium", 100)),
                "INCENDIUM", new ResourceLocation("minecraft", "the_nether"), "SCOPED_WORLDGEN", ExistingWorldPolicy.REFUSE);
        CompilationResult result = new DatapackCompiler(data, config, manager).compile(assignment);
        assertThat(result.success()).as(String.join("; ", result.messages())).isTrue();
        try (ZipFile zip = new ZipFile(result.output().toFile())) {
            assertThat(zip.getEntry("data/mwd_test_nether/worldgen/noise_settings/nether.json")).isNotNull();
            assertThat(zip.getEntry("data/mwd_test_nether/tags/worldgen/biome/structure/has_castle.json")).isNotNull();
            assertThat(zip.getEntry("data/mwd_test_nether/structure/castle/start.nbt")).isNotNull();
            assertThat(zip.getEntry("data/mwd_test_nether/dimension/test_nether.json")).isNotNull();
            assertThat(zip.getEntry("data/mwd_test_nether/dimension/the_nether.json")).isNull();
            assertThat(zip.getEntry("data/minecraft/worldgen/noise_settings/nether.json")).isNull();
            String noise = new String(zip.getInputStream(zip.getEntry("data/mwd_test_nether/worldgen/noise_settings/nether.json")).readAllBytes());
            assertThat(noise).contains("mwd_test_nether:terrain/lower").doesNotContain("incendium:terrain/lower");
            String structure = new String(zip.getInputStream(zip.getEntry("data/mwd_test_nether/worldgen/structure/castle.json")).readAllBytes());
            assertThat(structure).contains("#mwd_test_nether:structure/has_castle");
            String pool = new String(zip.getInputStream(zip.getEntry("data/mwd_test_nether/worldgen/template_pool/castle/start.json")).readAllBytes());
            assertThat(pool).contains("mwd_test_nether:castle/start").doesNotContain("incendium:castle/start");
            byte[] structureBytes = zip.getInputStream(zip.getEntry("data/mwd_test_nether/structure/castle/start.nbt")).readAllBytes();
            try (ByteArrayInputStream input = new ByteArrayInputStream(structureBytes)) {
                CompoundBinaryTag structureNbt = BinaryTagIO.readCompressedInputStream(input);
                assertThat(structureNbt.getCompound("block").getString("pool"))
                        .isEqualTo("mwd_test_nether:castle/start");
            }
            String meta = new String(zip.getInputStream(zip.getEntry("pack.mcmeta")).readAllBytes());
            assertThat(meta).contains("min_format", "94", "1");
        }
    }

    @Test void omitsServerGlobalAndUnrelatedUnknownResourcesWithoutRejectingWorldgen() throws IOException {
        Path data = temp.resolve("plugins/MonaWorldDatapacks"); Files.createDirectories(data.resolve("packs"));
        Path source = data.resolve("packs/full-pack.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(source))) {
            write(out, "pack.mcmeta", "{\"pack\":{\"min_format\":[94,1],\"max_format\":[94,1],\"description\":\"fixture\"}}");
            write(out, "data/minecraft/worldgen/noise_settings/nether.json", "{\"final_density\":\"fixture:terrain/lower\",\"future_density_field\":\"fixture:terrain/lower\"}");
            write(out, "data/fixture/worldgen/density_function/terrain/lower.json", "{\"type\":\"minecraft:constant\",\"argument\":0.0}");
            write(out, "data/fixture/recipe/example.json", "{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[\"minecraft:stone\"],\"result\":{\"id\":\"minecraft:diamond\"}}");
            write(out, "data/fixture/function/load.mcfunction", "say this must not become global");
            write(out, "data/fixture/future_global_registry/example.json", "{\"value\":\"fixture:anything\"}");
            write(out, "data/c/worldgen/biome_colors.json", "{\"fixture:biome\":{\"r\":1,\"g\":2,\"b\":3,\"name\":\"Fixture\"}}");
            write(out, "data/c/worldgen/structure_icons.json", "{\"fixture:structure\":{\"item\":\"minecraft:stone\"}}");
        }
        PluginConfiguration config = new PluginConfiguration(false, true, "ZIP", "mwd", ZipLimits.defaults(), true, true, true, true, false, true, true);
        DatapackManager manager = new DatapackManager(data, config.zipLimits());
        WorldAssignment assignment = new WorldAssignment("test_nether", true, "NETHER", List.of(new PackAssignment("full-pack", 100)),
                "GENERIC", new ResourceLocation("minecraft", "the_nether"), "SCOPED_WORLDGEN", ExistingWorldPolicy.REFUSE);

        CompilationResult result = new DatapackCompiler(data, config, manager).compile(assignment);

        assertThat(result.success()).as(String.join("; ", result.messages())).isTrue();
        assertThat(result.messages()).anyMatch(message -> message.contains("Omitted 2 server-global resource(s)"));
        assertThat(result.messages()).anyMatch(message -> message.contains("Omitted 3 non-worldgen unknown resource(s)"));
        try (ZipFile zip = new ZipFile(result.output().toFile())) {
            assertThat(zip.getEntry("data/fixture/recipe/example.json")).isNull();
            assertThat(zip.getEntry("data/fixture/function/load.mcfunction")).isNull();
            assertThat(zip.getEntry("data/fixture/future_global_registry/example.json")).isNull();
            assertThat(zip.getEntry("data/c/worldgen/biome_colors.json")).isNull();
            assertThat(zip.getEntry("data/c/worldgen/structure_icons.json")).isNull();
            String noise = new String(zip.getInputStream(zip.getEntry("data/mwd_test_nether/worldgen/noise_settings/nether.json")).readAllBytes());
            assertThat(noise).contains("\"future_density_field\": \"mwd_test_nether:terrain/lower\"");
            String manifest = new String(zip.getInputStream(zip.getEntry("mwd-manifest.json")).readAllBytes());
            assertThat(manifest).contains("\"omitted_server_global_resources\": 2", "\"omitted_unknown_resources\": 3");
        }
    }

    @Test void stillRejectsUnknownWorldgenRegistryInStrictMode() throws IOException {
        Path data = temp.resolve("plugins/MonaWorldDatapacks"); Files.createDirectories(data.resolve("packs"));
        Path source = data.resolve("packs/unsafe.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(source))) {
            write(out, "pack.mcmeta", "{\"pack\":{\"min_format\":[94,1],\"max_format\":[94,1],\"description\":\"fixture\"}}");
            write(out, "data/minecraft/worldgen/noise_settings/nether.json", "{\"final_density\":0.0}");
            write(out, "data/fixture/worldgen/future_registry/example.json", "{\"value\":1}");
        }
        PluginConfiguration config = new PluginConfiguration(false, true, "ZIP", "mwd", ZipLimits.defaults(), true, true, true, true, false, true, true);
        DatapackManager manager = new DatapackManager(data, config.zipLimits());
        WorldAssignment assignment = new WorldAssignment("test_nether", true, "NETHER", List.of(new PackAssignment("unsafe", 100)),
                "GENERIC", new ResourceLocation("minecraft", "the_nether"), "SCOPED_WORLDGEN", ExistingWorldPolicy.REFUSE);

        CompilationResult result = new DatapackCompiler(data, config, manager).compile(assignment);

        assertThat(result.success()).isFalse();
        assertThat(result.messages()).anyMatch(message -> message.contains("unknown worldgen resource(s)")
                && message.contains("worldgen/future_registry/example"));
    }
    private static void write(ZipOutputStream out, String name, String value) throws IOException {
        write(out, name, value.getBytes());
    }
    private static void write(ZipOutputStream out, String name, byte[] value) throws IOException {
        out.putNextEntry(new ZipEntry(name)); out.write(value); out.closeEntry();
    }
    private static byte[] structureNbt(String pool) throws IOException {
        CompoundBinaryTag block = CompoundBinaryTag.builder().putString("pool", pool).build();
        CompoundBinaryTag root = CompoundBinaryTag.builder().put("block", block).build();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BinaryTagIO.writeCompressedOutputStream(root, output);
        return output.toByteArray();
    }
}

package net.monacraft.mwd.config;

import net.monacraft.mwd.resource.ResourceLocation;
import net.monacraft.mwd.security.ZipLimits;
import org.yaml.snakeyaml.Yaml;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class ConfigManager {
    private final Path dataDirectory;
    public ConfigManager(Path dataDirectory) { this.dataDirectory = dataDirectory; }

    public ConfigurationBundle load() throws IOException {
        createLayout();
        copyDefault("config.yml");
        copyDefault("worlds.yml");
        Map<String, Object> config = yaml(dataDirectory.resolve("config.yml"));
        Map<String, Object> compiler = map(config, "compiler");
        Map<String, Object> safety = map(config, "safety");
        Map<String, Object> plugin = map(config, "plugin");
        Map<String, Object> mv = map(config, "multiverse");
        PluginConfiguration parsed = new PluginConfiguration(
                bool(plugin, "debug", false), bool(compiler, "cache", true), string(compiler, "output-format", "ZIP"),
                string(compiler, "namespace-prefix", "mwd"),
                new ZipLimits(mb(compiler, "max-pack-size-mb", 512), mb(compiler, "max-uncompressed-size-mb", 2048),
                        mb(compiler, "max-single-file-size-mb", 64), integer(compiler, "max-files", 100_000)),
                bool(safety, "strict-mode", true), bool(safety, "reject-global-registry-overrides", true),
                bool(safety, "reject-unknown-resources", true), bool(safety, "reject-existing-generated-world", true),
                bool(safety, "allow-experimental-runtime-scope", false), bool(mv, "auto-import", true),
                bool(mv, "validate-on-startup", true));
        return new ConfigurationBundle(parsed, parseWorlds(yaml(dataDirectory.resolve("worlds.yml"))));
    }

    private Map<String, WorldAssignment> parseWorlds(Map<String, Object> root) {
        Map<String, WorldAssignment> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map(root, "worlds").entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> raw)) continue;
            Map<String, Object> value = cast(raw);
            List<PackAssignment> packs = new ArrayList<>();
            Object list = value.get("datapacks");
            if (list instanceof List<?> items) for (Object item : items) {
                if (item instanceof String id) packs.add(new PackAssignment(id, 100));
                else if (item instanceof Map<?, ?> itemMap) {
                    Map<String, Object> p = cast(itemMap);
                    packs.add(new PackAssignment(string(p, "id", ""), integer(p, "priority", 100)));
                }
            }
            String source = string(value, "source-dimension", "minecraft:overworld");
            result.put(entry.getKey(), new WorldAssignment(entry.getKey(), bool(value, "enabled", false),
                    string(value, "environment", "NORMAL"), List.copyOf(packs), string(value, "profile", "GENERIC"),
                    ResourceLocation.parse(source), string(value, "strategy", "SCOPED_WORLDGEN"),
                    ExistingWorldPolicy.valueOf(string(value, "existing-world-policy", "REFUSE").toUpperCase(Locale.ROOT))));
        }
        return Collections.unmodifiableMap(result);
    }

    private void createLayout() throws IOException {
        Files.createDirectories(dataDirectory);
        for (String dir : List.of("packs", "compiled", "cache", "reports", "backups")) Files.createDirectories(dataDirectory.resolve(dir));
    }
    private void copyDefault(String name) throws IOException {
        Path target = dataDirectory.resolve(name);
        if (Files.exists(target)) return;
        try (InputStream source = ConfigManager.class.getClassLoader().getResourceAsStream(name)) {
            if (source == null) throw new FileNotFoundException("Bundled resource missing: " + name);
            Files.copy(source, target);
        }
    }
    @SuppressWarnings("unchecked") private Map<String, Object> yaml(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            Object loaded = new Yaml().load(reader);
            return loaded instanceof Map<?, ?> map ? cast(map) : new LinkedHashMap<>();
        }
    }
    private static Map<String, Object> map(Map<String, Object> parent, String key) {
        Object value = parent.get(key); return value instanceof Map<?, ?> map ? cast(map) : new LinkedHashMap<>();
    }
    @SuppressWarnings("unchecked") private static Map<String, Object> cast(Map<?, ?> raw) { return (Map<String, Object>) raw; }
    private static boolean bool(Map<String, Object> m, String k, boolean d) { Object v=m.get(k); return v instanceof Boolean b?b:d; }
    private static String string(Map<String, Object> m, String k, String d) { Object v=m.get(k); return v==null?d:String.valueOf(v); }
    private static int integer(Map<String, Object> m, String k, int d) { Object v=m.get(k); return v instanceof Number n?n.intValue():d; }
    private static long mb(Map<String, Object> m, String k, int d) { return (long) integer(m,k,d) << 20; }
}

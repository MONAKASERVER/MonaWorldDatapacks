package net.monacraft.mwd.config;

import org.yaml.snakeyaml.Yaml;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class AssignmentStore {
    private final Path dataDirectory;
    public AssignmentStore(Path dataDirectory) { this.dataDirectory = dataDirectory; }

    public synchronized void assign(String world, String pack, String profile) throws IOException {
        mutate(world, pack, profile, false);
    }
    public synchronized void unassign(String world, String pack) throws IOException {
        mutate(world, pack, null, true);
    }
    @SuppressWarnings("unchecked") private void mutate(String world, String pack, String profile, boolean remove) throws IOException {
        validateId(world, "world"); validateId(pack, "pack");
        Path worldsFile = dataDirectory.resolve("worlds.yml");
        Map<String, Object> root;
        try (Reader reader = Files.newBufferedReader(worldsFile)) {
            Object loaded = new Yaml().load(reader); root = loaded instanceof Map<?, ?> m ? (Map<String, Object>) m : new LinkedHashMap<>();
        }
        Map<String, Object> worlds = (Map<String, Object>) root.computeIfAbsent("worlds", ignored -> new LinkedHashMap<>());
        Map<String, Object> value = (Map<String, Object>) worlds.computeIfAbsent(world, ignored -> defaultWorld(profile));
        List<Object> packs = value.get("datapacks") instanceof List<?> l ? new ArrayList<>((List<Object>) l) : new ArrayList<>();
        packs.removeIf(item -> item instanceof String id ? id.equals(pack) : item instanceof Map<?, ?> m && pack.equals(String.valueOf(m.get("id"))));
        if (!remove) packs.add(new LinkedHashMap<>(Map.of("id", pack, "priority", 100)));
        value.put("datapacks", packs);
        if (profile != null) value.put("profile", profile.toUpperCase(Locale.ROOT));
        backup(worldsFile);
        Path temp = worldsFile.resolveSibling("worlds.yml.tmp");
        Files.writeString(temp, new Yaml().dump(root), StandardCharsets.UTF_8);
        atomicReplace(temp, worldsFile);
    }
    private static Map<String, Object> defaultWorld(String profile) {
        boolean end = profile != null && profile.equalsIgnoreCase("STELLARITY");
        Map<String, Object> value = new LinkedHashMap<>(); value.put("enabled", true); value.put("environment", end ? "THE_END" : "NETHER");
        value.put("profile", profile == null ? "GENERIC" : profile.toUpperCase(Locale.ROOT));
        value.put("source-dimension", end ? "minecraft:the_end" : "minecraft:the_nether");
        value.put("strategy", "SCOPED_WORLDGEN"); value.put("existing-world-policy", "REFUSE"); return value;
    }
    private void backup(Path file) throws IOException {
        if (!Files.exists(file)) return;
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now());
        Files.copy(file, dataDirectory.resolve("backups").resolve("worlds-" + stamp + ".yml"), StandardCopyOption.REPLACE_EXISTING);
    }
    private static void validateId(String value, String label) throws IOException {
        if (!value.matches("[A-Za-z0-9._-]+")) throw new IOException("Invalid " + label + " id: " + value);
    }
    private static void atomicReplace(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }
}


package net.monacraft.mwd.regen;

import net.monacraft.mwd.compiler.CompilationResult;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class RegenerationRequestStore {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private final Path dataDirectory;
    private final Clock clock;

    public RegenerationRequestStore(Path dataDirectory) { this(dataDirectory, Clock.systemDefaultZone()); }
    RegenerationRequestStore(Path dataDirectory, Clock clock) {
        this.dataDirectory = dataDirectory.toAbsolutePath().normalize();
        this.clock = clock;
    }

    public void request(String world, String dimensionKey) throws IOException {
        Path directory = dataDirectory.resolve("pending-regeneration");
        Files.createDirectories(directory);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(world.getBytes(StandardCharsets.UTF_8));
        Path target = directory.resolve(encoded + ".properties");
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Properties properties = new Properties();
        properties.setProperty("world", world);
        properties.setProperty("dimension-key", dimensionKey);
        try (OutputStream output = Files.newOutputStream(temporary, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            properties.store(output, "MonaWorldDatapacks pending safe regeneration");
        }
        move(temporary, target);
    }

    public List<Outcome> process(Map<String, CompilationResult> compilations) throws IOException {
        Path directory = dataDirectory.resolve("pending-regeneration");
        if (!Files.isDirectory(directory)) return List.of();
        List<Path> markers;
        try (var paths = Files.list(directory)) { markers = paths.filter(path -> path.getFileName().toString().endsWith(".properties")).sorted().toList(); }
        List<Outcome> outcomes = new ArrayList<>();
        for (Path marker : markers) outcomes.add(processOne(marker, compilations));
        return List.copyOf(outcomes);
    }

    private Outcome processOne(Path marker, Map<String, CompilationResult> compilations) {
        String world = marker.getFileName().toString();
        try {
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(marker)) { properties.load(input); }
            world = require(properties, "world");
            String dimensionKey = require(properties, "dimension-key");
            CompilationResult result = compilations.get(world);
            if (result == null || !result.success() || !result.dimensionKey().equals(dimensionKey))
                throw new IOException("Compiled assignment no longer matches " + dimensionKey);
            Path target = CustomWorldPath.resolve(CustomWorldPath.serverRoot(dataDirectory), dimensionKey);
            if (!Files.exists(target)) {
                Files.delete(marker);
                return new Outcome(world, true, "world folder was already absent; a clean world will be created");
            }
            if (!Files.isDirectory(target)) throw new IOException("Expected a world directory but found: " + target);
            Path backup = dataDirectory.resolve("backups/regenerated").resolve(STAMP.format(ZonedDateTime.now(clock))).resolve(target.getFileName());
            Files.createDirectories(backup.getParent());
            move(target, backup);
            Files.delete(marker);
            return new Outcome(world, true, "moved " + target.getFileName() + " to " + backup);
        } catch (IOException | RuntimeException failure) {
            return new Outcome(world, false, failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    private static String require(Properties properties, String key) throws IOException {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) throw new IOException("Missing marker property: " + key);
        return value.trim();
    }

    private static void move(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }

    public record Outcome(String world, boolean success, String detail) {}
}

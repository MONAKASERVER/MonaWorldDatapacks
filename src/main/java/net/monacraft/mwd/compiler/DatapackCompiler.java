package net.monacraft.mwd.compiler;

import com.google.gson.*;
import net.monacraft.mwd.config.*;
import net.monacraft.mwd.pack.*;
import net.monacraft.mwd.resource.*;
import net.monacraft.mwd.profile.*;
import net.monacraft.mwd.util.WorldNameSanitizer;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

public final class DatapackCompiler {
    public static final String TRANSFORM_VERSION = "13";
    private final Path dataDirectory;
    private final PluginConfiguration config;
    private final DatapackManager manager;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public DatapackCompiler(Path dataDirectory, PluginConfiguration config, DatapackManager manager) {
        this.dataDirectory = dataDirectory; this.config = config; this.manager = manager;
    }

    public CompilationResult compile(WorldAssignment assignment) {
        try {
            if (!assignment.enabled()) return CompilationResult.failure(assignment.worldName(), "Assignment is disabled");
            if (!"ZIP".equalsIgnoreCase(config.outputFormat())) return CompilationResult.failure(assignment.worldName(), "Only compiler.output-format ZIP is supported");
            if (!"SCOPED_WORLDGEN".equalsIgnoreCase(assignment.strategy())) return CompilationResult.failure(assignment.worldName(), "Unsupported strategy: " + assignment.strategy());
            String existing = existingWorldBlockReason(assignment);
            if (existing != null) return CompilationResult.failure(assignment.worldName(), existing);
            Map<String, DatapackAnalysis> analyses = new LinkedHashMap<>();
            DatapackProfile profile = new ProfileRegistry().named(assignment.profile());
            for (PackAssignment pack : assignment.datapacks()) {
                DatapackAnalysis analysis = manager.scan(pack.id()); analyses.put(pack.id(), analysis);
                if (config.rejectUnknownResources() && !analysis.compatibility().unsafeScopedUnknownResources().isEmpty())
                    return CompilationResult.failure(assignment.worldName(), "Safety policy rejects unknown worldgen resource(s) in "
                            + pack.id() + ": " + summarize(analysis.compatibility().unsafeScopedUnknownResources()));
                if (config.strictMode() && !analysis.compatibility().safeForScopedCompilation())
                    return CompilationResult.failure(assignment.worldName(), "Strict mode rejected " + pack.id() + ": " + analysis.compatibility().result() + " " + analysis.compatibility().problems());
            }
            DatapackAnalysis primary = analyses.values().stream().filter(profile::matches).findFirst().orElse(analyses.values().stream().findFirst().orElse(null));
            if (primary == null) return CompilationResult.failure(assignment.worldName(), "No datapacks assigned");
            List<String> profileErrors = profile.validate(primary, assignment);
            if (!profileErrors.isEmpty()) return CompilationResult.failure(assignment.worldName(), "Profile " + profile.id() + " rejected " + primary.id() + ": " + profileErrors);
            ConflictResolver.Resolution resolved = new ConflictResolver().resolve(assignment.datapacks(), analyses);
            if (resolved.resources().isEmpty()) return CompilationResult.failure(assignment.worldName(), "No worldgen resources found");
            String namespace = WorldNameSanitizer.namespace(config.namespacePrefix(), assignment.worldName());
            NamespaceMapper mapper = new NamespaceMapper(namespace, resolved.resources().keySet());
            String cacheKey = cacheKey(assignment, analyses, namespace);
            Path output = dataDirectory.resolve("compiled").resolve(assignment.worldName() + ".zip");
            Path marker = dataDirectory.resolve("cache").resolve(assignment.worldName() + ".sha256");
            Files.createDirectories(output.getParent());
            Files.createDirectories(marker.getParent());
            if (config.cache() && Files.isRegularFile(output) && Files.isRegularFile(marker)
                    && Files.readString(marker).trim().equals(cacheKey))
                return new CompilationResult(assignment.worldName(), true, true, output, namespace,
                        namespace + ':' + safeWorldPath(assignment.worldName()), successMessages(analyses, true), resolved.conflicts());

            Path temporary = output.resolveSibling(output.getFileName() + ".tmp"); Files.deleteIfExists(temporary);
            JsonResourceRewriter rewriter = new JsonResourceRewriter();
            NbtStructureRewriter nbtRewriter = new NbtStructureRewriter();
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW))) {
                JsonObject meta = new JsonObject(), pack = new JsonObject();
                JsonArray minimum = new JsonArray(); minimum.add(94); minimum.add(1);
                JsonArray maximum = new JsonArray(); maximum.add(94); maximum.add(1);
                pack.add("min_format", minimum); pack.add("max_format", maximum);
                pack.addProperty("description", "MonaWorldDatapacks scoped worldgen for " + assignment.worldName()); meta.add("pack", pack);
                write(zip, "pack.mcmeta", gson.toJson(meta));
                for (Map.Entry<ResourceKey, ResourceNode> entry : resolved.resources().entrySet()) {
                    ResourceKey key = entry.getKey(); ResourceLocation mapped = mapper.map(key);
                    // The source dimension is used as the template for the one
                    // assignment-specific dimension written below. Emitting it
                    // as well would expose an unintended second dimension.
                    if (key.type() == ResourceType.DIMENSION) continue;
                    ResourceNode node = entry.getValue();
                    if (node.isBinary()) write(zip, resourcePath(key.type(), mapped, true),
                            nbtRewriter.rewrite(node.binary(), mapper));
                    else {
                        JsonElement rewritten = rewriter.rewrite(node.json(), key.type(), mapper);
                        write(zip, resourcePath(key.type(), mapped, false), gson.toJson(rewritten));
                    }
                }
                String dimensionPath = safeWorldPath(assignment.worldName());
                JsonElement dimension = new DimensionCompiler().create(assignment, resolved.resources(), mapper, rewriter);
                write(zip, "data/" + namespace + "/dimension/" + dimensionPath + ".json", gson.toJson(dimension));
                write(zip, "mwd-manifest.json", manifest(assignment, analyses, namespace, cacheKey));
            }
            atomicReplace(temporary, output);
            Path markerTmp = marker.resolveSibling(marker.getFileName() + ".tmp"); Files.writeString(markerTmp, cacheKey, StandardCharsets.UTF_8);
            atomicReplace(markerTmp, marker);
            return new CompilationResult(assignment.worldName(), true, false, output, namespace,
                    namespace + ':' + safeWorldPath(assignment.worldName()), successMessages(analyses, false), resolved.conflicts());
        } catch (IOException | RuntimeException e) {
            return CompilationResult.failure(assignment.worldName(), e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private String existingWorldBlockReason(WorldAssignment assignment) throws IOException {
        Path serverRoot = dataDirectory.getParent() == null ? null : dataDirectory.getParent().getParent();
        if (serverRoot == null) return null;
        Path world = serverRoot.resolve(assignment.worldName()).normalize();
        if (!world.startsWith(serverRoot.normalize())) return "Unsafe world path";
        boolean generated = Files.exists(world.resolve("level.dat")) || Files.isDirectory(world.resolve("region"));
        if (!generated) return null;
        if (assignment.existingWorldPolicy() == ExistingWorldPolicy.REFUSE) return "Existing generated world refused: " + world;
        if (config.rejectExistingGeneratedWorld() && assignment.existingWorldPolicy() != ExistingWorldPolicy.FORCE)
            return "Global safety setting rejects existing generated worlds: " + world;
        return null;
    }
    private String cacheKey(WorldAssignment a, Map<String, DatapackAnalysis> analyses, String namespace) {
        StringBuilder value = new StringBuilder(TRANSFORM_VERSION).append("|1.21.11|").append(namespace).append('|')
                .append(a.environment()).append('|').append(a.profile()).append('|').append(a.sourceDimension());
        a.datapacks().stream().sorted(Comparator.comparing(PackAssignment::id)).forEach(p -> value.append('|').append(p.id()).append(':').append(p.priority()).append(':').append(analyses.get(p.id()).sha256()));
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private String manifest(WorldAssignment a, Map<String, DatapackAnalysis> analyses, String namespace, String cacheKey) {
        JsonObject root = new JsonObject(); root.addProperty("transform_version", TRANSFORM_VERSION); root.addProperty("minecraft", "1.21.11");
        root.addProperty("world", a.worldName()); root.addProperty("namespace", namespace); root.addProperty("cache_key", cacheKey);
        JsonObject hashes = new JsonObject(); analyses.forEach((id, analysis) -> hashes.addProperty(id, analysis.sha256())); root.add("source_hashes", hashes);
        root.addProperty("omitted_server_global_resources", count(analyses, net.monacraft.mwd.compatibility.ScopeClass.SERVER_GLOBAL));
        root.addProperty("omitted_runtime_resources", count(analyses, net.monacraft.mwd.compatibility.ScopeClass.RUNTIME_SCOPABLE));
        root.addProperty("omitted_unknown_resources", count(analyses, net.monacraft.mwd.compatibility.ScopeClass.UNKNOWN));
        return gson.toJson(root);
    }
    private List<String> successMessages(Map<String, DatapackAnalysis> analyses, boolean cacheHit) {
        List<String> messages = new ArrayList<>();
        messages.add(cacheHit ? "Compiled cache reused" : "Compiled safely");
        int global = count(analyses, net.monacraft.mwd.compatibility.ScopeClass.SERVER_GLOBAL);
        int runtime = count(analyses, net.monacraft.mwd.compatibility.ScopeClass.RUNTIME_SCOPABLE);
        int unknown = count(analyses, net.monacraft.mwd.compatibility.ScopeClass.UNKNOWN);
        if (global > 0) messages.add("Omitted " + global + " server-global resource(s); source packs were not globally enabled");
        if (runtime > 0) messages.add("Omitted " + runtime + " runtime resource(s)");
        if (unknown > 0) messages.add("Omitted " + unknown + " non-worldgen unknown resource(s)");
        return List.copyOf(messages);
    }
    private static int count(Map<String, DatapackAnalysis> analyses, net.monacraft.mwd.compatibility.ScopeClass scope) {
        return analyses.values().stream().mapToInt(a -> a.compatibility().counts().getOrDefault(scope, 0)).sum();
    }
    private static String summarize(List<ResourceKey> keys) {
        String shown = keys.stream().limit(5).map(ResourceKey::toString).reduce((a, b) -> a + ", " + b).orElse("unknown");
        return keys.size() > 5 ? shown + " (and " + (keys.size() - 5) + " more)" : shown;
    }
    private static String safeWorldPath(String name) { return WorldNameSanitizer.namespace("world", name).substring("world_".length()); }
    private static String resourcePath(ResourceType type, ResourceLocation location, boolean binary) {
        if (type == ResourceType.UNKNOWN || type.directory().isEmpty()) throw new IllegalArgumentException("Unknown resource cannot be compiled: " + location);
        return "data/" + location.namespace() + '/' + type.directory() + '/' + location.path() + (binary ? ".nbt" : ".json");
    }
    private static void write(ZipOutputStream zip, String name, String contents) throws IOException {
        write(zip, name, contents.getBytes(StandardCharsets.UTF_8));
    }
    private static void write(ZipOutputStream zip, String name, byte[] contents) throws IOException {
        ZipEntry entry = new ZipEntry(name); entry.setTime(0); zip.putNextEntry(entry); zip.write(contents); zip.closeEntry();
    }
    private static void atomicReplace(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }
}

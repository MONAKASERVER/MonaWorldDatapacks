package net.monacraft.mwd.pack;

import com.google.gson.*;
import net.monacraft.mwd.cache.PackHasher;
import net.monacraft.mwd.compatibility.*;
import net.monacraft.mwd.resource.*;
import net.monacraft.mwd.security.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class DatapackScanner {
    private final ZipLimits limits;
    private final Gson gson = new Gson();
    private final ReferenceScanner referenceScanner = new ReferenceScanner();
    public DatapackScanner(ZipLimits limits) { this.limits = limits; }

    public DatapackAnalysis scan(String id, Path archive) throws IOException {
        String hash = PackHasher.sha256(archive);
        List<ResourceNode> resources = new ArrayList<>();
        List<String> errors = new ArrayList<>(), unknownRefs = new ArrayList<>();
        Set<String> namespaces = new TreeSet<>();
        DatapackMetadata metadata;
        try (SafeZipReader zip = new SafeZipReader(archive, limits)) {
            if (!zip.contains("pack.mcmeta")) throw new IOException("pack.mcmeta is missing from archive root");
            if (zip.names().stream().noneMatch(n -> n.startsWith("data/"))) throw new IOException("data/ directory is missing");
            metadata = parseMetadata(zip.read("pack.mcmeta"));
            if (!metadata.supports(PackVersion.MINECRAFT_1_21_11))
                errors.add("Unsupported pack format range " + metadata.formatDisplay() + "; Paper 1.21.11 requires 94.1");
            for (String name : zip.names()) {
                if (!name.startsWith("data/") || (!name.endsWith(".json") && !name.endsWith(".mcfunction"))) continue;
                ParsedPath parsed = parsePath(name);
                if (parsed == null) continue;
                namespaces.add(parsed.key.location().namespace());
                try {
                    String contents = new String(zip.read(name), StandardCharsets.UTF_8);
                    JsonElement json = name.endsWith(".json") ? JsonParser.parseString(contents) : new JsonPrimitive(contents);
                    ReferenceScanner.ScanResult refs = referenceScanner.scan(parsed.key, json);
                    unknownRefs.addAll(refs.unknownReferenceCandidates());
                    resources.add(new ResourceNode(parsed.key, json, Path.of(name), refs.references(), id));
                } catch (JsonParseException e) {
                    errors.add("Broken JSON " + name + ": " + e.getMessage());
                }
            }
        }
        ResourceGraph graph;
        try { graph = new ResourceGraph(resources); }
        catch (IllegalArgumentException duplicate) {
            errors.add(duplicate.getMessage()); graph = new ResourceGraph(deduplicate(resources));
        }
        CompatibilityReport report = new CompatibilityAnalyzer().analyze(id, graph.nodes().values(), graph, namespaces, errors, unknownRefs);
        return new DatapackAnalysis(id, archive, hash, metadata, Collections.unmodifiableSet(namespaces),
                List.copyOf(graph.nodes().values()), graph, List.copyOf(errors), List.copyOf(unknownRefs), report);
    }

    private DatapackMetadata parseMetadata(byte[] bytes) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject pack = root.getAsJsonObject("pack");
            if (pack == null) throw new IOException("pack.mcmeta lacks pack object");
            String description = pack.has("description") ? pack.get("description").toString() : "";
            PackVersion min;
            PackVersion max;
            if (pack.has("min_format") && pack.has("max_format")) {
                min = parseVersion(pack.get("min_format"), false);
                max = parseVersion(pack.get("max_format"), true);
            } else if (pack.has("supported_formats")) {
                PackVersion[] range = parseLegacySupportedFormats(pack.get("supported_formats"));
                min = range[0]; max = range[1];
            } else if (pack.has("pack_format")) {
                min = parseVersion(pack.get("pack_format"), false);
                max = min;
            } else throw new IOException("pack.mcmeta requires min_format/max_format (or legacy pack_format)");
            if (min.compareTo(max) > 0) throw new IOException("pack.mcmeta min_format is greater than max_format");
            return new DatapackMetadata(min, max, description);
        } catch (RuntimeException e) { throw new IOException("Invalid pack.mcmeta", e); }
    }
    private PackVersion parseVersion(JsonElement element, boolean maximum) throws IOException {
        if (element.isJsonArray()) {
            JsonArray values = element.getAsJsonArray();
            if (values.isEmpty() || values.size() > 2) throw new IOException("Invalid pack version array: " + element);
            return new PackVersion(values.get(0).getAsInt(), values.size() == 2 ? values.get(1).getAsInt() : (maximum ? Integer.MAX_VALUE : 0));
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) throw new IOException("Invalid pack version: " + element);
        String value = element.getAsString();
        int dot = value.indexOf('.');
        return dot < 0 ? new PackVersion(Integer.parseInt(value), maximum ? Integer.MAX_VALUE : 0)
                : new PackVersion(Integer.parseInt(value.substring(0, dot)), Integer.parseInt(value.substring(dot + 1)));
    }
    private PackVersion[] parseLegacySupportedFormats(JsonElement element) throws IOException {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            int version = element.getAsInt(); return new PackVersion[] { new PackVersion(version, 0), new PackVersion(version, Integer.MAX_VALUE) };
        }
        if (element.isJsonArray() && element.getAsJsonArray().size() == 2) {
            int min = element.getAsJsonArray().get(0).getAsInt(), max = element.getAsJsonArray().get(1).getAsInt();
            return new PackVersion[] { new PackVersion(min, 0), new PackVersion(max, Integer.MAX_VALUE) };
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("min_inclusive") && object.has("max_inclusive")) {
                int min = object.get("min_inclusive").getAsInt(), max = object.get("max_inclusive").getAsInt();
                return new PackVersion[] { new PackVersion(min, 0), new PackVersion(max, Integer.MAX_VALUE) };
            }
        }
        throw new IOException("Invalid legacy supported_formats: " + element);
    }
    private ParsedPath parsePath(String name) {
        String[] parts = name.split("/");
        if (parts.length < 4) return null;
        String namespace = parts[1];
        String relative = String.join("/", Arrays.copyOfRange(parts, 2, parts.length));
        int suffix = relative.endsWith(".mcfunction") ? 11 : relative.endsWith(".json") ? 5 : -1;
        if (suffix < 0) return null;
        relative = relative.substring(0, relative.length() - suffix);
        ResourceType.Match match = ResourceType.match(relative);
        String resourcePath = match.type() == ResourceType.UNKNOWN ? relative
                : relative.substring(match.matchedDirectory().length() + 1);
        try { return new ParsedPath(new ResourceKey(match.type(), new ResourceLocation(namespace, resourcePath))); }
        catch (IllegalArgumentException invalid) { return null; }
    }
    private static List<ResourceNode> deduplicate(List<ResourceNode> nodes) {
        Map<ResourceKey, ResourceNode> unique = new LinkedHashMap<>(); nodes.forEach(n -> unique.putIfAbsent(n.key(), n)); return List.copyOf(unique.values());
    }
    private record ParsedPath(ResourceKey key) {}
}

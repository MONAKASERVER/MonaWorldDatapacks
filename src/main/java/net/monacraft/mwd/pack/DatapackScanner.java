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
                if (!name.startsWith("data/") || (!name.endsWith(".json") && !name.endsWith(".mcfunction") && !name.endsWith(".nbt"))) continue;
                ParsedPath parsed = parsePath(name);
                if (parsed == null) continue;
                namespaces.add(parsed.key.location().namespace());
                try {
                    if (name.endsWith(".nbt")) {
                        resources.add(new ResourceNode(parsed.key, null, Path.of(name), List.of(), id, zip.read(name)));
                        continue;
                    }
                    String contents = new String(zip.read(name), StandardCharsets.UTF_8);
                    JsonElement json = name.endsWith(".json") ? JsonParser.parseString(contents) : new JsonPrimitive(contents);
                    ReferenceScanner.ScanResult refs = referenceScanner.scan(parsed.key, json);
                    // Unknown references in recipes/functions/etc. are harmless to the
                    // scoped output because those resources are never copied. Keep the
                    // strict check for resources that can affect generated terrain.
                    if (parsed.key.type().worldgen() || looksLikeWorldgenResource(parsed.key))
                        unknownRefs.addAll(refs.unknownReferenceCandidates());
                    resources.add(new ResourceNode(parsed.key, json, Path.of(name), refs.references(), id));
                } catch (JsonParseException e) {
                    errors.add("Broken JSON " + name + ": " + e.getMessage());
                }
            }
        }
        Deduplication deduplication = deduplicate(resources);
        errors.addAll(deduplication.conflicts());
        ResourceGraph graph = new ResourceGraph(deduplication.resources());
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
        int suffix = relative.endsWith(".mcfunction") ? 11 : relative.endsWith(".json") ? 5 : relative.endsWith(".nbt") ? 4 : -1;
        if (suffix < 0) return null;
        relative = relative.substring(0, relative.length() - suffix);
        ResourceType.Match match = ResourceType.match(relative);
        String resourcePath = match.type() == ResourceType.UNKNOWN ? relative
                : relative.substring(match.matchedDirectory().length() + 1);
        try { return new ParsedPath(new ResourceKey(match.type(), new ResourceLocation(namespace, resourcePath))); }
        catch (IllegalArgumentException invalid) { return null; }
    }
    private static Deduplication deduplicate(List<ResourceNode> nodes) {
        Map<ResourceKey, ResourceNode> unique = new LinkedHashMap<>();
        List<String> conflicts = new ArrayList<>();
        for (ResourceNode node : nodes) {
            ResourceNode previous = unique.putIfAbsent(node.key(), node);
            if (previous != null && !sameContents(previous, node)) {
                conflicts.add("Duplicate resource with different contents: " + node.key()
                        + " (" + previous.archivePath() + " vs " + node.archivePath() + ")");
            }
        }
        return new Deduplication(List.copyOf(unique.values()), List.copyOf(conflicts));
    }
    private static boolean sameContents(ResourceNode left, ResourceNode right) {
        if (left.isBinary() || right.isBinary())
            return left.isBinary() && right.isBinary() && Arrays.equals(left.binary(), right.binary());
        return Objects.equals(left.json(), right.json());
    }
    private static boolean looksLikeWorldgenResource(ResourceKey key) {
        String path = key.location().path();
        return path.equals("worldgen") || path.startsWith("worldgen/")
                || path.equals("dimension") || path.startsWith("dimension/")
                || path.equals("dimension_type") || path.startsWith("dimension_type/");
    }
    private record ParsedPath(ResourceKey key) {}
    private record Deduplication(List<ResourceNode> resources, List<String> conflicts) {}
}

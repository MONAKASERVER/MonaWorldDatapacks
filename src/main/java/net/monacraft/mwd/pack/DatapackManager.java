package net.monacraft.mwd.pack;

import net.monacraft.mwd.security.ZipLimits;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class DatapackManager {
    private final Path packsDirectory;
    private final DatapackScanner scanner;
    public DatapackManager(Path dataDirectory, ZipLimits limits) {
        packsDirectory = dataDirectory.resolve("packs"); scanner = new DatapackScanner(limits);
    }
    public List<String> listPackIds() throws IOException {
        if (!Files.isDirectory(packsDirectory)) return List.of();
        try (var files = Files.list(packsDirectory)) {
            return files.filter(Files::isRegularFile).map(p -> p.getFileName().toString())
                    .filter(n -> n.toLowerCase(Locale.ROOT).endsWith(".zip"))
                    .map(n -> n.substring(0, n.length() - 4)).sorted().toList();
        }
    }
    public DatapackAnalysis scan(String id) throws IOException {
        Path archive = resolve(id); return scanner.scan(id, archive);
    }
    public Path resolve(String id) throws IOException {
        if (!id.matches("[A-Za-z0-9._-]+")) throw new IOException("Invalid pack id: " + id);
        Path path = packsDirectory.resolve(id + ".zip").normalize();
        if (!path.startsWith(packsDirectory.normalize()) || !Files.isRegularFile(path)) throw new IOException("Pack not found: " + id);
        return path;
    }
}

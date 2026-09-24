package net.monacraft.mwd.paper;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class BundledDatapackWriter {
    public Path write(Collection<Path> inputs, Path output) throws IOException {
        if (inputs.isEmpty()) throw new IllegalArgumentException("At least one generated datapack is required");
        Map<String, byte[]> files = new TreeMap<>();
        for (Path input : inputs) {
            try (ZipFile zip = new ZipFile(input.toFile())) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory() || entry.getName().equals("mwd-manifest.json")) continue;
                    String name = safeName(entry.getName());
                    byte[] bytes;
                    try (InputStream stream = zip.getInputStream(entry)) { bytes = stream.readAllBytes(); }
                    byte[] previous = files.putIfAbsent(name, bytes);
                    // Every generated pack has the same format bounds, but its description names
                    // the assigned world. A bundle only needs one root metadata file.
                    if (previous != null && name.equals("pack.mcmeta")) continue;
                    if (previous != null && !Arrays.equals(previous, bytes))
                        throw new IOException("Generated datapack bundle conflict: " + name);
                }
            }
        }
        if (!files.containsKey("pack.mcmeta")) throw new IOException("Generated datapacks lack pack.mcmeta");
        Files.createDirectories(output.getParent());
        Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temporary,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
            for (var file : files.entrySet()) {
                ZipEntry entry = new ZipEntry(file.getKey());
                entry.setTime(0L);
                zip.putNextEntry(entry);
                zip.write(file.getValue());
                zip.closeEntry();
            }
        }
        try { return Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ignored) { return Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING); }
    }

    private String safeName(String raw) throws IOException {
        String value = raw.replace('\\', '/');
        if (value.startsWith("/") || value.contains("../") || value.equals(".."))
            throw new IOException("Unsafe generated datapack path: " + raw);
        return value;
    }
}

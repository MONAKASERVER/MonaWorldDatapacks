package net.monacraft.mwd.security;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class SafeZipReader implements Closeable {
    private final Path archive;
    private final ZipLimits limits;
    private final ZipFile zip;
    private final Map<String, ZipEntry> entries;

    public SafeZipReader(Path archive, ZipLimits limits) throws IOException {
        this.archive = archive.toAbsolutePath().normalize();
        this.limits = limits;
        if (!Files.isRegularFile(this.archive)) throw new FileNotFoundException(this.archive.toString());
        if (Files.size(this.archive) > limits.maxArchiveBytes()) throw new UnsafeArchiveException("Archive exceeds maximum size: " + archive);
        this.zip = new ZipFile(this.archive.toFile());
        try {
            this.entries = validate();
        } catch (IOException | RuntimeException failure) {
            this.zip.close();
            throw failure;
        }
    }

    private Map<String, ZipEntry> validate() throws IOException {
        Map<String, ZipEntry> result = new LinkedHashMap<>();
        long total = 0;
        int files = 0;
        Enumeration<? extends ZipEntry> all = zip.entries();
        while (all.hasMoreElements()) {
            ZipEntry entry = all.nextElement();
            String name = normalizeEntry(entry.getName());
            if (name.isEmpty() || entry.isDirectory()) continue;
            if (++files > limits.maxFiles()) throw new UnsafeArchiveException("Archive contains too many files: " + files);
            long declared = entry.getSize();
            if (declared > limits.maxSingleFileBytes()) throw new UnsafeArchiveException("Entry exceeds size limit: " + name);
            if (declared > 0 && (total += declared) > limits.maxUncompressedBytes()) throw new UnsafeArchiveException("Archive exceeds uncompressed size limit");
            if (result.putIfAbsent(name, entry) != null) throw new UnsafeArchiveException("Duplicate ZIP entry: " + name);
        }
        return Collections.unmodifiableMap(result);
    }

    static String normalizeEntry(String raw) throws UnsafeArchiveException {
        String value = raw.replace('\\', '/');
        if (value.startsWith("/") || value.matches("^[A-Za-z]:.*")) throw new UnsafeArchiveException("Absolute ZIP entry rejected: " + raw);
        Path normalized;
        try { normalized = Path.of(value).normalize(); }
        catch (InvalidPathException e) { throw new UnsafeArchiveException("Invalid ZIP entry: " + raw); }
        if (normalized.isAbsolute() || normalized.startsWith("..")) throw new UnsafeArchiveException("ZIP Slip entry rejected: " + raw);
        String safe = normalized.toString().replace('\\', '/');
        if (safe.contains("\u0000")) throw new UnsafeArchiveException("NUL byte in ZIP entry");
        return safe;
    }

    public Set<String> names() { return entries.keySet(); }
    public boolean contains(String name) { return entries.containsKey(name.replace('\\', '/')); }

    public byte[] read(String name) throws IOException {
        ZipEntry entry = entries.get(name.replace('\\', '/'));
        if (entry == null) throw new FileNotFoundException(name + " in " + archive);
        try (InputStream input = zip.getInputStream(entry); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16_384];
            long count = 0;
            for (int n; (n = input.read(buffer)) >= 0;) {
                count += n;
                if (count > limits.maxSingleFileBytes()) throw new UnsafeArchiveException("Expanded entry exceeds size limit: " + name);
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }

    @Override public void close() throws IOException { zip.close(); }
}

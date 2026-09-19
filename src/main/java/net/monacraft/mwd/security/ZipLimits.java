package net.monacraft.mwd.security;

public record ZipLimits(long maxArchiveBytes, long maxUncompressedBytes, long maxSingleFileBytes, int maxFiles) {
    public static ZipLimits defaults() {
        return new ZipLimits(512L << 20, 2048L << 20, 64L << 20, 100_000);
    }
}


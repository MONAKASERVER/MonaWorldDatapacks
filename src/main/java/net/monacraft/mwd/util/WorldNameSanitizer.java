package net.monacraft.mwd.util;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;
import java.util.Locale;

public final class WorldNameSanitizer {
    private WorldNameSanitizer() {}
    public static String namespace(String prefix, String worldName) {
        String clean = worldName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_")
                .replaceAll("^[_.-]+|[_.-]+$", "");
        if (clean.isBlank()) clean = "world";
        if (clean.length() > 32) clean = clean.substring(0, 32);
        String base = prefix.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "") + '_' + clean;
        if (base.length() <= 48 && clean.equals(worldName.toLowerCase(Locale.ROOT))) return base;
        return (base.length() > 39 ? base.substring(0, 39) : base) + '_' + shortHash(worldName);
    }
    private static String shortHash(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 4);
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}

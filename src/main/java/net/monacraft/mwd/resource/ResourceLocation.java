package net.monacraft.mwd.resource;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public record ResourceLocation(String namespace, String path) implements Comparable<ResourceLocation> {
    private static final Pattern NS = Pattern.compile("[a-z0-9._-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9/._-]+");

    public ResourceLocation {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        if (!NS.matcher(namespace).matches() || !PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("Invalid resource location: " + namespace + ':' + path);
        }
    }

    public static ResourceLocation parse(String value) {
        Objects.requireNonNull(value, "value");
        int split = value.indexOf(':');
        return split < 0 ? new ResourceLocation("minecraft", value)
                : new ResourceLocation(value.substring(0, split), value.substring(split + 1));
    }

    public static Optional<ResourceLocation> tryParse(String value) {
        try {
            return Optional.of(parse(value.toLowerCase(Locale.ROOT)));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    @Override public String toString() { return namespace + ':' + path; }
    @Override public int compareTo(ResourceLocation other) { return toString().compareTo(other.toString()); }
}


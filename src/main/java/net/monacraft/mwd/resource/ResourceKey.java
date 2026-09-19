package net.monacraft.mwd.resource;

public record ResourceKey(ResourceType type, ResourceLocation location) implements Comparable<ResourceKey> {
    @Override public String toString() { return type.name().toLowerCase() + " " + location; }
    @Override public int compareTo(ResourceKey other) { return toString().compareTo(other.toString()); }
}


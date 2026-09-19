package net.monacraft.mwd.pack;

public record PackVersion(int major, int minor) implements Comparable<PackVersion> {
    public static final PackVersion MINECRAFT_1_21_11 = new PackVersion(94, 1);
    public PackVersion {
        if (major < 0 || minor < 0) throw new IllegalArgumentException("Negative pack version");
    }
    @Override public int compareTo(PackVersion other) {
        int majorOrder = Integer.compare(major, other.major);
        return majorOrder != 0 ? majorOrder : Integer.compare(minor, other.minor);
    }
    @Override public String toString() { return major + "." + minor; }
}


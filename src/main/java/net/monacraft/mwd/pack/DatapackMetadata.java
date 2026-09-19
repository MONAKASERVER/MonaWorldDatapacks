package net.monacraft.mwd.pack;
public record DatapackMetadata(PackVersion minimum, PackVersion maximum, String description) {
    public boolean supports(PackVersion version) { return minimum.compareTo(version) <= 0 && maximum.compareTo(version) >= 0; }
    public String formatDisplay() { return minimum.equals(maximum) ? minimum.toString() : minimum + "–" + maximum; }
}

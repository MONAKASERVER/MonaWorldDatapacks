package net.monacraft.mwd.compiler;
import net.monacraft.mwd.resource.ResourceKey;
import java.util.List;
public record ResourceConflict(ResourceKey key, List<String> providers, String winner, String reason) {}


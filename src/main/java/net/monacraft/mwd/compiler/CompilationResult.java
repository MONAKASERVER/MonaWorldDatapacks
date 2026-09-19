package net.monacraft.mwd.compiler;
import java.nio.file.Path;
import java.util.List;
public record CompilationResult(String world, boolean success, boolean cacheHit, Path output,
                                String namespace, String dimensionKey, List<String> messages,
                                List<ResourceConflict> conflicts) {
    public static CompilationResult failure(String world, String message) {
        return new CompilationResult(world, false, false, null, null, null, List.of(message), List.of());
    }
}


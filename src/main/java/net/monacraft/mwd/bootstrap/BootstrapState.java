package net.monacraft.mwd.bootstrap;

import net.monacraft.mwd.compiler.CompilationResult;
import net.monacraft.mwd.config.ConfigurationBundle;
import net.monacraft.mwd.pack.DatapackManager;
import java.nio.file.Path;
import java.util.*;

public record BootstrapState(Path dataDirectory, ConfigurationBundle configuration,
                             DatapackManager packManager, Map<String, CompilationResult> compilations,
                             List<String> bootstrapErrors) {
    private static volatile BootstrapState current;
    public static BootstrapState get() { return Objects.requireNonNull(current, "Bootstrap did not run"); }
    static void set(BootstrapState state) { current = state; }
}


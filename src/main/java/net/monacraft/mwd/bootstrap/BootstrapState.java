package net.monacraft.mwd.bootstrap;

import net.monacraft.mwd.compiler.CompilationResult;
import net.monacraft.mwd.config.ConfigurationBundle;
import net.monacraft.mwd.config.ConfigManager;
import net.monacraft.mwd.pack.DatapackManager;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public record BootstrapState(Path dataDirectory, ConfigurationBundle configuration,
                             DatapackManager packManager, Map<String, CompilationResult> compilations,
                             List<String> bootstrapErrors) {
    private static volatile BootstrapState current;
    public static BootstrapState get() { return Objects.requireNonNull(current, "Bootstrap did not run"); }
    static void set(BootstrapState state) { current = state; }

    public static synchronized BootstrapState reloadConfiguration() throws IOException {
        BootstrapState previous = get();
        ConfigurationBundle configuration = new ConfigManager(previous.dataDirectory()).load();
        BootstrapState refreshed = new BootstrapState(previous.dataDirectory(), configuration,
                new DatapackManager(previous.dataDirectory(), configuration.plugin().zipLimits()),
                previous.compilations(), previous.bootstrapErrors());
        current = refreshed;
        return refreshed;
    }

    public static synchronized void recordCompilations(Collection<CompilationResult> results) {
        BootstrapState previous = get();
        Map<String, CompilationResult> merged = new LinkedHashMap<>(previous.compilations());
        for (CompilationResult result : results) merged.put(result.world(), result);
        current = new BootstrapState(previous.dataDirectory(), previous.configuration(), previous.packManager(),
                Collections.unmodifiableMap(merged), previous.bootstrapErrors());
    }
}

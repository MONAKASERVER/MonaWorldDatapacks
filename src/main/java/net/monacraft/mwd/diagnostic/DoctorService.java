package net.monacraft.mwd.diagnostic;

import net.monacraft.mwd.bootstrap.BootstrapState;
import org.bukkit.Bukkit;
import org.mvplugins.multiverse.core.MultiverseCoreApi;
import java.nio.file.*;
import java.util.*;

public final class DoctorService {
    public List<DiagnosticResult> run(String world) {
        BootstrapState state = BootstrapState.get(); List<DiagnosticResult> out = new ArrayList<>();
        out.add(ok("Paper", Bukkit.getVersion()));
        out.add(ok("Minecraft", Bukkit.getMinecraftVersion()));
        out.add(ok("Java", System.getProperty("java.version")));
        out.add(new DiagnosticResult(MultiverseCoreApi.isLoaded() ? DiagnosticResult.Severity.OK : DiagnosticResult.Severity.ERROR,
                "Multiverse-Core API", MultiverseCoreApi.isLoaded() ? "loaded" : "not loaded"));
        if (state.bootstrapErrors().isEmpty()) out.add(ok("Bootstrap", "no errors"));
        else state.bootstrapErrors().forEach(e -> out.add(new DiagnosticResult(DiagnosticResult.Severity.ERROR, "Bootstrap", e)));
        state.compilations().values().stream().filter(c -> world == null || c.world().equalsIgnoreCase(world)).forEach(c -> {
            boolean exists = c.output() != null && Files.isRegularFile(c.output());
            out.add(new DiagnosticResult(c.success() && exists ? DiagnosticResult.Severity.OK : DiagnosticResult.Severity.ERROR,
                    "Compiled " + c.world(), c.success() ? c.dimensionKey() + (c.cacheHit() ? " (cache)" : "") : String.join("; ", c.messages())));
            if (Bukkit.getWorld(c.world()) == null && Bukkit.getWorld(Objects.requireNonNullElse(NamespacedKeySafe.parse(c.dimensionKey()), NamespacedKeySafe.fallback())) == null)
                out.add(new DiagnosticResult(DiagnosticResult.Severity.WARNING, "World " + c.world(), "not loaded by Bukkit"));
        });
        return List.copyOf(out);
    }
    private static DiagnosticResult ok(String check, String detail) { return new DiagnosticResult(DiagnosticResult.Severity.OK, check, detail); }
    private static final class NamespacedKeySafe {
        static org.bukkit.NamespacedKey parse(String value) { return value == null ? null : org.bukkit.NamespacedKey.fromString(value); }
        static org.bukkit.NamespacedKey fallback() { return org.bukkit.NamespacedKey.minecraft("overworld"); }
    }
}

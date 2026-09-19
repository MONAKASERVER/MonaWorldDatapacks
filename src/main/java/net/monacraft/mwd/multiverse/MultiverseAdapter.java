package net.monacraft.mwd.multiverse;

import net.monacraft.mwd.compiler.CompilationResult;
import net.monacraft.mwd.config.*;
import org.bukkit.*;
import org.mvplugins.multiverse.core.MultiverseCoreApi;
import org.mvplugins.multiverse.core.world.options.ImportWorldOptions;
import org.slf4j.Logger;
import java.util.Map;

public final class MultiverseAdapter {
    private final Logger logger;
    public MultiverseAdapter(Logger logger) { this.logger = logger; }

    public void connect(ConfigurationBundle configuration, Map<String, CompilationResult> compilations) {
        MultiverseCoreApi.whenLoaded(api -> {
            for (CompilationResult result : compilations.values()) {
                if (!result.success()) continue;
                WorldAssignment assignment = configuration.worlds().get(result.world());
                if (assignment == null) continue;
                map(api, assignment, result, configuration.plugin().multiverseAutoImport());
            }
        });
    }

    private void map(MultiverseCoreApi api, WorldAssignment assignment, CompilationResult result, boolean autoImport) {
        var manager = api.getWorldManager();
        if (manager.isWorld(assignment.worldName()) || manager.isWorld(result.dimensionKey())) {
            logger.info("Multiverse mapping already exists: {} -> {}", assignment.worldName(), result.dimensionKey()); return;
        }
        if (!autoImport) {
            logger.warn("Custom dimension {} is discovered but Multiverse auto-import is disabled", result.dimensionKey()); return;
        }
        NamespacedKey key = NamespacedKey.fromString(result.dimensionKey());
        if (key == null) { logger.error("Invalid compiled dimension key: {}", result.dimensionKey()); return; }
        World.Environment environment;
        try { environment = World.Environment.valueOf(assignment.environment().toUpperCase()); }
        catch (IllegalArgumentException invalid) { logger.error("Invalid environment for {}: {}", assignment.worldName(), assignment.environment()); return; }
        manager.importWorld(ImportWorldOptions.worldKey(key).environment(environment))
                .onSuccess(world -> logger.info("Multiverse world mapping: {} -> {}", world.getName(), result.dimensionKey()))
                .onFailure(failure -> logger.error("Multiverse could not import custom dimension {}: {}", result.dimensionKey(), failure));
    }
}


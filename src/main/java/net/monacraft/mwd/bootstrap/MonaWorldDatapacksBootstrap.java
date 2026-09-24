package net.monacraft.mwd.bootstrap;

import io.papermc.paper.plugin.bootstrap.*;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.monacraft.mwd.compiler.*;
import net.monacraft.mwd.config.*;
import net.monacraft.mwd.pack.DatapackManager;
import net.monacraft.mwd.paper.DatapackDiscoveryAdapter;
import net.monacraft.mwd.regen.RegenerationRequestStore;
import java.io.IOException;
import java.util.*;

public final class MonaWorldDatapacksBootstrap implements PluginBootstrap {
    @Override public void bootstrap(BootstrapContext context) {
        List<String> errors = new ArrayList<>();
        Map<String, CompilationResult> results = new LinkedHashMap<>();
        try {
            ConfigurationBundle bundle = new ConfigManager(context.getDataDirectory()).load();
            DatapackManager packs = new DatapackManager(context.getDataDirectory(), bundle.plugin().zipLimits());
            DatapackCompiler compiler = new DatapackCompiler(context.getDataDirectory(), bundle.plugin(), packs);
            context.getLogger().info("Loading {} world assignments", bundle.worlds().size());
            for (WorldAssignment assignment : bundle.worlds().values()) {
                if (!assignment.enabled()) continue;
                context.getLogger().info("Assignment {} | packs={} | profile={} | strategy={}", assignment.worldName(),
                        assignment.datapacks().stream().map(PackAssignment::id).toList(), assignment.profile(), assignment.strategy());
                CompilationResult result = compiler.compile(assignment); results.put(assignment.worldName(), result);
                if (result.success()) context.getLogger().info("Compiled {} -> {}{} | SHA-256 cache verified", assignment.worldName(), result.dimensionKey(), result.cacheHit() ? " (cache hit)" : "");
                else {
                    String detail = assignment.worldName() + ": " + String.join("; ", result.messages()); errors.add(detail);
                    context.getLogger().error("Assignment was NOT registered; other worlds were not modified: {}", detail);
                }
            }
            new RegenerationRequestStore(context.getDataDirectory()).process(results).forEach(outcome -> {
                if (outcome.success()) context.getLogger().info("Pending regeneration {}: {}", outcome.world(), outcome.detail());
                else context.getLogger().error("Pending regeneration {} failed and will be retried: {}", outcome.world(), outcome.detail());
            });
            BootstrapState.set(new BootstrapState(context.getDataDirectory(), bundle, packs,
                    Collections.unmodifiableMap(results), List.copyOf(errors)));
            DatapackDiscoveryAdapter adapter = new DatapackDiscoveryAdapter();
            context.getLifecycleManager().registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY, event -> {
                List<CompilationResult> successful=results.values().stream().filter(CompilationResult::success).toList();
                if(successful.isEmpty())return;
                try {
                    String id=adapter.discoverBundle(event.registrar(),successful,
                            context.getDataDirectory().resolve("compiled/monaworlddatapacks-generated.zip"));
                    context.getLogger().info("Discovered generated datapack bundle {} for worlds {}",id,
                            successful.stream().map(CompilationResult::world).toList());
                } catch (IOException | RuntimeException failure) {
                    context.getLogger().error("Generated datapack bundle was NOT enabled. Other worlds were not modified.", failure);
                }
            });
        } catch (IOException | RuntimeException fatal) {
            context.getLogger().error("Bootstrap configuration failed. No generated datapack was registered.", fatal);
            errors.add(fatal.getClass().getSimpleName() + ": " + fatal.getMessage());
            try {
                ConfigurationBundle empty = new ConfigManager(context.getDataDirectory()).load();
                BootstrapState.set(new BootstrapState(context.getDataDirectory(), empty,
                        new DatapackManager(context.getDataDirectory(), empty.plugin().zipLimits()), Map.of(), List.copyOf(errors)));
            } catch (IOException fallbackFailure) {
                context.getLogger().error("Unable to initialize safe fallback state", fallbackFailure);
            }
        }
    }
}

package net.monacraft.mwd.plugin;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.monacraft.mwd.bootstrap.BootstrapState;
import net.monacraft.mwd.command.MonaWorldDatapacksCommand;
import net.monacraft.mwd.multiverse.MultiverseAdapter;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.List;

public final class MonaWorldDatapacksPlugin extends JavaPlugin {
    @Override public void onEnable() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
                getPluginMeta(), "mwd", "Manage safely scoped worldgen datapacks",
                List.of("monaworlddatapacks", "mvdp"), new MonaWorldDatapacksCommand(this)));
        BootstrapState state = BootstrapState.get();
        new MultiverseAdapter(getSLF4JLogger()).connect(state.configuration(), state.compilations());
        getSLF4JLogger().info("Enabled with {} successful compiled assignment(s)", state.compilations().values().stream().filter(c -> c.success()).count());
    }
}

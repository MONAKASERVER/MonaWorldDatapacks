package net.monacraft.mwd.paper;

import io.papermc.paper.datapack.Datapack;
import io.papermc.paper.datapack.DatapackRegistrar;
import net.kyori.adventure.text.Component;
import net.monacraft.mwd.compiler.CompilationResult;
import java.io.IOException;

public final class DatapackDiscoveryAdapter {
    public void discover(DatapackRegistrar registrar, CompilationResult result) throws IOException {
        if (!result.success() || result.output() == null) throw new IllegalArgumentException("Only successful compilation results may be discovered");
        String id = "monaworlddatapacks/" + result.world();
        if (registrar.hasPackDiscovered(id)) registrar.removeDiscoveredPack(id);
        registrar.discoverPack(result.output(), id, configurer -> configurer
                .title(Component.text("MonaWorldDatapacks: " + result.world()))
                .autoEnableOnServerStart(true)
                .position(true, Datapack.Position.TOP));
    }
}


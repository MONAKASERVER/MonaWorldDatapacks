package net.monacraft.mwd.paper;

import io.papermc.paper.datapack.Datapack;
import io.papermc.paper.datapack.DatapackRegistrar;
import net.kyori.adventure.text.Component;
import net.monacraft.mwd.compiler.CompilationResult;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public final class DatapackDiscoveryAdapter {
    public String discoverBundle(DatapackRegistrar registrar, Collection<CompilationResult> results,
                                 Path output) throws IOException {
        List<CompilationResult> successful=results.stream().filter(r->r.success()&&r.output()!=null).toList();
        if(successful.isEmpty())throw new IllegalArgumentException("At least one successful compilation result is required");
        // Paper 1.21.11 currently crashes while comparing the Adventure titles of two
        // plugin-discovered packs before CraftRegistry has initialized. The compiler
        // already gives every assignment an isolated namespace, so safely bundle the
        // generated ZIPs and expose one Paper pack without weakening world isolation.
        Path bundle=new BundledDatapackWriter().write(successful.stream().map(CompilationResult::output).toList(),output);
        String id="monaworlddatapacks/"+successful.getFirst().world();
        for(CompilationResult result:successful){String legacy="monaworlddatapacks/"+result.world();if(registrar.hasPackDiscovered(legacy))registrar.removeDiscoveredPack(legacy);}
        registrar.discoverPack(bundle,id,configurer -> configurer
                .title(Component.empty())
                .autoEnableOnServerStart(true)
                .position(true, Datapack.Position.TOP));
        return id;
    }
}
